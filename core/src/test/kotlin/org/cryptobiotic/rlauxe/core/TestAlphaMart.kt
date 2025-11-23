package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.doublePrecision
import org.cryptobiotic.rlauxe.estimate.*
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.workflow.PollWithoutReplacement
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
        val contestUA = ContestUnderAudit(contest, isClca = false, hasStyle = true).addStandardAssertions()
        val assorter = contestUA.minPollingAssertion()!!.assorter

        val cvrs = test.makeCvrsFromContests()
        val config = AuditConfig(AuditType.POLLING, hasStyle=true, nsimEst=10)
        val cvrSampler = PollWithoutReplacement(contestUA.contest.id, cvrs.zip(cvrs), assorter)

        val eta0 = assorter.reportedMean()
        println("eta0=$eta0, margin=${mean2margin(eta0)}")

        val result = runRepeatedAlphaMart(
            config = config,
            sampleFn = cvrSampler,
            estimFn = null,
            eta0 = assorter.reportedMean(),
            upperBound = assorter.upperBound(),
            N = contestUA.Nb,
            moreParameters = mapOf("eta0" to eta0),
        )
        println("simulateSampleSizeAlphaMart = $result")

        val d = 100
        cvrSampler.reset()
        val result2 = runAlphaMartRepeated(
            drawSample = cvrSampler,
            N=N,
            eta0 = eta0,
            d = d,
            ntrials = 10,
            upperBound = assorter.upperBound()
        )
        println("runAlphaMartRepeated = $result2")
    }
}