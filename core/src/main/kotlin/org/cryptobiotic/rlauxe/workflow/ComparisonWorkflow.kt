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

class ComparisonWorkflow(
    val auditConfig: AuditConfig,
    contests: List<Contest>, // the contests you want to audit
    raireContests: List<RaireContestUnderAudit>, // TODO or call raire from here ??
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

        contestsUA = (makeContestsFromCvrs(contests, cvrs, auditConfig.hasStyles) + tabulateRaireVotes(raireContests, cvrs)).sortedBy{ it.id }
        contestsUA.forEach {
            //	2.b) If there are more CVRs that contain the contest than the upper bound, something is seriously wrong.
            if (it.Nc < it.ncvrs) throw RuntimeException(
                "upperBound ${it.Nc} < ncvrs ${it.ncvrs} for contest ${it.contest.info.id}"
            )
            if (it.choiceFunction != SocialChoiceFunction.IRV) {
                checkWinners(it, (it.contest as Contest).votes.entries.sortedByDescending { it.value })  // 2.a)
            }
        }

        // 3.c) Assign independent uniform pseudo-random numbers to CVRs that contain one or more contests under audit
        //      (including “phantom” CVRs), using a high-quality PRNG [OS19].
        val phantomCVRs = makePhantomCvrs(contestsUA, "phantom-", prng)
        cvrsUA = cvrs.map { CvrUnderAudit(it, prng.next()) } + phantomCVRs

        // 3. Prepare for sampling
        //	a) Generate a set of SHANGRLA [St20] assertions A_𝑐 for every contest 𝑐 under audit.
        //	b) Initialize A ← ∪ A_𝑐, c=1..C and C ← {1, . . . , 𝐶}. (Keep track of what assertions are proved)

        val votes: Map<Int, Map<Int, Int>> = tabulateVotes(cvrs)  // contestId -> candId, vote count (or rank?)
        contestsUA.filter{ !it.done }.forEach { contest ->
            contest.makeComparisonAssertions(cvrsUA, votes[contest.id]!!)
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
    fun chooseSamples(prevMvrs: List<CvrIF>, roundIdx: Int, show: Boolean = true): List<Int> {
        println("estimateSampleSizes round $roundIdx")

        val maxContestSize =  estimateSampleSizes(
            auditConfig,
            contestsUA,
            cvrs,
            prevMvrs,
            roundIdx,
        )

        // TODO how to control the round's sampleSize?

        //	c) Choose thresholds {𝑡_𝑐} 𝑐 ∈ C so that 𝑆_𝑐 ballot cards containing contest 𝑐 have a sample number 𝑢_𝑖 less than or equal to 𝑡_𝑐 .
        //     draws random ballots by consistent sampling, and returns their locations to the auditors.
        val contestsNotDone = contestsUA.filter{ !it.done }
        if (contestsNotDone.size > 0) {
            println("consistentCvrSampling round $roundIdx")
            val sampleIndices = consistentCvrSampling(contestsNotDone, cvrsUA)
            println(" ComparisonWithStyle.chooseSamples maxContestSize=$maxContestSize consistentSamplingSize= ${sampleIndices.size}")
            return sampleIndices
        }
        return emptyList()
    }

    //   The auditors retrieve the indicated cards, manually read the votes from those cards, and input the MVRs
    fun runAudit(sampleIndices: List<Int>, mvrs: List<Cvr>, roundIdx: Int): Boolean {
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
                    assertion.status = runOneAssertionAudit(auditConfig, contestUA, assertion, cvrPairs, roundIdx)
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

    fun showResultsOld() {
        println("Audit results")
        contestsUA.forEach{ contest ->
            println(" $contest status=${contest.status}")
        }
        println()
    }

    fun showResults() {
        println("Audit results")
        contestsUA.forEach{ contest ->
            val minAssertion = contest.minAssertion()
            if (minAssertion == null)
                println(" $contest has no assertions; status=${contest.status}")
            else if (auditConfig.hasStyles)
                println(" $contest samplesUsed=${minAssertion.samplesUsed} round=${minAssertion.round} status=${contest.status}")
            else
                println(" $contest samplesUsed=${minAssertion.samplesUsed} " +
                        "estTotalSampleSize=${contest.estTotalSampleSize} round=${minAssertion.round} status=${contest.status}")
        }
        println()
    }
}

///////////////////////////////////////////////////////////////////////

// tabulate votes, make sure of correct winners, count ncvrs for each contest, create ContestUnderAudit
fun makeContestsFromCvrs(contests: List<Contest>, cvrs: List<CvrIF>, hasStyles: Boolean=true): List<ContestUnderAudit> {
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
        val contestUA = ContestUnderAudit(contest, nc, true, hasStyles) // TODO nc vs ncvrs ??
        require(checkEquivilentVotes((contestUA.contest as Contest).votes, accumVotes))
        contestUA
    }
}

// ok if one has zero votes and the other doesnt
fun checkEquivilentVotes(votes1: Map<Int, Int>, votes2: Map<Int, Int>, ) : Boolean {
    if (votes1 == votes2) return true
    val votes1z = votes1.filter{ (_, vote) -> vote != 0 }
    val votes2z = votes2.filter{ (_, vote) -> vote != 0 }
    if (votes1z != votes2z)
        println("")
    return votes1z == votes2z
}

// TODO seems wrong
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
        // require(checkEquivilentVotes(contestUA.contest.votes, accumVotes))
        contestUA.ncvrs = nc
        contestUA
    }
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
    cvrPairs: List<Pair<Cvr, CvrUnderAudit>>, // (mvr, cvr)
    roundIdx: Int,
): TestH0Status {
    val assorter = assertion.cassorter
    val sampler = ComparisonSamplerGen(cvrPairs, contestUA, assorter, allowReset = false)

    // TODO always using the ComparisonErrorRates derived from fuzzPct. should have the option to use ones chosen by the user.
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

    val maxSamples = cvrPairs.count { it.first.hasContest(contestUA.id) }
    assertion.samplesUsed = maxSamples

    // do not terminate on null retject, continue to use all samples
    val testH0Result = testFn.testH0(contestUA.availableInSample, terminateOnNullReject = false) { sampler.sample() }
    if (!testH0Result.status.fail) {
        assertion.proved = true
        assertion.round = roundIdx
    } else {
        println("testH0Result.status = ${testH0Result.status}")
    }
    assertion.samplesNeeded = testH0Result.pvalues.indexOfFirst{ it < auditConfig.riskLimit }
    assertion.pvalue = testH0Result.pvalues.last()

    println(" ${contestUA.name} $assertion, samplesNeeded=${assertion.samplesNeeded} samplesUsed=${assertion.samplesUsed} pvalue = ${assertion.pvalue} status = ${testH0Result.status}")

    return testH0Result.status
}