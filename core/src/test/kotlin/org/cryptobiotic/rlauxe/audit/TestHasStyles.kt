package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.estimate.MultiContestCombineData
import org.cryptobiotic.rlauxe.estimate.MultiContestCombinePools
import org.cryptobiotic.rlauxe.estimate.MultiContestFromBallotStyles
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.TransformingIterator
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
import org.cryptobiotic.rlauxe.verify.VerifyAuditCommitment
import org.cryptobiotic.rlauxe.verify.VerifyElectionCommitment
import org.cryptobiotic.rlauxe.workflow.CreateElectionFromCards
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

private val showDetails = false

// replicate examples in MoreStyles paper

// p 6.
// There are N ballots cast in the jurisdiction, of which NB = N contain contest B and
// NS = pN < N contain contest S, where p ∈ (0, 1). So p = NS / NB the fraction of the ballots that S is on.

// "CSD" = hasStyle = true
// "absent CSD" = hasStyle = false = noStyle
// "single card" means each ballot has 1 card. on some cards theres only B, and on some cards there are both B and S (hasSingleCardStyle = false)
// "multi card" mean that each ballot consists of c > 1 cards. Contest B is on all N ballots and on N of the N * c cards.
//    Contest S is on N*p of the N*c cards. Assume c = 2, and that S is on a different card than B.

// NoStyle
//  single: Npop(B) = N, Npop(S) = N (single) or N*c (multiple)
//  multicard: Npop(B) = N*c, Npop(S) = N*c
// So theres a single pool we sample out of.

// HasStyle information for each card
//  single: Npop(B) = N, Npop(S) = p*N
//  multicard: Npop(B) = N, Npop(S) = p*N TODO CLCA has CSD

// HasBallotStyle information for each ballot
//   multicard: Npop(B) = N*c, Npop(S) = N

// HasStyle information for each multicard ballot, but the cards are mixed up and cant be individually retrieved.
// suppose the cards are labeled ballotId-cardX. There are N*c of them. The ballotId has a ballot style (list of contests).
// For our purposes there are 2 styles : (B) and (B, S)
// But maybe we need (B, T) and (B, S) for a third contest T ? Each ballot is one or the other, and I suppose S + T = B ??

class TestHasStyles {
    val contestB: Contest
    val contestS: Contest
    val contestT: Contest
    val contests: List<Contest>
    val contest3s: List<Contest>

    init {

        // Suppose N = 10,000, p = 0.1, and mB = 0.1 = mS . For contest
        // B, there are 5,500 reported votes for the winner and 4,500 reported votes for the loser; for
        // contest S there are 550 votes for the winner and 450 votes for the loser.

        contestB = Contest(
            ContestInfo("B", 1, mapOf("Wes" to 1, "Les" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 5500, 2 to 4500),
            10000,
            10000
        )
        assertEquals(0.1, contestB.reportedMargin(1, 2))

        contestS = Contest(
            ContestInfo("S", 2, mapOf("Del" to 1, "Mel" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 550, 2 to 450),
            1000,
            1000
        )
        assertEquals(0.1, contestS.reportedMargin(1, 2))

        contestT = Contest(
            ContestInfo("T", 3, mapOf("Del" to 1, "Mel" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 550, 2 to 450),
            1000,
            1000
        )
        assertEquals(0.1, contestS.reportedMargin(1, 2))

        contests = listOf(contestB, contestS)
        contest3s = listOf(contestB, contestS, contestT)
    }

    @Test
    fun doone() {
        // makeMultiCardHasBallotStylePolling() //
        makeMultiCardNoStylePolling() //
    }

    @Test
    fun testSingleCard() {
        makeSingleCardHasStyle(AuditType.CLCA) // mvrUsed = 115; paper has 122
        makeSingleCardNoStyle(AuditType.CLCA) // mvrUsed = 603; paper has 721

        makeSingleCardHasStyle(AuditType.POLLING) // mvrUsed = 825 (will vary)
        makeSingleCardNoStyle(AuditType.POLLING) // mvrUsed = 7727 (will vary)
    }

    @Test
    fun testMultiCard() {
        // If we had CSD, we would only need to sample 122 cards as before, no matter how large c is,
        // if every card that contains contest S also contains B. If contests B and S are on different
        // cards then with CSD we would need to sample 64 + 64 = 128 ballot cards, because no card
        // that contains S also contains B. Probably CSD vs BSD ??

        makeMultiCardHasCardStyleClca() // 114; paper has 128
        makeMultiCardHasCardStyle(AuditType.CLCA) // mvrUsed = 665; paper has 128
        makeMultiCardNoStyle(AuditType.CLCA) // mvrUsed = 1206; paper has 1,712

        // change to p = .3 TODO
        makeMultiCardHasBallotStylePolling() // mvrUsed = 2037 (will vary); paper has 2067-2432
        makeMultiCardNoStylePolling() // mvrUsed = 14000 (will vary) ; paper has 4053 TODO
    }

    fun makeSingleCardNoStyle(auditType: AuditType) {
        // nostyle single: Npop(B) = N, Npop(S) = N

        val testData = MultiContestCombineData(contests, contestB.Nc, poolId = 1)
        val (testCards, batches) = testData.makeCardsFromContests()

        val infos = contests.map{ it.info }.associateBy { it.id }
        val tabs = tabulateAuditableCards(Closer(testCards.iterator()), infos).toSortedMap()
        contests.forEach { contest ->
            assertEquals(contest.votes, tabs[contest.id]!!.votes)
        }
        // TODO Npop correct ??

        val topdir = "$testdataDir/persist/hasStyle/makeSingleCardNoStyle.$auditType"
        val ok = createAndRunTestAuditCards(auditType, "makeSingleCardNoStyle.$auditType", topdir, contests, testCards, batches)
        assertTrue(ok)
    }

    // use MultiContestCombineData to generate cards, then fix the batches
    fun makeSingleCardHasStyleOld(auditType: AuditType) {
        // hasStyle, single: Npop(B) = N, Npop(S) = p*N

        val testData = MultiContestCombineData(contests, contestB.Nc, poolId = 1)
        val (testCards, batches) = testData.makeCardsFromContests()

        val fixBatches = mapOf(
            listOf(1) to Batch("batchB", 1, intArrayOf(1), false),
            listOf(2) to Batch("batchS", 2, intArrayOf(2), false),
            listOf(1,2) to Batch("batchBS", 3, intArrayOf(1,2), false)
        )

        // now fix the batches
        val newBatchedIter: CloseableIterator<AuditableCard>  = TransformingIterator<AuditableCard, AuditableCard>(
            Closer( testCards.iterator())) { card:AuditableCard ->
            val contests = card.votes!!.keys.toList().sorted()
            card.copy(batch = fixBatches[contests]!!)
        }
        val newBatched = mutableListOf<AuditableCard>()
        while (newBatchedIter.hasNext()) { newBatched.add(newBatchedIter.next()) }

        val infos = contests.map{ it.info }.associateBy { it.id }
        val tabs = tabulateAuditableCards(Closer(newBatched.iterator()), infos).toSortedMap()
        contests.forEach { contest ->
            assertEquals(contest.votes, tabs[contest.id]!!.votes)
        }
        // TODO Npop correct ??

        val topdir = "$testdataDir/persist/hasStyle/makeSingleCardHasStyleOld.$auditType"
        val ok = createAndRunTestAuditCards(auditType,"makeSingleCardHasStyle.$auditType", topdir, contests, newBatched, fixBatches.values.toList())
        assertTrue(ok)
    }

    fun makeSingleCardHasStyle(auditType: AuditType) {
        // hasStyle, single: Npop(B) = N, Npop(S) = p*N

        val half1 = contestB.votes.mapValues{ 9*it.value/10 }
        val half2 = contestB.votes.mapValues{ it.value - 9*it.value/10 }

        val infos = contests.map{ it.info }.associateBy { it.id }
        val tabsBS =  mapOf(
            1 to ContestTabulation(infos[1]!!, half2, contestB.Nc),
            2 to ContestTabulation(infos[2]!!, contestS.votes, contestS.Nc),
        )
        val tabsB =  mapOf(
            1 to ContestTabulation(infos[1]!!,half1, contestB.Nc),
        )

        val pools = listOf(
            CardPool("poolB", 1, true, infos, tabsB, totalCards = 9 * contestB.Nc/10),
            CardPool("poolBS", 2, true, infos, tabsBS, totalCards = contestS.Nc),
        )

        val testData = MultiContestCombinePools(contest3s, 2 * contestB.Nc, pools)
        val (testCards, batches) = testData.makeCardsFromContests()

        val tabs = tabulateAuditableCards(Closer(testCards.iterator()), infos).toSortedMap()
        contests.forEach { contest ->
            assertEquals(contest.votes, tabs[contest.id]!!.votes)
        }

        val topdir = "$testdataDir/persist/hasStyle/makeSingleCardHasStyle.$auditType"
        val ok = createAndRunTestAuditCards(auditType,"makeSingleCardHasStyle.$auditType", topdir, contests, testCards, batches)
        assertTrue(ok)
    }

    // use batches, not _fromCvr_
    fun makeMultiCardHasCardStyle(auditType: AuditType) {
        // hasStyle,  multicard: Npop(B) = N, Npop(S) = p*N

        val infos = contest3s.map{ it.info }.associateBy { it.id }

        val half2 = contestB.votes.mapValues{ it.value/2 }
        val half3 = contestB.votes.mapValues{ it.value - it.value/2 }

        val tabsBS =  mapOf(
            1 to ContestTabulation(infos[1]!!, half2, contestB.Nc),
            2 to ContestTabulation(infos[2]!!, contestS.votes, contestS.Nc),
        )
        val tabsBT =  mapOf(
            1 to ContestTabulation(infos[1]!!,half3, contestB.Nc),
            3 to ContestTabulation(infos[3]!!, contestT.votes, contestT.Nc),
        )

        val pools = listOf(
            CardPool("poolBS", 2, false, infos, tabsBS, totalCards = contestB.Nc),
            CardPool("poolBT", 3, false, infos, tabsBT, totalCards = contestB.Nc)
        )

        // TODO can we consolidate all 4 cases into using MultiContestCombinePools ??
        val testData = MultiContestCombinePools(contest3s, 2 * contestB.Nc, pools)
        val (testCards, batches) = testData.makeCardsFromContests()

        val tabs = tabulateAuditableCards(Closer(testCards.iterator()), infos).toSortedMap()
        contests.forEach { contest ->
            assertEquals(contest.votes, tabs[contest.id]!!.votes)
        }
        // TODO Npop correct ??

        val topdir = "$testdataDir/persist/hasStyle/makeMultiCardHasStyle.$auditType"
        val ok = createAndRunTestAuditCards(auditType, "makeMultiCardHasBallotStyle.$auditType", topdir, contest3s, testCards, batches)
        assertTrue(ok)
    }

    // switch back to using _fromCvr_
    fun makeMultiCardHasCardStyleClca() {
        // hasStyle,  multicard: Npop(B) = N, Npop(S) = p*N

        val testData = MultiContestCombineData(contest3s, contestB.Nc, poolId = null)
        val (testCards, batches) = testData.makeCardsFromContests()

        val infos = contest3s.map{ it.info }.associateBy { it.id }
        val tabs = tabulateAuditableCards(Closer(testCards.iterator()), infos).toSortedMap()
        contests.forEach { contest ->
            assertEquals(contest.votes, tabs[contest.id]!!.votes)
        }

        val topdir = "$testdataDir/persist/hasStyle/makeMultiCardHasCardStyleClca"
        val ok = createAndRunTestAuditCards(AuditType.CLCA, "makeMultiCardHasCardStyleClca", topdir, contest3s, testCards, batches)
        assertTrue(ok)
    }

    // TODO this only applies to POLLING I Think. Note that the example changes p = .3
    // p 13: "Suppose we know which ballots contain S but not which particular cards contain S, and
    //  that the c cards comprising each ballot are kept in the same container."
    // I think this means that we have a multicard ballot, and we know what the possible contests, say by precinct, but the
    //   ballot's cards were not kept together, its just one pile of cards per precinct.
    // So there are two pools, one that doesnt contain S and one that might (?)
    fun makeMultiCardHasBallotStylePolling() {
        //   multicard: Npop(B) = N*c, Npop(S) = N   ??
        // actual: N, N/2
        val contestS3 = Contest(
            ContestInfo("S", 2, mapOf("Del" to 1, "Mel" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 1650, 2 to 1350),
            3000,
            3000
        )
        assertEquals(0.1, contestS.reportedMargin(1, 2))

        val contestT3 = Contest(
            ContestInfo("T", 3, mapOf("Bill" to 1, "Til" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 1650, 2 to 1350),
            3000,
            3000
        )
        assertEquals(0.1, contestS.reportedMargin(1, 2))
        val N = contestB.Nc

        val contests = listOf(contestB, contestS3, contestT3)
        val acontests = listOf(contestB, contestS3)

        val card1 = CardStyle("card1", 1, intArrayOf(1), true)
        val card2 = CardStyle("card2", 2, intArrayOf(2), true)
        val card3 = CardStyle("card3", 3, intArrayOf(3), true)
        val ballot12 = BallotStyle("style12", 4, false, listOf(card1, card2), N/2)
        val ballot13 = BallotStyle("style13", 5, false, listOf(card1, card3), N/2)

        val testData = MultiContestFromBallotStyles(contests, listOf(ballot12, ballot13))
        val (testCards, batches) = testData.makeCardsFromContests()

        val infos = contests.map{ it.info }.associateBy { it.id }
        val tabs = tabulateAuditableCards(Closer(testCards.iterator()), infos).toSortedMap()
        contests.forEach { contest ->
            assertEquals(contest.votes, tabs[contest.id]!!.votes)
        }

        val topdir = "$testdataDir/persist/hasStyle/makeMultiCardHasBallotStylePolling"
        val ok = createAndRunTestAuditCards(AuditType.POLLING, "makeMultiCardHasBallotStylePolling", topdir, acontests, testCards, batches)
        assertTrue(ok)
    }

    fun makeMultiCardNoStylePolling() {
        //   multicard: Npop(B) = N*c, Npop(S) = N   ??

        val contestS3 = Contest(
            ContestInfo("S", 2, mapOf("Del" to 1, "Mel" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 1650, 2 to 1350),
            3000,
            3000
        )
        assertEquals(0.1, contestS.reportedMargin(1, 2))

        val contestT3 = Contest(
            ContestInfo("T", 3, mapOf("Bill" to 1, "Til" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 1650, 2 to 1350),
            3000,
            3000
        )
        assertEquals(0.1, contestS.reportedMargin(1, 2))
        val N = contestB.Nc

        val contests = listOf(contestB, contestS3)

        val card1 = CardStyle("card1", 1, intArrayOf(1), true)
        val card2 = CardStyle("card2", 2, intArrayOf(2), true)
        val card3 = CardStyle("card3", 3, intArrayOf(3), true)
        val ballot123 = BallotStyle("style12", 4, false, listOf(card1, card2), N)
        // val ballot13 = BallotStyle("style13", 5, false, listOf(card1, card3), N)

        val testData = MultiContestFromBallotStyles(contests, listOf(ballot123))
        val (testCards, batches) = testData.makeCardsFromContests()

        val card12 = CardStyle("card12", 3, intArrayOf(1,2), false)

        // now fix the batches
        val newBatchedIter: CloseableIterator<AuditableCard>  = TransformingIterator<AuditableCard, AuditableCard>(
            Closer( testCards.iterator())) { card:AuditableCard ->
            val contests = card.votes!!.keys.toList().sorted()
            card.copy(batch = card12)
        }
        val newBatched = mutableListOf<AuditableCard>()
        while (newBatchedIter.hasNext()) { newBatched.add(newBatchedIter.next()) }

        val infos = contests.map{ it.info }.associateBy { it.id }
        val tabs = tabulateAuditableCards(Closer(testCards.iterator()), infos).toSortedMap()
        contests.forEach { contest ->
            assertEquals(contest.votes, tabs[contest.id]!!.votes)
        }

        val topdir = "$testdataDir/persist/hasStyle/makeMultiCardNoStylePolling"
        val ok = createAndRunTestAuditCards(AuditType.POLLING, "makeMultiCardNoStylePolling", topdir, contests, newBatched, listOf(card12))
        assertTrue(ok)
    }

    fun makeMultiCardNoStyle(auditType: AuditType) {
        // multicard: Npop(B) = N*c, Npop(S) = N*c

        val infos = contest3s.map{ it.info }.associateBy { it.id }

        val half2 = contestB.votes.mapValues{ it.value/2 }
        val half3 = contestB.votes.mapValues{ it.value - it.value/2 }

        val tabsBS =  mapOf(
            1 to ContestTabulation(infos[1]!!, half2, contestB.Nc),
            2 to ContestTabulation(infos[2]!!, contestS.votes, contestS.Nc),
        )
        val tabsBT =  mapOf(
            1 to ContestTabulation(infos[1]!!,half3, contestB.Nc),
            3 to ContestTabulation(infos[3]!!, contestT.votes, contestT.Nc),
        )

        val pools = listOf(
            CardPool("poolBS", 2, false, infos, tabsBS, totalCards = contestB.Nc),
            CardPool("poolBT", 3, false, infos, tabsBT, totalCards = contestB.Nc)
        )

        val testData = MultiContestCombinePools(contest3s, 2 * contestB.Nc, pools)
        val (testCards, batches) = testData.makeCardsFromContests()

        // now fix the batches
        val all = Batch("all", 1, intArrayOf(1,2,3), false)

        val newBatchedIter: CloseableIterator<AuditableCard>  = TransformingIterator<AuditableCard, AuditableCard>(
            Closer( testCards.iterator())) { card:AuditableCard ->
            card.copy(batch = all)
        }
        val newBatched = mutableListOf<AuditableCard>()
        while (newBatchedIter.hasNext()) { newBatched.add(newBatchedIter.next()) }

        val tabs = tabulateAuditableCards(Closer(testCards.iterator()), infos).toSortedMap()
        contests.forEach { contest ->
            assertEquals(contest.votes, tabs[contest.id]!!.votes)
        }
        // TODO Npop correct ??

        val topdir = "$testdataDir/persist/hasStyle/makeMultiCardNoStyle.$auditType"
        val ok = createAndRunTestAuditCards(auditType, "makeMultiCardNoStyle.$auditType", topdir, contests, newBatched, listOf(all))
        assertTrue(ok)
    }

    fun createAndRunTestAuditCards(auditType: AuditType, name:String, topdir: String, contests: List<Contest>,
                                   testCards: List<AuditableCard>, batches:List<BatchIF>): Boolean {

        // We find sample sizes for a risk limit of 0.05 on the assumption that the rate of one-vote overstatements will be 0.001.
        // val errorRates = PluralityErrorRates(0.0, 0.001, 0.0, 0.0, )

        val infos = contests.map{ it.info }.associateBy { it.id }
        val cardIter = Closer(testCards.iterator())
        val tabs = tabulateAuditableCards(cardIter, infos)
        if (showDetails) tabs.forEach { println(it) }

        val contestsUA = contests.map {
            val Nb = tabs[it.id]?.ncardsTabulated ?: throw RuntimeException("Contest ${it.id} not found")
            ContestWithAssertions(it, isClca=!auditType.isPolling(), NpopIn=Nb).addStandardAssertions()
        }

        val election = CreateElectionFromCards(
                name, auditType, contestsUA, testCards, cardPools = null, batches = batches,
            mvrSource=MvrSource.testPrivateMvrs)

        val auditdir = "$topdir/audit"
        createElectionRecord(election, auditDir = auditdir)

        val verifyECResults = VerifyElectionCommitment(auditdir, null, show = true).verify()
        if (verifyECResults.hasErrors) {
            print(verifyECResults)
            fail()
        }

        val creation = AuditCreationConfig(auditType, riskLimit=.05, seed = 123456789L)
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 10),
            ContestSampleControl.NONE,
            ClcaConfig(), PollingConfig())

        val config = Config(election.electionInfo(), creation, round)

        createAuditRecord(config, election, auditDir = auditdir)

        val verifyACResults = VerifyAuditCommitment(auditdir, null, show = true).verify()
        if (verifyACResults.hasErrors) {
            print(verifyACResults)
            fail()
        }

        startFirstRound(auditdir)

        return runAllRoundsAndVerify(auditdir)
    }
}

