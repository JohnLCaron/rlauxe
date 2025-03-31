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
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.ZipReaderTour
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.test.Test

class TestDominionCvrExportJson {

    @Test
    fun testReadDominionCvrJsonFile() {
        val filename = "/home/stormy/Downloads/CVR_Export_20241202143051/CvrExport_15.json"
        val result: Result<DominionCvrExportJson, ErrorMessages> = readDominionCvrJsonFile(filename)
        val dominionCvrs = if (result is Ok) result.unwrap()
        else throw RuntimeException("Cannot read DominionCvrJson from ${filename} err = $result")
        // println(dominionCvrs)

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

}
