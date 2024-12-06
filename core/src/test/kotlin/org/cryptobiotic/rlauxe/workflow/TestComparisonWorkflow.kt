package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import org.cryptobiotic.rlauxe.sampling.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class TestComparisonWorkflow {

    //@Test
    fun testComparisonWithStyleRepeat() {
        repeat(100) { testComparisonWithStyle() }
    }

   // @Test
    fun testComparisonNoStyleRepeat() {
        repeat(100) { testComparisonNoStyle() }
    }

    @Test
    fun testComparisonWithStyle() {
        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, hasStyles=true, seed = 12356667890L, quantile=.50, fuzzPct = 0.01)
        val N = 50000
        val testData = MultiContestTestData(11, 4, N)
        testComparisonWorkflow(auditConfig, N, testData)
    }

    @Test
    fun testComparisonNoStyle() {
        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, hasStyles=false, seed = 12356667890L, quantile=.50, fuzzPct = 0.01)
        val N = 50000
        val testData = MultiContestTestData(11, 4, N)
        testComparisonWorkflow(auditConfig, N, testData)
    }

    fun testComparisonWorkflow(auditConfig: AuditConfig, N: Int, testData: MultiContestTestData) {
        val contests: List<Contest> = testData.makeContests()
        println("Start testComparisonWorkflow N=$N")
        contests.forEach{ println(" $it")}
        println()

        // Synthetic cvrs for testing reflecting the exact contest votes.
        val cvrs = testData.makeCvrsFromContests()

        // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
        val fuzzedMvrs: List<Cvr> = makeFuzzedCvrsFrom(contests, cvrs, auditConfig.fuzzPct!!)

        val workflow = ComparisonWorkflow(auditConfig, contests, emptyList(), cvrs)
        val stopwatch = Stopwatch()

        var prevMvrs = emptyList<CvrIF>()
        val previousSamples = mutableSetOf<Int>()
        var rounds = mutableListOf<Round>()
        var roundIdx = 1

        var done = false
        while (!done) {
            val indices = workflow.chooseSamples(prevMvrs, roundIdx)
            val currRound = Round(roundIdx, indices, previousSamples)
            rounds.add(currRound)
            previousSamples.addAll(indices)

            println("$roundIdx choose ${indices.size} samples, new=${currRound.newSamples} took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            stopwatch.start()

            val sampledMvrs = indices.map { fuzzedMvrs[it] }

            done = workflow.runAudit(indices, sampledMvrs, roundIdx)
            println("runAudit $roundIdx done=$done took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            prevMvrs = sampledMvrs
            roundIdx++
        }

        rounds.forEach { println(it) }
        workflow.showResults()
    }

}