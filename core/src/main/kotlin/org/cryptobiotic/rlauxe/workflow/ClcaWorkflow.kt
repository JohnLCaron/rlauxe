package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.util.*

class ClcaWorkflow(
    val auditConfig: AuditConfig,
    contestsToAudit: List<Contest>, // the contests you want to audit
    raireContests: List<RaireContestUnderAudit>, // TODO or call raire from here ??
    val cvrs: List<Cvr>, // includes undervotes and phantoms.
): RlauxWorkflowIF {
    private val contestsUA: List<ContestUnderAudit>
    private val cvrsUA: List<CvrUnderAudit>
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

        // the order of the cvrs cannot be changed.
        val prng = Prng(auditConfig.seed)
        cvrsUA = cvrs.mapIndexed { idx, it -> CvrUnderAudit(it, idx, prng.next()) }.sortedBy { it.sampleNumber() }
    }

    //  return allDone
    override fun runAudit(auditRound: AuditRound, mvrs: List<Cvr>, quiet: Boolean): Boolean  {
        return runClcaAudit(auditConfig, auditRound.contestRounds, auditRound.sampledIndices, mvrs, cvrs, 
            auditRound.roundIdx, auditor = AuditClcaAssertion())
    }

    override fun auditConfig() =  this.auditConfig
    override fun auditRounds() = auditRounds
    override fun contestsUA(): List<ContestUnderAudit> = contestsUA
    override fun cvrs() = cvrs
    override fun sortedBallotsOrCvrs() : List<BallotOrCvr> = cvrsUA // sorted by sampleNum
}

/////////////////////////////////////////////////////////////////////////////////

fun runClcaAudit(auditConfig: AuditConfig,
                 contests: List<ContestRound>,
                 sampleIndices: List<Int>,
                 mvrs: List<Cvr>,
                 cvrs: List<Cvr>,
                 roundIdx: Int,
                 auditor: ClcaAssertionAuditor): Boolean {

    val contestsNotDone = contests.filter{ !it.done }
    val sampledCvrs = sampleIndices.map { cvrs[it] }

    // prove that sampledCvrs correspond to mvrs
    require(sampledCvrs.size == mvrs.size)
    val cvrPairs: List<Pair<Cvr, Cvr>> = mvrs.zip(sampledCvrs)
    cvrPairs.forEach { (mvr, cvr) ->
        if (mvr.id != cvr.id)
            println("why")
        require(mvr.id == cvr.id)
    }

    var allDone = true
    contestsNotDone.forEach { contest ->
        if (contest.contestUA.contest.choiceFunction == SocialChoiceFunction.IRV) {
            println("here")
        }
        val contestAssertionStatus = mutableListOf<TestH0Status>()
        contest.assertionRounds.forEach { assertionRound ->
            if (!assertionRound.status.complete) {
                val cassertion = assertionRound.assertion as ClcaAssertion
                val cassorter = cassertion.cassorter
                val sampler = ClcaWithoutReplacement(contest.contestUA.id, cvrPairs, cassorter, allowReset = false)
                val testH0Result = auditor.run(auditConfig, contest.contestUA.contest, assertionRound, sampler, roundIdx)
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

fun interface ClcaAssertionAuditor {
    fun run(
        auditConfig: AuditConfig,
        contest: ContestIF,
        assertionRound: AssertionRound,
        // cvrPairs: List<Pair<Cvr, Cvr>>, // (mvr, cvr)
        sampler: Sampler,
        roundIdx: Int,
    ): TestH0Result
}

class AuditClcaAssertion(val quiet: Boolean = true): ClcaAssertionAuditor {

    override fun run(
        auditConfig: AuditConfig,
        contest: ContestIF,
        assertionRound: AssertionRound,
        // cvrPairs: List<Pair<Cvr, Cvr>>, // (mvr, cvr)
        sampler: Sampler,
        roundIdx: Int,
    ): TestH0Result {

        val cassertion = assertionRound.assertion as ClcaAssertion
        val cassorter = cassertion.cassorter

        // val sampler = ClcaWithoutReplacement(contest, cvrPairs, cassorter, allowReset = false)

        val clcaConfig = auditConfig.clcaConfig
        val errorRates: ClcaErrorRates = when (clcaConfig.strategy) {
            ClcaStrategyType.previous,
            ClcaStrategyType.phantoms -> {
                // use phantomRate as apriori
                ClcaErrorRates(0.0, contest.phantomRate(), 0.0, 0.0)
            }

            /* ClcaStrategyType.oracle -> {
                // use the actual errors comparing mvrs to cvrs. Testing only
                ClcaErrorTable.calcErrorRates(contest.id, cassorter, cvrPairs)
            } */

            ClcaStrategyType.oracle, // TODO: disabled
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

        assertionRound.auditResult = AuditRoundResult(
            roundIdx,
            nmvrs = sampler.maxSamples(),
            maxBallotIndexUsed = sampler.maxSampleIndexUsed(), // TODO only for audit, bot estimation I think
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
}