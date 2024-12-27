package org.cryptobiotic.rlauxe.comparison

import org.cryptobiotic.rlauxe.sampling.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.plots.geometricMean
import org.cryptobiotic.rlauxe.rlaplots.SRT
import org.cryptobiotic.rlauxe.rlaplots.SRTcsvReader
import org.cryptobiotic.rlauxe.rlaplots.SRTcsvWriter
import org.cryptobiotic.rlauxe.sim.RepeatedTaskRunner
import kotlin.test.Test
import kotlin.collections.forEach
import kotlin.math.max

// CANDIDATE FOR REMOVAL
// explore comparison parameters
class ComparisonFull {
    val showRaw = false
    val showDetail = false
    val showFalsePositives = false
    val showBest = false

    @Test
    fun cvrComparisonND() {
        val cvrMeans = listOf(.501, .502, .503, .504, .505, .506, .507, .508, .51, .52, .53, .54)
        val cvrMeanDiffs = listOf(0.2, 0.1, 0.05, 0.025, 0.01, 0.005, 0.0, -.005, -.01, -.025, -.05, -0.1, -0.2)
        val eta0Factors = listOf(1.0, 1.25, 1.5, 1.7, 1.8, 1.9, 1.95)

        val N = 10000
        val d = 10000
        val ntrials = 10000

        val tasks = mutableListOf<AlphaComparisonTask>()
        var taskIdx = 0
        eta0Factors.forEach { eta0Factor ->
            cvrMeans.forEach { cvrMean ->
                val cvrs = makeCvrsByExactMean(N, cvrMean)
                cvrMeanDiffs.forEach { cvrMeanDiff ->
                    tasks.add(AlphaComparisonTask(taskIdx++, N, cvrMean, cvrMeanDiff, eta0Factor, d = d, cvrs = cvrs))
                }
            }
        }

        val writer = SRTcsvWriter("/home/stormy/temp/sim/cvrComparisonND.csv")

        val runner = RepeatedTaskRunner()
        val results = runner.run(tasks, ntrials)

        writer.writeCalculations(results)
        writer.close()
        println("${results.size} results written to ${writer.filename}")
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////
    @Test
    fun cvrComparisonFull() {
        val cvrMeanDiffs = listOf(-.005, -.01, -.05, -.10, -.15, 0.0, .005, .01, .05, .10, .15)
        val cvrMeans = listOf(.501, .502, .503, .504, .505, .506, .508, .51, .52, .53, .54, .55)
        val ns = listOf(5000, 10000, 20000, 50000)
        val ds = listOf(500, 1000, 5000, 10000)
        val eta0Factors = listOf(1.75, 1.8, 1.85, 1.9, 1.95)

        val ntrials = 10000

        val tasks = mutableListOf<AlphaComparisonTask>()
        var taskIdx = 0
        ns.forEach { N ->
            cvrMeans.forEach { cvrMean ->
                cvrMeanDiffs.forEach { cvrMeanDiff ->
                    val cvrs = makeCvrsByExactMean(N, cvrMean)
                    eta0Factors.forEach { eta0Factor ->
                        ds.forEach { d ->
                            tasks.add(
                                AlphaComparisonTask(
                                    taskIdx++,
                                    N,
                                    cvrMean,
                                    cvrMeanDiff,
                                    eta0Factor = eta0Factor,
                                    d = d,
                                    cvrs = cvrs
                                )
                            )
                        }
                    }
                }
            }
        }

        val writer = SRTcsvWriter("/home/stormy/temp/sim/comparisonFull.csv")

        val runner = RepeatedTaskRunner()
        val results = runner.run(tasks, ntrials)

        writer.writeCalculations(results)
        writer.close()
        println("${results.size} results written to ${writer.filename}")
    }

    @Test
    fun cvrComparisonAnalyze() {
        val reader = SRTcsvReader("/home/stormy/temp/sim/comparisonFull.csv")
        val all = reader.readCalculations()
        cvrComparisonAnalyze(all, 10)
        cvrComparisonAnalyze(all, 20)
        cvrComparisonAnalyze(all, 30)
        cvrComparisonAnalyze(all, 40)
    }

    fun cvrComparisonAnalyze(all: List<SRT>, cutoff: Int) {
        println("\n************************************************************")
        println("cvrComparisonAnalyze at cutoff=$cutoff")
        val mnMap: Map<MND, List<SRT>> = makeMNmap(all)
        mnMap.forEach { mn, srts ->
            // construct the MNFDresult, one for each SRT
            srts.forEach { srt ->
                mn.fds.add(MNFDresult(srt.d, srt.eta0Factor, srt.theta, srt.percentHist!!.cumul(cutoff)))
            }

            // find best one across the FD
            var best = 0.0
            var maxFalsePositive = 0.0
            mn.fds.forEach {
                if (it.theta > 0.5) {
                    best = max(best, it.percentHist)
                } else {
                    maxFalsePositive = max(maxFalsePositive, it.percentHist)
                }
            }

            // record the ratio
            if (best > 0.0) {
                mn.fds.forEach {
                    if (it.theta > 0.5) {
                        it.ratio = it.percentHist / best
                    }
                }
            }
            mn.best = best
            mn.maxFalsePositive = maxFalsePositive
        }

        if (showRaw) {
            // for each nm, we know which sd did the best, and the ration of all fds with it
            // lets just print that result out
            mnMap.forEach { mn, srts ->
                println(mn)
                if (showDetail) {
                    val fdsort = mn.fds.sortedBy { it.ratio }.reversed()
                    fdsort.forEach { println("  $it") }
                }
            }
        }

        // now lets find out which fd has the best geometric mean
        val fds = mutableMapOf<FD, FDresult>()
        mnMap.keys.forEach { mn ->
            mn.fds.forEach { mmfd ->
                val want = FD(mmfd.d, mmfd.eta0Factor)
                val fdr = fds.getOrPut(want) { FDresult(want) }
                if (mmfd.ratio > 0.0) fdr.ratios.add(mmfd.ratio)
                fdr.trackFalsePositive(mmfd.falsePositive())
            }
        }
        fds.values.forEach { fdr ->
            fdr.geometricMean = geometricMean(fdr.ratios)
        }
        println("geometricMean")
        val fdrsorted = fds.values.sortedBy { it.geometricMean }.reversed()
        fdrsorted.forEach { println("  $it") }

        if (showFalsePositives) {
            // who has the most false positives?
            println("false positives")
            val falsePos = mnMap.keys.filter { it.maxFalsePositive > 0.0 }.sortedBy { it.maxFalsePositive }.reversed()
            falsePos.forEach { println("  $it") }

            println("false positives > 5%")
            mnMap.keys.filter { it.maxFalsePositive > 5.0 }.forEach {
                println(it)
                it.fds.filter { it.percentHist > 5.0 }.forEach {
                    println("    d=${it.d} f=${dd(it.eta0Factor)} theta=${dd(it.theta)} percentHist=${dd(it.percentHist)}")
                }
            }
        }

        if (showBest) {
            // who has the smallest best?
            println("bests")
            val bests = mnMap.keys.filter { it.theta > 0.5 && it.best < 100.0 }
                .sortedWith(compareBy<MND> { it.best }.thenBy { it.theta })
            bests.forEach { println("  $it") }
        }
    }
}

////////////////////////////////////////////////////////////
// ((N, cvrMean, cvrMeanDiff, cutoff) x (eta0factor, D)

// have to be data classes to get the auto equals thing
data class MND(val N: Int, val cvrMean: Double, val cvrMeanDiff: Double) {
    val theta = cvrMean + cvrMeanDiff
    val fds = mutableListOf<MNFDresult>()
    var best : Double = 0.0
    var maxFalsePositive : Double = 0.0

    override fun toString() = "N=$N cvrMean=$cvrMean theta=${dd(theta)} best=${dd(best)} maxFalsePositive=${dd(maxFalsePositive)}"

    fun calcBestFandDbyRatio() {
        val fs = mutableMapOf<Double, FDresult>()
        val ds = mutableMapOf<Double, FDresult>()
        fds.forEach { mmfd ->
                val want = FD(mmfd.d, mmfd.eta0Factor)
                val fr = fs.getOrPut(mmfd.eta0Factor) { FDresult(want) }
                if (mmfd.ratio > 0.0) fr.ratios.add(mmfd.ratio)
                val dr = ds.getOrPut(mmfd.d.toDouble()) { FDresult(want) }
                if (mmfd.ratio > 0.0) dr.ratios.add(mmfd.ratio)
            }
        fs.values.forEach { fdr ->
            fdr.geometricMean = geometricMean(fdr.ratios)
        }
        ds.values.forEach { fdr ->
            fdr.geometricMean = geometricMean(fdr.ratios)
        }
        val fss = fs.values.sortedBy { it.geometricMean }.reversed()
        val fsBest = fss.first().fd.eta0Factor
        val dss = ds.values.sortedBy { it.geometricMean }.reversed()
        val dsBest = dss.first().fd.d
        println("   best d=${dsBest.toInt()} eta0Factor=${dd(fsBest)}")
    }
}

class MNFDresult(val d: Int, val eta0Factor: Double, val theta: Double, val percentHist: Double) {
    var ratio: Double = 0.0
    fun falsePositive() = if (theta > 0.5) 0.0 else percentHist
    override fun toString() = " ${dd(ratio)} : d=$d f=${dd(eta0Factor)} theta=${dd(theta)} percentHist=${dd(percentHist)}"
}

fun makeMNmap(srs: List<SRT>): Map<MND, List<SRT>> {
    val mmap = mutableMapOf<MND, MutableList<SRT>>()
    srs.forEach {
        val key = MND(it.Nc, it.reportedMean, it.reportedMeanDiff)
        val dmap : MutableList<SRT> = mmap.getOrPut(key) { mutableListOf() }
        dmap.add(it)
    }
    return mmap
}

data class FD(val d: Int, val eta0Factor: Double) {
    override fun toString() = "d=$d eta0Factor=$eta0Factor"
}

data class FDresult(val fd: FD) {
    val ratios = mutableListOf<Double>()
    var geometricMean: Double = 0.0
    var maxFalsePositive: Double = 0.0
    fun trackFalsePositive(falsePositive: Double) {
        maxFalsePositive = max(maxFalsePositive, falsePositive)
    }
    override fun toString() = "$fd geometricMean=${dd(geometricMean)} maxFalsePositive=${dd(maxFalsePositive)}"
}

data class FDaverage(val fd: FD) {
    var runningSum = 0.0
    var count = 0
    fun add(term: Double) {
        runningSum += term
        count++
    }
    fun average() = if (count == 0) 0.0 else runningSum / count

    override fun toString() = "$fd average=${dd(average() )}"
}

fun dd(d: Double) = "%5.3f".format(d)
