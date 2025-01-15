package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.makeContestOA
import org.cryptobiotic.rlauxe.util.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test

class TestOneAuditWorkflow {

    @Test
    fun testOneAuditContestSmall() {
        val contestOA = makeContestOA(100, 50, cvrPercent = .80, 0.0, undervotePercent=.0)
        println(contestOA)

        val testCvrs = contestOA.makeTestCvrs() // one for each ballot, with and without CVRS
        val auditConfig = AuditConfig(AuditType.ONEAUDIT, hasStyles=true, seed = Random.nextLong(), quantile=.80, fuzzPct = null, ntrials=10)
        val workflow = OneAuditWorkflow(auditConfig, listOf(contestOA), testCvrs)

        runWorkflow(workflow, testCvrs)
    }

    @Test
    fun testOneAuditContest() {
        val contestOA = makeContestOA(25000, 20000, cvrPercent = .70, 0.01, undervotePercent=.01)
        println(contestOA)

        val testCvrs = contestOA.makeTestCvrs() // one for each ballot, with and without CVRS
        val auditConfig = AuditConfig(AuditType.ONEAUDIT, hasStyles=true, seed = Random.nextLong(), quantile=.80, fuzzPct = null, ntrials=10)
        val workflow = OneAuditWorkflow(auditConfig, listOf(contestOA), testCvrs)

        runWorkflow(workflow, testCvrs)
    }
}