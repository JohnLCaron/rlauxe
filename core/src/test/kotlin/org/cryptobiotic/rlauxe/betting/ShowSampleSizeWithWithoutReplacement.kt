package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import kotlin.math.ln
import kotlin.test.Test

// all tests here are with assort values = noerror
class ShowSampleSizeWithWithoutReplacement {

    @Test
    fun showSampleSizeWithWithoutReplacement() {
        showPayoffWithMu(1000, .02, 1.0, bet = 2.0 / 1.03905, .05, show = false)
        showPayoffWithMu(10000, .02, 1.0, bet = 2.0 / 1.03905, .05, show = false)
        showPayoffWithMu(100000, .02, 1.0, bet = 2.0 / 1.03905, .05, show = false)
        showPayoffWithMu(1000000, .02, 1.0, bet = 2.0 / 1.03905, .05, show = false)
        println()
        showPayoffWithMu(1000, .01, 1.0, bet = 2.0 / 1.03905, .05, show = false)
        showPayoffWithMu(10000, .01, 1.0, bet = 2.0 / 1.03905, .05, show = false)
        showPayoffWithMu(100000, .01, 1.0, bet = 2.0 / 1.03905, .05, show = false)
        showPayoffWithMu(1000000, .01, 1.0, bet = 2.0 / 1.03905, .05, show = false)
        println()
        showPayoffWithMu(1000, .005, 1.0, bet = 2.0 / 1.03905, .05, show = false)
        showPayoffWithMu(10000, .005, 1.0, bet = 2.0 / 1.03905, .05, show = false)
        showPayoffWithMu(100000, .005, 1.0, bet = 2.0 / 1.03905, .05, show = false)
        showPayoffWithMu(1000000, .005, 1.0, bet = 2.0 / 1.03905, .05, show = false)
        println()
        showPayoffWithMu(100000, .01, 1.0, bet = 2.0 / 1.03905, .05, show = false)
        showPayoffWithMu(100000, .01, 1.0, bet = 2.0 / 1.03905, .04, show = false)
        showPayoffWithMu(100000, .01, 1.0, bet = 2.0 / 1.03905, .03, show = false)
        showPayoffWithMu(100000, .01, 1.0, bet = 2.0 / 1.03905, .02, show = false)
    }

    fun showPayoffWithMu(N: Int, margin: Double, upper: Double, bet: Double, risk: Double, show: Boolean): Pair<Int, Int> {
        val noerror: Double = 1.0 / (2.0 - margin / upper) // clca assort value when no error
        val tracker = ClcaErrorTracker(noerror, upper)

        if (show) {
            println("N=$N, margin= $margin upper=$upper risk=$risk")
            println("idx,       mj,   payoff, payoff-payoffC,      1/Tj,    1/TjC")
        }

        var nwr = 0 // with replacement
        var nwor = 0  // without replacement
        var Twor = 1.0
        var Twr = 1.0
        for (idx in 0..N) {
            val n = idx+1
            tracker.addSample(noerror)
            val mj = populationMeanIfH0(N = N, true, tracker)
            val payoff = 1.0 + bet * (noerror - mj)
            val payoffWr = 1.0 + bet * (noerror - 0.5)
            Twr = Twr * payoffWr
            Twor = Twor * payoff
            if (show) {
                print("${nfn((idx+1), 3)}, ${dfn(mj, 6)}, ${dfn(payoff, 6)}, ${dfn(payoff - payoffWr, 6)}, ")
                println("          ${dfn(1 / Twr, 4)}, ${dfn(1 / Twor, 4)}")
            }

            if (1/Twor <= risk && nwor == 0) {
                nwor = n
            }

            if (1/Twr <= risk && nwr == 0) {
                nwr = n
            }
        }
        println("wor= $nwor wr=$nwr; N=$N, margin= $margin upper=$upper risk=$risk")
        return Pair(nwor, nwr)
    }
}

//// these are accurate for withoutReplacement, no errors found
fun estSampleSize(N: Int, bet:Double, margin: Double, upper: Double, alpha: Double): Int {
    val noerror = 1.0 / (2.0 - margin / upper) // clca assort value when no error
    val tracker = ClcaErrorTracker(noerror, upper)
    val Tneeded = 1.0 / alpha

    var Twor = 1.0
    for (idx in 0..N) {
        val n = idx + 1
        tracker.addSample(noerror)
        val mj = populationMeanIfH0(N = N, withoutReplacement = true, tracker)
        val payoff = 1.0 + bet * (noerror - mj)
        Twor *= payoff
        if (Twor >= Tneeded) return n
    }
    return 0
}

fun estRisk(N: Int, bet:Double, margin: Double, upper: Double, nsamples: Int): Double {
    val noerror = 1.0 / (2.0 - margin / upper) // clca assort value when no error
    val tracker = ClcaErrorTracker(noerror, upper)

    var Twor = 1.0
    for (idx in 0..nsamples) {
        val n = idx + 1
        tracker.addSample(noerror)
        val mj = populationMeanIfH0(N = N, withoutReplacement = true, tracker)
        val payoff = 1.0 + bet * (noerror - mj)
        Twor *= payoff
    }
    return 1.0/Twor
}


/*
difference between wr amd wor: significant when n gets close to N.
so increases with smaller contests, smaller margins, smaller risk.

wor= 267 wr=310; N=1000, margin= 0.02 upper=1.0 risk=0.05
wor= 305 wr=310; N=10000, margin= 0.02 upper=1.0 risk=0.05
wor= 310 wr=310; N=100000, margin= 0.02 upper=1.0 risk=0.05
wor= 310 wr=310; N=1000000, margin= 0.02 upper=1.0 risk=0.05

wor= 463 wr=621; N=1000, margin= 0.01 upper=1.0 risk=0.05
wor= 603 wr=621; N=10000, margin= 0.01 upper=1.0 risk=0.05
wor= 620 wr=621; N=100000, margin= 0.01 upper=1.0 risk=0.05
wor= 621 wr=621; N=1000000, margin= 0.01 upper=1.0 risk=0.05

wor= 712 wr=0; N=1000, margin= 0.005 upper=1.0 risk=0.05
wor= 1170 wr=1244; N=10000, margin= 0.005 upper=1.0 risk=0.05
wor= 1236 wr=1244; N=100000, margin= 0.005 upper=1.0 risk=0.05
wor= 1243 wr=1244; N=1000000, margin= 0.005 upper=1.0 risk=0.05

wor= 620 wr=621; N=100000, margin= 0.01 upper=1.0 risk=0.05
wor= 665 wr=668; N=100000, margin= 0.01 upper=1.0 risk=0.04
wor= 725 wr=727; N=100000, margin= 0.01 upper=1.0 risk=0.03
wor= 808 wr=811; N=100000, margin= 0.01 upper=1.0 risk=0.02
 */