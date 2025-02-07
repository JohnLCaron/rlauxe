package org.cryptobiotic.rlauxe.workflow

import com.github.michaelbull.result.Err
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

class TestPersistentWorkflowClca {
    val topdir = "/home/stormy/temp/persist/testPersistentWorkflowClca"

    @Test
    fun testPersistentWorkflowClca() {
        val fuzzMvrs = .01
        val publish = Publisher(topdir)
        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, hasStyles=true, seed = 12356667890L, nsimEst=10)
        writeAuditConfigJsonFile(auditConfig, publish.auditConfigFile())

        val N = 5000
        val testData = MultiContestTestData(11, 4, N, marginRange=0.03..0.05)

        val contests: List<Contest> = testData.contests
        println("Start testPersistentWorkflowClca $testData")
        contests.forEach{ println("  $it")}
        println()

        // Synthetic cvrs for testing reflecting the exact contest votes, plus undervotes and phantoms.
        val testCvrs = testData.makeCvrsFromContests()
        val testMvrs = if (fuzzMvrs == 0.0) testCvrs
            // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
            else makeFuzzedCvrsFrom(contests, testCvrs, fuzzMvrs)

        var clcaWorkflow = ClcaWorkflow(auditConfig, contests, emptyList(), testCvrs)
        writeCvrsJsonFile(clcaWorkflow.cvrsUA, publish.cvrsFile())

        var round = 1
        var done = false
        var workflow : RlauxWorkflowIF = clcaWorkflow
        while (!done) {
            done = runPersistentWorkflowStage(round, workflow, clcaWorkflow.cvrsUA, testMvrs, publish)
            workflow = readPersistentWorkflow(round, publish)
            round++
        }
    }
}

fun runPersistentWorkflowStage(roundIdx: Int, workflow: RlauxWorkflowIF, bcUA: List<BallotOrCvr>, testMvrs: List<Cvr>, publish: Publisher): Boolean {
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

        val sampledMvrs = indices.map {
            testMvrs[it]
        }

        done = workflow.runAudit(indices, sampledMvrs, roundIdx)
        println("runAudit $roundIdx done=$done took ${roundStopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")

        val state = ElectionState("Round$roundIdx", workflow.getContests(), done)
        writeElectionStateJsonFile(state, publish.auditRoundFile(roundIdx))

        val sampledMvrus = indices.map {
            val cvr = bcUA[it]
            CvrUnderAudit(testMvrs[it], cvr.sampleNumber())
        }
        writeCvrsJsonFile(sampledMvrus, publish.sampleMvrsFile(roundIdx))

        println(currRound)
        workflow.showResults(indices.size)
    }

    return done
}

fun readPersistentWorkflow(round: Int, publish: Publisher): RlauxWorkflow {
    val resultAuditConfig = readAuditConfigJsonFile(publish.auditConfigFile())
    if (resultAuditConfig is Err) println(resultAuditConfig)
    assertTrue(resultAuditConfig is Ok)
    val auditConfig = resultAuditConfig.unwrap()

    val resultAuditResult: Result<ElectionState, ErrorMessages> = readElectionStateJsonFile(publish.auditRoundFile(round))
    if (resultAuditResult is Err) println(resultAuditResult)
    assertTrue(resultAuditResult is Ok)
    val electionState = resultAuditResult.unwrap()
    assertNotNull(electionState)

    if (auditConfig.auditType == AuditType.CARD_COMPARISON) {
        val resultCvrs = readCvrsJsonFile(publish.cvrsFile())
        if (resultCvrs is Err) println(resultCvrs)
        assertTrue(resultCvrs is Ok)
        val cvrs = resultCvrs.unwrap()
        return RlauxWorkflow(auditConfig, electionState.contests, emptyList(), cvrs)

    } else {
        val resultBallotManifest = readBallotManifestJsonFile(publish.ballotManifestFile())
        if (resultBallotManifest is Err) println(resultBallotManifest)
        assertTrue(resultBallotManifest is Ok)
        val ballotManifest = resultBallotManifest.unwrap()
        return RlauxWorkflow(auditConfig, electionState.contests, ballotManifest.ballots, emptyList())
    }

}