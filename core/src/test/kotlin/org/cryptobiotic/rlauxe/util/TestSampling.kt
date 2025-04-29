package org.cryptobiotic.rlauxe.util

import org.junit.jupiter.api.Test
import kotlin.random.Random

class TestSampling {
    val ntrials = 1000
    val Nc = 100000

    @Test
    fun testSample() {
        val fac = 1000.0
        testSampleRepeated(.01, fac)
        testSampleRepeated(.001, fac)
        testSampleRepeated(.0001, fac)
        testSampleRepeated(.00001, fac)
    }

    fun testSampleRepeated(fuzzPct: Double, fac: Double) {
        val changeDiff = Welford()
        repeat(ntrials) {
            changeDiff.update(testSamples(fuzzPct, fac))
        }
        val expect = Nc * fuzzPct
        println(" fuzzPct=$fuzzPct expect=$expect expectDiff: ${changeDiff.show()}")
    }

    fun testSamples(fuzzPct: Double, fac: Double = 1000.0): Double {
        val expect = Nc * fuzzPct
        val limit = fac / fuzzPct
        var count = 0
        repeat(Nc) {
            val r = Random.nextDouble(limit)
            if (r < fac) {
                count++
            }
        }
        // println("fac = $fac, count= $count expect=$expect wantPct = $fuzzPct, gotPct = ${count/Nc.toDouble()}")
        return count - expect
    }

    @Test
    fun testSampleDouble() {
        testSampleDouble(.01)
        testSampleDouble(.001)
        testSampleDouble(.0001)
        testSampleDouble(.00001)
    }

    fun testSampleDouble(fuzzPct: Double) {
        val expect = Nc * fuzzPct
        var count = 0
        repeat(Nc) {
            val r = Random.nextDouble(1.0)
            if (r < fuzzPct) {
                count++
            }
        }
        println("count= $count expect=$expect wantPct = $fuzzPct, gotPct = ${count/Nc.toDouble()}")
    }
}