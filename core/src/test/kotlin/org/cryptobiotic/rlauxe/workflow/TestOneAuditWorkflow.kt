package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test

class TestOneAuditWorkflow {
    val showSamples = false

    @Test
    fun testOneAuditContestSmall() {
        val contestOA = makeContestOA(100, 50, cvrPercent = .80, 0.0, undervotePercent=.0)
        println(contestOA)

        val testCvrs = contestOA.makeTestCvrs() // one for each ballot, with and without CVRS
        val auditConfig = AuditConfig(AuditType.ONEAUDIT, hasStyles=true, seed = Random.nextLong(), quantile=.80, fuzzPct = null, ntrials=10)
        val workflow = OneAuditWorkflow(auditConfig, listOf(contestOA), testCvrs)

        runOneAuditWorkflow(workflow, testCvrs)
    }

    @Test
    fun testOneAuditContest() {
        val contestOA = makeContestOA(25000, 20000, cvrPercent = .70, 0.01, undervotePercent=.01)
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
            val indices = workflow.chooseSamples(prevMvrs, roundIdx, show=true)
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