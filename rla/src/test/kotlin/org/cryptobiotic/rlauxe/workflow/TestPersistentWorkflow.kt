package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import org.cryptobiotic.rlauxe.sampling.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.util.Publisher
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class TestPersistentWorkflow {
    val topdir = "/home/stormy/temp/persist/testPersistentWorkflow"

    @Test
    fun testPersistentWorkflow() {
        val fuzzMvrs = .01
        val publish = Publisher(topdir)
        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, hasStyles=true, seed = 12356667890L, ntrials=10,
            pollingConfig = PollingConfig(fuzzPct = .01))

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

        val nassertions = workflow.contestsUA.sumOf { it.assertions().size }
        runPersistentWorkflow(publish, workflow, testMvrs, nassertions)
    }

}

fun runPersistentWorkflow(publish: Publisher, workflow: ClcaWorkflow, testMvrs: List<Cvr>, nassertions: Int) {
    val stopwatch = Stopwatch()

    var prevMvrs = emptyList<Cvr>()
    val previousSamples = mutableSetOf<Int>()
    var rounds = mutableListOf<Round>()
    var roundIdx = 1

    var done = false
    while (!done) {
        val roundStopwatch = Stopwatch()
        println("---------------------------")
        val indices = workflow.chooseSamples(prevMvrs, roundIdx, show=true)
        writeSampleIndicesJsonFile(indices, publish.sampleIndicesFile(roundIdx))

        val currRound = Round(roundIdx, indices, previousSamples.toSet())
        rounds.add(currRound)
        previousSamples.addAll(indices)
        println("$roundIdx choose ${indices.size} samples, new=${currRound.newSamples} took ${roundStopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")

        val sampledMvrs = indices.map { testMvrs[it] }
        done = workflow.runAudit(indices, sampledMvrs, roundIdx)
        val auditRound = AuditRound(roundIdx, workflow.contestsUA, done)
        writeAuditRoundJsonFile(auditRound, publish.auditRoundFile(roundIdx))

        println("runAudit $roundIdx done=$done took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
        prevMvrs = sampledMvrs
        roundIdx++
    }

    rounds.forEach { println(it) }
    workflow.showResults()
    println("that took ${stopwatch.tookPer(nassertions, "Assertions")}")
}