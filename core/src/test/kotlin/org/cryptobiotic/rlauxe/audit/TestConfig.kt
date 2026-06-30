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
  ElectionInfo(electionName='testing', auditType=CLCA, totalCardCount=42, contestCount=1, cvrsContainUndervotes=true, pollingMode=null, mvrSource=testClcaSimulated, metadata={}), 
  AuditCreationConfig(auditType=CLCA, riskLimit=0.05, seed=-1, riskMeasuringSampleLimit=null, other={}), 
  ContestSampleControl(minRecountMargin=0.005, minMargin=0.0, minSize=null, maxSamplePct=0.0, contestSampleCutoff=2000, auditSampleCutoff=10000, other={}, sampling=consistent))
  SimulationControl(nsimTrials=10, estPercentile=[50, 80], simFuzzPct=null, simulationStrategy=optimistic), 
  ClcaConfig(strategy=generalAdaptive, fuzzMvrs=null, d=100, maxLoss=0.9624175929935999, apriori=TausRates(rates={})) )
"""
        assertEquals(expected, config.toString())
    }

    @Test
    fun testClcaAudit() {
        val config = Config.from(AuditType.CLCA, nsimTrials = 11, simFuzzPct=.002, fuzzMvrs=.001, riskLimit=.10).replaceSeed(-1)
        val expected =
"""Config(
  ElectionInfo(electionName='testing', auditType=CLCA, totalCardCount=42, contestCount=1, cvrsContainUndervotes=true, pollingMode=null, mvrSource=testClcaSimulated, metadata={}), 
  AuditCreationConfig(auditType=CLCA, riskLimit=0.1, seed=-1, riskMeasuringSampleLimit=null, other={}), 
  ContestSampleControl(minRecountMargin=0.005, minMargin=0.0, minSize=null, maxSamplePct=0.0, contestSampleCutoff=2000, auditSampleCutoff=10000, other={}, sampling=consistent))
  SimulationControl(nsimTrials=11, estPercentile=[50, 80], simFuzzPct=0.002, simulationStrategy=optimistic), 
  ClcaConfig(strategy=generalAdaptive, fuzzMvrs=0.001, d=100, maxLoss=0.9624175929935999, apriori=TausRates(rates={})) )
"""
        assertEquals(expected, config.toString())
    }

    @Test
    fun testPollingAudit() {
        val electionInfo= ElectionInfo("testPollingAudit", AuditType.POLLING, 4200, 11, pollingMode= PollingMode.withBatches)
        val config = Config.from(electionInfo, nsimTrials = 100, simFuzzPct=.002, fuzzMvrs=.001).replaceSeed(-1)
        val expected =
            """Config(
  ElectionInfo(electionName='testPollingAudit', auditType=POLLING, totalCardCount=4200, contestCount=11, cvrsContainUndervotes=true, pollingMode=withBatches, mvrSource=testPrivateMvrs, metadata={}), 
  AuditCreationConfig(auditType=POLLING, riskLimit=0.05, seed=-1, riskMeasuringSampleLimit=null, other={}), 
  ContestSampleControl(minRecountMargin=0.005, minMargin=0.0, minSize=null, maxSamplePct=0.0, contestSampleCutoff=10000, auditSampleCutoff=10000, other={}, sampling=consistent))
  SimulationControl(nsimTrials=100, estPercentile=[50, 80], simFuzzPct=0.002, simulationStrategy=optimistic), 
  PollingConfig(d=100) )
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
            SimulationControl(nsimTrials = 1),
            ContestSampleControl.NONE,
            ClcaConfig(fuzzMvrs=0.0, apriori = TausRates(mapOf("win-oth" to .0099))), null)
        val config = Config(electionInfo, creation, round, "42.99").replaceSeed(-1)
        val expected =
            """Config(
  ElectionInfo(electionName='testSoftParams', auditType=CLCA, totalCardCount=4200, contestCount=11, cvrsContainUndervotes=true, pollingMode=null, mvrSource=testClcaSimulated, metadata={}), 
  AuditCreationConfig(auditType=CLCA, riskLimit=0.045, seed=-1, riskMeasuringSampleLimit=1000, other={}), 
  ContestSampleControl(minRecountMargin=0.0, minMargin=0.0, minSize=null, maxSamplePct=0.0, contestSampleCutoff=null, auditSampleCutoff=null, other={}, sampling=consistent))
  SimulationControl(nsimTrials=1, estPercentile=[50, 80], simFuzzPct=null, simulationStrategy=optimistic), 
  ClcaConfig(strategy=generalAdaptive, fuzzMvrs=0.0, d=100, maxLoss=0.9624175929935999, apriori=TausRates(rates={win-oth=0.0099})) )
"""
        assertEquals(expected, config.toString())
    }

    @Test
    fun testOneAudit() {
        val electionInfo= ElectionInfo("testOneAudit", AuditType.ONEAUDIT, 4200, 11, )
        val config = Config.from(electionInfo, nsimTrials = 101, simFuzzPct=.0021, fuzzMvrs=.0011).replaceSeed(-1)
        val expected =
            """Config(
  ElectionInfo(electionName='testOneAudit', auditType=ONEAUDIT, totalCardCount=4200, contestCount=11, cvrsContainUndervotes=true, pollingMode=null, mvrSource=testPrivateMvrs, metadata={}), 
  AuditCreationConfig(auditType=ONEAUDIT, riskLimit=0.05, seed=-1, riskMeasuringSampleLimit=null, other={}), 
  ContestSampleControl(minRecountMargin=0.005, minMargin=0.0, minSize=null, maxSamplePct=0.0, contestSampleCutoff=2000, auditSampleCutoff=10000, other={}, sampling=consistent))
  SimulationControl(nsimTrials=101, estPercentile=[50, 80], simFuzzPct=0.0021, simulationStrategy=optimistic), 
  ClcaConfig(strategy=generalAdaptive, fuzzMvrs=0.0011, d=100, maxLoss=0.9624175929935999, apriori=TausRates(rates={})) )
"""
        assertEquals(expected, config.toString())
    }

}