package org.cryptobiotic.rlauxe.betting

import kotlin.test.Test

// exoplore this function
// log T_i = ln(1.0 + lamda * (noerror - mui)) * p0  + Sum { ln(1.0 + lamda * (assortValue_k - mui)) * p_k; over error type k }
//          + Sum { ln(1.0 + lamda * (assortValue_pk - mui)) * p_pk; over pools and pool types }              (eq 2)

class TestGABetting2 {
    val maxLoss = 1/1.036

    @Test
    fun testStuff() {
        val diff = 27538
        val Npop = 396121
        val Nc = 44675
        val bet = 2/1.03905
        val alpha = .03
        val margin = diff / Nc.toDouble()
        val dmargin = diff / Npop.toDouble()
        val noerror: Double = 1.0 / (2.0 - margin)
        val noerrord: Double = 1.0 / (2.0 - dmargin)

        println("margin=$margin samples=${estSampleSizeStandardBet(Nc, noerror, alpha)}")
        println("dmargin=$dmargin samples=${estSampleSizeStandardBet(Npop, noerrord, alpha)}")


        val audit = PushAudit(
            eta = 0.5,
            noerror = noerror,
            riskLimit=alpha,
            Npop = Nc.toInt(),
            Nphantoms=0,
        )

        repeat(12) { audit.pushAssortValue(noerror) }
        println(audit.testStatistic)
        //repeat(105) { audit.pushAssortValue(0.5) }
        //println(audit.testStatistic)

    }
}


// this has samples pushed to it rather than it pulling samples until done
class PushAudit(val eta: Double, // null mean
                val noerror: Double,
                val upperBound: Double = 1.0,
                val riskLimit: Double = .05,
                val Npop: Int,
                val Nphantoms: Int,
) {
    val endingTestStatistic = 1 / riskLimit

    val bettingFun : GeneralAdaptiveBetting
    val startingTestStatistic: Double = 1.0
    val errorTracker: ClcaErrorTracker

    init {
        errorTracker = ClcaErrorTracker(noerror, upperBound)

        // use the same betting function as the real audit
        bettingFun = GeneralAdaptiveBetting(
            Npop, // population size for this contest
            aprioriErrorRates = ClcaErrorRates(noerror, upperBound, emptyMap()), // apriori rates not counting phantoms; non-null so we always have noerror and upper
            Nphantoms,
            1.0 / 1.03905,
            oaAssortRates=null,
        )
    }
    var status = TestH0Status.InProgress
    var testStatistic = startingTestStatistic // aka T
    var countUsed = 0

    fun pushAssortValue(assortValue: Double): Boolean {
        countUsed++

        val mui = populationMeanIfH0eta(Npop, eta, errorTracker)
        if (mui > upperBound) { // 1  # true mean is certainly less than 1/2
            status = TestH0Status.AcceptNull
            return true
        }
        if (mui < 0.0) { // 5 # true mean certainly greater than 1/2
            status = TestH0Status.SampleSumRejectNull
            return true
        }

        // we may have to make this deterministic to prove monotonic
        val bet = bettingFun.bet(errorTracker)

        val payoff = (1 + bet * (assortValue - mui))
        testStatistic *= payoff
        println("  $countUsed $payoff $testStatistic")


        errorTracker.addSample(assortValue, true)
        return false
    }

}
