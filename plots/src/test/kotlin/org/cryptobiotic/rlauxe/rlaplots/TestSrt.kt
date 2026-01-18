package org.cryptobiotic.rlauxe.rlaplots

import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.estimate.RunRepeatedResult
import kotlin.test.Test
import kotlin.test.assertEquals

class TestSrt {

    @Test
    fun testWriteRead() {

        // SRT(val N: Int, val reportedMean: Double, val reportedMeanDiff: Double, val d: Int, val eta0: Double, val eta0Factor: Double,
        //               val nsuccess: Int, val ntrials: Int, val totalSamplesNeeded: Int, val stddev: Double, val percentHist: Deciles?)
        val target =
            SRT(19, 42.0, -.006,
                mapOf("d" to 99.0, "eta0" to 77.0, "eta0Factor" to 1.1),
                123, 234, 456, 0.009)

        val tempFile = kotlin.io.path.createTempFile().toString()
        val writer = SRTcsvWriter(tempFile)
        writer.writeCalculations(listOf(target))
        writer.close()

        val reader = SRTcsvReader(tempFile)
        val srts = reader.readCalculations()

        assertEquals(1, srts.size)
        println(srts[0])
        assertEquals(target, srts[0])
    }

    @Test
    fun testWriteReadBetaResult() {
        val ntrials = 111
        // val hist = Deciles(ntrials)
        // repeat(ntrials) { hist.add(it + 1) }
        val status = mapOf(TestH0Status.LimitReached to 1, TestH0Status.AcceptNull to 2, TestH0Status.StatRejectNull to 3, TestH0Status.SampleSumRejectNull to 4 )

        //                val testParameters: Map<String, Double>, // various parameters, depends on the test
        //               val N: Int,                  // population size (eg number of ballots)
        //               val totalSamplesNeeded: Int, // total number of samples needed in nsuccess trials
        //               val nsuccess: Int,           // number of successful trials
        //               val ntrials: Int,            // total number of trials
        //               val variance: Double,        // variance over ntrials of samples needed
        //               val percentHist: Deciles? = null, // histogram of successful sample size as percentage of N, count trials in 10% bins
        //               val status: Map<TestH0Status, Int>? = null, // count of the trial status
        val parameters = mapOf("p1" to .01, "p2" to .001, "lam" to 1.1, "margin" to .04)
        val betta = RunRepeatedResult(parameters, N=43, totalSamplesNeeded=112, nsuccess=12, ntrials=ntrials, variance=11.5, status)

        // N: Int, reportedMean: Double, reportedMeanDiff: Double, d: Int, eta0Factor: Double = 0.0
        val target = betta.makeSRT(reportedMean=.52, reportedMeanDiff=.005)

        val tempFile = kotlin.io.path.createTempFile().toString()
        val writer = SRTcsvWriter(tempFile)
        writer.writeCalculations(listOf(target))
        writer.close()

        val reader = SRTcsvReader(tempFile)
        val srts = reader.readCalculations()

        assertEquals(1, srts.size)
        println(srts[0])
        assertEquals(target, srts[0])
    }

    @Test
    fun testWriteReadVersion1() {
        // SRT(val N: Int, val reportedMean: Double, val reportedMeanDiff: Double, val d: Int, val eta0: Double, val eta0Factor: Double,
        //               val nsuccess: Int, val ntrials: Int, val totalSamplesNeeded: Int, val stddev: Double, val percentHist: Deciles?)
        val target =
            SRT(19, 42.0, -.006,
                mapOf("d" to 99.0, "eta0" to 77.0, "eta0Factor" to 1.1),
                123, 234, 456, 0.009)

        val tempFile = kotlin.io.path.createTempFile().toString()
        val writer = SRTcsvWriterVersion1(tempFile)
        writer.writeCalculations(listOf(target))
        writer.close()

        val reader = SRTcsvReaderVersion1(tempFile)
        val srts = reader.readCalculations()

        assertEquals(1, srts.size)
        println(srts[0])
        assertEquals(target, srts[0])
    }

    @Test
    fun testNullHistogram() {
        // SRT(val N: Int, val reportedMean: Double, val reportedMeanDiff: Double, val d: Int, val eta0: Double, val eta0Factor: Double,
        //               val nsuccess: Int, val ntrials: Int, val totalSamplesNeeded: Int, val stddev: Double, val percentHist: Deciles?)
        val target =
            SRT(19, 42.0, -.006,
                mapOf("d" to 99.0, "eta0" to 77.0, "eta0Factor" to 1.1),
                123, 234, 456, 0.009)

        val tempFile = kotlin.io.path.createTempFile().toString()
        val writer = SRTcsvWriter(tempFile)
        writer.writeCalculations(listOf(target))
        writer.close()

        val reader = SRTcsvReader(tempFile)
        val srts = reader.readCalculations()

        assertEquals(1, srts.size)
        println(srts[0])
        assertEquals(target, srts[0])
    }
}