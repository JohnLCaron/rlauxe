package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.Assertion
import org.cryptobiotic.rlauxe.core.ClcaAssorter
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.PluralityAssorter
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.estimate.makeCvr
import org.cryptobiotic.rlauxe.estimate.makeCvrsByExactCount
import org.cryptobiotic.rlauxe.util.listToMap
import org.cryptobiotic.rlauxe.util.makeContestFromCvrs
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TestSampler {
    val cvrs: List<Cvr> = makeCvrsByExactCount(listOf(1, 1))
    val assertion = makeAssertion()

    init {
        assertEquals(2, cvrs.size)
    }

    @Test
    fun testPollWithoutReplacement() {
        val target = PollWithoutReplacement(0, true, cvrs, assertion.assorter)

        var count = 0
        while (target.hasNext()) {
            val wtf = target.next()
            count++
        }
        assertEquals(cvrs.size, count)
    }

    @Test
    fun testClcaWithoutReplacement() {
        val cassorter =  ClcaAssorter(assertion.info, assertion.assorter, hasStyle=false, true)
        val cvrPairs = cvrs.zip( cvrs)
        val target = ClcaWithoutReplacement(0, true, cvrPairs, cassorter, true)

        var count = 0
        while (target.hasNext()) {
            val wtf = target.next()
            count++
        }
        assertEquals(cvrs.size, count)
    }

    @Test
    fun testClcaNoErrorIterator() {
        val cassorter =  ClcaAssorter(assertion.info, assertion.assorter, hasStyle=false, true)

        val target = ClcaNoErrorIterator(0, cvrs.size, cassorter, cvrs.iterator())

        var count = 0
        while (target.hasNext()) {
            val wtf = target.next()
            count++
        }
        assertEquals(cvrs.size, count)
    }

    @Test
    fun testOneAuditNoErrorIterator() {
        val cassorter =  ClcaAssorter(assertion.info, assertion.assorter, hasStyle=false, true)

        val target = OneAuditNoErrorIterator(0, cvrs.size, -1,cassorter, cvrs.iterator())

        var count = 0
        while (target.hasNext()) {
            val wtf = target.next()
            count++
        }
        assertEquals(cvrs.size, count)
    }

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
    val contest = makeContestFromCvrs(info, listOf(winnerCvr, winnerCvr, loserCvr, otherCvr))

    val assorter = PluralityAssorter.makeWithVotes(contest, winner = 0, loser = 1)
    val target = Assertion(info, assorter)

    return target
}