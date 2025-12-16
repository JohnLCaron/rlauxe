package org.cryptobiotic.rlauxe.unittest

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.workflow.ClcaSimulatedErrorRates
import org.cryptobiotic.rlauxe.estimate.MultiContestTestDataP
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.workflow.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestClcaEstimationFailure {

    // @Test
    fun testClcaEstimationFailureRepeat() {
        repeat(111) { testClcaEstimationFailure() }
    }

    @Test
    fun testClcaEstimationFailure() {
        // TODO margin not accounting for phantoms
        val test = MultiContestTestDataP(50, 25, 50000) // , phantomPctRange = 0.0 .. 0.0)
        val testCvrs = test.makeCvrsFromContests()

        val auditConfig = AuditConfig(
            AuditType.CLCA,
            hasStyle = true,
            quantile = .50,     // TODO review
            contestSampleCutoff = 5000,
        )

        val ballotCards = MvrManagerForTesting(testCvrs, testCvrs, auditConfig.seed)
        val workflow = WorkflowTesterClca(auditConfig, test.contests, emptyList(), ballotCards)

        println("\nrunClcaSimulation")
        workflow.contestsUA().forEach { contestUA ->
            contestUA.clcaAssertions.forEach { assertion ->
                runClcaSimulation(testCvrs, contestUA, assertion.cassorter)
            }
        }
        //println("\nchooseSamples")
        //workflow.chooseSamples(1, show = true)
    }

    val debug = false
    fun runClcaSimulation(cvrs: List<Cvr>, contestUA: ContestUnderAudit, cassorter: ClcaAssorter) {
        val contest = contestUA.contest as Contest
        if (debug) println("\n$contest phantomRate=${contest.phantomRate()}")

        val phantomRate = contest.phantomRate()
        val errorRates = PluralityErrorRates(0.0, phantomRate, 0.0, 0.0)
        val sampler = ClcaSimulatedErrorRates(cvrs, contestUA.contest, cassorter, errorRates)
        // if (debug) print(sampler.showFlips())

        sampler.reset()

        val tracker = PluralityErrorTracker(cassorter.noerror())
        while (sampler.hasNext()) { tracker.addSample(sampler.next()) }

        val assorter = cassorter.assorter
        val contestMargin = contest.margin(assorter.winner(), assorter.loser())
        val adjustedMargin = contestMargin - contest.phantomRate()

        val noerror = 1.0 / (2.0 - adjustedMargin)
        val expectedMargin = mean2margin(noerror)
        val actualMargin = mean2margin(tracker.mean())
        if (debug) println("  contestMargin= ${df(contestMargin)} adjustedMargin=${df(adjustedMargin)} expectedMargin=$expectedMargin ClcaSimulationMargin = ${df(actualMargin)}")
        assertTrue( tracker.mean() > .5)
        assertEquals( expectedMargin, actualMargin, .01)
    }

}