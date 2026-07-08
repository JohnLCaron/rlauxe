package org.cryptobiotic.create

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.MvrSource
import org.cryptobiotic.rlauxe.audit.SimulationControl
import org.cryptobiotic.rlauxe.audit.createAuditRecord
import org.cryptobiotic.rlauxe.audit.createElectionRecord
import org.cryptobiotic.rlauxe.audit.startFirstRound
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvFile
import org.cryptobiotic.rlauxe.sf.CreatePrecinctAndStyle
import org.cryptobiotic.rlauxe.sf.createSfElection
import org.cryptobiotic.rlauxe.testdataDir
import kotlin.test.Test
import kotlin.test.fail

class MakeSfElection {
    private val logger = KotlinLogging.logger("AuditRecord")

    val sfDir = "${testdataDir}/cases/sf2024"
    val castVoteRecordZip = "$sfDir/CVR_Export_20241202143051.zip"
    val cvrExportCsv = "$sfDir/${cvrExportCsvFile}"

    @Test
    fun makeSFElectionOA() {
        val topdir = "${testdataDir}/cases/sf2024/oa"

        val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .05,)
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 22),
            ContestSampleControl(
                minRecountMargin = .005,
                minMargin = 0.0,
                contestSampleCutoff = 2500,
                auditSampleCutoff = 5000
            ),
            ClcaConfig(), null
        )

        createSfElection(
            topdir = topdir,
            castVoteRecordZip,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = cvrExportCsv,
            creation,
            round,
        )
    }

    @Test
    fun testRunVerifySFoa() {
        val topdir = "${testdataDir}/cases/sf2024/oa"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = false)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun makeSFElectionClca() {
        val topdir = "${testdataDir}/cases/sf2024/clca"

        val creation = AuditCreationConfig(AuditType.CLCA, riskLimit = .05,)
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 22),
            ContestSampleControl(contestSampleCutoff = 1000, auditSampleCutoff = 2000),
            ClcaConfig(fuzzMvrs = .001), null
        )

        createSfElection(
            topdir = topdir,
            castVoteRecordZip,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = cvrExportCsv,
            creation,
            round,
        )
    }

    @Test
    fun makePrecinctAndStyleOA() {
        val topdir = "${testdataDir}/cases/sf2024/oaps"
        val contestManifestFilename = "ContestManifest.json"
        val candidateManifestFile = "CandidateManifest.json"

        val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .05,)
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 22),
            ContestSampleControl(
                minRecountMargin = .005,
                minMargin = 0.0,
                contestSampleCutoff = 2500,
                auditSampleCutoff = 5000
            ),
            ClcaConfig(fuzzMvrs = .001), null
        )

        val mvrSource: MvrSource = MvrSource.testPrivateMvrs


        val election = CreatePrecinctAndStyle(
            castVoteRecordZip,
            contestManifestFilename,
            candidateManifestFile,
            cvrExportCsv,
            auditType = creation.auditType,
            poolsHaveOneCardStyle = true,
            mvrSource = mvrSource
        )

        createElectionRecord(election, topdir = topdir)

        val config = Config(election.electionInfo(), creation, round)
        createAuditRecord(config, election, topdir = topdir)

        val result = startFirstRound(topdir)
        if (result.isErr) logger.error{ result.toString() }
    }
}