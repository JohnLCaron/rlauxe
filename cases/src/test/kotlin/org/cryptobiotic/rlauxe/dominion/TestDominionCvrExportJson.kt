package org.cryptobiotic.rlauxe.dominion

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.CvrsWithPopulationsToCardManifest
import org.cryptobiotic.rlauxe.audit.Population
import org.cryptobiotic.rlauxe.persist.csv.AuditableCardHeader
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsv
import org.cryptobiotic.rlauxe.sf.ContestManifest
import org.cryptobiotic.rlauxe.sf.readBallotTypeContestManifestJsonFromZip
import org.cryptobiotic.rlauxe.sf.readContestManifest
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.ZipReaderIterator
import org.cryptobiotic.rlauxe.util.ZipReaderTour
import java.io.FileOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.use

class TestDominionCvrExportJson {

    // https://www.sfelections.org/results/20240305/data/20240322/CVR_Export_20240322103409.zip
    @Test
    fun testZipReader() {
        val topDir = "$testdataDir/cases/sf2024"
        val zipFilename = "$topDir/CVR_Export_20241202143051.zip"
        val contestManifest = readContestManifest("src/test/data/SF2024/manifests/ContestManifest.json")

        testZipReader(zipFilename, contestManifest)
    }

    fun testZipReader(filename: String, contestManifest: ContestManifest) {
        var countFiles = 0
        var countCards = 0
        var countPoolCards = 0
        // ZipReaderTour(zipFile: String, val silent: Boolean = true, val sort: Boolean = true,
        //    val filter: (Path) -> Boolean, val visitor: (InputStream) -> Unit)
        val tour = ZipReaderTour(
            zipFile = filename,
            filter = { it.toString().contains("CvrExport_") },
            visitor = { input ->
                countFiles++
                val cvrs = convertCvrExportJsonToCvrExports(input, contestManifest)
                cvrs.forEach {
                    countCards++
                    if (it.group == 1) countPoolCards++
                }
            },
        )
        tour.tourFiles()
        println("file count: $countFiles")
        println("card Count: $countCards")
        println("countPoolCards: $countPoolCards")

        var countCards2 = 0
        var countPoolCards2 = 0
        // ZipReaderTour(zipFile: String, val silent: Boolean = true, val sort: Boolean = true,
        //    val filter: (Path) -> Boolean, val visitor: (InputStream) -> Unit)
        val zipper = ZipReaderIterator(
            zipFile = filename,
            filter = { it.toString().contains("CvrExport_") },
            reader = { input -> convertCvrExportJsonToCvrExports(input, contestManifest)}
        )
        zipper.use { iter ->
            while (iter.hasNext()) {
                val cvr = iter.next()
                countCards2++
                if (cvr.group == 1) countPoolCards2++
            }
        }
        println("card Count: $countCards2")
        println("countPoolCards: $countPoolCards2")
        assertEquals(countCards, countCards2)
        assertEquals(countPoolCards, countPoolCards2)
    }

    // https://www.sfelections.org/results/20240305/data/20240322/CVR_Export_20240322103409.zip

    @Test
    fun testReadDominionCvrJsonFile() {
        val filename = "src/test/data/SF2024/CvrExport_15.json"
        "$testdataDir/cases/sf2024/CvrExport_23049.json"
        val result: Result<DominionCvrExportJson, ErrorMessages> = readDominionCvrJsonFile(filename)
        val dominionCvrs = if (result is Ok) result.unwrap()
            else throw RuntimeException("Cannot read DominionCvrJson from ${filename} err = $result")
        // println(dominionCvrs)

        val contestManifest = readContestManifest("src/test/data/SF2024/manifests/ContestManifest.json")

        val summary = dominionCvrs.import(contestManifest)
        println("number of cvrs = ${summary.ncvrs}")
    }

    @Test
    fun testReadWriteDominionCvrs() {
        // read example json file
        // val filename = "src/test/data/SF2024/CvrExport_15.json" // group 2
        val filename = "src/test/data/SF2024/CvrExport_23049.json" // group 1, precinct 31-125
        val result: Result<DominionCvrExportJson, ErrorMessages> = readDominionCvrJsonFile(filename)
        val dominionCvrs = if (result is Ok) result.unwrap()
        else throw RuntimeException("Cannot read DominionCvrJson from ${filename} err = $result")
        // println(dominionCvrs)

        val contestManifest = readContestManifest("src/test/data/SF2024/manifests/ContestManifest.json")

        val summary = dominionCvrs.import(contestManifest)
        println("number of cvrs = ${summary.ncvrs}")

        // write to cvrExport.csv
        val topdir = "$testdataDir/tests/scratch/"
        val cvrExportFilename = "$topdir/$cvrExportCsvFile"
        val cvrExportCsvStream = FileOutputStream(cvrExportFilename)
        cvrExportCsvStream.write(CvrExportCsvHeader.toByteArray())

        summary.cvrExports.forEach {
            cvrExportCsvStream.write(it.toCsv().toByteArray()) // UTF-8
        }
        cvrExportCsvStream.close()

        // read cvrExport.csv back in, and write it to AuditableCard
        val cardManifestFilename = "$topdir/cardManifest.csv"
        val cardManifestWriter = FileOutputStream(cardManifestFilename).writer()
        cardManifestWriter.write(AuditableCardHeader)

        cvrExportCsvIterator(cvrExportFilename).use { csvIter ->
            var index = 0
            while (csvIter.hasNext()) {
                val cvrExport = csvIter.next()
                val card = cvrExport.toAuditableCard(index++, 0, false, mapOf("31-125" to 11))
                cardManifestWriter.write(writeAuditableCardCsv(card))
            }
        }
        cardManifestWriter.close()
    }

    @Test
    fun testCvrExportToCvrAdapter() {
        val topdir = "$testdataDir/tests/scratch/"
        val cvrExportFilename = "$topdir/$cvrExportCsvFile"

        // read cvrExport.csv back in, and write it with CvrsWithStylesToCards for CLCA
        val cardManifestFilename2 = "$topdir/cardManifestClca.csv"
        val cardManifestWriter2 = FileOutputStream(cardManifestFilename2).writer()
        cardManifestWriter2.write(AuditableCardHeader)

        val cvrExportIter = cvrExportCsvIterator(cvrExportFilename)
        val cvrIter = CvrExportToCvrAdapter(cvrExportIter, null )
        val cardIter = CvrsWithPopulationsToCardManifest(AuditType.CLCA, cvrIter, null, null)

        while (cardIter.hasNext()) {
            val card = cardIter.next()
            cardManifestWriter2.write(writeAuditableCardCsv(card))
        }
        cardManifestWriter2.close()

        // read cvrExport.csv back in, and write it with CvrsWithStylesToCards for OA
        val cardManifestFilename3 = "$topdir/cardManifestOA.csv"
        val cardManifestWriter3 = FileOutputStream(cardManifestFilename3).writer()
        cardManifestWriter3.write(AuditableCardHeader)

        val cardStyle = Population("31-125", 2, intArrayOf(0, 1, 2), false)
        val cvrExportIter2 = cvrExportCsvIterator(cvrExportFilename) // CvrExport
        val cvrIter2 = CvrExportToCvrAdapter(cvrExportIter2, pools= mapOf("31-125" to 2)) // Cvr
        val cardIter2 = CvrsWithPopulationsToCardManifest(
            AuditType.ONEAUDIT,
            cvrIter2,
            null,
            listOf(cardStyle)
        ) // AuditableCard

        while (cardIter2.hasNext()) {
            val card = cardIter2.next()
            cardManifestWriter3.write(writeAuditableCardCsv(card))
        }
        cardManifestWriter3.close()
    }

    @Test
    fun countStuff() {
        val topDir = "$testdataDir/cases/sf2024"
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

    // @Test // fails: BallotTypeContestManifest not accurate?
    fun testBallotTypeContestManifest() {
        val topDir = "$testdataDir/cases/sf2024"
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

    /* @Test
    fun lookForBallotStyles() {
        val filename = "/home/stormy/dev/github/rla/rlauxe/cases/src/test/data/SF2024/CvrExport_23049.json"
        var countCards = 0

        val result: Result<DominionCvrExportJson, ErrorMessages> = readDominionCvrJsonStream(inputStream)


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

*/


    // use the cvrs to write a fake SF ballot manifest, following ballotManifest-dummy.xlsx format
    // @Test
    fun testWriteSfBallotManifest() {
        val topDir = "$testdataDir/cases/sf2024P"
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
