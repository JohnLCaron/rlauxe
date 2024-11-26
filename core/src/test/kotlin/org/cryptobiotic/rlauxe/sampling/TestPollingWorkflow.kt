package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class TestPollingWorkflow {

    @Test
    fun testPollingWorkflow() {
        val stopwatch = Stopwatch()
        val auditConfig = AuditConfig(AuditType.POLLING, riskLimit=0.05, seed = 12356667890L, quantile=.80, fuzzPct = .01)

        val test = MultiContestTestData(20, 11, 20000)
        val contests: List<Contest> = test.makeContests()
        // contests.forEach { println(it) }

        // in practice, we dont actually have any cvrs.
        val testCvrs = test.makeCvrsFromContests()
        val ballots = test.makeBallots()

        val testMvrs: List<Cvr> = makeFuzzedCvrsFrom(contests, testCvrs, auditConfig.fuzzPct)

        val workflow = PollingWorkflow(auditConfig, contests, ballots)
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

            val sampledMvrs = indices.map {
                testMvrs[it]
            }

            done = workflow.runAudit(sampledMvrs)
            println("$round runAudit took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            prevMvrs = sampledMvrs
            round++
        }
    }
}