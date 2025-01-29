package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TestPollingWorkflow {

   // @Test
    fun testPollingNoStyleRepeat() {
        repeat(100) { testPollingNoStyle() }
    }

    @Test
    fun testPollingNoStyle() {
        val auditConfig = AuditConfig(AuditType.POLLING, hasStyles=false, seed = 12356667890L, quantile=.80, nsimEst=10)

        // each contest has a specific margin between the top two vote getters.
        val N = 100000
        val ncontests = 11
        val nbs = 4
        val marginRange= 0.05 .. 0.10
        val underVotePct= 0.02..0.02
        val phantomPct= 0.005..0.005
        val test = MultiContestTestData(ncontests, nbs, N, marginRange =marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomPct)
        val contests: List<Contest> = test.contests

        println("Start testPollingNoStyle N=$N")
        contests.forEach{ println(" $it")}
        println()

        // Synthetic cvrs for testing reflecting the exact contest votes. In practice, we dont actually have the cvrs.
        val (testCvrs, ballotManifest) = test.makeCvrsAndBallotManifest(auditConfig.hasStyles)

        val testMvrs = testCvrs
        val workflow = PollingWorkflow(auditConfig, contests, ballotManifest, testCvrs.size)
        runWorkflow("testPollingNoStyle", workflow, testMvrs)
    }

    // @Test
    fun testPollingWithStyleRepeat() {
        repeat(100) { testPollingWithStyle() }
    }

    @Test
    fun testPollingWithStyle() {
        val auditConfig = AuditConfig(AuditType.POLLING, hasStyles=true, seed = 12356667890L, quantile=.80, nsimEst=10)

        // each contest has a specific margin between the top two vote getters.
        val N = 50000
        val ncontests = 11
        val nbs = 4
        val marginRange= 0.05 .. 0.10
        val underVotePct= 0.02 .. 0.02
        val phantomPct= 0.005 .. 0.005
        val test = MultiContestTestData(ncontests, nbs, N, marginRange =marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomPct)

        val contests: List<Contest> = test.contests
        contests.forEachIndexed { idx, contest ->
            val nvotes = contest.votes.map{ it.value }.sum()
            val fcontest = test.fcontests[idx]
            println(" $contest")
            val Nc = contest.Nc.toDouble()
            print("    phantomCount=${fcontest.phantomCount} (${df(fcontest.phantomCount/Nc)})")
            print(" underCount=${fcontest.underCount} (${df(fcontest.underCount/Nc)})")
            print(" nvotes=${nvotes} (${df(nvotes/Nc)})")
            println()
            assertEquals(contest.Nc, fcontest.phantomCount + fcontest.underCount + nvotes)
        }
        println()
        // Synthetic cvrs for testing reflecting the exact contest votes. In production, we dont actually have the cvrs.
        val (testCvrs, ballotManifest) = test.makeCvrsAndBallotManifest(auditConfig.hasStyles)
        val testMvrs = testCvrs

        val workflow = PollingWorkflow(auditConfig, contests, ballotManifest, testCvrs.size)
        runWorkflow("testPollingWithStyle", workflow, testMvrs)
    }

    @Test
    fun testPollingOneContest() {
        val Nc = 50000
        val ncontests = 1
        val nbs = 1
        val marginRange= 0.05 .. 0.05
        val underVotePct= 0.05 .. 0.05
        val phantomPct= 0.005 .. 0.005
        val test = MultiContestTestData(ncontests, nbs, Nc, marginRange =marginRange, underVotePctRange =underVotePct, phantomPctRange =phantomPct)
        test.contests.forEachIndexed { idx, contest ->
            val nvotes = contest.votes.map{ it.value }.sum()
            val fcontest = test.fcontests[idx]
            println(" $contest")
            val Nc = contest.Nc.toDouble()
            print("    phantomCount=${fcontest.phantomCount} (${df(fcontest.phantomCount/Nc)})")
            print(" underCount=${fcontest.underCount} (${df(fcontest.underCount/Nc)})")
            print(" nvotes=${nvotes} (${df(nvotes/Nc)})")
            println()
            assertEquals(contest.Nc, fcontest.phantomCount + fcontest.underCount + nvotes)
        }

        val auditConfig = AuditConfig(AuditType.POLLING, hasStyles=true, seed = 12356667890L, quantile=.80, nsimEst=10)
        val (testCvrs, ballotManifest) = test.makeCvrsAndBallotManifest(auditConfig.hasStyles)
        val workflow = PollingWorkflow(auditConfig, test.contests, ballotManifest, testCvrs.size)

        runWorkflow("testPollingOneContest", workflow, testCvrs)
    }

    /*
    fun runWorkflow(workflow: PollingWorkflow, testMvrs: List<Cvr>) {
        val stopwatch = Stopwatch()

        val previousSamples = mutableSetOf<Int>()
        var rounds = mutableListOf<Round>()
        var roundIdx = 1

        var prevMvrs = emptyList<Cvr>()
        var done = false
        while (!done) {
            val indices = workflow.chooseSamples(prevMvrs, roundIdx)
            val currRound = Round(roundIdx, indices, previousSamples.toSet())
            rounds.add(currRound)
            previousSamples.addAll(indices)

            println("estimateSampleSizes round $roundIdx took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            stopwatch.start()

            val sampledMvrs = indices.map {
                testMvrs[it]
            }

            done = workflow.runAudit(sampledMvrs, roundIdx)
            println("runAudit $roundIdx done=$done took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            prevMvrs = sampledMvrs
            roundIdx++
        }

        rounds.forEach { println(it) }
        workflow.showResults()
    }

     */
}