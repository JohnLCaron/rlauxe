package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ClcaStrategyType
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.raire.*
import kotlin.test.Test

class TestRaireAudit {

    @Test
    fun testRaireClcaWithStyle() {
        testRaireWorkflow(AuditConfig(AuditType.CLCA, nsimEst=10))
    }

    @Test
    fun testRaireClcaNoStyle() { // TODO hasStyle=false
        testRaireWorkflow(AuditConfig(AuditType.CLCA, hasStyle=false, nsimEst=10))
    }

    fun testRaireWorkflow(auditConfig: AuditConfig) {
        val (rcontest, testCvrs) = simulateRaireTestContest(N=20000, contestId=111, ncands=3, minMargin=.04, quiet = true)
        val workflow = WorkflowTesterClca(auditConfig, emptyList(), listOf(rcontest),
            MvrManagerForTesting(testCvrs, testCvrs, auditConfig.seed))
        runTestAuditToCompletion("testRaireWorkflow", workflow)
    }

    @Test
    fun testRaireFuzz() {
        val mvrFuzzPct = .02
        val config = AuditConfig(
            AuditType.CLCA, nsimEst=10, simFuzzPct = mvrFuzzPct,
            clcaConfig = ClcaConfig(ClcaStrategyType.fuzzPct, fuzzPct = mvrFuzzPct)
        )

        val (rcontest: RaireContestWithAssertions, testCvrs: List<Cvr>) = simulateRaireTestContest(N=20000, contestId=111, ncands=4, minMargin=.04, quiet = true)
        val testMvrs = makeFuzzedCvrsFrom(listOf(rcontest.contest), testCvrs, mvrFuzzPct)
        val workflow = WorkflowTesterClca(config, emptyList(), listOf(rcontest),
            MvrManagerForTesting(testCvrs, testMvrs, config.seed))
        runTestAuditToCompletion("testRaireWorkflow", workflow)
    }

}

