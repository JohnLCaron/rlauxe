package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.irv.*
import kotlin.test.Test

class TestRaireAudit {

    @Test
    fun testRaireClcaWithStyle() {
        testRaireWorkflow(Config.from(AuditType.CLCA, nsimTrials=10))
    }

    fun testRaireWorkflow(auditConfig: Config) {
        val (rcontest, testCvrs) = simulateRaireTestContest(N=20000, contestId=111, ncands=3, minMargin=.04, quiet = true)
        val workflow = WorkflowTesterClca(auditConfig, emptyList(), listOf(rcontest),
            MvrManagerForTesting(testCvrs, testCvrs, auditConfig.creation.seed))
        runTestAuditToCompletion("testRaireWorkflow", workflow)
    }

    @Test
    fun testRaireFuzz() {
        val mvrFuzzPct = .02
        val config = Config.from(AuditType.CLCA, nsimTrials=10, simFuzzPct = mvrFuzzPct, fuzzMvrs = mvrFuzzPct)

        val (rcontest: RaireContestWithAssertions, testCvrs: List<Cvr>) = simulateRaireTestContest(N=20000, contestId=111, ncands=4, minMargin=.04, quiet = true)
        val testMvrs =  makeFuzzedCvrsForClca(listOf(rcontest.contest.info()) , testCvrs, mvrFuzzPct)
        val workflow = WorkflowTesterClca(config, emptyList(), listOf(rcontest),
            MvrManagerForTesting(testCvrs, testMvrs, config.creation.seed))
        runTestAuditToCompletion("testRaireWorkflow", workflow)
    }

}

