package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.oneaudit.makeContestOA
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import kotlin.test.Test

class TestOneAuditWorkflow {

    @Test
    fun testOneAuditContestSmall() {
        val contestOA = makeContestOA(100, 50, cvrPercent = .80, 0.0, undervotePercent=.0, phantomPercent = .0)
        println(contestOA)

        val testCvrs = contestOA.makeTestCvrs() // one for each ballot, with and without CVRS
        val auditConfig = AuditConfig(AuditType.ONEAUDIT, hasStyles=true, nsimEst=10)
        val workflow = OneAuditWorkflow(auditConfig, listOf(contestOA), StartTestBallotCardsClca(testCvrs, testCvrs, auditConfig.seed))

        runWorkflow("testOneAuditContestSmall", workflow)
    }

    @Test
    fun testOneAuditContest() {
        val contestOA = makeContestOA(25000, 20000, cvrPercent = .70, 0.01, undervotePercent=.01, phantomPercent = .0)
        println(contestOA)

        val testCvrs = contestOA.makeTestCvrs() // one for each ballot, with and without CVRS
        val auditConfig = AuditConfig(AuditType.ONEAUDIT, hasStyles=true, nsimEst=10)
        val workflow = OneAuditWorkflow(auditConfig, listOf(contestOA), StartTestBallotCardsClca(testCvrs, testCvrs, auditConfig.seed))

        runWorkflow("testOneAuditContest", workflow)
    }

    @Test
    fun testOneAuditContestFuzzed() {
        val mvrFuzzPct = .0123
        val auditConfig = AuditConfig(AuditType.ONEAUDIT, hasStyles=true, nsimEst=10,
            oaConfig = OneAuditConfig(strategy=OneAuditStrategyType.default , simFuzzPct=mvrFuzzPct)
        )
        val contestOA = makeContestOA(25000, 20000, cvrPercent = .70, 0.01, undervotePercent=.01, phantomPercent = .0)
        println(contestOA)

        val testCvrs = contestOA.makeTestCvrs() // one for each ballot, with and without CVRS
        val testMvrs = makeFuzzedCvrsFrom(listOf(contestOA.makeContest()), testCvrs, mvrFuzzPct)
        val workflow = OneAuditWorkflow(auditConfig, listOf(contestOA), StartTestBallotCardsClca(testCvrs, testMvrs, auditConfig.seed))

        runWorkflow("testOneAuditContest", workflow)
    }

    @Test
    fun testOneAuditContestMax99() {
        val contestOA = makeContestOA(100, 50, cvrPercent = .80, 0.0, undervotePercent=.0, phantomPercent = .0)
        println(contestOA)

        val testCvrs = contestOA.makeTestCvrs() // one for each ballot, with and without CVRS
        val auditConfig = AuditConfig(AuditType.ONEAUDIT, hasStyles=true, nsimEst=10,
            oaConfig = OneAuditConfig(OneAuditStrategyType.max99))

        val workflow = OneAuditWorkflow(auditConfig, listOf(contestOA), StartTestBallotCardsClca(testCvrs, testCvrs, auditConfig.seed))
        runWorkflow("testOneAuditContestSmall", workflow)
    }

    @Test
    fun testOneAuditSingleRoundAudit() {
        val auditConfig = AuditConfig(AuditType.ONEAUDIT, hasStyles=true, nsimEst=10)

        val contestOA = makeContestOA(10000, 5000, cvrPercent = .80, 0.0, undervotePercent=.0, phantomPercent = .0)
        val contests = listOf(contestOA)

        val testCvrs = contestOA.makeTestCvrs() // one for each ballot, with and without CVRS
        val testMvrs = if (auditConfig.clcaConfig.strategy != ClcaStrategyType.fuzzPct) testCvrs
            else makeFuzzedCvrsFrom(contests, testCvrs, auditConfig.clcaConfig.simFuzzPct!!) // mvrs fuzz = sim fuzz

        val workflow = OneAuditWorkflow(auditConfig, contests, StartTestBallotCardsClca(testCvrs, testMvrs, auditConfig.seed))
        val contestRounds = workflow.contestsUA().map { ContestRound(it, 1) }
        runClcaSingleRoundAudit(workflow, contestRounds, auditor = OneAuditClcaAssertion())
    }
}