package org.cryptobiotic.rlauxe.sf

import org.cryptobiotic.rlauxe.dominion.CvrExportCsvHeader
import org.cryptobiotic.rlauxe.dominion.DominionCvrSummary
import org.cryptobiotic.rlauxe.dominion.convertCvrExportJsonToCsv
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvFile
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.ZipReaderTour
import java.io.FileOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CreateSf2024CvrExport {

    // extract the cvrExport from $zipFilename json files, write to cvrExport.csv
    // only need to do this once, all the SF variants can use.
    @Test
    fun createSf2024CvrExport() {
        val topDir = "/home/stormy/rla/cases/sf2024"
        val zipFilename = "$topDir/CVR_Export_20241202143051.zip"
        val manifestFile = "ContestManifest.json"
        val summary = createCvrExportCsvFile(topDir, zipFilename, manifestFile) // write to "$topDir/cvrExport.csv"
        println(summary)

        // check that the cvrs agree with the summary XML
        val staxContests = StaxReader().read("src/test/data/SF2024/summary.xml")
        // staxContests.forEach { println(it) }

        val contestManifest = readContestManifestFromZip(zipFilename, manifestFile)
        summary.contestSums.forEach { (id, contestSum) ->
            val contestName = contestManifest.contests[id]!!.Description
            val staxContest: StaxReader.StaxContest? = staxContests.find { it.id == contestName }
            assertNotNull(staxContest)
            assertEquals(staxContest.ncards(), contestSum.ncards)
            assertEquals(staxContest.undervotes(), contestSum.undervotes)
        }
    }
    // IRV contests = [18, 23, 24, 25, 26, 27, 28, 19, 21, 22, 20]
    //read 1603908 cvrs in 27554 files; took 57.72 s
    //took = 57.72 s
    //DominionCvrSummary ncvrs=1603908 ncards=1641744 cvrs=[]
    //  ContestSummary 1 ncards=412121 undervotes=8463 overvotes=661 isOvervote=0 isBlank = 9124
    //  ContestSummary 2 ncards=412121 undervotes=34597 overvotes=171 isOvervote=0 isBlank = 34768
    //  ContestSummary 3 ncards=412121 undervotes=38960 overvotes=103 isOvervote=0 isBlank = 39063
    //  ContestSummary 5 ncards=369231 undervotes=30043 overvotes=77 isOvervote=0 isBlank = 30120
    //  ContestSummary 7 ncards=42890 undervotes=6726 overvotes=14 isOvervote=0 isBlank = 6740
    //  ContestSummary 9 ncards=412121 undervotes=42947 overvotes=107 isOvervote=0 isBlank = 43054
    //  ContestSummary 11 ncards=226823 undervotes=26383 overvotes=50 isOvervote=0 isBlank = 26433
    //  ContestSummary 13 ncards=185298 undervotes=32860 overvotes=102 isOvervote=0 isBlank = 32962
    //  ContestSummary 14 ncards=412231 undervotes=632530 overvotes=3616 isOvervote=0 isBlank = 85729
    //  ContestSummary 15 ncards=412121 undervotes=823782 overvotes=1408 isOvervote=0 isBlank = 105412
    //  ContestSummary 16 ncards=14663 undervotes=4813 overvotes=18 isOvervote=0 isBlank = 4831
    //  ContestSummary 17 ncards=191007 undervotes=52961 overvotes=159 isOvervote=0 isBlank = 53120
    //  ContestSummary 18 ncards=410105 undervotes=19700 overvotes=1319 isOvervote=2062 isBlank = 18540
    //  ContestSummary 19 ncards=410105 undervotes=82399 overvotes=145 isOvervote=0 isBlank = 80726
    //  ContestSummary 20 ncards=410105 undervotes=109691 overvotes=3 isOvervote=0 isBlank = 106097
    //  ContestSummary 21 ncards=410105 undervotes=64220 overvotes=183 isOvervote=0 isBlank = 62474
    //  ContestSummary 22 ncards=410105 undervotes=96407 overvotes=179 isOvervote=0 isBlank = 94989
    //  ContestSummary 23 ncards=39257 undervotes=3806 overvotes=46 isOvervote=0 isBlank = 3732
    //  ContestSummary 24 ncards=33672 undervotes=4951 overvotes=69 isOvervote=1 isBlank = 4838
    //  ContestSummary 25 ncards=34405 undervotes=4937 overvotes=69 isOvervote=0 isBlank = 4628
    //  ContestSummary 26 ncards=42846 undervotes=5747 overvotes=23 isOvervote=1 isBlank = 5502
    //  ContestSummary 27 ncards=37091 undervotes=4344 overvotes=129 isOvervote=2 isBlank = 4220
    //  ContestSummary 28 ncards=32274 undervotes=4469 overvotes=147 isOvervote=0 isBlank = 4186
    //  ContestSummary 29 ncards=409893 undervotes=27399 overvotes=89 isOvervote=0 isBlank = 27488
    //  ContestSummary 30 ncards=409893 undervotes=22851 overvotes=107 isOvervote=0 isBlank = 22958
    //  ContestSummary 31 ncards=409893 undervotes=25798 overvotes=61 isOvervote=0 isBlank = 25859
    //  ContestSummary 32 ncards=409893 undervotes=31537 overvotes=80 isOvervote=0 isBlank = 31617
    //  ContestSummary 33 ncards=409893 undervotes=35724 overvotes=90 isOvervote=0 isBlank = 35814
    //  ContestSummary 34 ncards=409893 undervotes=27562 overvotes=80 isOvervote=0 isBlank = 27642
    //  ContestSummary 35 ncards=409893 undervotes=30820 overvotes=214 isOvervote=0 isBlank = 31034
    //  ContestSummary 36 ncards=409893 undervotes=45179 overvotes=161 isOvervote=0 isBlank = 45340
    //  ContestSummary 37 ncards=409893 undervotes=38540 overvotes=174 isOvervote=0 isBlank = 38714
    //  ContestSummary 38 ncards=409893 undervotes=30984 overvotes=91 isOvervote=0 isBlank = 31075
    //  ContestSummary 39 ncards=409515 undervotes=32146 overvotes=72 isOvervote=0 isBlank = 32218
    //  ContestSummary 40 ncards=409515 undervotes=32831 overvotes=47 isOvervote=0 isBlank = 32878
    //  ContestSummary 41 ncards=409515 undervotes=38991 overvotes=158 isOvervote=0 isBlank = 39149
    //  ContestSummary 42 ncards=409515 undervotes=43036 overvotes=152 isOvervote=0 isBlank = 43188
    //  ContestSummary 43 ncards=409515 undervotes=45520 overvotes=141 isOvervote=0 isBlank = 45661
    //  ContestSummary 44 ncards=409515 undervotes=47715 overvotes=129 isOvervote=0 isBlank = 47844
    //  ContestSummary 45 ncards=409515 undervotes=38594 overvotes=97 isOvervote=0 isBlank = 38691
    //  ContestSummary 46 ncards=409515 undervotes=43492 overvotes=81 isOvervote=0 isBlank = 43573
    //  ContestSummary 47 ncards=409515 undervotes=45993 overvotes=63 isOvervote=0 isBlank = 46056
    //  ContestSummary 48 ncards=409515 undervotes=46680 overvotes=50 isOvervote=0 isBlank = 46730
    //  ContestSummary 49 ncards=409515 undervotes=32912 overvotes=114 isOvervote=0 isBlank = 33026
    //  ContestSummary 50 ncards=409515 undervotes=39843 overvotes=97 isOvervote=0 isBlank = 39940
    //  ContestSummary 51 ncards=409515 undervotes=67068 overvotes=137 isOvervote=0 isBlank = 67205
    //  ContestSummary 52 ncards=409515 undervotes=45990 overvotes=93 isOvervote=0 isBlank = 46083
    //  ContestSummary 53 ncards=409515 undervotes=36175 overvotes=91 isOvervote=0 isBlank = 36266

    // read the CvrExport_* files out of the castVoteRecord JSON zip file, convert to "CvrExport" CSV file.
    // use the contestManifestFile to add the undervotes, and to identify the IRV contests
    // write to "$topDir/$cvrExportCsvFile"
    fun createCvrExportCsvFile(topDir: String, castVoteRecordZip: String, contestManifestFilename: String): DominionCvrSummary {
        val stopwatch = Stopwatch()
        val outputFilename = "$topDir/$cvrExportCsvFile"
        val cvrExportCsvStream = FileOutputStream(outputFilename)
        cvrExportCsvStream.write(CvrExportCsvHeader.toByteArray())

        val contestManifest = readContestManifestFromZip(castVoteRecordZip, contestManifestFilename)
        println("IRV contests = ${contestManifest.irvContests}")

        var countFiles = 0
        val summaryTotal = DominionCvrSummary()
        val zipReader = ZipReaderTour(
            castVoteRecordZip, silent = true, sortPaths = true,
            filter = { path -> path.toString().contains("CvrExport_") },
            visitor = { inputStream ->
                val summary = convertCvrExportJsonToCsv(inputStream, cvrExportCsvStream, contestManifest)
                summaryTotal.add(summary)
                countFiles++
            },
        )
        zipReader.tourFiles()
        cvrExportCsvStream.close()

        println("read ${summaryTotal.ncvrs} cvrs in $countFiles files; took $stopwatch")
        println("took = $stopwatch")
        return summaryTotal
    }
}