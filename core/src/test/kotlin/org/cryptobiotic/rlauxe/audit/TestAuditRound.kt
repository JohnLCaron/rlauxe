package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.consistentSampling
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.workflow.MvrManagerForTesting
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TestAuditRound {

    @Test
    fun testAuditRound() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUAs: List<ContestWithAssertions> = test.contests.map {
            ContestWithAssertions(it, isClca = true).addStandardAssertions()
        }
        val testCvrs = test.makeCvrsFromContests()
        val mvrManager = MvrManagerForTesting(testCvrs, testCvrs, Random.nextLong())

        val contestRounds = contestsUAs.map { contest -> ContestRound(contest, 1) }
        contestRounds.forEach { it.estSampleSize = it.Npop / 11 } // random

        val prng = Prng(Random.nextLong())
        testCvrs.mapIndexed { idx, it -> AuditableCard.fromCvr(it, idx, prng.next()) }

        val auditRound = AuditRound(1, contestRounds, samplePrns = emptyList())
        consistentSampling(auditRound, mvrManager)

        contestRounds.forEach { contestRound ->
            assertEquals(contestRound.estSampleSize, contestRound.wantSampleSize(0))
            assertEquals(contestRound.estSampleSize, contestRound.estSampleSizeEligibleForRemoval())

            val firstAssertion = contestRound.assertionRounds.first()
            assertEquals(0, firstAssertion.estSampleSize)
            assertNotEquals(firstAssertion.assertion.loser, firstAssertion.assertion.winner) // wtf?
        }

        assertEquals(0, auditRound.maxBallotsUsed())
    }

}
