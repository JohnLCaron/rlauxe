# Generalized Adaptive Betting for CLCA
_last updated 01/03/24_

The default betting strategy for CLCA is a generalized form of AdaptiveBetting from the COBRA paper. We generalize to use any
number of error types, and any kind of assorter, in particular ones with upper != 1, such as DHondt.

Suppose for a particular assorter, there are a fixed number and type of errors with known assort values and probabilities {p1..,pn},
and p0 = 1 - Sum { pk } is the probability of no error. The betting Martingale M_t is

````
M_t = Prod { T_i , i = 1...t }
M_t = Prod { 1 + lamda_i(X_i - mu_i) , i = 1...t }

where
  lamda_i is the bet at step i, lamda_i in [0..2]
  X_i is the CLCA assort value at step i
  mu_i is the populationMean under the null hypothesis, when sampling without replacement. See (ALPHA section 2.2.1).
  mu_i = (N * 0.5 - sampleTracker.sum()) / (N - i)
      N = population size
      i = sample number
````

Following COBRA, at each step, before sample X_i is drawn, we find the optimal value of lambda which maximizes the expected value of the log of T_i, and use that as the lamda bet for step i:

````
log T_i = ln(1.0 + lamda * (noerror - mui)) * p0  + Sum { ln(1.0 + lamda * (assortValue_k - mui)) * p_k }   (eq 1)

where 
    p0 is the probability of no error (mvr matches the cvr)
    p_k is the probability of error type k
    assortValue_k is the value of X when error type k occurs
````

We use the BrentOptimizer from _org.apache.commons.math3_ library to find the optimal lamda for equation 1 on
the interval \[0.0, maxBet].

## Maximum Bet using Maximum Risk

In order to prevent stalls in BettingMart, the maximum bet is bounded by a "maximum risk" value, which is the maximum
percent of your "winnings" you are willing to risk on any one bet:

````
    tj is how much you win or lose
    tj = 1 + lamj * (xj - mj)
    tj = 1 - lamj * mj when x == 0 (smallest value x can be)

    how much above 0 should it be?
    limit your bets so at most you lose maxRisk for any one bet:
    
    tj > (1 - maxRisk)
    1 - lamj * mj > 1 - maxRisk   when x = 0
    lamj * mj < maxRisk
    lamj <  maxRisk / mj
    maxBet = maxRisk / mj
````

TODO: whats a good maxRisk?

## Possible assort values

We do an affine transformation of our assorters so that they return values in the range [0, upper]. With the notable exception
of OneAudit CLCA assorters, all assorters (Plurality, DHondt, AboveThreshold (aka Supermajority), and BelowThreshold) 
return one of three values {0, 1/2 and upper}. 

A CLCA overstatement error = cvr_assort - mvr_assort has one of 7 possible values:

````
    [0, .5, u] - [0, .5, u] = 0, -.5, -u,
                             .5,  0, .5-u,
                              u, u-.5, 0
                              
    = [-u, -.5, .5-u, 0, .5, u-.5, u]  
````

The CLCA assorter (aka bassort) does an affine transformation of the overstatement error:

````
   bassort = (1-o/u)/(2-v/u) in [0, 2] * noerror
   where
     u = assorter upper value
     v = reported margin
     o = overstatement error
     noerror = 1/(2-v/u)

then the possible values of bassort = (1-o/u) * noerror are:

    [0, 1/2u, 1-1/2u, noerror, 1+1/2u, 2-1/2u, 2] * noerror (eq 1)
````

For Plurality, u = 1, so the possible values are:
````
[0, .5, 1, 1.5, 2] * noerror (u=1)
aka [p2o, p1o, noerror, p1u, p2u]
````

In general, when u != 1, there are 7 possible values, for example, a Dhondt assorter with u = 1.75:

````
DHondt upperBound=1.7500, noerror=0.51470588

[0.0, 0.14705882352941177, 0.36764705882352944, 0.51470588, 0.6617647058823529, 0.8823529411764708, 1.0294117647058825]
[0, 1/2u, 1-1/2u, 1, 1+1/2u, 2-1/2u, 2] * noerror

     winner-loser tau= 0.0000 '      0' (win-los)
     winner-other tau= 0.2857 '   1/2u' (win-oth)
      other-loser tau= 0.7143 ' 1-1/2u' (oth-los)
    winner-winner tau= 1.0000 'noerror' (noerror)
      other-other tau= 1.0000 'noerror' (noerror)
      loser-loser tau= 1.0000 'noerror' (noerror)
      loser-other tau= 1.2857 ' 1+1/2u' (oth-win)
     other-winner tau= 1.7143 ' 2-1/2u' (los-oth)
     loser-winner tau= 2.0000 '      2' (los-win)
````

when you throw phantoms into the mix:

````
     winner-loser tau= 0.0000 '      0' (win-los)
   winner-phantom tau= 0.0000 '      0' (win-los)
     winner-other tau= 0.2857 '   1/2u' (win-oth)
      other-loser tau= 0.7143 ' 1-1/2u' (oth-los)
    other-phantom tau= 0.7143 ' 1-1/2u' (oth-los)
    phantom-loser tau= 0.7143 ' 1-1/2u' (oth-los)
  phantom-phantom tau= 0.7143 ' 1-1/2u' (oth-los)
    winner-winner tau= 1.0000 'noerror' (noerror)
      other-other tau= 1.0000 'noerror' (noerror)
      loser-loser tau= 1.0000 'noerror' (noerror)
    loser-phantom tau= 1.0000 'noerror' (noerror)
    phantom-other tau= 1.0000 'noerror' (noerror)
      loser-other tau= 1.2857 ' 1+1/2u' (oth-win)
     other-winner tau= 1.7143 ' 2-1/2u' (los-oth)
   phantom-winner tau= 1.7143 ' 2-1/2u' (los-oth)
     loser-winner tau= 2.0000 '      2' (los-win)
````

## Estimating Error Rates

We keep track of the number of errors of each type that are found for steps < i, and use those to estimate the error rates at step i.
We use the "shrink-truncate" algorithm with d = 100 to ease the effects of errors found early in the sample, following COBRA eq 4. For
each of the error types:

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
