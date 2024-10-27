package org.cryptobiotic.rlauxe.shangrla

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TestWorkflow {
/*
    @Test
    fun testWorkflow() {

        val cvrs = makeCvrsByExactCount(listOf(1000, 599))
        println("ncvrs = ${cvrs.size}")

        // Create CVRs for phantom cards
        // skip for now, no phantoms

        val votes: Map<Int, Map<Int, Int>> = tabulateVotes(cvrs) // contest -> candidate -> count
        votes.forEach { key, cands ->
            println("contestIdx ${key} ")
            cands.forEach { println("  ${it}") }
        }

        // TODO not using cards, i think those are in ContestUnderAudit
        val contests: List<Contest> = makeContestsFromCvrs(votes, cardsPerContest(cvrs))

        //// Create audit and assertions for every Contest
        val audit = makeComparisonAudit(contests, cvrs)

        // sets margins on the assertions
        // audit.set_all_margins_from_cvrs(contests, cvrs)
        // println("minimum assorter margin = ${min_margin}")

        audit.assertions.map { (contestId, assertList) ->
            println("Contest ${contestId}")
            assertList.forEach { assert ->
                println("  ${assert}")
            }
        }

        // Set up for sampling
        val sample_size = find_sample_size(contests, cvrs=cvrs)
        println("sample_size = ${sample_size}")
        assertEquals(29, sample_size)

        val cvras: List<CvrUnderAudit> = makeCvras(cvrs, random = secureRandom)

        // Tst 1. suppose there are no errors, so that mvr == cvr
        // Compute the p values
        val p_max = Assertion.set_p_values(contests=contests, mvr_sample=samples, cvr_sample=samples)
        println("p_max = ${p_max}")

    }

 */
}