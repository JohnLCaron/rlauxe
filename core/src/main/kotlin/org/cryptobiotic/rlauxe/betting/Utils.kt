package org.cryptobiotic.rlauxe.betting

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

// note µ = 1/2 here
fun populationMeanIfH0(N: Int, withoutReplacement: Boolean, tracker: Tracker): Double {
    val sampleNum = tracker.numberOfSamples()
    val sum = tracker.sum()
    return if ((sampleNum == 0) || !withoutReplacement) 0.5 else (N * 0.5 - sum) / (N - sampleNum)
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