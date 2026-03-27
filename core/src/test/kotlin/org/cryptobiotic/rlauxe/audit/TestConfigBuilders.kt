package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.persist.json.import
import org.cryptobiotic.rlauxe.persist.json.publishJson
import org.cryptobiotic.rlauxe.audit.MvrSource
import kotlin.test.Test
import kotlin.test.assertEquals

class TestConfigBuilders {

    @Test
    fun testCorlaBuilderClca() {
        val electionInfo = ElectionInfo(
            "corla", AuditType.CLCA, 42, 11,
            true, true, pollingMode = null,
            mvrSource = MvrSource.testClcaSimulated
        )
        val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 10, estPercentile = listOf(42, 55, 67)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 10000, auditSampleCutoff = 20000),
            ClcaConfig(), null)

        val target = Config(electionInfo, creation, round)
        testConfigBuilderRoundtrip(electionInfo, target)
    }

    @Test
    fun testCorlaBuilderPolling() {
        val electionInfo = ElectionInfo(
            "corla", AuditType.POLLING, 4262, 11,
            true, true, pollingMode = PollingMode.withBatches
        )
        val creation = AuditCreationConfig(AuditType.POLLING, riskLimit=.03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 20, estPercentile = listOf(50, 80)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 200000, auditSampleCutoff = 100000),
            null, PollingConfig())

        val target = Config(electionInfo, creation, round)
        testConfigBuilderRoundtrip(electionInfo, target)
    }

    //        createSfElection(
    //            auditdir=auditdir,
    //            AuditType.CLCA,
    //            zipFilename,
    //            "ContestManifest.json",
    //            "CandidateManifest.json",
    //            cvrExportCsv = cvrExportCsv,
    //            contestSampleCutoff = 1000,
    //            auditSampleCutoff = 2000,
    //        )
    //        (auditType == AuditType.CLCA) -> AuditConfig(
    //            AuditType.CLCA, riskLimit = .05, nsimEst=20,
    //            minRecountMargin=minRecountMargin,
    //            minMargin=minMargin,
    //            removeMaxContests = removeMaxContests,
    //            contestSampleCutoff = contestSampleCutoff, auditSampleCutoff = auditSampleCutoff,
    //            simFuzzPct=mvrFuzz, persistedWorkflowMode=PersistedWorkflowMode.testPrivateMvrs,
    //            clcaConfig = ClcaConfig(fuzzMvrs=mvrFuzz)
    //        )
    @Test
    fun testSfBuilderClca() {
        val electionInfo = ElectionInfo(
            "sf", AuditType.CLCA, 42222, 111,
            true, false, pollingMode = null
        )
        val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.05, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 22),
            ContestSampleControl(contestSampleCutoff = 1000, auditSampleCutoff = 2000),
            ClcaConfig(fuzzMvrs=.001), null)

        val target = Config(electionInfo, creation, round)
        testConfigBuilderRoundtrip(electionInfo, target)
    }

    @Test
    fun testSfBuilderOA() {
        val electionInfo = ElectionInfo(
            "sf", AuditType.ONEAUDIT, 42222, 111,
            true, false, pollingMode = null
        )
        val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit=.05,)
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 22),
            ContestSampleControl(minRecountMargin = .005, minMargin=0.0, contestSampleCutoff = 2500, auditSampleCutoff = 5000),
            ClcaConfig(fuzzMvrs=.001), null)

        val target = Config(electionInfo, creation, round)
        testConfigBuilderRoundtrip(electionInfo, target)
    }

    @Test
    fun testBoulderBuilderOA() {
        val electionInfo = ElectionInfo(
            "boulder", AuditType.ONEAUDIT, 42222, 111,
            true, true, pollingMode = null
        )
        val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit=.05, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 22),
            ContestSampleControl(minRecountMargin = .005, minMargin=0.0, contestSampleCutoff = 2500, auditSampleCutoff = 5000),
            ClcaConfig(fuzzMvrs=.001), null)

        val target = Config(electionInfo, creation, round)
        testConfigBuilderRoundtrip(electionInfo, target)
    }

    @Test
    fun testBoulderBuilderClca() {
        val electionInfo = ElectionInfo(
            "boulder", AuditType.CLCA, 42222, 111,
            true, true, pollingMode = null
        )
        val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.05, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 20, estPercentile = listOf(42, 55, 67)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 1000, auditSampleCutoff = 2000),
            ClcaConfig(fuzzMvrs=.001), null)

        val target = Config(electionInfo, creation, round)
        testConfigBuilderRoundtrip(electionInfo, target)
    }
}

fun testConfigBuilderRoundtrip(electionInfo: ElectionInfo, target: Config) {
    val json = target.election.publishJson()
    val electionTrip = json.import()
    assertEquals(electionInfo, electionTrip)

    val creationJson = target.creation.publishJson()
    val creationTrip = creationJson.import()
    assertEquals(target.creation, creationTrip)

    val roundJson = target.round.publishJson()
    val roundTrip = roundJson.import()
    assertEquals(target.round, roundTrip)

    val configRound = Config(target.election, creationTrip, roundTrip)
    assertEquals(target, configRound)
}

fun createBoulderConfigBuilder(
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
            MvrSource.testPrivateMvrs,
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

fun createSfConfigBuilder(
    //auditdir: String,
    electionInfo: ElectionInfo, // trouble
    //auditConfigIn: AuditConfig? = null,
    // poolsHaveOneCardStyle: Boolean = false,
    estPercentile: List<Int>,
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
            MvrSource.testPrivateMvrs,
        )
        .setRoundConfig()
        .setSimulation(nsimEst = 10, estPercentile = estPercentile, mvrFuzz = mvrFuzz,)
        .setSampleControl(
            minRecountMargin = minRecountMargin,
            minMargin = minMargin,
            removeMaxContests = removeMaxContests,
            contestSampleCutoff = contestSampleCutoff,
            auditSampleCutoff = auditSampleCutoff,
        )
        .build()
}