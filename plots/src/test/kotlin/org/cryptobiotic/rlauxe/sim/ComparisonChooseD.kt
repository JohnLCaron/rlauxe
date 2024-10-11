package org.cryptobiotic.rlauxe.sim

import org.cryptobiotic.rlauxe.util.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.plots.geometricMean
import org.cryptobiotic.rlauxe.rlaplots.SRT
import org.cryptobiotic.rlauxe.rlaplots.SRTcsvReader
import org.cryptobiotic.rlauxe.rlaplots.SRTcsvWriter
import org.junit.jupiter.api.Test
import kotlin.collections.forEach
import kotlin.math.max

// * use etaFactor=1.8. use large enough N so it doesnt interfere
// * determine NS as a function of cvrMean abd cvrMeanDiff
// * fine tune d as a function of theta; can we improve based on cvrMean?

class ComparisonChooseD {
    val showRaw = false
    val showDetail = false
    val showFalsePositives = false
    val showBest = false

    @Test
    fun generateChooseDF() {
        val ds = listOf(5000)
        val fs = listOf(1.4, 1.5, 1.6, 1.7, 1.8)

        val cvrMeanDiffs = listOf(0.0, -.0005, -.001, -.003, -.005, -.008, -.010, -.015, -.020)
        val cvrMeans = listOf(.501, .502, .503, .504, .505, .506, .508, .51, .52)

        //val cvrMeanDiffs = listOf(0.0, -.0005, -.00075, -.001, -.002, -.003, -.004, -.005, -.008, -.010)
        //val cvrMeans = listOf(.501, .502, .503, .504, .505, .506, .508, .51, .52, .53, .54, .55)

        val N = 50000
        val ntrials = 10000

        val tasks = mutableListOf<ComparisonTask>()
        var taskIdx = 0

        cvrMeans.forEach { cvrMean ->
            cvrMeanDiffs.forEach { cvrMeanDiff ->
                val cvrs = makeCvrsByExactMean(N, cvrMean)
                ds.forEach { d ->
                    fs.forEach { eta0Factor ->
                        tasks.add(
                            ComparisonTask(
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

        val writer = SRTcsvWriter("/home/stormy/temp/sim/dvalues/testChooseDF.csv")

        val runner = ComparisonRunner()
        val results = runner.run(tasks, ntrials)

        writer.writeCalculations(results)
        writer.close()
        println("${results.size} results written to ${writer.filename}")
    }

    // analyse D values from generateChooseDF
    @Test
    fun analyzeChooseD() {
        val reader = SRTcsvReader("/home/stormy/temp/sim/dvalues/testChooseDF.csv")
        val all = reader.readCalculations()
        analyzeChooseD(all, 20)
    }

    fun analyzeChooseD(all: List<SRT>, cutoff: Int) {
        println("\n************************************************************")
        println("chooseDAnalyze at cutoff=$cutoff")
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
                mn.calcBestFandDbyRatio()
                if (showDetail) {
                    val fdsort = mn.fds.sortedBy { it.ratio }.reversed()
                    fdsort.forEach { println("  $it") }
                }
            }
        }

        // now lets find out which fd has the best geometric mean
        val fdsg = mutableMapOf<FD, FDresult>()
        mnMap.keys.forEach { mn ->
            mn.fds.forEach { mmfd ->
                val want = FD(mmfd.d, mmfd.eta0Factor)
                val fdr = fdsg.getOrPut(want) { FDresult(want) }
                if (mmfd.ratio > 0.0) fdr.ratios.add(mmfd.ratio)
                fdr.trackFalsePositive(mmfd.falsePositive())
            }
        }
        fdsg.values.forEach { fdr ->
            fdr.geometricMean = geometricMean(fdr.ratios)
        }
        println("geometricMean")
        val fdrgsorted = fdsg.values.sortedBy { it.geometricMean }.reversed()
        fdrgsorted.forEach { println("  $it") }

        // now lets find out which fd has the best average successPct
        val fda = mutableMapOf<FD, FDaverage>()
        mnMap.keys.forEach { mn ->
            mn.fds.forEach { mmfd ->
                val want = FD(mmfd.d, mmfd.eta0Factor)
                var welford = fda.getOrPut(want) { FDaverage(want) }
                if (mmfd.theta > 0.5) welford.add(mmfd.percentHist)
            }
        }
        println("arithmeticMean")
        val fdasorted = fda.values.sortedBy { it.average() }.reversed()
        fdasorted.forEach { println("  $it") }

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

    /*
    ////////////////////////////////////////////////////////////
    // ((N, cvrMean, cvrMeanDiff, cutoff) x (eta0factor, D)

    // have to be data classes to get the auto equals thing
    data class MND(val N: Int, val cvrMean: Double, val cvrMeanDiff: Double) {
        val theta = cvrMean + cvrMeanDiff
        val fds = mutableListOf<MNFDresult>()
        var best: Double = 0.0
        var maxFalsePositive: Double = 0.0

        override fun toString() =
            "N=$N cvrMean=$cvrMean theta=${dd(theta)} best=${dd(best)} maxFalsePositive=${dd(maxFalsePositive)}"
    }

    class MNFDresult(val d: Int, val eta0Factor: Double, val theta: Double, val percentHist: Double) {
        var ratio: Double = 0.0
        fun falsePositive() = if (theta > 0.5) 0.0 else percentHist
        override fun toString() =
            " ${dd(ratio)} : d=$d f=${dd(eta0Factor)} theta=${dd(theta)} percentHist=${dd(percentHist)}"
    }

    fun makeMNmap(srs: List<SRT>): Map<MND, List<SRT>> {
        val mmap = mutableMapOf<MND, MutableList<SRT>>()
        srs.forEach {
            val key = MND(it.N, it.reportedMean, it.reportedMeanDiff)
            val dmap: MutableList<SRT> = mmap.getOrPut(key) { mutableListOf() }
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

     */
}
