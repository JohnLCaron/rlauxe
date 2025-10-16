package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.persist.*
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class TestPersistentWorkflowClca {
    val auditDir = "/home/stormy/rla/persist/testPersistentWorkflowClca"
    // val auditDir = kotlin.io.path.createTempDirectory().toString()

    @Test
    fun testPersistentWorkflowClca() {
        clearDirectory(Path.of(auditDir))

        val fuzzMvrPct = .01
        val publisher = Publisher(auditDir)
        val auditConfig = AuditConfig(AuditType.CLCA, hasStyles=true, seed = 12356667890L, nsimEst=10, sampleLimit = 1000)
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
        writeAuditableCardCsvFile(cvrsUA, publisher.cardsCsvFile())
        println("write sortedCards to ${publisher.cardsCsvFile()} ")

        // put the testMvrs in the same sorted order as the cvrsUA
        val testMvrsUA = cvrsUA.map { AuditableCard.fromCvr(testMvrs[it.index], it.index, it.prn) }

        // val mvrManagerTest = MvrManagerTestFromRecord(testCvrs, testMvrs, auditConfig.seed) this does the mvrs manipulations internally
        val mvrManager = MvrManagerFromRecord(auditDir)
        var clcaWorkflow = ClcaAudit(auditConfig, contests, emptyList(), mvrManager)

        // these checks may modify the contest status
        checkContestsCorrectlyFormed(auditConfig, clcaWorkflow.contestsUA())
        checkContestsWithCvrs(clcaWorkflow.contestsUA(), CvrIteratorCloser(readCardsCsvIterator(publisher.cardsCsvFile())),
            cardPools = null)

        writeContestsJsonFile(clcaWorkflow.contestsUA(), publisher.contestsFile())
        println("write writeContestsJsonFile to ${publisher.contestsFile()} ")

        var round = 1
        var done = false
        var workflow : RlauxAuditIF = clcaWorkflow
        while (!done) {
            done = runPersistentWorkflowStage(round, workflow, auditDir, testMvrsUA, publisher)
            workflow = PersistentAudit(auditDir, useTest = false)
            round++
        }
        println("------------------ ")
    }
}

fun runPersistentWorkflowStage(roundIdx: Int, workflow: RlauxAuditIF, auditDir: String, testMvrsUA: List<AuditableCard>, publish: Publisher): Boolean {
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
        val auditRecord = AuditRecord.readFrom(auditDir)
        auditRecord.enterMvrs(sampledMvrs)

        done = workflow.runAuditRound(auditRound)

        println("runAudit $roundIdx done=$done took ${roundStopwatch.elapsed(TimeUnit.MILLISECONDS)} ms")
        writeAuditRoundJsonFile(auditRound, publish.auditRoundFile(roundIdx))

        println(auditRound.show())
        // workflow.showResults(indices.size)
    }
    return done
}