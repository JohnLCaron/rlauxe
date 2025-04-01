package org.cryptobiotic.rlauxe.sf

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.dominion.convertCvrExportToCvr
import org.cryptobiotic.rlauxe.persist.csv.CvrCsv
import org.cryptobiotic.rlauxe.persist.json.Publisher
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFile
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.ZipReaderTour
import org.cryptobiotic.rlauxe.util.mergeCvrs
import org.cryptobiotic.rlauxe.util.sortCvrs
import java.io.File
import java.io.FileOutputStream
import kotlin.test.Test

class TestSfElectionFromCvrs {

    // write sf2024 cvrs
    @Test
    fun convertCvrExportToCvr() {
        val stopwatch = Stopwatch()
        val zipFilename = "/home/stormy/Downloads/CVR_Export_20241202143051.zip"

        val auditDir = "/home/stormy/temp/sf2024"
        val outputFilename = "$auditDir/CVR_Export_20241202143051.csv"
        val outputStream = FileOutputStream(outputFilename)
        outputStream.write(CvrCsv.header.toByteArray())

        val irvIds = readContestManifestForIRV("src/test/data/SF2024/ContestManifest.json")

        var countFiles = 0
        var countCvrs = 0
        val zipReader = ZipReaderTour(
            zipFilename, silent = false, sort = true,
            filter = { path -> path.toString().contains("CvrExport_") },
            visitor = { inputStream ->
                countCvrs += convertCvrExportToCvr(inputStream, outputStream, irvIds)
                countFiles++
            },
        )
        zipReader.tourFiles()
        outputStream.close()
        println("read $countCvrs cvrs $countFiles files took $stopwatch")
        // read 1,641,744 cvrs 27,554 files took 58.67 s
    }

    // out of memory sort by sampleNum()
    @Test
    fun testSortMergeCvrs() {
        val auditDir = "/home/stormy/temp/sf2024"
        val cvrZipFile = "$auditDir/CVR_Export_20241202143051.zip"

        sortCvrs(auditDir, cvrZipFile, "$auditDir/sortChunks")
        mergeCvrs(auditDir, "$auditDir/sortChunks")
    }

    @Test
    fun testCopyFile() {
        val auditDir = "/home/stormy/temp/sf2024"
        val fromFile = File("src/test/data/SF2024/sortedCvrs.zip")
        val targetFile = File("$auditDir/sortedCvrs.zip")
        fromFile.copyTo(targetFile)
    }

    @Test
    fun testCreateSfElectionFromCvrs() {
        val auditDir = "/home/stormy/temp/sf2024"
        createSfElectionFromCvrs(
            "/home/stormy/temp/sf2024",
            "src/test/data/SF2024/ContestManifest.json",
            "src/test/data/SF2024/CandidateManifest.json",
            "$auditDir/sortedCvrs.zip",
        )
    }

    @Test
    fun showSfElectionFromCvrs() {
        val publisher = Publisher("/home/stormy/temp/sf2024")
        val contestsResults = readContestsJsonFile(publisher.contestsFile())
        val contestsUA = if (contestsResults is Ok) contestsResults.unwrap()
        else throw RuntimeException("Cannot read contests from ${publisher.contestsFile()} err = $contestsResults")

        contestsUA.forEach { contestUA ->
            println("$contestUA ${contestUA.contest.choiceFunction}")
            contestUA.contest.info.candidateNames.forEach { println("  $it") }
        }
    }

}