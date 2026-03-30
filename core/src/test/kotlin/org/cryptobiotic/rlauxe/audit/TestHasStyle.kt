package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.betting.TausRates
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.estimate.EstimateAudit
import org.cryptobiotic.rlauxe.estimate.MultiContestCombineData
import org.cryptobiotic.rlauxe.estimate.removeContestsAndSample
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
import org.cryptobiotic.rlauxe.workflow.CreateElectionFromCards
import org.cryptobiotic.rlauxe.workflow.CreateElectionFromCvrs
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

private val showDetails = false

// replicate examples in MoreStyles paper
// was using hasStyle, now removed
class TestHasStyle {

    @Test
    fun testHasStyleClcaSingleCard() {
        // p 6.
        // Suppose N = 10,000, p = 0.1, and mB = 0.1 = mS . For contest
        // B, there are 5,500 reported votes for the winner and 4,500 reported votes for the loser; for
        // contest S there are 550 votes for the winner and 450 votes for the loser.

        val hasStyle = false // TODO hasStyle=false

        val contestB = Contest(
            ContestInfo("B", 1, mapOf("Wes" to 1, "Les" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 5500, 2 to 4500),
            10000,
            10000
        )
        assertEquals(.1, contestB.reportedMargin(1, 2))

        ////////

        val contestS = Contest(
            ContestInfo("S", 2, mapOf("Del" to 1, "Mel" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 550, 2 to 450),
            1000,
            1000
        )
        assertEquals(0.1, contestS.reportedMargin(1, 2))

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

        val cardStyles = if (hasStyle) emptyList()
            else listOf(Batch("all",  1, contests.map{ it.id }.toIntArray(), hasStyle))

        val topdir = "$testdataDir/persist/hasStyle/testHasStyleClcaSingleCard"
        val auditRound = createAndRunTestAuditCards("testHasStyleClcaSingleCard", topdir, AuditType.CLCA, contests, emptyList(), hasStyle, testCards, cardStyles)

        println("==========================")
        println("testHasStyleClcaSingleCard hasStyle=${hasStyle} audit estimates we need ${auditRound.nmvrs}")
        auditRound.contestRounds.forEach { round ->
            println(" *** contest ${round.contestUA.name} wants ${round.estMvrs} mvrs")
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
        assertEquals(.1, contestB.reportedMargin(1, 2))

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
        assertEquals(0.1, contestS.reportedMargin(1, 2))

        val contest3 = Contest(
            ContestInfo("3", 3, mapOf("Jim" to 1, "John" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 5500, 2 to 4500),
            10000,
            10000
        )
        assertEquals(0.1, contestS.reportedMargin(1, 2))

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

        val cardStyles = if (hasStyle) emptyList()
            else listOf(Batch("all",  1, contests.map{ it.id }.toIntArray(), hasStyle))

        val topdir = "$testdataDir/persist/hasStyle/testHasStyleClcaMultiCard"
        val auditRound = createAndRunTestAuditCards("testHasStyleClcaMultiCard", topdir, AuditType.CLCA, contests, listOf(3), hasStyle, allCards, cardStyles)

        println("==========================")
        println("testHasStyleClcaMultiCard hasStyle=${hasStyle} audit estimates we need ${auditRound.nmvrs}")
        auditRound.contestRounds.forEach { round ->
            println(" *** contest ${round.contestUA.name} wants ${round.estMvrs} mvrs")
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
        assertEquals(.1, contestB.reportedMargin(1, 2))

        ////////

        val contestS = Contest(
            ContestInfo("S", 2, mapOf("Del" to 1, "Mel" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 1650, 2 to 1350),
            3000, // p = 0.3,
            3000
        )
        assertEquals(0.1, contestS.reportedMargin(1, 2))

        val testData = MultiContestCombineData(listOf(contestB, contestS), contestB.Nc, poolId=1)
        val testCvrs = testData.makeCvrsFromContests()
        val contests = listOf(contestB, contestS)

        // polling audits always must put in the possible contests
        val cardStyles = listOf(Batch("single", 1, contests.map{ it.id}.toIntArray(), hasStyle))

        val topdir = "$testdataDir/persist/hasStyle/testHasStylePollingSingleCard"
        val auditRound = createAndRunTestAuditCvrs("testHasStylePollingSingleCard", topdir, AuditType.POLLING, contests, hasStyle, testCvrs, cardStyles)

        println("==========================")
        println("testHasStylePollingSingleCard hasStyle=${hasStyle} audit estimates we need ${auditRound.nmvrs}")
        auditRound.contestRounds.forEach { round ->
            println(" *** contest ${round.contestUA.name} wants ${round.estMvrs} mvrs")
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
        assertEquals(.1, contestB.reportedMargin(1, 2))

        // card 1
        val testData1 = MultiContestCombineData(listOf(contestB), contestB.Nc)
        val testCvrs1 = testData1.makeCardsFromContests(cardStyle="all")

        ////////

        val contestS = Contest(
            ContestInfo("S", 2, mapOf("Del" to 1, "Mel" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 1650, 2 to 1350),
            3000, // p = 0.3,
            3000
        )
        assertEquals(0.1, contestS.reportedMargin(1, 2))

        val contest3 = Contest(
            ContestInfo("3", 3, mapOf("Jim" to 1, "John" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 5500, 2 to 4500),
            10000,
            10000
        )
        assertEquals(0.1, contestS.reportedMargin(1, 2))

        // card 2
        val testData2 = MultiContestCombineData(listOf(contest3, contestS), contest3.Nc)
        val testCvrs2 = testData2.makeCardsFromContests(testCvrs1.size, cardStyle="all")

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

        // polling audits always must put in the possible contests
        val cardStyles = listOf(Batch("all",  1, contests.map{ it.id }.toIntArray(), hasStyle))

        // make the audit
        val topdir = "$testdataDir/persist/hasStyle/testHasStylePollingMultiCard"
        val auditRound = createAndRunTestAuditCards("testHasStylePollingMultiCard", topdir, AuditType.POLLING, contests, listOf(3), hasStyle, allCvrs, cardStyles)

        // run the audit rounds
        println("==========================")
        println("testHasStylePollingMultiCard hasStyle=${hasStyle} audit estimates we need ${auditRound.nmvrs}")
        auditRound.contestRounds.forEach { round ->
            println(" *** contest ${round.contestUA.name} wants ${round.estMvrs} mvrs")
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

    fun createAndRunTestAuditCvrs(name:String, topdir: String, auditType: AuditType, contests: List<Contest>, hasStyle: Boolean,
                                  testCvrs: List<Cvr>, cardStyles:List<BatchIF>?): AuditRoundIF {

        // We find sample sizes for a risk limit of 0.05 on the assumption that the rate of one-vote overstatements will be 0.001.
        // val errorRates = PluralityErrorRates(0.0, 0.001, 0.0, 0.0, )

        val infos = contests.map{ it.info }.associateBy { it.id }
        val cardIter = Closer( mvrsToAuditableCardsList(auditType, testCvrs, cardStyles, null).iterator())
        val tabs = tabulateAuditableCards(cardIter, infos).toSortedMap()
        if (showDetails) tabs.forEach { println(it) }

        val contestsUA = contests.map {
            val Nb = tabs[it.id]?.ncardsTabulated ?: throw RuntimeException("Contest ${it.id} not found")
            ContestWithAssertions(it, true, NpopIn=Nb).addStandardAssertions()
        }

        val election =
            CreateElectionFromCvrs(
                name, contestsUA, testCvrs, auditType = auditType, cardPools = null,
                batches = cardStyles, mvrSource = MvrSource.testPrivateMvrs
            )

        val auditdir = "$topdir/audit"
        createElectionRecord(election, auditDir = auditdir)

        val config = Config.from(election.electionInfo(), nsimTrials = 100,
            apriori = TausRates(mapOf("win-oth" to .001)))

        createAuditRecord(config, election, auditDir = auditdir)
        startFirstRound(auditdir)

        return runTestPersistedAudit(config, topdir, contestsUA)
    }

    fun createAndRunTestAuditCards(name:String, topdir: String, auditType: AuditType, contests: List<Contest>, skipContests: List<Int>, hasStyle: Boolean,
                                   testCards: List<AuditableCard>, cardStyles:List<BatchIF>): AuditRoundIF {

        // We find sample sizes for a risk limit of 0.05 on the assumption that the rate of one-vote overstatements will be 0.001.
        // val errorRates = PluralityErrorRates(0.0, 0.001, 0.0, 0.0, )

        val infos = contests.map{ it.info }.associateBy { it.id }
        val cardIter = Closer(testCards.iterator())
        val tabs = tabulateAuditableCards(cardIter, infos)
        if (showDetails) tabs.forEach { println(it) }

        val contestsUA = contests.map {
            val Nb = tabs[it.id]?.ncardsTabulated ?:
                throw RuntimeException("Contest ${it.id} not found")
            ContestWithAssertions(it, isClca=true, NpopIn=Nb).addStandardAssertions()
        }

        val election =
            CreateElectionFromCards(
                name, contestsUA, testCards, cardPools = null, cardStyles = cardStyles, auditType,
                mvrSource = MvrSource.real
            )

        val auditdir = "$topdir/audit"
        createElectionRecord(election, auditDir = auditdir)

        val config = Config.from(election.electionInfo(), nsimTrials = 100,
            apriori = TausRates(mapOf("win-oth" to .001)))

        createAuditRecord(config, election, auditDir = auditdir)
        startFirstRound(auditdir)

        return runTestPersistedAudit(config, topdir, contestsUA)
    }
}

private fun runTestPersistedAudit(config: Config, topdir: String, wantAudit: List<ContestWithAssertions>): AuditRoundIF {
    val auditdir = "$topdir/audit"

    // TODO
    val verifyResults = RunVerifyContests.runVerifyContests(auditdir, null, show = true)
    if (showDetails) print(verifyResults)
    if (verifyResults.hasErrors) {
        print(verifyResults)
        fail()
    }

    val rlauxAudit = PersistedWorkflow.readFrom(auditdir)!!
    val mvrManager = rlauxAudit.mvrManager()
    val contestRounds = wantAudit.map { ContestRound(it, 1) }
    val auditRound = AuditRound(1, contestRounds = contestRounds, samplePrns = emptyList())

    val optimistic = EstimateAudit(config,  auditRound.roundIdx, auditRound.contestRounds, mvrManager.pools(), mvrManager.batches(), mvrManager.sortedManifest())
    optimistic.run()

    removeContestsAndSample(
        config.round.sampling,
        mvrManager.sortedManifest(),
        auditRound,
        emptySet(),
    )

    val nextRound = rlauxAudit.startNewRound(quiet = false)
    return nextRound
}
