package org.cryptobiotic.rlauxe.persist.json

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.makeCvr
import org.cryptobiotic.rlauxe.util.listToMap
import org.cryptobiotic.rlauxe.util.makeContestFromCvrs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import kotlin.test.assertNotNull

class TestAssertionJson {
    @Test
    fun testAssertionRoundtrip() {
        val target = makeAssertion()

        val json = target.publishIFJson()
        val roundtrip = json.import(target.info)
        assertNotNull(roundtrip)
        assertTrue(roundtrip.equals(target))
    }

    @Test
    fun testClcaAssertionRoundtrip() {
        val assertion = makeAssertion()
        // was hasUndervotes=false
        val cassorter =  ClcaAssorter(assertion.info, assertion.assorter, dilutedMargin=.111)
        val target = ClcaAssertion(assertion.info, cassorter)

        val json = target.publishJson()
        val roundtrip = json.import(assertion.info)
        assertNotNull(roundtrip)
        assertEquals(roundtrip, target)
    }

    fun makeAssertion(): Assertion {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap("A", "B", "C"),
        )
        val winnerCvr = makeCvr(0)
        val loserCvr = makeCvr(1)
        val otherCvr = makeCvr(2)
        val contest = makeContestFromCvrs(info, listOf(winnerCvr, winnerCvr, loserCvr, otherCvr))

        val assorter = PluralityAssorter.makeWithVotes(contest, winner = 0, loser = 1)
        val target = Assertion(info, assorter)

        return target
    }

    @Test
    fun testAboveThresholdRoundtrip() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.THRESHOLD,
            candidateNames = listToMap("A", "B", "C"),
            minFraction = .55
        )
        val winnerCvr = makeCvr(0)
        val loserCvr = makeCvr(1)
        val otherCvr = makeCvr(2)
        val contest = makeContestFromCvrs(info, listOf(winnerCvr, winnerCvr, winnerCvr, loserCvr, otherCvr))

        val assorter = AboveThreshold.makeFromVotes(contest, partyId = 0, contest.Nc)
        val target = Assertion(info, assorter)

        val json = target.publishIFJson()
        val roundtrip = json.import(target.info)
        assertNotNull(roundtrip)
        assertEquals(roundtrip, target)
    }

    @Test
    fun testBelowThresholdRoundtrip() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.THRESHOLD,
            candidateNames = listToMap("A", "B", "C"),
            minFraction = .55
        )
        val winnerCvr = makeCvr(0)
        val loserCvr = makeCvr(1)
        val otherCvr = makeCvr(2)
        val contest = makeContestFromCvrs(info, listOf(winnerCvr, winnerCvr, winnerCvr, loserCvr, otherCvr))

        //         fun makeFromVotes(info: ContestInfo, partyId: Int, votes: Map<Int, Int>, minFraction: Double, Nc: Int): AboveThreshold {
        val assorter = BelowThreshold.makeFromVotes(contest.info, partyId = 0, contest.votes()!!,  info.minFraction!!, contest.Nc)
        val target = Assertion(info, assorter)

        val json = target.publishIFJson()
        val roundtrip = json.import(target.info)
        assertNotNull(roundtrip)
        assertEquals(roundtrip, target)
    }
}