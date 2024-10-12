# Ballot Comparison using Betting Martingales

## 10/12/24

The BETTING paper presents general techniques for estimating an unknown mean from bounded observations.

ALPHA summarizes this for RLAs, in section 2.3. While formally equivalent to the sequential probability ratios (SPR) approach, 
the betting strategy approach gives better intuition on the "aggressive betting" strategy, which is necessary to
get good performance for ballot comparison audits.

The COBRA paper explores a number of algorithms for optimal betting parameters for ballot 
comparison audits, based on estimating the rates of the under/overstatement errors:

````
    Table 1
    p0 := #{xi = a}/N is the rate of correct CVRs.
    p1 := #{xi = a/2}/N is the rate of 1-vote overstatements.
    p2 := #{xi = 0}/N is the rate of 2-vote overstatements.
    p3 := #{xi = 3a/2}/N is the rate of 1-vote understatements.
    p4 := #{xi = 2a}/N is the rate of 2-vote understatements.
````
where
* a := 1 / (2 − v/au)
* v := 2Āc − 1 is the diluted margin
* au := assort upper value; = 1 for plurality, 1/(2*minFraction) for supermajority
* mu_i := mean value under H0 (= 1/2 for with replacement), otherwise for WoR, varies for each sample i (COBRA section 6.1).
  TODO: investigate stalling problem in 6.2.

The expected value of the test statistic (generalized from COBRA section 3.2):

````
Equation 1
EF[Ti] = p0 [1 + λ(a − mu_i)] + p1 [1 + λ(a/2 − mu_i)] + p2 [1 − λ*mu_i)]  + p3 [1 + λ(3*a/2 − mu_i)]  + p4 [[1 + λ(2*a − mu_i)]
````

We follow the code in https://github.com/spertus/comparison-RLA-betting/blob/main/comparison_audit_simulations.R, to
find the value of lamda that maximizes EF[Ti], using org.apache.commons.math3.optim.univariate.BrentOptimizer
[see OptimalComparison implementation](core/src/main/kotlin/org/cryptobiotic/rlauxe/core/OptimalComparison.kt).

## GenAdaptiveComparisonTable

Following COBRA, we start with only p2 errors, and use Adaptive betting from COBRA section 4.2, equation (4), 
to estimate the p2 rate using an adapted form of ALPHA's shrink_trunc algorithm:

````
    p̃_ki :=  (d_k * p̃_k + i * p̂_k(i−1)) / (d_k + i − 1)
where    
    k = (1,2,3,4) is one of the rates from Table 1 above
    p̃_k is the a priori estimate for rate pk
    d_k is the adaptive weighting value for the kth rate
    p̂_ki is the sample rate at time i
````

The estimated rate p̃_ki is then plugged into Equation 1, and BrentOptimizer is used to find the maximal value of lamda for 
each step i, on the interval [0, 2]. (TODO Should be [1, 2] ??)

In these plots we keep p2prior fixed to .001, and plot various values against theta, the true mean of the mvrs. With only
2-vote overstatements, theta = cvrMean - p2oracle, where p2oracle is the true value of p2. Thus we are seeing what happens
when the true p2 rate (p2oracle) is greater than p2prior. We expect to do best when p2oracle = p2prior, which for these plots
is when p2oracle = .001. 

Plot 1 shows the average number of samples needed to reject the null, aka "success":

![plotSuccessVsTheta](../plots/src/test/data/bet/plotAdaptiveComparison.plotSuccessVsMargin.100.svg)

Plot 2 shows the percentage of successes when the cutoff is 20% of N. Note these are false positives when
theta <= 0.5:

![plotSuccessVsTheta](../plots/src/test/data/bet/plotAdaptiveComparison.plotSuccess20VsMargin.100.svg)

Plot 3 zooms in on the false positives when the cutoff is 20% of N:

![plotSuccessVsTheta](../plots/src/test/data/bet/plotAdaptiveComparison.plotFailuresVsTheta.100.svg)

Comments
* the weighting factor d_k is set to 100 = .1/p_k. Arguably it should be inversely proportional to p_k, since
  we expect there to be one over/understatement error every 1/p_k samples. 
* this algorithm does well when p2oracle ~= p2prior, giving good results down to margins of 2-3%.
* we are testing very high values of p2oracle, in order to see how the algorithm responds when the overstatement
  errors are high enough to change the outcome.
* the false positives stay under the 5% risk limit, even when theta = 1/2.
* TODO test if these conclusions hold when p1 is non-zero, and when there are varying levels of 
  "random error" in the form of non-zero rates for all the errors.
* TODO test a wide range of p2priors, as well as a range of d values.