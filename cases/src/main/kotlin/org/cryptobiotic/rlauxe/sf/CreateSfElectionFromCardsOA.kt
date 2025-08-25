package org.cryptobiotic.rlauxe.sf

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.oneaudit.AssortAvgsInPools
import org.cryptobiotic.rlauxe.oneaudit.BallotPool
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.OAIrvContestUA
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.*
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.raire.IrvContestVotes
import org.cryptobiotic.rlauxe.raire.RaireContest
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.raire.VoteConsolidator
import org.cryptobiotic.rlauxe.raire.makeRaireContestUA
import org.cryptobiotic.rlauxe.util.*
import java.io.FileOutputStream
import kotlin.Boolean
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

private val logger = KotlinLogging.logger("createSfElectionFromCsvExportOA")

// use the contestManifest and candidateManifest to create the contestInfo, both regular and IRV.
// Use "CvrExport" CSV file to tally the votes and create the assertions.
fun createSfElectionFromCsvExportOA(
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
    val cardPools: Map<String, CardPool> = createCardPools(
        auditDir,
        contestInfos.associateBy { it.id},
        castVoteRecordZip,
        contestManifestFilename,
        cvrCsvFilename,
    )

    // make contests based on cardPools
    val irvContests = makeOneAuditIrvContests(contestInfos.filter { it.choiceFunction == SocialChoiceFunction.IRV }, cardPools)
    val contests = makeOneAuditContests(contestInfos.filter { it.choiceFunction == SocialChoiceFunction.PLURALITY }, cardPools)
    val allContests = irvContests + contests

    // pass 2 through cvrs, create all the clca assertions in one go
    val auditableContests: List<OAContestUnderAudit> = allContests.filter { it.preAuditStatus == TestH0Status.InProgress }
    addOAClcaAssorters(auditableContests, cvrExportCsvIterator(cvrCsvFilename), cardPools)

    // these checks may modify the contest status; dont call until clca assertions are created
    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.ONEAUDIT, hasStyles = true, sampleLimit = 20000, riskLimit = .05, nsimEst = 10,
    )

    // TODO check that the sum of pooled and unpooled votes is correct
    /*  check that the votes agree with the summary XML
    val contestManifest = readContestManifestFromZip(castVoteRecordZip, contestManifestFilename)
    val staxContests = StaxReader().read("src/test/data/SF2024/summary.xml")

    val votes = tabulateCvrs(CvrExportAdapter(cvrExportCsvIterator(cvrCsvFilename)))
    votes.forEach { (id, ct) ->
        val contestName = contestManifest.contests[id]!!.Description
        val staxContest: StaxReader.StaxContest = staxContests.find { it.id == contestName}!!
        if (staxContest.ncards() != ct.ncards) {
            println("staxContest $contestName ($id) has ncards = ${staxContest.ncards()} not equal to cvr summary = ${ct.ncards} ")
            // assertEquals(staxContest.blanks(), contest.blanks)
        }
    }

    //  check that the contests agree with the summary XML
    contests.forEach { contest ->
        val contestName = contestManifest.contests[contest.id]!!.Description
        val staxContest: StaxReader.StaxContest = staxContests.find { it.id == contestName}!!
        val ncards = staxContest.ncards()
        if (ncards != contest.Nc) {
            println("staxContest $contestName ($contest.id) has ncards = ${staxContest.ncards()} not equal to contest Nc = ${contest.Nc} ")
            // assertEquals(staxContest.blanks(), contest.blanks)
        }
    }

     */

    checkContestsCorrectlyFormed(auditConfig, contests)
    checkContestsWithCvrs(contests, CvrExportAdapter(cvrExportCsvIterator(cvrCsvFilename)), show = true)

    writeContestsJsonFile(allContests, publisher.contestsFile())
    println("   writeContestsJsonFile ${publisher.contestsFile()}")
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())
    println("   writeAuditConfigJsonFile ${publisher.auditConfigFile()}")

    println("took = $stopwatch")
}

// non-IRV
fun makeOneAuditContests(contestInfos: List<ContestInfo>, cardPools: Map<String, CardPool>): List<OAContestUnderAudit> {
    val contestsUAs = mutableListOf<OAContestUnderAudit>()
    contestInfos.map { info ->
        // get a complete tabulation over all the pools
        val allPools = ContestTabulation()
        var ncards = 0
        cardPools.values.forEach { pool ->
            val poolTab = pool.contestTabulations[info.id]
            if (poolTab != null) {
                allPools.addVotes(poolTab.votes)
                ncards += poolTab.ncards
            }
        }

        if (ncards > 0) {
            val contest = Contest(info, allPools.votes, ncards, ncards)
            contestsUAs.add(OAContestUnderAudit(contest))
        }
    }
    return contestsUAs
}

// IRV
fun makeOneAuditIrvContests(contestInfos: List<ContestInfo>, cardPools: Map<String, CardPool>): List<OAIrvContestUA> {
    val contestsUAs = mutableListOf<OAIrvContestUA>()
    contestInfos.map { info ->
        // get a complete tabulation over all the pools
        val allPools = VoteConsolidator()
        var ncards = 0
        cardPools.values.forEach { pool ->
            val poolVC = pool.irvVoteConsolidations[info.id]
            if (poolVC != null) {
                allPools.addVotes(poolVC.vc)
                ncards += poolVC.ncards
            }
        }

        // fun makeRaireContestUA(info: ContestInfo, voteConsolidator: VoteConsolidator, Nc: Int, Ncast: Int, Nundervotes: Int): RaireContestUnderAudit {
        val rau : RaireContestUnderAudit = makeRaireContestUA(
            info,
            allPools,
            ncards,
            ncards,
        )
        contestsUAs.add( OAIrvContestUA(rau.contest as RaireContest,  true, rau.rassertions) )
    }
    return contestsUAs
}

fun createCardPools(
    auditDir: String,
    contestInfos: Map<Int, ContestInfo>,
    castVoteRecordZip: String,
    contestManifestFilename: String,
    cvrCsvFilename: String,
    ): Map<String, CardPool>
{
    val contestManifest = readContestManifestFromZip(castVoteRecordZip, contestManifestFilename)
    println("IRV contests = ${contestManifest.irvContests}")

    val cardPools: MutableMap<String, CardPool> = mutableMapOf()

    val cvrIter = cvrExportCsvIterator(cvrCsvFilename)
    while (cvrIter.hasNext()) {
        val cvrExport: CvrExport = cvrIter.next()
        val pool = cardPools.getOrPut(cvrExport.poolKey() ) {
            CardPool(cvrExport.poolKey(), cardPools.size + 1, contestManifest.irvContests, contestInfos)
        }
        pool.accumulateVotes(cvrExport)
    }

    // BallotPools
    val poolFilename = "$auditDir/$ballotPoolsFile"
    println(" writing to $poolFilename with ${cardPools.size} pools")
    val poutputStream = FileOutputStream(poolFilename)
    poutputStream.write(BallotPoolCsvHeader.toByteArray()) // UTF-8

    // write the ballot pool file
    var poolCount = 0
    val sortedPools = cardPools.toSortedMap()
    sortedPools.forEach { (poolName, pool) ->
        val bpools = pool.toBallotPools() // one for each contest
        bpools.forEach { poutputStream.write(writeBallotPoolCSV(it).toByteArray()) }
        poolCount += bpools.size
    }
    poutputStream.close()
    println(" total ${sortedPools.size} pools")
    // accumulate across all the pools
    val contestCount = mutableMapOf<Int, ContestTabulation>()
    sortedPools.forEach { (_poolName, pool) ->
        pool.contestTabulations.forEach { contestId, poolCt ->
            val ct = contestCount.getOrPut(contestId) { ContestTabulation() }
            ct.addVotes( poolCt.votes)
            ct.ncards += poolCt.ncards
        }
        pool.irvVoteConsolidations.forEach { contestId, irv ->
            val ct = contestCount.getOrPut(contestId) { ContestTabulation() }
            ct.ncards += irv.ncards
        }
    }

    // check that the cardPools agree with the summary XML
    val staxContests = StaxReader().read("src/test/data/SF2024/summary.xml")
    println("staxContests")
    contestCount.toSortedMap().forEach { (id, ct) ->
        val contestName = contestManifest.contests[id]!!.Description
        val staxContest: StaxReader.StaxContest = staxContests.find { it.id == contestName}!!
        if (staxContest.ncards() != ct.ncards) {
            logger.warn{"staxContest $contestName ($id) has ncards = ${staxContest.ncards()} not equal to cvr summary = ${ct.ncards} "}
            // assertEquals(staxContest.blanks(), contest.blanks)
        }
        println("  $contestName ($id) has ncards stax = ${staxContest.ncards()}, ct.ncards = ${ct.ncards}")
    }

    return cardPools
}

// record the ContestTabulation (regular) or VoteConsolidator (IRV)
class CardPool(val poolName: String, val poolId: Int, val irvIds: Set<Int>, val contestInfos: Map<Int, ContestInfo>) {
    val contestTabulations = mutableMapOf<Int, ContestTabulation>()  // contestId -> ContestTabulation
    val irvVoteConsolidations = mutableMapOf<Int, IrvContestVotes>()  // contestId -> IrvContestVotes TODO i think you dont need this by pool
    var assortAvg : MutableMap<Int, MutableMap<AssorterIF, AssortAvg>>? = null // contestId -> assorter -> AssortAvg

    fun accumulateVotes(cvr : CvrExport) {
        cvr.votes.forEach { (contestId, candIds) ->
            if (irvIds.contains(contestId)) {
                val irvContestVotes = irvVoteConsolidations.getOrPut(contestId) { IrvContestVotes(contestInfos[contestId]!!) }
                irvContestVotes.addVote(candIds) // TODO not switching to index space !!
            } else {
                val contestTab = contestTabulations.getOrPut(contestId) { ContestTabulation() }
                contestTab.ncards++
                contestTab.addVotes(candIds)
            }
        }
    }

    fun toBallotPools(): List<BallotPool> {
        val bpools = mutableListOf<BallotPool>()
        contestTabulations.forEach { contestId, contestCount ->
            if (contestCount.ncards > 0) {
                bpools.add(BallotPool(poolName, poolId, contestId, contestCount.ncards, contestCount.votes))
            }
        }
        return bpools
    }

    fun ballotPoolForContest(contestId: Int): BallotPool? {
        val cm = contestTabulations[contestId]
        return if (cm == null) null else BallotPool(poolName, poolId, contestId, cm.ncards, cm.votes)
    }
}

// create the ClcaAssorters for OneAudit
fun addOAClcaAssorters(
    oaContests: List<OAContestUnderAudit>,
    cvrIter: Iterator<CvrExport>,
    cardPools: Map<String, CardPool>
) {

    cardPools.values.forEach { it.assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>() }

    // sum all the assorters values in one pass across all the cvrs
    while (cvrIter.hasNext()) {
        val cvrExport: CvrExport = cvrIter.next()
        val cardPool = cardPools[cvrExport.poolKey()]!!
        val cvr = cvrExport.toCvr()

        oaContests.forEach { contest ->
            val avg = cardPool.assortAvg!!.getOrPut(contest.id) { mutableMapOf() }
            contest.pollingAssertions.forEach { assertion ->
                val passorter = assertion.assorter
                val assortAvg = avg.getOrPut(passorter) { AssortAvg() } // TODO could have a hash collision ?
                if (cvr.hasContest(contest.id)) {
                    assortAvg.ncards++
                    assortAvg.totalAssort += passorter.assort(cvr, usePhantoms = false) // TODO usePhantoms correct ??
                }
            }
        }
    }

    // create the clcaAssertions and add them to the oaContests
    oaContests.forEach { oaContest ->
        val clcaAssertions = oaContest.pollingAssertions.map { assertion ->
            val assortAverage = mutableMapOf<Int, Double>() // poolId -> average assort value
            cardPools.values.forEach { cardPool ->
                val assortAvg = cardPool.assortAvg!![oaContest.id]!![assertion.assorter]!!
                assortAverage[cardPool.poolId] = assortAvg.avg()
            }

            val poolAvgs = AssortAvgsInPools(assertion.info.id, assortAverage)
            val clcaAssorter = OneAuditClcaAssorter(assertion.info, assertion.assorter, true, poolAvgs)
            ClcaAssertion(assertion.info, clcaAssorter)
        }
        oaContest.clcaAssertions = clcaAssertions
    }

    // compare the assortAverage with the value computed from the contestTabulation (non-IRV only)
    cardPools.values.forEach { cardPool ->
        cardPool.contestTabulations.forEach { (contestId, contestTabulation) ->
            val avg = cardPool.assortAvg!![contestId]!!
            avg.forEach { (assorter, assortAvg) ->
                val calcReportedMargin = assorter.calcReportedMargin(contestTabulation.votes, contestTabulation.ncards)
                val calcReportedMean = margin2mean(calcReportedMargin)
                val cvrAverage = assortAvg.avg()

                if (!doubleIsClose(calcReportedMean, cvrAverage)) {
                    println("pool ${cardPool.poolId} means not agree for assorter $assorter ")
                }
            }
        }
    }
}

class AssortAvg() {
    var ncards = 0
    var totalAssort = 0.0

    fun avg() : Double = if (ncards == 0) 0.0 else totalAssort / ncards
}

// class ContestTabulation {
//    val votes = mutableMapOf<Int, Int>()
//    var ncards = 0

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

//// obsolete
// TODO use ContestTabulation in CheckAudits
data class ContestCount(var ncards: Int = 0, val counts: MutableMap<Int, Int> = mutableMapOf() ) {

    fun reportedMargin(winner: Int, loser: Int): Double {
        val winnerVotes = counts[winner] ?: 0
        val loserVotes = counts[loser] ?: 0
        return (winnerVotes - loserVotes) / ncards.toDouble()
    }

    override fun toString(): String {
        return "total=$ncards, counts=${counts.toSortedMap()}"
    }
}
