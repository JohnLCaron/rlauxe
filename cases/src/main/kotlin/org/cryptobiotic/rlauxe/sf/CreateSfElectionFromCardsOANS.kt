package org.cryptobiotic.rlauxe.sf

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestInfo
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
fun createSfElectionFromCsvExportOANS(
    topDir: String,
    auditDir: String,
    castVoteRecordZip: String,
    contestManifestFilename: String,
    candidateManifestFile: String,
    cvrCsvFilename: String,
    auditConfigIn: AuditConfig? = null,
    show: Boolean = false
) {
    val stopwatch = Stopwatch()
    val contestInfos = makeContestInfos(castVoteRecordZip, contestManifestFilename, candidateManifestFile)

    val publisher = Publisher(auditDir) // creates auditDir

    // pass 1 through cvrs, make card pools
    val cardPools: Map<String, CardPoolNs> = createCardPoolsNS(
        auditDir,
        contestInfos.associateBy { it.id },
        castVoteRecordZip,
        contestManifestFilename,
        cvrCsvFilename,
    )
    val cardPoolList = CardPoolList(cardPools.values)

    // make contests based on cardPools
    val irvContests = makeOneAuditIrvContests(contestInfos.filter { it.choiceFunction == SocialChoiceFunction.IRV }, cardPools)
    val contests = makeOneAuditContests(contestInfos.filter { it.choiceFunction == SocialChoiceFunction.PLURALITY }, cardPools)
    val allContests = irvContests + contests

    // pass 2 through cvrs, create all the clca assertions in one go
    val auditableContests: List<OAContestUnderAudit> = allContests.filter { it.preAuditStatus == TestH0Status.InProgress }
    addOAClcaAssorters(auditableContests, cardPoolList.iterator(), cardPools)

    // these checks may modify the contest status; dont call until clca assertions are created
    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.ONEAUDIT, hasStyles = true, sampleLimit = 20000, riskLimit = .05, nsimEst = 10,
    )

    checkContestsCorrectlyFormed(auditConfig, contests)
    checkContestsWithCvrs(contests, CvrExportAdapter(cardPoolList.iterator()), show = true)

    writeContestsJsonFile(allContests, publisher.contestsFile())
    println("   writeContestsJsonFile ${publisher.contestsFile()}")
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())
    println("   writeAuditConfigJsonFile ${publisher.auditConfigFile()}")

    // we are going to write the sortedCards here since we have the CvrExports in memory
    val ballotPools = readBallotPoolCsvFile("$auditDir/$ballotPoolsFile")
    val pools = ballotPools.toPoolMap()
    SortMerge(auditDir, "unused", "$topDir/sortChunks", "$auditDir/$sortedCardsFile", pools = pools).run2(cardPoolList.iterator())

    println("took = $stopwatch")
}

// no styles case
fun createCardPoolsNS(
    auditDir: String,
    contestInfos: Map<Int, ContestInfo>,
    castVoteRecordZip: String,
    contestManifestFilename: String,
    cvrCsvFilename: String,
    ): Map<String, CardPoolNs>
{
    val contestManifest = readContestManifestFromZip(castVoteRecordZip, contestManifestFilename)
    println("IRV contests = ${contestManifest.irvContests}")

    // create the card Pools
    val cardPoolsU: MutableMap<String, CardPoolNs> = mutableMapOf()
    val cvrIter = cvrExportCsvIterator(cvrCsvFilename)
    while (cvrIter.hasNext()) {
        val cvrExport: CvrExport = cvrIter.next()
        val pool = cardPoolsU.getOrPut(cvrExport.poolKey() ) {
            CardPoolNs(cvrExport.poolKey(), cardPoolsU.size + 1, contestManifest.irvContests, contestInfos)
        }
        pool.accumulateVotes(cvrExport)
    }

    // for each pool, every cvr has to have every contest in the pool
    var cvrsAmended = 0
    val contestAmended = mutableMapOf<Int, Int>()
    cardPoolsU.filter { it.key != "unpooled" }.values.forEach { pool ->
        val needContests = pool.contestTabulations.keys + pool.irvVoteConsolidations.keys
        val cvrsM = mutableListOf<CvrExport>()

        // TODO does the assort average remain the same adding undervotes ?? ? i dont think so?
        //   but reported mean doesnt change, so then it should ?? im probably confusing assorter and clcaAssorter again
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
        pool.accumulateVotes(cvrExport)
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

    return cardPoolsM
}

class CardPoolNs( poolName: String, poolId: Int, irvIds: Set<Int>,  contestInfos: Map<Int, ContestInfo>) :
    CardPool(poolName, poolId, irvIds, contestInfos) {

    var cvrs = mutableListOf<CvrExport>() // TODO keeping all the CvrExport in memory here. better to write them back ??

    override fun accumulateVotes(cvr: CvrExport) {
        cvrs.add(cvr)
        super.accumulateVotes(cvr)
    }
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
