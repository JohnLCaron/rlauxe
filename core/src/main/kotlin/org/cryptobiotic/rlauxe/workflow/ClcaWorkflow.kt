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
): RlauxWorkflowIF {
    private val contestsUA: List<ContestUnderAudit>
    val cvrsUA: List<CvrUnderAudit>
    private val auditRounds = mutableListOf<AuditRound>()

    init {
        require (auditConfig.auditType == AuditType.CLCA)

        val regularContests = contestsToAudit.map { ContestUnderAudit(it, isComparison=true, auditConfig.hasStyles) }

        contestsUA = regularContests + raireContests
        contestsUA.forEach { contest ->
            contest.makeClcaAssertions(cvrs)
        }

        /* TODO only check regular contests ??
        check(auditConfig, contests)
        // TODO filter out contests that are done... */

        val prng = Prng(auditConfig.seed)
        cvrsUA = cvrs.map { CvrUnderAudit(it, prng.next()) }
    }

    override fun startNewRound(quiet: Boolean): AuditRound {
        val previousRound = if (auditRounds.isEmpty()) null else auditRounds.last()
        val roundIdx = auditRounds.size + 1

        val auditRound = if (previousRound == null) {
            val contestRounds = contestsUA.map { ContestRound(it, roundIdx) }
            AuditRound(roundIdx, contests = contestRounds, sampledIndices = emptyList())
        } else {
            previousRound.createNextRound()
        }
        auditRounds.add(auditRound)

        estimateSampleSizes(
            auditConfig,
            auditRound,
            cvrs,
            show=!quiet,
        )

        auditRound.sampledIndices = sample(this, auditRound, auditRounds.previousSamples(roundIdx), quiet)
        return auditRound
    }

    //  return allDone
    override fun runAudit(auditRound: AuditRound, mvrs: List<Cvr>, quiet: Boolean): Boolean  { // return allDone
        return runClcaAudit(auditConfig, auditRound.contests, auditRound.sampledIndices, mvrs, cvrs, auditRound.roundIdx, quiet)
    }

    override fun auditConfig() =  this.auditConfig
    override fun getContests(): List<ContestUnderAudit> = contestsUA
    override fun getBallotsOrCvrs() : List<BallotOrCvr> = cvrsUA
}

/////////////////////////////////////////////////////////////////////////////////

// TODO lot of common code between the audit types...
fun runClcaAudit(auditConfig: AuditConfig,
                 contests: List<ContestRound>,
                 sampleIndices: List<Int>,
                 mvrs: List<Cvr>,
                 cvrs: List<Cvr>,
                 roundIdx: Int,
                 quiet: Boolean): Boolean {

    val contestsNotDone = contests.filter{ !it.done }
    val sampledCvrs = sampleIndices.map { cvrs[it] }

    // prove that sampledCvrs correspond to mvrs
    require(sampledCvrs.size == mvrs.size)
    val cvrPairs: List<Pair<Cvr, Cvr>> = mvrs.zip(sampledCvrs)
    cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id) }

    if (!quiet) println("runAudit round $roundIdx")
    var allDone = true
    contestsNotDone.forEach { contest ->
        if (contest.contestUA.contest.choiceFunction == SocialChoiceFunction.IRV) {
            println("here")
        }
        val contestAssertionStatus = mutableListOf<TestH0Status>()
        contest.assertions.forEach { assertionRound ->
            if (!assertionRound.status.complete) {
                val testH0Result = auditClcaAssertion(auditConfig, contest.contestUA.contest, assertionRound, cvrPairs, roundIdx, quiet=quiet)
                assertionRound.status = testH0Result.status
                if (testH0Result.status.complete) assertionRound.round = roundIdx
            }
            contestAssertionStatus.add(assertionRound.status)
        }
        contest.done = contestAssertionStatus.all { it.complete }
        contest.status = contestAssertionStatus.minBy { it.rank } // use lowest rank status.
        allDone = allDone && contest.done
    }
    return allDone
}

fun auditClcaAssertion(
    auditConfig: AuditConfig,
    contest: ContestIF,
    assertionRound: AssertionRound,
    cvrPairs: List<Pair<Cvr, Cvr>>, // (mvr, cvr)
    roundIdx: Int,
    quiet: Boolean = true,
): TestH0Result {
    val cassertion = assertionRound.assertion as ClcaAssertion
    val cassorter = cassertion.cassorter
    val sampler = ClcaWithoutReplacement(contest, cvrPairs, cassorter, allowReset = false)

    val clcaConfig = auditConfig.clcaConfig
    val errorRates: ClcaErrorRates = when (clcaConfig.strategy) {
        ClcaStrategyType.previous,
        ClcaStrategyType.phantoms -> {
            // use phantomRate as apriori
            ClcaErrorRates(0.0, contest.phantomRate(), 0.0, 0.0)
        }

        ClcaStrategyType.oracle -> {
            // use the actual errors comparing mvrs to cvrs. Testing only
            ClcaErrorTable.calcErrorRates(contest.id, cassorter, cvrPairs)
        }

        ClcaStrategyType.noerror -> {
            ClcaErrorRates(0.0, 0.0, 0.0, 0.0)
        }

        ClcaStrategyType.fuzzPct -> {
            // use computed errors as apriori
            ClcaErrorTable.getErrorRates(contest.ncandidates, clcaConfig.simFuzzPct)
        }

        ClcaStrategyType.apriori ->
            // use given errors as apriori
            clcaConfig.errorRates!!
    }

    val bettingFn: BettingFn = if (clcaConfig.strategy == ClcaStrategyType.oracle) {
        OracleComparison(a = cassorter.noerror(), errorRates = errorRates)
    } else {
        AdaptiveComparison(Nc = contest.Nc, a = cassorter.noerror(), d = clcaConfig.d, errorRates = errorRates)
    }

    val testFn = BettingMart(
        bettingFn = bettingFn,
        Nc = contest.Nc,
        noerror = cassorter.noerror(),
        upperBound = cassorter.upperBound(),
        riskLimit = auditConfig.riskLimit,
        withoutReplacement = true
    )

    val testH0Result = testFn.testH0(sampler.maxSamples(), terminateOnNullReject = true) { sampler.sample() }

    assertionRound.auditResult  = AuditRoundResult(roundIdx,
        nmvrs = sampler.maxSamples(),
        maxBallotIndexUsed = sampler.maxSampleIndexUsed(),
        pvalue = testH0Result.pvalueLast,
        samplesNeeded = testH0Result.sampleFirstUnderLimit, // one based
        samplesUsed = testH0Result.sampleCount,
        status = testH0Result.status,
        measuredMean = testH0Result.tracker.mean(),
        startingRates = errorRates,
        measuredRates = testH0Result.tracker.errorRates(),
    )

    if (!quiet) println(" ${contest.info.name} ${cassertion} ${assertionRound.auditResult}")
    return testH0Result
}