# AlphaMart risk function for Polling Audits
last updated Nov 18, 2025

These are early results from implementing AlphaMart, somewhat dated.

<!-- TOC -->
* [AlphaMart risk function for Polling Audits](#alphamart-risk-function-for-polling-audits)
  * [AlphaMart](#alphamart)
  * [BRAVO testing statistic](#bravo-testing-statistic)
  * [Sampling with or without replacement](#sampling-with-or-without-replacement)
  * [Truncated shrinkage estimate of the population mean](#truncated-shrinkage-estimate-of-the-population-mean)
  * [Using BettingMart to implement AlphaMart](#using-bettingmart-to-implement-alphamart)
  * [Polling Simulations](#polling-simulations)
    * [compare table 3 of ALPHA paper with our Polling Audit with replacement](#compare-table-3-of-alpha-paper-with-our-polling-audit-with-replacement)
    * [how to set the parameter d?](#how-to-set-the-parameter-d)
<!-- TOC -->

````
AlphaMart (aka ALPHA) is a risk-measuring function that adapts to the drawn sample as it is made.
It estimates the reported winner’s share of the vote before the jth card is drawn from the j-1 cards already in the sample.
The estimator can be any measurable function of the first j − 1 draws, for example a simple truncated shrinkage estimate, described below.
ALPHA generalizes BRAVO to situations where the population {xj} is not necessarily binary, but merely nonnegative and bounded.
ALPHA works for sampling with or without replacement, with or without weights, while BRAVO is specifically for IID sampling with replacement.
````
(paraphrased from the [ALPHA paper](http://arxiv.org/abs/2201.02707v9))

## AlphaMart

````
θ 	        true population mean
Xk 	        the kth random sample drawn from the population.
X^j         (X1 , . . . , Xj) is the jth sequence of samples.

µj          E(Xj | X^j−1 ) computed under the null hypothesis that θ = 1/2. 
            "expected value of the next sample's assorted value (Xj) under the null hypothosis".
            With replacement, its 1/2.
            Without replacement, its the value that moves the mean to 1/2.

η0          an estimate of the true mean before sampling .
ηj          an estimate of the true mean, using X^j-1 (not using Xj), 
            estimate of what the sampled mean of X^j is (not using Xj) ??
            This is the "estimator function". 

Let ηj = ηj (X^j−1 ), j = 1, . . ., be a "predictable sequence": ηj may depend on X^j−1, but not on Xk for k ≥ j.

Tj          ALPHA nonnegative supermartingale (Tj)_j∈N  starting at 1

	E(Tj | X^j-1 ) = Tj-1, under the null hypothesis that θj = µj (7)

	E(Tj | X^j-1 ) < Tj-1, if θ < µ (8)

	P{∃j : Tj ≥ α−1 } ≤ α, if θ < µ (9) (follows from Ville's inequality)
````

## BRAVO testing statistic

BRAVO is based on Wald’s sequential probability ratio test (SPRT) of the simple hypothesis θ = µ against
a simple alternative θ = η from IID Bernoulli(θ) observations.

ALPHA is a simple adaptive extension of BRAVO.
It is motivated by the SPRT for the Bernoulli and its optimality when the simple alternative is true.

BRAVO is ALPHA with the following restrictions:
* the sample is drawn with replacement from ballot cards that do have a valid vote for the reported winner w
  or the reported loser ℓ (ballot cards with votes for other candidates or non-votes are ignored).
* ballot cards are encoded as 0 or 1, depending on whether they have a valid vote for the
  reported winner or for the reported loser; u = 1 and the only possible values of xi are 0 and 1.
* µ = 1/2, and µi = 1/2 for all i since the sample is drawn with replacement.
* ηi = η0 := Nw /(Nw + Nℓ ), where Nw is the number of votes reported for candidate w and
  Nℓ is the number of votes reported for candidate ℓ: ηi is not updated as data are collected.


## Sampling with or without replacement

We need E(Xj | X^j−1 ) computed with the null hypothosis that θ == µ == 1/2.

Sampling with replacement means that this value is always µ == 1/2.

For sampling without replacement from a population with mean µ, after draw j - 1, the mean of
the remaining numbers is

      (N * µ − Sum(X^j-1)/(N − j + 1).

If this ever becomes less than zero, the null hypothesis is certainly false.
When allowed to sample all N values without replacement, eventually this value becomes less than zero.


## Truncated shrinkage estimate of the population mean

The only settable parameter for the TruncShrink funcition function is d, which is the weighting between the initial guess
at the population mean (eta0) and the running mean of the sampled data:

    estTheta_i = (d*eta0 + sampleSum_i) / (d + sampleSize_i)

This trades off smaller sample sizes when theta = eta0 (large d) vs quickly adapting to when theta < eta0 (smaller d).

See section 2.5.2 of ALPHA for a function with parameters eta0, c and d.

See SHANGRLA shrink_trunc() in NonnegMean.py for an updated version with additional parameter f.

````
sample mean is shrunk towards eta, with relative weight d compared to a single observation,
then that combination is shrunk towards u, with relative weight f/(stdev(x)).

The result is truncated above at u*(1-eps) and below at m_j+etaj(c,j)
Shrinking towards eta stabilizes the sample mean as an estimate of the population mean.
Shrinking towards u takes advantage of low-variance samples to grow the test statistic more rapidly.

// Choosing epsi . To allow the estimated winner’s share ηi to approach √ µi as the sample grows
// (if the sample mean approaches µi or less), we shall take epsi := c/sqrt(d + i − 1) for a nonnegative constant c,
// for instance c = (η0 − µ)/2.
// The estimate ηi is thus the sample mean, shrunk towards η0 and truncated to the interval [µi + ǫi , 1), where ǫi → 0 as the sample size grows.

val weighted = ((d * eta0 + sampleSum) / (d + lastj - 1) + u * f / sdj3) / (1 + f / sdj3)
val npmax = max( weighted, mean2 + c / sqrt((d + lastj - 1).toDouble()))  // 2.5.2 "choosing ǫi"
return min(u * (1 - eps), npmax)
````

## Using BettingMart to implement AlphaMart

To use BettingMart rather than AlphaMart, we just have to set

    λ_i = (estTheta_i/µ_i − 1) / (upper − µ_i)

where upper is the upper bound of the assorter (1 for plurality, 1/(2f) for supermajority), and µ_i := E(Xi | Xi−1) as above.

## Polling Simulations

See the plots here: [9/4/24 plots](https://docs.google.com/spreadsheets/d/1bw23WFTB4F0xEP2-TFEu293wKvBdh802juC7CeRjp-g/edit?gid=662624429#gid=662624429)

* RLA cant help for close elections unless N is large
* seems like this is because sample size is approx. independent of N (Plot 1)
* sample size approx 1/margin as N -> inf. Something more complicated as margin -> 0. (Plot 2b)
* variance is quite large (Plot 4)

### compare table 3 of ALPHA paper with our Polling Audit with replacement

* eta0 = theta (no divergence of sample from true).
* 1000 repetitions

nvotes sampled vs theta = winning percent

| N     | 0.510    | 0.520    | 0.530    | 0.540 | 0.550 | 0.575 | 0.600 | 0.650 | 0.7 |
|-------|----------|----------|----------|-------|-------|-------|-------|-------|-----|
| 1000  |      897 |      726 |      569 | 446   | 340   | 201   | 128   | 60    | 36  |
| 5000  |     3447 |     1948 |     1223 | 799   | 527   | 256   | 145   | 68    | 38  |
| 10000 |     5665 |     2737 |     1430 | 871   | 549   | 266   | 152   | 68    | 38  |
| 20000 |     8456 |     3306 |     1546 | 926   | 590   | 261   | 154   | 65    | 38  |
| 50000 |    12225 |     3688 |     1686 | 994   | 617   | 263   | 155   | 67    | 37  |


stddev samples vs theta

| N      | 0.510    | 0.520    | 0.530    | 0.540   | 0.550   | 0.575   | 0.600   | 0.650  | 0.700  |
|--------|----------|----------|----------|---------|---------|---------|---------|--------|--------|
| 1000   | 119.444  | 176.939  | 195.837  | 176.534 | 153.460 | 110.204 | 78.946  | 40.537 | 24.501 |  
| 5000   | 1008.455 | 893.249  | 669.987  | 478.499 | 347.139 | 176.844 | 101.661 | 52.668 | 28.712 |  
| 10000  | 2056.201 | 1425.911 | 891.215  | 583.694 | 381.797 | 199.165 | 113.188 | 52.029 | 27.933 |  
| 20000  | 3751.976 | 2124.064 | 1051.194 | 656.632 | 449.989 | 190.791 | 123.333 | 47.084 | 28.173 |  
| 50000  | 6873.319 | 2708.147 | 1274.291 | 740.712 | 475.265 | 194.538 | 130.865 | 51.086 | 26.439 |

* no use for the parameter d in this case. Likely useful only for when eta0 != theta
* See PlotSampleSizes.kt

### how to set the parameter d?

From ALPHA (p 9)

````
Choosing d. As d → ∞, the sample size for ALPHA approaches that of BRAVO, for
binary data. The larger d is, the more strongly anchored the estimate is to the reported vote
shares, and the smaller the penalty ALPHA pays when the reported results are exactly correct.
Using a small value of d is particularly helpful when the true population mean is far from the
reported results. The smaller d is, the faster the method adapts to the true population mean,
but the higher the variance is. Whatever d is, the relative weight of the reported vote shares
decreases as the sample size increases.
````

A few representative plots are at
[meanDiff plots](https://docs.google.com/spreadsheets/d/1bw23WFTB4F0xEP2-TFEu293wKvBdh802juC7CeRjp-g/edit?gid=1185506629#gid=1185506629)

Notes:

* samples size when reported mean != theta (true mean)
* show tables of mean difference = (reported mean - theta) columns vs values of d parameter (rows)
* ntrials = 1000
* For many values of N and theta, we cant help (margin too small; N too small); or it doesn't matter much (margin large, N large).
* Ive chosen a few plots where values of N and theta have pct samples 10 - 30%, since thats where improvements might matter for having a successful RLA vs a full hand recount.
* High values of d work well when reported mean ~= theta.
* Low values of d work better as mean difference = (reported mean - theta) grows.
* When the true mean < reported mean, high d may force a full hand count unnecessarily.
* Tentatively, we will use d = 100 as default, and allow the user to override.
