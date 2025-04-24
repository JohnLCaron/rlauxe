package org.cryptobiotic.rlauxe.sf

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.BallotPool
import org.cryptobiotic.rlauxe.persist.PersistentAudit
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.audit.CvrIteratorAdapter
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readBallotPoolCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFile
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.workflow.OneAuditAssertionAuditor
import java.nio.file.Path
import kotlin.math.min
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals

class TestSfElectionFromCards {

    // This is to match up with SHANGRLA
    // make a OneAudit from Dominion exported CVRs, using CountingGroupId=1 as the pooled votes
    // write "$topDir/cards.csv", "$topDir/ballotPools.csv"
    @Test
    fun createSF2024PoaCards() {
        val stopwatch = Stopwatch()
        val sfDir = "/home/stormy/temp/cases/sf2024P"
        val zipFilename = "$sfDir/CVR_Export_20240322103409.zip"
        val manifestFile = "$sfDir/CVR_Export_20240322103409/ContestManifest.json"
        val topDir = "/home/stormy/temp/cases/sf2024Poa"
        createAuditableCardsWithPools(topDir, zipFilename, manifestFile) // write to "$topDir/cards.csv"
        println("that took $stopwatch")
        //   createAuditableCards 8957 files totalCards=467063 group1=55810 + group2=411253 = 467063
        // countingContests
        //   1 total=176637, groupCount={2=155705, 1=20932}
        //   2 total=19175, groupCount={2=15932, 1=3243}
        // total 2086 pools
        // total contest1 cards in pools = 20932
        // total contest2 cards in pools = 3243

        createSF2024PoaElectionFromCards()
        testCardContests()
        testCardVotes()
        testCardAssertions()
        auditSf2024Poa()
    }

    // needs createSF2024PoaCards to be run first
    // @Test
    fun createSF2024PoaElectionFromCards() {
        val stopwatch = Stopwatch()
        val sfDir = "/home/stormy/temp/cases/sf2024P"
        val topDir = "/home/stormy/temp/cases/sf2024Poa"

        // create sf2024 election audit
        val auditDir = "$topDir/audit"
        clearDirectory(Path.of(auditDir))

        createSfElectionFromCardsOA(
            auditDir,
            "$sfDir/CVR_Export_20240322103409/ContestManifest.json",
            "$sfDir/CVR_Export_20240322103409/CandidateManifest.json",
            "$topDir/cards.csv",
            "$topDir/ballotPools.csv",
            emptyList()
        )

        // create sorted cards
        sortCards(auditDir, "$topDir/cards.csv", "$topDir/sortChunks")
        mergeCards(auditDir, "$topDir/sortChunks") // merge to "$auditDir/sortedCards.csv"
        // manually zip (TODO)
        println("that took $stopwatch")
        //  read 411253 cards contest1=ContestVotes(contestId=1, countBallots=155705, votes={5=126942, 7=5058, 6=5262, 3=975, 234=4851, 1=813, 2=546, 4=893, 8=464}, nvotes=145804, underVotes=9901)
        //PRESIDENT OF THE UNITED STATES-DEM (1) Nc=176637 Np=0 votes={5=142814, 7=5761, 6=6374, 3=1185, 234=5923, 1=952, 2=617, 4=987, 8=584, 245=0, 246=0, 247=0, 248=0, 249=0, 250=0} minMargin=0.7724
    }

    // @Test
    fun testCardContests() {
        val topDir = "/home/stormy/temp/cases/sf2024Poa"
        val cardFile = "$topDir/cards.csv"

        val countingContestsFromCards = mutableMapOf<Int, ContestCount>()
        val cardIter = CvrIteratorAdapter(readCardsCsvIterator(cardFile))
        while (cardIter.hasNext()) {
            val cvr = cardIter.next()
            cvr.votes.keys.forEach { contestId ->
                val contestCount = countingContestsFromCards.getOrPut(contestId) { ContestCount() }
                contestCount.ncards++
                val isPooled = if (cvr.poolId == null) 0 else 1
                val groupCount = contestCount.counts.getOrPut(isPooled) { 0 }
                contestCount.counts[isPooled] = groupCount + 1
            }
        }
        println(" countingContestsFromCards")
        countingContestsFromCards.toSortedMap().forEach { (key, value) -> println("   $key $value") }
        println("--------------------------------------------------")

        val sortedCards = "$topDir/audit/sortedCards.csv"
        val countingContestsFromSortedCards = mutableMapOf<Int, ContestCount>()
        val scardIter = CvrIteratorAdapter(readCardsCsvIterator(sortedCards))
        while (scardIter.hasNext()) {
            val cvr = scardIter.next()
            cvr.votes.keys.forEach { contestId ->
                val contestCount = countingContestsFromSortedCards.getOrPut(contestId) { ContestCount() }
                contestCount.ncards++
                val isPooled = if (cvr.poolId == null) 0 else 1
                val groupCount = contestCount.counts.getOrPut(isPooled) { 0 }
                contestCount.counts[isPooled] = groupCount + 1
            }
        }
        println(" countingContestsFromSortedCards")
        countingContestsFromSortedCards.toSortedMap().forEach { (key, value) -> println("   $key $value") }

        assertEquals(countingContestsFromCards, countingContestsFromSortedCards)
    }

    // @Test
    fun testCardVotes() {
        val topDir = "/home/stormy/temp/cases/sf2024Poa"
        val cardFile = "$topDir/cards.csv"

        val countingVotesFromCards = mutableMapOf<Int, ContestCount>()
        val cardIter = CvrIteratorAdapter(readCardsCsvIterator(cardFile))
        while (cardIter.hasNext()) {
            val cvr = cardIter.next()
            cvr.votes.forEach { (contestId, choiceIds) ->
                val contestCount = countingVotesFromCards.getOrPut(contestId) { ContestCount() }
                contestCount.ncards++
                choiceIds.forEach { cand ->
                    val nvotes = contestCount.counts[cand] ?: 0
                    contestCount.counts[cand] = nvotes + 1
                }
            }
        }
        println(" countingVotesFromCards")
        println("  contest1 = ${countingVotesFromCards[1]!!}")
        println("  contest2 = ${countingVotesFromCards[2]!!}")
        println("--------------------------------------------------")

        val sortedCards = "$topDir/audit/sortedCards.csv"
        val countingVotesFromSortedCards = mutableMapOf<Int, ContestCount>()
        val sortedIter = CvrIteratorAdapter(readCardsCsvIterator(sortedCards))
        while (sortedIter.hasNext()) {
            val cvr = sortedIter.next()
            cvr.votes.forEach { (contestId, choiceIds) ->
                val contestCount = countingVotesFromSortedCards.getOrPut(contestId) { ContestCount() }
                contestCount.ncards++
                choiceIds.forEach { cand ->
                    val nvotes = contestCount.counts[cand] ?: 0
                    contestCount.counts[cand] = nvotes + 1
                }
            }
        }
        println(" countingVotesFromSortedCards")
        println("  contest1 = ${countingVotesFromSortedCards[1]!!}")
        println("  contest2 = ${countingVotesFromSortedCards[2]!!}")
        println("--------------------------------------------------")

        assertEquals(countingVotesFromCards[1], countingVotesFromSortedCards[1])
        assertEquals(countingVotesFromCards[2], countingVotesFromSortedCards[2])
    }

    // @Test
    fun testCardAssertions() {
        val topDir = "/home/stormy/temp/cases/sf2024Poa"
        val cardFile = "$topDir/cards.csv"

        val publisher = Publisher("$topDir/audit")
        val contestsResults = readContestsJsonFile(publisher.contestsFile())
        val contests = if (contestsResults is Ok) contestsResults.unwrap()
            else throw RuntimeException("Cannot read contests from ${publisher.contestsFile()} err = $contestsResults")

        val wantContest = 2
        val wantWinner = 11
        val wantLoser = 9
        val contest2 = contests.find { it.id == wantContest }!!
        val assert911 = contest2.clcaAssertions.find { it.winner == wantWinner && it.loser == wantLoser } ?: throw RuntimeException("no assorter for contest $contest2")
        val cassort911 = assert911.cassorter as OneAuditClcaAssorter
        val assort911 = assert911.cassorter.assorter

        val expectedMargin = (11530 - 5801)/19175.toDouble()
        println("contest: ${contest2.show()}")
        println("cassort:  ${cassort911}")
        println("  calcAssortMeanFromPools = ${cassort911.calcAssortMeanFromPools()}")
        println("  expectedAssorterMargin=$expectedMargin")
        //  cassort  OneAuditComparisonAssorter for contest PRESIDENT OF THE UNITED STATES-REP (2)
        //  assorter= winner=11 loser=9 reportedMargin=0.2988 reportedMean=0.6494
        //
        //  avgCvrAssortValue=0.607275097783572 calcAssortMeanFromPools = 0.6493872229465447
        //  assort   winner=11 loser=9 reportedMargin=0.2988 reportedMean=0.6494 expectedMargin=0.29877444589308993

        val track = Tracking()
        val cardIter = CvrIteratorAdapter(readCardsCsvIterator(cardFile))
        while (cardIter.hasNext()) {
            val cvr = cardIter.next()
            if (cvr.votes[wantContest] != null) {
                track.add(cassort911.bassort(cvr, cvr)) // TODO fuzz
            }
        }
        println(track)

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////
        /*
        val welfordNonPool = Welford()
        val welfordPool = Welford()

        val cvrsCount = ContestCount()
        val cvrsCountPool = ContestCount()

        val cardIter2 = CvrIteratorAdapter(readCardsCsvIterator(cardFile))
        while (cardIter2.hasNext()) {
            val cvr = cardIter2.next()
            if (cvr.votes[wantContest] != null) {
                if (cvr.poolId == null) { // only want non-pool cards
                    welfordNonPool.update(assort911.assort(cvr))
                    cvrsCount.ncards++
                    mergeVotes(cvr.votes[wantContest]!!, cvrsCount.counts)
                } else {
                    welfordPool.update(assort911.assort(cvr))
                    cvrsCountPool.ncards++
                    mergeVotes(cvr.votes[wantContest]!!, cvrsCountPool.counts)
                }
            }
        }
        //    2 total=19175, counts={2=15932, 1=3243}
        //   contest2 = total=19175, counts={9=5087, 10=44, 11=9201, 12=283, 13=35, 14=57, 15=37, 16=358, 17=190, 230=189}

        println("   nonpool welford=${welfordNonPool.count} mean=${welfordNonPool.mean} margin=${mean2margin(welfordNonPool.mean)}")
        println("           cvrsCount ${cvrsCount} reportedMargin = ${cvrsCount.reportedMargin(wantWinner, wantLoser)}")
        println("      pool welford=${welfordPool.count} mean=${welfordPool.mean} margin=${mean2margin(welfordPool.mean)}")
        println("           cvrsCount ${cvrsCountPool} reportedMargin = ${cvrsCountPool.reportedMargin(wantWinner, wantLoser)}")
        //  assort  winner=11 loser=9 reportedMargin=0.6073: count=19175 mean=0.5044041167893594

        // problem is the pool votes are all undercounts. have to add the pool votes in
        // data class BallotPool(
        //    val name: String,
        //    val id: Int,
        //    val contest:Int,
        //    val ncards: Int,
        //    val votes: Map<Int, Int>, // candid-> nvotes
        //)
        val ballotPoolFile = "$topDir/ballotPools.csv"
        val ballotPools: List<BallotPool> =  readBallotPoolCsvFile(ballotPoolFile)
        val poolCount = ContestCount()
        ballotPools.filter{ it.contest == wantContest }.forEach { pool ->
            poolCount.ncards += pool.ncards
            mergeVotes(pool.votes, poolCount.counts)
        }
        println("   pool counts = $poolCount reportedMargin = ${poolCount.reportedMargin(wantWinner, wantLoser)}")

        val totalCount = ContestCount()
        mergeVotes(cvrsCount.counts, totalCount.counts)
        mergeVotes(poolCount.counts, totalCount.counts)
        totalCount.ncards = cvrsCount.ncards + poolCount.ncards
        println("   totalCount = $totalCount reportedMargin = ${totalCount.reportedMargin(wantWinner, wantLoser)}")

         */

        //   nonpool welford=15932 mean=0.6291112226964624 margin=0.2582224453929247
        //           cvrsCount total=15932, counts={9=5087, 10=44, 11=9201, 12=283, 13=35, 14=57, 15=37, 16=358, 17=190, 230=189} reportedMargin = 0.2582224453929199
        //      pool welford=3243 mean=0.5 margin=0.0
        //           cvrsCount total=3243, counts={} reportedMargin = 0.0
        //   pool counts = total=3243, counts={9=714, 10=7, 11=2329, 12=24, 13=4, 14=6, 15=7, 16=41, 17=35, 230=21} reportedMargin = 0.49799568300955904
        //   totalCount = total=19175, counts={9=5801, 10=51, 11=11530, 12=307, 13=39, 14=63, 15=44, 16=399, 17=225, 230=210} reportedMargin = 0.29877444589308993
    }

    fun mergeVotes(votes: Map<Int, Int>, total: MutableMap<Int, Int>) {
        votes.forEach { (cand, nvotes) ->
            val tvotes = total[cand] ?: 0
            total[cand] = tvotes + nvotes
        }
    }

    fun mergeVotes(votes: IntArray, total: MutableMap<Int, Int>) {
        votes.forEach { cand->
            val tvotes = total[cand] ?: 0
            total[cand] = tvotes + 1
        }
    }

    class Tracking {
        var count = 0
        var sum = 0.0
        var max = 0.0
        var min = 999.0

        fun add(x: Double) {
            count++
            sum += x
            min = min(min, x)
            max = max(max, x)
        }

        override fun toString() = "Tracking count=$count min=$min max=$max avg=${sum/count}"
    }

    private val show = false

    // @Test
    fun auditSf2024Poa() {
        val auditDir = "/home/stormy/temp/cases/sf2024Poa/audit"

        val workflow = PersistentAudit(auditDir, true)
        val contestRounds = workflow.contestsUA().map { ContestRound(it, 1) }

        //val contestUA = workflow.contestsUA().first()
        //val contestRound = ContestRound(contestUA, 1)
        //val assertionRound = contestRound.assertionRounds.first()
        //val cassorter = (assertionRound.assertion as ClcaAssertion).cassorter

        val mvrManager = MvrManagerCardsSingleRound("$auditDir/sortedCards.csv")
        val cvrPairs = mvrManager.makeCvrPairsForRound() // same over all contests!
        //val sampler = ClcaWithoutReplacement(contestUA.id, true, cvrPairs, cassorter, allowReset = false)

        //     runClcaAudit(workflow.auditConfig(), contestRounds, workflow.mvrManager() as MvrManagerClcaIF, 1, auditor = auditor)
        val runner = OneAuditAssertionAuditor()

        contestRounds.forEach { contestRound ->
            if (show) println("run contest ${contestRound.contestUA.contest}")
            contestRound.assertionRounds.forEach { assertionRound ->
                val cassorter = (assertionRound.assertion as ClcaAssertion).cassorter
                val sampler = ClcaWithoutReplacement(contestRound.contestUA.id, true, cvrPairs, cassorter, allowReset = false)
                if (show) println("  run assertion ${assertionRound.assertion} reported Margin= ${mean2margin(cassorter.assorter.reportedMargin())}")

                val result: TestH0Result = runner.run(
                    workflow.auditConfig(),
                    contestRound.contestUA.contest,
                    assertionRound,
                    sampler,
                    1,
                )
                // assertEquals(TestH0Status.StatRejectNull, result.status)
                if (show) println("    sampleCount = ${result.sampleCount} poolCount = ${sampler.poolCount()} maxIdx=${sampler.maxSampleIndexUsed()} status = ${result.status}\n")
            }
        }
    }
}

// TODO put in testFixtures?
class MvrManagerCardsSingleRound(val sortedCardFile: String, val maxSamples: Int = -1) : MvrManagerClcaIF {

    override fun Nballots(contestUA: ContestUnderAudit): Int {
        TODO("Not yet implemented")
    }

    override fun sortedCards(): Iterator<AuditableCard> {
        TODO("Not yet implemented")
    }

    override fun makeCvrPairsForRound(): List<Pair<Cvr, Cvr>> {
        val cvrs = mutableListOf<Cvr>()
        val cardIter = cardIter()
        var count = 0
        var countPool = 0
        while (cardIter.hasNext() && (maxSamples < 0 || count < maxSamples)) {
            val cvr = cardIter.next().cvr()
            cvrs.add(cvr)
            count++
            if (cvr.poolId != null) countPool++
        }
        println("makeCvrPairsForRound: count=$count poolCount=$countPool")
        return cvrs.zip(cvrs)
    }

    private fun cardIter(): Iterator<AuditableCard> = readCardsCsvIterator(sortedCardFile)
}