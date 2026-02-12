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
  minRecountMargin=0.005 removeTooManyPhantoms=false contestSampleCutoff=30000 removeCutoffContests=false
  nsimEst=10, quantile=0.8, simFuzzPct=null, simulationStrategy=optimistic, mvrFuzzPct=0.0,
  ClcaConfig(strategy=generalAdaptive2, fuzzMvrs=null, d=100, maxLoss=0.9, cvrsContainUndervotes=true, apriori=TausRates(rates={}))
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
  minRecountMargin=0.005 removeTooManyPhantoms=false contestSampleCutoff=30000 removeCutoffContests=false
  nsimEst=10, quantile=0.8, simFuzzPct=null, simulationStrategy=optimistic, mvrFuzzPct=0.0,
  PollingConfig(d=100)
"""
        assertEquals(expected, config.toString())
        assertEquals("polling", config.strategy())
    }

    @Test
    fun testOneAudit() {
        val config = AuditConfig(
            AuditType.ONEAUDIT, nsimEst = 10, seed=-2417429242344992892,
        )
        val expected =
            """AuditConfig(auditType=ONEAUDIT, riskLimit=0.05, seed=-2417429242344992892 persistedWorkflowMode=testSimulated
  minRecountMargin=0.005 removeTooManyPhantoms=false contestSampleCutoff=30000 removeCutoffContests=false
  nsimEst=10, quantile=0.8, simFuzzPct=null, simulationStrategy=optimistic, mvrFuzzPct=0.0,
  OneAuditConfig(strategy=simulate)
"""
        assertEquals(expected, config.toString())
        assertEquals("simulate", config.strategy())
    }

}