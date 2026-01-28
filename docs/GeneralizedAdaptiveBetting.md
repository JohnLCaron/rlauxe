# Generalized Adaptive Betting
_last updated 01/08/26_

See [Betting risk function](docs/BettingRiskFunctions.md) for overview of the risk and betting functions.

## Estimating Error Rates

We keep track of the number of errors of each type that are found for steps < i, and use those to estimate the error rates at step i.
We use the "shrink-truncate" algorithm with (defaut d = 100) to ease the effects of errors found early in the sample, following COBRA eq 4. 
For each of the error types:

````
        if (sampleNum == 0) return 0.0
        if (errorCount == 0) return 0.0 
        val est = (d * aprioriRate + errorCount) / (d + sampleNum - 1)
        return est
    
    where
      aprioriRate = user settable, default is 0.0
      errorCount = number of errors of this type found so far
      sampleNum = i      
````

## OneAudit

Consider a single pool and assorter a, with upper bound u and avg assort value in the pool = poolAvg.
poolAvg is used as the cvr_value, so then cvr_assort - mvr_assort has one of 3 possible overstatement values:

    poolAvg - [0, .5, u] = [poolAvg, poolAvg -.5, poolAvg - u] for mvr loser, other and winner 

then bassort = (1-o/u)/(2-v/u) in [0, 2] * noerror

    bassort = [1-poolAvg/u, 1 - (poolAvg -.5)/u, 1 - (poolAvg - u)/u] * noerror
    bassort = [1-poolAvg/u, (u - poolAvg + .5)/u, (2u - poolAvg)/u] * noerror

For each pool, we know the expected number of loser, winner, and other votes:

```
    winnerVotes = votes[assorter.winner()]
    loserVotes = votes[assorter.loser()]
    otherVotes = pool.ncards() - winnerVotes - loserVotes
```

The expected rates are the votes divided by Npop.

Then we extend equation 1 with the expected assort values from the pools:

````
log T_i = ln(1.0 + lamda * (noerror - mui)) * p0  + Sum { ln(1.0 + lamda * (assortValue_k - mui)) * p_k } 
          + Sum { ln(1.0 + lamda * (assortValue_pk - mui)) * p_pk; over pools and pool types }              (eq 2)

where 
    p_pk is the probability (=rate)  of getting pool p, type k
    assortValue_pk is the assort value when pool p, type k occurs (k = winner, loser, other)
````

Suppose we have a OneAudit with one pool with 5% of the votes, and both the pool margin and the election margin are 2%,
and there are no errors at all in the CVRs. The terms in equation 2 are:

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/betting/oapayoff/oapayoff.html" rel="OABettingPayoff">![OABettingPayoff](plots2/betting/oapayoff/oapayoff.png)</a>

In this example, the optimal lamda is around 1.4. Here, the loser term is always negetive, and the winner term is always positive, so
even with no errors, we need many more samples than CLCA. This is highly dependent on the margin and the percent of cards
in the pools.

if there are no errors in the CVRs, lamda will be constant for all samples. we can then calculate the average and stddev of the number of samples needed:

    (payoff)^sampleSize = 1 / riskLimit and
    solving for sampleSize = -ln(riskLimit) / ln(payoff)


Prod (t_k)^(pk * N) > 1/alpha

Sum(ln(tk)*pk)*N) = -ln(alpha)
N  = -ln(alpha) / Sum(ln(tk)*pk)

N  = -ln(alpha) / Sum(ln(tk)*pk)


````
    fun expectedValueLogt(lam: Double, show: Boolean = false): Double {
        val noerrorTerm = ln(1.0 + lam * (noerror - mui)) * p0

        var sumClcaTerm = 0.0
        clcaErrorRates.forEach { (sampleValue: Double, rate: Double) ->
            sumClcaTerm += ln(1.0 + lam * (sampleValue - mui)) * rate
        }

        var sumOneAuditTerm = 0.0
        if (oaErrorRates != null) {
            oaErrorRates.forEach { (assortValue: Double, rate: Double) ->
                sumOneAuditTerm += ln(1.0 + lam * (assortValue - mui)) * rate
            }
        }
        val total = noerrorTerm + sumClcaTerm + sumOneAuditTerm

        if (debug) println("  lam=$lam, noerrorTerm=${df(noerrorTerm)} sumClcaTerm=${df(sumClcaTerm)} " +
                "sumOneAuditTerm=${df(sumOneAuditTerm)} expectedValueLogt=${total} ")

        return total
    }
````