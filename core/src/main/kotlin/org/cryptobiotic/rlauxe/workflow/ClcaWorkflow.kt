package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.sampling.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.math.max

private val debugErrorRates = false

// "Stylish Risk-Limiting Audits in Practice" STYLISH 2.1
// 1. Set up the audit
//	a) Read contest descriptors, candidate names, social choice functions, and reported winners.
//     Read upper bounds on the number of cards that contain each contest:
//	     Let 𝑁_𝑐 denote the upper bound on the number of cards that contain contest 𝑐, 𝑐 = 1, . . . , 𝐶.
//	b) Read audit parameters (risk limit for each contest, risk-measuring function to use for each contest,
//	   assumptions about errors for computing initial sample sizes), and seed for pseudo-random sampling.
//	c) Read ballot manifest.
//	d) Read CVRs.

class ClcaWorkflow(
    val auditConfig: AuditConfig,
    contestsToAudit: List<Contest>, // the contests you want to audit
    raireContests: List<RaireContestUnderAudit>, // TODO or call raire from here ??
    val cvrs: List<Cvr>, // includes undervotes and phantoms.
    val quiet: Boolean = true,
): RlauxWorkflowIF {
    val contestsUA: List<ContestUnderAudit>
    val cvrsUA: List<CvrUnderAudit>

    init {
        require (auditConfig.auditType == AuditType.CARD_COMPARISON)

        // 2. Pre-processing and consistency checks
        // 	a) Check that the winners according to the CVRs are the reported winners.
        //	b) If there are more CVRs that contain any contest than the upper bound on the number of cards that contain the contest, stop: something is seriously wrong.
        contestsUA = contestsToAudit.map { ContestUnderAudit(it, isComparison=true, auditConfig.hasStyles) } + raireContests
        contestsUA.forEach {
            if (it.choiceFunction != SocialChoiceFunction.IRV) {
                checkWinners(it, (it.contest as Contest).votes.entries.sortedByDescending { it.value })  // 2.a)
            }
        }

        // 3. Prepare for sampling
        //	a) Generate a set of SHANGRLA [St20] assertions A_𝑐 for every contest 𝑐 under audit.
        //	b) Initialize A ← ∪ A_𝑐, c=1..C and C ← {1, . . . , 𝐶}. (Keep track of what assertions are proved)

        contestsUA.filter{ !it.done }.forEach { contest ->
            contest.makeClcaAssertions(cvrs)
        }

        // must be done once and for all rounds
        val prng = Prng(auditConfig.seed)
        cvrsUA = cvrs.map { CvrUnderAudit(it, prng.next()) }
    }

    // debugging
    fun estimateSampleSizes(roundIdx: Int, show: Boolean): List<EstimationResult> {
        return estimateSampleSizes(
            auditConfig,
            contestsUA,
            cvrs,
            roundIdx,
            show = show,
        )
    }

    /** Choose lists of ballots to sample. */
    override fun chooseSamples(roundIdx: Int, show: Boolean): List<Int> {
        if (!quiet) println("----------estimateSampleSizes round $roundIdx")

        estimateSampleSizes(
            auditConfig,
            contestsUA,
            cvrs,
            roundIdx,
            show=show,
        )

        return createSampleIndices(this, roundIdx, quiet)
    }

    override fun showResultsOld(estSampleSize: Int) {
        println("Audit results")
        contestsUA.forEach{ contest ->
            val minAssertion = contest.minClcaAssertion()
            if (minAssertion == null) {
                println(" $contest has no assertions; status=${contest.status}")
            } else {
                if (minAssertion.roundResults.size == 1) {
                    print(" ${contest.name} (${contest.id}) Nc=${contest.Nc} Np=${contest.Np} minMargin=${df(contest.minMargin())} ${minAssertion.roundResults[0]}")
                    if (!auditConfig.hasStyles) println(" estSampleSizeNoStyles=${contest.estSampleSizeNoStyles}") else println()
                } else {
                    print(" ${contest.name} (${contest.id}) Nc=${contest.Nc} minMargin=${df(contest.minMargin())} est=${contest.estSampleSize} round=${minAssertion.round} status=${contest.status}")
                    if (!auditConfig.hasStyles) println(" estSampleSizeNoStyles=${contest.estSampleSizeNoStyles}") else println()
                    minAssertion.roundResults.forEach { rr -> println("   $rr") }
                }
            }
        }

        var maxBallotsUsed = 0
        contestsUA.forEach { contest ->
            contest.assertions().filter { it.roundResults.isNotEmpty() }.forEach { assertion ->
                val lastRound = assertion.roundResults.last()
                maxBallotsUsed = max(maxBallotsUsed, lastRound.maxBallotsUsed)
            }
        }
        println("$estSampleSize - $maxBallotsUsed = extra ballots = ${estSampleSize - maxBallotsUsed}\n")
    }

    override fun runAudit(sampleIndices: List<Int>, mvrs: List<Cvr>, roundIdx: Int): Boolean {
        return runClcaAudit(auditConfig, contestsUA, sampleIndices, mvrs, cvrs, roundIdx, quiet)
    }

    override fun auditConfig() =  this.auditConfig
    override fun getContests(): List<ContestUnderAudit> = contestsUA
    override fun getBallotsOrCvrs() : List<BallotOrCvr> = cvrsUA
}

/////////////////////////////////////////////////////////////////////////////////

//   The auditors retrieved the indicated cards, manually read the votes from those cards, and input the MVRs
fun runClcaAudit(auditConfig: AuditConfig,
                 contestsUA: List<ContestUnderAudit>,
                 sampleIndices: List<Int>,
                 mvrs: List<Cvr>,
                 cvrs: List<Cvr>,
                 roundIdx: Int,
                 quiet: Boolean): Boolean {
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

    // TODO could parallelize across assertions
    if (!quiet) println("runAudit round $roundIdx")
    var allDone = true
    contestsNotDone.forEach { contestUA ->
        var allAssertionsDone = true
        contestUA.clcaAssertions.forEach { cassertion ->
            if (!cassertion.status.complete) {
                val testH0Result = auditClcaAssertion(auditConfig, contestUA, cassertion, cvrPairs, roundIdx, quiet=quiet)
                cassertion.status = testH0Result.status
                cassertion.round = roundIdx
                allAssertionsDone = allAssertionsDone && cassertion.status.complete
            }
        }
        if (allAssertionsDone) {
            contestUA.done = true
            contestUA.status = TestH0Status.StatRejectNull // TODO ???
        }
        allDone = allDone && contestUA.done

    }
    return allDone
}

fun auditClcaAssertion(
    auditConfig: AuditConfig,
    contestUA: ContestUnderAudit,
    cassertion: ClcaAssertion,
    cvrPairs: List<Pair<Cvr, Cvr>>, // (mvr, cvr)
    roundIdx: Int,
    quiet: Boolean = true,
): TestH0Result {
    val debug = false
    val cassorter = cassertion.cassorter
    val sampler = ComparisonWithoutReplacement(contestUA.contest, cvrPairs, cassorter, allowReset = false)

    val clcaConfig = auditConfig.clcaConfig
    val bettingFn: BettingFn = when (clcaConfig.strategy) {

        ClcaStrategyType.previous -> {
            // use previous round errors as apriori, then adapt to actual mvrs
            val phantomRate = contestUA.contest.phantomRate()
            val errorRates = if (roundIdx > 1) (cassertion.roundResults.last().errorRates!!) // TODO minimum phantomRate for p1o?
                    else if (phantomRate == 0.0) ErrorRates(0.0, 0.0, 0.0, 0.0) else ErrorRates(0.0, phantomRate, 0.0, 0.0)
            if (debugErrorRates) println(" previous audit round $roundIdx errorRates=$errorRates")
            AdaptiveComparison(
                Nc = contestUA.Nc,
                withoutReplacement = true,
                a = cassorter.noerror(),
                d = clcaConfig.d,
                errorRates
            )
        }

        ClcaStrategyType.phantoms -> {
            // use previous round errors as apriori, then adapt to actual mvrs
            val phantomRate = contestUA.contest.phantomRate()
            val errorRates = if (phantomRate == 0.0) ErrorRates(0.0, 0.0, 0.0, 0.0) else ErrorRates(0.0, phantomRate, 0.0, 0.0)
            if (debugErrorRates) println(" phantoms audit round $roundIdx errorRates=$errorRates")
            AdaptiveComparison(
                Nc = contestUA.Nc,
                withoutReplacement = true,
                a = cassorter.noerror(),
                d = clcaConfig.d,
                errorRates
            )
        }

        ClcaStrategyType.oracle -> {
            // use the actual errors comparing mvrs to cvrs. Testing only
            val errorRates = ClcaErrorRates.calcErrorRates(contestUA.id, cassorter, cvrPairs)
            OracleComparison(a = cassorter.noerror(), errorRates = errorRates)
        }

        ClcaStrategyType.noerror -> {
            // optimistic, no errors as apriori, then adapt to actual mvrs
            AdaptiveComparison(
                Nc = contestUA.Nc,
                withoutReplacement = true,
                a = cassorter.noerror(),
                d = clcaConfig.d,
                ErrorRates(0.0, 0.0, 0.0, 0.0)
            )
        }

        ClcaStrategyType.fuzzPct -> {
            // use computed errors as apriori, then adapt to actual mvrs.
            val errorRates = ClcaErrorRates.getErrorRates(contestUA.ncandidates, clcaConfig.simFuzzPct)
            if (debugErrorRates) println(" fuzzPct errorRates = ${errorRates} for round ${cassertion.roundResults.size + 1}")

            AdaptiveComparison(
                Nc = contestUA.Nc,
                withoutReplacement = true,
                a = cassorter.noerror(),
                d = clcaConfig.d,
                errorRates
            )
        }

        ClcaStrategyType.apriori ->
            // use given errors as apriori, then adapt to actual mvrs.
            AdaptiveComparison(
                Nc = contestUA.Nc,
                withoutReplacement = true,
                a = cassorter.noerror(),
                d = clcaConfig.d,
                clcaConfig.errorRates!!
            )
    }

    val testFn = BettingMart(
        bettingFn = bettingFn,
        Nc = contestUA.Nc,
        noerror = cassorter.noerror(),
        upperBound = cassorter.upperBound(),
        riskLimit = auditConfig.riskLimit,
        withoutReplacement = true
    )

    val testH0Result = testFn.testH0(sampler.maxSamples(), terminateOnNullReject = true) { sampler.sample() }

    val roundResult = AuditRoundResult(roundIdx,
        estSampleSize=cassertion.estSampleSize,
        maxBallotsUsed = sampler.maxSamplesUsed(),
        pvalue = testH0Result.pvalues.last(),
        samplesNeeded = testH0Result.pvalues.indexOfFirst{ it < auditConfig.riskLimit } + 1,
        samplesUsed = testH0Result.sampleCount,
        status = testH0Result.status,
        errorRates = testH0Result.errorRates
    )
    cassertion.roundResults.add(roundResult)

    if (!quiet) println(" ${contestUA.name} $roundResult")
    return testH0Result
}