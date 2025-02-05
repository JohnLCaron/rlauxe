package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import org.cryptobiotic.rlauxe.sampling.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.util.Publisher
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class TestPersistentWorkflowPolling {
    val topdir = "/home/stormy/temp/persist/testPersistentWorkflowPolling"

    @Test
    fun testPersistentWorkflowPolling() {
        val fuzzMvrs = .01
        val publish = Publisher(topdir)
        val auditConfig = AuditConfig(AuditType.POLLING, hasStyles=true, seed = 12356667890L, nsimEst=10)
        writeAuditConfigJsonFile(auditConfig, publish.auditConfigFile())

        val N = 5000
        val testData = MultiContestTestData(11, 4, N, marginRange=0.03..0.05)

        val contests: List<Contest> = testData.contests
        println("Start testPersistentWorkflowPolling $testData")
        contests.forEach{ println("  $it")}
        println()

        val (testCvrs, ballotManifest) = testData.makeCvrsAndBallotManifest(auditConfig.hasStyles)
        val testMvrs = makeFuzzedCvrsFrom(contests, testCvrs, fuzzMvrs)
        val pollingWorkflow = PollingWorkflow(auditConfig, contests, ballotManifest, testCvrs.size)

        val ballotManifestUA = BallotManifestUnderAudit(pollingWorkflow.ballotsUA, ballotManifest.ballotStyles)
        writeBallotManifestJsonFile(ballotManifestUA, publish.ballotManifestFile())

        var round = 1
        var done = false
        var workflow : RlauxWorkflowIF = pollingWorkflow
        while (!done) {
            done = runPersistentWorkflowPollingStage(round, workflow, pollingWorkflow.ballotsUA, testMvrs, publish)
            workflow = readPersistentWorkflow(round, publish)
            round++
        }
    }
}

// delete
fun runPersistentWorkflowPollingStage(roundIdx: Int, workflow: RlauxWorkflowIF, bcUA: List<BallotOrCvr>, testMvrs: List<Cvr>, publish: Publisher): Boolean {
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
        workflow.showResults()
    }

    return done
}