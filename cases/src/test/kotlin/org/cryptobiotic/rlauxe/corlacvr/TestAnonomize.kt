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
        testAnonomize(Statewide2020General(), county = "Larimer")
    }

    @Test
    fun testAllStatewideInput() {
        val compare = true
        testAnonomize(Statewide2020General(), compare=compare)
        //testAnonStatewideInput(Statewide2022Primary(), compare=compare)
        testAnonomize(Statewide2023Coordinated(), compare=compare)
        testAnonomize(Statewide2024General(), compare=compare)
        testAnonomize(Statewide2025Coordinated(), compare=compare)
        //testAnonStatewideInput(Statewide2026Primary(), compare=compare)
        //testAnonStatewideInput(Statewide2026PMerged(), compare=compare)
        testAnonomize(Statewide2026PwithCvrs(), compare=compare)
    }

    fun testAnonomize(input: StatewideInputWithCvrs, compare: Boolean = false, county: String? = null) {
        val stopwatch = Stopwatch()
        val countNredacted = mutableMapOf<String, RedactionCount>()
        val baseOutput = "$outputBase/${input.year}"
        validateOutputDir(Path(baseOutput))

        val counties = if (county != null) listOf(county) else input.counties()
        for (county in counties) {
            try {
                val countyInput = input.corlaCountyInput(county)
                if (countyInput == null) continue
                println("===========================================================================================")
                val output = "$baseOutput/$county-Redacted.csv"
                println("County $county from ${countyInput.cvrsSource} to $output")

                if (county == "Boulder") {
                    val sourceFile = File(countyInput.cvrsSource)
                    val targetFile = File(output)

                    try {
                        // Copies source to target; fails if destination already exists
                        sourceFile.copyTo(targetFile, overwrite = false)
                    } catch(e: Exception) {
                        e.printStackTrace()
                    }
                    continue
                }

                val anon = Anonomicer(countyInput.readCorlaCvrs()) // , styleOnly=styleOnly)
                anon.writeRedactedFile(output)
                    .verify(readCorlaCvrs(countyInput.year, countyInput.countyName, output))

                anon.rpt.writeTo("$baseOutput/${county}-Report.txt")

                if (compare) {
                    println("---------------------------------")
                    println("python vs raire")
                    countNredacted[county] = compareTwoRedactions(null, output, show = true)
                }
            } catch (e: Throwable) {
                println("failed on $county: ${e.message}")
            }
        }
        if (compare) writeRedactionCount("$baseRedactedCount/${input.name()}.csv", countNredacted)
        println("that took $stopwatch")
    }
}