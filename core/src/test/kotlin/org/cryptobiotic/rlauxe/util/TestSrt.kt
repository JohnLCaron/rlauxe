package org.cryptobiotic.rlauxe.util

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TestSrt {

    @Test
    fun testWriteRead() {
        val ntrials = 111
        val hist = Deciles(ntrials)
        repeat(ntrials) { hist.add(it + 1) }

        // SRT(val N: Int, val reportedMean: Double, val reportedMeanDiff: Double, val d: Int, val eta0: Double, val eta0Factor: Double,
        //               val nsuccess: Int, val ntrials: Int, val totalSamplesNeeded: Int, val stddev: Double, val percentHist: Deciles?)
        val target = SRT(19, 42.0, -.006, 99, mapOf("eta0" to 77.0), 1.1, 123, 234, 456, 0.009, hist)

        val testFile = "/home/stormy/temp/test/testWriteRead.csv"
        val writer = SRTcsvWriter(testFile)
        writer.writeCalculations(listOf(target))
        writer.close()

        val reader = SRTcsvReader(testFile)
        val srts = reader.readCalculations()

        assertEquals(1, srts.size)
        println(srts[0])
        assertEquals(target, srts[0])
    }

    @Test
    fun testNullHistogram() {
        // SRT(val N: Int, val reportedMean: Double, val reportedMeanDiff: Double, val d: Int, val eta0: Double, val eta0Factor: Double,
        //               val nsuccess: Int, val ntrials: Int, val totalSamplesNeeded: Int, val stddev: Double, val percentHist: Deciles?)
        val target = SRT(19, 42.0, -.006, 99, mapOf("eta0" to 77.0), 1.1, 123, 234, 456, 0.009, null)

        val testFile = "/home/stormy/temp/test/testNullHistogram.csv"
        val writer = SRTcsvWriter(testFile)
        writer.writeCalculations(listOf(target))
        writer.close()

        val reader = SRTcsvReader(testFile)
        val srts = reader.readCalculations()

        assertEquals(1, srts.size)
        println(srts[0])
        assertEquals(target, srts[0])
    }
}