package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.TestH0Result
import org.cryptobiotic.rlauxe.sampling.ContestSimulation
import org.cryptobiotic.rlauxe.sampling.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.util.showLast
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.random.Random
import kotlin.test.Test

class EstimationDist {
    val nruns = 10  // number of times to run workflow
    val name = "estVsMarginByStrategy"
    val dirName = "/home/stormy/temp/workflow/$name"

    @Test
    fun testOne() {
        val quiet = false
        val Nc = 50000
        val margin = .005
        val mvrsFuzzPct = .01
        val simFuzzPct = .02

        val undervotePct = 0.0
        val phantomPct = 0.0

        val clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, simFuzzPct)
        val auditConfig = AuditConfig(
            AuditType.CARD_COMPARISON,
            true,
            seed = Random.nextLong(),
            quantile = .50,
            ntrials = 100,
            clcaConfig = clcaConfig,
            version = 1.1,
        )

        val sim =
            ContestSimulation.make2wayTestContest(Nc = Nc, margin, undervotePct = undervotePct, phantomPct = phantomPct)
        var testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        var testMvrs = makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, mvrsFuzzPct)
        println("oracle errorRates = ${ClcaErrorRates.getErrorRates(2, mvrsFuzzPct)}")

        val workflow = ClcaWorkflow(auditConfig, listOf(sim.contest), emptyList(), testCvrs, quiet = quiet)

        // heres the ConsistentSample permutation
        val cvrsUA = workflow.cvrsUA
        val sortedIndices = cvrsUA.indices.sortedBy { cvrsUA[it].sampleNumber() }
        val sortedCvrs = sortedIndices.map { testCvrs[it] }
        val sortedMvrs = sortedIndices.map { testMvrs[it] }
        val sortedPairs: List<Pair<Cvr, Cvr>> = sortedMvrs.zip(sortedCvrs)

        // "oracle" audit
        val contestUA = workflow.contestsUA.first()
        val assertion = contestUA.minClcaAssertion()!!
        val result: TestH0Result = runClcaAssertionAudit(auditConfig, contestUA, assertion, sortedPairs, 1, quiet=quiet)
        workflow.showResults()
        //println("last 20 pvalues= ${showLast(result.pvalues, 20)}")
        //println("last 20 bets= ${showLast(result.bets, 20)}")

        // remove that round
        assertion.roundResults.removeAll { true }
        assertion.proved = false

        // now do the normal workflow
        val previousSamples = mutableSetOf<Int>()
        var rounds = mutableListOf<Round>()
        var roundIdx = 1

        var prevMvrs = emptyList<Cvr>()
        var done = false
        while (!done) {
            val indices = workflow.chooseSamples(prevMvrs, roundIdx, show = false)

            val currRound = Round(roundIdx, indices, previousSamples.toSet())
            rounds.add(currRound)
            previousSamples.addAll(indices)

            val sampledMvrs = indices.map {
                testMvrs[it]
            }

            done = workflow.runAudit(indices, sampledMvrs, roundIdx)
            prevMvrs = sampledMvrs
            roundIdx++
        }

        if (!quiet) {
            rounds.forEach { println(it) }
            workflow.showResults()
        }

    }

}