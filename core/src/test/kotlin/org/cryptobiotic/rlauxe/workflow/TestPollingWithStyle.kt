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
    fun testPollingWithStyleRepeat() {
        repeat(100) { testPollingWithStyle() }
    }

    @Test
    fun testPollingWithStyle() {
        val stopwatch = Stopwatch()
        val auditConfig = AuditConfig(AuditType.POLLING, riskLimit=0.05, seed = 12356667890L, quantile=.80, fuzzPct = .01)

        // each contest has a specific margin between the top two vote getters.
        val test = MultiContestTestData(20, 11, 50000, minMargin = .03)
        val contests: List<Contest> = test.makeContests()
        println("Start testPollingWithStyle")
        contests.forEach{ println(" $it")}
        println()

        // Synthetic cvrs for testing reflecting the exact contest votes. In practice, we dont actually have the cvrs.
        val testCvrs = test.makeCvrsFromContests()
        val ballots = test.makeBallotsForPolling()

        // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
        val testMvrs: List<Cvr> = makeFuzzedCvrsFrom(contests, testCvrs, auditConfig.fuzzPct!!)

        val workflow = PollingWithStyle(auditConfig, contests, BallotManifest(ballots, test.ballotStyles))
        println("initialize took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
        stopwatch.start()

        val previousSamples = mutableSetOf<Int>()
        var rounds = mutableListOf<Round>()
        var roundIdx = 1

        var prevMvrs = emptyList<CvrIF>()
        var done = false
        while (!done) {
            val indices = workflow.chooseSamples(prevMvrs, roundIdx)
            val currRound = Round(roundIdx, indices, previousSamples)
            rounds.add(currRound)
            previousSamples.addAll(indices)

            println("estimateSampleSizes round $roundIdx took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            stopwatch.start()

            val sampledMvrs = indices.map {
                testMvrs[it]
            }

            done = workflow.runAudit(sampledMvrs)
            println("runAudit $roundIdx done=$done took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            prevMvrs = sampledMvrs
            roundIdx++
        }

        rounds.forEach { println(it) }
        workflow.showResults()
    }
}