package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.sampling.*
import org.cryptobiotic.rlauxe.util.*

// STYLISH 2.1
//Card-level Comparison Audits and Card-Style Data
// Assume we van derive BallotStyles from CVRs, which is equivilent to styles = true.
// TODO what happens if we dont?

// 1. Set up the audit
//	a) Read contest descriptors, candidate names, social choice functions, and reported winners.
//     Read upper bounds on the number of cards that contain each contest:
//	     Let 𝑁_𝑐 denote the upper bound on the number of cards that contain contest 𝑐, 𝑐 = 1, . . . , 𝐶.
//	b) Read audit parameters (risk limit for each contest, risk-measuring function to use for each contest,
//	   assumptions about errors for computing initial sample sizes), and seed for pseudo-random sampling.
//	c) Read ballot manifest.
//	d) Read CVRs.

class ComparisonWithStyle(
    contests: List<Contest>, // the contests you want to audit
    raireContests: List<RaireContestUnderAudit>, // TODO or call raire from here ??
    val auditConfig: AuditConfig,
    val cvrs: List<Cvr>,
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

        contestsUA = (tabulateVotes(contests, cvrs) + tabulateRaireVotes(raireContests, cvrs)).sortedBy{ it.id }
        contestsUA.forEach {
            //	2.b) If there are more CVRs that contain the contest than the upper bound, something is seriously wrong.
            if (it.Nc < it.ncvrs) throw RuntimeException(
                "upperBound ${it.Nc} < ncvrs ${it.ncvrs} for contest ${it.contest.id}"
            )
            checkWinners(it, it.contest.votes.entries.sortedByDescending { it.value })  // 2.a)
        }

        // 3.c) Assign independent uniform pseudo-random numbers to CVRs that contain one or more contests under audit
        //      (including “phantom” CVRs), using a high-quality PRNG [OS19].
        val phantomCVRs = makePhantomCvrs(contestsUA, "phantom-", prng)
        cvrsUA = cvrs.map { CvrUnderAudit(it, prng.next()) } + phantomCVRs

        // 3. Prepare for sampling
        //	a) Generate a set of SHANGRLA [St20] assertions A_𝑐 for every contest 𝑐 under audit.
        //	b) Initialize A ← ∪ A_𝑐, c=1..C and C ← {1, . . . , 𝐶}. (Keep track of what assertions are proved)
        contestsUA.filter{ !it.done }.forEach { contest ->
            contest.makeComparisonAssertions(cvrsUA)
            // TODO apply minMargin ?? maybe handled by failPct?
        }
    }

    // 4. Main audit loop. While A is not empty:
    //   chooseSamples()
    //   runAudit()

    /**
     * Choose lists of ballots to sample.
     * @parameter mvrs: use existing mvrs to estimate samples. may be empty.
     */
    fun chooseSamples(prevMvrs: List<CvrIF>, roundIdx: Int): List<Int> {
        println("EstimateSampleSize.simulateSampleSizeComparisonContest round $roundIdx")

        val sampleSizer = EstimateSampleSize(auditConfig)
        val contestsNotDone = contestsUA.filter{ !it.done }
        contestsNotDone.forEach { contestUA ->
            sampleSizer.simulateSampleSizeComparisonContest(contestUA, cvrs, prevMvrs, roundIdx, show=true)
        }
        println()
        val maxContestSize = contestsNotDone.map { it.estSampleSize }.max()

        // TODO should we know max sampling percent? or is it an absolute number?
        //   should there be a minimum increment?? esp if its going to end up hand-counted?
        //   user should be able to force a total count size.

        //	c) Choose thresholds {𝑡_𝑐} 𝑐 ∈ C so that 𝑆_𝑐 ballot cards containing contest 𝑐 have a sample number 𝑢_𝑖 less than or equal to 𝑡_𝑐 .
        //     draws random ballots by consistent sampling, and returns their locations to the auditors.
        println("consistentCvrSampling round $roundIdx")
        val sampleIndices = consistentCvrSampling(contestsUA.filter{ !it.done }, cvrsUA)
        println(" ComparisonWithStyle.chooseSamples maxContestSize=$maxContestSize consistentSamplingSize= ${sampleIndices.size}")

        return sampleIndices
    }

    //   The auditors retrieve the indicated cards, manually read the votes from those cards, and input the MVRs
    fun runAudit(sampleIndices: List<Int>, mvrs: List<Cvr>): Boolean {
        //4.d) Retrieve any of the corresponding ballot cards that have not yet been audited and inspect them manually to generate MVRs.
        // 	e) Import the MVRs.
        //	f) For each MVR 𝑖:
        //		For each 𝑐 ∈ C:
        //			If 𝑢_𝑖 ≤ 𝑡_𝑐 , then for each 𝑎 ∈ A 𝑐 ∩ A:
        //				• If the 𝑖th CVR is a phantom, define 𝑎(CVR𝑖 ) := 1/2.
        //				• If card 𝑖 cannot be found or if it is a phantom, define 𝑎(MVR𝑖 ) := 0.
        //				• Find the overstatement of assertion 𝑎 for CVR 𝑖, 𝑎(CVR𝑖 ) − 𝑎(MVR𝑖 ).
        //	g) Use the overstatement data from the previous step to update the measured risk for every assertion 𝑎 ∈ A.

        val contestsNotDone = contestsUA.filter{ !it.done }
        val sampledCvrs = sampleIndices.map { cvrsUA[it] }

        // prove that sampledCvrs correspond to mvrs
        require(sampledCvrs.size == mvrs.size)
        val cvrPairs: List<Pair<Cvr, CvrUnderAudit>> = mvrs.zip(sampledCvrs)
        cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id) }

        // TODO could parellelize across assertions
        println("runOneAssertionAudit")
        var allDone = true
        contestsNotDone.forEach { contestUA ->
            var allAssertionsDone = true
            contestUA.comparisonAssertions.forEach { assertion ->
                if (!assertion.proved) {
                    assertion.status = runOneAssertionAudit(auditConfig, contestUA, assertion, cvrPairs)
                    allAssertionsDone = allAssertionsDone && (!assertion.status.fail)
                }
                if (allAssertionsDone) {
                    contestUA.done = true
                    contestUA.status = TestH0Status.StatRejectNull
                }
                allDone = allDone && contestUA.done
            }
        }
        return allDone
    }

    fun showResults() {
        println("Audit results")
        contestsUA.forEach{ contest ->
            println(" $contest status=${contest.status}")
        }
        println()
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
        val contestUA = ContestUnderAudit(contest, nc)// nc vs ncvrs ??
        require(contestUA.contest.votes == accumVotes)
        contestUA
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
        val contestUA = contests.find { it.id == conId }
        if (contestUA == null) throw RuntimeException("no contest for contest id= $conId")
        val nc = ncvrs[conId]!!
        val accumVotes = allVotes[conId]!!
        require(contestUA.contest.votes == accumVotes)
        contestUA.ncvrs = nc
        contestUA
    }
}

/* 2.a) Check that the winners according to the CVRs are the reported winners on the Contest.
fun checkWinners(contestUA: ContestUnderAudit, accumVotes: Map<Int, Int>) {
    val sortedVotes: List<Map.Entry<Int, Int>> = accumVotes.entries.sortedByDescending { it.value }
    val contest = contestUA.contest
    val nwinners = contest.winners.size

    // make sure that the winners are unique
    val winnerSet = mutableSetOf<Int>()
    winnerSet.addAll(contest.winners)
    if (winnerSet.size != contest.winners.size) {
        println("winners in contest ${contest} have duplicates")
        contestUA.done = true
        contestUA.status = TestH0Status.ContestMisformed
        return
    }

    // see if theres a tie
    val winnerMin: Int = sortedVotes.take(nwinners).map{ it.value }.min()
    if (sortedVotes.size > nwinners) {
        val firstLoser = sortedVotes[nwinners]
        if (firstLoser.value == winnerMin ) {
            println("tie in contest ${contest}")
            contestUA.done = true
            contestUA.status = TestH0Status.MinMargin
            return
        }
    }

    // check that the top nwinners are in winners list
    sortedVotes.take(nwinners).forEach { (candId, vote) ->
        if (!contest.winners.contains(candId)) {
            println("winners ${contest.winners} does not contain candidateId $candId")
            contestUA.done = true
            contestUA.status = TestH0Status.ContestMisformed
            return
        }
    }
}

 */

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
    cvrPairs: List<Pair<Cvr, CvrUnderAudit>>, // (mvr, cvr)
): TestH0Status {
    val assorter = assertion.assorter
    val sampler = ComparisonSamplerGen(cvrPairs, contestUA, assorter, allowReset = false)

    val errorRates = ComparisonErrorRates.getErrorRates(contestUA.ncandidates, auditConfig.fuzzPct)
    val optimal = AdaptiveComparison(
        Nc = contestUA.Nc,
        withoutReplacement = true,
        a = assorter.noerror,
        d1 = auditConfig.d1,
        d2 = auditConfig.d2,
        p1 = errorRates[0],
        p2 = errorRates[1],
        p3 = errorRates[2],
        p4 = errorRates[3],
    )
    val testFn = BettingMart(
        bettingFn = optimal,
        Nc = contestUA.Nc,
        noerror = assorter.noerror,
        upperBound = assorter.upperBound,
        riskLimit = auditConfig.riskLimit,
        withoutReplacement = true
    )

    // do not terminate on null retject, continue to use all samples
    val testH0Result = testFn.testH0(contestUA.availableInSample, terminateOnNullReject = false) { sampler.sample() }
    if (!testH0Result.status.fail) {
        assertion.proved = true
    } else {
        println("testH0Result.status = ${testH0Result.status}")
    }
    assertion.samplesNeeded = testH0Result.pvalues.indexOfFirst{ it < auditConfig.riskLimit }
    assertion.samplesUsed = testH0Result.sampleCount
    assertion.pvalue = testH0Result.pvalues.last()

    println(" ${contestUA.name} $assertion, samplesNeeded=${assertion.samplesNeeded} samplesUsed=${assertion.samplesUsed} pvalue = ${assertion.pvalue} status = ${testH0Result.status}")

    return testH0Result.status
}