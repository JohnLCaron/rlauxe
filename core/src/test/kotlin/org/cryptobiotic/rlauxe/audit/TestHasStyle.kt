package org.cryptobiotic.rlauxe.audit

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.core.ClcaErrorRates
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.estimate.MultiContestCombineData
import org.cryptobiotic.rlauxe.estimate.estimateSampleSizes
import org.cryptobiotic.rlauxe.estimate.sampleWithContestCutoff
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

private val showDetails = false

// replicate examples in MoreStyles paper
class TestHasStyle {

    @Test
    fun testHasStyleClcaSingleCard() {
        // p 6.
        // Suppose N = 10,000, p = 0.1, and mB = 0.1 = mS . For contest
        // B, there are 5,500 reported votes for the winner and 4,500 reported votes for the loser; for
        // contest S there are 550 votes for the winner and 450 votes for the loser.

        val hasStyle = false

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

        val poolId = if (hasStyle) null else 1
        val testData = MultiContestCombineData(listOf(contestB, contestS), contestB.Nc, poolId = poolId)
        val testCards = testData.makeCardsFromContests()

        val contests = listOf(contestB, contestS)
        val infos = contests.map{ it.info }.associateBy { it.id }
        val tabs = tabulateAuditableCards(Closer(testCards.iterator()), infos).toSortedMap()
        if (showDetails) tabs.forEach { println(it) }
        contests.forEach { contest ->
            assertEquals(contest.votes, tabs[contest.id]!!.votes)
        }

        val cardStyles = if (hasStyle) null
            else listOf(CardStyle("all", 1, contests.map{ it.name}, contests.map{ it.id}, null))

        val topdir = "/home/stormy/rla/persist/testHasStyleClcaSingleCard"
        val auditRound = createAndRunTestAuditCards(topdir, false, contests, emptyList(), hasStyle, testCards, cardStyles)
        println("==========================")
        println("testHasStyleClcaSingleCard hasStyle=${hasStyle} audit estimates we need ${auditRound.nmvrs}")
        auditRound.contestRounds.forEach { round ->
            println(" *** contest ${round.contestUA.name} wants ${round.estSampleSize} mvrs")
        }

        // testHasStyleClcaSingleCard hasStyle=false audit estimates we need 550
        // *** contest B wants 59 mvrs
        // *** contest S wants 550 mvrs
        //
        // testOneCardBallots hasStyle=true audit estimates we need 111
        // *** contest B wants 59 mvrs
        // *** contest S wants 57 mvrs

        // testOneCardBallots hasStyle=false audit estimates we need 549
        //        *** contest B wants 59 mvrs
        //        *** contest S wants 549 mvrs
        // hasStyle=true audit estimates we need 111
        // *** contest B wants 59 mvrs
        // *** contest S wants 57 mvrs
        //
        // paper no CSD: "Contest B has 64 cards, and contest S has 721"
        // paper with CSD: "Contest B has 64 cards, and contest S has 122"
    }

    @Test
    fun testHasStyleClcaMultiCard() {
        // p 9.
        // Now suppose that each ballot consists of c > 1 cards. For simplicity, suppose that every
        // voter casts all c cards of their ballot. Contest B is on all N ballots and on N of the N c cards.
        // Contest S is on N p of the N c cards.
        //
        // Assume c = 2, and that S is on a different card than B.

        // Suppose N = 10,000, p = 0.1, and mB = 0.1 = mS . For contest
        // B, there are 5,500 reported votes for the winner and 4,500 reported votes for the loser; for
        // contest S there are 550 votes for the winner and 450 votes for the loser.

        val hasStyle = false
        val poolId = if (hasStyle) null else 1


        val contestB = Contest(
            ContestInfo("B", 1, mapOf("Wes" to 1, "Les" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 5500, 2 to 4500),
            10000,
            10000
        )
        assertEquals(.1, contestB.margin(1, 2))

        // card 1
        val testData1 = MultiContestCombineData(listOf(contestB), contestB.Nc, poolId = poolId)
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
        val testData2 = MultiContestCombineData(listOf(contest3, contestS), contest3.Nc, poolId = poolId)
        val testCvrs2 = testData2.makeCardsFromContests(testCvrs1.size)

        val allCards = mutableListOf<AuditableCard>()
        allCards.addAll(testCvrs1)
        allCards.addAll(testCvrs2)
        assertEquals(20000, allCards.size)
        allCards.shuffle(Random)

        val contests = listOf(contestB, contestS, contest3)
        val infos = contests.map{ it.info }.associateBy { it.id }
        val tabs = tabulateAuditableCards(Closer(allCards.iterator()), infos).toSortedMap()
        if (showDetails) tabs.forEach { println(it) }
        contests.forEach { contest ->
            assertEquals(contest.votes, tabs[contest.id]!!.votes)
        }

        val cardStyles = if (hasStyle) null
            else listOf(CardStyle("all", 1, contests.map{ it.name}, contests.map{ it.id}, null))

        val topdir = "/home/stormy/rla/persist/testHasStyleClcaMultiCard"
        val auditRound = createAndRunTestAuditCards(topdir, false, contests, listOf(3), hasStyle, allCards, cardStyles)
        println("==========================")
        println("testHasStyleClcaMultiCard hasStyle=${hasStyle} audit estimates we need ${auditRound.nmvrs}")
        auditRound.contestRounds.forEach { round ->
            println(" *** contest ${round.contestUA.name} wants ${round.estSampleSize} mvrs")
        }

        // testHasStyleClcaMultiCard hasStyle=false audit estimates we need 830
        // *** contest B wants 118 mvrs
        // *** contest S wants 830 mvrs
        //
        // testHasStyleClcaMultiCard hasStyle=true audit estimates we need 116
        // *** contest B wants 59 mvrs
        // *** contest S wants 57 mvrs
        //
        // paper no CSD: "examine 1,712" (2x) we agree on diluted margin, but he has ρ/0.005 = 1,712 cards.
        // paper with CSD: "need 128"
    }

    @Test
    fun testHasStylePollingSingleCard() {
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

        val hasStyle = false

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

        val testData = MultiContestCombineData(listOf(contestB, contestS), contestB.Nc) // TODO use poolId ??
        val testCvrs = testData.makeCvrsFromContests()
        val contests = listOf(contestB, contestS)

        val config = AuditConfig(AuditType.POLLING, hasStyle = hasStyle, seed = 12356667890L, nsimEst = 100, pollingConfig = PollingConfig())

        val cards = mutableListOf<AuditableCard>()
        CvrsWithStylesToCards(config.auditType, hasStyle,
            Closer(testCvrs.iterator()),
            null,
            styles = null,
        ).forEach { cards.add(it)}

        val infos = contests.map{ it.info }.associateBy { it.id }
        val tabs = tabulateCvrs(testCvrs.iterator(), infos).toSortedMap()
        if (showDetails) tabs.forEach { println(it) }
        contests.forEach { contest ->
            assertEquals(contest.votes, tabs[contest.id]!!.votes)
        }

        val topdir = "/home/stormy/rla/persist/testHasStylePollingSingleCard"
        val auditRound = createAndRunTestAuditCvrs(topdir, true, contests, emptyList(), hasStyle, testCvrs)
        println("==========================")
        println("testHasStylePollingSingleCard hasStyle=${hasStyle} audit estimates we need ${auditRound.nmvrs}")
        auditRound.contestRounds.forEach { round ->
            println(" *** contest ${round.contestUA.name} wants ${round.estSampleSize} mvrs")
        }
        //testHasStylePollingSingleCard hasStyle=false audit estimates we need 2061
        // *** contest B wants 872 mvrs
        // *** contest S wants 2061 mvrs
        //
        // testHasStylePollingSingleCard hasStyle=true audit estimates we need 1526
        // *** contest B wants 1031 mvrs
        // *** contest S wants 828 mvrs

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
    fun testHasStylePollingMultiCard() {
        val hasStyle = false

        val contestB = Contest(
            ContestInfo("B", 1, mapOf("Wes" to 1, "Les" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 5500, 2 to 4500),
            10000,
            10000
        )
        assertEquals(.1, contestB.margin(1, 2))

        // card 1
        val testData1 = MultiContestCombineData(listOf(contestB), contestB.Nc)
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
        val testData2 = MultiContestCombineData(listOf(contest3, contestS), contest3.Nc)
        val testCvrs2 = testData2.makeCardsFromContests(testCvrs1.size)

        val allCvrs = mutableListOf<AuditableCard>()
        allCvrs.addAll(testCvrs1)
        allCvrs.addAll(testCvrs2)
        assertEquals(20000, allCvrs.size)
        allCvrs.shuffle(Random)

        val contests = listOf(contestB, contestS, contest3)
        val infos = contests.map{ it.info }.associateBy { it.id }
        val tabs = tabulateAuditableCards(Closer(allCvrs.iterator()), infos).toSortedMap()
        if (showDetails) tabs.forEach { println(it) }
        contests.forEach { contest ->
            assertEquals(contest.votes, tabs[contest.id]!!.votes)
        }

        val topdir = "/home/stormy/rla/persist/testHasStylePollingMultiCard"
        val auditRound = createAndRunTestAuditCards(topdir, true, contests, listOf(3), hasStyle, allCvrs)
        println("==========================")
        println("testHasStylePollingMultiCard hasStyle=${hasStyle} audit estimates we need ${auditRound.nmvrs}")
        auditRound.contestRounds.forEach { round ->
            println(" *** contest ${round.contestUA.name} wants ${round.estSampleSize} mvrs")
        }
        // testHasStylePollingMultiCard hasStyle=false audit estimates we need 2769
        // *** contest B wants 2275 mvrs
        // *** contest S wants 2769 mvrs
        // testHasStylePollingMultiCard hasStyle=true audit estimates we need 1800
        // *** contest B wants 1063 mvrs
        // *** contest S wants 737 mvrs
        //
        // testMultiCardBallots hasStyle=false audit estimates we need 2733
        // *** contest B wants 2045 mvrs
        // *** contest S wants 2733 mvrs
        // testMultiCardBallots hasStyle=true audit estimates we need 1785
        // *** contest B wants 1027 mvrs
        // *** contest S wants 758 mvrs
        //
        // paper no CSD: "absent CSD we would expect to sample (2/0.3) × 608 = 4,053 cards" we agree on diluted margin, but he has ρ/0.005 = 1,712 cards.
        // paper with CSD: "With partial CSD, but no information about which contests are contained on an individual
        //  card, we would expect to sample 2 × 608 + 2 × 608 = 2,432 ballot cards if the contests are on different cards."
    }

    fun createAndRunTestAuditCvrs(topdir: String, isPolling: Boolean, contests: List<Contest>, skipContests: List<Int>, hasStyle: Boolean,
                                  testCvrs: List<Cvr>, cardStyles:List<CardStyleIF>? = null ): AuditRound {

        // We find sample sizes for a risk limit of 0.05 on the assumption that the rate of one-vote overstatements will be 0.001.
        val errorRates = ClcaErrorRates(0.0, 0.001, 0.0, 0.0, )
        val config = if (isPolling) {
            AuditConfig(AuditType.POLLING, hasStyle = hasStyle, seed = 12356667890L, nsimEst = 100, skipContests=skipContests,
                pollingConfig = PollingConfig())
        } else {
            AuditConfig(AuditType.CLCA, hasStyle = hasStyle, seed = 12356667890L, nsimEst = 100, skipContests=skipContests,
                clcaConfig = ClcaConfig(strategy= ClcaStrategyType.apriori, errorRates=errorRates))
        }

        val infos = contests.map{ it.info }.associateBy { it.id }
        val cardIter = CvrsWithStylesToCards(config.auditType, hasStyle,
            Closer(testCvrs.iterator()),
            null,
            styles = cardStyles,
        )
        val tabs = tabulateAuditableCards(cardIter, infos).toSortedMap()
        if (showDetails) tabs.forEach { println(it) }

        val contestsUA = contests.map {
            val Nb = tabs[it.id]?.ncards ?: throw RuntimeException("Contest ${it.id} not found")
            ContestUnderAudit(it, true, true, Nbin=Nb).addStandardAssertions()
        }

        val election =
            CreateElectionFromCvrs(contestsUA, testCvrs, cardPools = null, cardStyles = cardStyles, config = config)

        CreateAudit("testOneCardBallots", topdir, config, election, clear = true)

        return runTestPersistedAudit(topdir, contestsUA)
    }

    fun createAndRunTestAuditCards(topdir: String, isPolling: Boolean, contests: List<Contest>, skipContests: List<Int>, hasStyle: Boolean,
                                   testCards: List<AuditableCard>, cardStyles:List<CardStyleIF>? = null, ): AuditRound {

        // We find sample sizes for a risk limit of 0.05 on the assumption that the rate of one-vote overstatements will be 0.001.
        val errorRates = ClcaErrorRates(0.0, 0.001, 0.0, 0.0, )
        val config = if (isPolling) {
            AuditConfig(AuditType.POLLING, hasStyle = hasStyle, seed = 12356667890L, nsimEst = 100, skipContests=skipContests,
                pollingConfig = PollingConfig())
        } else {
            AuditConfig(AuditType.CLCA, hasStyle = hasStyle, seed = 12356667890L, nsimEst = 100, skipContests=skipContests,
                clcaConfig = ClcaConfig(strategy= ClcaStrategyType.apriori, errorRates=errorRates))
        }

        val infos = contests.map{ it.info }.associateBy { it.id }
        val cardIter = CardsWithStylesToCards(config.auditType, hasStyle,
            Closer(testCards.iterator()),
            null,
            styles = cardStyles,
        )
        val tabs = tabulateAuditableCards(cardIter, infos)
        if (showDetails) tabs.forEach { println(it) }

        val contestsUA = contests.map {
            val Nb = tabs[it.id]?.ncards ?: throw RuntimeException("Contest ${it.id} not found")
            ContestUnderAudit(it, true, true, Nbin=Nb).addStandardAssertions()
        }

        // class TestCreateElection (
        //    val contestsUA: List<ContestUnderAudit>,
        //    val cvrs: List<Cvr>,
        //    val cardPools: List<CardPoolIF>? = null,
        //    val config: AuditConfig,
        //):
        val election =
            CreateElectionFromCards(contestsUA, testCards, cardPools = null, cardStyles = cardStyles, config = config)

        CreateAudit("testOneCardBallots", topdir, config, election, clear = true)

        return runTestPersistedAudit(topdir, contestsUA)
    }
}

fun runTestPersistedAudit(topdir: String, wantAudit: List<ContestUnderAudit>): AuditRound {
    val auditdir = "$topdir/audit"
    val publisher = Publisher(auditdir)
    val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
    writeSortedCardsExternalSort(topdir, publisher, config.seed)

    // TODO
    val verifyResults = RunVerifyContests.runVerifyContests(auditdir, null, show = true)
    if (showDetails) print(verifyResults)
    if (verifyResults.hasErrors) fail()

    val rlauxAudit = PersistedWorkflow(auditdir, useTest=true) // useTest ??
    val mvrManager = rlauxAudit.mvrManager()
    val contestRounds = wantAudit.map { ContestRound(it, 1) }
    val auditRound = AuditRound(1, contestRounds = contestRounds, samplePrns = emptyList())

    estimateSampleSizes(
        config,
        auditRound,
        cardManifest = mvrManager.sortedCards(),
        // nthreads=1,
    )

    sampleWithContestCutoff(
        config,
        mvrManager,
        auditRound,
        emptySet(),
        quiet = false
    )

    val nextRound = rlauxAudit.startNewRound(quiet = false)
    return nextRound
}
