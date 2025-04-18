package org.cryptobiotic.rlauxe.audit

import kotlin.test.Test
import kotlin.test.assertEquals

class TestAuditConfig {

    @Test
    fun testClcaAudit() {
        val auditConfig = AuditConfig(
            AuditType.CLCA, hasStyles = true, nsimEst = 10, seed=-2417429242344992892,
            clcaConfig = ClcaConfig(ClcaStrategyType.previous)
        )
        val expected =
"""AuditConfig(auditType=CLCA, hasStyles=true, riskLimit=0.05, seed=-2417429242344992892
  nsimEst=10, quantile=0.8, sampleLimit=-1, minRecountMargin=0.005 version=1.0
  ClcaConfig(strategy=previous, simFuzzPct=null, errorRates=null, d=100)
"""
        assertEquals(expected, auditConfig.toString())
        assertEquals("previous", auditConfig.strategy())
    }

    @Test
    fun testPollingAudit() {
        val auditConfig = AuditConfig(
            AuditType.POLLING, hasStyles = true, nsimEst = 10, seed=-2417429242344992892,
        )
        val expected =
            """AuditConfig(auditType=POLLING, hasStyles=true, riskLimit=0.05, seed=-2417429242344992892
  nsimEst=10, quantile=0.8, sampleLimit=-1, minRecountMargin=0.005 version=1.0
  PollingConfig(simFuzzPct=null, d=100)
"""
        assertEquals(expected, auditConfig.toString())
        assertEquals("polling", auditConfig.strategy())
    }

    @Test
    fun testOneAudit() {
        val auditConfig = AuditConfig(
            AuditType.ONEAUDIT, hasStyles = true, nsimEst = 10, seed=-2417429242344992892,
            oaConfig = OneAuditConfig(OneAuditStrategyType.eta0Eps)
        )
        val expected =
            """AuditConfig(auditType=ONEAUDIT, hasStyles=true, riskLimit=0.05, seed=-2417429242344992892
  nsimEst=10, quantile=0.8, sampleLimit=-1, minRecountMargin=0.005 version=1.0
  OneAuditConfig(strategy=eta0Eps, simFuzzPct=null, d=100)
"""
        assertEquals(expected, auditConfig.toString())
        assertEquals("eta0Eps", auditConfig.strategy())
    }

}