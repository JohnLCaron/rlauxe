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
import org.cryptobiotic.rlauxe.integration.eps
import org.cryptobiotic.rlauxe.integration.runAlphaMartRepeated
import kotlin.collections.first
import kotlin.collections.set
import kotlin.test.Test

// create the raw data for showing plots of comparison with theta != eta0
// these are 4 dimensional: N, theta, d, diffMean
class CreateCvrComparison {
    val showCalculation = false
    val showCalculationAll = false

    data class ComparisonTask(val idx: Int, val N: Int, val cvrMean: Double, val cvrMeanDiff: Double, val eta0Factor: Double, val cvrs: List<Cvr>)

    // recreate TestComparisonsFromAlpha.comparisonNvsTheta, with cvrMeanDiff != 0
    @Test
    fun comparisonNvsTheta() {
        val cvrMeans = listOf(.501, .502, .503, .504, .505, .51, .52, .53, .54, .55, .6, .7)
        val nlist = listOf(50000, 20000, 10000, 5000, 1000)

        val d = 10000
        val ntrials = 10
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
                    tasks.add(ComparisonTask(taskIdx++, N, cvrMean, cvrMeanDiff=cvrMeanDiff, eta0Factor=eta0Factor, cvrs))
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
            println("ballot comparison, ntrials=$ntrials, eta0Factor=$eta0Factor, d = $d, cvrMeanDiff=$cvrMeanDiff, theta(col) vs N(row)")
            colHeader(calculations, "theta", colf = "%6.3f") { it.theta }
            println()

            plotNTsuccessPct(calculations, "plotNTsuccessPct", colTitle = "cvrMean")
            plotNTsamples(calculations, "plotNTsamples", colTitle = "cvrMean")
            plotNTpct(calculations, "plotNTpct", colTitle = "cvrMean")

            /*
               plotSRS(calculations, "successPct", false, colf = "%6.3f", rowf = "%6.0f", ff = "%6.3f", colTitle = "theta",
               colFld = { srt: SRT -> srt.theta },
               rowFld = { srt: SRT -> srt.N.toDouble() },
               fld = { srt: SRT -> srt.successPct }
           )

           plotSRS(calculations, "nsamples", true, colf = "%6.3f", rowf = "%6.0f", colTitle = "theta",
               colFld = { srt: SRT -> srt.theta },
               rowFld = { srt: SRT -> srt.N.toDouble() },
               fld = { srt: SRT -> srt.nsamples.toDouble() }
           )

           plotSRS(calculations, "pct nsamples", false, colf = "%6.3f", rowf = "%6.0f", colTitle = "theta",
               colFld = { srt: SRT -> srt.theta },
               rowFld = { srt: SRT -> srt.N.toDouble() },
               fld = { srt: SRT -> srt.pctSamples }
           )

            */
        }

        println("samplePct ratios across cvrMeanDiff: ntrials=$ntrials, d = $d, cvrMeanDiff=$cvrMeanDiff, theta(col) vs N(row)")
        plotRatio(results)
    }

    @Test
    fun comparisonNSampleHistogram() {
        val cvrMeans = listOf(.51, .52) //, .53, .54, .55, .575, .6, .65, .7)
        val nlist = listOf(10000, 20000) // listOf(50000, 20000, 10000, 5000, 1000)

        val d = 10000
        val ntrials = 100
        val cvrMeanDiff = -.005
        val eta0Factors = listOf(1.5) // 1.0, 1.25, 1.5, 1.75, 2.0 - eps)
        val nthreads = 30
        val results = mutableMapOf<Double, List<SRT>>() // eta0Factor vs list<SRT>

        eta0Factors.forEach { eta0Factor ->
            val tasks = mutableListOf<ComparisonTask>()
            var taskIdx = 0
            cvrMeans.forEach { cvrMean ->
                nlist.forEach { N ->
                    val cvrs = makeCvrsByExactMean(N, cvrMean)
                    tasks.add(ComparisonTask(taskIdx++, N, cvrMean, cvrMeanDiff=cvrMeanDiff, eta0Factor=eta0Factor, cvrs))
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
            println("ballot comparison, ntrials=$ntrials, eta0Factor=$eta0Factor, d = $d, cvrMeanDiff=$cvrMeanDiff, theta(col) vs N(row)")
            println()
            colHeader(calculations, "theta", colf = "%6.3f") { it.theta }

            /*
            fun plotNTfailPct(srs: List<SRT>, title: String) {
    val utitle = "pct failed N (row) vs cvrMean (col): " + title
    plotSRS(srs, utitle, true, ff = "%6.3f",
        colFld = { srt: SRT -> srt.reportedMean },
        rowFld = { srt: SRT -> srt.N.toDouble() },
        fld = { srt: SRT -> srt.failPct }
    )
}

fun plotNTsamples(srs: List<SRT>, title: String) {
    val utitle = "nsamples, N (row) vs cvrMean (col): " + title
    plotSRS(srs, utitle, false, ff = "%6.0f",
        colFld = { srt: SRT -> srt.reportedMean },
        rowFld = { srt: SRT -> srt.N.toDouble() },
        fld = { srt: SRT -> srt.nsamples }
    )
}

fun plotNTpct(srs: List<SRT>, title: String) {
    val utitle = "pct samples, N (row) vs cvrMean (col): " + title
    plotSRS(srs, utitle, false, ff = "%6.1f",
        colFld = { srt: SRT -> srt.reportedMean },
        rowFld = { srt: SRT -> srt.N.toDouble() },
        fld = { srt: SRT -> srt.pctSamples }
    )
}

            plotSRS(calculations, "successPct", false, colf = "%6.3f", rowf = "%6.0f", ff = "%6.3f", colTitle = "theta",
                colFld = { srt: SRT -> srt.theta },
                rowFld = { srt: SRT -> srt.N.toDouble() },
                fld = { srt: SRT -> srt.successPct }
            )

            plotSRS(calculations, "plotSRS", true, colf = "%6.3f", rowf = "%6.0f", colTitle = "theta",
                colFld = { srt: SRT -> srt.theta },
                rowFld = { srt: SRT -> srt.N.toDouble() },
                fld = { srt: SRT -> srt.nsamples.toDouble() }
            )
*/
            plotNTsamples(calculations, "plotNTsamples", colTitle = "cvrMean")

/*
            plotSRS(calculations, "pct nsamples", false, colf = "%6.3f", rowf = "%6.0f", colTitle = "theta",
                colFld = { srt: SRT -> srt.theta },
                rowFld = { srt: SRT -> srt.N.toDouble() },
                fld = { srt: SRT -> srt.pctSamples }
            )

 */
            plotNTpct(calculations, "plotNTpct", colTitle = "cvrMean")
        }

        println("samplePct ratios across cvrMeanDiff: ntrials=$ntrials, d = $d, cvrMeanDiff=$cvrMeanDiff, theta(col) vs N(row)")
        plotRatio(results)
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
            fld = { srt: SRT -> srt.failPct }
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
        val cvrMeans = listOf(.501, .502, .503, .504, .505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        val nlist = listOf(50000, 20000, 10000, 5000, 1000)
        val tasks = mutableListOf<ComparisonTask>()

        var taskIdx = 0
        nlist.forEach { N ->
            cvrMeans.forEach { cvrMean ->
                val cvrs = makeCvrsByExactMean(N, cvrMean)
                tasks.add(ComparisonTask(taskIdx++, N, cvrMean, 0.0, eta0Factor=1.0, cvrs))
            }
        }

        val nthreads = 20
        val nrepeat = 100

        // val cvrMeanDiffs = listOf(0.005, 0.01, 0.02, 0.05, 0.1, 0.2)   // % greater than actual mean
        // val cvrMeanDiffs = listOf(-0.004, -0.01, -0.02,- 0.04, -0.09)   // % less than actual mean
        val cvrMeanDiffs = listOf(0.2, 0.1, 0.05, 0.025, 0.01, 0.005, 0.0, -.005, -.01, -.025, -.05, -0.1, -0.2)
        val dl = listOf(100)

        val writer = SRTwriter("/home/stormy/temp/CvrComparison/Full$nrepeat.csv")
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
            fld = { srt: SRT -> srt.successPct }
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

        calculations = mutableListOf<SRT>()
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
