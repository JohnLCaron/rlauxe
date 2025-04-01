package org.cryptobiotic.rlauxe.dominion

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.persist.csv.CvrCsv
import org.cryptobiotic.rlauxe.persist.csv.publishCsv
import org.cryptobiotic.rlauxe.persist.csv.writeCSV
import org.cryptobiotic.rlauxe.sf.readContestManifestForIRV
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.ZipReaderTour
import kotlin.test.Test

class TestDominionCvrExportJson {

    // https://www.sfelections.org/results/20240305/data/20240322/CVR_Export_20240322103409.zip

    @Test
    fun testReadDominionCvrJsonFile() {
        val filename = "/home/stormy/Downloads/CVR_Export_20241202143051/CvrExport_15.json"
        val result: Result<DominionCvrExportJson, ErrorMessages> = readDominionCvrJsonFile(filename)
        val dominionCvrs = if (result is Ok) result.unwrap()
        else throw RuntimeException("Cannot read DominionCvrJson from ${filename} err = $result")
        println(dominionCvrs)

        val irvIds = readContestManifestForIRV("src/test/data/SF2024/ContestManifest.json")

        val cvrs = dominionCvrs.import(irvIds)
        println("ncvrs = ${cvrs.size}")
        cvrs.forEach { println(it) }

        println("==================================================")
        print(CvrCsv.header)
        cvrs.forEach {
            val cvrUA = CvrUnderAudit(it, 0, 0)
            print(writeCSV(cvrUA.publishCsv()))
        }
    }

    @Test
    fun testReadCountingGroupId() {
        val zipFilename = "/home/stormy/Downloads/CVR_Export_20241202143051.zip"

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
        // testReadCountingGroupId 27554 files
        //  2 1387622
        //  1 216286
        // SHANGRLA SF primary i think SF_CVR_Export_20240311150227
        //     cvr_list.extend(Dominion.read_cvrs(_fname, use_current=True, enforce_rules=True, include_groups=[1,2],
        //                                      pool_groups=[1]))
        // cvrs: 443578 unique IDs: 443578
    }

}
