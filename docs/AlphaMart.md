
## ALPHA testing statistic

ALPHA is a risk-measuring function that adapts to the drawn sample as it is made.
ALPHA estimates the reported winner’s share of the vote before the jth card is drawn from the j-1 cards already in the sample.
The estimator can be any measurable function of the first j − 1 draws, for example a simple truncated shrinkage estimate, described below.
ALPHA generalizes BRAVO to situations where the population {xj} is not necessarily binary, but merely nonnegative and bounded.
ALPHA works for sampling with or without replacement, with or without weights, while BRAVO is specifically for IID sampling with replacement.

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

### BRAVO testing statistic

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


### AlphaMart formula as generalization of Wald SPRT:

Bravo: Probability of drawing y if theta=n over probability of drawing y if theta=m:

    y*(n/m) + (1-y)*(1-n)/(1-m)

makes sense where y is 0 or 1, so one term or the other vanishes.

Alpha:

Step 1: Replace discrete y with continuous x:

    (1a) x*(n/m) + (1-x)*(1-n)/(1-m)

Its not obvious what this is now. Still the probability ratio?? Should you really use

    (1b) (x*n + (1-x)*(1-n)) / (x*m + (1-x)*(1-m))

?? Turns out these equations are the same when mu = 1/2. then mu = (1-mu) and

    (1a) x*n/m + (1-x)*(1-n)/(1-m) = (x*n + (1-x)*(1-n) / 2

    (1b denominator) (x*m + (1-x)*(1-m)) = (x + (1-x))/2 = 1/2

    (1c) (x*n + (1-x)(1-n)) / (1/2)

(I think they are not identical for the general case of m != 1/2 and m != n.

Step 2: Generalize range [0,1] to [0,upper]

I think you need to replace x with x/u, (1-x) with (u-x)/u, n with n/u, etc

So then (1a) becomes

    (2a) (x/u*(n/m) + ((u-x)/u)*(u-n)/(u-m))
       = (x*n/m + (u-x)*(u-n)/(u-m))/u

But 1b becomes

    (2b) (x/u)*(n/u) + ((u-x)/u)*((u-n)/u)) / (x/u)*(m/u) + ((u-x)/u)*((u-m)/u))  
       = (x*n + (u-x)*(u-n)) / (x*m + (u-x)*(u-m))  

note the lack of division by u, since every term has a u*u denominator, so all those cancel out.
when m=1/2, 1c becomes

    (2c) (x*n + (u-x)*(u-n)) / (x*m + (u-x)*(u-m)) = (x*n + (u-x)*(u-n)) / (u/2)


Step 3: Use estimated nj instead of fixed n, and mj instead of fixed m = 1/2 when sampling without replacement:

    (3a) (xj * (nj/mj) + (u-xj) * (u-nj)/(u-mj)) / u

    (3b) (xj*nj + (u-xj)*(u-nj)) / (x*mj + (u-xj)*(u-mj))


### Sampling with or without replacement

We need E(Xj | X^j−1 ) computed with the null hypothosis that θ == µ == 1/2.

Sampling with replacement means that this value is always µ == 1/2.

For sampling without replacement from a population with mean µ, after draw j - 1, the mean of
the remaining numbers is

      (N * µ − Sum(X^j-1)/(N − j + 1).

If this ever becomes less than zero, the null hypothesis is certainly false.
When allowed to sample all N values without replacement, eventually this value becomes less than zero.


### Truncated shrinkage estimate of the population mean

The estimate function can be anything, but it strongly affects the efficiency.

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

````
Choosing d. As d → ∞, the sample size for ALPHA approaches that of BRAVO, for
binary data. The larger d is, the more strongly anchored the estimate is to the reported vote
shares, and the smaller the penalty ALPHA pays when the reported results are exactly correct.
Using a small value of d is particularly helpful when the true population mean is far from the
reported results. The smaller d is, the faster the method adapts to the true population mean,
but the higher the variance is. Whatever d is, the relative weight of the reported vote shares
decreases as the sample size increases.
````

### Questions

Is ALPHA dependent on the ordering of the sample? YES
_"The draws must be in random order, or the sequence is not a supermartingale under the null"_

Is ALPHA dependent on N? Only to test sampleSum > N * t ??
I think this means that one needs the same number of samples for 100, 1000, 1000000 etc.
So its effectiveness increases (as percentage of sampling) as N increases.

Is sampling without replacement more efficient than with replacement? YES

Can we really replicate BRAVO results? YES

Options
* ContestType: PLURALITY, APPROVAL, SUPERMAJORITY, IRV
* AuditType: POLLING, CARD_COMPARISON, ONEAUDIT
* SamplingType: withReplacement, withoutReplacement
* use_styles: do we know what ballots have which contests? Can sample from just those??
* do we have CVRs for all ballots? with/without phantom ballots?
* are we using batches (cluster sampling)?
