# Development
last changed: 12/02/2024

## TODO

### core
* raire library
* hybrid audits
* DONE PollingWithStyles
* PollingWithoutStyles
* the effect of adding n worst-case ballots to an audit.

### sampling
* Estimate sample sizes with fixed formula
* DONE Estimate sample sizes with simulation reps and quantile
* DONE Raire error rates
* Parallelization (?)
* SecureRandom must be deterministic using a given seed, so verifiers can test. 
  Make a version that agrees exactly with SHANGRLA's version. (3)
  BigIntegers? Strings? Maybe hex strings?

* COBRA 4.3 Diversified betting

### multiple rounds
* Serialization of intermediate stages
* Re-estimate sample sizes for all assertions, using previous MVRs. What causes sample size to get larger?

### interface
* CLI
* web server


## Clients
We need interfaces for testing. How elaborate should we make those?

* remote clients
* web?
* swing?
* arlo?
* colorado-rla?

## Questions

### N_c
What is effect of overestimating N_c, the bound on the number of cards for contest c?
If each of these gets conveerted to a 2-vote overstatement, which the sampleSize is highly sensisitive to.


### Check sample size

What about when you start an audit, then more CVRs arrive?

I _think_ its fine if more ballots come in between rounds. Just add to the "all cvrs list". Ideally N_c doesnt change,
so it just makes less evil zombies. Then the consistent sampling is just rerun.

Add feature to run the sample size estimates by contest, but not the full audit.
DONE: FindSampleSize.computeSampleSize()


### Compare alternatives

Running the workflow with other RiskTestingFn, BettingFn, etc.


### Databases

If everything is stored in the database, then you have to trust the database software. If you have some kind of BB
with the raw data that anyone caan read, then you can have 3rd party verifiers. Otherwise what do you have?
A PostGres DB that you have to trust.

Theres not a problem using a DB, but you should be able to recreate it from the raw data.

OTOH the RLA should detect a compromised DB?

## Classes

### core
Samples       // keeps track of the latest sample, number of samples, and the sample sum.

### sampling
ComparisonSamplerSimulation // create internal cvr and mvr with the correct under/over statements. specific to a contest. only used for estimating the sample size
ConstistentSampling
EstimateSampleSize
FuzzSampler           // this takes a list of cvrs and fuzzes them. Version for Sampling and Polling. 
                      // fun makeFuzzedCvrsFrom(contests: List<Contest>, cvrs: List<Cvr>, fuzzPct: Double): List<Cvr> {
MultiContestTestData  // creates a set of contests and ballotStyles, with randomly chosen candidates and margins. create cvrs that reflect the contests' exact votes.
SampleGenerator           // abstraction for creating a sequence of samples. mostly superceded, mostly for testing.