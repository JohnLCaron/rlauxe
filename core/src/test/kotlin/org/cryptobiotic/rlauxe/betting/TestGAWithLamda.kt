package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.oneaudit.OneAuditAssortValueRates
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.ln
import kotlin.test.Test

// exoplore this function
// log T_i = ln(1.0 + lamda * (noerror - mui)) * p0  + Sum { ln(1.0 + lamda * (assortValue_k - mui)) * p_k; over error type k }
//          + Sum { ln(1.0 + lamda * (assortValue_pk - mui)) * p_pk; over pools and pool types }              (eq 2)

class TestGAWithLamda {

    @Test
    fun showMarginsAtRisk() {
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

        val betFn = GeneralAdaptiveBetting(N,
            startingErrors = ClcaErrorCounts.empty(noerror, upper),
            nphantoms = 0,
            oaErrorRates, d=0,  maxLoss = maxLoss, debug=false)

        val tracker = ClcaErrorTracker(noerror, upper)
        repeat(1000) { tracker.addSample(noerror) }

        val tauPlurality = Taus(1.0)
        println(tauPlurality.names)
        //tauPlurality.values().forEach { tau ->
        //    repeat(10) { tracker.addSample( tau * noerror) }
        //}
        repeat(1) { tracker.addSample( 0.0 * noerror) }
        repeat(10) { tracker.addSample( 0.5 * noerror) }
        repeat(10) { tracker.addSample( 1.5 * noerror) }
        repeat(1) { tracker.addSample( 2.0 * noerror) }
        println(tracker.measuredClcaErrorCounts().show())

        val bet = betFn.bet(tracker)
        println("bet = $bet maxLoss = $maxLoss")

        val mui = populationMeanIfH0(N, withoutReplacement=true, tracker = tracker)
        val expect = expectedValueLogt(bet, mui, tracker.measuredClcaErrorCounts())
        println("expectedValueLogt = $expect")

        findSamplesNeededUsingLambda(N, margin, upper, bet, mui, show=false)
        findSamplesNeededUsingLambda(N, margin, upper, 1.8, mui, show=false)
        findSamplesNeededUsingLambda(N, margin, upper, 2.0, mui, show=false)
    }
}

fun findSamplesNeededUsingLambda(N:Int, margin: Double, upper: Double, lamda: Double, mui: Double = 0.5, show: Boolean = false) {
    val noerror: Double = 1.0 / (2.0 - margin / upper) // clca assort value when no error
    val tracker = ClcaErrorTracker(noerror, upper)
    var T: Double = 1.0
    var sample = 0

    while (T < 20.0) {
        tracker.addSample(noerror)
        val mj = populationMeanIfH0(N = N, withoutReplacement = true, tracker)
        val ttj = 1.0 + lamda * (noerror - mj)
        T *= ttj
        sample++
        if (show) println("${nfn(tracker.numberOfSamples(), 3)}: ttj=${dfn(ttj, 6)} Tj=${dfn(T, 6)}")
    }
    println("lamda: ${df(lamda)} N=$N, margin=$margin, upper=$upper noerror:${df(noerror)}: needed $sample samples" )
}

fun expectedValueLogt(lam: Double, mui: Double, errs: ClcaErrorCounts): Double {
    val rates = errs.errorCounts.map { (sampleValue, count) ->
        Pair(sampleValue, count / errs.totalSamples.toDouble())
    }.toMap()

    val p0 = 1.0 - rates.map{ it.value }.sum() // - oasum  // calculate against other values to get it exact
    val noerror = errs.noerror
    val noerrorTerm = ln(1.0 + lam * (noerror - mui)) * p0

    var sumClcaTerm = 0.0
    rates.forEach { (sampleValue: Double, rate: Double) ->
        val term = ln(1.0 + lam * (sampleValue - mui)) * rate
        println("    $sampleValue, $mui, ${(sampleValue - mui)}, $rate, $term")
        sumClcaTerm += ln(1.0 + lam * (sampleValue - mui)) * rate
    }

    var sumOneAuditTerm = 0.0
    /* if (oaErrorRates != null) {
        oaErrorRates.filter { it.value != 0.0 }.forEach { (sampleValue: Double, rate: Double) ->
            sumOneAuditTerm += ln(1.0 + lam * (sampleValue - mui)) * rate
        }
    } */
    val total = noerrorTerm + sumClcaTerm + sumOneAuditTerm

    println("  lam=$lam, noerrorTerm=${df(noerrorTerm)} sumClcaTerm=${df(sumClcaTerm)} " +
            "sumOneAuditTerm=${df(sumOneAuditTerm)} expectedValueLogt=${total} ")

    return total
}

