package org.cryptobiotic.rlauxe.clca

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.PrevSamplesWithRates
import org.cryptobiotic.rlauxe.estimate.ClcaFuzzSampler
import org.cryptobiotic.rlauxe.estimate.ContestSimulation
import org.cryptobiotic.rlauxe.estimate.ContestTestData
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import kotlin.test.Test

// theory is that the errorRates are proportional to fuzzPct
// Then p1 = fuzzPct * r1, p2 = fuzzPct * r2, p3 = fuzzPct * r3, p4 = fuzzPct * r4.
// margin doesnt matter (TODO show this)
// TODO: Currently the percentage of ballots with no votes cast for a contest is not well accounted for?

class GenerateClcaErrorTable {
    val showRates = false

    @Test
    fun generateErrorTable() {
        val auditConfig = AuditConfig(AuditType.CLCA, hasStyles = true, seed = 12356667890L, nsimEst = 1000)
        val N = 100000

        // TODO how much do the rates depend on the margin?
        val margin = .05
        val ncands = listOf(2, 3, 4, 5, 6, 7, 8, 9, 10)
        val underVotePcts = listOf(0.01, .05, .1, .2, .5)
        val fuzzPcts = listOf(0.001, .005, .01, .02, .05)

        underVotePcts.forEach { underVotePct ->

            val result = mutableMapOf<Int, List<Double>>()
            println("underVotePct=$underVotePct N=$N ntrials = ${auditConfig.nsimEst}")
            println("| ncand | r2o    | r1o    | r1u    | r2u    |")
            println("|-------|--------|--------|--------|--------|")
            ncands.forEach { ncand ->
                val fcontest = ContestTestData(0, ncand, margin, underVotePct, 0.0)
                fcontest.ncards = N
                val contest = fcontest.makeContest()
                // print("contest votes = ${contest.votes} ")
                val sim = ContestSimulation(contest)

                val sumRForNcand = mutableListOf(0.0, 0.0, 0.0, 0.0)
                fuzzPcts.forEach { fuzzPct ->
                    val sumRForPct = mutableListOf(0.0, 0.0, 0.0, 0.0)

                    repeat(auditConfig.nsimEst) {
                        val cvrs = sim.makeCvrs()
                        val contestUA = ContestUnderAudit(contest, true, hasStyle=true).addStandardAssertions()
                        val minAssert = contestUA.minClcaAssertion()!!
                        val minAssort = minAssert.cassorter

                        val tracker = PrevSamplesWithRates(minAssort.noerror())
                        val sampler = ClcaFuzzSampler(fuzzPct, cvrs, contestUA.contest as Contest, minAssort)
                        while (sampler.hasNext()) {
                            tracker.addSample(sampler.next())
                        }
                        tracker.errorRatesList()
                            .forEachIndexed { idx, rate -> sumRForNcand[idx] = sumRForNcand[idx] + (rate / fuzzPct) }
                        tracker.errorRatesList()
                            .forEachIndexed { idx, rate -> sumRForPct[idx] = sumRForPct[idx] + (rate / fuzzPct) }
                    }
                    if (showRates) {
                        print("   $fuzzPct = [")
                        sumRForPct.forEach { R -> print(" ${df(R / auditConfig.nsimEst)},") }
                        println("]")
                    }
                }
                val avgRforNcand = sumRForNcand.map { it / (auditConfig.nsimEst * fuzzPcts.size) }
                print("| $ncand | ")
                avgRforNcand.forEach { avgR -> print(" ${df(avgR)} |") }
                println()

                result[ncand] = avgRforNcand
            }
            val code = buildString {
                result.toSortedMap().forEach { (key, value) ->
                    append("rrates[$key] = listOf(")
                    value.forEach { append("${dfn(it, 7)}, ") }
                    append(")\n")
                }
            }
            println(code)
        }
    }

}