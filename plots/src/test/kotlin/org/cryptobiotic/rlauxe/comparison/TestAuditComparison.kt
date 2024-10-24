package org.cryptobiotic.rlauxe.comparison

import org.cryptobiotic.rlauxe.core.AuditContest
import org.cryptobiotic.rlauxe.util.ComparisonNoErrors
import org.cryptobiotic.rlauxe.core.ComparisonAssertion
import org.cryptobiotic.rlauxe.core.ComparisonAssorter
import org.cryptobiotic.rlauxe.core.PluralityAssorter
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.core.makeComparisonAudit
import org.cryptobiotic.rlauxe.rlaplots.SRTcsvWriter
import org.cryptobiotic.rlauxe.sim.AlphaComparisonTask
import org.cryptobiotic.rlauxe.sim.RepeatedTaskRunner
import org.cryptobiotic.rlauxe.util.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.sim.runAlphaMartRepeated
import org.junit.jupiter.api.Test

// TODO
class TestAuditComparison {

    @Test
    fun testComparisonWorkflow() {
        // simulated CVRs
        val theta = .51
        val N = 10000

        val cvrs = makeCvrsByExactMean(N, theta)
        println("ncvrs = ${cvrs.size} theta=$theta")

        val contest = AuditContest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listOf( "A", "B"),
            winnerNames = listOf("A"),
        )

        val assort = PluralityAssorter(contest, 0, 1)
        val assortAvg = cvrs.map { assort.assort(it) }.average()
        val cwinner = ComparisonAssorter(contest, assort, assortAvg)
        val cwinnerAvg = cvrs.map { cwinner.bassort(it, it) }.average()

        // Comparison Audit
        val audit = makeComparisonAudit(contests = listOf(contest), cvrs = cvrs)

        // this has to be run separately for each assorter, but we want to combine them in practice
        audit.assertions.map { (contest, assertions) ->
            println("Assertions for Contest ${contest.id}")
            assertions.forEach { it: ComparisonAssertion ->
                println("  ${it}")

                val cvrSampler = ComparisonNoErrors(cvrs, it.assorter)
                val result = runAlphaMartRepeated(
                    drawSample = cvrSampler,
                    maxSamples = N,
                    eta0 = cvrSampler.sampleMean(),
                    d = 100,
                    ntrials = 100,
                    upperBound = it.assorter.upperBound()
                )
                println(result)
            }
        }
    }

    // TODO setting the eta0Factor by hand
    @Test
    fun genAlphaComparisonDValues() {
        val thetas = listOf(.505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        // val thetas = listOf(.501, .502, .503, .504, .505, .506, .507, .508, .51, .52, .53, .54)
        val cvrMeanDiffs = listOf(0.2, 0.1, 0.05, 0.025, 0.01, 0.005, 0.0, -.005, -.01, -.025, -.05, -0.1, -0.2)
        val dlist = listOf(10, 50, 250, 1250)

        val ns = listOf(5000, 10000, 20000)
        val ntrials = 100

        val tasks = mutableListOf<AlphaComparisonTask>()
        var taskIdx = 0
        dlist.forEach { d ->
            thetas.forEach { theta ->
                ns.forEach { N ->
                    cvrMeanDiffs.forEach { cvrMeanDiff ->
                        val cvrMean = theta - cvrMeanDiff
                        val cvrs = makeCvrsByExactMean(N, cvrMean)
                        tasks.add(AlphaComparisonTask(taskIdx++, N, cvrMean, cvrMeanDiff, eta0Factor=1.9, d = d, cvrs = cvrs))
                    }
                }
            }
        }

        val writer = SRTcsvWriter("/home/stormy/temp/sim/dvalues/alphaComparisonDValues.csv")

        val runner = RepeatedTaskRunner()
        val results =  runner.run(tasks, ntrials)

        writer.writeCalculations(results)
        writer.close()
        println("${results.size} results written to ${writer.filename}")
    }

}
