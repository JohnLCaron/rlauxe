package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.TestH0Result
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.rlaplots.genericPlotter
import org.cryptobiotic.rlauxe.sampling.ContestSimulation
import org.cryptobiotic.rlauxe.sampling.EstimationResult
import org.cryptobiotic.rlauxe.sampling.RunTestRepeatedResult
import org.cryptobiotic.rlauxe.sampling.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.random.Random
import kotlin.test.Test

class EstimationDist {
    val quiet = true
    val nruns = 10  // number of times to run workflow
    val name = "estVsMarginByStrategy"
    val dirName = "/home/stormy/temp/workflow/$name"

    @Test
    fun testOneWorkflow() {
        val Nc = 50000
        val margin = .02
        val mvrsFuzzPct = .02
        val simFuzzPct = .02

        val auditConfig = AuditConfig(
            AuditType.CARD_COMPARISON,
            true,
            quantile = .50,
            nsimEst = 100,
            clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, simFuzzPct),
            version = 2.0,
        )

        doOneWorkflow(Nc, margin, mvrsFuzzPct, auditConfig)
    }

    fun doOneWorkflow(Nc: Int, margin: Double, mvrsFuzzPct: Double, auditConfig: AuditConfig): ClcaWorkflow {
        val undervotePct = 0.0
        val phantomPct = 0.0

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
        val result: TestH0Result =
            runClcaAssertionAudit(auditConfig, contestUA, assertion, sortedPairs, 1, quiet = quiet)
        println("oracle audit")
        workflow.showResults()
        //println("last 20 pvalues= ${showLast(result.pvalues, 20)}")
        //println("last 20 bets= ${showLast(result.bets, 20)}")

        // remove that round
        assertion.roundResults.removeAll { true }
        assertion.status = TestH0Status.InProgress

        // now do the normal workflow
        val previousSamples = mutableSetOf<Int>()
        var rounds = mutableListOf<Round>()
        var roundIdx = 1

        var done = false
        while (!done) {
            val indices = workflow.chooseSamples(roundIdx, show = false)

            val currRound = Round(roundIdx, indices, previousSamples.toSet())
            rounds.add(currRound)
            previousSamples.addAll(indices)

            val sampledMvrs = indices.map {
                testMvrs[it]
            }

            done = workflow.runAudit(indices, sampledMvrs, roundIdx)
            roundIdx++
        }

        rounds.forEach { println(it) }
        workflow.showResults()

        return workflow
    }

    ////////////////////////////////////////////////////////////////////////////////////

    // Used in docs

    @Test
    fun testOneEstSample() {
        val Nc = 50000
        val margin = .02
        val mvrsFuzzPct = .02
        val simFuzzPct = .02

        val auditConfig = AuditConfig(
            AuditType.CARD_COMPARISON,
            true,
            quantile = .50,
            nsimEst = 100,
            clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, simFuzzPct),
            version = 2.0,
        )

        println("doOneHundred")
        val actuals = doOneHundred(Nc, margin, mvrsFuzzPct, auditConfig).sorted()
        val tripleActs =
            actuals.mapIndexed { idx, y -> Triple((idx + 1).toDouble(), 100.0 * y.toDouble() / Nc, "actual") }

        println("oracle")
        val oracles = doOneHundred(
            Nc,
            margin,
            mvrsFuzzPct,
            auditConfig.copy(clcaConfig = ClcaConfig(ClcaStrategyType.oracle))
        ).sorted()
        val tripleOra =
            oracles.mapIndexed { idx, y -> Triple((idx + 1).toDouble(), 100.0 * y.toDouble() / Nc, "oracle") }

        println("doOneEstSample")
        val rr: RunTestRepeatedResult = doOneEstSample(Nc, margin, mvrsFuzzPct, auditConfig).first().repeatedResult
        //println(rr)
        //println(rr.showSampleDist())
        //println(rr.sampleCount.sorted())
        val sdata = rr.sampleCount.sorted()
        val tripleEst =
            sdata.mapIndexed { idx, y -> Triple((idx + 1).toDouble(), 100.0 * y.toDouble() / Nc, "estimate") }

        val name = "estSampleDistributionVs1"
        val dirName = "/home/stormy/temp/workflow/estSampleDistribution2"
        plotCumul(
            name,
            dirName,
            "Nc=$Nc margin=$margin version2 mvrsFuzzPct=$mvrsFuzzPct simFuzzPct=$simFuzzPct",
            tripleEst + tripleOra + tripleActs
        )
    }

    fun plotCumul(name: String, dirName: String, subtitle: String, data: List<Triple<Double, Double, String>>) {

        genericPlotter(
            "Cumulative Sample Distribution",
            subtitle,
            "$dirName/$name",
            data,
            "percentile",
            "sample size % of Nc",
            "cat",
            xfld = { it.first },
            yfld = { it.second },
            catfld = { it.third },
            addPoints = false
        )
    }

    fun doOneEstSample(Nc: Int, margin: Double, mvrsFuzzPct: Double, auditConfig: AuditConfig): List<EstimationResult> {
        val undervotePct = 0.0
        val phantomPct = 0.0

        val sim =
            ContestSimulation.make2wayTestContest(Nc = Nc, margin, undervotePct = undervotePct, phantomPct = phantomPct)
        var testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        var testMvrs = makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, mvrsFuzzPct)
        println("oracle errorRates = ${ClcaErrorRates.getErrorRates(2, mvrsFuzzPct)}")

        val workflow = ClcaWorkflow(auditConfig, listOf(sim.contest), emptyList(), testCvrs, quiet = quiet)

        /* heres the ConsistentSample permutation
        val cvrsUA = workflow.cvrsUA
        val sortedIndices = cvrsUA.indices.sortedBy { cvrsUA[it].sampleNumber() }
        val sortedCvrs = sortedIndices.map { testCvrs[it] }
        val sortedMvrs = sortedIndices.map { testMvrs[it] }
        val sortedPairs: List<Pair<Cvr, Cvr>> = sortedMvrs.zip(sortedCvrs)

        // "oracle" audit
        val contestUA = workflow.contestsUA.first()
        val assertion = contestUA.minClcaAssertion()!!
        val result: TestH0Result = runClcaAssertionAudit(auditConfig, contestUA, assertion, sortedPairs, 1, quiet=quiet)
        println("oracle audit")
        workflow.showResults()

        // remove that round
        assertion.roundResults.removeAll { true }
        assertion.proved = false */

        // just want the sample estimation stuff
        return workflow.estimateSampleSizes(1, show = false)
    }

    fun doOneHundred(Nc: Int, margin: Double, mvrsFuzzPct: Double, auditConfig: AuditConfig): List<Int> {
        val undervotePct = 0.0
        val phantomPct = 0.0

        val results = mutableListOf<Int>()
        repeat(100) {
            val sim =
                ContestSimulation.make2wayTestContest(
                    Nc = Nc,
                    margin,
                    undervotePct = undervotePct,
                    phantomPct = phantomPct
                )
            var testCvrs = sim.makeCvrs() // includes undervotes and phantoms
            var testMvrs = makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, mvrsFuzzPct)
            // println("mvrsFuzzPct=$mvrsFuzzPct errorRates = ${ClcaErrorRates.getErrorRates(2, mvrsFuzzPct)}")

            val workflow = ClcaWorkflow(auditConfig, listOf(sim.contest), emptyList(), testCvrs, quiet = false)

            // heres the ConsistentSample permutation
            val cvrsUA = workflow.cvrsUA
            val sortedIndices = cvrsUA.indices.sortedBy { cvrsUA[it].sampleNumber() }
            val sortedCvrs = sortedIndices.map { testCvrs[it] }
            val sortedMvrs = sortedIndices.map { testMvrs[it] }
            val sortedPairs: List<Pair<Cvr, Cvr>> = sortedMvrs.zip(sortedCvrs)

            // "oracle" audit
            val contestUA = workflow.contestsUA.first()
            val assertion = contestUA.minClcaAssertion()!!
            runClcaAssertionAudit(auditConfig, contestUA, assertion, sortedPairs, 1, quiet = quiet)
            results.add(assertion.roundResults.last().samplesNeeded)
        }

        // just want the sample estimation stuff
        return results
    }


}