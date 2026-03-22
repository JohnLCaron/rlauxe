# Estimation notes
_3/8/2026_

1. SubsetForEstimation (CLCA, OneAudit)

        // cardManifest may not fit into memory, so extract in-memory subset of cardManifest to use for the estimations.
        getSubsetForEstimation(
            config,
            auditRound.contestRounds,
            cardManifest,
            previousSamples,
        ): CardSamples

For each Contest, decide on nsamples using estSamplesNeeded(config: AuditConfig, contestRound: ContestRound, ncards: Int). 
Then use (simplified variant of) ConsistentSampling to choose the actual Cards to put into CardSamples.

This is the crux of the problem:

    fun estSamplesNeeded(config: AuditConfig, contestRound: ContestRound, ncards: Int) {
        val nsamples = minAssertionRound.calcNewMvrsNeeded(contest, config) // TODO NEXT
    
        // TODO underestimates when nsamples is low ?
        val stddev = .586 * nsamples - 23.85 // see https://github.com/JohnLCaron/rlauxe?tab=readme-ov-file#clca-with-errors
    
        val fac = 10
        // Approximately 95.45% / 99.73% of the data in a normal distribution falls within two / three standard deviations of the mean.
        val needed = if (stddev > 0) roundUp(nsamples + fac * stddev) else fac * nsamples
    
        // TODO using contestSampleCutoff as maximum
        var est =  min( contest.Npop, needed)
        if (config.contestSampleCutoff != null) est = min(config.contestSampleCutoff, est)
    }

* fac = 10 is ridiculous, should be fac = 3
* problem is that 
    1. stddev estimate is not good for small values of nsamples
    2. calcNewMvrsNeeded can be negetive sometime. perhaps only OneAudit using optimalBet ??


2. Estimation (CLCA and OneAudit):

Get the CardSamples one time. For OneAudit, use VunderFuzz one time to create mvr pairs, and optionally fuzz CVRs.

Run simulation for each contest and assertion.

Always use GeneralAdaptiveBetting(apriori, nphantoms)

CLCA: Optionally fuzz the CardSamples with config.simFuzzPct 
    sampler = ClcaFuzzSamplerTracker(config.simFuzzPct ?: 0.0, cardSamples, contestUA, cassorter, assertionRound.previousErrorCounts())
        (extracts cards for this contest from CardSamples).

    Set results into estimationResult.estimatedDistribution
    Set calcMvrsNeeded = assertionRound.calcNewMvrsNeeded(contestRound.contestUA, config) into estimationResult.calcNewMvrsNeeded

    Set estimationResult.simNewMvrsNeeded = when {
            (result.sampleCount.size < ntrials/2) -> calcMvrsNeeded // more than half the simulations fail
            (roundIdx == 1) -> result.findQuantile(.50) // TODO set quantile value in AuditConfig ??
            else -> result.findQuantile(config.quantile)
        }

OneAudit: Optionally fuzz the CardSamples with vunderFuzz(config.simFuzzPct), then use vunderFuzz.mvrCvrPairs is in ClcaSamplerErrorTracker
    val oaFuzzedPairs: List<Pair<AuditableCard, AuditableCard>> = vunderFuzz.mvrCvrPairs
    val sampler = ClcaSamplerErrorTracker.fromIndexList(contestUA.contest.id, oaCassorter, oaFuzzedPairs, wantIndices, assertionRound.previousErrorCounts())
        (extracts cards for this contest from CardSamples).
    
    Set results into estimationResult.estimatedDistribution
    Set calcMvrsNeeded = assertionRound.calcNewMvrsNeeded(contestRound.contestUA, config) into estimationResult.calcNewMvrsNeeded

    Set estimationResult.simNewMvrsNeeded = when {
            (result.sampleCount.size < ntrials/2) -> calcMvrsNeeded // more than half the simulations fail
            (roundIdx == 1) -> result.findQuantile(.50) // TODO value put in AuditConfig ??
            else -> result.findQuantile(config.quantile)
        }

* how can "more than half the simulations fail" ?  

  3. After Estimation (CLCA and OneAudit):

          if ((config.isClca || config.isOA ) && auditRound.roundIdx == 1 && config.simulationStrategy == SimulationStrategy.optimistic) {
              calculateSampleSizes(config, auditRound, overwrite = false)
          }

          // assertionRound.calcNewMvrsNeeded is written to     
          // assertionRound.estNewMvrs, assertionRound.estMvrs, // what is this doing, why are we doing it ??
          // estimationResult.calcNewMvrsNeeded                 // already done. not needed
          // and if (overwrite) contestRound.estNewMvrs, contestRound.maxNewEstMvrs



|               | round | apriori | nphantoms | measured | OAassortRates | use  |
|---------------|-------|---------|-----------|----------|---------------|------|
| clca subset   | 1     | x       | x         |          |               | calc |
| clca estimate | 1     | x       | x         |          |               | sim  |
| clca subset   | > 1   | ?       | ?         | x        |               | calc |
| clca estimate | > 1   | ?       | ?         | x        |               | sim  |


* calc : AssertionRound.calcNewMvrsNeeded
* sim  : simulate the audit n trials, create distribution, use kth quantile

## clca subset round 1



=================

When sampling elements without replacement from a finite population of size N
where an element occurs with probability p
(meaning N*p elements have the characteristic), "Bernoulli distribution"

the sample variance of the number of successes is 

Var(X) = n * p * (1 - p) (N - n) / (N - 1)

where (N - n) / (N - 1) is the finite population correction

N = 351540
n = 203
p = 21083 / 351540 = .059973

fpc = (N - n) / (N - 1) = .999092562
n * p * (1 - p) * fpc = 0.999092562 × (.059973) × (1 − .059973) × 203 = 11.43
stddev = 3.38

in a sample of 203, expect 12.2 +/- 3.4.
if you find more phantoms, sample size go up 

    // you could just count the damn number of phantoms you are going to see....
    // you could run a mini audit to track the phantoms and just keep adding cards until you pass the risk limit.
    // is that kosher? It might solve the "variance due to phantoms" problem.
    // maybe you should not do incremental, just start over each time ???
    // maybe its bogus to have so many phantoms - they should be undervotes....

    // corla contest 116 - margin .133, noerror is .536 (payoff 1.0685) and phantom pct is 6% (payoff .5531)
    //  (1.0684)^n * (.5531) = 1
    // n = -ln(.5531) / ln(1.0684) = 9
    // n_payoffRisk = ln (.03) ÷ ln(1.0685) = 52
    // n_payoffPhantoms = nphantoms * n
    // nphantoms = phantomPct * sampleSize
    // sampleSize = n_payoffRisk + phantomPct * sampleSize * 9
    // sampleSize = n_payoffRisk / (1 - phantomPct * 9)
    // phantomPct = .06, sampleSize = 113,
    // phantomPct = .08, sampleSize = 185,
    // phantomPct = .09, sampleSize = 274,
    // phantomPct = .10, sampleSize = 520 (!) close to the margin

Note 

    variance = n * p * (1 - p) is linear with n;  (N - n) / (N - 1) ~ 1 when n << N

    n = sampleSize = n_payoffRisk / (1 - phantomPct * 9) = 52 / (1 - p * 9) = 113 when p = .06

    phantomCount = 12.2 +/- 3.4 = 8.8 .. 15.6
    if phantomCount =  15.6, pct = 15.6/203 = 

    

