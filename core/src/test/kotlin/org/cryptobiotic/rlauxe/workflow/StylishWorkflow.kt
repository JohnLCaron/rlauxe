package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlaux.core.raire.RaireCvr
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.sampling.BallotManifest
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.raire.makeRaireComparisonAudit
import org.cryptobiotic.rlauxe.sampling.RunTestRepeatedResult
import org.cryptobiotic.rlauxe.sampling.consistentSampling
import org.cryptobiotic.rlauxe.sampling.runTestRepeated
import org.cryptobiotic.rlauxe.util.*

data class AuditParams(val riskLimit: Double, val seed: Long, val auditType: AuditType)

// STYLISH 2.1
//Card-level Comparison Audits and Card-Style Data

// TODO, what about when you start an audit, then more CVRs arrive?
// or even just check what your sample sizes are, based on info you have ??
class StylishWorkflow(
    val contests: List<Contest>, // the contests you want to audit
    val raireContests: List<RaireContestUnderAudit>,
    val auditParams: AuditParams,
    val ballotManifest: BallotManifest,
    val cvrs: List<Cvr>,
    val upperBounds: Map<Int, Int>, // Let 𝑁_𝑐 denote the upper bound on the number of cards that contain contest
) {
    // 1. Set up the audit
    //	a) Read contest descriptors, candidate names, social choice functions, upper bounds on the number of cards that contain each contest, and reported winners.
    //	     Let 𝑁_𝑐 denote the upper bound on the number of cards that contain contest 𝑐, 𝑐 = 1, . . . , 𝐶.
    //	b) Read audit parameters (risk limit for each contest, risk-measuring function to use for each contest,
    //	   assumptions about errors for computing initial sample sizes), and seed for pseudo-random sampling.
    //	c) Read ballot manifest.
    //	d) Read CVRs.

    val contestsUA: List<ContestUnderAudit>
    val cvrsUA: List<CvrUnderAudit>

    // 2. Pre-processing and consistency checks
    // 	a) Check that the winners according to the CVRs are the reported winners.
    //	b) If there are more CVRs that contain any contest than the upper bound on the number of cards that contain the contest, stop: something is seriously wrong.
    //	c) If the upper bound on the number of cards that contain any contest is greater than the number of CVRs that contain the contest, create a corresponding set
    //	    of “phantom” CVRs as described in section 3.4 of [St20]. The phantom CVRs are generated separately for each contest: each phantom card contains only one contest.
    //	d) If the upper bound 𝑁_𝑐 on the number of cards that contain contest 𝑐 is greater than the number of physical cards whose locations are known,
    //     create enough “phantom” cards to make up the difference. TODO diff between c) and d) ?
    init {
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
        val phantomCVRs = makePhantomCvrs(contestsUA, "phantom-")
        cvrsUA = cvrs.map { CvrUnderAudit(it, false, secureRandom.nextInt()) } + phantomCVRs

        // 3. Prepare for sampling
        //	a) Generate a set of SHANGRLA [St20] assertions A_𝑐 for every contest 𝑐 under audit.
        //	b) Initialize A ← ∪ A_𝑐, c=1..C and C ← {1, . . . , 𝐶}. TODO just notation?
        contestsUA.forEach { contest ->
            contest.makeComparisonAssertions(cvrsUA)
        }
    }

    // 4. Main audit loop. While A is not empty:

    fun generateSampleSizes() {
        //	a) Pick the (cumulative) sample sizes {𝑆_𝑐} for 𝑐 ∈ C to attain by the end of this round of sampling.
        //	    The software offers several options for picking {𝑆_𝑐}, including some based on simulation.

        // set the needed sizes on each contestsUA
        calcSampleSizes(contestsUA, cvrsUA)
    }

    fun chooseSamples(): List<Int> {
        // 4.a)    The desired sampling fraction 𝑓_𝑐 := 𝑆_𝑐 /𝑁_𝑐 for contest 𝑐 is the sampling probability
        //	      for each card that contains contest 𝑘, treating cards already in the sample as having sampling probability 1.
        //	    The probability 𝑝_𝑖 that previously unsampled card 𝑖 is sampled in the next round is the largest of those probabilities:
        //	      𝑝_𝑖 := max (𝑓_𝑐), 𝑐 ∈ C ∩ C𝑖, where C_𝑖 denotes the contests on card 𝑖.
        //	b) Estimate the total sample size to be Sum(𝑝_𝑖), where the sum is across all cards 𝑖 except phantom cards.
        //	c) Choose thresholds {𝑡_𝑐} 𝑐 ∈ C so that 𝑆_𝑐 ballot cards containing contest 𝑐 have a sample number 𝑢_𝑖 less than or equal to 𝑡_𝑐 .

        // set the needed sizes on each contestsUA
        return consistentSampling(contestsUA, cvrsUA)
    }

    fun runAudit(mvrs: List<CvrIF>) {
        //4.d) Retrieve any of the corresponding ballot cards that have not yet been audited and inspect them manually to generate MVRs.
        // 	e) Import the MVRs.
        //	f) For each MVR 𝑖:
        //		For each 𝑐 ∈ C:
        //			If 𝑢_𝑖 ≤ 𝑡_𝑐 , then for each 𝑎 ∈ A 𝑐 ∩ A:
        //				• If the 𝑖th CVR is a phantom, define 𝑎(CVR𝑖 ) := 1/2.
        //				• If card 𝑖 cannot be found or if it is a phantom, define 𝑎(MVR𝑖 ) := 0.
        //				• Find the overstatement of assertion 𝑎 for CVR 𝑖, 𝑎(CVR𝑖 ) − 𝑎(MVR𝑖 ).
        //	g) Use the overstatement data from the previous step to update the measured risk for every assertion 𝑎 ∈ A.
    }
}

// SHANGRLA.make_phantoms(). Probably 2.d ?
fun makePhantomCvrs(
    contestas: List<ContestUnderAudit>,
    prefix: String = "phantom-",
): List<CvrUnderAudit> {
    // code assertRLA.ipynb
    // + Prepare ~2EZ:
    //    - `N_phantoms = max_cards - cards_in_manifest`
    //    - If `N_phantoms < 0`, complain
    //    - Else create `N_phantoms` phantom cards
    //    - For each contest `c`:
    //        + `N_c` is the input upper bound on the number of cards that contain `c`
    //        + if `N_c is None`, `N_c = max_cards - non_c_cvrs`, where `non_c_cvrs` is #CVRs that don't contain `c`
    //        + `C_c` is the number of CVRs that contain the contest
    //        + if `C_c > N_c`, complain
    //        + else if `N_c - C_c > N_phantoms`, complain
    //        + else:
    //            - Consider contest `c` to be on the first `N_c - C_c` phantom CVRs
    //            - Consider contest `c` to be on the first `N_c - C_c` phantom ballots

    // 3.4 SHANGRLA
    // If N_c > ncvrs, create N − n “phantom ballots” and N − n “phantom CVRs.”

    // create phantom CVRs as needed for each contest
    val phantombs = mutableListOf<PhantomBuilder>()

    for (contest in contestas) {
        val phantoms_needed = contest.upperBound!! - contest.ncvrs
        while (phantombs.size < phantoms_needed) { // make sure you have enough phantom CVRs
            phantombs.add(PhantomBuilder(id = "${prefix}${phantombs.size + 1}"))
        }
        // list contest on the first n phantom CVRs
        repeat(phantoms_needed) {
            phantombs[it].contests.add(contest.id)
        }
    }
    return phantombs.map { it.build() }
}

private class PhantomBuilder(val id: String) {
    val contests = mutableListOf<Int>()
    fun build(): CvrUnderAudit {
        val votes = contests.map { it to IntArray(0) }.toMap()
        return CvrUnderAudit(Cvr(id, votes), phantom = true, secureRandom.nextInt())
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

fun calcSampleSizes( contests: List<ContestUnderAudit>, cvrs: List<CvrUnderAudit>) {

}

// TODO sample size is always the same, so no need to make repeated runs
// artifact of testing without errors
fun calcSampleSizesBySimulation(
    ntrials: Int,
    contests: List<ContestUnderAudit>,
    cvrs: List<CvrUnderAudit>,
    p1: Double = .01,
    p2: Double = .001,
) {
    val N = cvrs.size // or is this N_c ??

    contests.forEach { contestUA ->
        val stopwatch = Stopwatch()
        val minAssertion = contestUA.minAssert!!
        val minAssorter = minAssertion.assorter

        // TODO this assumes winner == 0
        val sampler: GenSampleFn = ComparisonWithErrorRates(cvrs, minAssorter, p2 = p2, p1 = p1)
        println("sampleCount= ${sampler.sampleCount()}  sampleMean= ${sampler.sampleMean()}")

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
            N = N,
            withoutReplacement = true,
            upperBound = minAssorter.upperBound,
            a = minAssorter.noerror,
            d1 = 100,
            d2 = 100,
            p1 = .01,
            p2 = .001,
            p3 = 0.0,
            p4 = 0.0,
        )
        val betta = BettingMart(bettingFn = optimal, N = N, noerror = 0.0, withoutReplacement = false)

        // TODO use coroutines
        val result: RunTestRepeatedResult = runTestRepeated(
            drawSample = sampler,
            maxSamples = N,
            ntrials = ntrials,
            testFn = betta,
            testParameters = mapOf("p2" to optimal.p2, "margin" to minAssertion.margin),
            showDetails = true,
        )
        // TODO             sam_size = int(np.quantile(sams, quantile))
        contestUA.sampleSize = result.avgSamplesNeeded()
        println(" that took $stopwatch")
    }
}