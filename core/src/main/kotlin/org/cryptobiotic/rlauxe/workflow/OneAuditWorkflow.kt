package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.sampling.*
import org.cryptobiotic.rlauxe.util.*

// "Stylish Risk-Limiting Audits in Practice" STYLISH 2.1
// 1. Set up the audit
//	a) Read contest descriptors, candidate names, social choice functions, and reported winners.
//     Read upper bounds on the number of cards that contain each contest:
//	     Let 𝑁_𝑐 denote the upper bound on the number of cards that contain contest 𝑐, 𝑐 = 1, . . . , 𝐶.
//	b) Read audit parameters (risk limit for each contest, risk-measuring function to use for each contest,
//	   assumptions about errors for computing initial sample sizes), and seed for pseudo-random sampling.
//	c) Read ballot manifest.
//	d) Read CVRs.

class OneAuditWorkflow(
    val auditConfig: AuditConfig,
    contests: List<ContestOA>, // the contests you want to audit
    val cvrs: List<Cvr>, // includes undervotes and phantoms.
) {
    val contestsUA: List<ContestUnderAudit>
    val cvrsUA: List<CvrUnderAudit>
    init {
        require (auditConfig.auditType == AuditType.ONEAUDIT)

        contestsUA = contests.map { it.makeContestUnderAudit(cvrs) }

        // must be done once and for all rounds
        val prng = Prng(auditConfig.seed)
        cvrsUA = cvrs.map { CvrUnderAudit(it, prng.next()) }
    }

    /**
     * Choose lists of ballots to sample.
     * @parameter prevMvrs: use existing mvrs to estimate samples. may be empty.
     */
    fun chooseSamples(prevMvrs: List<Cvr>, roundIdx: Int, show: Boolean = true): List<Int> {
        println("estimateSampleSizes round $roundIdx")

        val maxContestSize = estimateSampleSizes(
            auditConfig,
            contestsUA,
            cvrs,
            prevMvrs,
            roundIdx,
            show=show,
        )

        //	2.c) If the upper bound on the number of cards that contain any contest is greater than the number of CVRs that contain the contest, create a corresponding set
        //	    of “phantom” CVRs as described in section 3.4 of [St20]. The phantom CVRs are generated separately for each contest: each phantom card contains only one contest.
        //	2.d) If the upper bound 𝑁_𝑐 on the number of cards that contain contest 𝑐 is greater than the number of physical cards whose locations are known,
        //     create enough “phantom” cards to make up the difference. TODO c) vs d)  diffrence?
        //  3.c) Assign independent uniform pseudo-random numbers to CVRs that contain one or more contests under audit
        //      (including “phantom” CVRs), using a high-quality PRNG [OS19].
        // val ncvrs =  makeNcvrsPerContest(contests, cvrs)
        // val phantomCVRs = makePhantomCvrs(contests, ncvrs)

        // TODO how to control the round's sampleSize?

        //	4.c) Choose thresholds {𝑡_𝑐} 𝑐 ∈ C so that 𝑆_𝑐 ballot cards containing contest 𝑐 have a sample number 𝑢_𝑖 less than or equal to 𝑡_𝑐 .
        // draws random ballots and returns their locations to the auditors.
        val contestsNotDone = contestsUA.filter{ !it.done }
        if (contestsNotDone.size > 0) {
            return if (auditConfig.hasStyles) {
                println("\nconsistentSampling round $roundIdx")
                val sampleIndices = consistentSampling(contestsNotDone, cvrsUA)
                println(" maxContestSize=$maxContestSize consistentSamplingSize= ${sampleIndices.size}")
                sampleIndices
            } else {
                println("\nuniformSampling round $roundIdx")
                val sampleIndices = uniformSampling(contestsNotDone, cvrsUA, auditConfig.samplePctCutoff, cvrs.size, roundIdx)
                println(" maxContestSize=$maxContestSize consistentSamplingSize= ${sampleIndices.size}")
                sampleIndices
            }
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
        val sampledCvrs = sampleIndices.map { cvrs[it] }

        // prove that sampledCvrs correspond to mvrs
        require(sampledCvrs.size == mvrs.size)
        val cvrPairs: List<Pair<Cvr, Cvr>> = mvrs.zip(sampledCvrs)
        cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id) }

        // TODO could parellelize across assertions
        println("runAudit round $roundIdx")
        var allDone = true
        contestsNotDone.forEach { contestUA ->
            var allAssertionsDone = true
            contestUA.comparisonAssertions.forEach { assertion ->
                if (!assertion.proved) {
                    assertion.status = runOneAuditAssertion(auditConfig, contestUA, assertion, cvrPairs, roundIdx)
                    allAssertionsDone = allAssertionsDone && (!assertion.status.fail)
                }
            }
            if (allAssertionsDone) {
                contestUA.done = true
                contestUA.status = TestH0Status.StatRejectNull
            }
            allDone = allDone && contestUA.done

        }
        return allDone
    }

    fun showResults() {
        println("Audit results")
        contestsUA.forEach{ contest ->
            val minAssertion = contest.minAssertion()
            if (minAssertion == null) {
                println(" $contest has no assertions; status=${contest.status}")
            } else {
                if (minAssertion.roundResults.size == 1) {
                    print(" ${contest.name} (${contest.id}) Nc=${contest.Nc} Np=${contest.Np} minMargin=${df(minAssertion.margin)} ${minAssertion.roundResults[0]}")
                    if (!auditConfig.hasStyles) println(" estSampleSizeNoStyles=${contest.estSampleSizeNoStyles}") else println()
                } else {
                    print(" ${contest.name} (${contest.id}) Nc=${contest.Nc} minMargin=${df(minAssertion.margin)} est=${contest.estSampleSize} round=${minAssertion.round} status=${contest.status}")
                    if (!auditConfig.hasStyles) println(" estSampleSizeNoStyles=${contest.estSampleSizeNoStyles}") else println()
                    minAssertion.roundResults.forEach { rr -> println("   $rr") }
                }
            }
        }
        println()
    }
}

fun runOneAuditAssertion(
    auditConfig: AuditConfig,
    contestUA: ContestUnderAudit,
    cassertion: ComparisonAssertion,
    cvrPairs: List<Pair<Cvr, Cvr>>, // (mvr, cvr)
    roundIdx: Int,
): TestH0Status {
    val cassorter = cassertion.cassorter
    val sampler = ComparisonWithoutReplacement(contestUA.contest, cvrPairs, cassorter, allowReset = false)

    val errorRates = auditConfig.errorRates ?: ComparisonErrorRates.getErrorRates(contestUA.ncandidates, auditConfig.fuzzPct)
    val optimal = AdaptiveComparison(
        Nc = contestUA.Nc,
        withoutReplacement = true,
        a = cassorter.noerror,
        d1 = auditConfig.d1,
        d2 = auditConfig.d2,
        p2o = errorRates[0],
        p1o = errorRates[1],
        p1u = errorRates[2],
        p2u = errorRates[3],
    )
    val testFn = BettingMart(
        bettingFn = optimal,
        Nc = contestUA.Nc,
        noerror = cassorter.noerror,
        upperBound = cassorter.upperBound,
        riskLimit = auditConfig.riskLimit,
        withoutReplacement = true
    )

    // do not terminate on null reject, continue to use all samples
    val testH0Result = testFn.testH0(sampler.maxSamples(), terminateOnNullReject = false) { sampler.sample() }
    if (!testH0Result.status.fail) {
        cassertion.proved = true
        cassertion.round = roundIdx
    } else {
        println("testH0Result.status = ${testH0Result.status}")
    }

    val roundResult = AuditRoundResult(roundIdx,
        estSampleSize=cassertion.estSampleSize,
        samplesNeeded = testH0Result.pvalues.indexOfFirst{ it < auditConfig.riskLimit },
        samplesUsed = testH0Result.sampleCount,
        pvalue = testH0Result.pvalues.last(),
        status = testH0Result.status,
        // calcAssortMargin=cassorter.calcAssorterMargin(cvrPairs),
        )
    cassertion.roundResults.add(roundResult)

    println(" ${contestUA.name} $roundResult")
    return testH0Result.status
}