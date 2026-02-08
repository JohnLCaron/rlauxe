package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.oneaudit.OneAuditAssortValueRates
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import kotlin.random.Random
import kotlin.test.Test

// exoplore this function
// log T_i = ln(1.0 + lamda * (noerror - mui)) * p0  + Sum { ln(1.0 + lamda * (assortValue_k - mui)) * p_k; over error type k } (eq 1)

class ShowGeneralAdaptiveBetting {

    @Test
    fun makeBet() {
        makeBet(N = 1000, margin = .01, upper = 1.0, maxLoss = .9)
    }

    fun makeBet(
        N: Int,
        margin: Double,
        upper: Double,
        maxLoss: Double,
    ) {
        val noerror: Double = 1.0 / (2.0 - margin / upper)

        val assorts = mutableListOf<Double>()

        repeat(10) { assorts.add( 0.0 * noerror) }
        repeat(0) { assorts.add( 0.5 * noerror) }
        repeat(0) { assorts.add( 1.5 * noerror) }
        repeat(0) { assorts.add( 2.0 * noerror) }
        val nnerr = N - assorts.size
        repeat(nnerr ) { assorts.add(noerror) }

        val startingTracker = ClcaErrorTracker(noerror, upper)
        assorts.forEach{ startingTracker.addSample(it) }
        println(startingTracker.measuredClcaErrorCounts().show())

        val betFn = GeneralAdaptiveBetting(N,
            startingErrors = startingTracker.measuredClcaErrorCounts(),
            nphantoms = 2,
            oaAssortRates = null, d=0,  maxLoss = maxLoss, debug=true)

        val emptyTracker = ClcaErrorTracker(noerror, upper)
        val bet = betFn.bet(emptyTracker)
        println("bet = $bet maxLoss = $maxLoss")

        assorts.shuffle(Random)
        findSamplesNeededUsingAssorts(N, margin, upper, 1.8, assorts, show=false)
    }
}

// this is the case where you use the same bet (non adaptive)
// see PlotWithAssortValues
private fun findSamplesNeededUsingAssorts(N:Int, margin: Double, upper: Double, lamda: Double, assorts: List<Double>, show: Boolean = false) {
    val taus = Taus(1.0)

    val noerror: Double = 1.0 / (2.0 - margin / upper) // clca assort value when no error
    val tracker = ClcaErrorTracker(noerror, upper)
    var T: Double = 1.0
    var sample = 0

    println("  ${sfn("idx", 4)}, ${sfn("tau", 3)}, ${sfn("type", 8)}, ${sfn("mj", 6)}, , ${sfn("ttj", 6)}, ${sfn("T", 6)}")
    while (T < 20.0 && sample < assorts.size) {
        val x = assorts[sample]
        val mj = populationMeanIfH0(N = N, withoutReplacement = true, tracker) // uses previous samples
        val ttj = 1.0 + lamda * (x - mj)
        T *= ttj
        sample++
        val name = taus.nameOf(x/noerror)
        val useName = if (name == "noerror") name
        else "*$name"
        println("  ${nfn(sample, 4)}, ${dfn(x/noerror, 3)}, ${sfn(useName,8)}, ${df(mj)}, ${df(ttj)}, ${df(T)}")

        if (show) println("${nfn(tracker.numberOfSamples(), 3)}: ttj=${dfn(ttj, 6)} Tj=${dfn(T, 6)}")

        tracker.addSample(x)
    }
    println("lamda: ${df(lamda)} N=$N, margin=$margin, upper=$upper noerror:${df(noerror)}: stat=$T needed $sample samples out of $N" )
}