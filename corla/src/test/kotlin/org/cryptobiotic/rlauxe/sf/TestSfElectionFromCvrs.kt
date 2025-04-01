package org.cryptobiotic.rlauxe.sf

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.persist.json.Publisher
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFile
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.mergeCvrs
import org.cryptobiotic.rlauxe.util.sortCvrs
import java.io.File
import kotlin.test.Test

class TestSfElectionFromCvrs {

    // write sf2024 cvrs
    @Test
    fun createSF2024Pcvrs() {
        val stopwatch = Stopwatch()
        val topDir = "/home/stormy/temp/sf2024P"
        val zipFilename = "$topDir/CVR_Export_20240322103409.zip"
        val manifestFile = "$topDir/CVR_Export_20240322103409/ContestManifest.json"
        createSfElectionCvrs(topDir, zipFilename, manifestFile) // write to "$topDir/cvrs.csv"

        val auditDir = "$topDir/audit"
        createSfElectionFromCvrs(
            auditDir,
            "$topDir/CVR_Export_20240322103409/ContestManifest.json",
            "$topDir/CVR_Export_20240322103409/CandidateManifest.json",
            "$topDir/cvrs.csv",
        )

        sortCvrs(auditDir, "$topDir/cvrs.csv", "$topDir/sortChunks")
        mergeCvrs(auditDir, "$topDir/sortChunks") // merge to "$topDir/sortedCvrs.csv"
        // manually zip (TODO)
        println("that took $stopwatch")
    }

    @Test
    fun createSF2024PElectionFromCvrs() {
        val topDir = "/home/stormy/temp/sf2024P"
        createSfElectionFromCvrs(
            "$topDir/audit",
            "$topDir/CVR_Export_20240322103409/ContestManifest.json",
            "$topDir/CVR_Export_20240322103409/CandidateManifest.json",
            "$topDir/sortedCvrs.zip",
        )
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