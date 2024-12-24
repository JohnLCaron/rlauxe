# Development
last changed: 12/24/2024

## TODO

### core
* raire library
* hybrid audits
* the effect of adding n worst-case ballots to an audit.

### sampling
* Estimate sample sizes with fixed formula
* DONE Raire error rates
* Parallelization (?)
* SecureRandom must be deterministic using a given seed, so verifiers can test. 
  Make a version that agrees exactly with SHANGRLA's version. (3)
  BigIntegers? Strings? Maybe hex strings?
* COBRA 4.3 Diversified betting

### plots
* cobra/GenAdaptiveComparison
* sampling/GenPollingDvalues
* sampling/GenPollingNoStyles
* sampling/GenSampleSizeEstimates

### multiple rounds
* Serialization of intermediate stages

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

ConsistentSampling    // implement consistent or uniform sampling
EstimateSampleSize    / estimates sample size; polling or comparison, with/out styles

ComparisonSimulation // create internal cvr and mvr with the correct under/over statements based on the error rates.
                     // specific to a contest. only used for estimating the sample size

PollingFuzzSampler, ComparisonFuzzSampler           // this takes a list of cvrs and fuzzes them. New fuzz each reset.
                      // fun makeFuzzedCvrsFrom(contests: List<Contest>, cvrs: List<Cvr>, fuzzPct: Double): List<Cvr> 

MultiContestTestData  // creates a set of contests and ballotStyles, with randomly chosen candidates and margins. create cvrs that reflect the contests' exact votes.
PollingSimulation

SampleGenerator       // abstraction for creating a sequence of samples. 
PollWithoutReplacement
ComparisonWithoutReplacement

### plots module
betting/GenBettingPayoff
sampling/GenerateComparisonErrorTable  
sampling/PlotPollingNoStyles  
sampling/PlotSampleSizeEstimates 

## Notes

### Polling

        val test = MultiContestTestData(11, 4, N, marginRange= 0.04..0.10)
        val contests: List<Contest> = test.makeContests()
        // Synthetic cvrs for testing reflecting the exact contest votes. In practice, we dont actually have the cvrs.
        val testCvrs = test.makeCvrsFromContests()
        val ballots = test.makeBallotsForPolling()
        // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
        val testMvrs: List<Cvr> = makeFuzzedCvrsFrom(contests, testCvrs, auditConfig.fuzzPct!!)

estimateSampleSizes
    simulateSampleSizePollingAssorter
        val simContest = PollingSimulation(contestUA.contest as Contest, assorter)
        val cvrs = simContest.makeCvrs()
        val sampler = if (auditConfig.fuzzPct == null) {
            PollWithoutReplacement(contestUA, cvrs, assorter) // use as is
        } else {
            PollingFuzzSampler(auditConfig.fuzzPct, cvrs, contestUA, assorter) // fuzz them
        }

consistentPollingSampling or uniformPollingSampling

runAudit
    val sampler = PollWithoutReplacement(contestUA, mvrs, assorter, allowReset=false)


### Comparison

        val testData = MultiContestTestData(11, 4, N)
        val contests: List<Contest> = testData.makeContests()
        // Synthetic cvrs for testing reflecting the exact contest votes.
        val cvrs = testData.makeCvrsFromContests()
        // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
        val fuzzedMvrs: List<Cvr> = makeFuzzedCvrsFrom(contests, cvrs, auditConfig.fuzzPct!!)

estimateSampleSizes
    simulateSampleSizeComparisonAssorter
        val sampler = if (auditConfig.fuzzPct == null) {
            // TODO always using the ComparisonErrorRates derived from fuzzPct. should have the option to use ones chosen by the user.
            val errorRates = ComparisonErrorRates.getErrorRates(contestUA.ncandidates, auditConfig.fuzzPct)
            // ComparisonSimulation carefully adds that number of errors. So simulation has that error in it.
            ComparisonSimulation(cvrs, contestUA, cassorter, errorRates)
        } else {
            ComparisonFuzzSampler(auditConfig.fuzzPct, cvrs, contestUA, cassorter)
        }

consistentCvrSampling

runAudit
    runOneAssertionAudit
        val sampler = ComparisonWithoutReplacement(contestUA, cvrPairs, assorter, allowReset = false)

        // TODO always using the ComparisonErrorRates derived from fuzzPct. should have the option to use ones chosen by the user.
        val errorRates = ComparisonErrorRates.getErrorRates(contestUA.ncandidates, auditConfig.fuzzPct)
        val optimal = AdaptiveComparison(errorRates)