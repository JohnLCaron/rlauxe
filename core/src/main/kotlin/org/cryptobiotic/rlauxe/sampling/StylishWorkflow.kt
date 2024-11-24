package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.util.*
import java.util.concurrent.TimeUnit

private val showQuantiles = false

// STYLISH 2.1
//Card-level Comparison Audits and Card-Style Data

// 1. Set up the audit
//	a) Read contest descriptors, candidate names, social choice functions, and reported winners.
//     Read upper bounds on the number of cards that contain each contest:
//	     Let 𝑁_𝑐 denote the upper bound on the number of cards that contain contest 𝑐, 𝑐 = 1, . . . , 𝐶.
//	b) Read audit parameters (risk limit for each contest, risk-measuring function to use for each contest,
//	   assumptions about errors for computing initial sample sizes), and seed for pseudo-random sampling.
//	c) Read ballot manifest.
//	d) Read CVRs.
class StylishWorkflow(
    contests: List<Contest>, // the contests you want to audit
    raireContests: List<RaireContestUnderAudit>, // TODO or call raire from here ??
    val auditConfig: AuditConfig,
    val cvrs: List<Cvr>,
    // val upperBounds: Map<Int, Int>, // 𝑁_𝑐.
) {
    val contestsUA: List<ContestUnderAudit>
    val cvrsUA: List<CvrUnderAudit>
    val prng = Prng(auditConfig.seed)

    init {
        // 2. Pre-processing and consistency checks
        // 	a) Check that the winners according to the CVRs are the reported winners.
        //	b) If there are more CVRs that contain any contest than the upper bound on the number of cards that contain the contest, stop: something is seriously wrong.
        //	c) If the upper bound on the number of cards that contain any contest is greater than the number of CVRs that contain the contest, create a corresponding set
        //	    of “phantom” CVRs as described in section 3.4 of [St20]. The phantom CVRs are generated separately for each contest: each phantom card contains only one contest.
        //	d) If the upper bound 𝑁_𝑐 on the number of cards that contain contest 𝑐 is greater than the number of physical cards whose locations are known,
        //     create enough “phantom” cards to make up the difference. TODO diff between c) and d) ?
        contestsUA = tabulateVotes(contests, cvrs) + tabulateRaireVotes(raireContests, cvrs)
        contestsUA.forEach {
            // it.Nc = upperBounds[it.contest.id]!!
            //	2.b) If there are more CVRs that contain the contest than the upper bound, something is seriously wrong.
            if (it.Nc < it.ncvrs) throw RuntimeException(
                "upperBound ${it.Nc} < ncvrs ${it.ncvrs} for contest ${it.contest.id}"
            )
        }

        // 3.c) Assign independent uniform pseudo-random numbers to CVRs that contain one or more contests under audit
        //      (including “phantom” CVRs), using a high-quality PRNG [OS19].
        val phantomCVRs = makePhantomCvrs(contestsUA, "phantom-", prng)
        cvrsUA = cvrs.map { CvrUnderAudit(it, false, prng.next()) } + phantomCVRs

        // 3. Prepare for sampling
        //	a) Generate a set of SHANGRLA [St20] assertions A_𝑐 for every contest 𝑐 under audit.
        //	b) Initialize A ← ∪ A_𝑐, c=1..C and C ← {1, . . . , 𝐶}. (Keep track of what assertions are proved)
        contestsUA.forEach { contest ->
            contest.makeComparisonAssertions(cvrsUA)
        }
    }

    // 4. Main audit loop. While A is not empty:
    //   chooseSamples()
    //   runAudit()

    /**
     * Choose lists of ballots to sample.
     * @parameter mvrs: use existing mvrs to estimate samples. may be empty.
     */
    fun chooseSamples(mvrs: List<CvrIF>, round: Int): List<Int> {
        // set contestUA.sampleSize
        contestsUA.forEach { it.sampleThreshold = 0L } // need to reset this each round
        val maxContestSize = simulateSampleSizes(auditConfig, contestsUA, cvrsUA, mvrs, round)

        // TODO should we know max sampling percent? or is it an absolute number?
        //   should there be a minimum increment?? esp if its going to end up hand-counted?
        //   user should be able to force a total count size.

        //	c) Choose thresholds {𝑡_𝑐} 𝑐 ∈ C so that 𝑆_𝑐 ballot cards containing contest 𝑐 have a sample number 𝑢_𝑖 less than or equal to 𝑡_𝑐 .
        // draws random ballots by consistent sampling, and returns their locations to the auditors.
        val samples = consistentCvrSampling(contestsUA, cvrsUA)
        println(" maxContestSize=$maxContestSize consistentSamplingSize= ${samples.size}")
        return samples// set contestUA.sampleThreshold
    }

    //   The auditors retrieve the indicated cards, manually read the votes from those cards, and input the MVRs
    fun runAudit(sampleIndices: List<Int>, mvrs: List<CvrIF>): Boolean {
        //4.d) Retrieve any of the corresponding ballot cards that have not yet been audited and inspect them manually to generate MVRs.
        // 	e) Import the MVRs.
        //	f) For each MVR 𝑖:
        //		For each 𝑐 ∈ C:
        //			If 𝑢_𝑖 ≤ 𝑡_𝑐 , then for each 𝑎 ∈ A 𝑐 ∩ A:
        //				• If the 𝑖th CVR is a phantom, define 𝑎(CVR𝑖 ) := 1/2.
        //				• If card 𝑖 cannot be found or if it is a phantom, define 𝑎(MVR𝑖 ) := 0.
        //				• Find the overstatement of assertion 𝑎 for CVR 𝑖, 𝑎(CVR𝑖 ) − 𝑎(MVR𝑖 ).
        //	g) Use the overstatement data from the previous step to update the measured risk for every assertion 𝑎 ∈ A.

        val sampledCvrs = sampleIndices.map { cvrsUA[it] }
        val useMvrs = if (mvrs.isEmpty()) sampledCvrs else mvrs

        // prove that sampledCvrs correspond to mvrs
        require(sampledCvrs.size == useMvrs.size)
        val cvrPairs: List<Pair<CvrIF, CvrUnderAudit>> = useMvrs.zip(sampledCvrs)
        cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id) }

        // TODO could parellelize across assertions
        var allDone = true
        contestsUA.forEach { contestUA ->
            contestUA.comparisonAssertions.forEach { assertion ->
                if (!assertion.proved) {
                    val done = runOneAssertionAudit(auditConfig, contestUA, assertion, cvrPairs)
                    allDone = allDone && done
                    simulateSampleSize(auditConfig, contestUA, assertion, cvrPairs)
                    // println()
                }
            }
        }
        return allDone
    }
}

///////////////////////////////////////////////////////////////////////

// tabulate votes, make sure of correct winners, count ncvrs for each contest, create ContestUnderAudit
fun tabulateVotes(contests: List<Contest>, cvrs: List<CvrIF>): List<ContestUnderAudit> {
    if (contests.isEmpty()) return emptyList()

    val allVotes = mutableMapOf<Int, MutableMap<Int, Int>>()
    val ncvrs = mutableMapOf<Int, Int>()
    for (cvr in cvrs) {
        for ((conId, conVotes) in cvr.votes) {
            val accumVotes = allVotes.getOrPut(conId) { mutableMapOf() }
            for (cand in conVotes) {
                val accum = accumVotes.getOrPut(cand) { 0 }
                accumVotes[cand] = accum + 1
            }
        }
        for (conId in cvr.votes.keys) {
            val accum = ncvrs.getOrPut(conId) { 0 }
            ncvrs[conId] = accum + 1
        }
    }
    return allVotes.keys.map { conId ->
        val contest = contests.find { it.id == conId }
        if (contest == null) throw RuntimeException("no contest for contest id= $conId")
        val nc = ncvrs[conId]!!
        val accumVotes = allVotes[conId]!!
        checkWinners(contest, accumVotes)
        ContestUnderAudit(contest, nc)// nc vs ncvrs ??
    }
}

fun tabulateRaireVotes(contests: List<RaireContestUnderAudit>, cvrs: List<CvrIF>): List<ContestUnderAudit> {
    if (contests.isEmpty()) return emptyList()

    val allVotes = mutableMapOf<Int, MutableMap<Int, Int>>()
    val ncvrs = mutableMapOf<Int, Int>()
    for (cvr in cvrs) {
        for ((conId, conVotes) in cvr.votes) {
            val accumVotes = allVotes.getOrPut(conId) { mutableMapOf() }
            for (cand in conVotes) {
                val accum = accumVotes.getOrPut(cand) { 0 }
                accumVotes[cand] = accum + 1
            }
        }
        for (conId in cvr.votes.keys) {
            val accum = ncvrs.getOrPut(conId) { 0 }
            ncvrs[conId] = accum + 1
        }
    }
    return allVotes.keys.map { conId ->
        val contest = contests.find { it.id == conId }
        if (contest == null) throw RuntimeException("no contest for contest id= $conId")
        val nc = ncvrs[conId]!!
        val accumVotes = allVotes[conId]!!
        checkWinners(contest.contest, accumVotes)
        contest.ncvrs = nc
        contest
    }
}

// 2.a) Check that the winners according to the CVRs are the reported winners on the Contest.
fun checkWinners(contest: Contest, accumVotes: Map<Int, Int>): Boolean {
    val sortedMap: List<Map.Entry<Int, Int>> = accumVotes.entries.sortedByDescending { it.value }
    val nwinners = contest.winners.size
    contest.winners.forEach { candidate -> // TODO clumsy
        val s = sortedMap.find { it.key == candidate }
        val si = sortedMap.indexOf(s)
        if (si < 0) throw RuntimeException("contest winner= ${candidate} not found in cvrs")
        if (si >= nwinners) throw RuntimeException("wrong contest winners= ${contest.winners}")
    }
    return true
}

// TODO somehow est is much higher than actual
// TODO what forces this to a higher count on subsequent rounds ?? the overstatements in the mvrs ??
fun simulateSampleSizes(auditConfig: AuditConfig, contestsUA: List<ContestUnderAudit>, cvrs: List<CvrUnderAudit>, mvrs: List<CvrIF>, round: Int): Int {
    val stopwatch = Stopwatch()
    // TODO could parellelize
    val finder = FindSampleSize(auditConfig)
    contestsUA.forEach { contestUA ->
        val sampleSizes = mutableListOf<Int>()
        contestUA.comparisonAssertions.map { assert ->
            if (!assert.proved) {
                val result = finder.simulateSampleSize(contestUA, assert.assorter, cvrs,)
                if (showQuantiles) {
                    print("   quantiles: ")
                    repeat(9) {
                        val quantile = .1 * (1 + it)
                        print("${df(quantile)} = ${result.findQuantile(quantile)}, ")
                    }
                    println()
                }
                val size = result.findQuantile(auditConfig.quantile)
                assert.samplesEst = size + round * 100  // TODO how to increase sample size ??
                sampleSizes.add(assert.samplesEst)
                println("simulateSampleSizes at ${100*auditConfig.quantile}% quantile: ${assert} took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms")
                // println("  errorRates % = [${result.errorRates()}]")
            }
        }
        contestUA.sampleSize = sampleSizes.max()
    }

    // AFAICT, the calculation of the total_size using the probabilities as described in 4.b) is when you just want the
    // total_size estimate, but not do the consistent sampling.
    //val computeSize = finder.computeSampleSize(contestsUA, cvrs)
    //println(" computeSize=$computeSize consistentSamplingSize= ${samples.size}")

    return contestsUA.map { it.sampleSize }.max()
}

/////////////////////////////////////////////////////////////////////////////////
// run audit for one assertion; could be parallel
// TODO could pass the testFn into the workflow
// note that you need the assorter for the sampler
// need the upperBound and noerror for the AdaptiveComparison bettingFn
// the testFn is independent, it assumes drawSample already does the assort.

fun runOneAssertionAudit(
    auditConfig: AuditConfig,
    contestUA: ContestUnderAudit,
    assertion: ComparisonAssertion,
    cvrPairs: List<Pair<CvrIF, CvrUnderAudit>>, // (mvr, cvr)
): Boolean {
    val assorter = assertion.assorter

    val samples = PrevSamplesWithRates(assorter.noerror)
    cvrPairs.forEach { (mvr, cvr) -> samples.addSample(assorter.bassort(mvr,cvr)) }
    println("runOneAssertionAudit ${assorter.name()} samplingErrors= ${samples.samplingErrors()}")

    val sampler: SampleFn = ComparisonSampler(cvrPairs, contestUA, assorter)

    val optimal = AdaptiveComparison(
        Nc = contestUA.Nc,
        withoutReplacement = true,
        a = assorter.noerror,
        d1 = auditConfig.d1,
        d2 = auditConfig.d2,
        p1 = auditConfig.p1,
        p2 = auditConfig.p2,
        p3 = auditConfig.p3,
        p4 = auditConfig.p4,
    )
    val testFn = BettingMart(
        bettingFn = optimal,
        Nc = contestUA.Nc,
        noerror = assorter.noerror,
        upperBound = assorter.upperBound,
        withoutReplacement = true  // TODO WTF was false??
    )

    val testH0Result = testFn.testH0(contestUA.sampleSize, terminateOnNullReject = true) { sampler.sample() }
    if (testH0Result.status == TestH0Status.StatRejectNull) {
        assertion.proved = true
        assertion.samplesNeeded = testH0Result.sampleCount
    }
    assertion.pvalue = testH0Result.pvalues.last()
    println("runOneAssertionAudit: $assertion, status = ${testH0Result.status}")
    return (testH0Result.status == TestH0Status.StatRejectNull)
}

fun simulateSampleSize(
    auditConfig: AuditConfig,
    contest: ContestUnderAudit,
    assertion: ComparisonAssertion,
    cvrPairs: List<Pair<CvrIF, CvrUnderAudit>>, // (mvr, cvr)
) {
    val assorter = assertion.assorter

    val samples = PrevSamplesWithRates(assorter.noerror)
    cvrPairs.forEach { (mvr, cvr) -> samples.addSample(assorter.bassort(mvr,cvr)) }
    println("simulateSampleSize ${assorter.name()} samplingErrors= ${samples.samplingErrors()}")

    val cvrs = cvrPairs.map { it.second }
    val sampler = ComparisonSamplerSimulation(
        cvrs, contest, assorter,
        p1 = auditConfig.p1, p2 = auditConfig.p2, p3 = auditConfig.p3, p4 = auditConfig.p4
    )
    // println("${sampler.showFlips()}")

    val optimal = AdaptiveComparison(
        Nc = contest.Nc,
        withoutReplacement = true,
        a = assorter.noerror,
        d1 = auditConfig.d1,
        d2 = auditConfig.d2,
        p1 = auditConfig.p1,
        p2 = auditConfig.p2,
        p3 = auditConfig.p3,
        p4 = auditConfig.p4,
    )
    val testFn = BettingMart(
        bettingFn = optimal,
        Nc = contest.Nc,
        noerror = assorter.noerror,
        upperBound = assorter.upperBound,
        withoutReplacement = false,
    )

    val result: RunTestRepeatedResult = runTestRepeated(
        drawSample = sampler,
        maxSamples = cvrPairs.size,
        ntrials = auditConfig.ntrials,
        testFn = testFn,
        testParameters = mapOf(
            "p1" to optimal.p1,
            "p2" to optimal.p2,
            "p3" to optimal.p3,
            "p4" to optimal.p4,
            "margin" to assorter.margin
        ),
        showDetails = false,
    )
    val size = result.findQuantile(auditConfig.quantile)
    println("simulateSampleSize: ${assorter.name()} margin=${df(assorter.margin)} ${100*auditConfig.quantile}% quantile = $size " +
            "actual= ${assertion.samplesNeeded} ${cumul(result.sampleCount, assertion.samplesNeeded)}%")
    // println("  errorRate % = [${result.errorRates()}]")
}