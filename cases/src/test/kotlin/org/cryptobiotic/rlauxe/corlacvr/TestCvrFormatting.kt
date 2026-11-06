package org.cryptobiotic.rlauxe.corlacvr

import org.cryptobiotic.rlauxe.corlaInput.Statewide2020General
import org.cryptobiotic.rlauxe.corlaInput.Statewide2020Redacted
import org.cryptobiotic.rlauxe.corlaInput.defaultCharset
import org.cryptobiotic.rlauxe.util.Locations.testdataDir
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlin.test.Test

class TestCvrFormatting {

    @Test
    fun testRedacted2020Cvrs() {
        val stopwatch = Stopwatch()
        val input = Statewide2020General()
        input.counties().forEach { county ->
            try {
                val stopwatchCounty = Stopwatch()

                val countyInput = input.corlaCountyInput(county)!!
                println("===========================================================================================")
                println("County $county from ${countyInput.cvrsSource}")

                val corlaCvrs = countyInput.readCorlaCvrs()
                println(corlaCvrs.show())
                println("that took $stopwatchCounty")

            } catch (e: Throwable) {
                println("failed on $county: ${e.message}")
            }
        }
        println("that took $stopwatch total")
    }

    @Test
    fun testDoubleQuotes() {
        val stopwatch = Stopwatch()
        val input = Statewide2020Redacted()
        input.counties().forEach { county ->
            try {
                val countyInput = input.corlaCountyInput(county)!!
                println("===========================================================================================")
                println("County $county from ${countyInput.cvrsSource}")

                val file = File(countyInput.cvrsSource)
                val firstFiveLines: List<String> = file.useLines { lines ->
                    lines.take(5).toList()
                }

                if (county == "Garfield") {
                    checkDoubleQuotes(0, firstFiveLines.first())
                } else {
                    checkDoubleQuotes(1, firstFiveLines[1])
                    checkDoubleQuotes(2, firstFiveLines[2])

                }

            } catch (e: Throwable) {
                println("failed on $county: ${e.message}")
            }
        }
        println("that took $stopwatch total")
    }

    fun checkDoubleQuotes(lineno: Int, line: String) {
        var count = 0
        var startPos = 0
        print("D $lineno=[")
        while (true) {
            val start = line.indexOf("\"\"", startIndex = startPos)
            if (start < 0) break
            val end = line.indexOf("\"\"", startIndex = start+2)
            print("${line.substring(start, end+2)},")
            startPos = end+2
            count++
        }
        println("]")
        if (count == 0) checkSingleQuotes(lineno, line)
    }

    fun checkSingleQuotes(lineno: Int, line: String) {
        var startPos = 0
        print("S $lineno=[")
        while (true && startPos < line.length) {
            val start = line.indexOf("\"", startIndex = startPos)
            if (start < 0) break
            val end = line.indexOf("\"", startIndex = start+1)
            if (end < 0) break
            print("${line.substring(start, end+1)},")
            startPos = end+1
        }
        println("]")
    }

    @Test
    fun testCommasFirstCvrLines() {
        val stopwatch = Stopwatch()
        val input = Statewide2020General()
        input.counties().forEach { county ->
            try {
                val countyInput = input.corlaCountyInput(county)!!
                println("===========================================================================================")
                println("County $county from ${countyInput.cvrsSource}")

                val file = File(countyInput.cvrsSource)
                val firstFiveLines: List<String> = file.useLines { lines ->
                    lines.take(5).toList()
                }
                firstFiveLines.forEach { line ->
                    val ncommas = line.count { it == ',' }
                    println("(${nfn(ncommas,2)}): $line")
                }

            } catch (e: Throwable) {
                println("failed on $county: ${e.message}")
            }
        }
        println("that took $stopwatch total")
    }

    @Test
    fun showFirstCvrLines() {
        val nlines = 10
        val stopwatch = Stopwatch()
        val input = Statewide2020General()
        input.counties().forEach { county ->
            try {
                val countyInput = input.corlaCountyInput(county)!!
                println("===========================================================================================")

                val file = File(countyInput.cvrsSource)
                val firstLines: List<String> = file.useLines { lines ->
                    lines.take(nlines).toList()
                }
                firstLines.forEach { line ->
                    println(line)
                }

                val outputFilename = "$testdataDir/first10/$county.csv"
                val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
                firstLines.forEach { line ->
                    writer.write(line)
                    writer.write("\n")
                }
                writer.close()

            } catch (e: Throwable) {
                println("failed on $county: ${e.message}")
            }
        }
        println("that took $stopwatch total")
    }

    @Test
    fun testFremontEncoding() {
        val counties = listOf("Fremont", "Pitkin", "Jefferson", "Sedgwick")
        val testfile = "/home/stormy/dev/github/rla/anonymize_cvr/2020cvrs/Fremont.csv"
        println(testfile)

        // UnicodeDecodeError: 'utf-8' codec can't decode byte 0xf1 in position 4008: invalid continuation byte
        // City of Ca�on City vs City of Cañon City
        val file = File(testfile)
        val text = file.readText(charset = defaultCharset) // utf-8
        repeat(30) {
            val idx = 3990 + it
            println("$idx == ${text[idx]}")
        }
    }
}