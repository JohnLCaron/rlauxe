package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.workflow.StartTestBallotCardsClca
import kotlin.test.Test

class TestRaireAudit {

    @Test
    fun testRaireClcaWithStyle() {
        testRaireWorkflow(AuditConfig(AuditType.CLCA, hasStyles=true, nsimEst=10))
    }

    @Test
    fun testRaireClcaNoStyle() {
        testRaireWorkflow(AuditConfig(AuditType.CLCA, hasStyles=false, nsimEst=10))
    }

    fun testRaireWorkflow(auditConfig: AuditConfig) {
        val (rcontest, testCvrs) = simulateRaireTestData(N=20000, contestId=111, ncands=3, minMargin=.04, quiet = true)
        val workflow = ClcaAudit(auditConfig, emptyList(), listOf(rcontest), StartTestBallotCardsClca(testCvrs, testCvrs, auditConfig.seed))
        runAudit("testRaireWorkflow", workflow)
    }

    @Test
    fun testRaireFuzz() {
        val mvrFuzzPct = .02
        val auditConfig = AuditConfig(
            AuditType.CLCA, hasStyles=false, nsimEst=10,
            clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, simFuzzPct = mvrFuzzPct)
        )

        val (rcontest: RaireContestUnderAudit, testCvrs: List<Cvr>) = simulateRaireTestData(N=20000, contestId=111, ncands=4, minMargin=.04, quiet = true)
        val testMvrs = makeFuzzedCvrsFrom(listOf(rcontest.contest), testCvrs, mvrFuzzPct)
        val workflow = ClcaAudit(auditConfig, emptyList(), listOf(rcontest), StartTestBallotCardsClca(testCvrs, testMvrs, auditConfig.seed))
        runAudit("testRaireWorkflow", workflow)
    }

}

