package org.cryptobiotic.rlauxe.core

import org.junit.jupiter.api.Test

class TestWorkflowComparison {

    @Test
    fun testComparisonWorkflow() {
        // simulated CVRs
        val margin = .05
        val N = 10000

        val cvrs = makeCvrsByMargin(N, margin)
        println("ncvrs = ${cvrs.size} margin=$margin")

        // count actual votes
        val votes: Map<Int, Map<Int, Int>> = tabulateVotes(cvrs) // contest -> candidate -> count
        votes.forEach { key, cands ->
            println("contest ${key} ")
            cands.forEach { println("  ${it} ${it.value.toDouble() / cvrs.size}") }
        }

        // make contests from cvrs
        val contests: List<AuditContest> = makeContestsFromCvrs(votes, cardsPerContest(cvrs))
        println("Contests")
        contests.forEach { println("  ${it}") }

        // Create CVRs for phantom cards
        // skip for now, no phantoms

        // Comparison Audit
        val audit = makeComparisonAudit(contests = contests, cvrs = cvrs)

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
                    eta0 = .5 + margin / 2,
                    d = 100,
                    nrepeat = 100,
                )
                println(result)
            }
        }
    }

}

// from SHANGRLA OC_example.ipyn
// CVR.prep_comparison_sample(mvr_sample, cvr_sample, sample_order)  # for comparison audit
//# CVR.prep_polling_sample(mvr_sample, sample_order)  # for polling audit
//#%%
//%%time
//p_max = Assertion.set_p_values(contests=contests, mvr_sample=mvr_sample, cvr_sample=cvr_sample)
//print(f'maximum assertion p-value {p_max}')
//done = audit.summarize_status(contests)
//#%%
//# Log the status of the audit
//audit.write_audit_parameters(contests)