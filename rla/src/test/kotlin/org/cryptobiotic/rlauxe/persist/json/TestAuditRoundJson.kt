package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.AuditRoundResult
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.Publisher
import org.cryptobiotic.rlauxe.workflow.AuditConfig
import org.cryptobiotic.rlauxe.workflow.AuditType
import org.cryptobiotic.rlauxe.workflow.ComparisonWorkflow
import org.cryptobiotic.rlauxe.workflow.runPersistentWorkflow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import kotlin.test.assertNotNull

class TestAuditRoundJson {

    @Test
    fun testRoundtrip() {
        val publish = Publisher("/home/stormy/temp/persist/TestAuditRoundJson/")
        val auditConfig =
            AuditConfig(AuditType.CARD_COMPARISON, hasStyles = true, seed = 12356667890L, fuzzPct = 0.01, ntrials = 10)
        val N = 5000
        val testData = MultiContestTestData(11, 4, N, marginRange = 0.01..0.011)

        val contests: List<Contest> = testData.contests

        // Synthetic cvrs for testing reflecting the exact contest votes, plus undervotes and phantoms.
        val testCvrs = testData.makeCvrsFromContests()

        val workflow = ComparisonWorkflow(auditConfig, contests, emptyList(), testCvrs)
        val nassertions = workflow.contestsUA.sumOf { it.assertions().size }
        runPersistentWorkflow(publish, workflow, testCvrs, nassertions)

        val target = AuditRound(1, workflow.contestsUA, true)
        val json = target.publishJson()
        val roundtrip: AuditResult = json.import()
        assertNotNull(roundtrip)
        compareAuditResult(target, roundtrip)
    }

    fun compareAuditResult(target: AuditRound, result: AuditResult) {
        assertEquals(target.round, result.round)
        assertEquals(target.done, result.done)
        result.contests.forEach { contest ->
            val targetContest = target.contests.find { it.name == contest.name }
            assertNotNull( targetContest)
            assertEquals(contest.name, targetContest.name)
            assertEquals(contest.id, targetContest.id)
            assertEquals(contest.done, targetContest.done)

            contest.assertions.forEach { assertion ->
                val targetAssertion = targetContest.assertions().find { it.toString() == assertion.desc }
                assertNotNull( targetAssertion )
                assertEquals(assertion.round, targetAssertion.round)
                assertEquals(assertion.desc, targetAssertion.toString())

                val rr: AuditRoundResult = targetAssertion.roundResults[assertion.round - 1]
                assertEquals(assertion.round, rr.roundIdx)
                assertEquals(assertion.pvalue, rr.pvalue)
                assertEquals(assertion.estSampleSize, rr.estSampleSize)
                assertEquals(assertion.samplesNeeded, rr.samplesNeeded)
                assertEquals(assertion.samplesUsed, rr.samplesUsed)
                assertEquals(assertion.status, rr.status)
            }
        }
    }

    @Test
    fun testRoundtripIO() {
        val publish = Publisher("/home/stormy/temp/persist/testRoundtripIO/")
        val auditConfig =
            AuditConfig(AuditType.CARD_COMPARISON, hasStyles = true, seed = 12356667890L, fuzzPct = 0.01, ntrials = 10)
        val N = 5000
        val testData = MultiContestTestData(11, 4, N, marginRange = 0.01..0.011)

        val contests: List<Contest> = testData.contests

        // Synthetic cvrs for testing reflecting the exact contest votes, plus undervotes and phantoms.
        val testCvrs = testData.makeCvrsFromContests()

        val workflow = ComparisonWorkflow(auditConfig, contests, emptyList(), testCvrs)
        val nassertions = workflow.contestsUA.sumOf { it.assertions().size }
        runPersistentWorkflow(publish, workflow, testCvrs, nassertions)
        val target = AuditRound(1, workflow.contestsUA, true)
/*
        val nrounds = 1
        for (round in 1..nrounds) {
            val result: Result<AuditResult, ErrorMessages> = readAuditRoundJsonFile(publish.auditRoundFile(round))
            assertTrue(result is Ok)
            val roundtrip = result.unwrap()
            assertNotNull(roundtrip)
            compareAuditResult(target, roundtrip)
        }

 */
    }
}