package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import kotlin.test.Test

class TestRaireWorkflow {

    @Test
    fun testRaireComparisonWithStyle() {
        testRaireWorkflow(AuditConfig(AuditType.CLCA, hasStyles=true, nsimEst=10))
    }

    @Test
    fun testRaireComparisonNoStyle() {
        testRaireWorkflow(AuditConfig(AuditType.CLCA, hasStyles=false, nsimEst=10))
    }

    fun testRaireWorkflow(auditConfig: AuditConfig) {
        val (rcontest, testCvrs) = makeRaireContest(N=20000, contestId=111, ncands=3, minMargin=.04, quiet = true)
        val workflow = ClcaWorkflow(auditConfig, emptyList(), listOf(rcontest), BallotCardsClcaStart(testCvrs, testCvrs, auditConfig.seed))
        runWorkflow("testRaireWorkflow", workflow)
    }

    @Test
    fun testRaireFuzz() {
        val mvrFuzzPct = .02
        val auditConfig = AuditConfig(AuditType.CLCA, hasStyles=false, nsimEst=10,
            clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, simFuzzPct = mvrFuzzPct))

        val (rcontest: RaireContestUnderAudit, testCvrs: List<Cvr>) = makeRaireContest(N=20000, contestId=111, ncands=4, minMargin=.04, quiet = true)
        val testMvrs = makeFuzzedCvrsFrom(listOf(rcontest.contest), testCvrs, mvrFuzzPct)
        val workflow = ClcaWorkflow(auditConfig, emptyList(), listOf(rcontest), BallotCardsClcaStart(testCvrs, testMvrs, auditConfig.seed))
        runWorkflow("testRaireWorkflow", workflow)
    }

}

