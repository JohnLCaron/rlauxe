package org.cryptobiotic.rlauxe.workflow

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import org.cryptobiotic.rlauxe.sampling.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.Publisher
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestPersistentWorkflowInStages {
    val topdir = "/home/stormy/temp/persist/testPersistentWorkflow"

    @Test
    fun testPersistentWorkflow() {
        val fuzzMvrs = .05
        val publish = Publisher(topdir)
        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, hasStyles=true, seed = 12356667890L, nsimEst=10,
            pollingConfig = PollingConfig(simFuzzPct = .01))

        writeAuditConfigJsonFile(auditConfig, publish.auditConfigFile())

        val N = 5000
        val testData = MultiContestTestData(11, 4, N)

        val contests: List<Contest> = testData.contests
        println("Start testComparisonWorkflow $testData")
        contests.forEach{ println("  $it")}
        println()

        val electionInit = ElectionInit(
            "class TestPersistentWorkflow {\n",
            contests.map { it.info }
        )
        val electionInitJson = electionInit.publishJson()
        writeElectionInitJsonFile(electionInitJson, publish.electionInitFile())

        // Synthetic cvrs for testing reflecting the exact contest votes, plus undervotes and phantoms.
        val testCvrs = testData.makeCvrsFromContests()
        val testMvrs = if (fuzzMvrs == 0.0) testCvrs
            // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
            else makeFuzzedCvrsFrom(contests, testCvrs, fuzzMvrs)

        val workflow = ClcaWorkflow(auditConfig, contests, emptyList(), testCvrs)
        writeCvrsJsonFile(workflow.cvrsUA, publish.cvrsFile())

        var round = 1
        var done = false
        while (!done) {
            done = runPersistentWorkflowStage(round, workflow, testMvrs, publish)
        }
    }

}

fun runPersistentWorkflowStage(roundIdx: Int, workflow: RlauxWorkflowIF, testMvrs: List<Cvr>, publish: Publisher): Boolean {
    val roundStopwatch = Stopwatch()
    val previousSamples = mutableSetOf<Int>()

    var done = false

    val indices = workflow.chooseSamples(roundIdx, show=false)
    if (indices.isEmpty()) {
        done = true

    } else {
        writeSampleIndicesJsonFile(indices, publish.sampleIndicesFile(roundIdx))

        val currRound = Round(roundIdx, indices, previousSamples.toSet())
        previousSamples.addAll(indices)
        println(
            "$roundIdx choose ${indices.size} samples, new=${currRound.newSamples} took ${
                roundStopwatch.elapsed(
                    TimeUnit.MILLISECONDS
                )
            } ms\n"
        )

        val sampledMvrs = indices.map {
            testMvrs[it]
        }

        done = workflow.runAudit(indices, sampledMvrs, roundIdx)
        println("runAudit $roundIdx done=$done took ${roundStopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
        val auditRound = AuditRound(roundIdx, workflow.getContests(), done)
        writeAuditRoundJsonFile(auditRound, publish.auditRoundFile(roundIdx))

        println(currRound)
        workflow.showResults()
    }

    return done
}

fun recreateAudit(publish: Publisher, round: Int) {
    val resultAuditConfig = readAuditConfigJsonFile(publish.auditConfigFile())
    assertTrue(resultAuditConfig is Ok)
    val auditConfig = resultAuditConfig.unwrap()

    val resultAuditResult: Result<AuditResult, ErrorMessages> = readAuditRoundJsonFile(publish.auditRoundFile(round))
    assertTrue(resultAuditResult is Ok)
    val auditResult = resultAuditResult.unwrap()
    assertNotNull(auditResult)

    val resultCvrs = readCvrsJsonFile(publish.cvrsFile())
    assertTrue(resultCvrs is Ok)
    val cvrs = resultCvrs.unwrap()


    // val workflow = ClcaWorkflow(auditConfig, auditResult.contests, emptyList(), cvrs)

}