package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.raire.*
import kotlin.test.Test

class TestRaireWorkflow {

    @Test
    fun testRaireComparisonWithStyle() {
        testRaireWorkflow(AuditConfig(AuditType.CARD_COMPARISON, hasStyles=true, seed = 12356667890L, ntrials=10))
    }

    @Test
    fun testRaireComparisonNoStyle() {
        testRaireWorkflow(AuditConfig(AuditType.CARD_COMPARISON, hasStyles=false, seed = 123568667890L, ntrials=10))
    }

    fun testRaireWorkflow(auditConfig: AuditConfig) {
        val (rcontest, cvrs) = makeRaireContest(N=20000, margin=.01)
        val workflow = ClcaWorkflow(auditConfig, emptyList(), listOf(rcontest), cvrs)
        val nassertions = rcontest.assertions.size

        runWorkflow("testRaireWorkflow", workflow, cvrs)
    }

}

