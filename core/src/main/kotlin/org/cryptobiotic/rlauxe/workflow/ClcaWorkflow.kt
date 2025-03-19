package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit

// what if the workflows use List, put PersistentWorkflow doesnt ??
class ClcaWorkflow(
    val auditConfig: AuditConfig,
    contestsToAudit: List<Contest>, // the contests you want to audit
    raireContests: List<RaireContestUnderAudit>, // TODO or call raire from here ??
    val ballotCards: BallotCardsClcaStart, // mutable
): RlauxWorkflowIF {
    private val contestsUA: List<ContestUnderAudit>
    private val auditRounds = mutableListOf<AuditRound>()

    init {
        require (auditConfig.auditType == AuditType.CLCA)

        val regularContests = contestsToAudit.map { ContestUnderAudit(it, isComparison=true, auditConfig.hasStyles) }

        contestsUA = regularContests + raireContests
        contestsUA.forEach { contest ->
            contest.makeClcaAssertions(ballotCards.cvrs)
        }

        /* TODO only check regular contests ??
        check(auditConfig, contests)
        // TODO filter out contests that are done... */
    }

    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>) {
        ballotCards.setMvrsBySampleNumber(sampleNumbers)
    }

    //  return complete
    override fun runAudit(auditRound: AuditRound, quiet: Boolean): Boolean  {
        val complete = runClcaAudit(auditConfig, auditRound.contestRounds, ballotCards, auditRound.roundIdx,
            auditor = AuditClcaAssertion(quiet))
        auditRound.auditWasDone = true
        auditRound.auditIsComplete = complete
        return complete
    }

    override fun auditConfig() =  this.auditConfig
    override fun auditRounds() = auditRounds
    override fun contestsUA(): List<ContestUnderAudit> = contestsUA
    override fun ballotCards() = ballotCards
}

/////////////////////////////////////////////////////////////////////////////////

// run all contests and assertion - TODO parellelize?
fun runClcaAudit(auditConfig: AuditConfig,
                 contests: List<ContestRound>,
                 ballotCards: BallotCardsClca,
                 roundIdx: Int,
                 auditor: ClcaAssertionAuditor): Boolean {

    val contestsNotDone = contests.filter{ !it.done }

    var allDone = true
    contestsNotDone.forEach { contest ->
        val contestAssertionStatus = mutableListOf<TestH0Status>()
        contest.assertionRounds.forEach { assertionRound ->
            if (!assertionRound.status.complete) {
                val cassertion = assertionRound.assertion as ClcaAssertion
                val cassorter = cassertion.cassorter
                val sampler = ballotCards.makeSampler(contest.id, cassorter, allowReset = false)

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
        sampler: Sampler,
        roundIdx: Int,
    ): TestH0Result
}

class AuditClcaAssertion(val quiet: Boolean = true): ClcaAssertionAuditor {

    override fun run(
        auditConfig: AuditConfig,
        contest: ContestIF,
        assertionRound: AssertionRound,
        sampler: Sampler,
        roundIdx: Int,
    ): TestH0Result {

        val cassertion = assertionRound.assertion as ClcaAssertion
        val cassorter = cassertion.cassorter

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
            AdaptiveBetting(Nc = contest.Nc, a = cassorter.noerror(), d = clcaConfig.d, errorRates = errorRates)
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
        /* TODO this is left over from when we ran testH0Result with terminateOnNullReject = false
        //    I think we could probably just use testH0Result.sampleCount and ignore sampleFirstUnderLimit
        val samplesNeeded = if (testH0Result.sampleFirstUnderLimit > 0) testH0Result.sampleFirstUnderLimit else testH0Result.sampleCount
        if (testH0Result.sampleFirstUnderLimit > 0 && (testH0Result.sampleFirstUnderLimit != testH0Result.sampleCount + 1)) {
            println("WARNING: sampleFirstUnderLimit = ${testH0Result.sampleFirstUnderLimit} != sampleCount = ${testH0Result.sampleCount}")
        } */

        assertionRound.auditResult = AuditRoundResult(
            roundIdx,
            nmvrs = sampler.maxSamples(),
            maxBallotIndexUsed = sampler.maxSampleIndexUsed(), // TODO only for audit, not estimation I think
            pvalue = testH0Result.pvalueLast,
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