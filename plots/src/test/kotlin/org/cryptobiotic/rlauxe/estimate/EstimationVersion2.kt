package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.ClcaErrorTable
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.TestH0Result
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

// trying to improve on version 1
class EstimationVersion2 {
    val quiet = true
    val nruns = 10  // number of times to run workflow
    val name = "version2Workflow"
    val dirName = "/home/stormy/temp/workflow/$name"

    @Test
    fun testVersion2Workflow() {
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

        doOneContestWorkflow(Nc, margin, mvrsFuzzPct, auditConfig)
    }

    fun doOneContestWorkflow(Nc: Int, margin: Double, mvrsFuzzPct: Double, auditConfig: AuditConfig): ClcaWorkflow {
        val undervotePct = 0.0
        val phantomPct = 0.0

        val sim =
            ContestSimulation.make2wayTestContest(Nc = Nc, margin, undervotePct = undervotePct, phantomPct = phantomPct)
        var testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        var testMvrs = makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, mvrsFuzzPct)
        println("oracle errorRates = ${ClcaErrorTable.getErrorRates(2, mvrsFuzzPct)}")

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
            auditClcaAssertion(auditConfig, contestUA, assertion, sortedPairs, 1, quiet = quiet)
        println("oracle audit")
        workflow.showResults(0)
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
        workflow.showResults(0)

        return workflow
    }

}