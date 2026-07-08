package org.cryptobiotic.rlauxe.dominion

import org.cryptobiotic.rlauxe.sf.readContestManifestFromZip
import org.cryptobiotic.rlauxe.testdataDir
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.test.Test

class TestConvertCvrExportJsonToCsv {

    @Test
    fun testConvertCvrExportJsonToCsv() {
        val topDir = "$testdataDir/cases/sf2024/test"
        val castVoteRecordZip = "$topDir/CVR_Export_20241202143051.zip"
        val manifestFile = "ContestManifest.json"

        val jsonExportIn = "src/test/data/CvrExport_11.json"
        val jsonStreamIn = FileInputStream(jsonExportIn)

        val csvExportOut = "src/test/data/CvrExport_11.csv"
        val csvStreamOut = FileOutputStream(csvExportOut)

        csvStreamOut.write(CvrExportCsvHeader.toByteArray())
        val contestManifest = readContestManifestFromZip(castVoteRecordZip, manifestFile)

        convertCvrExportJsonToCsv(jsonStreamIn, csvStreamOut, contestManifest)
    }

}