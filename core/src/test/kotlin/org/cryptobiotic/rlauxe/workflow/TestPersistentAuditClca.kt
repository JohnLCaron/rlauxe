package org.cryptobiotic.rlauxe.workflow

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.persist.*
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.verify.VerifyContests
import org.junit.jupiter.api.Assertions.assertFalse
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.fail

class TestPersistentAuditClca {
    val auditDir = "/home/stormy/rla/persist/testPersistentWorkflowClca"
    // val auditDir = kotlin.io.path.createTempDirectory().toString()

    // @Test
    fun testPersistentAuditClca() {
        clearDirectory(Path.of(auditDir))

        val fuzzMvrPct = .01
        val publisher = Publisher(auditDir)
        val auditConfig = AuditConfig(AuditType.CLCA, hasStyles=true, seed = 12356667890L, nsimEst=10, contestSampleCutoff = 1000)
        writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())

        val N = 5000
        val testData = MultiContestTestData(11, 4, N, marginRange=0.03..0.05)

        val contests: List<Contest> = testData.contests
        println("Start testPersistentWorkflowClca $testData")

        // Synthetic cvrs for testing reflecting the exact contest votes, plus undervotes and phantoms.
        val testCvrs = testData.makeCvrsFromContests()
        val testMvrs = if (fuzzMvrPct == 0.0) testCvrs
            // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
            else makeFuzzedCvrsFrom(contests, testCvrs, fuzzMvrPct)

        // the order of the cvrs cannot be changed once the audit is initialized.
        val prng = Prng(auditConfig.seed)
        val cvrsUA = testCvrs.mapIndexed { idx, it -> AuditableCard.fromCvr(it, idx, prng.next()) }.sortedBy { it.prn }
        writeAuditableCardCsvFile(cvrsUA, publisher.sortedCardsFile())
        println("write sortedCards to ${publisher.sortedCardsFile()} ")

        // put the testMvrs in the same sorted order as the cvrsUA
        val testMvrsUA = cvrsUA.map { AuditableCard.fromCvr(testMvrs[it.index], it.index, it.prn) }

        // val mvrManagerTest = MvrManagerTestFromRecord(testCvrs, testMvrs, auditConfig.seed) this does the mvrs manipulations internally
        val mvrManager = MvrManagerFromRecord(auditDir)
        var clcaWorkflow = WorkflowTesterClca(auditConfig, contests, emptyList(), mvrManager)

        // these checks may modify the contest status
        val verifier = VerifyContests(auditDir)
        val resultsv = verifier.verify(clcaWorkflow.contestsUA(), false)
        println(resultsv.toString())
        assertFalse(resultsv.hasErrors)

        writeContestsJsonFile(clcaWorkflow.contestsUA(), publisher.contestsFile())
        println("write writeContestsJsonFile to ${publisher.contestsFile()} ")

        var round = 1
        var done = false
        var workflow : AuditWorkflow = clcaWorkflow
        while (!done) {
            done = runPersistentWorkflowStage(round, workflow, auditDir, testMvrsUA, publisher)
            workflow = PersistedWorkflow(auditDir, useTest = false)
            round++
        }
        println("------------------ ")
    }
}

fun runPersistentWorkflowStage(roundIdx: Int, workflow: AuditWorkflow, auditDir: String, testMvrsUA: List<AuditableCard>, publish: Publisher): Boolean {
    val roundStopwatch = Stopwatch()
    var done = false
    println("------------------ Round ${roundIdx}")

    // estimate and sample
    val auditRound = workflow.startNewRound()

    if (auditRound.samplePrns.isEmpty()) {
        done = true

    } else {
        // write the samplePrns for this round
        writeSamplePrnsJsonFile(auditRound.samplePrns, publish.samplePrnsFile(roundIdx))

        // fetch the corresponding testMvrs, add them to the audit record
        val sampledMvrs =  findSamples(auditRound.samplePrns, Closer(testMvrsUA.iterator()))

        val auditRecordResult = AuditRecord.readFromResult(auditDir)
        val auditRecord = if (auditRecordResult is Ok) {
            auditRecordResult.unwrap()
        } else {
            println( auditRecordResult.toString() )
            fail()
        }

        auditRecord.enterMvrs(sampledMvrs)
        done = workflow.runAuditRound(auditRound)

        println("runAudit $roundIdx done=$done took ${roundStopwatch.elapsed(TimeUnit.MILLISECONDS)} ms")
        writeAuditRoundJsonFile(auditRound, publish.auditRoundFile(roundIdx))

        println(auditRound.show())
        // workflow.showResults(indices.size)
    }
    return done
}