# Ballot Payoff Plots

## 12/10/24

Plots 1-5 shows the betting payoffs when all 4 error rates are equal to {0.0, 0.0001, .001, .005, .01}, respectively:

<a href="https://johnlcaron.github.io/rlauxe/docs/plots/betting/BettingPayoff0.0.html" rel="BettingPayoff0">![BettingPayoff0](plots/betting/BettingPayoff0.0.png)</a>
<a href="https://johnlcaron.github.io/rlauxe/docs/plots/betting/BettingPayoff1.0E-4.html" rel="BettingPayoff1.0E-4">![BettingPayoff1.0E-4](plots/betting/BettingPayoff1.0E-4.png)</a>
<a href="https://johnlcaron.github.io/rlauxe/docs/plots/betting/BettingPayoff0.001.html" rel="BettingPayoff0.001">![BettingPayoff0.001](./plots/betting/BettingPayoff0.001.png)</a>
<a href="https://johnlcaron.github.io/rlauxe/docs/plots/betting/BettingPayoff0.005.html" rel="BettingPayoff0.005">![BettingPayoff0.005](plots/betting/BettingPayoff0.005.png)</a>
<a href="https://johnlcaron.github.io/rlauxe/docs/plots/betting/BettingPayoff0.01.html" rel="BettingPayoff01">![BettingPayoff01](plots/betting/BettingPayoff0.01.png)</a>

Plot 6 shows the payoffs for all the error rates when the MVR matches the CVR (assort value = 1.0 * noerror):

<a href="https://johnlcaron.github.io/rlauxe/docs/plots/betting/BettingPayoffAssort1.0.html" rel="BettingPayoffAssort1">![BettingPayoffAssort1](plots/betting/BettingPayoffAssort1.0.png)</a>

Plot 7 translates the payoff into a sample size, when there are no errors, using (payoff)^sampleSize = 1 / riskLimit and
solving for sampleSize = -ln(riskLimit) / ln(payoff).

<a href="https://johnlcaron.github.io/rlauxe/docs/plots/betting/BettingPayoffSampleSize.html" rel="BettingPayoffSampleSize">![BettingPayoffSampleSize](plots/betting/BettingPayoffSampleSize.png)</a>

The plot "error=0.0" is the equivilent to COBRA Fig 1, p. 6 for risk=.05. This is the best that can be done,
this is the minimum sampling size for the RLA.
Note that this value is independent of N, the number of ballots.

See GenBettingPayoff.kt for the geration of these plots.