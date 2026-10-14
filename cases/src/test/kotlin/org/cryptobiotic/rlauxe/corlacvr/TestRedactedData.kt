package org.cryptobiotic.rlauxe.corlacvr

import org.cryptobiotic.rlauxe.corlaInput.Colorado2020Redacted
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test

class TestRedactedData {

    @Test
    fun testRedacted2020Cvrs() {
        val stopwatch = Stopwatch()
        val input = Colorado2020Redacted()
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
}