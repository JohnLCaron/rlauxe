package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.BallotManifest
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import org.cryptobiotic.rlauxe.sampling.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class TestPollingWithStyle {

    @Test
    fun testPollingWithStyle() {
        val stopwatch = Stopwatch()
        val auditConfig = AuditConfig(AuditType.POLLING, riskLimit=0.05, seed = 12356667890L, quantile=.80, fuzzPct = .01)

        // each contest has a specific margin between the top two vote getters.
        val test = MultiContestTestData(20, 11, 20000)
        val contests: List<Contest> = test.makeContests()

        // Synthetic cvrs for testing reflecting the exact contest votes. In practice, we dont actually have the cvrs.
        val testCvrs = test.makeCvrsFromContests()
        val ballots = test.makeBallotsForPolling()

        // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
        val testMvrs: List<Cvr> = makeFuzzedCvrsFrom(contests, testCvrs, auditConfig.fuzzPct!!)

        val workflow = PollingWithStyle(auditConfig, contests, BallotManifest(ballots, test.ballotStyles))
        println("initialize took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
        stopwatch.start()

        var done = false
        var prevMvrs = emptyList<CvrIF>()
        var round = 0
        while (!done) {
            val indices = workflow.chooseSamples(prevMvrs, round)
            println("estimateSampleSizes round $round took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            stopwatch.start()

            val sampledMvrs = indices.map {
                testMvrs[it]
            }

            done = workflow.runAudit(sampledMvrs)
            println("runAudit round $round took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            prevMvrs = sampledMvrs
            round++
        }
    }
}