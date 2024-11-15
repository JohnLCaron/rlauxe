package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.util.*

data class AuditParams(val riskLimit: Double, val seed: Long, val auditType: AuditType)

data class AuditRound(val riskLimit: Double, val seed: Long, val auditType: AuditType)

data class ExecutiveFunction(val riskLimit: Double, val seed: Long, val auditType: AuditType)

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
    val auditParams: AuditParams,
    val ballotManifest: BallotManifest,
    val cvrs: List<Cvr>,
    val upperBounds: Map<Int, Int>, // 𝑁_𝑐. Or should this be part of Contest?
) {
    val contestsUA: List<ContestUnderAudit>
    val cvrsUA: List<CvrUnderAudit>
    val prng = Prng(auditParams.seed)

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
            it.upperBound = upperBounds[it.contest.id]!!
            //	2.b) If there are more CVRs that contain the contest than the upper bound, something is seriously wrong.
            if (it.upperBound!! < it.ncvrs) throw RuntimeException(
                "upperBound ${it.upperBound} < ncvrs ${it.ncvrs} for contest ${it.contest.id}"
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

    fun chooseSamples(): List<Int> {
        //4.a) Pick the (cumulative) sample sizes {𝑆_𝑐} for 𝑐 ∈ C to attain by the end of this round of sampling.
        //	    The software offers several options for picking {𝑆_𝑐}, including some based on simulation.
        //      The desired sampling fraction 𝑓_𝑐 := 𝑆_𝑐 /𝑁_𝑐 for contest 𝑐 is the sampling probability
        //	      for each card that contains contest 𝑘, treating cards already in the sample as having sampling probability 1.
        //	    The probability 𝑝_𝑖 that previously unsampled card 𝑖 is sampled in the next round is the largest of those probabilities:
        //	      𝑝_𝑖 := max (𝑓_𝑐), 𝑐 ∈ C ∩ C𝑖, where C_𝑖 denotes the contests on card 𝑖.
        //	b) Estimate the total sample size to be Sum(𝑝_𝑖), where the sum is across all cards 𝑖 except phantom cards.
        val computeSize = estimateSampleSizes(contestsUA, cvrsUA, auditParams.riskLimit) // set contestUA.sampleSize

        //	c) Choose thresholds {𝑡_𝑐} 𝑐 ∈ C so that 𝑆_𝑐 ballot cards containing contest 𝑐 have a sample number 𝑢_𝑖 less than or equal to 𝑡_𝑐 .
        // draws random ballots by consistent sampling, and returns their locations to the auditors.
        val samples = consistentSampling(contestsUA, cvrsUA)
        println(" computeSize=$computeSize consistentSamplingSize= ${samples.size}")
        return samples// set contestUA.sampleThreshold

        // n_sampled_phantoms = np.sum(sampled_cvr_indices > manifest_cards)
        //print(f'The sample includes {n_sampled_phantoms} phantom cards.')

        // cards_to_retrieve, sample_order, cvr_sample, mvr_phantoms_sample = \
        //    Dominion.sample_from_cvrs(cvr_list, manifest, sampled_cvr_indices)

        // # write the sample
        //Dominion.write_cards_sampled(audit.sample_file, cards_to_retrieve, print_phantoms=False)
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

        // TODO could parellelize
        var allDone = true
        contestsUA.forEach { contestUA ->
            contestUA.comparisonAssertions.forEach { assertion ->
                allDone = allDone && runOneAssertion(contestUA, assertion, cvrPairs)
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

// expect these parameters
// SHANGRLA Nonneg_mean.sample_size
//                    'test':             NonnegMean.alpha_mart,
//                   'estim':            NonnegMean.optimal_comparison
//          'quantile':       0.8,
//         'error_rate_1':   0.001,
//         'error_rate_2':   0.0,
//         'reps':           100,
fun estimateSampleSizes(contestsUA: List<ContestUnderAudit>, cvrs: List<CvrUnderAudit>, alpha: Double): Int {
    // TODO could parellelize
    val finder = FindSampleSize(alpha, p1 = .01, p2 = .001, ntrials = 100, quantile = .90)
    contestsUA.forEach { contestUA ->
        val sampleSizes = contestUA.comparisonAssertions.map { assert ->
            finder.simulateSampleSize(contestUA, assert.assorter, cvrs,)
        }
        contestUA.sampleSize = sampleSizes.max()
    }

    // AFAICT, the calculation of the total_size using the probabilities as described in 4.b) is when you just want the
    // total_size estimate, but not do the consistent sampling.
    val computeSize = finder.computeSampleSize(contestsUA, cvrs)
    return computeSize
}

/////////////////////////////////////////////////////////////////////////////////
// run audit for one assertion; could be parallel
fun runOneAssertion(
    contestUA: ContestUnderAudit,
    assertion: ComparisonAssertion,
    cvrPairs: List<Pair<CvrIF, CvrUnderAudit>>,
): Boolean {
    val assorter = assertion.assorter
    // each assorted needs their own sampler
    val sampler: SampleFn = ComparisonSampler(cvrPairs, contestUA, assorter)
    // val sampleSize = cvrPairs.size

    // TODO could pass the testFn into the workflow
    // note that you need the assorter for the sampler
    // need the upperBound and noerror for the AdaptiveComparison bettingFn
    // the testFn is independent, it assumes drawSample already does the assort.

    // class AdaptiveComparison(
    //    val N: Int,
    //    val withoutReplacement: Boolean = true,
    //    val upperBound: Double, // compareAssorter.upperBound
    //    val a: Double, // noerror
    //    val d1: Int,  // weight p1, p3 // TODO derive from p1-p4 ??
    //    val d2: Int, // weight p2, p4
    //    val p1: Double = 1.0e-2, // apriori rate of 1-vote overstatements; set to 0 to remove consideration
    //    val p2: Double = 1.0e-4, // apriori rate of 2-vote overstatements; set to 0 to remove consideration
    //    val p3: Double = 1.0e-2, // apriori rate of 1-vote understatements; set to 0 to remove consideration
    //    val p4: Double = 1.0e-4, // apriori rate of 2-vote understatements; set to 0 to remove consideration
    //    val eps: Double = .00001
    val optimal = AdaptiveComparison(
        N = contestUA.ncvrs,
        withoutReplacement = true,
        a = assorter.noerror,
        d1 = 100,  // TODO set params
        d2 = 100,
        p1 = .01,
        p2 = .001,
        p3 = 0.0,
        p4 = 0.0,
    )
    val testFn = BettingMart(bettingFn = optimal, N = contestUA.ncvrs, noerror = assorter.noerror, upperBound = assorter.upperBound, withoutReplacement = false)

    val testH0Result = testFn.testH0(contestUA.ncvrs, terminateOnNullReject = false) { sampler.sample() }
    if (testH0Result.status == TestH0Status.StatRejectNull) {
        assertion.proved = true
    }
    println(" assertion $assertion finished, status = ${testH0Result.status} ")
    return (testH0Result.status == TestH0Status.StatRejectNull)
}

