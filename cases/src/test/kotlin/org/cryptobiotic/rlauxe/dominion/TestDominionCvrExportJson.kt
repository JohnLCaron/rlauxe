package org.cryptobiotic.rlauxe.dominion

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.sf.readBallotTypeContestManifestJsonFromZip
import org.cryptobiotic.rlauxe.sf.readContestManifest
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.ZipReaderTour
import java.io.FileOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class TestDominionCvrExportJson {

    // https://www.sfelections.org/results/20240305/data/20240322/CVR_Export_20240322103409.zip

    @Test
    fun testReadDominionCvrJsonFile() {
        val filename = "src/test/data/SF2024/CvrExport_15.json"
        val filename2 = "/home/stormy/rla/cases/sf2024/CvrExport_23049.json"
        val result: Result<DominionCvrExportJson, ErrorMessages> = readDominionCvrJsonFile(filename)
        val dominionCvrs = if (result is Ok) result.unwrap()
            else throw RuntimeException("Cannot read DominionCvrJson from ${filename} err = $result")
        // println(dominionCvrs)

        val contestManifest = readContestManifest("src/test/data/SF2024/manifests/ContestManifest.json")

        val summary = dominionCvrs.import(contestManifest)
        println("number of cvrs = ${summary.ncvrs}")
        /* val tabs1 = tabulateCvrs(cvrsNoManifest.iterator())
        tabs1.forEach { (key, tab) ->
            println("  $key == $tab")
        }

        val cvrsWithManifest = dominionCvrs.import(irvIds, manifest)
        println("with manifest ncvrs = ${cvrsWithManifest.size}")
        repeat(5) { println(cvrsWithManifest[it]) }
        val tabs2 = tabulateCvrs(cvrsWithManifest.iterator())
        tabs2.forEach { (key, tab) ->
            println("  $key == $tab")
            assertEquals(tab.votes, tabs1[key]!!.votes)
        }

        cvrs.forEach { println(it) }

        println("==================================================")
        print(AuditableCardHeader)
        cvrs.forEach {
            val card = AuditableCard.fromCvrWithZeros(it)
            println(writeAuditableCardCsv(card))
        }

         */
    }

    @Test
    fun countStuff() {
        val topDir = "/home/stormy/rla/cases/sf2024"
        val zipFilename = "$topDir/CVR_Export_20241202143051.zip"

        val countGroups = mutableMapOf<Int, Int>()
        val countGroupCards = mutableMapOf<Int, Int>()

        val countPools = mutableMapOf<String, Int>()
        val countBallotType = mutableMapOf<Int, Int>()

        var countNotCurrent = 0
        var countFiles = 0
        var countCards = 0
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
                    val orgOrMod = if (session.Original.IsCurrent) session.Original else session.Modified
                    if (orgOrMod != null) {
                        val ncards = orgOrMod.Cards.size
                        countCards += ncards

                        val groupIdcount = countGroups[session.CountingGroupId] ?: 0
                        countGroups[session.CountingGroupId] = groupIdcount + 1

                        val groupIdcards = countGroupCards[session.CountingGroupId] ?: 0
                        countGroupCards[session.CountingGroupId] = groupIdcards + ncards

                        if (session.CountingGroupId == 1) {
                            // tally_pool=str(c["TabulatorId"]) + "-" + str(c["BatchId"])
                            val poolId = "${session.TabulatorId}-${session.BatchId}"
                            val poolIdCount = countPools[poolId] ?: 0
                            countPools[poolId] = poolIdCount + ncards
                        }

                        val ballotType = countBallotType[orgOrMod.BallotTypeId] ?: 0
                        countBallotType[orgOrMod.BallotTypeId] = ballotType + ncards
                    } else {
                        countNotCurrent++
                    }
                }
            },
        )
        zipReader.tourFiles()

        println("testReadCountingGroupId $countFiles files $countCards cards countNotCurrent= $countNotCurrent")
        println("CountingGroupId = ${countGroups.size} distinct, total = ${countGroups.values.sum()}")
        countGroups.forEach { (key, value) -> println("  $key = $value") }
        println("CountingGroupCards = ${countGroupCards.size} distinct, total = ${countGroupCards.values.sum()}")
        countGroupCards.forEach { (key, value) -> println("  $key = $value") }
        println("countPools = ${countPools.size} distinct, total in pools = ${countPools.values.sum()}")
        // countPools.forEach { (key, value) -> println("  $key = $value") }
        println("BallotTypeId = ${countBallotType.size} distinct, total = ${countBallotType.values.sum()}")
        // countBallotType.forEach { (key, value) -> println("  $key = $value") }

        // testReadCountingGroupId 27554 files 1641744 cards countNotCurrent= 0
        //CountingGroupId = 2 distinct, total = 1603908
        //  2 = 1387622
        //  1 = 216286
        //CountingGroupCards = 2 distinct, total = 1641744
        //  2 = 1408295
        //  1 = 233449
        //countPools = 4223 distinct, total = 233449
        //BallotTypeId = 78 distinct, total = 1641744

        // "The election produced 1,603,908 CVRs,
        // of which 216,286 were for cards cast in 4,223 precinct batches
        // and 1,387,622 CVRs were for vote-by-mail (VBM) cards." (SliceDice)

        // Dominion.read_cvrs
        // cvr_list: list of CVR objects, with additional fields, viz,
        //                  `id=str(c["TabulatorId"]) + "-" + str(c["BatchId"])  + "-"  + str(record_id)`
        //                  `tally_pool=str(c["TabulatorId"]) + "-" + str(c["BatchId"])`
        //                  `pool=(c["CountingGroupId"] in pool_groups)`

        // testWriteSfBallotManifest()
    }

    // @Test fails: BallotTypeContestManifest not accurate?
    fun testBallotStyles() {
        val topDir = "/home/stormy/rla/cases/sf2024"
        val zipFilename = "$topDir/CVR_Export_20241202143051.zip"
        val ifilename = "BallotTypeContestManifest.json"
        val manifest = readBallotTypeContestManifestJsonFromZip(zipFilename, ifilename).unwrap()
        val ballotStyles = manifest.ballotStyles
        var countCards = 0

        val zipReader = ZipReaderTour(
            zipFilename,
            silent = true,
            filter = { path -> path.toString().contains("CvrExport_") },
            visitor = { inputStream ->
                val result: Result<DominionCvrExportJson, ErrorMessages> = readDominionCvrJsonStream(inputStream)
                val dominionCvrs = if (result is Ok) result.unwrap()
                    else throw RuntimeException("Cannot read DominionCvrJson from stream err = $result")

                dominionCvrs.Sessions.forEach {
                    val ballotType = ballotStyles[it.Original.BallotTypeId]!!
                    it.Original.Cards.forEach { card ->
                        if (ballotType.size != card.Contests.size) {
                            println("HEY")
                        }
                        assertEquals(ballotType.size, card.Contests.size)
                        countCards++
                    }
                }
            },
        )
        zipReader.tourFiles()

        println("testBallotStyles $countCards cards are ok")
    }


    // use the cvrs to write a fake SF ballot manifest, following ballotManifest-dummy.xlsx format
    // @Test
    fun testWriteSfBallotManifest() {
        val topDir = "/home/stormy/rla/cases/sf2024P"
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
