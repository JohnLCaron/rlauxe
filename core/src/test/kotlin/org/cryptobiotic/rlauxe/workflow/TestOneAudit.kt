package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.oneaudit.makeContestOA
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.oneaudit.makeTestMvrs
import org.cryptobiotic.rlauxe.oneaudit.makeTestMvrsScaled
import kotlin.test.Test

class TestOneAudit {

    @Test
    fun testOneAuditContestSmall() {
        val contestOA = makeContestOA(100, 50, cvrPercent = .80, undervotePercent=.0, phantomPercent = .0)
        println(contestOA)

        val testCvrs = makeTestMvrs(contestOA) // one for each ballot, with and without CVRS
        val auditConfig = AuditConfig(AuditType.ONEAUDIT, hasStyles=true, nsimEst=10)
        val workflow = OneAudit(auditConfig, listOf(contestOA),
            MvrManagerClcaForTesting(testCvrs, testCvrs, auditConfig.seed))

        runAudit("testOneAuditContestSmall", workflow)
    }

    @Test
    fun testOneAuditContest() {
        val contestOA = makeContestOA(25000, 20000, cvrPercent = .70, undervotePercent=.01, phantomPercent = 0.0)
        println(contestOA)

        val testCvrs = makeTestMvrs(contestOA)  // one for each ballot, with and without CVRS
        val auditConfig = AuditConfig(AuditType.ONEAUDIT, hasStyles=true, nsimEst=10)
        val workflow = OneAudit(auditConfig, listOf(contestOA), MvrManagerClcaForTesting(testCvrs, testCvrs, auditConfig.seed))

        runAudit("testOneAuditContest", workflow)
    }

    @Test
    fun testMakeScaledMvrs() {
        val contestOA = makeContestOA(25000, 20000, cvrPercent = .70, undervotePercent=.01, phantomPercent = 0.0)
        println(contestOA)

        val testCvrs = makeTestMvrsScaled(contestOA, sampleLimit = 10000, show = true)
        val auditConfig = AuditConfig(AuditType.ONEAUDIT, hasStyles=true, nsimEst=10)
        val workflow = OneAudit(auditConfig, listOf(contestOA), MvrManagerClcaForTesting(testCvrs, testCvrs, auditConfig.seed))

        runAudit("testOneAuditContest", workflow)
    }

    @Test
    fun testOneAuditContestFuzzed() {
        val mvrFuzzPct = .0123
        val auditConfig = AuditConfig(
            AuditType.ONEAUDIT, hasStyles=true, nsimEst=10,
            oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.reportedMean , simFuzzPct=mvrFuzzPct)
        )
        val contestOA = makeContestOA(25000, 20000, cvrPercent = .70, undervotePercent=.01, phantomPercent = .0)
        println(contestOA)

        val testCvrs = makeTestMvrs(contestOA)  // one for each ballot, with and without CVRS
        val testMvrs = makeFuzzedCvrsFrom(listOf(contestOA), testCvrs, mvrFuzzPct)
        val workflow = OneAudit(auditConfig, listOf(contestOA),
            MvrManagerClcaForTesting(testCvrs, testMvrs, auditConfig.seed))

        runAudit("testOneAuditContest", workflow, quiet=false)
    }

    @Test
    fun testOneAuditContestMax99() {
        val contestOA = makeContestOA(100, 50, cvrPercent = .80, undervotePercent=.0, phantomPercent = .0)
        println(contestOA)

        val testCvrs = makeTestMvrs(contestOA)  // one for each ballot, with and without CVRS
        val auditConfig = AuditConfig(
            AuditType.ONEAUDIT, hasStyles=true, nsimEst=10,
            oaConfig = OneAuditConfig(OneAuditStrategyType.bet99)
        )

        val workflow = OneAudit(auditConfig, listOf(contestOA), MvrManagerClcaForTesting(testCvrs, testCvrs, auditConfig.seed))
        runAudit("testOneAuditContestSmall", workflow)
    }

    @Test
    fun testOneAuditSingleRoundAudit() {
        val auditConfig = AuditConfig(AuditType.ONEAUDIT, hasStyles=true, nsimEst=10)

        val contestOA = makeContestOA(10000, 5000, cvrPercent = .80, undervotePercent=.0, phantomPercent = .0)
        val contests = listOf(contestOA)

        val testCvrs = makeTestMvrs(contestOA)  // one for each ballot, with and without CVRS
        val testMvrs = if (auditConfig.clcaConfig.strategy != ClcaStrategyType.fuzzPct) testCvrs
            else makeFuzzedCvrsFrom(contests, testCvrs, auditConfig.clcaConfig.simFuzzPct!!) // mvrs fuzz = sim fuzz

        val workflow = OneAudit(auditConfig, contests, MvrManagerClcaForTesting(testCvrs, testMvrs, auditConfig.seed))
        val contestRounds = workflow.contestsUA().map { ContestRound(it, 1) }
        runClcaSingleRoundAudit(workflow, contestRounds, auditor = OneAuditAssertionAuditor())
    }
}