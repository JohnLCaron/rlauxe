# Simulations
last update: 09/20/2024

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

See [output](docs/DiffMeanOutput.txt) of DiffMeans.kt and PlotDiffMeans.kt. This is done for each value of
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

Tables 7 and 8 in ALPHA, showing comparison ballot results, switch from using theta to using "mass at 1". Which is
not very clear (see [section](#unrealistic-simulations-in-the-alpha-paper) below).
Also, the very small sample sizes in Table 7 of ALPHA are surprising. Really need only 5 ballots out of 500,000 
to reject the null hypothosis?

However, see TestComparisonFromAlpha.kt; setting eta0 very high brings the numbers down such that comparison is much better
than polling, and can deal even with very small margins:

````
nsamples, ballot comparison, N=10000, d = 100, error-free; theta (col) vs eta0 (row)
      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
 0.900,   9955,   9766,   9336,   8571,   7461,   2257,    464,    221,    140,    101,     59,     41,     25,     18,
 1.000,   9951,   9718,   9115,   7957,   6336,   1400,    314,    159,    104,     77,     46,     32,     20,     14,
 1.500,   9916,   9014,   5954,   3189,   1827,    418,    153,     98,     74,     59,     39,     29,     19,     14,
 2.000,   9825,   6722,   2923,   1498,    937,    309,    148,     98,     74,     59,     39,     29,     19,     14,
 5.000,   5173,   1620,    962,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
 7.500,   3310,   1393,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
10.000,   2765,   1390,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
15.000,   2588,   1390,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
20.000,   2588,   1390,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
````

The idea of setting eta0 very high seems suspect. Examining the number of success vs failures shows that you cant set eta0 higher
than the upper limit of the comparison assorter, or else you don't detect when theta <= .5 (the null hypothosis is true), as the following shows.

In the plots below, we simulate the reported mean of the cvrs as exactly 0.5% higher than theta (the true mean). 
So for example when the reported cvr mean assort (aka Āc in SHANGRLA section 3.2) is 0.501, theta is .501 - .005 = 0.496.
The number of successes when theta <= .5 are all false positives, and by contract the successPct must be < 5% = risk factor. 
We show theta vs various values of _eta0Factor_, where eta0 = eta0Factor * noerrors, and _noerrors_ is the comparison 
assort value when the cvrs and the mvrs agree exactly (all overstatement errors are 0). 
The value noerrors is a simple function of Āc: _noerrors = 1.0 / (3 - 2 * Āc)_.

In the table below, the tables use eta0 = eta0Factor * noerrors.
The upper bounds of the comparison assorter is 2 * noerrors, represented by the row with etaFactor = 2.0.

Using eta0 == noerrors, there are no false positives. When eta0Factor is between 1.0 and 2.0, the false positives are
(mostly) all less than 5%. When eta0 >= 2.0 * noerrors, the algorithm no longer stays within the risk limit.

````
Comparison ntrials=1000, N=10000, d=10000 cvrMeanDiff=-0.005; theta (col) vs etaFactor (row)
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

Going back to N vs theta plots, for various values of eta0Factor, we want to see the influence of eta0Factor.
(The value of d also matters, but for now, we set it high which tends to exaggerate the effect of eta0Factor).
Here are the ratio of the number of samples / min number of samples, as well as the geometric average:

````
samplePct ratios across eta0Factor: ntrials=1000, d = 10000, cvrMeanDiff=-0.005, theta(col) vs N(row)

ratio eta0Factor=1.0,  theta(col) vs N(row)
cvrMean: 0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700, 
  1000,  1.468,  3.336,  4.965,  5.994,  6.708,  6.874,  6.767,  5.486,  5.023, 
  5000,  3.395, 10.264, 12.493, 12.662, 11.789,  9.500,  8.179,  6.350,  5.414, 
 10000,  5.406, 15.133, 16.062, 14.679, 13.174, 10.177,  8.356,  6.141,  5.434, 
 20000,  9.454, 20.869, 18.711, 15.999, 14.270, 10.417,  8.312,  6.405,  5.577, 
 50000, 17.334, 25.519, 20.855, 16.968, 14.488, 10.579,  8.307,  6.842,  5.405, 
geometric mean = 8.789756163050848

ratio eta0Factor=1.25,  theta(col) vs N(row)
cvrMean: 0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700, 
  1000,  1.343,  1.930,  2.144,  2.229,  2.307,  2.285,  2.362,  2.163,  2.178, 
  5000,  1.514,  2.153,  2.306,  2.420,  2.423,  2.350,  2.362,  2.257,  2.249, 
 10000,  1.481,  2.207,  2.365,  2.395,  2.448,  2.380,  2.340,  2.187,  2.233, 
 20000,  1.570,  2.299,  2.423,  2.420,  2.512,  2.402,  2.300,  2.250,  2.281, 
 50000,  1.577,  2.269,  2.399,  2.441,  2.455,  2.383,  2.281,  2.397,  2.192, 
geometric mean = 2.1980639614241024

ratio eta0Factor=1.5,  theta(col) vs N(row)
cvrMean: 0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700, 
  1000,  1.101,  1.255,  1.313,  1.323,  1.360,  1.365,  1.426,  1.362,  1.444, 
  5000,  1.000,  1.246,  1.324,  1.372,  1.370,  1.363,  1.424,  1.398,  1.443, 
 10000,  1.000,  1.235,  1.313,  1.366,  1.375,  1.376,  1.399,  1.341,  1.438, 
 20000,  1.000,  1.271,  1.337,  1.354,  1.396,  1.383,  1.366,  1.374,  1.475, 
 50000,  1.000,  1.278,  1.317,  1.356,  1.376,  1.388,  1.356,  1.504,  1.411, 
geometric mean = 1.3196811521015344

ratio eta0Factor=1.75,  theta(col) vs N(row)
cvrMean: 0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700, 
  1000,  1.000,  1.000,  1.000,  1.000,  1.000,  1.000,  1.056,  1.035,  1.072, 
  5000,  1.036,  1.000,  1.000,  1.000,  1.000,  1.000,  1.028,  1.066,  1.078, 
 10000,  1.009,  1.000,  1.000,  1.000,  1.000,  1.000,  1.016,  1.000,  1.083, 
 20000,  1.169,  1.000,  1.000,  1.000,  1.000,  1.000,  1.000,  1.031,  1.099, 
 50000,  1.149,  1.000,  1.000,  1.000,  1.000,  1.000,  1.000,  1.114,  1.050, 
geometric mean = 1.0234372204868512

ratio eta0Factor=1.9999999999999998,  theta(col) vs N(row)
cvrMean: 0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700, 
  1000,  1.163,  1.363,  1.248,  1.134,  1.162,  1.043,  1.000,  1.000,  1.000, 
  5000,  1.944,  1.825,  1.554,  1.369,  1.254,  1.076,  1.000,  1.000,  1.000, 
 10000,  2.459,  2.193,  1.584,  1.433,  1.237,  1.066,  1.000,  1.001,  1.000, 
 20000,  3.506,  2.291,  1.768,  1.417,  1.260,  1.083,  1.021,  1.000,  1.000, 
 50000,  3.962,  2.361,  1.769,  1.427,  1.295,  1.092,  1.021,  1.000,  1.000, 
geometric mean = 1.3339424034953913
````

The larger values of eta0Factor do better, and eta0Factor=1 is sometimes 20x worse than the smallest amount.
More studies are needed varying across d, cvrMeanDiff, etc.

It seems that the eta0Factor acts as an accelerant, making each sampled value count
more towards accepting or rejecting the null hypotheses. Not clear if that's a fair thing to do.

The above results show average number of samples needed and as a percentage of N. This ignores the large variance in the distribution.
What we really want to know is what percentage of trials succeed at various cut-off values (where a full hand count will be more
efficient than sampling a large percentage of the ballots.)

A more germane simulation is to keep a histogram of the percentage "successful RLA", as deciles. Below are the
percentage "successful RLA" (higher is better) for audits with sampling cutoffs at 10, 20, 30, 40, and 50% of N.
The reported cvr means have been adjusted to show details around close elections.

````
Comparison ntrials=1000, N=10000, d=10000 cvrMeanDiff=-0.005; theta (col) vs etaFactor (row)
cvrMean: 0.506,  0.507,  0.508,  0.509,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,

% successRLA, for sampleMaxPct=10: 
  theta: 0.501,  0.502,  0.503,  0.504,  0.505,  0.515,  0.525,  0.535,  0.545,  0.570, 
  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,  100.0, 
  1.25,    0.0,    0.0,    0.0,    0.0,    0.0,   80.0,  100.0,  100.0,  100.0,  100.0, 
  1.50,    0.0,    0.7,    3.2,    7.3,   22.5,   98.5,  100.0,  100.0,  100.0,  100.0, 
  1.75,    3.7,    9.9,   13.9,   26.5,   32.9,   97.6,   99.9,  100.0,  100.0,  100.0, 
  1.99,    7.1,   11.4,   16.2,   21.7,   25.3,   77.2,   96.1,   99.8,  100.0,  100.0, 

% successRLA, for sampleMaxPct=20: 
  theta: 0.501,  0.502,  0.503,  0.504,  0.505,  0.515,  0.525,  0.535,  0.545,  0.570, 
  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,   77.8,  100.0,  100.0, 
  1.25,    0.0,    0.2,    0.9,    6.8,   18.6,  100.0,  100.0,  100.0,  100.0,  100.0, 
  1.50,    5.2,   14.7,   29.3,   48.0,   68.7,  100.0,  100.0,  100.0,  100.0,  100.0, 
  1.75,    9.7,   21.0,   31.9,   47.0,   64.8,  100.0,  100.0,  100.0,  100.0,  100.0, 
  1.99,    7.9,   13.7,   19.2,   25.8,   30.6,   95.3,  100.0,  100.0,  100.0,  100.0, 

% successRLA, for sampleMaxPct=30: 
  theta: 0.501,  0.502,  0.503,  0.504,  0.505,  0.515,  0.525,  0.535,  0.545,  0.570, 
  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,   20.9,  100.0,  100.0,  100.0, 
  1.25,    0.2,    2.8,   13.6,   43.4,   70.7,  100.0,  100.0,  100.0,  100.0,  100.0, 
  1.50,    9.4,   28.1,   49.3,   76.1,   90.5,  100.0,  100.0,  100.0,  100.0,  100.0, 
  1.75,   11.3,   27.1,   43.7,   62.0,   79.9,  100.0,  100.0,  100.0,  100.0,  100.0, 
  1.99,    8.1,   14.1,   20.8,   28.4,   36.0,   99.9,  100.0,  100.0,  100.0,  100.0, 

% successRLA, for sampleMaxPct=40: 
  theta: 0.501,  0.502,  0.503,  0.504,  0.505,  0.515,  0.525,  0.535,  0.545,  0.570, 
  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,  100.0,  100.0,  100.0,  100.0, 
  1.25,    1.2,   12.9,   41.4,   79.8,   95.5,  100.0,  100.0,  100.0,  100.0,  100.0, 
  1.50,   13.7,   41.6,   66.3,   91.6,   98.8,  100.0,  100.0,  100.0,  100.0,  100.0, 
  1.75,   13.5,   31.4,   53.3,   77.0,   91.4,  100.0,  100.0,  100.0,  100.0,  100.0, 
  1.99,    8.2,   14.6,   22.0,   31.5,   41.7,  100.0,  100.0,  100.0,  100.0,  100.0, 

% successRLA, for sampleMaxPct=50: 
  theta: 0.501,  0.502,  0.503,  0.504,  0.505,  0.515,  0.525,  0.535,  0.545,  0.570, 
  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,    0.5,  100.0,  100.0,  100.0,  100.0, 
  1.25,    3.0,   28.3,   70.6,   96.4,   99.9,  100.0,  100.0,  100.0,  100.0,  100.0, 
  1.50,   17.6,   53.5,   83.0,   97.9,   99.9,  100.0,  100.0,  100.0,  100.0,  100.0, 
  1.75,   15.2,   36.5,   64.6,   88.3,   98.7,  100.0,  100.0,  100.0,  100.0,  100.0, 
  1.99,    8.3,   14.7,   23.5,   37.7,   51.5,  100.0,  100.0,  100.0,  100.0,  100.0, 

````
We can combine these plots with the false positives to get a sense of both successes and failures.
Below, false positives are shown as negative "successRLA".

````
Comparison ntrials=1000, N=10000, d=10000 cvrMeanDiff=-0.005; theta (col) vs etaFactor (row)
cvrMean: 0.501,  0.502,  0.503,  0.504,  0.505,  0.506,  0.508,  0.510,  0.520,  0.530,  0.540, 

% successRLA, for sampleMaxPct=10: 
  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.501,  0.503,  0.505,  0.515,  0.525,  0.535, 
  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0, 
  1.25,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,   81.6,  100.0,  100.0, 
  1.50,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    2.7,   21.3,   98.3,  100.0,  100.0, 
  1.75,    0.0,    0.0,    0.0,    0.0,   -1.5,    2.9,   14.8,   30.8,   96.7,  100.0,  100.0, 
  1.99,    0.0,    0.0,    0.0,   -2.0,   -4.7,    8.4,   19.1,   27.5,   76.7,   97.0,   99.6, 

% successRLA, for sampleMaxPct=20: 
  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.501,  0.503,  0.505,  0.515,  0.525,  0.535, 
  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,   77.2, 
  1.25,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.8,   18.1,  100.0,  100.0,  100.0, 
  1.50,    0.0,    0.0,    0.0,    0.0,   -0.6,    4.6,   26.2,   67.7,  100.0,  100.0,  100.0, 
  1.75,    0.0,    0.0,    0.0,   -1.7,   -3.1,    8.1,   33.3,   62.7,  100.0,  100.0,  100.0, 
  1.99,    0.0,   -0.1,   -0.5,   -2.1,   -5.5,    9.3,   21.2,   33.2,   94.9,  100.0,  100.0, 

% successRLA, for sampleMaxPct=30: 
  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.501,  0.503,  0.505,  0.515,  0.525,  0.535, 
  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,   21.7,  100.0, 
  1.25,    0.0,    0.0,    0.0,    0.0,    0.0,    0.3,   14.6,   73.2,  100.0,  100.0,  100.0, 
  1.50,    0.0,    0.0,    0.0,   -0.2,   -1.1,    8.6,   46.1,   91.7,  100.0,  100.0,  100.0, 
  1.75,    0.0,    0.0,    0.0,   -1.9,   -3.8,   10.9,   43.9,   79.9,  100.0,  100.0,  100.0, 
  1.99,    0.0,   -0.1,   -0.5,   -2.2,   -5.5,    9.4,   22.6,   38.3,   99.6,  100.0,  100.0, 

% successRLA, for sampleMaxPct=40: 
  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.501,  0.503,  0.505,  0.515,  0.525,  0.535, 
  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,  100.0,  100.0, 
  1.25,    0.0,    0.0,    0.0,    0.0,    0.0,    1.1,   42.9,   94.2,  100.0,  100.0,  100.0, 
  1.50,    0.0,    0.0,    0.0,   -0.3,   -2.0,   11.8,   65.7,   98.5,  100.0,  100.0,  100.0, 
  1.75,    0.0,    0.0,    0.0,   -1.9,   -4.2,   12.4,   55.1,   91.6,  100.0,  100.0,  100.0, 
  1.99,    0.0,   -0.1,   -0.5,   -2.2,   -5.6,    9.4,   24.1,   45.3,  100.0,  100.0,  100.0, 

% successRLA, for sampleMaxPct=50: 
  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.501,  0.503,  0.505,  0.515,  0.525,  0.535, 
  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,    0.5,  100.0,  100.0, 
  1.25,    0.0,    0.0,    0.0,    0.0,    0.0,    3.3,   72.9,  100.0,  100.0,  100.0,  100.0, 
  1.50,    0.0,    0.0,    0.0,   -0.3,   -2.7,   15.5,   83.6,  100.0,  100.0,  100.0,  100.0, 
  1.75,    0.0,    0.0,    0.0,   -1.9,   -4.2,   14.5,   67.1,   98.1,  100.0,  100.0,  100.0, 
  1.99,    0.0,   -0.1,   -0.5,   -2.2,   -5.6,    9.4,   26.5,   56.3,  100.0,  100.0,  100.0, 

% successRLA, for sampleMaxPct=100: 
  theta: 0.496,  0.497,  0.498,  0.499,  0.500,  0.501,  0.503,  0.505,  0.515,  0.525,  0.535, 
  1.00,    0.0,    0.0,    0.0,    0.0,    0.0,    0.0,  100.0,  100.0,  100.0,  100.0,  100.0, 
  1.25,    0.0,    0.0,    0.0,    0.0,   -0.6,   99.8,  100.0,  100.0,  100.0,  100.0,  100.0, 
  1.50,    0.0,    0.0,    0.0,   -0.3,   -3.7,   99.2,  100.0,  100.0,  100.0,  100.0,  100.0, 
  1.75,    0.0,    0.0,    0.0,   -1.9,   -4.4,   89.8,  100.0,  100.0,  100.0,  100.0,  100.0, 
  1.99,    0.0,   -0.1,   -0.5,   -2.2,   -5.6,   20.8,  100.0,  100.0,  100.0,  100.0,  100.0, 
````

The false positives are partially mitigated when taking the sample cutoff into account.

There's a dramatic increase in RLA success going from etaFactor = 1 to 1.25, and perhaps 1.5 is a good choice dor
these values of N, d, cvrMean, and cvrMeanDiff.

## Polling vs Comparison

Here is the RLA success rate for a 20% cutoff for Polling and CVR Comparison when eta0Factor=1.0.

````
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

RLA success difference for sampleMaxPct=20 % cutoff: (compare - polling)
cvrMean: 0.506,  0.507,  0.508,  0.510,  0.520,  0.530,  0.540, 
  1000,  -0.01,  -0.02,   0.00,  -0.01,  -0.29,  -1.63,  -6.69, 
  5000,  -0.31,  -0.40,  -0.72,  -1.08,  -9.04, -30.62, -57.57, 
 10000,  -0.72,  -1.01,  -1.59,  -2.70, -20.81, -55.78, -19.40, 
 20000,  -1.42,  -1.97,  -3.13,  -5.74, -41.10,  -8.41,   1.35, 
 50000,  -2.95,  -4.15,  -5.71, -11.51, -53.10,   0.34,   0.00, 
````
CVR compare is quite a bit worse than polling when eta0Factor=1.0.

However, it's much better when eta0Factor > 1:
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

IS there something Im missing?

## Notes/thoughts

The larger values of eta0 do better. It seems that the eta0Factor acts as an accelerant, making each sampled value count
more towards accepting or rejecting the null hypotheses. Not clear if thats fair.

TODO: quantify the accelerant value. See AlphaMart, trunk_shrink formula. Weights earlier values more, depending on d. 

If you can accelerate comparison, can you accelerate polling?

TODO: vary by N, d, cvrMean, and cvrMeanDiff

Sample size is proportional to 1/theta, no N dependence for N > 1000. 

Note bug in ALPHA python code for alpha mart.

