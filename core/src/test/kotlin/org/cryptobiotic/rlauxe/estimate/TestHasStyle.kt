package org.cryptobiotic.rlauxe.estimate

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardLocationManifest
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ClcaStrategyType
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.audit.CreateAudit
import org.cryptobiotic.rlauxe.audit.CreateElectionIF
import org.cryptobiotic.rlauxe.audit.PollingConfig
import org.cryptobiotic.rlauxe.audit.writeSortedCardsExternalSort
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.core.ClcaErrorRates
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

// replicate examples in MoreStyles paper; use Cards not Cvrs
class TestHasStyle {

    @Test
    fun testOneCardBallots() {
        // p 6.
        // Suppose N = 10,000, p = 0.1, and mB = 0.1 = mS . For contest
        // B, there are 5,500 reported votes for the winner and 4,500 reported votes for the loser; for
        // contest S there are 550 votes for the winner and 450 votes for the loser.

        val contestB = Contest(
            ContestInfo("B", 1, mapOf("Wes" to 1, "Les" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 5500, 2 to 4500),
            10000,
            10000
        )
        assertEquals(.1, contestB.margin(1, 2))

        ////////

        val contestS = Contest(
            ContestInfo("S", 2, mapOf("Del" to 1, "Mel" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 550, 2 to 450),
            1000,
            1000
        )
        assertEquals(0.1, contestS.margin(1, 2))

        val hasStyle = true
        val testData = MultiContestCombineData(listOf(contestB, contestS), contestB.Nc, hasStyle = hasStyle)
        val testCards = testData.makeCardsFromContests()

        val contests = listOf(contestB, contestS)
        val infos = contests.map{ it.info }.associateBy { it.id }
        val tabs = tabulateAuditableCards(Closer(testCards.iterator()), infos).toSortedMap()
        println(tabs)
        contests.forEach { contest ->
            if (contest.votes != tabs[contest.id]!!.votes)
                println("heh")
            assertEquals(contest.votes, tabs[contest.id]!!.votes)
        }

        val topdir = "/home/stormy/rla/persist/testOneCardBallots"
        val auditRound = createAndRunTestAudit(topdir, false, contests, emptyList(), hasStyle, testCards)
        println("testOneCardBallots hasStyle=${hasStyle} audit estimates we need ${auditRound.nmvrs}")
        auditRound.contestRounds.forEach { round ->
            println(" *** contest ${round.contestUA.name} wants ${round.estSampleSize} mvrs")
        }

        // testOneCardBallots hasStyle=false audit estimates we need 549
        //        *** contest B wants 59 mvrs
        //        *** contest S wants 549 mvrs
        //
        // hasStyle=true audit estimates we need 111
        // *** contest B wants 59 mvrs
        // *** contest S wants 57 mvrs
        //
        // paper no CSD: "Contest B has 64 cards, and contest S has 721"
        // paper with CSD: "Contest B has 64 cards, and contest S has 122"
    }

    @Test
    fun testMultiCardBallots() {
        // p 9.
        // Now suppose that each ballot consists of c > 1 cards. For simplicity, suppose that every
        // voter casts all c cards of their ballot. Contest B is on all N ballots and on N of the N c cards.
        // Contest S is on N p of the N c cards.
        //
        // Assume c = 2, and that S is on a different card than B.

        // Suppose N = 10,000, p = 0.1, and mB = 0.1 = mS . For contest
        // B, there are 5,500 reported votes for the winner and 4,500 reported votes for the loser; for
        // contest S there are 550 votes for the winner and 450 votes for the loser.

        val contestB = Contest(
            ContestInfo("B", 1, mapOf("Wes" to 1, "Les" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 5500, 2 to 4500),
            10000,
            10000
        )
        assertEquals(.1, contestB.margin(1, 2))

        // card 1
        val hasStyle = false
        val testData1 = MultiContestCombineData(listOf(contestB), contestB.Nc, hasStyle = hasStyle)
        val testCvrs1 = testData1.makeCardsFromContests()

        ////////

        val contestS = Contest(
            ContestInfo("S", 2, mapOf("Del" to 1, "Mel" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 550, 2 to 450),
            1000,
            1000
        )
        assertEquals(0.1, contestS.margin(1, 2))

        val contest3 = Contest(
            ContestInfo("3", 3, mapOf("Jim" to 1, "John" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 5500, 2 to 4500),
            10000,
            10000
        )
        assertEquals(0.1, contestS.margin(1, 2))

        // card 2
        val testData2 = MultiContestCombineData(listOf(contest3, contestS), contest3.Nc, hasStyle = hasStyle)
        val testCvrs2 = testData2.makeCardsFromContests(testCvrs1.size)

        val allCards = mutableListOf<AuditableCard>()
        allCards.addAll(testCvrs1)
        allCards.addAll(testCvrs2)
        assertEquals(20000, allCards.size)
        allCards.shuffle(kotlin.random.Random)

        val contests = listOf(contestB, contestS, contest3)
        val infos = contests.map{ it.info }.associateBy { it.id }
        val tabs = tabulateAuditableCards(Closer(allCards.iterator()), infos).toSortedMap()
        println(tabs)
        contests.forEach { contest ->
            assertEquals(contest.votes, tabs[contest.id]!!.votes)
        }

        val topdir = "/home/stormy/rla/persist/testMultiCardBallots"
        val auditRound = createAndRunTestAudit(topdir, false, contests, listOf(3), hasStyle, allCards)
        println("testMultiCardBallots hasStyle=${hasStyle} audit estimates we need ${auditRound.nmvrs}")
        auditRound.contestRounds.forEach { round ->
            println(" *** contest ${round.contestUA.name} wants ${round.estSampleSize} mvrs")
        }

        // testMultiCardBallots hasStyle=false audit estimates we need 845
        // *** contest B wants 118 mvrs
        // *** contest S wants 845 mvrs
        //
        // testMultiCardBallots hasStyle=true audit estimates we need 116
        // *** contest B wants 59 mvrs
        // *** contest S wants 57 mvrs
        //
        // paper no CSD: "examine 1,712" (2x) we agree on diluted margin, but he has ρ/0.005 = 1,712 cards.
        // paper with CSD: "need 128"
    }


    @Test
    fun testPollingOneCard() {
        // p 11.
        // Consider again auditing contests B and S with margins MB and MS (in votes), N ballots
        // cast each consisting of c cards, contest B on N of the N c cards and S is on pN of the cards
        //
        // Suppose we know which ballots contain S but not which particular cards contain S, and
        // that the c cards comprising each ballot are kept in the same container.
        //
        // For a risk limit of 5% and a margin of 10%, using BRAVO, we would expect to sample
        // 608 cards if we could target the sample. Consider the example N = 10,000, p = 0.3, MB =
        // 0.1 = MS . If c = 2, absent CSD we would expect to sample (2/0.3) × 608 = 4,053 cards.
        //
        // With partial CSD, but no information about which contests are contained on an individual
        // card, we would expect to sample (1 − 0.3)2 × 608 + 2 × 608 = 2,067 cards if the contests are
        // on the same card and 2 × 608 + 2 × 608 = 2,432 ballot cards if the contests are on different
        // cards. In either case, using CSD reduces the sample size by roughly half.

        val contestB = Contest(
            ContestInfo("B", 1, mapOf("Wes" to 1, "Les" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 5500, 2 to 4500),
            10000,
            10000
        )
        assertEquals(.1, contestB.margin(1, 2))

        ////////

        val contestS = Contest(
            ContestInfo("S", 2, mapOf("Del" to 1, "Mel" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 1650, 2 to 1350),
            3000, // p = 0.3,
            3000
        )
        assertEquals(0.1, contestS.margin(1, 2))

        val hasStyle = false
        val testData = MultiContestCombineData(listOf(contestB, contestS), contestB.Nc, hasStyle = hasStyle)
        val testCvrs = testData.makeCardsFromContests()

        val contests = listOf(contestB, contestS)
        val infos = contests.map{ it.info }.associateBy { it.id }
        val tabs = tabulateAuditableCards(Closer(testCvrs.iterator()), infos).toSortedMap()
        println(tabs)
        contests.forEach { contest ->
            assertEquals(contest.votes, tabs[contest.id]!!.votes)
        }

        val topdir = "/home/stormy/rla/persist/testPollingOneCard"
        val auditRound = createAndRunTestAudit(topdir, true, contests, emptyList(), hasStyle, testCvrs)
        println("testPollingOneCard hasStyle=${hasStyle} audit estimates we need ${auditRound.nmvrs}")
        auditRound.contestRounds.forEach { round ->
            println(" *** contest ${round.contestUA.name} wants ${round.estSampleSize} mvrs")
        }

        // testPollingOneCard hasStyle=false audit estimates we need 2170
        // *** contest B wants 1065 mvrs
        // *** contest S wants 2170 mvrs
        //
        // testPollingOneCard hasStyle=true audit estimates we need 1647
        // *** contest B wants 1259 mvrs
        // *** contest S wants 787 mvrs
        //
        // paper no CSD: "absent CSD we would expect to sample (2/0.3) × 608 = 4,053 cards" we agree on diluted margin, but he has ρ/0.005 = 1,712 cards.
        // paper with CSD: "With partial CSD, but no information about which contests are contained on an individual
        //  card, we would expect to sample (1 − 0.3)2 × 608 + 2 × 608 = 2,067 cards if the contests are on the same card".
    }

    @Test
    fun testPollingMultiCard() {
        val hasStyle = false

        val contestB = Contest(
            ContestInfo("B", 1, mapOf("Wes" to 1, "Les" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 5500, 2 to 4500),
            10000,
            10000
        )
        assertEquals(.1, contestB.margin(1, 2))

        // card 1
        val testData1 = MultiContestCombineData(listOf(contestB), contestB.Nc, hasStyle = hasStyle)
        val testCvrs1 = testData1.makeCardsFromContests()

        ////////

        val contestS = Contest(
            ContestInfo("S", 2, mapOf("Del" to 1, "Mel" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 1650, 2 to 1350),
            3000, // p = 0.3,
            3000
        )
        assertEquals(0.1, contestS.margin(1, 2))

        val contest3 = Contest(
            ContestInfo("3", 3, mapOf("Jim" to 1, "John" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 5500, 2 to 4500),
            10000,
            10000
        )
        assertEquals(0.1, contestS.margin(1, 2))

        // card 2
        val testData2 = MultiContestCombineData(listOf(contest3, contestS), contest3.Nc, hasStyle = hasStyle)
        val testCvrs2 = testData2.makeCardsFromContests(testCvrs1.size)

        val allCvrs = mutableListOf<AuditableCard>()
        allCvrs.addAll(testCvrs1)
        allCvrs.addAll(testCvrs2)
        assertEquals(20000, allCvrs.size)
        allCvrs.shuffle(kotlin.random.Random)

        val contests = listOf(contestB, contestS, contest3)
        val infos = contests.map{ it.info }.associateBy { it.id }
        val tabs = tabulateAuditableCards(Closer(allCvrs.iterator()), infos).toSortedMap()
        println(tabs)
        contests.forEach { contest ->
            assertEquals(contest.votes, tabs[contest.id]!!.votes)
        }

        val topdir = "/home/stormy/rla/persist/testPollingMultiCard"
        val auditRound = createAndRunTestAudit(topdir, true, contests, listOf(3), hasStyle, allCvrs)
        println("testMultiCardBallots hasStyle=${hasStyle} audit estimates we need ${auditRound.nmvrs}")
        auditRound.contestRounds.forEach { round ->
            println(" *** contest ${round.contestUA.name} wants ${round.estSampleSize} mvrs")
        }

        // testMultiCardBallots hasStyle=false audit estimates we need 2733
        // *** contest B wants 2045 mvrs
        // *** contest S wants 2733 mvrs
        //
        // testMultiCardBallots hasStyle=true audit estimates we need 1785
        // *** contest B wants 1027 mvrs
        // *** contest S wants 758 mvrs
        //
        // paper no CSD: "absent CSD we would expect to sample (2/0.3) × 608 = 4,053 cards" we agree on diluted margin, but he has ρ/0.005 = 1,712 cards.
        // paper with CSD: "With partial CSD, but no information about which contests are contained on an individual
        //  card, we would expect to sample 2 × 608 + 2 × 608 = 2,432 ballot cards if the contests are on different cards."
    }

    fun createAndRunTestAudit(topdir: String, isPolling: Boolean, contests: List<Contest>, skipContests: List<Int>, hasStyle: Boolean, testCards: List<AuditableCard>): AuditRound {
        // class CvrToCardAdapter(val cvrIterator: CloseableIterator<Cvr>, val pools: Map<String, Int>? = null, startCount: Int = 0) : CloseableIterator<AuditableCard> {
        // class FromCvrNoStyle(val cvrs: CloseableIterator<Cvr>, val possibleContests: IntArray) : CloseableIterator<AuditableCard> {

        val cardManifest = if (isPolling) {
            if (hasStyle) {
                CloseableIterable { Closer(testCards.iterator()) }
            } else {
                val possibleContests = intArrayOf(1,2,3)
                val modCards = testCards.map { it.copy(possibleContests = possibleContests) }
                CloseableIterable { Closer(modCards.iterator()) }
            }
        } else if (hasStyle) {
            CloseableIterable { Closer(testCards.iterator()) }
        } else {
            val possibleContests = intArrayOf(1,2,3)
            val modCards = testCards.map { it.copy(possibleContests = possibleContests) }
            CloseableIterable { Closer(modCards.iterator()) }
        }

        val infos = contests.map{ it.info }.associateBy { it.id }
        val tabs = tabulateAuditableCards(cardManifest.iterator(), infos)

        val contestsUA = contests.map {
            val Nb = tabs[it.id]?.ncards ?: throw RuntimeException("Contest ${it.id} not found")
            ContestUnderAudit(it, true, true, Nbin=Nb).addStandardAssertions()
        }

        val election = TestCreateElection(contestsUA, hasStyle = hasStyle, cardManifest, null)

        // We find sample sizes for a risk limit of 0.05 on the assumption that the rate of one-vote overstatements will be 0.001.
        val errorRates = ClcaErrorRates(0.0, 0.001, 0.0, 0.0, )
        val config = if (isPolling) {
            AuditConfig(AuditType.POLLING, hasStyle = hasStyle, seed = 12356667890L, nsimEst = 100, skipContests=skipContests,
                pollingConfig = PollingConfig())
        } else {
            AuditConfig(AuditType.CLCA, hasStyle = hasStyle, seed = 12356667890L, nsimEst = 100, skipContests=skipContests,
                clcaConfig = ClcaConfig(strategy= ClcaStrategyType.apriori, errorRates=errorRates))
        }

        CreateAudit("testOneCardBallots", topdir, config, election, clear = true)

        return runTestPersistedAudit(topdir, contestsUA)
    }
}

class TestCreateElection (
    val contestsUA: List<ContestUnderAudit>,
    val hasStyle: Boolean,
    val cardManifest: CloseableIterable<AuditableCard>,
    val cardPools: List<CardPoolIF>? = null,
): CreateElectionIF {

    override fun cardPools() = cardPools
    override fun contestsUA() = contestsUA

    override fun cardManifest(): CardLocationManifest {
        return CardLocationManifest(cardManifest, emptyList())
    }
}

fun runTestPersistedAudit(topdir: String, wantAudit: List<ContestUnderAudit>): AuditRound {
    val auditdir = "$topdir/audit"
    val publisher = Publisher(auditdir)
    val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
    writeSortedCardsExternalSort(topdir, publisher, config.seed)

    // TODO
    val verifyResults = RunVerifyContests.runVerifyContests(auditdir, null, show = true)
    println()
    print(verifyResults)
    if (verifyResults.hasErrors) fail()

    val rlauxAudit = PersistedWorkflow(auditdir, useTest=true) // useTest ??
    val mvrManager = rlauxAudit.mvrManager()
    val contestRounds = wantAudit.map { ContestRound(it, 1) }
    val auditRound = AuditRound(1, contestRounds = contestRounds, samplePrns = emptyList())

    estimateSampleSizes(
        config,
        auditRound,
        cardManifest = if (config.auditType == AuditType.POLLING) null else mvrManager.sortedCards(),
        // nthreads=1,
    )

    sampleWithContestCutoff(
        config,
        mvrManager,
        auditRound,
        emptySet(),
        quiet = false)

    val nextRound = rlauxAudit.startNewRound(quiet = false)
    return nextRound
}
