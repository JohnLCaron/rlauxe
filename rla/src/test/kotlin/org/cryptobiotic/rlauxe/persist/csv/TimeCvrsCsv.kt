package org.cryptobiotic.rlauxe.persist.csv

import org.cryptobiotic.rlauxe.persist.json.readCvrsJsonFile
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.test.Test

class TimeCvrsCsv {

    @Test
    fun timeJsonVsCsv() {
        val filenameIn = "/home/stormy/temp/persist/testCvrs/runBoulder24.json"
        val filenameOut = "/home/stormy/temp/persist/testCvrs/runBoulder24.csv"

        // size difference
        val jsonPath = Path.of(filenameIn)
        println("size Json = ${jsonPath.fileSize()}")
        val csvPath = Path.of(filenameOut)
        println("size Csv = ${csvPath.fileSize()}")
        println("json/csv size = ${jsonPath.fileSize().toDouble()/csvPath.fileSize()}")
        println()

        // read time
        val stopwatch = Stopwatch()
        val cvrsResult = readCvrsJsonFile(filenameIn)
        val timeJson = stopwatch.stop()
        println("read Json = ${stopwatch}")

        stopwatch.start()
        val roundtrip = readCvrsCsvFile(filenameOut)
        val timeCsv = stopwatch.stop()
        println("read Csv = ${stopwatch}")

        println("json/csv time = ${timeJson.toDouble()/timeCsv}")
    }

    // size Json = 1296559805
    // size Csv = 71412696
    // json/csv size = 18.15587252160316
    //
    // read Json = 7.676 s
    // read Csv = 4.335 s
    // json/csv time = 1.770892724458079

}