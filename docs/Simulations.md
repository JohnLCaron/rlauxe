# Simulations
last update: 09/26/2024

Table of Contents
<!-- TOC -->
* [Simulations](#simulations)
  * [Sample size simulations (Polling)](#sample-size-simulations-polling)
    * [compare table 3 of ALPHA for Polling Audit with replacement](#compare-table-3-of-alpha-for-polling-audit-with-replacement)
    * [how to set the parameter d?](#how-to-set-the-parameter-d)
  * [Sample size simulations (Ballot Comparison)](#sample-size-simulations-ballot-comparison)
  * [Polling vs Comparison](#polling-vs-comparison)
  * [Unrealistic simulations in the ALPHA paper](#unrealistic-simulations-in-the-alpha-paper)
  * [Notes/thoughts](#notesthoughts)
<!-- TOC -->

## Sample size simulations (Polling)

See [9/4/24 plots](https://docs.google.com/spreadsheets/d/1bw23WFTB4F0xEP2-TFEu293wKvBdh802juC7CeRjp-g/edit?gid=662624429#gid=662624429)

* RLA cant help for close elections unless N is large
* seems like this is because sample size is approx. independent of N (Plot 1)
* sample size approx 1/margin as N -> inf. Something more complicated as margin -> 0. (Plot 2b)
* variance is quite large (Plot 4)

### compare table 3 of ALPHA for Polling Audit with replacement

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

See [output](DiffMeanOutput.txt) of DiffMeans.kt and PlotDiffMeans.kt. This is done for each value of
N and theta.

A few representative plots are at 
[meanDiff plots](https://docs.google.com/spreadsheets/d/1bw23WFTB4F0xEP2-TFEu293wKvBdh802juC7CeRjp-g/edit?gid=1185506629#gid=1185506629)

* samples size when reported mean != theta (true mean)
* show tables of mean difference = (reported mean - theta) columns vs values of d parameter (rows)
* ntrials = 1000

Notes:

* For many values of N and theta, we cant help (margin too small; N too small); or it doesn't matter much (margin large, N large).
* Ive chosen a few plots where values of N and theta have pct samples 10 - 30%, since thats where improvements might matter for having a successful RLA vs a full hand recount.
* High values of d work well when reported mean ~= theta. 
* Low values of d work better as mean difference = (reported mean - theta) grows.
* The question is, how much weight to give "outliers", at the expense of improving success rate for "common case" of reported mean ~= theta ?
* See CreatePollingDiffMeans.kt

To Investigate
* Does it make sense to use small values of d for large values of reported mean? because it will only matter if (reported mean - theta) is large.
* Sample percents get higher as theta -> 0. Can we characterize that? proportional to 1 / theta ?
* Number of samples is independent of N as N gets large.


## Sample size simulations (Ballot Comparison)

This algorithm seems to be less efficient than polling. The margins are approximately half, so take much longer.

For example, for theta = .51, and the CVRs agree exactly with the MVRs (all overstatements == 0),
the avg number of samples went from 12443 to 16064 (N=50000), and 5968 to 7044 (N=10000).

This is because the SHANGRLA comparison assorter reduces the margin by approx half:

````
  theta   margin  noerror marginB marginB/margin
  0.5010   0.0020   0.5005   0.0010   0.5005
  0.5025   0.0050   0.5013   0.0025   0.5013
  0.5050   0.0100   0.5025   0.0050   0.5025
  0.5100   0.0200   0.5051   0.0101   0.5051
  0.5200   0.0400   0.5102   0.0204   0.5102
  0.5300   0.0600   0.5155   0.0309   0.5155
  0.5400   0.0800   0.5208   0.0417   0.5208
  0.5500   0.1000   0.5263   0.0526   0.5263
  0.5750   0.1500   0.5405   0.0811   0.5405
  0.6000   0.2000   0.5556   0.1111   0.5556
  0.6500   0.3000   0.5882   0.1765   0.5882
  0.7000   0.4000   0.6250   0.2500   0.6250
````

See TestComparisonAssorter.testBvsV().

Tables 7 and 8 in ALPHA, showing comparison ballot results, switch from using theta to using "mass at 1". Which is
not very clear (see [section](#unrealistic-simulations-in-the-alpha-paper) below).
Also, the very small sample sizes in Table 7 of ALPHA are surprising. Really need only 5 ballots out of 500,000 
to reject the null hypothosis?

However, see TestComparisonFromAlpha.comparisonReplication(): setting eta0 very high brings the numbers down such that comparison is much better
than polling, and can deal even with very small margins:

````
TestComparisonFromAlpha.comparisonReplication ntrials=1000
 nsamples, ballot comparison, N=10000, d = 100, error-free
 theta (col) vs eta0 (row)
       : 0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700, 
   0.9,   9955,   9768,   9343,   8594,   7511,   2357,    501,    243,    157,    115,     70,     51,     34,     26, 
   1.0,   9951,   9720,   9127,   7994,   6410,   1468,    336,    174,    116,     87,     54,     40,     27,     21, 
   1.5,   9917,   9027,   6008,   3242,   1864,    427,    155,     98,     74,     59,     39,     29,     19,     14, 
   2.0,   9826,   6756,   2956,   1517,    949,    312,    148,     98,     74,     59,     39,     29,     19,     14, 
   5.0,   5185,   1625,    963,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14, 
   7.5,   3315,   1393,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14, 
  10.0,   2768,   1390,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14, 
  15.0,   2588,   1390,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14, 
  20.0,   2588,   1390,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14, 
````

However, setting eta0 very high is suspect. Below, we see that setting eta0 higher
than the upper limit of the comparison assorter creates false positives and so exceeds the risk limit.

In the plots below, we simulate the reported mean of the cvrs as exactly 0.5% higher than theta (the true mean). 
So for example when the reported cvr mean assort (aka Āc in SHANGRLA section 3.2) is 0.501, theta is .501 - .005 = 0.496.
The number of successes when theta <= .5 are all false positives, and by contract the successPct must be < 5% = risk factor. 
We show theta vs various values of _eta0Factor_, where eta0 = eta0Factor * noerrors, and _noerrors_ is the comparison 
assort value when the cvrs and the mvrs agree exactly (all overstatement errors are 0). 
The value noerrors is a simple function of Āc: _noerrors = 1.0 / (3 - 2 * Āc)_.

The tables below use eta0 = eta0Factor * noerrors.
The upper bounds of the comparison assorter is 2 * noerrors, represented by the row with etaFactor = 2.0.

Using eta0 == noerrors, there are no false positives. When eta0Factor is between 1.0 and 2.0, the false positives are
less than 5%. When eta0 >= 2.0 * noerrors, the algorithm no longer stays within the risk limit.

````
ComparisonWithErrors.cvrComparisonFailure ntrials=1000, N=10000, d=10000 cvrMeanDiff=-0.005; theta (col) vs etaFactor (row)

cvrMean: 0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700, 
successes
  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.505,  0.515,  0.525,  0.535,  0.545,  0.570,  0.595,  0.645,  0.695, 
  1.00,      0,      0,      0,      0,      0,   1000,   1000,   1000,   1000,   1000,   1000,   1000,   1000,   1000, 
  1.25,      0,      0,      0,      0,      4,   1000,   1000,   1000,   1000,   1000,   1000,   1000,   1000,   1000, 
  1.50,      0,      0,      0,      4,     42,   1000,   1000,   1000,   1000,   1000,   1000,   1000,   1000,   1000, 
  1.75,      0,      0,      3,      9,     46,   1000,   1000,   1000,   1000,   1000,   1000,   1000,   1000,   1000, 
  1.99,      0,      1,      1,     17,     43,   1000,   1000,   1000,   1000,   1000,   1000,   1000,   1000,   1000, 
  2.00,      0,      0,      7,     27,     63,   1000,   1000,   1000,   1000,   1000,   1000,   1000,   1000,   1000, 
  2.10,      0,     25,    780,   1000,   1000,   1000,   1000,   1000,   1000,   1000,   1000,   1000,   1000,   1000, 
  2.20,      0,    866,   1000,   1000,   1000,   1000,   1000,   1000,   1000,   1000,   1000,   1000,   1000,   1000, 

successPct
  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.505,  0.515,  0.525,  0.535,  0.545,  0.570,  0.595,  0.645,  0.695, 
  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0, 
  1.25,    0.0,    0.0,    0.0,    0.0,    0.4,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0, 
  1.50,    0.0,    0.0,    0.0,    0.4,    4.2,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0, 
  1.75,    0.0,    0.0,    0.3,    0.9,    4.6,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0, 
  1.99,    0.0,    0.1,    0.1,    1.7,    4.3,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0, 
  2.00,    0.0,    0.0,    0.7,    2.7,    6.3,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0, 
  2.10,    0.0,    2.5,   78.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0, 
  2.20,    0.0,   86.6,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0,  100.0, 

pct nsamples needed
  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.505,  0.515,  0.525,  0.535,  0.545,  0.570,  0.595,  0.645,  0.695, 
  1.00,   0.00,   0.00,   0.00,   0.00,   0.00,  91.27,  53.51,  29.97,  18.44,  12.31,   5.79,   3.35,   1.54,   0.90, 
  1.25,   0.00,   0.00,   0.00,   0.00,  75.97,  25.38,   7.66,   4.43,   3.06,   2.29,   1.35,   0.94,   0.55,   0.37, 
  1.50,   0.00,   0.00,   0.00,  30.49,  41.34,  16.84,   4.42,   2.48,   1.69,   1.29,   0.79,   0.56,   0.34,   0.24, 
  1.75,   0.00,   0.00,  15.04,  15.11,  16.80,  17.53,   3.54,   1.87,   1.29,   0.95,   0.57,   0.40,   0.26,   0.18, 
  1.99,   0.00,  15.00,  10.04,   7.95,   6.82,  37.41,   6.07,   2.50,   1.43,   0.95,   0.55,   0.37,   0.22,   0.16, 
  2.00,   0.00,   0.00,   9.94,   7.46,   7.82,  41.45,   7.31,   2.97,   1.74,   1.18,   0.61,   0.40,   0.23,   0.17, 
  2.10,   0.00,  15.83,  10.31,   7.60,   6.01,   2.98,   1.49,   0.99,   0.74,   0.59,   0.39,   0.29,   0.19,   0.14, 
  2.20,   0.00,  17.54,  10.44,   7.61,   6.02,   2.98,   1.49,   0.99,   0.74,   0.59,   0.39,   0.29,   0.19,   0.14, 
````

The "pct nsamples" is the number of samples needed for the successful RLAs (lower is better). 
These get dramatically smaller as etaFactor gets larger. But eta0Factor >= upperLimit are bogus.

Going back to N vs theta plots, for various values of eta0Factor
(the value of d also matters, but for now, we set it high which tends to exaggerate the effect of eta0Factor),
here are the ratio of the number of samples / min number of samples across eta0Factor
(so 1.0 means that is the best setting of etaFactor),
as well as the geometric average of the ratios:

````
ComparisonWithErrors.comparisonNvsTheta samplePct ratios across cvrMean: ntrials=1000, d = 10000, cvrMeanDiff=-0.005, theta(col) vs N(row)
  theta: 0.501,  0.502,  0.503,  0.504,  0.505,  0.515,  0.525,  0.535,  0.545,  0.595,  0.695, 

ratio eta0Factor=1.0,  theta(col) vs N(row)
cvrMean: 0.506,  0.507,  0.508,  0.509,  0.510,  0.520,  0.530,  0.540,  0.550,  0.600,  0.700, 
  1000,  2.019,  1.094,  1.186,  1.340,  1.445,  3.340,  4.965,  5.953,  6.674,  6.391,  4.910, 
  5000,  1.140,  1.569,  2.108,  2.711,  3.406, 10.475, 12.530, 12.811, 11.827,  8.009,  5.307, 
 10000,  1.297,  2.087,  3.039,  4.334,  5.632, 14.828, 16.177, 14.938, 12.983,  8.252,  5.546, 
 20000,  1.554,  2.956,  4.954,  7.200,  9.375, 19.493, 18.613, 16.350, 13.975,  8.275,  5.098, 
 50000,  2.085,  5.583,  9.922, 13.693, 17.645, 24.982, 20.751, 17.162, 14.598,  8.433,  5.559, 
geometric mean = 5.822488696803993

ratio eta0Factor=1.25,  theta(col) vs N(row)
cvrMean: 0.506,  0.507,  0.508,  0.509,  0.510,  0.520,  0.530,  0.540,  0.550,  0.600,  0.700, 
  1000,  2.013,  1.088,  1.158,  1.271,  1.315,  1.926,  2.160,  2.229,  2.310,  2.215,  2.141, 
  5000,  1.081,  1.249,  1.354,  1.437,  1.525,  2.201,  2.329,  2.444,  2.425,  2.311,  2.204, 
 10000,  1.109,  1.243,  1.303,  1.439,  1.552,  2.153,  2.384,  2.444,  2.404,  2.318,  2.278, 
 20000,  1.105,  1.188,  1.325,  1.461,  1.561,  2.155,  2.373,  2.478,  2.437,  2.277,  2.089, 
 50000,  1.041,  1.178,  1.337,  1.447,  1.569,  2.212,  2.391,  2.454,  2.463,  2.324,  2.268, 
geometric mean = 1.7815724664178847

ratio eta0Factor=1.5,  theta(col) vs N(row)
cvrMean: 0.506,  0.507,  0.508,  0.509,  0.510,  0.520,  0.530,  0.540,  0.550,  0.600,  0.700, 
  1000,  1.967,  1.045,  1.055,  1.100,  1.079,  1.259,  1.308,  1.323,  1.360,  1.348,  1.415, 
  5000,  1.000,  1.000,  1.000,  1.006,  1.015,  1.284,  1.339,  1.370,  1.375,  1.380,  1.418, 
 10000,  1.000,  1.000,  1.000,  1.000,  1.000,  1.203,  1.325,  1.367,  1.361,  1.381,  1.469, 
 20000,  1.000,  1.000,  1.000,  1.000,  1.000,  1.210,  1.310,  1.372,  1.376,  1.347,  1.337, 
 50000,  1.000,  1.000,  1.000,  1.000,  1.000,  1.238,  1.328,  1.355,  1.395,  1.369,  1.452, 
geometric mean = 1.1960415499515684

ratio eta0Factor=1.75,  theta(col) vs N(row)
cvrMean: 0.506,  0.507,  0.508,  0.509,  0.510,  0.520,  0.530,  0.540,  0.550,  0.600,  0.700, 
  1000,  1.788,  1.000,  1.000,  1.000,  1.000,  1.000,  1.000,  1.000,  1.000,  1.000,  1.065, 
  5000,  1.002,  1.070,  1.052,  1.000,  1.000,  1.000,  1.000,  1.000,  1.000,  1.014,  1.064, 
 10000,  1.073,  1.175,  1.125,  1.115,  1.058,  1.000,  1.000,  1.000,  1.000,  1.000,  1.096, 
 20000,  1.187,  1.292,  1.263,  1.250,  1.119,  1.000,  1.000,  1.000,  1.000,  1.000,  1.008, 
 50000,  1.416,  1.654,  1.508,  1.331,  1.239,  1.000,  1.000,  1.000,  1.000,  1.000,  1.106, 
geometric mean = 1.0815679797954942

ratio eta0Factor=1.9999999999999998,  theta(col) vs N(row)
cvrMean: 0.506,  0.507,  0.508,  0.509,  0.510,  0.520,  0.530,  0.540,  0.550,  0.600,  0.700, 
  1000,  1.000,  1.008,  1.030,  1.135,  1.134,  1.332,  1.246,  1.162,  1.089,  1.019,  1.000, 
  5000,  1.060,  1.338,  1.623,  1.779,  1.830,  1.913,  1.542,  1.321,  1.208,  1.000,  1.000, 
 10000,  1.200,  1.707,  2.094,  2.414,  2.548,  2.042,  1.614,  1.343,  1.246,  1.000,  1.000, 
 20000,  1.411,  2.231,  2.928,  3.157,  3.384,  2.166,  1.654,  1.425,  1.234,  1.018,  1.000, 
 50000,  1.861,  3.611,  4.449,  4.232,  4.159,  2.086,  1.718,  1.429,  1.293,  1.046,  1.000, 
geometric mean = 1.5539324296871986
````

The larger values of eta0Factor do better, and eta0Factor=1 is sometimes 20x worse than the smallest amount.
However, as the margin gets smaller, eta0Factor=1 does better.
More studies are needed varying across d, cvrMeanDiff, etc.

It seems that eta0Factor acts as an accelerant, making each sampled value count
more towards accepting or rejecting the null hypotheses. Not clear if that's a "fair" thing to do. And if it is,
can it be done with polling?

The above results show average number of samples needed and as a percentage of N. This ignores the large variance in the distribution.
What we really want to know is what percentage of trials succeed at various cut-off values (where a full hand count will be more
efficient than sampling a large percentage of the ballots.)

A more germane simulation is to keep a histogram of the percentage "successful RLA", as deciles. 
Below are the
percentage "successful RLA" (higher is better) for audits with sampling cutoffs at 10, 20, 30, 40, 50, and 100% of N.
The reported cvr means have been adjusted to show details around close elections.
Remember that "successRLA" are false positives when theta <= 0.5.

````
ComparisonWithErrors.cvrComparisonFailure ntrials=10000, N=10000, d=10000 cvrMeanDiff=-0.005; theta (col) vs etaFactor (row)
cvrMean: 0.501,  0.502,  0.503,  0.504,  0.505,  0.506,  0.508,  0.510,  0.520,  0.530,  0.540,

% successRLA, for sampleMaxPct=10:
  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.501,  0.503,  0.505,  0.515,  0.525,  0.535,
  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,
  1.25,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,   81.1,  100.0,  100.0,
  1.50,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    2.9,   20.4,   98.5,  100.0,  100.0,
  1.75,    0.0,    0.0,    0.0,    0.0,    1.6,    3.1,   14.3,   32.2,   97.3,  100.0,  100.0,
  1.99,    0.0,    0.0,    0.0,    1.9,    4.6,    7.5,   16.4,   26.3,   76.0,   96.0,   99.8,

% successRLA, for sampleMaxPct=20:
  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.501,  0.503,  0.505,  0.515,  0.525,  0.535,
  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,   77.0,
  1.25,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    1.2,   18.9,  100.0,  100.0,  100.0,
  1.50,    0.0,    0.0,    0.0,    0.0,    0.7,    4.5,   29.5,   68.0,  100.0,  100.0,  100.0,
  1.75,    0.0,    0.0,    0.1,    0.8,    3.5,    9.1,   32.8,   62.7,  100.0,  100.0,  100.0,
  1.99,    0.0,    0.0,    0.5,    2.0,    5.1,    8.8,   19.3,   33.3,   93.8,  100.0,  100.0,

% successRLA, for sampleMaxPct=30:
  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.501,  0.503,  0.505,  0.515,  0.525,  0.535,
  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,   19.5,  100.0,
  1.25,    0.0,    0.0,    0.0,    0.0,    0.0,    0.1,   15.0,   72.1,  100.0,  100.0,  100.0,
  1.50,    0.0,    0.0,    0.0,    0.1,    1.7,    8.7,   51.5,   90.6,  100.0,  100.0,  100.0,
  1.75,    0.0,    0.0,    0.1,    1.0,    4.3,   11.5,   44.6,   80.2,  100.0,  100.0,  100.0,
  1.99,    0.0,    0.0,    0.5,    2.0,    5.1,    9.0,   20.9,   38.7,   99.6,  100.0,  100.0,

% successRLA, for sampleMaxPct=40:
  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.501,  0.503,  0.505,  0.515,  0.525,  0.535,
  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,  100.0,  100.0,
  1.25,    0.0,    0.0,    0.0,    0.0,    0.0,    1.1,   43.1,   96.1,  100.0,  100.0,  100.0,
  1.50,    0.0,    0.0,    0.0,    0.2,    2.4,   13.2,   70.6,   98.3,  100.0,  100.0,  100.0,
  1.75,    0.0,    0.0,    0.1,    1.0,    4.5,   13.0,   55.3,   91.5,  100.0,  100.0,  100.0,
  1.99,    0.0,    0.0,    0.5,    2.0,    5.1,    9.1,   22.4,   45.3,  100.0,  100.0,  100.0,

% successRLA, for sampleMaxPct=50:
  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.501,  0.503,  0.505,  0.515,  0.525,  0.535,
  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.3,  100.0,  100.0,
  1.25,    0.0,    0.0,    0.0,    0.0,    0.1,    3.2,   72.1,   99.9,  100.0,  100.0,  100.0,
  1.50,    0.0,    0.0,    0.0,    0.2,    3.0,   17.5,   85.2,   99.9,  100.0,  100.0,  100.0,
  1.75,    0.0,    0.0,    0.1,    1.0,    4.7,   14.4,   66.3,   97.8,  100.0,  100.0,  100.0,
  1.99,    0.0,    0.0,    0.5,    2.0,    5.2,    9.2,   24.2,   56.3,  100.0,  100.0,  100.0,

% successRLA, for sampleMaxPct=100:
  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.501,  0.503,  0.505,  0.515,  0.525,  0.535,
  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,  100.0,  100.0,  100.0,  100.0,  100.0,
  1.25,    0.0,    0.0,    0.0,    0.0,    0.7,   99.8,  100.0,  100.0,  100.0,  100.0,  100.0,
  1.50,    0.0,    0.0,    0.0,    0.2,    4.2,   99.6,  100.0,  100.0,  100.0,  100.0,  100.0,
  1.75,    0.0,    0.0,    0.1,    1.0,    4.8,   89.1,  100.0,  100.0,  100.0,  100.0,  100.0,
  1.99,    0.0,    0.0,    0.5,    2.0,    5.2,   20.9,  100.0,  100.0,  100.0,  100.0,  100.0,
````

The false positives are partially mitigated when taking the sample cutoff into account.

There's a dramatic increase in RLA success going from etaFactor = 1 to 1.25, and perhaps 1.5-1.75 is a good choice for
these values of N, d, cvrMean, and cvrMeanDiff.

## Polling vs Comparison

Here is the RLA success rate for a 20% cutoff for Polling and CVR Comparison when eta0Factor=1.0:

````
CompareAuditTypeWithErrors.plotAuditTypes
Success Percentage Ratio nsamples Comparison and Polling; ntrials=10000, N=10000, d=1000 eta0Factor=1.0 cvrMeanDiff=-0.005; theta (col) vs N (row)
  theta: 0.501,  0.502,  0.503,  0.505,  0.515,  0.525,  0.535, 
  
% successRLA, for sampleMaxPct=20: polling
       : 0.506,  0.507,  0.508,  0.510,  0.520,  0.530,  0.540, 
  1000,    0.0,    0.0,    0.0,    0.0,    0.3,    1.6,    6.7, 
  5000,    0.3,    0.4,    0.7,    1.1,    9.0,   30.6,   57.6, 
 10000,    0.7,    1.0,    1.6,    2.7,   20.8,   55.8,   85.5, 
 20000,    1.4,    2.0,    3.1,    5.7,   41.1,   83.2,   98.7, 
 50000,    3.0,    4.2,    5.7,   11.5,   76.8,   99.7,  100.0, 

% successRLA, for sampleMaxPct=20: compare
       : 0.506,  0.507,  0.508,  0.510,  0.520,  0.530,  0.540, 
  1000,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0, 
  5000,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0, 
 10000,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,   66.1, 
 20000,    0.0,    0.0,    0.0,    0.0,    0.0,   74.8,  100.0, 
 50000,    0.0,    0.0,    0.0,    0.0,   23.7,  100.0,  100.0, 

RLA % success difference for sampleMaxPct=20 % cutoff: (compare - polling)
cvrMean: 0.506,  0.507,  0.508,  0.510,  0.520,  0.530,  0.540, 
  1000,  -0.01,  -0.02,   0.00,  -0.01,  -0.29,  -1.63,  -6.69, 
  5000,  -0.31,  -0.40,  -0.72,  -1.08,  -9.04, -30.62, -57.57, 
 10000,  -0.72,  -1.01,  -1.59,  -2.70, -20.81, -55.78, -19.40, 
 20000,  -1.42,  -1.97,  -3.13,  -5.74, -41.10,  -8.41,   1.35, 
 50000,  -2.95,  -4.15,  -5.71, -11.51, -53.10,   0.34,   0.00, 
````
CVR compare is quite a bit worse than polling when eta0Factor=1.0.

However, comparison audits are much better when eta0Factor > 1:
````
*** eta0Factor=1.25
Success Percentage Ratio nsamples Comparison and Polling; ntrials=10000, N=10000, d=1000 eta0Factor=1.25 cvrMeanDiff=-0.005; theta (col) vs N (row)
  theta: 0.501,  0.502,  0.503,  0.505,  0.515,  0.525,  0.535, 

% successRLA, for sampleMaxPct=20: polling
       : 0.506,  0.507,  0.508,  0.510,  0.520,  0.530,  0.540, 
  1000,    0.0,    0.0,    0.0,    0.0,    0.3,    1.7,    6.6, 
  5000,    0.3,    0.4,    0.6,    0.9,    8.8,   29.9,   58.6, 
 10000,    0.8,    1.3,    1.5,    2.9,   20.5,   56.3,   85.5, 
 20000,    1.5,    2.2,    3.2,    5.6,   39.2,   84.3,   98.7, 
 50000,    2.9,    4.0,    5.8,   11.7,   77.1,   99.7,  100.0, 

% successRLA, for sampleMaxPct=20: compare
       : 0.506,  0.507,  0.508,  0.510,  0.520,  0.530,  0.540, 
  1000,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0, 
  5000,    0.0,    0.0,    0.0,    0.0,   50.3,  100.0,  100.0, 
 10000,    0.0,    0.0,    0.0,    0.0,   99.8,  100.0,  100.0, 
 20000,    0.0,    0.0,    0.0,    7.3,  100.0,  100.0,  100.0, 
 50000,    0.0,    0.1,    2.7,   70.1,  100.0,  100.0,  100.0, 

RLA success difference for sampleMaxPct=20 % cutoff: (compare - polling)
cvrMean: 0.506,  0.507,  0.508,  0.510,  0.520,  0.530,  0.540, 
  1000,   0.00,  -0.01,   0.00,   0.00,  -0.27,  -1.68,  -6.56, 
  5000,  -0.26,  -0.39,  -0.64,  -0.88,  41.52,  70.04,  41.36, 
 10000,  -0.79,  -1.28,  -1.54,  -2.89,  79.27,  43.68,  14.48, 
 20000,  -1.54,  -2.15,  -3.21,   1.74,  60.83,  15.71,   1.28, 
 50000,  -2.94,  -3.97,  -3.13,  58.42,  22.95,   0.34,   0.00, 

*** eta0Factor=1.5
Success Percentage Ratio nsamples Comparison and Polling; ntrials=10000, N=10000, d=1000 eta0Factor=1.5 cvrMeanDiff=-0.005; theta (col) vs N (row)
  theta: 0.501,  0.502,  0.503,  0.505,  0.515,  0.525,  0.535, 
% successRLA, for sampleMaxPct=20: polling
       : 0.506,  0.507,  0.508,  0.510,  0.520,  0.530,  0.540, 
  1000,    0.0,    0.0,    0.0,    0.0,    0.3,    1.7,    6.8, 
  5000,    0.4,    0.5,    0.7,    1.2,    8.9,   29.8,   57.0, 
 10000,    0.9,    1.0,    1.5,    2.8,   21.0,   56.6,   84.9, 
 20000,    1.6,    2.3,    3.5,    5.8,   40.1,   84.3,   98.5, 
 50000,    2.7,    3.8,    5.9,   11.8,   76.8,   99.6,  100.0, 

% successRLA, for sampleMaxPct=20: compare
       : 0.506,  0.507,  0.508,  0.510,  0.520,  0.530,  0.540, 
  1000,    0.0,    0.0,    0.0,    0.0,    0.0,   36.3,   80.7, 
  5000,    0.0,    0.0,    0.5,    5.4,   98.2,  100.0,  100.0, 
 10000,    0.1,    1.2,    5.9,   35.5,  100.0,  100.0,  100.0, 
 20000,    0.7,    5.7,   22.4,   74.4,  100.0,  100.0,  100.0, 
 50000,    2.6,   20.5,   58.1,   98.1,  100.0,  100.0,  100.0, 

RLA success difference for sampleMaxPct=20 % cutoff: (compare - polling)
cvrMean: 0.506,  0.507,  0.508,  0.510,  0.520,  0.530,  0.540, 
  1000,   0.00,  -0.01,  -0.01,  -0.02,  -0.27,  34.51,  73.96, 
  5000,  -0.36,  -0.49,  -0.15,   4.26,  89.35,  70.25,  43.03, 
 10000,  -0.77,   0.17,   4.39,  32.75,  79.02,  43.40,  15.13, 
 20000,  -0.85,   3.37,  18.91,  68.53,  59.92,  15.70,   1.46, 
 50000,  -0.11,  16.63,  52.20,  86.23,  23.16,   0.41,   0.00, 

*** eta0Factor=1.75
Success Percentage Ratio nsamples Comparison and Polling; ntrials=10000, N=10000, d=1000 eta0Factor=1.75 cvrMeanDiff=-0.005; theta (col) vs N (row)
  theta: 0.501,  0.502,  0.503,  0.505,  0.515,  0.525,  0.535, 
% successRLA, for sampleMaxPct=20: polling
       : 0.506,  0.507,  0.508,  0.510,  0.520,  0.530,  0.540, 
  1000,    0.0,    0.0,    0.0,    0.0,    0.3,    1.7,    7.0, 
  5000,    0.4,    0.4,    0.6,    1.0,    9.2,   30.1,   57.7, 
 10000,    1.0,    1.4,    1.5,    2.9,   20.0,   56.3,   84.9, 
 20000,    1.6,    2.2,    3.0,    5.7,   40.2,   84.0,   98.5, 
 50000,    2.9,    4.2,    5.8,   11.9,   77.0,   99.7,  100.0, 

% successRLA, for sampleMaxPct=20: compare
       : 0.506,  0.507,  0.508,  0.510,  0.520,  0.530,  0.540, 
  1000,    0.0,    0.0,    0.0,    0.0,    0.0,   72.5,   95.6, 
  5000,    0.9,    4.1,    8.9,   27.1,   98.8,  100.0,  100.0, 
 10000,    3.6,   10.2,   21.8,   54.6,  100.0,  100.0,  100.0, 
 20000,    6.0,   19.1,   39.6,   82.0,  100.0,  100.0,  100.0, 
 50000,    9.3,   34.9,   67.4,   97.6,  100.0,  100.0,  100.0, 

RLA success difference for sampleMaxPct=20 % cutoff: (compare - polling)
cvrMean: 0.506,  0.507,  0.508,  0.510,  0.520,  0.530,  0.540, 
  1000,  -0.01,   0.00,  -0.01,   0.00,  -0.29,  70.79,  88.55, 
  5000,   0.48,   3.77,   8.22,  26.10,  89.56,  69.93,  42.27, 
 10000,   2.60,   8.83,  20.31,  51.64,  79.96,  43.75,  15.11, 
 20000,   4.36,  16.96,  36.65,  76.25,  59.76,  16.01,   1.54, 
 50000,   6.40,  30.68,  61.58,  85.75,  23.05,   0.33,   0.00, 
````

## Unrealistic simulations in the ALPHA paper

Ive been puzzling over why I'm seeing comparison audits do worse than polling audits, when I had understood the opposite.

I have not found any code in the SHANGRLA, ALPHA or OneAudit repositories that clearly shows an example comparison audit.
The closest is [this ipython notebook](https://github.com/pbstark/alpha/blob/main/Code/alpha.ipynb).

Under "Simulation of a comparison audit", we've got

````
  N = 10000 # ballot cards containing the contest
  assorter_mean = (9000*0.51*1 + 1000*.5)/N # contest has 51% for winner in 9000 valid votes, and 1000 non-votes
  assorter_margin = 2*assorter_mean - 1
  u = 2/(2-assorter_margin)
  etal = [0.9, 1, u, 2, 2*u]
  ..
  mart = alpha_mart(x, N, mu=1/2, eta=eta, u=u,
  estim=lambda x, N, mu, eta, u: shrink_trunc(x,N,mu,eta,u,c=c,d=d))
````
The value u is clearly the upperBounds for the alpha_mart function ,and is equal to 1.009081735.
The values of eta in etal are the initial eta0 guesses for the shrink_trunc function.
But we've already seen above that values of eta0 > upperBounds will not satisfy the risk limit.
Further, the range of values being tried here are not realistic. Realistic numbers would be
in the range of u/2 plus or minus some percent error.

Under "set up simulations" of the same notebook, we see the generation of the values in the ALPHA paper
tables 7, 8, and 9 for comparison audits. The paper describes this as

````
To assess the relative performance of these supermartingales for comparison audits, they
were applied to pseudo-random samples from nonnegative populations that had mass 0.001
at zero (corresponding to errors that overstate the margin by the maximum possible, e.g.,
that erroneously interpreted a vote for the loser as a vote for the winner), mass m ∈
{0.01, 0.1, 0.25, 0.5, 0.75, 0.9, 0.99} at 1, and the remain mass uniformly distributed on [0, 1].
````

But AFAIU, there's no realistic scenario where a large mass should be clustered at 1. The large mass should be at 1/2, 
meaning "no error" between the CVR and the MVR. 
This assumes that you are using normalized assorter values, which could be a wrong assumption for the paper.
TODO: I dont know what "normalized assorter values" look like, so Im really not sure what "clustered at 1" means.
Perhaps u=1 is a bug?

In any case, it's a funny way to simulate the errors. Here are the possible comparison assort values (aka "bassort"):

````
Possible assort values bassort ∈ [0, 1/2, 1, 3/2, 2] * noerror, where:
  0 = flipped vote from loser to winner
  1/2 = flipped vote from loser to other, or other to winner
  1 = no error
  3/2 = flipped vote from other to loser, or winner to other
  2 = flipped vote from winner to loser
where
  noerror = 1.0 / (2.0 - margin) == 1.0 / (3 - 2 * awinnerAvg), which ranges from .5 to 1.0.

If you normalize the assort valeus by dividing by noassort, the possible assort values are:
  [0, 1/4, 1/2, 3/4, 1], where
    0 = flipped vote from loser to winner
    1/4 = flipped vote from loser to other, or other to winner
    1/2 = no error
    3/4 = flipped vote from other to loser, or winner to other
    1 = flipped vote from winner to loser
````
Seems like you would use historical error rates for each of the 5 possibilities.

The code generates the samples in this way:
````
while t <= 0.5:
  x = sp.stats.uniform.rvs(size=N)
  y = sp.stats.uniform.rvs(size=N)
  x[y<=m] = 1
  x[y>=(1-zm)] = 0
  t = np.mean(x)
  thetas[m][N] = t
````
and the x array is used as input to alpha_mart like:
````
  mart = alpha_mart(x, N, mu=1/2, eta=eta, u=1,
  estim=lambda x, N, mu, eta, u: shrink_trunc(x,N,mu,eta,1,c=c,d=d))
  al[m][N][eta][d] += np.argmax(mart >= 1/alpha)
````
We also see u=1 indicating that the upper limit is 1, indicating normalized values.
If one prints out the various thetas

````
m=0.99 N=10000 theta=0.9944485501322493
m=0.9 N=10000 theta=0.9489346794038512
m=0.75 N=10000 theta=0.8709895512609641
m=0.5 N=10000 theta=0.744881901269972
m=0.25 N=10000 theta=0.6216431953908437
m=0.1 N=10000 theta=0.5526041143301229
m=0.01 N=10000 theta=0.5069034946014742
````
only the bottom 3 are realistic, and there's not enough attention to the interesting cases of
close elections.

The results for mixtures = [.25, .1, .01] and N=10000 agree reasonably well with the ALPHA table 7 and 8, for N=10000, ntrials = 10000:

````
test N=10000 m=0.25 theta=0.6261105699927882
  eta0=0.99 d=10  avgSamplesNeeded = 59
  eta0=0.99 d=100  avgSamplesNeeded = 69
  eta0=0.9 d=10  avgSamplesNeeded = 56
  eta0=0.9 d=100  avgSamplesNeeded = 52
  eta0=0.75 d=10  avgSamplesNeeded = 61
  eta0=0.75 d=100  avgSamplesNeeded = 48
  eta0=0.55 d=10  avgSamplesNeeded = 82
  eta0=0.55 d=100  avgSamplesNeeded = 100
test N=10000 m=0.1 theta=0.5432493788831303
  eta0=0.99 d=10  avgSamplesNeeded = 564
  eta0=0.99 d=100  avgSamplesNeeded = 1004
  eta0=0.9 d=10  avgSamplesNeeded = 513
  eta0=0.9 d=100  avgSamplesNeeded = 651
  eta0=0.75 d=10  avgSamplesNeeded = 491
  eta0=0.75 d=100  avgSamplesNeeded = 394
  eta0=0.55 d=10  avgSamplesNeeded = 557
  eta0=0.55 d=100  avgSamplesNeeded = 518
test N=10000 m=0.01 theta=0.5045508021773442
  eta0=0.99 d=10  avgSamplesNeeded = 8026
  eta0=0.99 d=100  avgSamplesNeeded = 9019
  eta0=0.9 d=10  avgSamplesNeeded = 7903
  eta0=0.9 d=100  avgSamplesNeeded = 8744
  eta0=0.75 d=10  avgSamplesNeeded = 7744
  eta0=0.75 d=100  avgSamplesNeeded = 7988
  eta0=0.55 d=10  avgSamplesNeeded = 7778
  eta0=0.55 d=100  avgSamplesNeeded = 7640
````
and for N=100000:
````
ntrials=10000
test N=100000 m=0.01 theta=0.5056565121104439
  eta0=0.99 d=10  avgSamplesNeeded = 29323
  eta0=0.99 d=100  avgSamplesNeeded = 56187
  eta0=0.9 d=10  avgSamplesNeeded = 26901
  eta0=0.9 d=100  avgSamplesNeeded = 45784
  eta0=0.75 d=10  avgSamplesNeeded = 24558
  eta0=0.75 d=100  avgSamplesNeeded = 31021
  eta0=0.55 d=10  avgSamplesNeeded = 23771
  eta0=0.55 d=100  avgSamplesNeeded = 22608
````
Using high values for eta0 (instead of the obvious value of using eta0 = mean assorter value of the cvrs)
is explored in the [Sample size simulations (Ballot Comparison)](#sample-size-simulations-ballot-comparison) and
[Polling vs Comparison](#polling-vs-comparison) sections above, and needs more exploration.

Is there something Im missing?

## Notes/thoughts

The larger values of eta0 do better. It seems that the eta0Factor acts as an accelerant, making each sampled value count
more towards accepting or rejecting the null hypotheses. Not clear if thats fair.

TODO: quantify the accelerant value. See AlphaMart, trunk_shrink formula. Weights earlier values more, depending on d. 

If you can accelerate comparison, can you accelerate polling?

TODO: vary by N, d, cvrMean, and cvrMeanDiff

Sample size is proportional to 1/theta, no N dependence for N > 1000. 

Note bug in ALPHA python code for alpha mart (PR submitted).

