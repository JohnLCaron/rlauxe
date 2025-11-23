package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.oneaudit.makeOneContestUA
import kotlin.test.Test

class TestOneAudit {

    @Test
    fun testOneAuditContestSmall() {
        val (contestOA, _, testCvrs) = makeOneContestUA(
            100,
            50,
            cvrFraction = .80,
            undervoteFraction = .0,
            phantomFraction = .0
        )
        println(contestOA)

        val config = AuditConfig(AuditType.ONEAUDIT, hasStyle=true, nsimEst=10)
        val workflow = WorkflowTesterOneAudit(config, listOf(contestOA),
            MvrManagerForTesting(testCvrs, testCvrs, config.seed))

        runTestAuditToCompletion("testOneAuditContestSmall", workflow)
    }

    @Test
    fun testOneAuditContest() {
        val (contestOA, _, testCvrs) = makeOneContestUA(
            25000,
            20000,
            cvrFraction = .70,
            undervoteFraction = .01,
            phantomFraction = 0.0
        )
        println(contestOA)

        val config = AuditConfig(AuditType.ONEAUDIT, hasStyle=true, nsimEst=10)
        val workflow = WorkflowTesterOneAudit(config, listOf(contestOA), MvrManagerForTesting(testCvrs, testCvrs, config.seed))

        runTestAuditToCompletion("testOneAuditContest", workflow)
    }

    @Test
    fun testMakeScaledMvrs() {
        val (contestOA, _, testCvrs) = makeOneContestUA(
            25000,
            20000,
            cvrFraction = .70,
            undervoteFraction = .01,
            phantomFraction = 0.0
        )
        println(contestOA)

        val config = AuditConfig(AuditType.ONEAUDIT, hasStyle=true, nsimEst=10)
        val workflow = WorkflowTesterOneAudit(config, listOf(contestOA), MvrManagerForTesting(testCvrs, testCvrs, config.seed))

        runTestAuditToCompletion("testOneAuditContest", workflow)
    }

    @Test
    fun testOneAuditContestMax99() {
        val (contestOA, _, testCvrs) = makeOneContestUA(
            100,
            50,
            cvrFraction = .80,
            undervoteFraction = .0,
            phantomFraction = .0
        )
        println(contestOA)

        val config = AuditConfig(
            AuditType.ONEAUDIT, hasStyle=true, nsimEst=10,
            oaConfig = OneAuditConfig(OneAuditStrategyType.bet99)
        )

        val workflow = WorkflowTesterOneAudit(config, listOf(contestOA), MvrManagerForTesting(testCvrs, testCvrs, config.seed))
        runTestAuditToCompletion("testOneAuditContestSmall", workflow)
    }

/*
    @Test
    fun testOneAuditContestFuzzed() {
        val mvrFuzzPct = .0123
        val config = AuditConfig(
            AuditType.ONEAUDIT, hasStyles=true, nsimEst=10,
            oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.reportedMean , simFuzzPct=mvrFuzzPct)
        )
        val (contestOA, testCvrs) = makeOneContestUA(25000, 20000, cvrPercent = .70, undervotePercent=.01, phantomPercent = .0)
        println(contestOA)

        val testMvrs = makeFuzzedCvrsFrom(listOf(contestOA), testCvrs, mvrFuzzPct)
        val workflow = OneAudit(config, listOf(contestOA),
            MvrManagerClcaForTesting(testCvrs, testMvrs, config.seed))

        runAudit("testOneAuditContest", workflow, quiet=false)
    }

    @Test
    fun testOneAuditSingleRoundAudit() {
        val config = AuditConfig(AuditType.ONEAUDIT, hasStyles=true, nsimEst=10)

        val (contestOA, testCvrs) = makeOneContestUA(10000, 5000, cvrPercent = .80, undervotePercent=.0, phantomPercent = .0)
        val contests = listOf(contestOA)

        val testMvrs = if (config.clcaConfig.strategy != ClcaStrategyType.fuzzPct) testCvrs
            else makeFuzzedCvrsFrom(contests, testCvrs, config.clcaConfig.simFuzzPct!!) // mvrs fuzz = sim fuzz

        val workflow = OneAudit(config, contests, MvrManagerClcaForTesting(testCvrs, testMvrs, config.seed))
        val contestRounds = workflow.contestsUA().map { ContestRound(it, 1) }
        runClcaSingleRoundAudit(workflow, contestRounds, auditor = OneAuditAssertionAuditor())
    }

 */
}