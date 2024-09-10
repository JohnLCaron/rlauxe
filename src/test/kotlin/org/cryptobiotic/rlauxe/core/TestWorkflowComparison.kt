package org.cryptobiotic.rlauxe.core

import org.junit.jupiter.api.Test

class TestWorkflowComparison {

    @Test
    fun testComparisonWorkflow() {
        // simulated CVRs
        val theta = .51
        val N = 10000

        val cvrs = makeCvrsByExactTheta(N, theta)
        println("ncvrs = ${cvrs.size} theta=$theta")

        val contest = AuditContest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidates = listOf(0, 1),
            winners = listOf(0),
        )

        val assort = PluralityAssorter(contest, 0, 1)
        val assortAvg = cvrs.map { assort.assort(it) }.average()
        val cwinner = ComparisonAssorter(contest, assort, assortAvg)
        val cwinnerAvg = cvrs.map { cwinner.assort(it, it) }.average()

        // Comparison Audit
        val audit = makeComparisonAudit(contests = listOf(contest), cvrs = cvrs)

        // this has to be run separately for each assorter, but we want to combine them in practice
        audit.assertions.map { (contest, assertions) ->
            println("Assertions for Contest ${contest.id}")
            assertions.forEach { it: ComparisonAssertion ->
                println("  ${it}")

                val cvrSampler = CompareWithoutReplacement(cvrs, it.assorter)
                val result = runAlphaMartRepeated(
                    drawSample = cvrSampler,
                    maxSamples = N,
                    theta = cvrSampler.truePopulationMean(),
                    eta0 = cvrSampler.truePopulationMean(),
                    d = 100,
                    nrepeat = 100,
                )
                println(result)
            }
        }
    }

}
