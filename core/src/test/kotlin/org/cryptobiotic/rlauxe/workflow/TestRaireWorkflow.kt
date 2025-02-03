package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.raire.*
import kotlin.test.Test

class TestRaireWorkflow {

    @Test
    fun testRaireComparisonWithStyle() {
        testRaireWorkflow(AuditConfig(AuditType.CARD_COMPARISON, hasStyles=true, nsimEst=10))
    }

    @Test
    fun testRaireComparisonNoStyle() {
        testRaireWorkflow(AuditConfig(AuditType.CARD_COMPARISON, hasStyles=false, nsimEst=10))
    }

    fun testRaireWorkflow(auditConfig: AuditConfig) {
        val (rcontest, cvrs) = makeRaireContest(N=20000, minMargin=.04, quiet = true)
        val workflow = ClcaWorkflow(auditConfig, emptyList(), listOf(rcontest), cvrs)
        runWorkflow("testRaireWorkflow", workflow, cvrs)
    }

}

