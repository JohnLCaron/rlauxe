package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.ClcaSamplerErrorTracker
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.rlaplots.genericPlotter
import org.cryptobiotic.rlauxe.util.makeContestsFromCvrs
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.workflow.*
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode
import kotlin.test.Test

// show distribution of Clca samplesNeeded estimation vs actual audits
class ClcaDistributions {
    val Nc = 50000
    val nsimEst = 100
    val margin = .02
    val mvrsFuzzPct = .02
    val simFuzzPct = .04

    val name = "estErrorRatesDouble"
    val dirName = "$testdataDir/plots/dist"

    // Used in docs: Under/Over estimating CLCA sample sizes, show distributions

    @Test
    fun plotClcaDistributions() {
        val auditConfig = AuditConfig(AuditType.CLCA, true,
            nsimEst = nsimEst, simFuzzPct = simFuzzPct, quantile = 0.5,
            persistedWorkflowMode =  PersistedWorkflowMode.testSimulated,
            simulationStrategy = SimulationStrategy.regular,
            clcaConfig = ClcaConfig(fuzzMvrs=mvrsFuzzPct),
        )

        // TODO use deciles

        println("doOneHundred")
        val actuals = doOneHundredAudits(Nc, margin, mvrsFuzzPct, auditConfig).sorted()
        val tripleActuals =
            actuals.mapIndexed { idx, y -> Triple((idx + 1).toDouble(), 100.0 * y.toDouble() / Nc, "actual") }

        println("doOneEstSample")
        val estResults: RunRepeatedResult = doOneEstSample(Nc, margin, mvrsFuzzPct, auditConfig).first() // first and only contest
        val estSampleCounts = estResults.sampleCount.sorted()
        val tripleEst =
            estSampleCounts.mapIndexed { idx, y -> Triple((idx + 1).toDouble(), 100.0 * y.toDouble() / Nc, "estimate") }

        plotCumul(
            name,
            dirName,
            "Nc=$Nc margin=$margin mvrsFuzzPct=$mvrsFuzzPct simFuzzPct=$simFuzzPct",
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
    fun doOneEstSample(Nc: Int, margin: Double, mvrsFuzzPct: Double, config: AuditConfig): List<RunRepeatedResult> {
        val undervotePct = 0.0
        val phantomPct = 0.0

        val testCvrs = makeCvrsByExactMean(Nc, margin2mean(margin))
        val contest = makeContestsFromCvrs(testCvrs).first()

        val mvrManager = MvrManagerForTesting(testCvrs, testCvrs, config.seed) // not using the mvrs here
        val workflow = WorkflowTesterClca(config, listOf(contest), emptyList(), mvrManager)
        val contestRounds = workflow.contestsUA().map { ContestRound(it, 1) }
        val auditRound = AuditRound(1, contestRounds = contestRounds, samplePrns = emptyList())

        // just want the sample estimation stuff
        return estimateSampleSizes(
            config,
            auditRound,
            cardManifest = mvrManager.cardManifest(),
            cardPools = emptyList(),
            previousSamples = emptySet(),
        )
    }

    // calculate 100 simulated audits, return "samplesNeeded", single contest, fuzzed, no phantoms
    fun doOneHundredAudits(Nc: Int, margin: Double, mvrsFuzzPct: Double, auditConfig: AuditConfig): List<Int> {
        val undervotePct = 0.0
        val phantomPct = 0.0

        val results = mutableListOf<Int>()
        repeat(100) {
            val (cu, testCvrs) = simulateCvrsWithDilutedMargin(Nc = Nc, margin, undervotePct = undervotePct, phantomPct = phantomPct)
            val testMvrs = makeFuzzedCvrsForClca(listOf(cu.contest.info()), testCvrs, mvrsFuzzPct)

            val ballotCards = MvrManagerForTesting(testCvrs, testMvrs, auditConfig.seed)
            val workflow = WorkflowTesterClca(auditConfig, listOf(cu.contest), emptyList(), ballotCards)

            // heres the ConsistentSample permutation
            //             val sortedIndices = cvrsUA.indices.sortedBy { cvrsUA[it].sampleNumber() }
            val sortedIndices = ballotCards.sortedCards.indices.sortedBy { ballotCards.sortedCards[it].prn } // TODO?
            val sortedCards = sortedIndices.map { AuditableCard.fromCvr(testCvrs[it], it, 0) }
            val sortedMvrs = sortedIndices.map { testMvrs[it] }

            val sortedPairs: List<Pair<Cvr, AuditableCard>> = sortedMvrs.zip(sortedCards)

            // "oracle" audit
            val contestUA = workflow.contestsUA().first()
            val assertionRound = AssertionRound(contestUA.minAssertion()!!, 1, null)

            val cassertion = assertionRound.assertion as ClcaAssertion
            val cassorter = cassertion.cassorter

            val sampler = ClcaSamplerErrorTracker.withMaxSample(
                contestUA.id,
                cassorter,
                sortedPairs,
                maxSampleIndex = null,
            )

            val contestRound = ContestRound(contestUA, listOf(assertionRound), 1)
            ClcaAssertionAuditor().run(auditConfig, contestRound, assertionRound, sampler, 1)

            results.add(assertionRound.auditResult!!.samplesUsed)
        }

        // just want the sample estimation stuff
        return results
    }


}