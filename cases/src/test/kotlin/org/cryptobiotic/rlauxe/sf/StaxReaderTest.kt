package org.cryptobiotic.rlauxe.sf

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvFile
import kotlin.test.Test

class StaxReaderTest {
    @Test
    fun testReadSFsummary() {
        val xmlFile = "src/test/data/SF2024/summary.xml"
        val reader = StaxReader()
        val contests = reader.read(xmlFile)
        contests.forEach {
            println(" ${it.id} ncards = ${it.ncards()} underVotes = ${it.undervotes()} overvotes = ${it.overvotes()} blanks = ${it.blanks()}")
            println(it)
        }
    }

    @Test
    fun checkSfElectionTotals() {
        val sfDir = "$testdataDir/cases/sf2024"
        val zipFilename = "$sfDir/CVR_Export_20241202143051.zip"
        val cvrExportCsv = "$sfDir/$cvrExportCsvFile"
        val summaryFile = "src/test/data/SF2024/summary.xml"

        val auditdir = "$sfDir/oa/audit"


        // fun checkSfElectionTotals(
        //    auditDir: String,
        //    castVoteRecordZip: String,
        //    contestManifestFilename: String,

        checkSfElectionTotals(
            auditdir,
            zipFilename,
            "ContestManifest.json",
            summaryFile,
        )
    }
}
