package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.estimate.*
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.workflow.AuditConfig
import org.cryptobiotic.rlauxe.workflow.AuditType
import org.cryptobiotic.rlauxe.workflow.PollWithoutReplacement
import org.cryptobiotic.rlauxe.workflow.Sampler
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals

class TestAlphaMart {
    @Test
    fun testLamToEta() {
        //    noCvr  eta=0.6677852348993288 mean=0.5 upperBound=1.0 lam=0.6711409395973154 round=0.6669463087248322
        val eta = 0.6677852348993288
        val lam = etaToLam(eta, 0.5, 1.0)
        val roundtrip = lamToEta(lam, 0.5, 1.0)
        println(" eta=$eta lam=$lam round=$roundtrip")
        assertEquals(eta, roundtrip, doublePrecision)
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
        val contestUA = ContestUnderAudit(contest, isComparison = false, hasStyle = true).makePollingAssertions()
        val assorter = contestUA.minPollingAssertion()!!.assorter

        val cvrs = test.makeCvrsFromContests()
        val auditConfig = AuditConfig(AuditType.POLLING, hasStyles=true, nsimEst=10)
        val cvrSampler = PollWithoutReplacement(contestUA.contest as Contest, cvrs, assorter)

        val margin = assorter.reportedMargin()
        println("margin=$margin, mean=${margin2mean(margin)}")

        val result = simulateSampleSizeAlphaMart(
            auditConfig = auditConfig,
            sampleFn = cvrSampler,
            margin = margin,
            upperBound = assorter.upperBound(),
            Nc = contest.Nc,
            moreParameters = mapOf("eta0" to margin2mean(margin)),
        )
        println("simulateSampleSizeAlphaMart = $result")

        val d = 100
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
    estimFn: EstimFn? = null, // if not supplied, use TruncShrinkage
): RunTestRepeatedResult {

    val t = 0.5
    val c = max(eps, ((eta0 - t) / 2))

    val useEstimFn = estimFn ?: TruncShrinkage(drawSample.maxSamples(), true, upperBound = upperBound, d = d, eta0 = eta0, c = c)

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
        margin = mean2margin(eta0),
        Nc=drawSample.maxSamples(), // TODO ??
    )
}