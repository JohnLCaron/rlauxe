package org.cryptobiotic.util

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.writeSortedCardsInternalSort
import org.cryptobiotic.rlauxe.boulder.createBoulderElection
import org.cryptobiotic.rlauxe.corla.createColoradoOneAudit
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.cvrExportCsvFile
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import org.cryptobiotic.rlauxe.sf.createSfElection
import org.cryptobiotic.rlauxe.sf.createSfElectionNoStyles
import org.junit.jupiter.api.Test

class TestGenerateAllUseCases {
    val sfDir = "/home/stormy/rla/cases/sf2024"
    val sfZipFile = "$sfDir/CVR_Export_20241202143051.zip"

    @Test
    fun createBoulder24oa() {
        val topdir = "/home/stormy/rla/cases/boulder24/oa"

        createBoulderElection(
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            topdir = topdir,
            isClca = false,
        )

        val publisher = Publisher("$topdir/audit")
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)
    }

    @Test
    fun createBoulder24clca() { // simulate CVRs
        val topdir = "/home/stormy/rla/cases/boulder24/clca"
        createBoulderElection(
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            topdir = "/home/stormy/rla/cases/boulder24/clca",
            isClca = true,
        )

        val publisher = Publisher("$topdir/audit")
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)
    }

    @Test
    fun testCreateColoradoOneAudit() {
        val topdir = "/home/stormy/rla/cases/corla/oneaudit"
        val detailXmlFile = "src/test/data/2024election/detail.xml"
        val contestRoundFile = "src/test/data/2024audit/round1/contest.csv"
        val precinctFile = "src/test/data/2024election/2024GeneralPrecinctLevelResults.zip"

        createColoradoOneAudit(topdir, detailXmlFile, contestRoundFile, precinctFile, isClca=false, clear=true)

        val publisher = Publisher("$topdir/audit")
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)
    }

    @Test
    fun testCreateColoradoClca() {
        val topdir = "/home/stormy/rla/cases/corla/clca"
        val detailXmlFile = "src/test/data/2024election/detail.xml"
        val contestRoundFile = "src/test/data/2024audit/round1/contest.csv"
        val precinctFile = "src/test/data/2024election/2024GeneralPrecinctLevelResults.zip"

        createColoradoOneAudit(topdir, detailXmlFile, contestRoundFile, precinctFile, isClca=true, clear=true)

        val publisher = Publisher("$topdir/audit")
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)
    }

    @Test
    fun createSFElectionOA() {
        val topdir = "/home/stormy/rla/cases/sf2024/oa"

        createSfElection(
            topdir,
            sfZipFile,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = "$sfDir/$cvrExportCsvFile",
            isClca = false,
        )

        val publisher = Publisher("$topdir/audit")
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)
    }

    @Test
    fun createSFElectionClca() {
        val topdir = "/home/stormy/rla/cases/sf2024/clca"

        createSfElection(
            topdir,
            sfZipFile,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = "$sfDir/$cvrExportCsvFile",
            isClca = true,
        )

        val publisher = Publisher("$topdir/audit")
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)
    }

    @Test
    fun createSFElectionOAnostyles() {
        val topdir = "/home/stormy/rla/cases/sf2024/oans"

        createSfElectionNoStyles(
            topdir,
            sfZipFile,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = "$sfDir/$cvrExportCsvFile",
        )

        val publisher = Publisher("$topdir/audit")
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)
    }

}