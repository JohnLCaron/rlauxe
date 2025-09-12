package org.cryptobiotic.rlauxe.sf

import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test

class TestSfPrimaryElection {

    @Test
    fun createSfPrimaryElection() {
        val stopwatch = Stopwatch()
        val topDir = "/home/stormy/rla/cases/sf2024P"
        val zipFilename = "$topDir/CVR_Export_20240322103409.zip"
        val manifestFile = "ContestManifest.json"
        val auditDir = "$topDir/audit"
        val cvrCsv = "$topDir/$cvrExportCsvFile"

        createCvrExportCsvFile(topDir, zipFilename, manifestFile) // write to "$topDir/cvrExport.csv"

        // create sf2024 primary audit
        createSfElectionFromCvrExport(
            auditDir,
            zipFilename,
            manifestFile,
            "CandidateManifest.json",
            cvrCsv,
            show = false,
        )

        createSortedCards(topDir, auditDir, cvrCsv, zip = true, ballotPoolFile = null) // write to "$auditDir/sortedCards.csv"
        println("that took $stopwatch")
    }
}