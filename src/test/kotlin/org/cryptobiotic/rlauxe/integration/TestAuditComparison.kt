package org.cryptobiotic.rlauxe.integration

import org.cryptobiotic.rlauxe.core.AuditContest
import org.cryptobiotic.rlauxe.core.ComparisonNoErrors
import org.cryptobiotic.rlauxe.core.ComparisonAssertion
import org.cryptobiotic.rlauxe.core.ComparisonAssorter
import org.cryptobiotic.rlauxe.core.PluralityAssorter
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.core.makeComparisonAudit
import org.cryptobiotic.rlauxe.core.makeCvrsByExactMean
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
            candidates = listOf(0, 1),
            winners = listOf(0),
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

}
