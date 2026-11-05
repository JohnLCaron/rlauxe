package org.cryptobiotic.rlauxe.corlacvr

import org.cryptobiotic.rlauxe.corlaInput.Statewide2020General
import org.cryptobiotic.rlauxe.corlaInput.Statewide2020Redacted
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.nfn
import java.io.File
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
    fun testFirstCvrLine() {
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
}