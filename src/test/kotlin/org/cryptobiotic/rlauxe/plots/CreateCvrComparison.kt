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
import org.cryptobiotic.rlauxe.core.AuditContest
import org.cryptobiotic.rlauxe.core.ComparisonWithErrors
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.comparisonAssorterCalc
import org.cryptobiotic.rlauxe.core.makeComparisonAudit
import org.cryptobiotic.rlauxe.integration.AlphaMartRepeatedResult
import org.cryptobiotic.rlauxe.core.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.core.theta2margin
import org.cryptobiotic.rlauxe.integration.runAlphaMartRepeated
import kotlin.collections.first
import kotlin.test.Test

// create the raw data for showing plots of comparison with theta != eta0
// these are 4 dimensional: N, theta, d, diffMean
class CreateCvrComparison {
    val showCalculation = false
    val showCalculationAll = false

    data class ComparisonTask(val idx: Int, val N: Int, val cvrMean: Double, val cvrMeanDiff: Double, val eta0Factor: Double, val cvrs: List<Cvr>)

    // recreate TestComparisonsFromAlpha.comparisonNvsTheta
    @Test
    fun recreateExact() {
        //val cvrMeans = listOf(.501, .502, .503, .504, .505, .51, .52, .53, .54, .55, .6, .7)
        //val cvrMeanDiffs = listOf(0.2, 0.1, 0.05, 0.02, 0.01, 0.005, 0.0, -.005, -.01, -0.02, -0.05, -0.1, -0.2)

        val thetas = listOf(.501, .502, .503, .504, .505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        val nlist = listOf(50000, 20000, 10000, 5000, 1000)

        val d = 100
        val ntrials = 1000
        val eta0Factor = 2.0

        val tasks = mutableListOf<ComparisonTask>()
        var taskIdx = 0
        thetas.forEach { cvrMean ->
            nlist.forEach { N ->
                val cvrs = makeCvrsByExactMean(N, cvrMean)
                tasks.add(ComparisonTask(taskIdx++, N, cvrMean, cvrMeanDiff=0.0, eta0Factor=eta0Factor, cvrs))
            }
        }

        val nthreads = 30
        val writer = SRTwriter("/home/stormy/temp/CvrComparison/Exact.csv")

        runBlocking {
            val taskProducer = produceTasks(tasks)
            val calcJobs = mutableListOf<Job>()
            repeat(nthreads) {
                calcJobs.add(
                    launchCalculations(taskProducer) { task ->
                        calculate(task, ntrials, d=d, cvrMeanDiff=task.cvrMeanDiff)
                    })
            }

            // wait for all calculations to be done
            joinAll(*calcJobs.toTypedArray())
        }
        writer.writeCalculations(calculations)

        writer.close()
        println("totalCalculations = ${calculations.size}")

        val title = " nsamples, ballot comparison, eta0Factor=$eta0Factor, d = $d, error-free\n theta (col) vs N (row)"
        plotSRS(calculations, title, true, colf = "%6.3f", rowf = "%6.0f",
            colFld = { srt: SRT -> srt.reportedMean },
            rowFld = { srt: SRT -> srt.N.toDouble() },
            fld = { srt: SRT -> srt.nsamples.toDouble() }
        )

        val titlePct = " pct nsamples, ballot comparison, eta0Factor=$eta0Factor, d = $d, error-free\n theta (col) vs N (row)"
        plotSRS(calculations, titlePct, false, colf = "%6.3f", rowf = "%6.0f",
            colFld = { srt: SRT -> srt.reportedMean },
            rowFld = { srt: SRT -> srt.N.toDouble() },
            fld = { srt: SRT -> 100.0 * srt.nsamples / srt.N }
        )

        //// latest
        //  nsamples, ballot comparison, eta0Factor=1.0, d = 100, error-free
        // theta (col) vs N (row)
        //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
        //  1000,      0,      0,    998,    995,    992,    968,    883,    770,    653,    547,    351,    235,    124,     76,
        //  5000,   4992,   4967,   4926,   4870,   4800,   4285,   2999,   2000,   1365,    971,    488,    290,    137,     81,
        // 10000,   9967,   9869,   9709,   9493,   9230,   7498,   4283,   2500,   1581,   1075,    513,    299,    139,     81,
        // 20000,  19868,  19480,  18867,  18070,  17140,  11993,   5450,   2857,   1717,   1136,    526,    303,    140,     82,
        // 50000,  49180,  46871,  43471,  39462,  35279,  18733,   6515,   3124,   1810,   1176,    535,    306,    141,     82,
        //
        // pct nsamples, ballot comparison, eta0Factor=1.0, d = 100, error-free
        // theta (col) vs N (row)
        //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
        //  1000,   0.00,   0.00,  99.80,  99.50,  99.20,  96.80,  88.30,  77.00,  65.30,  54.70,  35.10,  23.50,  12.40,   7.60,
        //  5000,  99.84,  99.34,  98.52,  97.40,  96.00,  85.70,  59.98,  40.00,  27.30,  19.42,   9.76,   5.80,   2.74,   1.62,
        // 10000,  99.67,  98.69,  97.09,  94.93,  92.30,  74.98,  42.83,  25.00,  15.81,  10.75,   5.13,   2.99,   1.39,   0.81,
        // 20000,  99.34,  97.40,  94.34,  90.35,  85.70,  59.97,  27.25,  14.29,   8.59,   5.68,   2.63,   1.52,   0.70,   0.41,
        // 50000,  98.36,  93.74,  86.94,  78.92,  70.56,  37.47,  13.03,   6.25,   3.62,   2.35,   1.07,   0.61,   0.28,   0.16,
        //
        //  nsamples, ballot comparison, eta0Factor=2.0, d = 100, error-free
        // theta (col) vs N (row)
        //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
        //  1000,      0,    998,    992,    980,    955,    656,    259,    145,     98,     74,     45,     32,     20,     14,
        //  5000,   4988,   4930,   4773,   4444,   3902,   1247,    307,    157,    103,     76,     46,     32,     20,     15,
        // 10000,   9951,   9719,   9118,   7965,   6346,   1403,    314,    159,    104,     77,     46,     32,     20,     15,
        // 20000,  19804,  18900,  16738,  13190,   9239,   1497,    318,    160,    105,     77,     46,     32,     20,     15,
        // 50000,  48785,  43631,  33569,  21754,  12716,   1560,    320,    160,    105,     77,     46,     32,     20,     15,
        //
        // pct nsamples, ballot comparison, eta0Factor=2.0, d = 100, error-free
        // theta (col) vs N (row)
        //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
        //  1000,   0.00,  99.80,  99.20,  98.00,  95.50,  65.60,  25.90,  14.50,   9.80,   7.40,   4.50,   3.20,   2.00,   1.40,
        //  5000,  99.76,  98.60,  95.46,  88.88,  78.04,  24.94,   6.14,   3.14,   2.06,   1.52,   0.92,   0.64,   0.40,   0.30,
        // 10000,  99.51,  97.19,  91.18,  79.65,  63.46,  14.03,   3.14,   1.59,   1.04,   0.77,   0.46,   0.32,   0.20,   0.15,
        // 20000,  99.02,  94.50,  83.69,  65.95,  46.20,   7.49,   1.59,   0.80,   0.53,   0.39,   0.23,   0.16,   0.10,   0.08,
        // 50000,  97.57,  87.26,  67.14,  43.51,  25.43,   3.12,   0.64,   0.32,   0.21,   0.15,   0.09,   0.06,   0.04,   0.03,

        //// older
        //  nsamples, ballot comparison, eta0=20.0, d = 100, error-free
        // theta (col) vs N (row)
        //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
        //  1000,    951,    777,    631,    527,    450,    258,    138,     94,     71,     57,     38,     29,     19,     14,
        //  5000,   2253,   1294,    904,    695,    564,    290,    147,     98,     73,     59,     39,     29,     19,     14,
        // 10000,   2588,   1390,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
        // 20000,   2781,   1442,    973,    734,    589,    296,    148,     99,     74,     59,     39,     29,     19,     14,
        // 50000,   2907,   1475,    988,    742,    595,    298,    149,     99,     74,     59,     39,     29,     19,     14,
        //
        // pct nsamples, ballot comparison, eta0=20.0, d = 100, error-free
        // theta (col) vs N (row)
        //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
        //  1000,  95.10,  77.70,  63.10,  52.70,  45.00,  25.80,  13.80,   9.40,   7.10,   5.70,   3.80,   2.90,   1.90,   1.40,
        //  5000,  45.06,  25.88,  18.08,  13.90,  11.28,   5.80,   2.94,   1.96,   1.46,   1.18,   0.78,   0.58,   0.38,   0.28,
        // 10000,  25.88,  13.90,   9.49,   7.21,   5.81,   2.94,   1.48,   0.98,   0.74,   0.59,   0.39,   0.29,   0.19,   0.14,
        // 20000,  13.91,   7.21,   4.87,   3.67,   2.95,   1.48,   0.74,   0.50,   0.37,   0.30,   0.20,   0.15,   0.10,   0.07,
        // 50000,   5.81,   2.95,   1.98,   1.48,   1.19,   0.60,   0.30,   0.20,   0.15,   0.12,   0.08,   0.06,   0.04,   0.03,

        // eta0=1
        //   nsamples, ballot comparison, eta0=1.0, d = 100, error-free
        // theta (col) vs N (row)
        //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
        //  1000,   1000,    998,    992,    979,    955,    656,    259,    145,     98,     74,     45,     32,     20,     14,
        //  5000,   4988,   4930,   4772,   4441,   3898,   1245,    306,    157,    103,     76,     46,     32,     20,     14,
        // 10000,   9951,   9718,   9115,   7957,   6336,   1400,    314,    159,    104,     77,     46,     32,     20,     14,
        // 20000,  19803,  18897,  16726,  13169,   9216,   1494,    317,    160,    104,     77,     46,     32,     20,     14,
        // 50000,  48783,  43612,  33519,  21696,  12674,   1556,    319,    160,    105,     77,     46,     32,     20,     14,
        //
        // pct nsamples, ballot comparison, eta0=1.0, d = 100, error-free
        // theta (col) vs N (row)
        //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
        //  1000, 100.00,  99.80,  99.20,  97.90,  95.50,  65.60,  25.90,  14.50,   9.80,   7.40,   4.50,   3.20,   2.00,   1.40,
        //  5000,  99.76,  98.60,  95.44,  88.82,  77.96,  24.90,   6.12,   3.14,   2.06,   1.52,   0.92,   0.64,   0.40,   0.28,
        // 10000,  99.51,  97.18,  91.15,  79.57,  63.36,  14.00,   3.14,   1.59,   1.04,   0.77,   0.46,   0.32,   0.20,   0.14,
        // 20000,  99.02,  94.49,  83.63,  65.85,  46.08,   7.47,   1.59,   0.80,   0.52,   0.39,   0.23,   0.16,   0.10,   0.07,
        // 50000,  97.57,  87.22,  67.04,  43.39,  25.35,   3.11,   0.64,   0.32,   0.21,   0.15,   0.09,   0.06,   0.04,   0.03,
    }

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

        val writer = SRTwriter("/home/stormy/temp/CvrComparison/SRT$ntrials.csv")
        runBlocking {
            val taskProducer = produceTasks(tasks)
            val calcJobs = mutableListOf<Job>()
            repeat(nthreads) {
                calcJobs.add(
                    launchCalculations(taskProducer) { task ->
                        calculate(task, ntrials, d=d, cvrMeanDiff=task.cvrMeanDiff)
                    })
            }

            // wait for all calculations to be done
            joinAll(*calcJobs.toTypedArray())
        }
        writer.writeCalculations(calculations)
        writer.close()
        println("totalCalculations = ${calculations.size}")

        val titleFail = " failurePct, ballot comparison, cvrMean=$cvrMean, d = $d, error-free\n theta (col) vs eta0Factor (row)"
        plotSRS(calculations, titleFail, true, colf = "%6.3f", rowf = "%6.2f",
            colFld = { srt: SRT -> cvrMean + srt.reportedMeanDiff },
            rowFld = { srt: SRT -> srt.eta0Factor },
            fld = { srt: SRT -> 100.0 * srt.failPct }
        )

        val title = " nsamples, ballot comparison, cvrMean=$cvrMean, d = $d, error-free\n theta (col) vs eta0Factor (row)"
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

    @Test
    fun cvrComparisonFull() {
        val cvrMeans = listOf(.51) // listOf(.501, .502, .503, .504, .505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        val nlist = listOf(10000) // listOf(50000, 20000, 10000, 5000, 1000)
        val tasks = mutableListOf<ComparisonTask>()
        val eta0 = 2.0

        var taskIdx = 0
        nlist.forEach { N ->
            cvrMeans.forEach { cvrMean ->
                val cvrs = makeCvrsByExactMean(N, cvrMean)
                tasks.add(ComparisonTask(taskIdx++, N, cvrMean, 0.0, eta0Factor=eta0, cvrs))
            }
        }

        val nthreads = 20
        val nrepeat = 10

        // val cvrMeanDiffs = listOf(0.005, 0.01, 0.02, 0.05, 0.1, 0.2)   // % greater than actual mean
        // val cvrMeanDiffs = listOf(-0.004, -0.01, -0.02,- 0.04, -0.09)   // % less than actual mean
        val cvrMeanDiffs = listOf(0.2, 0.1, 0.05, 0.025, 0.01, 0.005, 0.0, -.005, -.01, -.025, -.05, -0.1, -0.2)
        val dl = listOf(10, 50, 250, 1250)

        val writer = SRTwriter("/home/stormy/temp/CvrComparison/SRT$nrepeat.csv")
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
                                calculate(task, nrepeat, d=d, cvrMeanDiff=cvrMeanDiff)
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

    @Test
    fun cvrComparisonFailure() {
        val nlist = listOf(50000, 20000, 10000, 5000, 1000)
        // val cvrMeanDiffs = listOf(0.005, 0.01, 0.02, 0.05, 0.1, 0.2)   // % greater than actual mean
        // val cvrMeanDiffs = listOf(-0.004, -0.01, -0.02,- 0.04, -0.09)   // % less than actual mean
        val dl = listOf(100, 1000)

        val cvrMeanDiff = -.005
        val cvrMeans = listOf(.501, .502, .503, .504, .505, .51, .52, .53, .54, .55, .575, .6, .65, .7)

        val eta0Factors = listOf(1.0, 1.25, 1.5, 1.75, 1.99)
        val N = 10000
        val d = 100

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

        val writer = SRTwriter("/home/stormy/temp/CvrComparison/Failures.csv")
        runBlocking {
            val taskProducer = produceTasks(tasks)
            val calcJobs = mutableListOf<Job>()
            repeat(nthreads) {
                calcJobs.add(
                    launchCalculations(taskProducer) { task ->
                        calculate(task, ntrials, d=d, cvrMeanDiff=task.cvrMeanDiff)
                    })
            }

            // wait for all calculations to be done
            joinAll(*calcJobs.toTypedArray())
        }
        writer.writeCalculations(calculations)
        writer.close()
        println("totalCalculations = ${calculations.size}")

        println("Comparison ntrials=$ntrials, N=$N, d=$d cvrMeanDiff=$cvrMeanDiff\n theta (col) vs etaFactor (row)")
        plotSRS(calculations, " successes", true, colf = "%6.3f", rowf = "%6.2f",
            colFld = { srt: SRT -> srt.reportedMean + srt.reportedMeanDiff },
            rowFld = { srt: SRT -> srt.eta0Factor },
            fld = { srt: SRT -> srt.nsuccess.toDouble() }
        )

        plotSRS(calculations, " successPct", false, colf = "%6.3f", rowf = "%6.2f", ff = "%6.1f",
            colFld = { srt: SRT -> srt.reportedMean + srt.reportedMeanDiff },
            rowFld = { srt: SRT -> srt.eta0Factor },
            fld = { srt: SRT -> 100.0 * srt.nsuccess.toDouble() / srt.ntrials }
        )

        plotSRS(calculations, " nsamples", true, colf = "%6.3f", rowf = "%6.2f",
            colFld = { srt: SRT -> srt.reportedMean + srt.reportedMeanDiff },
            rowFld = { srt: SRT -> srt.eta0Factor },
            fld = { srt: SRT -> srt.nsamples.toDouble() }
        )

        val titlePct = " pct nsamples"
        plotSRS(calculations, titlePct, false, colf = "%6.3f", rowf = "%6.2f",
            colFld = { srt: SRT -> srt.reportedMean + srt.reportedMeanDiff },
            rowFld = { srt: SRT -> srt.eta0Factor },
            fld = { srt: SRT -> 100.0 * srt.nsamples / srt.N }
        )
    }

    fun calculate(task: ComparisonTask, nrepeat: Int, d: Int, cvrMeanDiff: Double): SRT {
        val rr = runComparisonWithMeanDiff(task.cvrMean, task.cvrs, cvrMeanDiff=cvrMeanDiff,
            nrepeat = nrepeat, d = d, eta0Factor=task.eta0Factor)
        val sr = makeSRT(task.N, reportedMean=task.cvrMean, reportedMeanDiff=cvrMeanDiff, d=d, eta0Factor=task.eta0Factor, rr=rr)
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
        
        val contest = AuditContest("contest0", 0, listOf(0, 1), listOf(0))
        val compareAudit = makeComparisonAudit(contests = listOf(contest), cvrs = cvrs)
        val compareAssertion = compareAudit.assertions[contest]!!.first()
        val sampleWithErrors = ComparisonWithErrors(cvrs, compareAssertion.assorter, theta)

        // fun comparisonAssorterCalc(assortAvgValue:Double, assortUpperBound: Double): Triple<Double, Double, Double> {
        val (_, noerrors, upperBound) = comparisonAssorterCalc(cvrMean, compareAssertion.assorter.upperBound)

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
}
