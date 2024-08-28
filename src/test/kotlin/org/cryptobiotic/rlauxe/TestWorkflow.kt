package org.cryptobiotic.rlauxe

import kotlin.test.Test
import kotlin.test.assertEquals

class TestWorkflow {

    @Test
    fun testWorkflow() {

        // simulated CVRs
        val margin = .005
        val N = 10000
        val cvrs = makeCvrsByMargin(N, margin)
        println("ncvrs = ${cvrs.size} margin=$margin")

        // count actual votes
        val votes: Map<String, Map<String, Int>> = tabulateVotes(cvrs) // contest -> candidate -> count
        votes.forEach { key, cands ->
            println("contest ${key} ")
            cands.forEach { println("  ${it} ${it.value.toDouble()/cvrs.size}") }
        }

        // make contests from cvrs
        val contests: List<AuditContest> = makeContestsFromCvrs(votes, cardsPerContest(cvrs))
        println("Contests")
        contests.forEach { println("  ${it}") }

        val audit = Audit(auditType = AuditType.CARD_COMPARISON, contests = contests)

        // Create CVRs for phantom cards
        // skip for now, no phantoms

        // sets margins on the assertions
        // audit.set_all_margins_from_cvrs(contests, cvrs)
        // println("minimum assorter margin = ${min_margin}")

        // this has to be run separately for each assorter
        audit.assertions.map { (contest, assertions) ->
            println("Assertions for Contest ${contest.id}")
            assertions.forEach {
                println("  ${it}")

                val cvrSampler = SampleCvrWithoutReplacement(cvrs, it.assorter)
                val result = runAlphaGen(
                    drawSample = cvrSampler,
                    maxSamples = N,
                    genRatio = .5 + margin,
                    d = 100,
                    nrepeat = 1000,
                )
                println("result:  ${result}")
            }
        }



        /* Set up for sampling
        val sample_size = audit.find_sample_size(contests, cvrs=cvrs)
        println("sample_size = ${sample_size}")

        val samples = audit.assign_sample_nums(cvrs, sample_size).toList()

        // Tst 1. suppose there are no errors, so that mvr == cvr
        // Compute the p values
        val p_max = Assertion.set_p_values(contests=contests, mvr_sample=samples, cvr_sample=samples)
        println("p_max = ${p_max}")

        contests.map { contest ->
            println("Assertions for Contest ${contest.id}")
            contest.assertions.forEach { println("  ${it}") }
        }

        assertEquals(29, sample_size)

         */
    }
}