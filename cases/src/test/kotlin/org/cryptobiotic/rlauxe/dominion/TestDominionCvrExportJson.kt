package org.cryptobiotic.rlauxe.dominion

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.persist.csv.*
import org.cryptobiotic.rlauxe.sf.readContestManifestForIRV
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.ZipReaderTour
import java.io.FileOutputStream
import kotlin.test.Test

class TestDominionCvrExportJson {

    // https://www.sfelections.org/results/20240305/data/20240322/CVR_Export_20240322103409.zip

    @Test
    fun testReadDominionCvrJsonFile() {
        val filename1 = "src/test/data/SF2024/CvrExport_15.json"
        val filename = "/home/stormy/temp/cases/sf2024P/CVR_Export_20240322103409/CvrExport_0.json"
        val result: Result<DominionCvrExportJson, ErrorMessages> = readDominionCvrJsonFile(filename)
        val dominionCvrs = if (result is Ok) result.unwrap()
        else throw RuntimeException("Cannot read DominionCvrJson from ${filename} err = $result")
        // println(dominionCvrs)

        val irvIds = readContestManifestForIRV("src/test/data/SF2024/ContestManifest.json")

        val cvrs = dominionCvrs.import(irvIds)
        // println("ncvrs = ${cvrs.size}")
        // cvrs.forEach { println(it) }

        println("==================================================")
        print(AuditableCardHeader)
        cvrs.forEach {
            val card = AuditableCard.fromCvrWithZeros(it)
            writeAuditableCardCsv(card)
        }
    }

    @Test
    fun testReadCountingGroupId() {
        val zipFilename1 = "/home/stormy/Downloads/CVR_Export_20241202143051.zip"
        val topDir = "/home/stormy/temp/cases/sf2024P"
        val zipFilename = "$topDir/CVR_Export_20240322103409.zip"

        val countIds = mutableMapOf<Int, Int>()

        var countFiles = 0
        val zipReader = ZipReaderTour(
            zipFilename,
            silent = true,
            filter = { path -> path.toString().contains("CvrExport_") },
            visitor = { inputStream ->
                val result: Result<DominionCvrExportJson, ErrorMessages> = readDominionCvrJsonStream(inputStream)
                val dominionCvrs = if (result is Ok) result.unwrap()
                else throw RuntimeException("Cannot read DominionCvrJson from stream err = $result")
                countFiles++

                dominionCvrs.Sessions.forEach {
                    val ncount = countIds[it.CountingGroupId] ?: 0
                    countIds[it.CountingGroupId] = ncount + 1
                }
            },
        )
        zipReader.tourFiles()
        println("testReadCountingGroupId $countFiles files")
        countIds.forEach { (key, value) -> println("  $key $value") }

        testWriteSfBallotManifest()
    }

    // use the cvrs to write a fake SF ballot manifest, following ballotManifest-dummy.xlsx format
    // @Test
    fun testWriteSfBallotManifest() {
        val topDir = "/home/stormy/temp/cases/sf2024P"
        val zipFilename = "$topDir/CVR_Export_20240322103409.zip"

        val countingContests = mutableMapOf<Int, SfContestCount>()
        val batches = mutableMapOf<String, SfBallotManifest>()

        var countFiles = 0
        val zipReader = ZipReaderTour(
            zipFilename,
            silent = true,
            filter = { path -> path.toString().contains("CvrExport_") },
            visitor = { inputStream ->
                val result: Result<DominionCvrExportJson, ErrorMessages> = readDominionCvrJsonStream(inputStream)
                val dominionCvrs = if (result is Ok) result.unwrap()
                else throw RuntimeException("Cannot read DominionCvrJson from stream err = $result")
                countFiles++
                dominionCvrs.Sessions.forEach { session ->

                    val key = "${session.TabulatorId}-${session.BatchId}"
                    val batch = batches.getOrPut(key) { SfBallotManifest(session.TabulatorId, session.BatchId, 0) }
                    batch.count += session.Original.Cards.size

                    session.Original.Cards.forEach { card ->
                        card.Contests.forEach { contest ->
                            val contestCount = countingContests.getOrPut(contest.Id) { SfContestCount(0) }
                            contestCount.total++
                            val groupCount = contestCount.groupCount.getOrPut(session.CountingGroupId) { 0}
                            contestCount.groupCount[session.CountingGroupId] = groupCount + 1
                        }
                    }
                }
            },
        )
        zipReader.tourFiles()
        println("testReadCountingGroupId $countFiles files")
        println("countingContests")
        countingContests.toSortedMap().forEach { (key, value) -> println("  $key $value") }

        val header = "    ,Tray #,Tabulator Number,Batch Number,Total Ballots,VBMCart.Cart number\n"
        val outputFilename = "$topDir/sfBallotManifest.csv"
        println("writing to $outputFilename with ${batches.size} batches")
        val outputStream = FileOutputStream(outputFilename)
        outputStream.write(header.toByteArray()) // UTF-8

        var total = 0
        var lineNo = 0
        val sbatches = batches.toSortedMap()
        sbatches.values.forEach {
            val line = "${lineNo},${lineNo+1},${it.tab},${it.batch},${it.count},${lineNo+1}\n"
            outputStream.write(line.toByteArray())
            lineNo++
            total += it.count
        }
        outputStream.close()
        println("total ballots = $total")
    }
    //  1 total=176637, groupCount={2=155705, 1=20932}
    //  2 total=19175, groupCount={2=15932, 1=3243}

    class SfBallotManifest(val tab: Int, val batch: Int, var count: Int)
    class SfContestCount(var total: Int) {
        val groupCount = mutableMapOf<Int, Int>()
        override fun toString(): String {
            return "total=$total, groupCount=$groupCount"
        }

    }
}
