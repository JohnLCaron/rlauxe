package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
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

        val ballotCards = MvrManagerClcaForTesting(testCvrs, testMvrs, auditConfig.seed)
        var clcaWorkflow = ClcaAudit(auditConfig, contests, emptyList(), ballotCards)

        writeAuditableCardCsvFile(ballotCards.cvrsUA, publish.cardsCsvFile())
        writeContestsJsonFile(clcaWorkflow.contestsUA(), publish.contestsFile())

        var round = 1
        var done = false
        var workflow : RlauxAuditIF = clcaWorkflow
        while (!done) {
            done = runPersistentWorkflowStage(round, workflow, ballotCards.mvrsUA, publish)
            workflow = PersistentAudit(topdir)
            round++
        }
    }
}

fun runPersistentWorkflowStage(roundIdx: Int, workflow: RlauxAuditIF, mvrsUA: Iterable<AuditableCard>, publish: Publisher): Boolean {
    val roundStopwatch = Stopwatch()
    var done = false

    val nextRound = workflow.startNewRound()

    if (nextRound.samplePrns.isEmpty()) {
        done = true

    } else {
        writeSampleNumbersJsonFile(nextRound.samplePrns, publish.sampleNumbersFile(roundIdx))

        // TODO updateMvrs I think
        //val sampledMvrs = nextRound.sampleNumbers.map {
        //    testMvrs[it]
        //}
        done = workflow.runAuditRound(nextRound)

        println("runAudit $roundIdx done=$done took ${roundStopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
        writeAuditRoundJsonFile(nextRound, publish.auditRoundFile(roundIdx))

        val sampledMvrus = findSamples(nextRound.samplePrns, mvrsUA.iterator()) // TODO use IteratorCvrsCsvFile?
        writeAuditableCardCsvFile(sampledMvrus, publish.sampleMvrsFile(roundIdx))

        println(nextRound)
        // workflow.showResults(indices.size)
    }

    return done
}