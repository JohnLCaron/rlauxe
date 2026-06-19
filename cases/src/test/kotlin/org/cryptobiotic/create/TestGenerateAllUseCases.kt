package org.cryptobiotic.create

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.MvrSource
import org.cryptobiotic.rlauxe.audit.PollingConfig
import org.cryptobiotic.rlauxe.audit.PollingMode
import org.cryptobiotic.rlauxe.audit.Sampling
import org.cryptobiotic.rlauxe.audit.SimulationControl
import org.cryptobiotic.rlauxe.audit.createAuditRecord
import org.cryptobiotic.rlauxe.audit.createElectionRecord
import org.cryptobiotic.rlauxe.audit.startFirstRound
import org.cryptobiotic.rlauxe.belgium.toptopdir
import org.cryptobiotic.rlauxe.boulder.createBoulderElection
import org.cryptobiotic.rlauxe.auditcenter.Colorado2024General
import org.cryptobiotic.rlauxe.corla.createCorlaElection
import org.cryptobiotic.rlauxe.corla.createUniformElection
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvFile
import org.cryptobiotic.rlauxe.sf.CreatePrecinctAndStyle
import org.cryptobiotic.rlauxe.sf.createSfElection
import kotlin.test.Test
import kotlin.test.fail

class TestGenerateAllUseCases {
    val sfDir = "$testdataDir/cases/sf2024"
    val sfZipFile = "$sfDir/CVR_Export_20241202143051.zip"

    @Test
    fun createBoulder24oa() {
        val auditdir = "$testdataDir/cases/boulder24/oa/audit"

        val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit=.05, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 22),
            ContestSampleControl(minRecountMargin = .005, minMargin=0.0, contestSampleCutoff = 2500, auditSampleCutoff = 5000),
            ClcaConfig(fuzzMvrs=.001), null)

        createBoulderElection(
            "2024",
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            auditdir = auditdir,
            creation,
            round,
            distributeOvervotes = listOf(0, 63)
        )
    }

    @Test
    fun createBoulder24clca() { // simulate CVRs
        val auditdir = "$testdataDir/cases/boulder24/clca/audit"

        val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.05, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 20, estPercentile = listOf(42, 55, 67)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 1000, auditSampleCutoff = 2000),
            ClcaConfig(fuzzMvrs=.001), null)

        createBoulderElection(
            "2024",
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            auditdir = auditdir,
            creation,
            round,
            distributeOvervotes = listOf(0, 63)
        )
    }

    // obsolete
    // @Test
    fun makeColoradoClcaUniform() {
        val topdir = "$testdataDir/cases/corla/uniform"

        val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 10, estPercentile = listOf(42, 55, 67)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 10000, auditSampleCutoff = 200000,
                sampling = Sampling.uniform),
            ClcaConfig(), null)

        createUniformElection(topdir, "$topdir/audit",
            Colorado2024General(), creation, round, name = "Corla24Uniform")
    }

    // @Test
    fun makeColoradoClcaConsistent() {
        val topdir = "$testdataDir/cases/corla/consistent"

        val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 10, estPercentile = listOf(42, 55, 67)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 10000, auditSampleCutoff = 200000,
                sampling = Sampling.consistent),
            ClcaConfig(), null)

        createCorlaElection(topdir, "$topdir/audit", Colorado2024General(),
            null, creation, round, name = "Corla24Consistent", startFirstRound = true)
    }

    // @Test
    fun createColoradoClca() {
        val topdir = "$testdataDir/cases/corla/clca"

        val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 10, estPercentile = listOf(42, 55, 67)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 10000, auditSampleCutoff = 20000),
            ClcaConfig(), null)

        createCorlaElection(topdir, "$topdir/audit", Colorado2024General(), null, creation, round)
    }

   //  @Test
    fun createColoradoPollingPools() {
        val topdir = "$testdataDir/cases/corla/polling"

        val creation = AuditCreationConfig(AuditType.POLLING, riskLimit=.03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 10, estPercentile = listOf(50, 80)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 200000, auditSampleCutoff = 100000),
            null, PollingConfig())

        createCorlaElection(topdir, "$topdir/audit", Colorado2024General(),
            pollingMode=PollingMode.withPools, creation, round)
    }

    // @Test
    fun createColoradoPollingBatches() {
        val topdir = "$testdataDir/cases/corla/polling2"

        val creation = AuditCreationConfig(AuditType.POLLING, riskLimit=.03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 10, estPercentile = listOf(50, 80)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 200000, auditSampleCutoff = 100000),
            null, PollingConfig())

        createCorlaElection(topdir, "$topdir/audit", Colorado2024General(),
            pollingMode=PollingMode.withBatches, creation, round)
    }

    // @Test // too long - fix
    fun createColoradoPollingWithoutBatches() {
        val topdir = "$testdataDir/cases/corla/polling3"

        val creation = AuditCreationConfig(AuditType.POLLING, riskLimit=.03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 10, estPercentile = listOf(50, 80)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 200000, auditSampleCutoff = 100000),
            null, PollingConfig())

        createCorlaElection(topdir, "$topdir/audit", Colorado2024General(),
            pollingMode=PollingMode.withoutBatches, creation, round,
            startFirstRound = false,
        )
    }

    // @Test
    fun makeSFPrecinctAndStyleOA() {
        val auditdir = "$testdataDir/cases/sf2024/oaps/audit"
        val contestManifestFilename = "ContestManifest.json"
        val candidateManifestFile = "CandidateManifest.json"

        val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit=.05,)
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 22),
            ContestSampleControl(minRecountMargin = .005, minMargin=0.0, contestSampleCutoff = 2500, auditSampleCutoff = 5000),
            ClcaConfig(), null)

        val mvrSource: MvrSource = MvrSource.testPrivateMvrs


        val election = CreatePrecinctAndStyle(
            sfZipFile,
            contestManifestFilename,
            candidateManifestFile,
            cvrExportCsv = "$sfDir/$cvrExportCsvFile",
            auditType = creation.auditType,
            poolsHaveOneCardStyle=true,
            mvrSource = mvrSource
        )

        createElectionRecord(election, auditDir = auditdir)

        val config = Config(election.electionInfo(), creation, round)
        createAuditRecord(config, election, auditDir = auditdir)

        val result = startFirstRound(auditdir)
        if (result.isErr) {
            println( result.toString() )
            fail()
        }
    }

    @Test
    fun makeSFElectionOA() {
        val auditdir = "$testdataDir/cases/sf2024/oa/audit"

        val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit=.05, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 22),
            ContestSampleControl(minRecountMargin = .005, minMargin=0.0, contestSampleCutoff = 2500, auditSampleCutoff = 5000),
            ClcaConfig(), null)

        createSfElection(
            auditdir=auditdir,
            sfZipFile,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = "$sfDir/$cvrExportCsvFile",
            creation,
            round,
        )
    }

    @Test
    fun makeSFElectionClca() {
        val auditdir = "$testdataDir/cases/sf2024/clca/audit"

        val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.05, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 22),
            ContestSampleControl(contestSampleCutoff = 1000, auditSampleCutoff = 2000),
            ClcaConfig(), null)

        createSfElection(
            auditdir=auditdir,
            sfZipFile,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = "$sfDir/$cvrExportCsvFile",
            creation,
            round,
        )
    }

    @Test
    fun createAllBelgiumElections() {
        org.cryptobiotic.rlauxe.belgium.createAllBelgiumElections(toptopdir)
    }

}