package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.makeCvr
import org.cryptobiotic.rlauxe.persist.json.import
import org.cryptobiotic.rlauxe.persist.json.publishJson
import org.cryptobiotic.rlauxe.util.listToMap
import org.cryptobiotic.rlauxe.util.makeContestFromCvrs
import kotlin.test.Test
import kotlin.test.assertTrue

import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestRaireJson {
    val filename = "/home/stormy/temp/persist/test/TestRaireAssertion.json"

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
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap("A", "B", "C", "D"),
        )
        val target = RaireContest(info, listOf(3), 42, 33)

        val json = target.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(target, roundtrip)
        assertTrue(roundtrip.equals(target))
    }

    // class RaireContestUnderAudit(
    //    contest: RaireContest,
    //    val winner: Int,  // the sum of winner and eliminated must be all the candiates in the contest
    //    val rassertions: List<RaireAssertion>,
    //): ContestUnderAudit(contest, isComparison=true, hasStyle=true) {
    @Test
    fun testContestUARoundtrip() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap("A", "B", "C", "D"),
        )
        val contest = RaireContest(info, listOf(1), 42, 33)

        val assert1 = RaireAssertion(1, 0, 42, RaireAssertionType.winner_only)
        val assert2 = RaireAssertion(1, 2, 422, RaireAssertionType.irv_elimination,
            listOf(2), mapOf(1 to 1, 2 to 2, 3 to 3), "some explanation")

        val target = RaireContestUnderAudit(contest, 1, listOf(assert1, assert2))

        val json = target.publishRaireJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(target, roundtrip)
        assertTrue(roundtrip.equals(target))
    }

    @Test
    fun testAssertionRoundtrip() {
        val target = RaireAssertion(4, 2, 42, RaireAssertionType.irv_elimination,
            listOf(1,3,5), mapOf(1 to 11, 2 to 22, 3 to 33),"some explanation")

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
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap("A", "B", "C", "D"),
        )
        val rassertion = RaireAssertion(4, 2, 42, RaireAssertionType.irv_elimination,
            listOf(1,3,5),  mapOf(1 to 111, 2 to 222, 3 to 333), "some explanation")
        val target = RaireAssorter(info, rassertion, rassertion.margin.toDouble() / 1000)

        val json = target.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(target, roundtrip)
        assertTrue(roundtrip.equals(target))
    }

}

fun make(): Assertion {
    val info = ContestInfo(
        name = "AvB",
        id = 0,
        choiceFunction = SocialChoiceFunction.PLURALITY,
        candidateNames = listToMap("A", "B", "C"),
    )
    val winnerCvr = makeCvr(0)
    val loserCvr = makeCvr(1)
    val otherCvr = makeCvr(2)
    val contest = makeContestFromCvrs(info, listOf(winnerCvr, loserCvr, otherCvr))

    val assorter = PluralityAssorter.makeWithVotes(contest, winner = 0, loser = 1)
    val target = Assertion(contest, assorter)
    target.estSampleSize = 1000
    target.status = TestH0Status.AuditorRemoved
    target.round = 42

    return target
}