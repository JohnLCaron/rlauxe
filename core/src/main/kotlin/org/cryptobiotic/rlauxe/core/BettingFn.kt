package org.cryptobiotic.rlauxe.core

/**
 * Interface for defining a betting function.
 *
 * Choose the amount to bet (aka lambda) for a given sample number and associated sample value.
 * "λi can be a predictable function of the data X1 , . . . , Xi−1" COBRA section 4.2
 *  The bet must only use the previous samples.
 */
interface BettingFn {
    fun bet(prevSamples: SampleTracker): Double
}

// SmithRamdas eq 33, ALPHA section 2.2.1
// NonnegMean line 173
// m = ( (N * t - S) / (N - j + 1) # mean of population after (j-1)st draw, if null is true (t=eta is the mean)
//   where t= 1/2, j-1 = sample number, N = population size, S = sum of samples 1..j
fun populationMeanIfH0(N: Int, withoutReplacement: Boolean, sampleTracker: SampleTracker): Double {
    val sampleNum = sampleTracker.numberOfSamples()
    return if ((sampleNum == 0) || !withoutReplacement) 0.5 else (N * 0.5 - sampleTracker.sum()) / (N - sampleNum)
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