package org.cryptobiotic.rlauxe.corlacvr

import org.cryptobiotic.rlauxe.corlaInput.RedactionCount
import org.cryptobiotic.rlauxe.corlaInput.Statewide2020General
import org.cryptobiotic.rlauxe.corlaInput.Statewide2022Primary
import org.cryptobiotic.rlauxe.corlaInput.Statewide2023Coordinated
import org.cryptobiotic.rlauxe.corlaInput.Statewide2024General
import org.cryptobiotic.rlauxe.corlaInput.Statewide2025Coordinated
import org.cryptobiotic.rlauxe.corlaInput.Statewide2026PMerged
import org.cryptobiotic.rlauxe.corlaInput.Statewide2026Primary
import org.cryptobiotic.rlauxe.corlaInput.Statewide2026PwithCvrs
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.corlaInput.StatewideInputWithCvrs
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestAnonAllInputs {
    val base = "/home/stormy/rla/temp/Anonomice"
    val baseRedactedCount = "src/main/resources/resources/data/cases/corla/redactedCount"

    @Test
    fun testAllAnonStatewideInput() {
        testAnonStatewideInput(Statewide2020General())
        //testAnonStatewideInput(Statewide2022Primary())
        testAnonStatewideInput(Statewide2023Coordinated())
        //testAnonStatewideInput(Statewide2024General())
        testAnonStatewideInput(Statewide2025Coordinated())
        //testAnonStatewideInput(Statewide2026Primary())
        //testAnonStatewideInput(Statewide2026PMerged())
        testAnonStatewideInput(Statewide2026PwithCvrs())
    }

    fun testAnonStatewideInput(input: StatewideInputWithCvrs) {
        val stopwatch = Stopwatch()
        val countNredacted = mutableMapOf<String, RedactionCount>()
        val baseOutput = "$base/${input.name()}Anon"

        for (county in input.counties()) {
            try {
                val countyInput = input.corlaCountyInput(county)
                if (countyInput == null) continue
                println("===========================================================================================")
                println("County $county from ${countyInput.cvrsSource}")

                // val styleOnly = county == "Arapahoe"
                val output = "$baseOutput-$county.csv"
                val anon = Anonomicer(countyInput.readCorlaCvrs()) // , styleOnly=styleOnly)
                anon.redact(output)

                println("---------------------------------")
                println("python vs raire")
                countNredacted[county] = compareTwoRedactions(null, output, show = true)
            } catch (e: Throwable) {
                println("failed on $county: ${e.message}")
            }
        }
        writeRedactionCount("$baseRedactedCount/${input.name()}.csv", countNredacted)
        println("that took $stopwatch")
    }
}