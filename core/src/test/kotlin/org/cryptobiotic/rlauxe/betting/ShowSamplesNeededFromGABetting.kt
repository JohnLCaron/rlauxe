package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals

// exoplore this function
// log T_i = ln(1.0 + lamda * (noerror - mui)) * p0  + Sum { ln(1.0 + lamda * (assortValue_k - mui)) * p_k; over error type k }
//          + Sum { ln(1.0 + lamda * (assortValue_pk - mui)) * p_pk; over pools and pool types }              (eq 2)

class ShowSamplesNeededFromGABetting {
    val N = 10000
    val margin = .01
    val upper = 1.0
    val maxLoss = .9

    @Test
    fun showSamplesNeeded() {
        makeBet(0)
    }

    fun makeBet(
        nphantoms: Int
    ): List<Int> {
        println("nphantoms = $nphantoms")
        val noerror: Double = 1.0 / (2.0 - margin / upper)

        // data class GeneralAdaptiveBetting2(
        //    val Npop: Int, // population size for this contest
        //    val aprioriCounts: ClcaErrorCounts, // apriori counts not counting phantoms, non-null so we have noerror and upper
        //    val nphantoms: Int, // number of phantoms in the population
        //    val maxLoss: Double, // between 0 and 1; this bounds how close lam can get to 2.0; maxBet = maxLoss / mui
        //
        //    val oaAssortRates: OneAuditAssortValueRates? = null, // non-null for OneAudit
        //    val d: Int = 100,  // trunc weight
        //    val debug: Boolean = false,
        val betFn = GeneralAdaptiveBetting2(
            Npop = N,
            aprioriCounts = ClcaErrorCounts.empty(noerror, upper),
            nphantoms = nphantoms,
            maxLoss = maxLoss,
            oaAssortRates=null,
            d = 0,
            debug=true,
        )

        val betFnOld = GeneralAdaptiveBetting(
            N,
            startingErrors = ClcaErrorCounts.empty(noerror, upper),
            nphantoms = nphantoms,
            oaAssortRates = null, d = 0, maxLoss = maxLoss, debug = false
        )

        val tracker = ClcaErrorTracker(noerror, upper)
        repeat(1000) { tracker.addSample(noerror) }

        repeat(1) { tracker.addSample(0.0 * noerror) }
        repeat(10) { tracker.addSample(0.5 * noerror) }
        repeat(10) { tracker.addSample(1.5 * noerror) }
        repeat(1) { tracker.addSample(2.0 * noerror) }
        println("errorCounts = ${tracker.measuredClcaErrorCounts().show()}")
        assertEquals(listOf(1, 10, 10, 1), tracker.measuredClcaErrorCounts().errorCounts().map { it.value })

        // get optimal bet
        val bet = betFn.bet(tracker)
        println("optimal bet = $bet")

        val mui = populationMeanIfH0(N, withoutReplacement = true, tracker = tracker)
        expectedValueLogt(bet, mui, tracker.measuredClcaErrorCounts())
        println()

        return listOf(
            findSamplesNeededUsingLambda(N, margin, upper, bet,),
            findSamplesNeededUsingLambda(N, margin, upper, 1.8,),
            findSamplesNeededUsingLambda(N, margin, upper, 2.0,),
        )
    }
}

fun findSamplesNeededUsingLambda(N:Int, margin: Double, upper: Double, lamda: Double, show: Boolean = false): Int {
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
    return sample
}

fun expectedValueLogt(lam: Double, mui: Double, errs: ClcaErrorCounts): Double {
    val rates = errs.errorCounts.map { (sampleValue, count) ->
        Pair(sampleValue, count / errs.totalSamples.toDouble())
    }.toMap()

    val p0 = 1.0 - rates.map{ it.value }.sum() // - oasum  // calculate against other values to get it exact
    val noerror = errs.noerror
    val noerrorTerm = ln(1.0 + lam * (noerror - mui)) * p0

    var sumClcaTerm = 0.0
    println("expectedValueLogt terms at lamda=$lam")
    println("    sampleValue, mui, (sampleValue - mui), rate, term")
    rates.forEach { (sampleValue: Double, rate: Double) ->
        val term = ln(1.0 + lam * (sampleValue - mui)) * rate
        println("    $sampleValue, $mui, ${(sampleValue - mui)}, $rate, $term")
        sumClcaTerm += ln(1.0 + lam * (sampleValue - mui)) * rate
    }

    val total = noerrorTerm + sumClcaTerm

    println("noerrorTerm=${df(noerrorTerm)} sumClcaTerm=${df(sumClcaTerm)} " +
           "expectedValueLogt=${total} ")

    return total
}

