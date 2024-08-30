package org.cryptobiotic.rlauxe

import kotlin.test.Test

class TestWorkflow {

    @Test
    fun testPollingWorkflow() {

        // simulated CVRs
        val margin = .05
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

        val audit = PollingAudit(auditType = AuditType.POLLING, contests = contests)

        // this has to be run separately for each assorter, but we want to combine them in practice
        audit.assertions.map { (contest, assertions) ->
            println("Assertions for Contest ${contest.id}")
            assertions.forEach {
                println("  ${it}")

                val cvrSampler = PollWithoutReplacement(cvrs, it.assorter)
                val result = runAlphaMart(
                    drawSample = cvrSampler,
                    maxSamples = N,
                    genRatio = .5 + margin,
                    d = 100,
                    nrepeat = 100,
                )
                println("result:  ${result}")
            }
        }
    }

    @Test
    fun testComparisonWorkflow() {

        // simulated CVRs
        val margin = .05
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

        // Create CVRs for phantom cards
        // skip for now, no phantoms

        val audit = ComparisonAudit(auditType = AuditType.CARD_COMPARISON, contests = contests, cvrs = cvrs)

        // this has to be run separately for each assorter, but we want to combine them in practice
        audit.assertions.map { (contest, assertions) ->
            println("Assertions for Contest ${contest.id}")
            assertions.forEach { it : ComparisonAssertion ->
                println("  ${it}")

                val cvrSampler = CompareWithoutReplacement(cvrs, it.assorter)
                val result = runAlphaMart(
                    drawSample = cvrSampler,
                    maxSamples = N,
                    genRatio = .5 + margin,
                    d = 100,
                    nrepeat = 100,
                )
                println("result:  ${result}")
            }
        }
    }
}