package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.consistentSampling
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.workflow.MvrManagerClcaForTesting
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TestAuditRound {

    @Test
    fun testAuditRound() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUAs: List<ContestUnderAudit> = test.contests.map {
            ContestUnderAudit(it, isComparison = true)
        }
        val testCvrs = test.makeCvrsFromContests()
        val mvrManager = MvrManagerClcaForTesting(testCvrs, testCvrs, Random.nextLong())

        contestsUAs.forEach { it.addClcaAssertionsFromReportedMargin() }
        val contestRounds = contestsUAs.map { contest -> ContestRound(contest, 1) }
        contestRounds.forEach { it.estSampleSize = it.Nc / 11 } // random

        val prng = Prng(Random.nextLong())
        testCvrs.mapIndexed { idx, it -> AuditableCard.fromCvr(it, idx, prng.next()) }

        val auditRound = AuditRound(1, contestRounds, samplePrns = emptyList(), sampledBorc = emptyList())
        consistentSampling(auditRound, mvrManager)

        contestRounds.forEach { contestRound ->
            assertEquals(contestRound.estSampleSize, contestRound.wantSampleSize(0))
            assertEquals(contestRound.estSampleSize, contestRound.estSampleSizeEligibleForRemoval())

            val minAssertion = contestRound.minAssertion()!!
            assertEquals(0, minAssertion.estSampleSize)
            assertNotEquals(minAssertion.assertion.loser, minAssertion.assertion.winner)
        }

        assertEquals(0, auditRound.maxBallotsUsed())
    }

}
