package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.estimate.*
import org.cryptobiotic.rlauxe.util.*

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
        require (auditConfig.auditType == AuditType.CLCA)

        val regularContests = contestsToAudit.map { ContestUnderAudit(it, isComparison=true, auditConfig.hasStyles) }

        contestsUA = regularContests + raireContests
        contestsUA.forEach { contest ->
            contest.makeClcaAssertions(cvrs)
        }

        // only check regular contests
        check(auditConfig, regularContests)

        val prng = Prng(auditConfig.seed)
        cvrsUA = cvrs.map { CvrUnderAudit(it, prng.next()) }
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

        return sample(this, roundIdx, quiet)
    }

    //  return allDone
    override fun runAudit(sampleIndices: List<Int>, mvrs: List<Cvr>, roundIdx: Int): Boolean {
        return runClcaAudit(auditConfig, contestsUA, sampleIndices, mvrs, cvrs, roundIdx, quiet)
    }

    override fun auditConfig() =  this.auditConfig
    override fun getContests(): List<ContestUnderAudit> = contestsUA
    override fun getBallotsOrCvrs() : List<BallotOrCvr> = cvrsUA
}

/////////////////////////////////////////////////////////////////////////////////

// TODO lot of common code between the audit types...
fun runClcaAudit(auditConfig: AuditConfig,
                 contestsUA: List<ContestUnderAudit>,
                 sampleIndices: List<Int>,
                 mvrs: List<Cvr>,
                 cvrs: List<Cvr>,
                 roundIdx: Int,
                 quiet: Boolean): Boolean {

    val contestsNotDone = contestsUA.filter{ !it.done }
    val sampledCvrs = sampleIndices.map { cvrs[it] }

    // prove that sampledCvrs correspond to mvrs
    require(sampledCvrs.size == mvrs.size)
    val cvrPairs: List<Pair<Cvr, Cvr>> = mvrs.zip(sampledCvrs)
    cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id) }

    if (!quiet) println("runAudit round $roundIdx")
    var allDone = true
    contestsNotDone.forEach { contestUA ->
        if (contestUA.contest.choiceFunction == SocialChoiceFunction.IRV) {
            println("here")
        }
        var contestAssertionStatus = mutableListOf<TestH0Status>()
        contestUA.clcaAssertions.forEach { cassertion ->
            if (!cassertion.status.complete) {
                val testH0Result = auditClcaAssertion(auditConfig, contestUA, cassertion, cvrPairs, roundIdx, quiet=quiet)
                cassertion.status = testH0Result.status
                if (testH0Result.status.complete) cassertion.round = roundIdx
            }
            contestAssertionStatus.add(cassertion.status)
        }
        contestUA.done = contestAssertionStatus.all { it.complete }
        contestUA.status = contestAssertionStatus.minBy { it.rank } // use lowest rank status.
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
    val cassorter = cassertion.cassorter
    val sampler = ClcaWithoutReplacement(contestUA.contest, cvrPairs, cassorter, allowReset = false)

    val clcaConfig = auditConfig.clcaConfig
    val errorRates: ClcaErrorRates = when (clcaConfig.strategy) {
        ClcaStrategyType.previous,
        ClcaStrategyType.phantoms -> {
            // use phantomRate as apriori
            ClcaErrorRates(0.0, contestUA.contest.phantomRate(), 0.0, 0.0)
        }

        ClcaStrategyType.oracle -> {
            // use the actual errors comparing mvrs to cvrs. Testing only
            ClcaErrorTable.calcErrorRates(contestUA.id, cassorter, cvrPairs)
        }

        ClcaStrategyType.noerror -> {
            ClcaErrorRates(0.0, 0.0, 0.0, 0.0)
        }

        ClcaStrategyType.fuzzPct -> {
            // use computed errors as apriori
            ClcaErrorTable.getErrorRates(contestUA.ncandidates, clcaConfig.simFuzzPct)
        }

        ClcaStrategyType.apriori ->
            // use given errors as apriori
            clcaConfig.errorRates!!
    }

    val bettingFn: BettingFn = if (clcaConfig.strategy == ClcaStrategyType.oracle) {
        OracleComparison(a = cassorter.noerror(), errorRates = errorRates)
    } else {
        AdaptiveComparison(Nc = contestUA.Nc, a = cassorter.noerror(), d = clcaConfig.d, errorRates = errorRates)
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
        maxBallotIndexUsed = sampler.maxSampleIndexUsed(),
        pvalue = testH0Result.pvalueLast,
        samplesNeeded = testH0Result.sampleFirstUnderLimit, // one based
        samplesUsed = testH0Result.sampleCount,
        status = testH0Result.status,
        measuredMean = testH0Result.tracker.mean(),
        startingRates = errorRates,
        measuredRates = testH0Result.tracker.errorRates(),
    )
    cassertion.roundResults.add(roundResult)

    if (!quiet) println(" ${contestUA.name} ${cassertion} $roundResult")
    return testH0Result
}