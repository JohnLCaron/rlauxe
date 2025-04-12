package org.cryptobiotic.rlauxe.persist.json

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.makeCvr
import org.cryptobiotic.rlauxe.util.listToMap
import org.cryptobiotic.rlauxe.util.makeContestFromCvrs
import kotlin.test.Test
import kotlin.test.assertTrue

import kotlin.test.assertNotNull

class TestAssertionJson {
    @Test
    fun testAssertionRoundtrip() {
        val target = makeAssertion()

        val json = target.publishJson()
        val roundtrip = json.import(target.info)
        assertNotNull(roundtrip)
        assertTrue(roundtrip.equals(target))
    }

    @Test
    fun testClcaAssertionRoundtrip() {
        val assertion = makeAssertion()
        val cassorter =  ClcaAssorter(assertion.info, assertion.assorter, .52, false, true)
        val target = ClcaAssertion(assertion.info, cassorter)

        val json = target.publishJson()
        val roundtrip = json.import(target.info)
        assertNotNull(roundtrip)
        assertTrue(roundtrip.equals(target))
    }

    fun makeAssertion(): Assertion {
        // Assertion(
        //    val contest: ContestIF,
        //    val assorter: AssorterFunction,
        //) {
        //    val winner = assorter.winner()
        //    val loser = assorter.loser()
        //
        //    // these values are set during estimateSampleSizes()
        //    var estSampleSize = 0   // estimated sample size for current round
        //    var estNewSamples = 0   // estimated new sample size for current round
        //    val estRoundResults = mutableListOf<EstimationRoundResult>()
        //
        //    // these values are set during runAudit()
        //    val roundResults = mutableListOf<AuditRoundResult>()
        //    var status = TestH0Status.InProgress
        //    var round = 0           // round when set to proved or disproved
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
        val target = Assertion(info, assorter)

        return target
    }

    @Test
    fun testSuperAssertionRoundtrip() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listToMap("A", "B", "C"),
            minFraction = .67
        )
        val winnerCvr = makeCvr(0)
        val loserCvr = makeCvr(1)
        val otherCvr = makeCvr(2)
        val contest = makeContestFromCvrs(info, listOf(winnerCvr, winnerCvr, winnerCvr, loserCvr, otherCvr))

        val assorter = SuperMajorityAssorter.makeWithVotes(contest, winner = 0, info.minFraction!!)
        val target = Assertion(info, assorter)

        val json = target.publishJson()
        val roundtrip = json.import(target.info)
        assertNotNull(roundtrip)
        assertTrue(roundtrip.equals(target))
    }
}