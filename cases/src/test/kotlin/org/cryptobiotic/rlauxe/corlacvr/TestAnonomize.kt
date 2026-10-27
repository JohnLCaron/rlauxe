package org.cryptobiotic.rlauxe.corlacvr

import org.cryptobiotic.rlauxe.corlaInput.RedactionCount
import org.cryptobiotic.rlauxe.corlaInput.Statewide2020General
import org.cryptobiotic.rlauxe.corlaInput.Statewide2023Coordinated
import org.cryptobiotic.rlauxe.corlaInput.Statewide2024General
import org.cryptobiotic.rlauxe.corlaInput.Statewide2025Coordinated
import org.cryptobiotic.rlauxe.corlaInput.Statewide2026PwithCvrs
import org.cryptobiotic.rlauxe.corlaInput.StatewideInputWithCvrs
import org.cryptobiotic.rlauxe.corlaInput.readCorlaCvrs
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.io.File
import kotlin.io.path.Path
import kotlin.test.Test

class TestAnonomize {
    val outputBase = "/home/stormy/rla/temp/Anonomice"
    val baseRedactedCount = "src/main/resources/resources/data/cases/corla/redactedCount"

    @Test
    fun testOneStateOrCounty() {
        testAnonomize(Statewide2020General(), county = "Grand")
    }

    @Test
    fun compare2026withPython() {
        testAnonomize(Statewide2026PwithCvrs(), python="/home/stormy/datadrive/github/nealmcb/anonymize_cvr/testData/2026") // , county = "Boulder")
    }

    @Test
    fun compare2020withPython() {
        testAnonomize(Statewide2020General(), python="/home/stormy/datadrive/github/nealmcb/anonymize_cvr/testData/2020") // , county = "Boulder")
    }

    @Test
    fun testAllStatewideInput() {
        val python = null
        // testAnonomize(Statewide2020General(), python=python)
        //testAnonStatewideInput(Statewide2022Primary(), python=python)
        testAnonomize(Statewide2023Coordinated(), python=python)
        testAnonomize(Statewide2024General(), python=python)
        testAnonomize(Statewide2025Coordinated(), python=python)
        //testAnonStatewideInput(Statewide2026Primary(), python=python)
        //testAnonStatewideInput(Statewide2026PMerged(), python=python)
        testAnonomize(Statewide2026PwithCvrs(), python=python)
    }

    fun testAnonomize(input: StatewideInputWithCvrs, python:String?=null, county: String? = null) {
        val stopwatch = Stopwatch()
        val countNredacted = mutableMapOf<String, RedactionCount>()
        val baseOutput = "$outputBase/${input.year}"
        validateOutputDir(Path(baseOutput))
        // slow - could parellelize ??
        val counties = if (county != null) listOf(county) else input.counties()
        for (county in counties) {
            try {
                val countyInput = input.corlaCountyInput(county)
                if (countyInput == null) continue
                println("===========================================================================================")
                val output = "$baseOutput/$county-Redacted.csv"
                println("County $county from ${countyInput.cvrsSource} to $output")

                if (county == "Boulder") {
                    val sourceFile = if (input.year != "2026pc") countyInput.cvrsSource else
"/home/stormy/dev/github/rla/rlauxe/cases/src/main/resources/resources/data/cases/boulder2026p/2026P-Redacted-CVR-Public.csv"
                    val targetFile = output

                    try {
                        File(sourceFile).copyTo(File(targetFile), overwrite = true)
                    } catch(e: Exception) {
                        e.printStackTrace()
                    }

                } else {
                    val anon = Anonomicer(countyInput.readCorlaCvrs()) // , styleOnly=styleOnly)
                    anon.writeRedactedFile(output)
                        .verify(readCorlaCvrs(countyInput.year, countyInput.countyName, output))
                    anon.rpt.writeTo("$baseOutput/${county}-Report.txt")
                    println("write and verify Redaction to $output")
                }

                println("---------------------------------")
                println("python vs raire")
                val pythonFile = if (python != null) "$python/$county.csv" else null
                countNredacted[county] = compareTwoRedactions(input.year, county, pythonFile, output, show = true)

            } catch (e: Throwable) {
                println("failed on $county: ${e.message}")
            }
        }
        if (county == null) {
            writeRedactionCount("$baseRedactedCount/${input.name()}.csv", countNredacted)
            println("writeRedactionCount to $baseRedactedCount/${input.name()}.csv")
        }
        println("that took $stopwatch")
    }
}