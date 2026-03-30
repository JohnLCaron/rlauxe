package org.cryptobiotic.create

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.PollingConfig
import org.cryptobiotic.rlauxe.audit.PollingMode
import org.cryptobiotic.rlauxe.audit.SimulationControl
import org.cryptobiotic.rlauxe.belgium.belgianElectionMap
import org.cryptobiotic.rlauxe.belgium.createBelgiumElection
import org.cryptobiotic.rlauxe.boulder.createBoulderElection
import org.cryptobiotic.rlauxe.corla.createColoradoElection
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvFile
import org.cryptobiotic.rlauxe.sf.createSfElection
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.test.Test

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
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            auditdir = auditdir,
            creation,
            round
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
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            auditdir = auditdir,
            creation,
            round,
        )
    }

    @Test
    fun createColoradoClca() {
        val topdir = "$testdataDir/cases/corla/clca"
        val detailXmlFile = "src/test/data/corla/2024election/detail.xml"
        val contestRoundFile = "src/test/data/corla/2024audit/round1/contest.csv"
        val precinctFile = "src/test/data/corla/2024election/2024GeneralPrecinctLevelResults.zip"

        val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 10, estPercentile = listOf(42, 55, 67)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 10000, auditSampleCutoff = 20000),
            ClcaConfig(), null)

        createColoradoElection(topdir, "$topdir/audit",
            detailXmlFile, contestRoundFile, precinctFile,
            null, creation, round)
    }

    @Test
    fun createColoradoPollingPools() {
        val topdir = "$testdataDir/cases/corla/polling"
        val detailXmlFile = "src/test/data/corla/2024election/detail.xml"
        val contestRoundFile = "src/test/data/corla/2024audit/round1/contest.csv"
        val precinctFile = "src/test/data/corla/2024election/2024GeneralPrecinctLevelResults.zip"

        val creation = AuditCreationConfig(AuditType.POLLING, riskLimit=.03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 10, estPercentile = listOf(50, 80)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 200000, auditSampleCutoff = 100000),
            null, PollingConfig())

        createColoradoElection(topdir, "$topdir/audit",
            detailXmlFile, contestRoundFile, precinctFile,
            pollingMode=PollingMode.withPools, creation, round)
    }

    @Test
    fun createColoradoPollingBatches() {
        val topdir = "$testdataDir/cases/corla/polling2"
        val detailXmlFile = "src/test/data/corla/2024election/detail.xml"
        val contestRoundFile = "src/test/data/corla/2024audit/round1/contest.csv"
        val precinctFile = "src/test/data/corla/2024election/2024GeneralPrecinctLevelResults.zip"

        val creation = AuditCreationConfig(AuditType.POLLING, riskLimit=.03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 10, estPercentile = listOf(50, 80)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 200000, auditSampleCutoff = 100000),
            null, PollingConfig())

        createColoradoElection(topdir, "$topdir/audit",
            detailXmlFile, contestRoundFile, precinctFile,
            pollingMode=PollingMode.withBatches, creation, round)
    }

    @Test // too long - fix
    fun createColoradoPollingWithoutBatches() {
        val topdir = "$testdataDir/cases/corla/polling3"
        val detailXmlFile = "src/test/data/corla/2024election/detail.xml"
        val contestRoundFile = "src/test/data/corla/2024audit/round1/contest.csv"
        val precinctFile = "src/test/data/corla/2024election/2024GeneralPrecinctLevelResults.zip"

        val creation = AuditCreationConfig(AuditType.POLLING, riskLimit=.03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 10, estPercentile = listOf(50, 80)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 200000, auditSampleCutoff = 100000),
            null, PollingConfig())

        createColoradoElection(topdir, "$topdir/audit",
            detailXmlFile, contestRoundFile, precinctFile,
            pollingMode=PollingMode.withoutBatches, creation, round,
            startFirstRound = false,
        )
    }

    @Test
    fun makeSFElectionOA() {
        val auditdir = "$testdataDir/cases/sf2024/oa/audit"

        val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit=.05, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 22),
            ContestSampleControl(minRecountMargin = .005, minMargin=0.0, contestSampleCutoff = 2500, auditSampleCutoff = 5000),
            ClcaConfig(fuzzMvrs=.001), null)

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
            ClcaConfig(fuzzMvrs=.001), null)

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
        val allmvrs = mutableMapOf<String, Pair<Int, Int>>()
        belgianElectionMap.keys.forEachIndexed { idx, name ->
            allmvrs[name] =  createBelgiumElection(name, idx+1)
        }
        allmvrs.forEach {
            val pct = (100.0 * it.value.second) / it.value.first.toDouble()
            println("${sfn(it.key, 15)}: Nc= ${trunc(it.value.first.toString(), 10)} " +
                    " nmvrs= ${trunc(it.value.second.toString(), 6)} pct= ${dfn(pct, 2)} %")
        }
    }

}