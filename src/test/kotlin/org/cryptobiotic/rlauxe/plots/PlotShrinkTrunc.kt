package org.cryptobiotic.rlauxe.plots

import org.cryptobiotic.rlauxe.core.PrevSamples
import org.cryptobiotic.rlauxe.core.Samples
import org.cryptobiotic.rlauxe.core.Welford
import org.cryptobiotic.rlauxe.integration.eps
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.test.Test


// Direct compare TruncShrinkage with output from SHANGRLA TestNonnegMean output
class PlotShrinkTrunc {

    /*
    data class SR(val N: Int, val margin: Double, val eta: Double)

    fun plotSRSnVt(srs: List<SR>, margins: List<Double>, title: String = "") {
        val utitle = "number votes sampled: " + title
        plotSRS(srs, margins, utitle, true) { it.nsamples }
    }

    fun plotSamplePctnVt(srs: List<SR>, margins: List<Double>, title: String = "", isInt:Boolean=true) {
        val utitle = "pct votes sampled: " + title
        plotSRS(srs, margins, utitle, isInt) { it.pct }
    }

    fun plotStddevSnVt(srs: List<SR>, margins: List<Double>, title: String = "") {
        val utitle = "stddev votes sampled: " + title
        plotSRS(srs, margins, utitle, true) { it.stddev }
    }

    fun plotSuccesses(srs: List<SR>, margins: List<Double>, sampleMaxPct: Int, nrepeat: Int, title: String = "") {
        val utitle = "% successRLA, for sampleMaxPct=$sampleMaxPct: " + title
        plotSRS(srs, margins, utitle, true) {
            val cumul = it.hist!!.cumul(sampleMaxPct)
            (100.0 * cumul) / nrepeat
        }
    }

    fun plotSRS(srs: List<SR>, margins: List<Double>, title: String, isInt: Boolean, extract: (SR) -> Double) {
        println()
        println(title)
        print("     N, ")
        val theta = margins.sorted().map { .5 + it * .5 }
        theta.forEach { print("${"%6.3f".format(it)}, ") }
        println()

        val mmap = makeMapFromSRs(srs, extract)

        mmap.forEach { dkey, dmap ->
            print("${"%6d".format(dkey)}, ")
            dmap.toSortedMap().forEach { nkey, fld ->
                if (isInt)
                    print("${"%6d".format(fld.toInt())}, ")
                else
                    print("${"%6.3f".format(fld)}, ")
            }
            println()
        }
    }

    fun makeMapFromSRs(srs: List<SR>, extract: (SR) -> Double): Map<Int, Map<Double, Double>> {
        val mmap = mutableMapOf<Int, MutableMap<Double, Double>>() // N, m -> fld
        srs.forEach {
            val dmap = mmap.getOrPut(it.N) { mutableMapOf() }
            dmap[it.margin] = extract(it)
        }
        return mmap.toSortedMap()
    }

    fun makeSR(N: Int, margin: Double, rr: AlphaMartRepeatedResult): SR {
        val (sampleCountAvg, sampleCountVar, _) = rr.nsamplesNeeded.result()
        val pct = (100.0 * sampleCountAvg / N)
        return SR(N, margin, sampleCountAvg, pct, sqrt(sampleCountVar), rr.hist)
    }

     */

    @Test
    fun testTruncShrinkageResult() {
        val x = listOf(0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 0.0, 1.0, 1.0, 1.0, 1.0)
        val eta0 = x.average()
        println("x= $x")

        var sum = 0.0
        val sumMinus1: List<Double> = x.mapIndexed { idx, it ->
            val summ1 = if (idx == 0) 0.0 else sum
            sum += it
            summ1
        }
        println("xs=$sumMinus1")

        val d = 10
        val t = 0.5
        val c = (eta0 - t) / 2
        val N = x.size
        println("eta0=$eta0 d=$d c=$c")
        println()

        val trunkShrink = TruncShrinkageDebug(
            N = N,
            withoutReplacement = true,
            upperBound = 1.0,
            minsd = 1.0e-6,
            d = d,
            eta0 = eta0,
            c = c
        )

        val assortValues = PrevSamples()
        x.forEach {
            val eta = trunkShrink.eta(assortValues)
            println("eta= $eta")
            assortValues.addSample(it)
        }
    }
}

data class TruncShrinkageResult(val capBelow: Double, val est: Double, val capAbove: Double, val boundedEst: Double) {
    val isCapAbove = if (est > capAbove) "*" else ""
    val isCapBelow = if (est < capBelow) "*" else ""

    override fun toString() = buildString {
        append("${"%5.3f".format(est)} in ")
        append("[${"%5.3f".format(capBelow)}$isCapBelow,")
        append("${"%3.0f".format(capAbove)}$isCapAbove]")
        append(" boundedEst = ${"%5.3f".format(boundedEst)}")
    }
}

// instrumented version of TruncShrinkage in AlphaMart. Keep them synchonized.
class TruncShrinkageDebug(
    val N: Int,
    val withoutReplacement: Boolean = true,
    val upperBound: Double,
    val minsd: Double,
    val eta0: Double,
    val c: Double,
    val d: Int,
    val f: Double = 0.0,
) {
    val capAbove = upperBound * (1 - eps)
    val wterm = d * eta0

    init {
        require(upperBound > 0.0)
        require(eta0 >= 0.5) // ??
        require(c > 0.0)
        require(d >= 0)
    }

    val welford = Welford()

    // estimate population mean from previous samples
    fun eta(prevSamples: Samples): TruncShrinkageResult {
        val lastj = prevSamples.size()
        val dj1 = (d + lastj).toDouble()

        val sampleSum = if (lastj == 0) 0.0 else {
            welford.update(prevSamples.last())
            prevSamples.sum()
        }

        // note stdev not used if f = 0, except in capBelow
        //val (_, variance, _) = welford.result()
        //val stdev = Math.sqrt(variance) // stddev of sample
        //val sdj3 = if (lastj < 2) 1.0 else max(stdev, minsd) // LOOK

        // (2.5.2, eq 14, "truncated shrinkage")
        // weighted = ((d * eta + S) / (d + j - 1) + u * f / sdj) / (1 + f / sdj)
        // val est = ((d * eta0 + sampleSum) / dj1 + upperBound * f / sdj3) / (1 + f / sdj3)
        val est = (d * eta0 + sampleSum) / dj1
        // println("est = $est sampleSum=$sampleSum d=$d eta0=$eta0 dj1=$dj1 lastj = $lastj")

        // Choosing epsi . To allow the estimated winner’s share ηi to approach √ µi as the sample grows
        // (if the sample mean approaches µi or less), we shall take epsi := c/ sqrt(d + i − 1) for a nonnegative constant c,
        // for instance c = (η0 − µ)/2.

        val e_j = c / sqrt(dj1)
        val mean = meanUnderNull(N, 0.5, prevSamples)
        val mean2 = populationMeanIfH0(prevSamples.size(), prevSamples.sum())
        if  (mean != mean2) {
            println("wtf")
        }
        val capBelow = mean + e_j
        println(" meanUnderNull=$mean e_j=$e_j capBelow=$capBelow")

        // The estimate ηi is thus the sample mean, shrunk towards η0 and truncated to the interval [µi + ǫi , 1), where ǫi → 0 as the sample size grows.
        //    return min(capAbove, max(est, capBelow)): capAbove > est > capAbove: u*(1-eps) > est > mu_j+e_j(c,j)

        val boundedEst = min( max(capBelow, est), capAbove)
        return TruncShrinkageResult(capBelow, est, capAbove, boundedEst)
    }

    // whats difference with:
    fun populationMeanIfH0(sampleNum: Int, sampleSumMinusCurrent: Double): Double {
        return if (!withoutReplacement) 0.5 else (N * 0.5 - sampleSumMinusCurrent) / (N - sampleNum + 1)
    }

    fun meanUnderNull(N: Int, t: Double, x: Samples): Double {
        if (!withoutReplacement) return t  // t is always 1/2 ??
        if (x.size() == 0) return t

        val sum = x.sum()
        val m1 = (N * t - sum)
        val m2 = (N - x.size())
        val m3 = m1 / m2
        return m3
    }
}