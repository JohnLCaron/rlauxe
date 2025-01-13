package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test

class TestOneAuditWorkflow {
    val showSamples = false

    @Test
    fun testOneAuditContest() {
        val N = 50000
        val ncontests = 1
        val nbs = 1
        val marginRange= 0.05 .. 0.05
        val underVotePct= 0.05 .. 0.05
        val phantomPct= 0.005 .. 0.005

        val contestOA = makeContestOA(23000, 21000, cvrPercent = .70, undervotePercent=.01)
        println(contestOA)

        val testCvrs = contestOA.makeTestCvrs() // one for each ballot, with and without CVRS
        val auditConfig = AuditConfig(AuditType.ONEAUDIT, hasStyles=true, seed = Random.nextLong(), quantile=.80, fuzzPct = null, ntrials=10)
        val workflow = OneAuditWorkflow(auditConfig, listOf(contestOA), testCvrs)

        runOneAuditWorkflow(workflow, testCvrs)
    }

    fun runOneAuditWorkflow(workflow: OneAuditWorkflow, testMvrs: List<Cvr>) {
        val stopwatch = Stopwatch()

        val previousSamples = mutableSetOf<Int>()
        var rounds = mutableListOf<Round>()
        var roundIdx = 1

        var prevMvrs = emptyList<Cvr>()
        var done = false
        while (!done) {
            val indices = workflow.chooseSamples(prevMvrs, roundIdx)
            val currRound = Round(roundIdx, indices, previousSamples.toSet())
            rounds.add(currRound)
            previousSamples.addAll(indices)

            println("estimateSampleSizes round $roundIdx took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            stopwatch.start()

            val sampledMvrs = indices.map {
                testMvrs[it]
            }

            var nsamples = indices.size.toDouble()
            var nocvrs = 0
            println("sampledMvrs")
            sampledMvrs.forEachIndexed { idx, cvr ->
                if (showSamples) println("  $idx ${indices[idx]} = $cvr")
                if (cvr.id == "noCvr") nocvrs++
            }
            println(" nsamples=$nsamples nocvrs=${df(nocvrs/nsamples)} withCvrs=${df((nsamples-nocvrs)/nsamples)}")

            // TODO were not yet using the ONE algorithm, just comparisions that always agree
            done = workflow.runAudit(indices, sampledMvrs, roundIdx)
            println("runAudit $roundIdx done=$done took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            prevMvrs = sampledMvrs
            roundIdx++
        }

        rounds.forEach { println(it) }
        workflow.showResults()
    }
}