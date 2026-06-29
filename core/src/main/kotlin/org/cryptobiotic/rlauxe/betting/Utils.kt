package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.util.noerror
import kotlin.math.exp
import kotlin.math.ln

val stdBet = 2.0 / 1.03905

// SmithRamdas eq 33
//   m_t(WOR) = N * µ - Sum {Xi, i=1..t-1 } / (N - (t - 1))
// Notice that constructing a WoR test martingale only relies on changing the fixed conditional mean µ
// to the time-varying conditional mean µ_t(WoR)
//
// ALPHA section 2.2.1
// To use ALPHA with a sample drawn without replacment, we need E(Xj |X j−1 ) computed on the assumption that
//    θ := Sum {Xi, i=1..N} = µ
// For sampling without replacement from a population with mean µ, after draw j - 1, the mean of the remaining numbers is
//   (N * µ − Sum {Xi, i=1..j-1 }) / (N - j  + 1)

// withoutReplacement is always 0.5
// note eta = 1/2 here
fun populationMeanIfH0(N: Int, withoutReplacement: Boolean, tracker: Tracker): Double {
    val sampleNum = tracker.numberOfSamples()
    val sum = tracker.sum()
    return if ((sampleNum == 0) || !withoutReplacement) 0.5 else (N * 0.5 - sum) / (N - sampleNum)
}

// Consider a single stratum with true mean µ* and null mean eta. N is the number of samples in the population.
// eta is the null hypothesis, that avg sample assort values <= eta
// The sample assort values are (X_t), t = 1..N.
// The "conditional stratumwise null mean" =  eta_t is the mean of the values remaining in X at time t if the null is true.
// without replacement: eta_t = (N * eta - Sum { X_i for 1 = 1..t-1 } / (N - (t-1))
//
fun populationMeanIfH0eta(N: Int, eta: Double, tracker: Tracker): Double {
    val sampleNum = tracker.numberOfSamples() // t-1  (sample_i has not been put into tracker)
    val sum = tracker.sum() // Sum { X_i for 1 = 1..t-1 }
    return if (sampleNum == 0) eta else (N * eta - sum) / (N - sampleNum)
}

// formulation not using a tracker.
// Npop size of population
// sampleNum is the current sample number, starting at 0
// eta is the null hypothosis mean, ie the mean of the population is <= eta. usually 0.5 unless dealing with strata
// sumOfPrevSamples is the sum of previous samples
fun populationMeanIfH0(Npop: Int, eta: Double, sampleNum: Int, sumOfPrevSamples: Double): Double {
    return if (sampleNum == 0) 0.5 else (Npop * eta - sumOfPrevSamples) / (Npop - sampleNum)
}

/*
    Alpha eq 12. Choosing λi is equivalent to choosing ηi :
           λi = (ηi /µi − 1) / (u − µi )
        ⇐⇒ ηi = µi (1 + λi (u − µi ))
    As ηi ranges from µi to u, λi ranges continuously from 0 to 1/µi , the same range of values of λi permitted in
    Waudby-Smith and Ramdas (2021): selecting λi is equivalent to selecting a method for estimating θi.
    The difference is only in how λi is chosen. However, see section 4 for a generalization to allow sampling weights
    and to allow u to vary by draw.
 */

fun lamToEta(lam: Double, mu: Double, upper: Double): Double {
    return mu * (1 + lam * (upper - mu))
}

fun etaToLam(eta: Double, mu: Double, upper: Double): Double {
    return (eta / mu - 1) / (upper - mu)
}

//// these are accurate for withoutReplacement, no errors found, eta = 0.5
fun estSampleSizeStandardBet(Npop: Int, noerror: Double, alpha: Double): Int {
    return estSampleSize(Npop, 2.0 / 1.03905, noerror, alpha)
}

// for viewer
fun estSampleSizeStandardBet(voteDiff: Int, Npop: Int, upper: Double, alpha: Double ): Int {
    val margin = voteDiff / Npop.toDouble()
    val noerror = noerror(margin, upper)
    return estSampleSize(Npop, 2.0 / 1.03905, noerror, alpha)
}

fun estSampleSize(Npop: Int, bet:Double, noerror: Double, alpha: Double): Int {
    val tracker = TrackerImpl()
    val Tneeded = 1.0 / alpha

    var Twor = 1.0
    for (idx in 0..Npop) {
        val n = idx + 1
        tracker.update(noerror)
        val mj = populationMeanIfH0(N = Npop, withoutReplacement = true, tracker)
        val payoff = 1.0 + bet * (noerror - mj)
        Twor *= payoff
        if (Twor >= Tneeded) return n
    }
    return 0
}

fun estRiskStandardBet(Npop: Int, noerror: Double, nsamples: Int): Double {
    return estRisk(Npop, 2.0 / 1.03905, noerror, nsamples)
}

// for viewer
fun estRiskStandardBet(voteDiff: Int, Npop: Int, upper: Double, nsamples: Int, ): Double {
    val margin = voteDiff / Npop.toDouble()
    val noerror = noerror(margin, upper)
    return estRisk(Npop, 2.0 / 1.03905, noerror, nsamples)
}

fun estRisk(Npop: Int, bet:Double, noerror: Double, nsamples: Int): Double {
    val tracker = TrackerImpl()
    var Twor = 1.0
    for (idx in 0..nsamples) {
        val n = idx + 1
        tracker.update(noerror)
        val mj = populationMeanIfH0(N = Npop, withoutReplacement = true, tracker)
        val payoff = 1.0 + bet * (noerror - mj)
        Twor *= payoff
    }
    return 1.0/Twor
}

// WR approximation
fun payoff(bet:Double, noerror:Double,) = 1.0 + bet * (noerror - 0.5)

///////////////////////////////////////
// work backwards, if you have nsamples, whats the largest margin satisfying the risk limit?
// assumes no errors and a constant payout
// return margin/upper
fun estMarginUpperFromSamples(bet:Double, nsamples:Int, alpha: Double): Double {
    // payoff^n = 1/alpha
    // ln(payoff) * n = -ln(alpha)
    // ln(1.0 + bet * nomargin / 2) = -ln(alpha) / n
    // ln(1.0 + bet * nomargin / 2) = -ln(alpha) / n
    // 1.0 + bet * nomargin / 2 = e^(-ln(alpha) / n)
    // 1.0 + bet * (noerror - 1/2) = e^(-ln(alpha) / n)

    // let term = e^(-ln(alpha) / n)
    // 1.0 + bet * (noerror - 1/2) = term
    // (noerror - 1/2) = (term - 1)/bet
    // noerror = (term - 1)/bet + 1/2
    // substitute noerror = 1/(2 - marginUpper)

    // 1/(2 - marginUpper) = (term - 1)/bet + 1/2
    // 1/(2 - marginUpper) = 2(term - 1)/2bet + bet/2bet
    // 1/(2 - marginUpper) = (2term - 2 + bet)/2bet
    // (2 - marginUpper) = 2bet/(2term - 2 + bet)
    // marginUpper = 2 - 2bet/(2term - 2 + bet)

    val term = exp(-ln(alpha) / nsamples)
    val den = (2.0*term - 2.0 + bet)
    return 2.0 - 2.0 * bet / den
}