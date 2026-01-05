package org.cryptobiotic.rlauxe.audit

import kotlin.test.Test
import kotlin.test.assertEquals

class TestAuditConfig {

    @Test
    fun testClcaAudit() {
        val config = AuditConfig(
            AuditType.CLCA, nsimEst = 10, seed=-2417429242344992892,
        )
        val expected =
"""AuditConfig(auditType=CLCA, riskLimit=0.05, seed=-2417429242344992892 persistedWorkflowMode=testSimulated
  nsimEst=10, quantile=0.8, simFuzzPct=null,
  minRecountMargin=0.005 removeTooManyPhantoms=false contestSampleCutoff=30000 removeCutoffContests=false
  ClcaConfig(strategy=generalAdaptive, fuzzPct=null, d=100, maxRisk=0.9, cvrsContainUndervotes=true)
"""
        assertEquals(expected, config.toString())
        assertEquals("generalAdaptive", config.strategy())
    }

    @Test
    fun testPollingAudit() {
        val config = AuditConfig(
            AuditType.POLLING, nsimEst = 10, seed=-2417429242344992892,
        )
        val expected =
            """AuditConfig(auditType=POLLING, riskLimit=0.05, seed=-2417429242344992892 persistedWorkflowMode=testSimulated
  nsimEst=10, quantile=0.8, simFuzzPct=null,
  minRecountMargin=0.005 removeTooManyPhantoms=false contestSampleCutoff=30000 removeCutoffContests=false
  PollingConfig(d=100)
"""
        assertEquals(expected, config.toString())
        assertEquals("polling", config.strategy())
    }

    @Test
    fun testOneAudit() {
        val config = AuditConfig(
            AuditType.ONEAUDIT, nsimEst = 10, seed=-2417429242344992892,
            oaConfig = OneAuditConfig(OneAuditStrategyType.eta0Eps)
        )
        val expected =
            """AuditConfig(auditType=ONEAUDIT, riskLimit=0.05, seed=-2417429242344992892 persistedWorkflowMode=testSimulated
  nsimEst=10, quantile=0.8, simFuzzPct=null,
  minRecountMargin=0.005 removeTooManyPhantoms=false contestSampleCutoff=30000 removeCutoffContests=false
  OneAuditConfig(strategy=eta0Eps, d=100, useFirst=false)
"""
        assertEquals(expected, config.toString())
        assertEquals("eta0Eps", config.strategy())
    }

}