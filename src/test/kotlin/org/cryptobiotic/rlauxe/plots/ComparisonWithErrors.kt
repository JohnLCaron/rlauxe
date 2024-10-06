@file:OptIn(ExperimentalCoroutinesApi::class)

package org.cryptobiotic.rlauxe.plots

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import kotlinx.coroutines.yield
import org.cryptobiotic.rlauxe.core.ComparisonWithErrors
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.comparisonAssorterCalc
import org.cryptobiotic.rlauxe.sim.AlphaMartRepeatedResult
import org.cryptobiotic.rlauxe.core.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.core.theta2margin
import org.cryptobiotic.rlauxe.core.eps
import org.cryptobiotic.rlauxe.sim.runAlphaMartRepeated
import org.cryptobiotic.rlauxe.makeStandardComparisonAssorter
import org.cryptobiotic.rlauxe.util.SRT
import org.cryptobiotic.rlauxe.util.SRTcsvWriter
import org.cryptobiotic.rlauxe.sim.makeSRT
import kotlin.collections.set
import kotlin.test.Test

// create the raw data for showing plots of comparison with theta != eta0
// these are 4 dimensional: N, theta, d, diffMean
class ComparisonWithErrors {
    val showCalculation = false
    val showCalculationAll = false

    data class ComparisonTask(
        val idx: Int,
        val N: Int,
        val cvrMean: Double,
        val cvrMeanDiff: Double,
        val eta0Factor: Double,
        val cvrs: List<Cvr>
    )

    @Test
    fun comparisonNvsTheta() {
        val cvrMeans = listOf(.506, .507, .508, .509, .51, .52, .53, .54, .55, .6, .7)
        val nlist = listOf(50000, 20000, 10000, 5000, 1000)

        val d = 10000
        val ntrials = 1000
        val cvrMeanDiff = -.005
        val eta0Factors = listOf(1.0, 1.25, 1.5, 1.75, 2.0 - eps)
        val nthreads = 30
        val results = mutableMapOf<Double, List<SRT>>() // eta0Factor vs list<SRT>

        eta0Factors.forEach { eta0Factor ->
            val tasks = mutableListOf<ComparisonTask>()
            var taskIdx = 0
            cvrMeans.forEach { cvrMean ->
                nlist.forEach { N ->
                    val cvrs = makeCvrsByExactMean(N, cvrMean)
                    tasks.add(
                        ComparisonTask(
                            taskIdx++,
                            N,
                            cvrMean,
                            cvrMeanDiff = cvrMeanDiff,
                            eta0Factor = eta0Factor,
                            cvrs
                        )
                    )
                }
            }
            // val writer = SRTwriter("/home/stormy/temp/CvrComparison/comparisonNvsTheta$eta0Factor.csv")

            calculations = mutableListOf<SRT>()
            runBlocking {
                val taskProducer = produceTasks(tasks)
                val calcJobs = mutableListOf<Job>()
                repeat(nthreads) {
                    calcJobs.add(
                        launchCalculations(taskProducer) { task ->
                            calculate(task, ntrials, d = d, cvrMeanDiff = task.cvrMeanDiff)
                        })
                }

                // wait for all calculations to be done
                joinAll(*calcJobs.toTypedArray())
            }
            //writer.writeCalculations(calculations)
            //writer.close()

            results[eta0Factor] = calculations

            /*
            println()
            println("ComparisonWithErrors.comparisonNvsTheta ballot comparison, ntrials=$ntrials, eta0Factor=$eta0Factor, d = $d, cvrMeanDiff=$cvrMeanDiff, theta(col) vs N(row)")
            colHeader(calculations, "theta", colf = "%6.3f") { it.theta }
            println()

            plotNTsuccessPct(calculations, "plotNTsuccessPct", colTitle = "cvrMean")
            plotNTsamples(calculations, "plotNTsamples", colTitle = "cvrMean")
            plotNTpct(calculations, "plotNTpct", colTitle = "cvrMean")

             */
        }

        println("ComparisonWithErrors.comparisonNvsTheta samplePct ratios across cvrMean: ntrials=$ntrials, d = $d, cvrMeanDiff=$cvrMeanDiff, theta(col) vs N(row)")
        colHeader(calculations, "theta", colf = "%6.3f") { it.theta }
        plotRatio(results)

        // 09/25/24
        // ComparisonWithErrors.comparisonNvsTheta samplePct ratios across cvrMeanDiff: ntrials=10000, d = 10000, cvrMeanDiff=-0.005, theta(col) vs N(row)
        //  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.505,  0.515,  0.525,  0.535,  0.545,  0.595,  0.695,
        //
        //ratio eta0Factor=1.0,  theta(col) vs N(row)
        //cvrMean: 0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.600,  0.700,
        //  1000, -0.000,  0.000,  0.000,  0.000,  0.000,  1.482,  3.339,  4.956,  6.046,  6.610,  6.471,  5.038,
        //  5000, -0.000,  0.000,  0.000,  0.000,  0.000,  3.367, 10.355, 12.695, 12.586, 11.840,  7.940,  5.383,
        // 10000,  0.000,  0.000,  0.000,  0.000,  0.000,  5.552, 14.976, 16.187, 14.777, 13.090,  8.184,  5.470,
        // 20000, -0.000,  0.000,  0.000,  0.000,  0.000,  9.330, 19.913, 18.789, 16.048, 13.870,  8.331,  5.495,
        // 50000, -0.000,  0.000,  0.000,  0.000,  0.000, 17.629, 25.076, 20.630, 16.997, 14.444,  8.434,  5.405,
        //geometric mean = 3.6281420913383373
        //
        //ratio eta0Factor=1.25,  theta(col) vs N(row)
        //cvrMean: 0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.600,  0.700,
        //  1000, -0.000,  0.000,  0.000,  0.000,  2.029,  1.350,  1.927,  2.148,  2.250,  2.284,  2.254,  2.188,
        //  5000, -0.000,  0.000,  0.000,  0.000,  6.401,  1.514,  2.187,  2.357,  2.399,  2.425,  2.299,  2.239,
        // 10000,  0.000,  0.000,  0.000,  0.000, 10.365,  1.537,  2.161,  2.395,  2.432,  2.419,  2.294,  2.247,
        // 20000, -0.000,  0.000,  0.000,  0.000, 16.693,  1.557,  2.184,  2.397,  2.429,  2.430,  2.290,  2.250,
        // 50000, -0.000,  0.000,  0.000,  0.000, 28.955,  1.574,  2.219,  2.386,  2.434,  2.441,  2.323,  2.197,
        //geometric mean = 1.8845224638455382
        //
        //ratio eta0Factor=1.5,  theta(col) vs N(row)
        //cvrMean: 0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.600,  0.700,
        //  1000, -0.000,  0.000,  0.000,  1.543,  1.813,  1.112,  1.259,  1.310,  1.344,  1.353,  1.359,  1.444,
        //  5000, -0.000,  0.000,  2.627,  2.465,  4.338,  1.009,  1.264,  1.334,  1.351,  1.372,  1.374,  1.434,
        // 10000,  0.000,  0.000,  0.000,  3.065,  5.763,  1.000,  1.231,  1.336,  1.369,  1.371,  1.369,  1.446,
        // 20000, -0.000,  0.000,  0.000,  3.687,  7.127,  1.000,  1.226,  1.332,  1.354,  1.361,  1.368,  1.442,
        // 50000, -0.000,  0.000,  0.000,  4.156,  9.832,  1.000,  1.241,  1.326,  1.349,  1.366,  1.374,  1.408,
        //geometric mean = 1.4740755088560604
        //
        //ratio eta0Factor=1.75,  theta(col) vs N(row)
        //cvrMean: 0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.600,  0.700,
        //  1000, -0.000,  0.000,  1.190,  1.280,  1.519,  1.000,  1.000,  1.000,  1.000,  1.000,  1.000,  1.081,
        //  5000, -0.000,  0.000,  1.545,  1.617,  2.256,  1.000,  1.000,  1.000,  1.000,  1.000,  1.000,  1.075,
        // 10000,  0.000,  0.000,  1.469,  1.808,  2.716,  1.053,  1.000,  1.000,  1.000,  1.000,  1.000,  1.084,
        // 20000, -0.000,  0.000,  1.606,  1.949,  2.923,  1.116,  1.000,  1.000,  1.000,  1.000,  1.000,  1.085,
        // 50000, -0.000,  1.357,  1.446,  1.929,  2.655,  1.197,  1.000,  1.000,  1.000,  1.000,  1.000,  1.057,
        //geometric mean = 1.1769269127560964
        //
        //ratio eta0Factor=1.9999999999999998,  theta(col) vs N(row)
        //cvrMean: 0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.600,  0.700,
        //  1000, -0.000,  1.000,  1.000,  1.000,  1.000,  1.154,  1.324,  1.251,  1.174,  1.106,  1.004,  1.000,
        //  5000, -0.000,  1.000,  1.000,  1.000,  1.000,  1.908,  1.877,  1.538,  1.355,  1.223,  1.006,  1.000,
        // 10000,  1.000,  1.000,  1.000,  1.000,  1.000,  2.514,  2.015,  1.612,  1.388,  1.245,  1.015,  1.000,
        // 20000, -0.000,  1.000,  1.000,  1.000,  1.000,  3.251,  2.161,  1.664,  1.373,  1.250,  1.005,  1.000,
        // 50000, -0.000,  1.000,  1.000,  1.000,  1.000,  4.118,  2.308,  1.663,  1.389,  1.229,  1.003,  1.000,
        //geometric mean = 1.2232986997072774
    }

    // choose one d and cvrMeanDiff
    @Test
    fun comparisonNSampleHistogram() {
        //val cvrMeans = listOf(.51, .52) //, .53, .54, .55, .575, .6, .65, .7)
        val cvrMeans = listOf(0.506,  0.507,  0.508,  0.509,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575)
        val nlist = listOf(50000, 20000, 10000, 5000, 1000)

        val d = 10000
        val ntrials = 1000
        val cvrMeanDiff = -.005
        val eta0Factors = listOf(1.0, 1.25, 1.5, 1.75, 2.0 - eps)
        val nthreads = 30
        val results = mutableMapOf<Double, List<SRT>>() // eta0Factor vs list<SRT>

        eta0Factors.forEach { eta0Factor ->
            val tasks = mutableListOf<ComparisonTask>()
            var taskIdx = 0
            cvrMeans.forEach { cvrMean ->
                nlist.forEach { N ->
                    val cvrs = makeCvrsByExactMean(N, cvrMean)
                    tasks.add(
                        ComparisonTask(
                            taskIdx++,
                            N,
                            cvrMean,
                            cvrMeanDiff = cvrMeanDiff,
                            eta0Factor = eta0Factor,
                            cvrs
                        )
                    )
                }
            }
            // val writer = SRTwriter("/home/stormy/temp/CvrComparison/comparisonNvsTheta$eta0Factor.csv")

            calculations = mutableListOf<SRT>()
            runBlocking {
                val taskProducer = produceTasks(tasks)
                val calcJobs = mutableListOf<Job>()
                repeat(nthreads) {
                    calcJobs.add(
                        launchCalculations(taskProducer) { task ->
                            calculate(task, ntrials, d = d, cvrMeanDiff = task.cvrMeanDiff)
                        })
                }

                // wait for all calculations to be done
                joinAll(*calcJobs.toTypedArray())
            }
            //writer.writeCalculations(calculations)
            //writer.close()

            results[eta0Factor] = calculations

            println()
            println("ComparisonWithErrors.comparisonNSampleHistogram")
            println("ballot comparison, ntrials=$ntrials, eta0Factor=$eta0Factor, d = $d, cvrMeanDiff=$cvrMeanDiff, theta(col) vs N(row)")
            println()
            colHeader(calculations, "theta", colf = "%6.3f") { it.theta }

            plotNTsamplesPct(calculations, "plotNTpct", colTitle = "cvrMean")
        }

        println("samplePct ratios across cvrMeanDiff: ntrials=$ntrials, d = $d, cvrMeanDiff=$cvrMeanDiff, theta(col) vs N(row)")
        plotRatio(results)
    }

    // choose one N and d
    @Test
    fun cvrComparisonFixNandD() {
        val cvrMeans = listOf(.501, .502, .503, .504, .505, .51, .52, .53, .54, .55, .6, .7)
        val cvrMeanDiffs = listOf(0.02, 0.01, 0.0, -.005, -.01, -0.02, -0.03, -0.04, -0.05, -0.1, -0.2)

        val thetas = listOf(.501, .502, .503, .504, .505, .51, .52, .53, .54, .55, .575, .6, .65, .7)

        val dl = listOf(100, 1000, 10000)
        val nlist = listOf(50000, 20000, 10000, 5000, 1000)
        val cvrMean = .55
        val N = 10000
        val d = 10

        // * If overstatement error is always zero (no errors in CRV), the assort value is 1 / (2.0 - margin/this.assorter.upperBound())
        val expected = 2 / (2 - theta2margin(cvrMean)) // u = 2/(2-assorter_margin)
        val etas = listOf(0.9, 0.95, 0.99, 1.0, expected, 2.0, 5.0)

        val tasks = mutableListOf<ComparisonTask>()
        var taskIdx = 0
        cvrMeanDiffs.forEach { cvrDiff ->
            etas.forEach { eta ->
                val cvrs = makeCvrsByExactMean(N, cvrMean)
                tasks.add(ComparisonTask(taskIdx++, N, cvrMean, cvrDiff, eta0Factor = eta, cvrs))
            }
        }

        val nthreads = 30
        val ntrials = 100

        // val cvrMeanDiffs = listOf(0.005, 0.01, 0.02, 0.05, 0.1, 0.2)   // % greater than actual mean
        // val cvrMeanDiffs = listOf(-0.004, -0.01, -0.02,- 0.04, -0.09)   // % less than actual mean

        val writer = SRTcsvWriter("/home/stormy/temp/CvrComparison/SRT$ntrials.csv")
        runBlocking {
            val taskProducer = produceTasks(tasks)
            val calcJobs = mutableListOf<Job>()
            repeat(nthreads) {
                calcJobs.add(
                    launchCalculations(taskProducer) { task ->
                        calculate(task, ntrials, d = d, cvrMeanDiff = task.cvrMeanDiff)
                    })
            }

            // wait for all calculations to be done
            joinAll(*calcJobs.toTypedArray())
        }
        writer.writeCalculations(calculations)
        writer.close()

        colHeader(calculations, "cvrMean", colf = "%6.3f") { it.reportedMean }

        val titleFail =
            " failurePct, ballot comparison, cvrMean=$cvrMean, d = $d, error-free\n theta (col) vs eta0Factor (row)"
        plotSRS(calculations, titleFail, true, colf = "%6.3f", rowf = "%6.2f",
            colFld = { srt: SRT -> cvrMean + srt.reportedMeanDiff },
            rowFld = { srt: SRT -> srt.eta0Factor },
            fld = { srt: SRT -> srt.failPct }
        )

        val title =
            " nsamples, ballot comparison, cvrMean=$cvrMean, d = $d, error-free\n theta (col) vs eta0Factor (row)"
        plotSRS(calculations, title, true, colf = "%6.3f", rowf = "%6.2f",
            colFld = { srt: SRT -> cvrMean + srt.reportedMeanDiff },
            rowFld = { srt: SRT -> srt.eta0Factor },
            fld = { srt: SRT -> srt.nsamples.toDouble() }
        )

        val titlePct = " pct nsamples, ballot comparison, d = $d, error-free\n theta (col) vs eta0Factor (row)"
        plotSRS(calculations, titlePct, false, colf = "%6.3f", rowf = "%6.2f",
            colFld = { srt: SRT -> cvrMean + srt.reportedMeanDiff },
            rowFld = { srt: SRT -> srt.eta0Factor },
            fld = { srt: SRT -> 100.0 * srt.nsamples / srt.N }
        )

        // expect 59 at .55, eta0=20 (diff == 0)
        //
        //  failurePct, ballot comparison, cvrMean=0.55, d = 100, error-free
        // theta (col) vs eta0 (row)
        //      ,  0.350,  0.450,  0.500,  0.510,  0.520,  0.530,  0.540,  0.545,  0.550,  0.560,  0.570,  0.600,  0.650,  0.750,
        //  0.20,    100,    100,    100,      0,      0,      0,      0,      0,      0,      0,      0,      0,      0,      0,
        //  0.50,    100,    100,    100,      0,      0,      0,      0,      0,      0,      0,      0,      0,      0,      0,
        //  1.00,    100,    100,     98,      0,      0,      0,      0,      0,      0,      0,      0,      0,      0,      0,
        //  2.00,    100,    100,     98,      0,      0,      0,      0,      0,      0,      0,      0,      0,      0,      0,
        //  5.00,    100,     99,     97,      0,      0,      0,      0,      0,      0,      0,      0,      0,      0,      0,
        // 10.00,    100,    100,     95,      0,      0,      0,      0,      0,      0,      0,      0,      0,      0,      0,
        // 20.00,    100,    100,     96,      0,      0,      0,      0,      0,      0,      0,      0,      0,      0,      0,
        //
        // nsamples, ballot comparison, cvrMean=0.55, d = 100, error-free
        // theta (col) vs eta0 (row)
        //      ,  0.350,  0.450,  0.500,  0.510,  0.520,  0.530,  0.540,  0.545,  0.550,  0.560,  0.570,  0.600,  0.650,  0.750,
        //  0.20,   8639,   9501,  10000,   8143,   5918,   4340,   3287,   2931,   2620,   2147,   1808,   1202,    743,    415,
        //  0.50,   8638,   9502,  10000,   7374,   4220,   2496,   1678,   1396,   1189,    905,    727,    431,    249,    127,
        //  1.00,   8638,   9501,   9805,   5760,   1509,    568,    219,    108,     77,     66,     56,     43,     30,     17,
        //  2.00,   8638,   9500,   9801,   9307,   7780,   6515,   3536,    966,     59,     53,     48,     38,     26,     17,
        //  5.00,   8638,   9407,   9701,   8914,   8529,   6590,   4325,   1353,     59,     51,     47,     37,     27,     18,
        // 10.00,   8637,   9502,   9502,   8618,   7946,   6901,   4661,   2155,     59,     53,     47,     37,     27,     17,
        // 20.00,   8637,   9502,   9602,   9209,   8530,   6804,   5114,   2745,     59,     52,     49,     37,     26,     16,
        //
        // pct nsamples, ballot comparison, d = 100, error-free
        // theta (col) vs eta0 (row)
        //      ,  0.350,  0.450,  0.500,  0.510,  0.520,  0.530,  0.540,  0.545,  0.550,  0.560,  0.570,  0.600,  0.650,  0.750,
        //  0.20,  86.40,  95.01, 100.00,  81.44,  59.18,  43.40,  32.88,  29.31,  26.20,  21.47,  18.08,  12.03,   7.43,   4.16,
        //  0.50,  86.39,  95.03, 100.00,  73.74,  42.21,  24.96,  16.79,  13.96,  11.89,   9.06,   7.28,   4.31,   2.49,   1.28,
        //  1.00,  86.39,  95.02,  98.05,  57.60,  15.10,   5.69,   2.19,   1.09,   0.77,   0.67,   0.56,   0.43,   0.30,   0.18,
        //  2.00,  86.38,  95.00,  98.01,  93.07,  77.80,  65.16,  35.37,   9.67,   0.59,   0.53,   0.48,   0.39,   0.26,   0.18,
        //  5.00,  86.39,  94.08,  97.02,  89.14,  85.30,  65.90,  43.25,  13.54,   0.59,   0.52,   0.47,   0.38,   0.27,   0.18,
        // 10.00,  86.37,  95.03,  95.03,  86.19,  79.46,  69.01,  46.62,  21.55,   0.59,   0.54,   0.47,   0.38,   0.27,   0.18,
        // 20.00,  86.38,  95.02,  96.02,  92.09,  85.30,  68.05,  51.14,  27.46,   0.59,   0.52,   0.49,   0.38,   0.27,   0.16,
    }

    // create full 4D: N, cvrMean, cvrMeanDiff, d. Choose what plots you want to see in PlotComparisonWithErrors
    @Test
    fun cvrComparisonFull() {
        val cvrMeans = listOf(.501, .502, .503, .504, .505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        val nlist = listOf(50000, 20000, 10000, 5000, 1000)
        val tasks = mutableListOf<ComparisonTask>()

        var taskIdx = 0
        nlist.forEach { N ->
            cvrMeans.forEach { cvrMean ->
                val cvrs = makeCvrsByExactMean(N, cvrMean)
                tasks.add(ComparisonTask(taskIdx++, N, cvrMean, 0.0, eta0Factor = 1.0, cvrs))
            }
        }

        val nthreads = 20
        val nrepeat = 100

        // val cvrMeanDiffs = listOf(0.005, 0.01, 0.02, 0.05, 0.1, 0.2)   // % greater than actual mean
        // val cvrMeanDiffs = listOf(-0.004, -0.01, -0.02,- 0.04, -0.09)   // % less than actual mean
        val cvrMeanDiffs = listOf(0.2, 0.1, 0.05, 0.025, 0.01, 0.005, 0.0, -.005, -.01, -.025, -.05, -0.1, -0.2)
        val dl = listOf(100)

        val writer = SRTcsvWriter("/home/stormy/temp/CvrComparison/Full$nrepeat.csv")
        var totalCalculations = 0

        cvrMeanDiffs.forEach { cvrMeanDiff ->
            val dlcalcs = mutableMapOf<Int, List<SRT>>()
            dl.forEach { d ->

                runBlocking {
                    val taskProducer = produceTasks(tasks)
                    val calcJobs = mutableListOf<Job>()
                    repeat(nthreads) {
                        calcJobs.add(
                            launchCalculations(taskProducer) { task ->
                                calculate(task, nrepeat, d = d, cvrMeanDiff = cvrMeanDiff)
                            })
                    }

                    // wait for all calculations to be done
                    joinAll(*calcJobs.toTypedArray())
                }

                dlcalcs[d] = calculations.toList()
                writer.writeCalculations(calculations)
                println(" cvrMeanDiff=$cvrMeanDiff, $d ncalcs = ${calculations.size}")
                totalCalculations += calculations.size

                calculations = mutableListOf<SRT>()
            }

        }
        writer.close()
        println("totalCalculations = $totalCalculations")
    }

    // Examine the false positives: succeeded when it should not have, as function of eta0Factors
    // Results are in README: eta0 cannot exceed assorter upperBounds.
    @Test
    fun cvrComparisonFailure() {
        val nlist = listOf(50000, 20000, 10000, 5000, 1000)
        // val cvrMeanDiffs = listOf(0.005, 0.01, 0.02, 0.05, 0.1, 0.2)   // % greater than actual mean
        // val cvrMeanDiffs = listOf(-0.004, -0.01, -0.02,- 0.04, -0.09)   // % less than actual mean
        val dl = listOf(100, 1000)

        val cvrMeanDiff = -.005
        val cvrMeans = listOf(.501, .502, .503, .504, .505, .506, .508, .51, .52, .53, .54)// , .6, .65, .7)
        // val cvrMeans = listOf(.506, .507, .508, .509, .51, .52, .53, .54, .55, .575) // , .6 , .65, .7)

        // val eta0Factors = listOf(1.0, 1.25, 1.5, 1.75, 1.99)
        val eta0Factors = listOf(1.0, 1.25, 1.5, 1.75, 1.99, 2.0, 2.10, 2.20)
        val N = 10000
        val d = 10000

        val nthreads = 30
        val ntrials = 1000

        val tasks = mutableListOf<ComparisonTask>()
        var taskIdx = 0
        cvrMeans.forEach { cvrMean ->
            eta0Factors.forEach { eta0Factor ->
                val cvrs = makeCvrsByExactMean(N, cvrMean)
                tasks.add(ComparisonTask(taskIdx++, N, cvrMean, cvrMeanDiff, eta0Factor = eta0Factor, cvrs))
            }
        }

        val writer = SRTcsvWriter("/home/stormy/temp/CvrComparison/Failures.csv")
        runBlocking {
            val taskProducer = produceTasks(tasks)
            val calcJobs = mutableListOf<Job>()
            repeat(nthreads) {
                calcJobs.add(
                    launchCalculations(taskProducer) { task ->
                        calculate(task, ntrials, d = d, cvrMeanDiff = task.cvrMeanDiff)
                    })
            }

            // wait for all calculations to be done
            joinAll(*calcJobs.toTypedArray())
        }
        writer.writeCalculations(calculations)
        writer.close()

        println("ComparisonWithErrors.cvrComparisonFailure ntrials=$ntrials, N=$N, d=$d cvrMeanDiff=$cvrMeanDiff; theta (col) vs etaFactor (row)")
        colHeader(calculations, "cvrMean", colf = "%6.3f") { it.reportedMean }

        plotSRS(calculations, "successes", true, colf = "%6.3f", rowf = "%6.2f", colTitle = "theta",
            colFld = { srt: SRT -> srt.theta },
            rowFld = { srt: SRT -> srt.eta0Factor },
            fld = { srt: SRT -> srt.nsuccess.toDouble() }
        )

        plotSRS(calculations, "successPct", false, colf = "%6.3f", rowf = "%6.2f", ff = "%6.1f", colTitle = "theta",
            colFld = { srt: SRT -> srt.theta },
            rowFld = { srt: SRT -> srt.eta0Factor },
            fld = { srt: SRT -> srt.successPct }
        )

        plotSRS(calculations, "nsamples", true, colf = "%6.3f", rowf = "%6.2f", colTitle = "theta",
            colFld = { srt: SRT -> srt.theta },
            rowFld = { srt: SRT -> srt.eta0Factor },
            fld = { srt: SRT -> srt.nsamples.toDouble() }
        )

        plotSRS(calculations, "pct nsamples", false, colf = "%6.3f", rowf = "%6.2f", colTitle = "theta",
            colFld = { srt: SRT -> srt.theta },
            rowFld = { srt: SRT -> srt.eta0Factor },
            fld = { srt: SRT -> srt.pctSamples }
        )

        plotTFsuccessDecile(calculations, "", sampleMaxPct = 10, colTitle = "theta")
        plotTFsuccessDecile(calculations, "", sampleMaxPct = 20, colTitle = "theta")
        plotTFsuccessDecile(calculations, "", sampleMaxPct = 30, colTitle = "theta")
        plotTFsuccessDecile(calculations, "", sampleMaxPct = 40, colTitle = "theta")
        plotTFsuccessDecile(calculations, "", sampleMaxPct = 50, colTitle = "theta")
        plotTFsuccessDecile(calculations, "", sampleMaxPct = 100, colTitle = "theta")

        // 9/25/24
        //cvrComparisonFailure ntrials=10000, N=10000, d=10000 cvrMeanDiff=-0.005; theta (col) vs etaFactor (row)
        //cvrMean: 0.501,  0.502,  0.503,  0.504,  0.505,  0.506,  0.508,  0.510,  0.520,  0.530,  0.540,
        //successes
        //  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.501,  0.503,  0.505,  0.515,  0.525,  0.535,
        //  1.00,      0,      0,      0,      0,      0,  10000,  10000,  10000,  10000,  10000,  10000,
        //  1.25,      0,      0,      0,      0,     79,  10000,  10000,  10000,  10000,  10000,  10000,
        //  1.50,      0,      0,      0,     19,    421,  10000,  10000,  10000,  10000,  10000,  10000,
        //  1.75,      0,      0,     12,    104,    483,  10000,  10000,  10000,  10000,  10000,  10000,
        //  1.99,      0,      4,     48,    202,    517,  10000,  10000,  10000,  10000,  10000,  10000,
        //
        //successPct
        //  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.501,  0.503,  0.505,  0.515,  0.525,  0.535,
        //  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,
        //  1.25,    0.0,    0.0,    0.0,    0.0,    0.8,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,
        //  1.50,    0.0,    0.0,    0.0,    0.2,    4.2,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,
        //  1.75,    0.0,    0.0,    0.1,    1.0,    4.8,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,
        //  1.99,    0.0,    0.0,    0.5,    2.0,    5.2,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,
        //
        //nsamples
        //  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.501,  0.503,  0.505,  0.515,  0.525,  0.535,
        //  1.00,      0,      0,      0,      0,      0,   9965,   9676,   9127,   5352,   3002,   1846,
        //  1.25,      0,      0,      0,      0,   7649,   8517,   4177,   2531,    776,    442,    303,
        //  1.50,      0,      0,      0,   2978,   4043,   7593,   3049,   1648,    440,    245,    170,
        //  1.75,      0,      0,   1404,   1525,   1735,   8303,   3573,   1759,    353,    187,    125,
        //  1.99,      0,   1500,   1023,    823,    733,   9087,   6382,   3765,    601,    247,    143,
        //
        //pct nsamples
        //  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.501,  0.503,  0.505,  0.515,  0.525,  0.535,
        //  1.00,   0.00,   0.00,   0.00,   0.00,   0.00,  99.65,  96.76,  91.27,  53.53,  30.03,  18.47,
        //  1.25,   0.00,   0.00,   0.00,   0.00,  76.49,  85.18,  41.78,  25.32,   7.77,   4.43,   3.04,
        //  1.50,   0.00,   0.00,   0.00,  29.79,  40.44,  75.94,  30.49,  16.48,   4.41,   2.46,   1.70,
        //  1.75,   0.00,   0.00,  14.05,  15.26,  17.35,  83.04,  35.73,  17.59,   3.53,   1.87,   1.26,
        //  1.99,   0.00,  15.00,  10.23,   8.23,   7.34,  90.88,  63.83,  37.65,   6.02,   2.48,   1.44,
        //
        //% successRLA, for sampleMaxPct=10:
        //  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.501,  0.503,  0.505,  0.515,  0.525,  0.535,
        //  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,
        //  1.25,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,   81.1,  100.0,  100.0,
        //  1.50,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    2.9,   20.4,   98.5,  100.0,  100.0,
        //  1.75,    0.0,    0.0,    0.0,    0.0,    1.6,    3.1,   14.3,   32.2,   97.3,  100.0,  100.0,
        //  1.99,    0.0,    0.0,    0.0,    1.9,    4.6,    7.5,   16.4,   26.3,   76.0,   96.0,   99.8,
        //
        //% successRLA, for sampleMaxPct=20:
        //  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.501,  0.503,  0.505,  0.515,  0.525,  0.535,
        //  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,   77.0,
        //  1.25,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    1.2,   18.9,  100.0,  100.0,  100.0,
        //  1.50,    0.0,    0.0,    0.0,    0.0,    0.7,    4.5,   29.5,   68.0,  100.0,  100.0,  100.0,
        //  1.75,    0.0,    0.0,    0.1,    0.8,    3.5,    9.1,   32.8,   62.7,  100.0,  100.0,  100.0,
        //  1.99,    0.0,    0.0,    0.5,    2.0,    5.1,    8.8,   19.3,   33.3,   93.8,  100.0,  100.0,
        //
        //% successRLA, for sampleMaxPct=30:
        //  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.501,  0.503,  0.505,  0.515,  0.525,  0.535,
        //  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,   19.5,  100.0,
        //  1.25,    0.0,    0.0,    0.0,    0.0,    0.0,    0.1,   15.0,   72.1,  100.0,  100.0,  100.0,
        //  1.50,    0.0,    0.0,    0.0,    0.1,    1.7,    8.7,   51.5,   90.6,  100.0,  100.0,  100.0,
        //  1.75,    0.0,    0.0,    0.1,    1.0,    4.3,   11.5,   44.6,   80.2,  100.0,  100.0,  100.0,
        //  1.99,    0.0,    0.0,    0.5,    2.0,    5.1,    9.0,   20.9,   38.7,   99.6,  100.0,  100.0,
        //
        //% successRLA, for sampleMaxPct=40:
        //  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.501,  0.503,  0.505,  0.515,  0.525,  0.535,
        //  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,  100.0,  100.0,
        //  1.25,    0.0,    0.0,    0.0,    0.0,    0.0,    1.1,   43.1,   96.1,  100.0,  100.0,  100.0,
        //  1.50,    0.0,    0.0,    0.0,    0.2,    2.4,   13.2,   70.6,   98.3,  100.0,  100.0,  100.0,
        //  1.75,    0.0,    0.0,    0.1,    1.0,    4.5,   13.0,   55.3,   91.5,  100.0,  100.0,  100.0,
        //  1.99,    0.0,    0.0,    0.5,    2.0,    5.1,    9.1,   22.4,   45.3,  100.0,  100.0,  100.0,
        //
        //% successRLA, for sampleMaxPct=50:
        //  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.501,  0.503,  0.505,  0.515,  0.525,  0.535,
        //  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.3,  100.0,  100.0,
        //  1.25,    0.0,    0.0,    0.0,    0.0,    0.1,    3.2,   72.1,   99.9,  100.0,  100.0,  100.0,
        //  1.50,    0.0,    0.0,    0.0,    0.2,    3.0,   17.5,   85.2,   99.9,  100.0,  100.0,  100.0,
        //  1.75,    0.0,    0.0,    0.1,    1.0,    4.7,   14.4,   66.3,   97.8,  100.0,  100.0,  100.0,
        //  1.99,    0.0,    0.0,    0.5,    2.0,    5.2,    9.2,   24.2,   56.3,  100.0,  100.0,  100.0,
        //
        //% successRLA, for sampleMaxPct=100:
        //  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.501,  0.503,  0.505,  0.515,  0.525,  0.535,
        //  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,  100.0,  100.0,  100.0,  100.0,  100.0,
        //  1.25,    0.0,    0.0,    0.0,    0.0,    0.7,   99.8,  100.0,  100.0,  100.0,  100.0,  100.0,
        //  1.50,    0.0,    0.0,    0.0,    0.2,    4.2,   99.6,  100.0,  100.0,  100.0,  100.0,  100.0,
        //  1.75,    0.0,    0.0,    0.1,    1.0,    4.8,   89.1,  100.0,  100.0,  100.0,  100.0,  100.0,
        //  1.99,    0.0,    0.0,    0.5,    2.0,    5.2,   20.9,  100.0,  100.0,  100.0,  100.0,  100.0,
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    // common

    fun calculate(task: ComparisonTask, nrepeat: Int, d: Int, cvrMeanDiff: Double): SRT {
        val rr = runComparisonWithMeanDiff(
            task.cvrMean, task.cvrs, cvrMeanDiff = cvrMeanDiff,
            nrepeat = nrepeat, d = d, eta0Factor = task.eta0Factor
        )
        val sr = makeSRT(
            task.N,
            reportedMean = task.cvrMean,
            reportedMeanDiff = cvrMeanDiff,
            d = d,
            eta0Factor = task.eta0Factor,
            rr = rr
        )
        if (showCalculation) println("${task.idx} (${calculations.size}): ${task.N}, ${task.cvrMean}, ${rr.eta0}, $sr")
        if (showCalculationAll) println("${task.idx} (${calculations.size}): $rr")
        return sr
    }

    private fun CoroutineScope.produceTasks(producer: Iterable<ComparisonTask>): ReceiveChannel<ComparisonTask> =
        produce {
            for (task in producer) {
                send(task)
                yield()
            }
            channel.close()
        }

    private var calculations = mutableListOf<SRT>()
    private val mutex = Mutex()

    private fun CoroutineScope.launchCalculations(
        input: ReceiveChannel<ComparisonTask>,
        calculate: (ComparisonTask) -> SRT?,
    ) = launch(Dispatchers.Default) {
        for (task in input) {
            val calculation = calculate(task) // not inside the mutex!!
            if (calculation != null) {
                mutex.withLock {
                    calculations.add(calculation)
                }
            }
            yield()
        }
    }
}

fun runComparisonWithMeanDiff(
    cvrMean: Double,
    cvrs: List<Cvr>,
    cvrMeanDiff: Double,
    nrepeat: Int,
    eta0Factor: Double,
    d: Int = 100,
    silent: Boolean = true
): AlphaMartRepeatedResult {
    val N = cvrs.size
    val theta = cvrMean + cvrMeanDiff // the true mean
    if (!silent) println(" N=${cvrs.size} theta=$theta d=$d diffMean=$cvrMeanDiff")

    /*
    val contest = AuditContest("contest0", 0, listOf(0, 1), listOf(0))
    val compareAudit = makeComparisonAudit(contests = listOf(contest), cvrs = cvrs)
    val compareAssertion = compareAudit.assertions[contest]!!.first()
    val compareAssorter = compareAssertion.assorter
     */
    val compareAssorter = makeStandardComparisonAssorter(cvrMean)

    // fun comparisonAssorterCalc(assortAvgValue:Double, assortUpperBound: Double): Triple<Double, Double, Double> {
    val (_, noerrors, upperBound) = comparisonAssorterCalc(cvrMean, compareAssorter.upperBound)
    val sampleWithErrors = ComparisonWithErrors(cvrs, compareAssorter, theta)

    val compareResult = runAlphaMartRepeated(
        drawSample = sampleWithErrors,
        maxSamples = N,
        eta0 = eta0Factor *  noerrors,
        d = d,
        ntrials = nrepeat,
        withoutReplacement = true,
        upperBound = upperBound
    )
    return compareResult
}
