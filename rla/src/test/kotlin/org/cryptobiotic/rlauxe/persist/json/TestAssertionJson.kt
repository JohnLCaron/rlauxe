package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeCvr
import org.cryptobiotic.rlauxe.util.listToMap
import org.cryptobiotic.rlauxe.util.makeContestFromCvrs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import kotlin.test.assertNotNull

class TestAssertionJson {
    val filename = "/home/stormy/temp/persist/test/TestAssertion.json"

    @Test
    fun testRoundtrip() {
        val target = makeAssertion()

        val json = target.publishJson()
        val roundtrip = json.import()
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
        val target = Assertion(contest, assorter)
        target.estSampleSize = 1000
        target.estNewSamples = 500
        target.status = TestH0Status.AuditorRemoved
        target.round = 42

        return target
    }
}