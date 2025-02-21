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
        val workflow = OneAuditWorkflow(auditConfig, listOf(contestOA), testCvrs)

        runWorkflow("testOneAuditContestSmall", workflow, testCvrs)
    }

    @Test
    fun testOneAuditContest() {
        val contestOA = makeContestOA(25000, 20000, cvrPercent = .70, 0.01, undervotePercent=.01, phantomPercent = .0)
        println(contestOA)

        val testCvrs = contestOA.makeTestCvrs() // one for each ballot, with and without CVRS
        val auditConfig = AuditConfig(AuditType.ONEAUDIT, hasStyles=true, nsimEst=10)
        val workflow = OneAuditWorkflow(auditConfig, listOf(contestOA), testCvrs)

        runWorkflow("testOneAuditContest", workflow, testCvrs)
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
        val workflow = OneAuditWorkflow(auditConfig, listOf(contestOA), testCvrs)
        val testMvrs = makeFuzzedCvrsFrom(listOf(contestOA.makeContest()), testCvrs, mvrFuzzPct)

        runWorkflow("testOneAuditContest", workflow, testMvrs)
    }

    @Test
    fun testOneAuditContestMax99() {
        val contestOA = makeContestOA(100, 50, cvrPercent = .80, 0.0, undervotePercent=.0, phantomPercent = .0)
        println(contestOA)

        val testCvrs = contestOA.makeTestCvrs() // one for each ballot, with and without CVRS
        val auditConfig = AuditConfig(AuditType.ONEAUDIT, hasStyles=true, nsimEst=10,
            oaConfig = OneAuditConfig(OneAuditStrategyType.max99))

        val workflow = OneAuditWorkflow(auditConfig, listOf(contestOA), testCvrs)
        runWorkflow("testOneAuditContestSmall", workflow, testCvrs)
    }
}