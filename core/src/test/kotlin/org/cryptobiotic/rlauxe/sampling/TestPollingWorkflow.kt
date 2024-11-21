package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class TestPollingWorkflow {

    val showContests = false

    @Test
    fun testWorkflow() {
        val stopwatch = Stopwatch()
        val auditConfig = AuditConfig(AuditType.POLLING, riskLimit=0.05, seed = 12356667890L, quantile=.50)

        val margin = .01
        val N = 20000

        // hmmm, use these for simulation? in practice, we dont actually have any cvrs.
        val cvrs = makeCvrsByExactMean(N, margin2mean(margin))

        // count actual votes
        val votes: Map<Int, Map<Int, Int>> = tabulateVotes(cvrs) // contest -> candidate -> count
        if (showContests) {
            votes.forEach { key, cands ->
                println("contest ${key} ")
                cands.forEach { println("  ${it} ${it.value.toDouble() / cvrs.size}") }
            }
        }

        // make contests from cvrs
        val contests: List<Contest> = makeContestsFromCvrs(votes, cardsPerContest(cvrs))

        val workflow = PollingWorkflow(auditConfig, contests, cvrs, mapOf(339 to cvrs.size))
        println("initialize took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
        stopwatch.start()

        var done = false
        var prevMvrs = emptyList<CvrIF>()
        var round = 0
        while (!done) {
            val indices = workflow.chooseSamples(prevMvrs, round)
            println("$round chooseSamples ${indices.size} took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)}\n")
            // println("$round samples=${indices}")
            stopwatch.start()

            val sampledMvrs = indices.map { cvrs[it] }

            done = workflow.runAudit(indices, sampledMvrs)
            println("$round runAudit took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            prevMvrs = sampledMvrs
            round++
        }
    }
}