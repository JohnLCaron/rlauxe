package org.cryptobiotic.rlauxe.sf

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.audit.OneAuditConfig
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.CvrExport
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.oneaudit.CardPoolFromCvrs
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.addOAClcaAssortersFromMargin
import org.cryptobiotic.rlauxe.oneaudit.unpooled
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
    cvrExportCsv: String,
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
        cvrExportCsv,
    )
    val cardPoolList = CardPoolList(cardPools.values)
    val contestNcsAmended = mutableMapOf<Int, Int>()
    contestNcs.forEach { (contestId, Nc) -> contestNcsAmended[contestId] = Nc + (contestAmended[contestId] ?: 0) }

    val contestTabSums = mutableMapOf<Int, ContestTabulation>()
    cardPools.forEach { (_, pool : CardPoolFromCvrs) ->
        pool.sum( contestTabSums)
    }

    // make contests based on cardPool tabulations
    val unpooled = cardPools.values.find { it.poolName == unpooled }!!
    val allContests =  makeAllOneAuditContests(contestTabSums, contestNcsAmended, unpooled)

    // pass 2 through cvrs, create all the clca assertions in one go
    val auditableContests: List<OAContestUnderAudit> = allContests.filter { it.preAuditStatus == TestH0Status.InProgress }
    val poolsOnly = cardPools.filter { it.value.poolName != org.cryptobiotic.rlauxe.oneaudit.unpooled }
    addOAClcaAssortersFromMargin(auditableContests, poolsOnly)

    // these checks may modify the contest status; dont call until clca assertions are created
    checkContestsCorrectlyFormed(auditConfig, allContests)
    val state = checkContestsWithCvrs(allContests, CvrExportAdapter(cardPoolList.iterator()), show = true)
    logger.info{state}

    writeContestsJsonFile(allContests, publisher.contestsFile())
    logger.info{"   writeContestsJsonFile ${publisher.contestsFile()}"}
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())
    logger.info{"   writeAuditConfigJsonFile ${publisher.auditConfigFile()}"}

    // we write the sortedCards here while we have the amended CvrExports in memory
    val poolNameToId = readBallotPoolCsvFile("$auditDir/$ballotPoolsFile").poolNameToId()
    val working = workingDir ?: "$topDir/sortChunks"
    SortMerge(auditDir, "unused", working, "$auditDir/$sortedCardsFile", pools = poolNameToId).run2(cardPoolList.iterator())

    println("took = $stopwatch")
}

// no styles case
fun createCardPoolsNS(
    auditDir: String,
    contestInfos: Map<Int, ContestInfo>,
    castVoteRecordZip: String,
    contestManifestFilename: String,
    cvrExportCsv: String,
    ): Pair<Map<Int, CardPoolNs>, Map<Int, Int>>
{
    val contestManifest = readContestManifestFromZip(castVoteRecordZip, contestManifestFilename)
    println("IRV contests = ${contestManifest.irvContests}")

    // create the unamended card Pools,
    var count = 0
    val cardPoolsU: MutableMap<String, CardPoolNs> = mutableMapOf()
    val cvrIter = cvrExportCsvIterator(cvrExportCsv)
    while (cvrIter.hasNext()) {
        count++
        val cvrExport: CvrExport = cvrIter.next()
        val pool = cardPoolsU.getOrPut(cvrExport.poolKey() ) {
            CardPoolNs(cvrExport.poolKey(), cardPoolsU.size + 1, contestInfos)
        }
        pool.accumulateVotes(cvrExport.toCvr())
        pool.cvrs.add(cvrExport)
    }
    println("unamended pools = ${cardPoolsU.size} from $count cvrs")

    // for each pool, every cvr has to have every contest in the pool
    var cvrsAmended = 0
    val contestAmended = mutableMapOf<Int, Int>()  // contestId, nadded
    cardPoolsU.filter { it.key != unpooled }.values.forEach { pool ->
        val needContests = pool.contestTabs.keys
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
            CardPoolNs(cvrExport.poolKey(), cardPoolsM.size + 1, contestInfos)
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
    val sortedPools = cardPoolsM.filter { it.value.poolName != unpooled }.toSortedMap()
    sortedPools.forEach { (poolName, pool) ->
        val bpools = pool.toBallotPools() // one for each contest
        bpools.forEach { poutputStream.write(writeBallotPoolCSV(it).toByteArray()) }
        poolCount += bpools.size
    }
    poutputStream.close()
    println(" total ${sortedPools.size} pools")

    return Pair(cardPoolsM.values.associateBy { it.poolId }, contestAmended)
}

// keep all the CVRS, which we can use to calculate average assort.
class CardPoolNs( poolName: String, poolId: Int, contestInfos: Map<Int, ContestInfo>) :
    CardPoolFromCvrs(poolName, poolId, contestInfos) {
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