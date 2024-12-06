# RLA Options
11/4/2024 (DRAFT)

I am trying to see what "user visible" options need to be allowed, where user is, eg, Colorado DOS, 
or a consultant working with them configuring the library for their needs. 
So I wanted to leave out all the research questions, its our job to answer those, 
and I was picking the best answers in my understanding so far.

## Sampling with replacement vs sampling without replacement

I dont know of any reason to use sampling with replacement.

## Polling vs Comparison

If a jurisdiction cant create CVRS, then use polling, otherwise use Comparison.

### Polling options

For the risk function, use AlphaMart (or equivilent BettingMart) with ShrinkTrunkage, which estimates the true 
population mean (theta) with a weighted average of an initial estimate (eta0) with the actual sampled mean.
Use the reported winner's mean as eta0.
The only settable parameter is d, which is used for estimating theta at each sample draw:

    estTheta = (d*eta0 + sampleSum_i) / (d + sampleSize_i)

which trades off smaller sample sizes when theta = eta0 (large d) vs quickly adapting to when theta < eta0 (smaller d).

A few representative plots showing the effect of d are at [meanDiff plots](https://docs.google.com/spreadsheets/d/1bw23WFTB4F0xEP2-TFEu293wKvBdh802juC7CeRjp-g/edit?gid=1185506629#gid=1185506629).

### Comparison options

The requirements for Comparison audits:

1. Assign unique identifier to each physical ballot, and put on the CVR. This is used to find the physical ballot from the sampled CVR.
2. Must have independent upper bound on the number of cast cards that contain the contest.

For the risk function, use BettingMart with AdaptiveComparison. AdaptiveComparison needs estimates of the rates of 
over(under)statements. If these estimates are correct, one gets optimal sample sizes. AdaptiveComparison use a variant of
ShrinkTrunkage that uses a weighted average of initial estimates (aka priors) with the actual sampled rates.

TODO: quantify how things go when rate estimates are incorrect. A first pass is at 
[Ballot Comparison using Betting Martingales](https://johnlcaron.github.io/rlauxe/docs/Betting.html)

The nice thing about SHANGRLA is that it cleanly separates the risk-function from the sampling strategy. All of the above
is the risk-function. Following concerns the sampling strategy.


#### Comparison Audits with CSD (card-style data)

Risk-limiting audits (RLAs) can use information about which ballot cards contain which
contests (card-style data, CSD), see [Stylish Risk-Limiting Audits in Practice](https://arxiv.org/abs/2309.09081).
According to that paper, this makes a huge difference in sample sizes.

CSD requires:

3. A set of ballot types, which define which contests appear on ballots of each type. CVRs must contain the ballot type.
4. Alternatively, the CVRs must encode empty contests. Then, we can infer the ballot type from the CVR.

This information is available for any election. The question is whether its included on the CVR.

TODO: quantify how much difference CSD makes.


#### Sample size estimation

With all of the above, we can estimate sample sizes by simulation, knowing only the reported margin and risk limit.
The main settable parameter here is the target percentage (aka quantile) of runs that should finish within the 
estimated sample size.

TODO: show sample size estimates as function of quantile and reported margin.


#### Consistent Sampling

Here we create a consistent sampling across all contests under audit. I dont think there are any user 
visible options here.


## Hybrid Audits (not done)


## Summary

Jurisdiction Scenarios

1. Single jurisdiction - eg county, city.
2. Multiple jurisdictions, eg congressional district.
3. Statewide, eg statewide offices.

Audit types

1. Polling/Comparison
2. hasStyles/noStyles
4. ONEAudit?
5. Hybrid?
6. Stratified?
