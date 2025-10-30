package org.cryptobiotic.rlauxe.persist.json

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.util.listToMap
import kotlin.test.Test
import kotlin.test.assertTrue

import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestRaireJson {

    // data class RaireContestJson(
    //    val info: ContestInfoJson,
    //    val winners: List<Int>,
    //    val Nc: Int,
    //    val Np: Int,
    //)
    @Test
    fun testContestRoundtrip() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.IRV,
            candidateNames = listToMap("A", "B", "C", "D"),
        )
        val target = RaireContest(info, listOf(3), 42, 33, undervotes=11)
        // TODO target.roundsPaths.addAll(roundPathsById)

        val json = target.publishJson()
        val roundtrip = json.import(info) as RaireContest
        assertNotNull(roundtrip)
        assertEquals(target, roundtrip)
        assertTrue(roundtrip.equals(target))
        assertTrue(roundtrip.roundsPaths.equals(target.roundsPaths))
    }

    // class RaireContestUnderAudit(
    //    contest: RaireContest,
    //    val winner: Int,  // the sum of winner and eliminated must be all the candiates in the contest
    //    val rassertions: List<RaireAssertion>,
    //): ContestUnderAudit(contest, isComparison=true, hasStyle=true) {
    @Test
    fun testContestUARoundtrip() {
        val target = makeRaireUA()

        val json = target.publishRaireJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(target, roundtrip)
        assertTrue(roundtrip.equals(target))
        val tcontest = target.contest as RaireContest
        val rcontest = roundtrip.contest as RaireContest
        assertTrue(tcontest.roundsPaths.equals(rcontest.roundsPaths))
    }

    @Test
    fun testAssertionRoundtrip() {
        val target = RaireAssertion(4, 2, 0.0,42, RaireAssertionType.irv_elimination,
            listOf(1,3,5), mapOf(1 to 11, 2 to 22, 3 to 33))

        val json = target.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(target, roundtrip)
        assertTrue(roundtrip.equals(target))
    }

    @Test
    fun testAssortorRoundtrip() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.IRV,
            candidateNames = listToMap("A", "B", "C", "D"),
        )
        val rassertion = RaireAssertion(4, 2, 0.0,42, RaireAssertionType.irv_elimination,
            listOf(1,3,5),  mapOf(1 to 111, 2 to 222, 3 to 333))
        val target = RaireAssorter(info, rassertion, rassertion.marginInVotes.toDouble() / 1000)

        val json = target.publishJson()
        val roundtrip = json.import(info)
        assertNotNull(roundtrip)
        assertEquals(target, roundtrip)
        assertTrue(roundtrip.equals(target))
    }
}

fun makeRaireUA(): RaireContestUnderAudit {
    val info = ContestInfo(
        name = "AvB",
        id = 0,
        choiceFunction = SocialChoiceFunction.IRV,
        candidateNames = listToMap("A", "B", "C", "D"),
    )
    val contest = RaireContest(info, listOf(2), 42, 33, undervotes=1)

    val round1 =  IrvRound(mapOf(0 to 42, 1 to 99, 2 to 1032)) // data class IrvRound(val count: Map<Int, Int>) { // count is candidate -> nvotes for one round
    val round2 =  IrvRound(mapOf(1 to 99, 2 to 1032))
    val irvWinner = IrvWinners(done = true, winners = setOf(2)) // data class IrvWinners(val done:Boolean = false, val winners: Set<Int>
    val irp = IrvRoundsPath(listOf(round1, round2), irvWinner)
    contest.roundsPaths.add(irp)

    val assert1 = RaireAssertion(1, 0, 0.0,42, RaireAssertionType.winner_only)
    val assert2 = RaireAssertion(1, 2, 0.0,422, RaireAssertionType.irv_elimination,
        listOf(2), mapOf(1 to 1, 2 to 2, 3 to 3))

    return RaireContestUnderAudit(contest, rassertions=listOf(assert1, assert2))
}