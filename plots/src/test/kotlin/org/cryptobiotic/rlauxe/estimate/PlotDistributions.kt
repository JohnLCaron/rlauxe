package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.ClcaErrorTable
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.rlaplots.genericPlotter
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test
import kotlin.test.assertNotNull

// show distribution of samplesNeeded estimations
class PlotDistributions {
    val Nc = 50000
    val nruns = 10  // number of times to run workflow
    val nsimEst = 10
    val margin = .02
    val mvrsFuzzPct = .02
    val simFuzzPct = .02

    val name = "estErrorRatesEqual"
    val dirName = "/home/stormy/temp/dist/distSamplesNeeded"

    // Used in docs: Under/Over estimating CLCA sample sizes, show distributions

    @Test
    fun plotDistributions() {
        val auditConfig = AuditConfig(
            AuditType.CLCA,
            true,
            samplePctCutoff = 1.0,
            nsimEst = nsimEst,
            clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, simFuzzPct),
        )

        println("doOneHundred")
        val actuals = doOneHundredAudits(Nc, margin, mvrsFuzzPct, auditConfig).sorted()
        val tripleActuals =
            actuals.mapIndexed { idx, y -> Triple((idx + 1).toDouble(), 100.0 * y.toDouble() / Nc, "actual") }

        println("doOneEstSample")
        val rr: RunTestRepeatedResult = doOneEstSample(Nc, margin, mvrsFuzzPct, auditConfig).first()
        val sdata = rr.sampleCount.sorted()
        val tripleEst =
            sdata.mapIndexed { idx, y -> Triple((idx + 1).toDouble(), 100.0 * y.toDouble() / Nc, "estimate") }

        plotCumul(
            name,
            dirName,
            "Nc=$Nc margin=$margin version1 mvrsFuzzPct=$mvrsFuzzPct simFuzzPct=$simFuzzPct",
            tripleEst + tripleActuals // + tripleOra
        )
    }

    // data:  xvalue, yvalue, category name,
    fun plotCumul(name: String, dirName: String, subtitle: String, data: List<Triple<Double, Double, String>>) {
        genericPlotter(
            titleS = "Cumulative Sample Distribution",
            subtitleS = subtitle,
            writeFile = "$dirName/$name",
            data = data,
            xname = "percentile", xfld = { it.first },
            yname = "sample size % of Nc", yfld = { it.second },
            catName = "cat", catfld = { it.third },
            addPoints = false
        )
    }

    // calculate 100 estimateSampleSizes, return List<EstimationResult>, single contest, no phantoms
    fun doOneEstSample(Nc: Int, margin: Double, mvrsFuzzPct: Double, auditConfig: AuditConfig): List<RunTestRepeatedResult> {
        val undervotePct = 0.0
        val phantomPct = 0.0

        val sim =
            ContestSimulation.make2wayTestContest(Nc = Nc, margin, undervotePct = undervotePct, phantomPct = phantomPct)
        val testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        println("oracle errorRates = ${ClcaErrorTable.getErrorRates(2, mvrsFuzzPct)}")

        val workflow = ClcaWorkflow(auditConfig, listOf(sim.contest), emptyList(), testCvrs)

        val contestRounds = workflow.getContests().map { ContestRound(it, 1) }
        val auditRound = AuditRound(1, contests = contestRounds, sampledIndices = emptyList())

        // just want the sample estimation stuff
        return estimateSampleSizes(
            auditConfig,
            auditRound,
            workflow.cvrs,
        )
    }

    // calculate 100 simulated audits, return "samplesNeeded", single contest, fuzzed, no phantoms
    fun doOneHundredAudits(Nc: Int, margin: Double, mvrsFuzzPct: Double, auditConfig: AuditConfig): List<Int> {
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

            val workflow = ClcaWorkflow(auditConfig, listOf(sim.contest), emptyList(), testCvrs)

            // heres the ConsistentSample permutation
            val cvrsUA = workflow.cvrsUA
            val sortedIndices = cvrsUA.indices.sortedBy { cvrsUA[it].sampleNumber() }
            val sortedCvrs = sortedIndices.map { testCvrs[it] }
            val sortedMvrs = sortedIndices.map { testMvrs[it] }
            val sortedPairs: List<Pair<Cvr, Cvr>> = sortedMvrs.zip(sortedCvrs)

            // "oracle" audit
            val contestUA = workflow.getContests().first()
            val assertionRound = AssertionRound(contestUA.minAssertion()!!, 1, null)

            auditClcaAssertion(auditConfig, contestUA.contest, assertionRound, sortedPairs, 1)
            results.add(assertionRound.auditResult!!.samplesNeeded)
        }

        // just want the sample estimation stuff
        return results
    }


}