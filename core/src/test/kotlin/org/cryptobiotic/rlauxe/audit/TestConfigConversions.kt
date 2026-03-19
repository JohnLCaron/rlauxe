package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode
import kotlin.test.Test
import kotlin.test.assertEquals

class TestConfigConversions {
    val auditRecords = listOf(
        "$testdataDir/cases/boulder24/oa/audit",
        "$testdataDir/cases/boulder24/clca/audit",
        "$testdataDir/cases/belgium/2024",
        "$testdataDir/cases/sf2024/oa/audit",
        "$testdataDir/cases/sf2024/clca/audit",
        "$testdataDir/cases/corla/polling/audit",
        "$testdataDir/cases/corla/polling2/audit",
        "$testdataDir/cases/corla/polling3/audit",
    )

    @Test
    fun testAuditRecordConfigRoundtrip() {
        auditRecords.forEach {
            println("doing $it")
            val auditRecord = AuditRecord.readFrom(it)!!
            testAuditConfigRoundtrip(it, auditRecord.config)
        }
    }

    @Test
    fun testRound() {
        val auditdir = "$testdataDir/cases/belgium/2024/Namur/audit"
        val auditRecord = AuditRecord.readFrom(auditdir)!!
        testAuditConfigRoundtrip(auditdir, auditRecord.config)
    }

    @Test
    fun testAuditConfigRoundtrip() {
        val config = AuditConfig(
            AuditType.CLCA, nsimEst = 10, seed = -2417429242344992892,
        )
        testAuditConfigRoundtrip("testAuditConfigRoundtrip", config)
    }

    fun testAuditConfigRoundtrip(name: String, config: AuditConfig) {
        val electionInfo = ElectionInfo(name, config.auditType, 42, 11, true, true)
        val config2 = Config.fromAuditConfig(electionInfo, config)
        val roundtrip = config2.toAuditConfig()

        assertEquals(config, roundtrip)
        assertEquals(electionInfo, config2.electionInfo)
    }

    @Test
    fun testAuditConfig2Roundtrip() {
        val electionInfo = ElectionInfo("name", AuditType.CLCA, 42, 11, false, false)

        val target = AuditConfigBuilder(electionInfo)
            .setCreation(
                riskLimit = .042,
                persistedWorkflowMode = PersistedWorkflowMode.testPrivateMvrs,
            )
            .setRoundConfig()
            .setSimulation(nsimEst = 99, estPercentSuccess = listOf(0.42, 0.17), simFuzzPct = .12, mvrFuzz = .01,)
            .setSampleControl(
                minRecountMargin = .0042,
                minMargin = .04,
                maxSamplePct = .50,
                removeMaxContests = 12,
                contestSampleCutoff = 12000,
                auditSampleCutoff = 120000,
            )
            .build()
        testAuditConfig2Roundtrip(electionInfo, target)
    }

    @Test
    fun testAuditConfig2RoundtripCases() {
        val electionInfo = ElectionInfo("corla", AuditType.CLCA, 42, 11, true, true)
        val target = createColoradoElection(electionInfo, removeMaxContests = 11)
        testAuditConfig2Roundtrip(electionInfo, target)

        val election2 = electionInfo.copy(electionName="boulder")
        val target2 = createBoulderConfig(election2,
            minRecountMargin = .015,
            minMargin = 0.02,
            maxSamplePct  = 0.0,
            mvrFuzz = 0.0,
            contestSampleCutoff = 1000,
            auditSampleCutoff = 10000,
            removeMaxContests = 2)
        testAuditConfig2Roundtrip(election2, target2)

        val election3 = electionInfo.copy(electionName="sf", poolsHaveOneCardStyle=false)
        val target3 = createSfElection(election3,
            minRecountMargin = .015,
            estPercentSuccess = listOf(0.42, 0.17),
            minMargin = 0.02,
            mvrFuzz = 0.0,
            contestSampleCutoff = 1000,
            auditSampleCutoff = 10000,
            removeMaxContests = 22)
        testAuditConfig2Roundtrip(election3, target3)
    }
}

fun testAuditConfig2Roundtrip(electionInfo: ElectionInfo, target: Config) {
    val config = target.toAuditConfig()
    val roundtrip = Config.fromAuditConfig(electionInfo, config)

    assertEquals(electionInfo, roundtrip.electionInfo)
    assertEquals(target.electionInfo, roundtrip.electionInfo)
    assertEquals(target.creation, roundtrip.creation)
    assertEquals(target.round!!.simulation, roundtrip.round!!.simulation)
    assertEquals(target.round.sampling, roundtrip.round.sampling)
    assertEquals(target.round.clcaConfig, roundtrip.round.clcaConfig)
    assertEquals(target.round.pollingConfig, roundtrip.round.pollingConfig)
    assertEquals(target.round, roundtrip.round)
    assertEquals(target, roundtrip)
}


fun createBoulderConfig(
    electionInfo: ElectionInfo, // trouble
    //auditdir: String,
    //auditType : AuditType,
    riskLimit: Double = 0.03,
    minRecountMargin: Double = .005,
    minMargin: Double = 0.0,
    maxSamplePct: Double = 0.0,
    //auditConfigIn: AuditConfig? = null,
    mvrFuzz: Double? = null,
    contestSampleCutoff: Int?,
    auditSampleCutoff: Int?,
    removeMaxContests: Int? = null,
): Config {

    return AuditConfigBuilder(electionInfo)
        .setCreation(
            riskLimit = riskLimit,
            persistedWorkflowMode = PersistedWorkflowMode.testPrivateMvrs,
        )
        .setRoundConfig()
        .setSimulation(nsimEst = 20, mvrFuzz = mvrFuzz,)
        .setSampleControl(
            minRecountMargin = minRecountMargin,
            minMargin = minMargin,
            maxSamplePct = maxSamplePct,
            removeMaxContests = removeMaxContests,
            contestSampleCutoff = contestSampleCutoff,
            auditSampleCutoff = auditSampleCutoff,
        )
        .build()
}

fun createSfElection(
    //auditdir: String,
    electionInfo: ElectionInfo, // trouble
    //auditConfigIn: AuditConfig? = null,
    // poolsHaveOneCardStyle: Boolean = false,
    estPercentSuccess: List<Double>,
    mvrFuzz: Double? = null,
    minRecountMargin: Double = 0.005,
    minMargin: Double = 0.0,
    contestSampleCutoff: Int?,
    auditSampleCutoff: Int?,
    removeMaxContests: Int? = null,
): Config {

    return AuditConfigBuilder(electionInfo)
        .setCreation(
            riskLimit = .05,
            persistedWorkflowMode = PersistedWorkflowMode.testPrivateMvrs,
        )
        .setRoundConfig()
        .setSimulation(nsimEst = 10, estPercentSuccess = estPercentSuccess, mvrFuzz = mvrFuzz,)
        .setSampleControl(
            minRecountMargin = minRecountMargin,
            minMargin = minMargin,
            removeMaxContests = removeMaxContests,
            contestSampleCutoff = contestSampleCutoff,
            auditSampleCutoff = auditSampleCutoff,
        )
        .build()
}

fun createColoradoElection(
    //topdir: String,
    //auditdir: String,
    electionInfo: ElectionInfo, // trouble
    //auditConfigIn: AuditConfig? = null,
    //auditType : AuditType,
    //hasSingleCardStyle: Boolean,
    //pollingMode: PollingMode?,
    //startFirstRound: Boolean = true,
    removeMaxContests: Int? = null,
): Config {

    return when (electionInfo.auditType) {
        AuditType.CLCA -> AuditConfigBuilder(electionInfo)
            .setCreation(
                riskLimit = .03,
                persistedWorkflowMode = PersistedWorkflowMode.testPrivateMvrs,
            )
            .setRoundConfig()
            .setSimulation(nsimEst = 10, estPercentSuccess = listOf(0.42, 0.55))
            .setSampleControl(
                minRecountMargin = .005,
                removeMaxContests = removeMaxContests,
                contestSampleCutoff = 10000,
                auditSampleCutoff = 20000,
            )
            .build()

        AuditType.POLLING -> AuditConfigBuilder(electionInfo)
            .setCreation(
                riskLimit = .03,
                persistedWorkflowMode = PersistedWorkflowMode.testPrivateMvrs,
            )
            .setRoundConfig()
            .setSimulation(nsimEst = 20)
            .setSampleControl(
                minRecountMargin = .005,
                contestSampleCutoff = 20000,
                auditSampleCutoff = 100000,
                removeMaxContests = removeMaxContests,
            )
            .build()

        else -> throw RuntimeException("oneAudit not supported at this time")
    }
}