package org.cryptobiotic.rlauxe.sf

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvFile
import kotlin.test.Test
import kotlin.test.fail

class MakeSfElection {
    private val logger = KotlinLogging.logger("AuditRecord")

    val sfDir = "$testdataDir/cases/sf2024"
    val castVoteRecordZip = "$sfDir/CVR_Export_20241202143051.zip"
    val cvrExportCsv = "$sfDir/$cvrExportCsvFile"

    @Test
    fun makeSFElectionOA() {
        val auditdir = "$testdataDir/cases/sf2024/oa/audit"

        val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit=.05,)
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 22),
            ContestSampleControl(minRecountMargin = .005, minMargin=0.0, contestSampleCutoff = 2500, auditSampleCutoff = 5000),
            ClcaConfig(), null)

        createSfElection(
            auditdir=auditdir,
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
        val auditdir = "$testdataDir/cases/sf2024/oa/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = false)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun makeSFElectionClca() {
        val auditdir = "$testdataDir/cases/sf2024/clca/audit"

        val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.05, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 22),
            ContestSampleControl(contestSampleCutoff = 1000, auditSampleCutoff = 2000),
            ClcaConfig(fuzzMvrs=.001), null)

        createSfElection(
            auditdir=auditdir,
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
        val auditdir = "$testdataDir/cases/sf2024/oaps/audit"
        val contestManifestFilename = "ContestManifest.json"
        val candidateManifestFile = "CandidateManifest.json"

        val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit=.05,)
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 22),
            ContestSampleControl(minRecountMargin = .005, minMargin=0.0, contestSampleCutoff = 2500, auditSampleCutoff = 5000),
            ClcaConfig(fuzzMvrs=.001), null)

        val mvrSource: MvrSource = MvrSource.testPrivateMvrs


        val election = CreatePrecinctAndStyle(
            castVoteRecordZip,
            contestManifestFilename,
            candidateManifestFile,
            cvrExportCsv,
            auditType = creation.auditType,
            poolsHaveOneCardStyle=true,
            mvrSource = mvrSource
        )

        createElectionRecord(election, auditDir = auditdir)

        val config = Config(election.electionInfo(), creation, round)
        createAuditRecord(config, election, auditDir = auditdir)

        val result = startFirstRound(auditdir)
        if (result.isErr) logger.error{ result.toString() }
    }
}



