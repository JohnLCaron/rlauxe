package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.betting.TausRates
import kotlin.test.Test
import kotlin.test.assertEquals

class TestConfig {

    @Test
    fun testClcaDefault() {
        val config = Config.from(AuditType.CLCA).replaceSeed(-1)
        val expected =
            """Config(
  electionInfo=ElectionInfo(electionName=testing, auditType=CLCA, totalCardCount=42, contestCount=1, cvrsContainUndervotes=true, poolsHaveOneCardStyle=null, pollingMode=null, other={}), 
  creation=AuditCreationConfig(auditType=CLCA, riskLimit=0.05, persistedWorkflowMode=testClcaSimulated, seed=-1, riskMeasuringSampleLimit=null, other={}), 
  simulation=SimulationControl(nsimEst=10, estPercentile=[50, 80], simFuzzPct=null, simulationStrategy=optimistic), 
  sampling=ContestSampleControl(minRecountMargin=0.005, minMargin=0.0, maxSamplePct=0.0, contestSampleCutoff=2000, auditSampleCutoff=10000, removeCutoffContests=true, other={}))
  clcaConfig=ClcaConfig(strategy=generalAdaptive, fuzzMvrs=null, d=100, maxLoss=0.9624175929935999, apriori=TausRates(rates={})) )
"""
        assertEquals(expected, config.toString())
    }

    @Test
    fun testClcaAudit() {
        val config = Config.from(AuditType.CLCA, nsimEst = 11, simFuzzPct=.002, fuzzMvrs=.001, riskLimit=.10).replaceSeed(-1)
        val expected =
"""Config(
  electionInfo=ElectionInfo(electionName=testing, auditType=CLCA, totalCardCount=42, contestCount=1, cvrsContainUndervotes=true, poolsHaveOneCardStyle=null, pollingMode=null, other={}), 
  creation=AuditCreationConfig(auditType=CLCA, riskLimit=0.1, persistedWorkflowMode=testClcaSimulated, seed=-1, riskMeasuringSampleLimit=null, other={}), 
  simulation=SimulationControl(nsimEst=11, estPercentile=[50, 80], simFuzzPct=0.002, simulationStrategy=optimistic), 
  sampling=ContestSampleControl(minRecountMargin=0.005, minMargin=0.0, maxSamplePct=0.0, contestSampleCutoff=2000, auditSampleCutoff=10000, removeCutoffContests=true, other={}))
  clcaConfig=ClcaConfig(strategy=generalAdaptive, fuzzMvrs=0.001, d=100, maxLoss=0.9624175929935999, apriori=TausRates(rates={})) )
"""
        assertEquals(expected, config.toString())
    }

    @Test
    fun testPollingAudit() {
        val electionInfo= ElectionInfo("testPollingAudit", AuditType.POLLING, 4200, 11, poolsHaveOneCardStyle=false, pollingMode= PollingMode.withBatches)
        val config = Config.from(electionInfo, nsimEst = 100, simFuzzPct=.002, fuzzMvrs=.001).replaceSeed(-1)
        val expected =
            """Config(
  electionInfo=ElectionInfo(electionName=testPollingAudit, auditType=POLLING, totalCardCount=4200, contestCount=11, cvrsContainUndervotes=true, poolsHaveOneCardStyle=false, pollingMode=withBatches, other={}), 
  creation=AuditCreationConfig(auditType=POLLING, riskLimit=0.05, persistedWorkflowMode=testPrivateMvrs, seed=-1, riskMeasuringSampleLimit=null, other={}), 
  simulation=SimulationControl(nsimEst=100, estPercentile=[50, 80], simFuzzPct=0.002, simulationStrategy=optimistic), 
  sampling=ContestSampleControl(minRecountMargin=0.005, minMargin=0.0, maxSamplePct=0.0, contestSampleCutoff=10000, auditSampleCutoff=10000, removeCutoffContests=true, other={}))
  pollingConfig=PollingConfig(d=100, mode=withPools) )
"""
        assertEquals(expected, config.toString())
    }

    @Test
    fun testSoftParams() {
        // data class ElectionInfo(
        //    val electionName: String,
        //    val auditType: AuditType,
        //    val totalCardCount: Int,    // total cards in the election
        //    val contestCount: Int,
        //
        //    val cvrsContainUndervotes: Boolean = true, // TODO where do we use this ??
        //    val poolsHaveOneCardStyle: Boolean? = null,
        //    val pollingMode: PollingMode? = null,
        val electionInfo= ElectionInfo("testSoftParams", AuditType.CLCA, 4200, 11)
        val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.045, riskMeasuringSampleLimit=1000)
        val round = AuditRoundConfig(
            SimulationControl(nsimEst = 1),
            ContestSampleControl.NONE,
            ClcaConfig(fuzzMvrs=0.0, apriori = TausRates(mapOf("win-oth" to .0099))), null)
        val config = Config(electionInfo, creation, round, "42.99").replaceSeed(-1)
        val expected =
            """Config(
  electionInfo=ElectionInfo(electionName=testSoftParams, auditType=CLCA, totalCardCount=4200, contestCount=11, cvrsContainUndervotes=true, poolsHaveOneCardStyle=null, pollingMode=null, other={}), 
  creation=AuditCreationConfig(auditType=CLCA, riskLimit=0.045, persistedWorkflowMode=testClcaSimulated, seed=-1, riskMeasuringSampleLimit=1000, other={}), 
  simulation=SimulationControl(nsimEst=1, estPercentile=[50, 80], simFuzzPct=null, simulationStrategy=optimistic), 
  sampling=ContestSampleControl(minRecountMargin=0.0, minMargin=0.0, maxSamplePct=0.0, contestSampleCutoff=null, auditSampleCutoff=null, removeCutoffContests=false, other={}))
  clcaConfig=ClcaConfig(strategy=generalAdaptive, fuzzMvrs=0.0, d=100, maxLoss=0.9624175929935999, apriori=TausRates(rates={win-oth=0.0099})) )
"""
        assertEquals(expected, config.toString())
    }

    @Test
    fun testOneAudit() {
        val electionInfo= ElectionInfo("testOneAudit", AuditType.ONEAUDIT, 4200, 11, poolsHaveOneCardStyle=false, pollingMode= PollingMode.withBatches)
        val config = Config.from(electionInfo, nsimEst = 101, simFuzzPct=.0021, fuzzMvrs=.0011).replaceSeed(-1)
        val expected =
            """Config(
  electionInfo=ElectionInfo(electionName=testOneAudit, auditType=ONEAUDIT, totalCardCount=4200, contestCount=11, cvrsContainUndervotes=true, poolsHaveOneCardStyle=false, pollingMode=withBatches, other={}), 
  creation=AuditCreationConfig(auditType=ONEAUDIT, riskLimit=0.05, persistedWorkflowMode=testPrivateMvrs, seed=-1, riskMeasuringSampleLimit=null, other={}), 
  simulation=SimulationControl(nsimEst=101, estPercentile=[50, 80], simFuzzPct=0.0021, simulationStrategy=optimistic), 
  sampling=ContestSampleControl(minRecountMargin=0.005, minMargin=0.0, maxSamplePct=0.0, contestSampleCutoff=2000, auditSampleCutoff=10000, removeCutoffContests=true, other={}))
  clcaConfig=ClcaConfig(strategy=generalAdaptive, fuzzMvrs=0.0011, d=100, maxLoss=0.9624175929935999, apriori=TausRates(rates={})) )
"""
        assertEquals(expected, config.toString())
    }

}