package org.cryptobiotic.util

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.PollingMode
import org.cryptobiotic.rlauxe.belgium.belgianElectionMap
import org.cryptobiotic.rlauxe.belgium.createBelgiumElection
import org.cryptobiotic.rlauxe.boulder.createBoulderElection
import org.cryptobiotic.rlauxe.corla.createColoradoElection
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvFile
import org.cryptobiotic.rlauxe.sf.createSfElection
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import org.junit.jupiter.api.Test

class TestGenerateAllUseCases {
    val sfDir = "$testdataDir/cases/sf2024"
    val sfZipFile = "$sfDir/CVR_Export_20241202143051.zip"

    @Test
    fun createBoulder24oa() {
        val auditdir = "$testdataDir/cases/boulder24/oa/audit"

        createBoulderElection(
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            auditdir = auditdir,
            auditType = AuditType.ONEAUDIT,
            contestSampleCutoff = 2500,
            auditSampleCutoff = 5000,
        )
    }

    @Test
    fun createBoulder24clca() { // simulate CVRs
        val auditdir = "$testdataDir/cases/boulder24/clca/audit"
        createBoulderElection(
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            auditdir = auditdir,
            auditType = AuditType.CLCA,
            contestSampleCutoff = 1000,
            auditSampleCutoff = 2000,
        )
    }

    @Test
    fun createColoradoClca() {
        val topdir = "$testdataDir/cases/corla/clca"
        val detailXmlFile = "src/test/data/corla/2024election/detail.xml"
        val contestRoundFile = "src/test/data/corla/2024audit/round1/contest.csv"
        val precinctFile = "src/test/data/corla/2024election/2024GeneralPrecinctLevelResults.zip"

        createColoradoElection(topdir, "$topdir/audit",
            detailXmlFile, contestRoundFile, precinctFile, auditType = AuditType.CLCA,
            hasSingleCardStyle=true, pollingMode=null)
    }

    @Test
    fun testCreateColoradoPolling() {
        val topdir = "$testdataDir/cases/corla/polling"
        val detailXmlFile = "src/test/data/corla/2024election/detail.xml"
        val contestRoundFile = "src/test/data/corla/2024audit/round1/contest.csv"
        val precinctFile = "src/test/data/corla/2024election/2024GeneralPrecinctLevelResults.zip"

        createColoradoElection(topdir, "$topdir/audit",
            detailXmlFile, contestRoundFile, precinctFile, auditType = AuditType.POLLING,
            hasSingleCardStyle = false, pollingMode=PollingMode.withPools)
    }

    @Test
    fun createSFElectionOA() {
        val auditdir = "$testdataDir/cases/sf2024/oa/audit"

        createSfElection(
            auditdir = auditdir,
            AuditType.ONEAUDIT,
            sfZipFile,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = "$sfDir/$cvrExportCsvFile",
            contestSampleCutoff = 2500,
            auditSampleCutoff = 5000,
        )
    }

    @Test
    fun createSFElectionClca() {
        val auditdir = "$testdataDir/cases/sf2024/clca/audit"

        createSfElection(
            auditdir = auditdir,
            AuditType.CLCA,
            sfZipFile,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = "$sfDir/$cvrExportCsvFile",
            contestSampleCutoff = 1000,
            auditSampleCutoff = 2000,
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