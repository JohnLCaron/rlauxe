package org.cryptobiotic.util

import org.cryptobiotic.rlauxe.boulder.createBoulderElection
import org.cryptobiotic.rlauxe.corla.createColoradoOneAuditNew
import org.cryptobiotic.rlauxe.sf.createSfElection
import org.cryptobiotic.rlauxe.sf.createSfElectionNoStyles
import org.junit.jupiter.api.Test

class TestGenerateAllUseCases {
    val sfDir = "/home/stormy/rla/cases/sf2024"
    val sfZipFile = "$sfDir/CVR_Export_20241202143051.zip"
    val cvrExportCsvFile = "cvrExport.csv"

    @Test
    fun createBoulder24oa() {
        createBoulderElection(
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            topdir = "/home/stormy/rla/cases/boulder24/oa",
            isClca = false,
        )
    }

    @Test
    fun createBoulder24clca() { // simulate CVRs
        createBoulderElection(
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            topdir = "/home/stormy/rla/cases/boulder24/clca",
            isClca = true,
        )
    }

    @Test
    fun testCreateColoradoOneAudit() {
        val topdir = "/home/stormy/rla/cases/corla/oneaudit"
        val detailXmlFile = "src/test/data/2024election/detail.xml"
        val contestRoundFile = "src/test/data/2024audit/round1/contest.csv"
        val precinctFile = "src/test/data/2024election/2024GeneralPrecinctLevelResults.zip"

        createColoradoOneAuditNew(topdir, detailXmlFile, contestRoundFile, precinctFile, isClca=false, clear=true)
    }

    @Test
    fun testCreateColoradoClca() {
        val topdir = "/home/stormy/rla/cases/corla/clca"
        val detailXmlFile = "src/test/data/2024election/detail.xml"
        val contestRoundFile = "src/test/data/2024audit/round1/contest.csv"
        val precinctFile = "src/test/data/2024election/2024GeneralPrecinctLevelResults.zip"

        createColoradoOneAuditNew(topdir, detailXmlFile, contestRoundFile, precinctFile, isClca=true, clear=true)
    }

    @Test
    fun createSFElectionOA() {
        val topDir = "/home/stormy/rla/cases/sf2024/oa"

        createSfElection(
            topDir,
            sfZipFile,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = "$sfDir/$cvrExportCsvFile",
            isClca = false,
        )
    }

    @Test
    fun createSFElectionClca() {
        val topDir = "/home/stormy/rla/cases/sf2024/clca"

        createSfElection(
            topDir,
            sfZipFile,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = "$sfDir/$cvrExportCsvFile",
            isClca = true,
        )
    }

    @Test
    fun createSFElectionOAnostyles() {
        val topDir = "/home/stormy/rla/cases/sf2024/oans"

        createSfElectionNoStyles(
            topDir,
            sfZipFile,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = "$sfDir/$cvrExportCsvFile",
        )
    }

}