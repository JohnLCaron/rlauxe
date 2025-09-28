package org.cryptobiotic.rlauxe.sf

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.audit.OneAuditConfig
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.CvrExport
import org.cryptobiotic.rlauxe.core.CvrExport.Companion.unpooled
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.*
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.util.*
import java.io.FileOutputStream
import kotlin.Boolean
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

private val logger = KotlinLogging.logger("createSfElectionFromCsvExportOANS")

// NoStyles case
fun createSfElectionFromCvrExportOANS(
    topDir: String,
    auditDir: String,
    castVoteRecordZip: String,
    contestManifestFilename: String,
    candidateManifestFile: String,
    cvrCsvFilename: String,
    auditConfigIn: AuditConfig? = null,
    workingDir: String? = null,
) {
    val stopwatch = Stopwatch()
    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.ONEAUDIT, hasStyles = true, sampleLimit = 20000, riskLimit = .05, nsimEst = 50,
        oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = false)
    )
    val publisher = Publisher(auditDir) // creates auditDir

    val (contestNcs, contestInfos) = makeContestInfos(castVoteRecordZip, contestManifestFilename, candidateManifestFile)

    // pass 1 through cvrs, make card pools and augment
    val (cardPools: Map<Int, CardPoolNs>, contestAmended: Map<Int, Int>) = createCardPoolsNS(
        auditDir,
        contestInfos.associateBy { it.id },
        castVoteRecordZip,
        contestManifestFilename,
        cvrCsvFilename,
    )
    val cardPoolList = CardPoolList(cardPools.values)
    val contestNcsAmended = mutableMapOf<Int, Int>()
    contestNcs.forEach { (contestId, Nc) -> contestNcsAmended[contestId] = Nc + (contestAmended[contestId] ?: 0) }

    // make contests based on cardPools, which must include the unpooled pool
    val irvContests = makeOneAuditIrvContests(contestInfos.filter { it.choiceFunction == SocialChoiceFunction.IRV }, cardPools, contestNcsAmended)
    val contestsUA = makeOneAuditContests(contestInfos.filter { it.choiceFunction == SocialChoiceFunction.PLURALITY }, cardPools, contestNcsAmended)
    val allContests = contestsUA + irvContests

    // pass 2 through cvrs, create all the clca assertions in one go
    val auditableContests: List<OAContestUnderAudit> = allContests.filter { it.preAuditStatus == TestH0Status.InProgress }
    // addOAClcaAssorters(auditableContests, CvrExportAdapter(cardPoolList.iterator()), cardPools)
    addOAClcaAssortersFromCvrExport(auditableContests, cardPoolList.iterator(), cardPools)

    // these checks may modify the contest status; dont call until clca assertions are created
    checkContestsCorrectlyFormed(auditConfig, contestsUA)
    checkContestsWithCvrs(contestsUA, CvrExportAdapter(cardPoolList.iterator()), show = true)

    writeContestsJsonFile(allContests, publisher.contestsFile())
    println("   writeContestsJsonFile ${publisher.contestsFile()}")
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())
    println("   writeAuditConfigJsonFile ${publisher.auditConfigFile()}")

    // we write the sortedCards here while we have the amended CvrExports in memory
    val ballotPools = readBallotPoolCsvFile("$auditDir/$ballotPoolsFile")
    val pools = ballotPools.poolNameToId()
    val working = workingDir ?: "$topDir/sortChunks"
    SortMerge(auditDir, "unused", working, "$auditDir/$sortedCardsFile", pools = pools).run2(cardPoolList.iterator())

    println("took = $stopwatch")
}

// no styles case
fun createCardPoolsNS(
    auditDir: String,
    contestInfos: Map<Int, ContestInfo>,
    castVoteRecordZip: String,
    contestManifestFilename: String,
    cvrCsvFilename: String,
    ): Pair<Map<Int, CardPoolNs>, Map<Int, Int>>
{
    val contestManifest = readContestManifestFromZip(castVoteRecordZip, contestManifestFilename)
    println("IRV contests = ${contestManifest.irvContests}")

    // create the unamended card Pools,
    var count = 0
    val cardPoolsU: MutableMap<String, CardPoolNs> = mutableMapOf()
    val cvrIter = cvrExportCsvIterator(cvrCsvFilename)
    while (cvrIter.hasNext()) {
        count++
        val cvrExport: CvrExport = cvrIter.next()
        val pool = cardPoolsU.getOrPut(cvrExport.poolKey() ) {
            CardPoolNs(cvrExport.poolKey(), cardPoolsU.size + 1, contestManifest.irvContests, contestInfos)
        }
        pool.accumulateVotes(cvrExport.toCvr())
        pool.cvrs.add(cvrExport)
    }
    println("unamended pools = ${cardPoolsU.size} from $count cvrs")

    // for each pool, every cvr has to have every contest in the pool
    var cvrsAmended = 0
    val contestAmended = mutableMapOf<Int, Int>()  // contestId, nadded
    cardPoolsU.filter { it.key != unpooled }.values.forEach { pool ->
        val needContests = pool.contestTabulations.keys + pool.irvVoteConsolidations.keys
        val cvrsM = mutableListOf<CvrExport>()

        pool.cvrs.forEach { cvr ->
            var wasAmended = false
            val votesM= cvr.votes.toMutableMap()
            needContests.forEach { contestId ->
                if (!votesM.containsKey(contestId)) {
                    votesM[contestId] = IntArray(0)
                    wasAmended = true
                    pool.addUndervote(contestId)
                    val ca = contestAmended.getOrPut(contestId) { 0 }
                    contestAmended[contestId] = ca + 1
                }
            }
            if (wasAmended) {
                cvrsM.add(cvr.copy(votes = votesM))
                cvrsAmended++
            } else {
                cvrsM.add(cvr)
            }
        }
        pool.cvrs = cvrsM
    }
    println("cvrAmended = $cvrsAmended")
    println("contestAmended")
    contestAmended.toSortedMap().forEach { println("  $it") }

    // create amended card Pools
    val cardPoolsM: MutableMap<String, CardPoolNs> = mutableMapOf()
    val cvrIterM = CardPoolList(cardPoolsU.values).iterator()
    while (cvrIterM.hasNext()) {
        val cvrExport: CvrExport = cvrIterM.next()
        val pool = cardPoolsM.getOrPut(cvrExport.poolKey() ) {
            CardPoolNs(cvrExport.poolKey(), cardPoolsM.size + 1, contestManifest.irvContests, contestInfos)
        }
        pool.accumulateVotes(cvrExport.toCvr())
        pool.cvrs.add(cvrExport)
    }

    // write the ballot pool file. read back in createSortedCards to mark the pooled cvrs
    // note contests with ncards = 0 are not written. TODO does bassort deal with that case correctly?
    val poolFilename = "$auditDir/$ballotPoolsFile"
    println(" writing to $poolFilename with ${cardPoolsM.size} pools")
    val poutputStream = FileOutputStream(poolFilename)
    poutputStream.write(BallotPoolCsvHeader.toByteArray()) // UTF-8

    var poolCount = 0
    val sortedPools = cardPoolsM.toSortedMap()
    sortedPools.forEach { (poolName, pool) ->
        val bpools = pool.toBallotPools() // one for each contest
        bpools.forEach { poutputStream.write(writeBallotPoolCSV(it).toByteArray()) }
        poolCount += bpools.size
    }
    poutputStream.close()
    println(" total ${sortedPools.size} pools")

    return Pair(cardPoolsM.values.associateBy { it.poolId }, contestAmended)
}

// keep all the CVRS, which we can use to caclulate average assort. only used for SF.
class CardPoolNs( poolName: String, poolId: Int, irvIds: Set<Int>,  contestInfos: Map<Int, ContestInfo>) :
    CardPoolSF(poolName, poolId, irvIds, contestInfos) {

    // TODO keeping all the CvrExport in memory here. better to write them back ??
    var cvrs = mutableListOf<CvrExport>()
}

class CardPoolList(val cardPools: Collection<CardPoolNs>): Iterable<CvrExport> {

    override fun iterator(): Iterator<CvrExport> {
        return PoolIterator()
    }

    inner class PoolIterator() : Iterator<CvrExport> {
        var pools = cardPools.iterator()
        var base = getNextBaseIterator()

        override fun hasNext(): Boolean {
            if (base == null) base = getNextBaseIterator()
            if (base == null) return false

            // its possible that the base iterator is empty
            while (!base!!.hasNext()) {
                base = getNextBaseIterator()
                if (base == null) return false
            }
            return true
        }

        override fun next(): CvrExport {
            return base!!.next()
        }

        fun getNextBaseIterator(): BaseIterator? {
            if (!pools.hasNext()) return null
            return BaseIterator(pools.next())
        }
    }

    class BaseIterator(val pool: CardPoolNs) : Iterator<CvrExport> {
        val bitter = pool.cvrs.iterator()
        override fun hasNext(): Boolean = bitter.hasNext()
        override fun next(): CvrExport = bitter.next()
    }
}
