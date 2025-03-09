package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.PersistentWorkflow
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.persist.json.Publisher
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class TestPersistentWorkflowClca {
    // val topdir = "/home/stormy/temp/persist/testPersistentWorkflowClca"
    val topdir = kotlin.io.path.createTempDirectory().toString()

    @Test
    fun testPersistentWorkflowClca() {
        val fuzzMvrs = .01
        val publish = Publisher(topdir)
        val auditConfig = AuditConfig(AuditType.CLCA, hasStyles=true, seed = 12356667890L, nsimEst=10)
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

        writeContestsJsonFile(clcaWorkflow.contestUA(), publish.contestsFile())

        var round = 1
        var done = false
        var workflow : RlauxWorkflowIF = clcaWorkflow
        while (!done) {
            done = runPersistentWorkflowStage(round, workflow, clcaWorkflow.cvrsUA, testMvrs, publish)
            workflow = PersistentWorkflow(topdir)
            round++
        }
    }
}

fun runPersistentWorkflowStage(roundIdx: Int, workflow: RlauxWorkflowIF, bcUA: List<BallotOrCvr>, testMvrs: List<Cvr>, publish: Publisher): Boolean {
    val roundStopwatch = Stopwatch()
    var done = false

    val nextRound = workflow.startNewRound()

    if (nextRound.sampledIndices.isEmpty()) {
        done = true

    } else {
        writeSampleIndicesJsonFile(nextRound.sampledIndices, publish.sampleIndicesFile(roundIdx))

        val sampledMvrs = nextRound.sampledIndices.map {
            testMvrs[it]
        }
        done = workflow.runAudit(nextRound, sampledMvrs)

        println("runAudit $roundIdx done=$done took ${roundStopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
        writeAuditRoundJsonFile(nextRound, publish.auditRoundFile(roundIdx))

        val sampledMvrus = nextRound.sampledIndices.map {
            val cvr = bcUA[it]
            CvrUnderAudit(testMvrs[it], cvr.sampleNumber())
        }
        writeCvrsJsonFile(sampledMvrus, publish.sampleMvrsFile(roundIdx))

        println(nextRound)
        // workflow.showResults(indices.size)
    }

    return done
}

/*
fun readPersistentWorkflow(round: Int, publish: Publisher): PersistentWorkflow {
    val resultAuditConfig = readAuditConfigJsonFile(publish.auditConfigFile())
    if (resultAuditConfig is Err) println(resultAuditConfig)
    assertTrue(resultAuditConfig is Ok)
    val auditConfig = resultAuditConfig.unwrap()

    val resultAuditResult: Result<AuditRound, ErrorMessages> = readAuditRoundJsonFile(publish.auditRoundFile(round))
    if (resultAuditResult is Err) println(resultAuditResult)
    assertTrue(resultAuditResult is Ok)
    val electionState = resultAuditResult.unwrap()
    assertNotNull(electionState)

    if (auditConfig.auditType == AuditType.CLCA) {
        val resultCvrs = readCvrsJsonFile(publish.cvrsFile())
        if (resultCvrs is Err) println(resultCvrs)
        assertTrue(resultCvrs is Ok)
        val cvrs = resultCvrs.unwrap()
        return PersistentWorkflow(auditConfig, electionState.contests, emptyList(), cvrs)

    } else {
        val resultBallotManifest = readBallotManifestJsonFile(publish.ballotManifestFile())
        if (resultBallotManifest is Err) println(resultBallotManifest)
        assertTrue(resultBallotManifest is Ok)
        val ballotManifest = resultBallotManifest.unwrap()
        return PersistentWorkflow(auditConfig, electionState.contests, ballotManifest.ballots, emptyList())
    }

} */