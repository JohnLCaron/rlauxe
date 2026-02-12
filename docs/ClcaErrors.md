# CLCA errors
2/12/2026

Read [BettingRiskFunctions](BettingRiskFunctions.md) for background. Here are necessary definitions:

    x_i = the assort value, 0 <= x_i <= upper
    1/2 < upper < unbounded but known
    µ_i = the expected value of the sample mean, if the assertion is false (very close to 1/2 usually)
    λ_i = the "bet" placed on the ith sample, based on the previous samples, 0 <= λ_i <= 2 .

    payoff_i = (1 + λ_i (x_i − µ_i)) = the payoff of the ith bet
    T_i = Prod (payoff_i, i= 1..i) = the product of the payoffs, aka the "testStatistic"


## CLCA assort values

We do an affine transformation of our assorters so that they all return one of three values [0, 1/2, upper] * noerror,
corresponding to whether the card has a vote for the loser, other, or winner.

A CLCA overstatement error = cvr_assort - mvr_assort has one of 7 possible values:

````

    [0, .5, u] - [0, .5, u] = 0, -.5, -u,
                             .5,  0, .5-u,
                              u, u-.5, 0

ordering these:
                             
    = [-u, -.5, .5-u, 0, .5, u-.5, u] * noerror  
    
the corresponding names are
    cvr - mvr
    [los,oth,win]-[los,oth,win] = [noerror, los-oth, los-win]
                                  [oth-los, noerror, oth-win]
                                  [win-los, win-oth, noerror] 
                        
matching this ordering [-u, -.5, .5-u, 0, .5, u-.5, u]:
                                  
    ["los-win", "los-oth", "oth-win", "noerror", "oth-los", "win-oth", "win-los"] 
                                  
````

The CLCA assorter (aka bassort) does an affine transformation of the overstatement error:

````
   bassort = (1-o/u)*noerror = taus * noerror
   where
     o = overstatement error
     u = assorter upper value
     v = reported margin
     noerror = 1/(2-v/u)

then the possible values of bassort = (1-o/u) * noerror are:

    o = [u, u-.5, .5, 0, .5-u, -.5, -u]
    (1-o/u) = [1-u/u, 1-(u-.5)/u, 1-.5/u, 0, 1-(.5-u)/u, 1--.5/u, 1- -u/u]
    (1-o/u) = [0, 1-(u/u-.5/u), 1-.5/u, 0, 1-(.5/u-u/u), 1+.5/u, 1+u/u]
    (1-o/u) = [0, -.5/u), 1-.5/u, 0, 2-.5/u, 1+.5/u, 2]
    
          o= [-u, -.5, .5-u, 0, .5, u-.5, u]
    (1-o/u)= [1- -u/u, 1- -.5/u, 1-(.5-u)/u, 1, 1-.5/u, 1-(u-.5)/u, 1-u/u]
    (1-o/u)= [1+u/u, 1+.5/u, 1-(.5/u-u/u), 1, 1-.5/u, 1-(u/u-.5/u), 1-u/u]
    (1-o/u)= [2, 1+.5/u, 2-.5/u, 1, 1-.5/u, .5/u, 0]
    (1-o/u)= [2, 1+1/2u, 2-1/2u, 1, 1-1/2u, 1/2u, 0]
    (1-o/u)= [2, 1+u12, 2-u12, 1, 1-u12, u12, 0], where u12= 1/2u
    
taus' = (1-o/u) = [2,          1+u12,    2-u12,     1,         1-u12,     u12,       0]
taus' names     = ["los-win", "los-oth", "oth-win", "noerror", "oth-los", "win-oth", "win-los"]

in our code its more convenient to reverse the ordering:

taus =       [0.0,       u12,       1-u12,     1.0,       2-u12,     1+u12,     2.0]
taus names = ["win-los", "win-oth", "oth-los", "noerror", "oth-win", "los-oth", "los-win"]

Note that taus depends only on u = assorter.upperLimit
````

For Plurality, u = 1, so the possible values are:
````
    [0, .5, 1, 1.5, 2] * noerror (u=1)
    
we give them SHANGRLA names: tauNames = ["p2o", "p1o", "noerror", "p1u", "p2u"] corresponding to these values.

````

In general, when u != 1, there are 7 possible values. For example, a Dhondt assorter with u = 1.75:

````
DHondt upperBound=1.7500, noerror=0.51470588

[0.0, 0.1470588235294, 0.3676470588235, 0.51470588, 0.661764705882, 0.882352941176, 1.029411764705]
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

When you throw phantoms into the mix:

````
     winner-loser tau= 0.0000 '      0' (win-los)
   winner-phantom tau= 0.0000 '      0' (win-los) = (win-pha)
     winner-other tau= 0.2857 '   1/2u' (win-oth)
      other-loser tau= 0.7143 ' 1-1/2u' (oth-los)
    other-phantom tau= 0.7143 ' 1-1/2u' (oth-los) = (oth-pha)
    phantom-loser tau= 0.7143 ' 1-1/2u' (oth-los) = (pha-los)
  phantom-phantom tau= 0.7143 ' 1-1/2u' (oth-los) = (pha-pha)
    winner-winner tau= 1.0000 'noerror' (noerror)
      other-other tau= 1.0000 'noerror' (noerror)
      loser-loser tau= 1.0000 'noerror' (noerror)
    loser-phantom tau= 1.0000 'noerror' (noerror) = (los-pha)
    phantom-other tau= 1.0000 'noerror' (noerror) = (pha-oth)
      loser-other tau= 1.2857 ' 1+1/2u' (los-oth)
     other-winner tau= 1.7143 ' 2-1/2u' (oth-win)
   phantom-winner tau= 1.7143 ' 2-1/2u' (oth-win) = (pha-win)
     loser-winner tau= 2.0000 '      2' (los-win)
````

When the cvr is a phantom, it's the same as "oth".
When the mvr is a phantom, it's the same as "los".

## The effects of CLCA Errors

It's instructive to see the effect of each error type on the testStatistic T, which gets multiplied by the 
payoff for that error. We will measure the effect of one error by looking at the increase in noerror samples needed.

When the mvr and cvr agree, the assort value = noerror, so the payoff is (µ_i approximately 1/2):

    payoff_noerror = (1 + λ * (noerror − 1/2))  

When the mvr and cvr disagree, the assort value = tau * noerror, and the payoff is

    payoff_taus = (1 + λ * (taus * noerror − 1/2))

How many "noerror" samples are equivilent to one sample with assort value taus * noerror ?

    payoff_noerror^n_taus = payoff_taus
    n_taus = ln(payoff_taus) / ln(payoff_noerror)
    n_taus = ln((1 + λ * (taus * noerror − 1/2)) / ln(1 + λ * (noerror − 1/2))

This depends on lamda, as well as upper and margin, since noerror = 1/(2-margin/upper)

First we fix margin = .01 and upper = 1.0, and show the dependence on lamda:

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/betting/errorComp/byLamda.html" rel="byLamda">![byLamda](plots2/betting/errorComp/byLamda.png)</a>

Then we fix lamda = 1.8 and show the dependence on margin for various values of upper:

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/betting/errorComp/byMargin.html" rel="byMargin">![byLamda](plots2/betting/errorComp/byMargin.png)</a>
<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/betting/errorComp/byUpper2.html" rel="byUpper2">![byUpper2](plots2/betting/errorComp/byUpper2.png)</a>
<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/betting/errorComp/byUpperUnder1.html" rel="byUpperUnder1">![byUpperUnder1](plots2/betting/errorComp/byUpperUnder1.png)</a>
<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/betting/errorComp/byUpper10.html" rel="byUpper10">![byUpper10](plots2/betting/errorComp/byUpper10.png)</a>

The last two plots correspond to the BelowThreshold and AboveThreshhold assorters with 5% threshold, as used in Belgian D'hondt elections.

* Understatement errors have the effect of decreasing the number of samples needed, shown on the plots as negetive numbers.

* It is sobering to see how many extra samples are needed for just one error. For example, for a single p2o error, a plurality contest (upper=1) needs 100 extra samples when the margin is .05, and 500 extra when the margin is .01. These are absolute numbers, not dependent on the population size.

* Extra samples needed appear to scale linearly with upper: when upper=10, one needs 1000 and 5000 extra ballots for margins of .05 and .01, and so on.

* When applied to a real audit, one must take into account the probability of encountering an error in the sampled population. This will roughly be
  nerrors * sampleSize / populationSize.


These results are intrinsic to the risk function's payoff calculation, and independent of any implementation, except for the choice of lamda.
From the first plot vs lamda, we see that only p2o is strongly dependent on the choice of lamda. But we cant peek ahead to see what the next sample is,
and a 10% decrease in lamda approximately increases the samples needed by 10-15% when there are noerrors.

It seems that there's an unavoidable choice between optimistically assuming there are few to no errors, and trying to minimize the effects of errors if they are found. 

Also see [Choosing MaxLoss](https://github.com/JohnLCaron/rlauxe/blob/main/docs/BettingRiskFunctions.md#choosing-maxloss)





