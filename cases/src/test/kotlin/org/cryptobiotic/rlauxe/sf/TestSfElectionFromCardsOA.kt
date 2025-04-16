package org.cryptobiotic.rlauxe.sf

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.BallotPool
import org.cryptobiotic.rlauxe.persist.PersistentAudit
import org.cryptobiotic.rlauxe.persist.csv.CvrIteratorAdapter
import org.cryptobiotic.rlauxe.persist.csv.readBallotPoolCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.workflow.OneAuditAssertionAuditor
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

        // data class ContestInfo(
        //    val name: String,
        //    val id: Int,
        //    val candidateNames: Map<String, Int>, // candidate name -> candidate id
        //    val choiceFunction: SocialChoiceFunction,
        //    val nwinners: Int = 1,
        //    val minFraction: Double? = null, // supermajority only.
        //)
        val wantContest = 2
        val winner = 11
        val loser = 9
        val info = ContestInfo("test", wantContest, mapOf(winner.toString() to winner, loser.toString() to loser), SocialChoiceFunction.PLURALITY)
        // totalCount = total=19175, counts={9=5801, 10=51, 11=11530, 12=307, 13=39, 14=63, 15=44, 16=399, 17=225, 230=210} reportedMargin = 0.29877444589308993
        val expectedMargin = (11530 - 5801)/19175.toDouble()

        val testAssort = PluralityAssorter(info, winner, loser, expectedMargin)
        val welford = Welford()

        val cvrsCount = ContestCount()
        val cardIter = CvrIteratorAdapter(readCardsCsvIterator(cardFile))
        while (cardIter.hasNext()) {
            val cvr = cardIter.next()
            if (cvr.poolId == null) { // only want non-pool cards
                if (cvr.votes[wantContest] != null) {
                    welford.update(testAssort.assort(cvr))
                    cvrsCount.ncards++
                    mergeVotes(cvr.votes[wantContest]!!, cvrsCount.counts)
                }
            }
        }
        //    2 total=19175, counts={2=15932, 1=3243}
        //   contest2 = total=19175, counts={9=5087, 10=44, 11=9201, 12=283, 13=35, 14=57, 15=37, 16=358, 17=190, 230=189}
        println(" assort ${testAssort.desc()}")
        println("   welford=${welford.count} mean=${welford.mean} margin=${mean2margin(welford.mean)}")
        println("   cvrsCount ${cvrsCount} reportedMargin = ${cvrsCount.reportedMargin(winner, loser)}")
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
        println("   pool counts = $poolCount reportedMargin = ${poolCount.reportedMargin(winner, loser)}")

        val totalCount = ContestCount()
        mergeVotes(cvrsCount.counts, totalCount.counts)
        mergeVotes(poolCount.counts, totalCount.counts)
        totalCount.ncards = cvrsCount.ncards + poolCount.ncards
        println("   totalCount = $totalCount reportedMargin = ${totalCount.reportedMargin(winner, loser)}")

        // assort  winner=11 loser=9 reportedMargin=0.2988
        //   welford=15932 mean=0.6291112226964624 margin=0.2582224453929247
        //   cvrsCount total=15932, counts={9=5087, 10=44, 11=9201, 12=283, 13=35, 14=57, 15=37, 16=358, 17=190, 230=189} reportedMargin = 0.2582224453929199
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

    @Test
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
            println("run contest ${contestRound.contestUA.contest}")
            contestRound.assertionRounds.forEach { assertionRound ->
                val cassorter = (assertionRound.assertion as ClcaAssertion).cassorter
                val sampler = ClcaWithoutReplacement(contestRound.contestUA.id, true, cvrPairs, cassorter, allowReset = false)
                println("  run assertion ${assertionRound.assertion} cvrMargin= ${mean2margin(cassorter.meanAssort())}")

                val result: TestH0Result = runner.run(
                    workflow.auditConfig(),
                    contestRound.contestUA.contest,
                    assertionRound,
                    sampler,
                    1,
                )
                // assertEquals(TestH0Status.StatRejectNull, result.status)
                println("    sampleCount = ${result.sampleCount} poolCount = ${sampler.poolCount()} maxIdx=${sampler.maxSampleIndexUsed()} status = ${result.status}\n")
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