package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.AlphaMart
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.EstimFn
import org.cryptobiotic.rlauxe.core.TruncShrinkage
import org.cryptobiotic.rlauxe.core.eps
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.workflow.AuditConfig
import org.cryptobiotic.rlauxe.workflow.AuditType
import org.cryptobiotic.rlauxe.workflow.checkEquivilentVotes
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestMultiContestTestData {

    // @Test
    fun testMakeSampleDataRepeat() {
        repeat(100) { testMultiContestTestData() }
    }

    @Test
    fun testMultiContestTestData() {
        val N = 50000
        val ncontests = 40
        val nbs = 11
        val marginRange= 0.01 .. 0.04
        val underVotePct= 0.234 .. 0.345
        val phantomRange= 0.001 .. 0.01
        val test = MultiContestTestData(ncontests, nbs, N, marginRange, underVotePct, phantomRange)
        val calcN = test.ballotStylePartition.map { it.value }.sum()
        assertEquals(N, calcN)
        println(test)

        println("test makeContests")
        assertEquals(ncontests, test.contests.size)

        test.contests.forEachIndexed { idx, contest ->
            val fcontest = test.fcontests[idx]
            contest.winners.forEach { winner ->
                contest.losers.forEach { loser ->
                    assertTrue(marginRange.contains(fcontest.margin))
                    assertTrue(underVotePct.contains(fcontest.undervotePct))
                    assertTrue(phantomRange.contains(fcontest.phantomPct))

                    val calcMargin = contest.calcMargin(winner, loser)
                    val margin = (contest.votes[winner]!! - contest.votes[loser]!!) / contest.Nc.toDouble()
                    val calcReportedMargin = contest.calcMargin(winner, loser)
                    assertEquals(margin, calcReportedMargin, doublePrecision)
                    assertEquals(margin, calcMargin, doublePrecision)
                    println(" ${contest.id} fcontest= ${fcontest.margin} contest=$margin")
                }
            }
        }
        println()

        println("test makeCvrsFromContests")
        val cvrs = test.makeCvrsFromContests()
        val votes: Map<Int, Map<Int, Int>> = org.cryptobiotic.rlauxe.util.tabulateVotes(cvrs) // contestId -> candidateId -> nvotes
        votes.forEach { vcontest ->
            println("  tabulate contest $vcontest")
            votes.forEach { vcontest ->
                val contest = test.contests.find { it.id == vcontest.key }!!
                assertTrue(checkEquivilentVotes(vcontest.value, contest.votes))
            }
        }

        val ballots = test.makeBallotsForPolling(true)
        println("test makeBallotsForPolling nballots= ${ballots.size}")

        test.contests.forEachIndexed { idx, contest ->
            val fcontest = test.fcontests[idx]
            assertEquals(contest.Nc, fcontest.ncards + fcontest.phantomCount)
            println(" ${contest.id} ncards ${fcontest.ncards} Nc=${contest.Nc}")
            val ncvr = cvrs.count { it.hasContest(contest.id) }
            assertEquals(contest.Nc, ncvr)
            val nbs = ballots.count { it.hasContest(contest.id) }
            assertEquals(contest.Nc, nbs)
        }
    }

    @Test
    fun testMultiContestTestDataOneContest() {
        val N = 50000
        val ncontests = 1
        val nbs = 1
        val marginRange= 0.04 .. 0.04
        val underVotePct= 0.20 .. 0.20
        val phantomRange= 0.05 .. 0.05
        val test = MultiContestTestData(ncontests, nbs, N, marginRange, underVotePct, phantomRange)
        val calcN = test.ballotStylePartition.map { it.value }.sum()
        assertEquals(N, calcN)
        println(test)

        assertEquals(ncontests, test.contests.size)

        val cvrs = test.makeCvrsFromContests()
        val ballots = test.makeBallotsForPolling(true)

        test.contests.forEachIndexed { idx, contest ->
            val fcontest = test.fcontests[idx]
            assertEquals(contest.Nc, fcontest.ncards + fcontest.phantomCount)
            println("contest $contest ncards=${fcontest.ncards}")
            val ncvr = cvrs.count { it.hasContest(contest.id) }
            assertEquals(contest.Nc, ncvr)
            val nbs = ballots.count { it.hasContest(contest.id) }
            assertEquals(contest.Nc, nbs)

            print(" fcontest margin=${df(fcontest.margin)} undervotePct=${fcontest.undervotePct} phantomPct=${fcontest.phantomPct}")
            println(" underCount=${fcontest.underCount} phantomCount=${fcontest.phantomCount}")
            val contestUA = ContestUnderAudit(contest, isComparison = false).makePollingAssertions()
            contestUA.assertions().forEach {
                println("  $it")
            }
        }
    }


    @Test
    fun testRunAlphaMart() {
        val N = 50000
        val ncontests = 1
        val nbs = 1
        val marginRange= 0.01 .. 0.01
        val underVotePct= 0.20 .. 0.20
        val phantomRange= 0.005 .. 0.005
        val test = MultiContestTestData(ncontests, nbs, N, marginRange, underVotePct, phantomRange)

        val contest = test.contests.first()
        val contestUA = ContestUnderAudit(contest, isComparison = false).makePollingAssertions()
        val assorter = contestUA.minPollingAssertion()!!.assorter

        val cvrs = test.makeCvrsFromContests()
        val ballots = test.makeBallotsForPolling(true)

        val cvrSampler = PollWithoutReplacement(contestUA.contest as Contest, cvrs, assorter)

        val d = 100
        val margin = assorter.reportedMargin()
        println("margin=$margin, mean=${margin2mean(margin)}")

        // fun simulateSampleSizeAlphaMart(
        //    auditConfig: AuditConfig,
        //    sampleFn: SampleGenerator,
        //    margin: Double,
        //    upperBound: Double,
        //    Nc: Int,
        //    startingTestStatistic: Double = 1.0,
        //    moreParameters: Map<String, Double> = emptyMap(),
        //): RunTestRepeatedResult
        val auditConfig = AuditConfig(AuditType.POLLING, hasStyles=true, seed = 12356667890L, quantile=.50, fuzzPct = null, ntrials=10)
        val result = simulateSampleSizeAlphaMart(
            auditConfig = auditConfig,
            sampleFn = cvrSampler,
            margin = margin,
            upperBound = assorter.upperBound(),
            Nc = contest.Nc,
            moreParameters = mapOf("eta0" to margin2mean(margin)),
        )
        println("simulateSampleSizeAlphaMart = $result")

        val result2 = runAlphaMartRepeated(
            drawSample = cvrSampler,
            eta0 = margin2mean(margin),
            d = d,
            ntrials = 10,
            upperBound = assorter.upperBound()
        )
        println("runAlphaMartRepeated = $result2")
    }
}

// run AlphaMart with TrunkShrinkage in repeated trials
// this creates the riskTestingFn for you
fun runAlphaMartRepeated(
    drawSample: Sampler,
    // maxSamples: Int,
    eta0: Double,
    d: Int = 500,
    withoutReplacement: Boolean = true,
    ntrials: Int = 1,
    upperBound: Double = 1.0,
    showDetails: Boolean = false,
    estimFn: EstimFn? = null, // if not supplied, use TruncShrinkage
): RunTestRepeatedResult {

    val t = 0.5
    val minsd = 1.0e-6
    val c = max(eps, ((eta0 - t) / 2))

    val useEstimFn = estimFn ?: TruncShrinkage(drawSample.maxSamples(), true, upperBound = upperBound, minsd = minsd, d = d, eta0 = eta0, c = c)

    val alpha = AlphaMart(
        estimFn = useEstimFn,
        N = drawSample.maxSamples(),
        upperBound = upperBound,
        withoutReplacement = withoutReplacement,
    )

    return runTestRepeated(
        drawSample = drawSample,
        terminateOnNullReject = true,
        ntrials = ntrials,
        testFn = alpha,
        testParameters = mapOf("eta0" to eta0, "d" to d.toDouble()),
        showDetails = showDetails,
        margin = mean2margin(eta0),
        Nc=drawSample.maxSamples(), // TODO ??
    )
}