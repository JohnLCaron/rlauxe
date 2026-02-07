package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.oneaudit.OneAuditAssortValueRates
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import kotlin.random.Random
import kotlin.test.Test

// exoplore this function
// log T_i = ln(1.0 + lamda * (noerror - mui)) * p0  + Sum { ln(1.0 + lamda * (assortValue_k - mui)) * p_k; over error type k } (eq 1)

class TestGeneralAdaptiveBetting1 {

    @Test
    fun makeBet() {
        makeBet(N = 10000, margin = .01, upper = 1.0, maxLoss = .9)
    }

    fun makeBet(
        N: Int,
        margin: Double,
        upper: Double,
        maxLoss: Double,
        oaErrorRates: OneAuditAssortValueRates? = null
    ) {
        val noerror: Double = 1.0 / (2.0 - margin / upper)

        val assorts = mutableListOf<Double>()
        repeat(1000) { assorts.add(noerror) }

        repeat(1) { assorts.add( 0.0 * noerror) }
        repeat(10) { assorts.add( 0.5 * noerror) }
        repeat(10) { assorts.add( 1.5 * noerror) }
        repeat(1) { assorts.add( 2.0 * noerror) }

        val tracker = ClcaErrorTracker(noerror, upper)
        assorts.forEach{ tracker.addSample(it) }
        println(tracker.measuredClcaErrorCounts().show())

        val betFn = GeneralAdaptiveBetting(N,
            startingErrors = ClcaErrorCounts.empty(noerror, upper),
            nphantoms = 2,
            oaErrorRates, d=0,  maxLoss = maxLoss, debug=true)

        val starting = betFn.estimatedErrorRates()
        println("starting = $starting maxLoss")

        val bet = betFn.bet(tracker)
        println("bet = $bet maxLoss = $maxLoss")

        assorts.shuffle(Random)
        findSamplesNeededUsingAssorts(N, margin, upper, bet, assorts, show=false)
    }
}

// this is the case where ou use the same bet
// see PlotWithAssortValues
fun findSamplesNeededUsingAssorts(N:Int, margin: Double, upper: Double, lamda: Double, assorts: List<Double>, show: Boolean = false) {
    val taus = Taus(1.0)

    val noerror: Double = 1.0 / (2.0 - margin / upper) // clca assort value when no error
    val tracker = ClcaErrorTracker(noerror, upper)
    var T: Double = 1.0
    var sample = 0

    while (T < 20.0 && sample < assorts.size) {
        val x = assorts[sample]
        tracker.addSample(x)
        val mj = populationMeanIfH0(N = N, withoutReplacement = true, tracker)
        val ttj = 1.0 + lamda * (x - mj)
        T *= ttj
        sample++
        val name = taus.nameOf(x/noerror)
        if (name != "noerror") println("  $name $ttj")

        if (show) println("${nfn(tracker.numberOfSamples(), 3)}: ttj=${dfn(ttj, 6)} Tj=${dfn(T, 6)}")
    }
    println("lamda: ${df(lamda)} N=$N, margin=$margin, upper=$upper noerror:${df(noerror)}: stat=$T needed $sample samples" )
}