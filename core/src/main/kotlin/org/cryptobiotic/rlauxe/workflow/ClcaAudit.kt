package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskRunnerG
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit

private val logger = KotlinLogging.logger("ClcaAudit")

class ClcaAudit(
    val auditConfig: AuditConfig,
    contestsToAudit: List<Contest>, // the contests you want to audit
    raireContests: List<RaireContestUnderAudit>,
    val mvrManager: MvrManagerClcaIF,
): RlauxAuditIF {
    private val contestsUA: List<ContestUnderAudit>
    private val auditRounds = mutableListOf<AuditRound>()

    init {
        require (auditConfig.auditType == AuditType.CLCA)

        val regularContests = contestsToAudit.map { ContestUnderAudit(it, isComparison=true, auditConfig.hasStyles) }
        contestsUA = regularContests + raireContests

        contestsUA.forEach { contest ->
            contest.makeClcaAssertionsFromReportedMargin()
        }

        /* TODO only check regular contests ??
        check(auditConfig, contests)
        // TODO filter out contests that are done... */
    }

    override fun runAuditRound(auditRound: AuditRound, quiet: Boolean): Boolean  {
        val complete = runClcaAudit(auditConfig, auditRound.contestRounds, mvrManager, auditRound.roundIdx,
            auditor = AuditClcaAssertion(quiet)
        )
        auditRound.auditWasDone = true
        auditRound.auditIsComplete = complete
        return complete
    }

    override fun auditConfig() =  this.auditConfig
    override fun auditRounds() = auditRounds
    override fun contestsUA(): List<ContestUnderAudit> = contestsUA
    override fun mvrManager() = mvrManager
}

/////////////////////////////////////////////////////////////////////////////////

// run all contests and assertions for one round with the given auditor
fun runClcaAudit(auditConfig: AuditConfig,
                 contests: List<ContestRound>,
                 mvrManager: MvrManagerClcaIF,
                 roundIdx: Int,
                 auditor: ClcaAssertionAuditor,
): Boolean {
    val cvrPairs = mvrManager.makeCvrPairsForRound() // same over all contests!

    // parallelize over contests
    val contestsNotDone = contests.filter{ !it.done }
    val auditContestTasks = mutableListOf<RunContestTask>()
    contestsNotDone.forEach { contest ->
        auditContestTasks.add(RunContestTask(auditConfig, contest, cvrPairs, auditor, roundIdx))
    }

    logger.info { "Run ${auditContestTasks.size} tasks for auditor ${auditor.javaClass.name} " }

    val complete: List<Boolean> = ConcurrentTaskRunnerG<Boolean>().run(auditContestTasks)
    return complete.reduce { acc, b -> acc && b }
}

class RunContestTask(
    val config: AuditConfig,
    val contest: ContestRound,
    val cvrPairs: List<Pair<Cvr, Cvr>>,
    val auditor: ClcaAssertionAuditor,
    val roundIdx: Int): ConcurrentTaskG<Boolean> {

    override fun name() = "RunContestTask for ${contest.contestUA.name} round $roundIdx nassertions ${contest.assertionRounds.size}"

    override fun run(): Boolean {
        val contestAssertionStatus = mutableListOf<TestH0Status>()
        contest.assertionRounds.forEach { assertionRound ->
            if (!assertionRound.status.complete) {
                val cassertion = assertionRound.assertion as ClcaAssertion
                val cassorter = cassertion.cassorter
                val sampler =  ClcaWithoutReplacement(contest.id, config.hasStyles, cvrPairs, cassorter, allowReset = false)

                val testH0Result = auditor.run(config, contest.contestUA.contest, assertionRound, sampler, roundIdx)
                assertionRound.status = testH0Result.status
                if (testH0Result.status.complete) assertionRound.round = roundIdx
            }
            contestAssertionStatus.add(assertionRound.status)
        }
        contest.done = contestAssertionStatus.all { it.complete }
        contest.status = contestAssertionStatus.minBy { it.rank } // use lowest rank status.
        return contest.done
    }
}

// abstraction so ClcaAudit can be used for OneAudit
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
            ClcaStrategyType.phantoms
                -> {
                // use phantomRate as apriori
                ClcaErrorRates(0.0, contest.phantomRate(), 0.0, 0.0)
            }

            /* ClcaStrategyType.oracle -> {
                // use the actual errors comparing mvrs to cvrs. Testing only
                ClcaErrorTable.calcErrorRates(contest.id, cassorter, cvrPairs)
            } */

            ClcaStrategyType.oracle, // TODO: disabled
            ClcaStrategyType.noerror
                -> {
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
            AdaptiveBetting(Nc = contest.Nc(), a = cassorter.noerror(), d = clcaConfig.d, errorRates = errorRates)
        }

        val testFn = BettingMart(
            bettingFn = bettingFn,
            Nc = contest.Nc(),
            noerror = cassorter.noerror(),
            upperBound = cassorter.upperBound(),
            riskLimit = auditConfig.riskLimit,
            withoutReplacement = true
        )
        // testFn.setDebuggingSequences()

        val testH0Result = testFn.testH0(sampler.maxSamples(), terminateOnNullReject = true) { sampler.sample() }

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

        if (!quiet) logger.debug{" (${contest.id}) ${contest.name} ${cassertion} ${assertionRound.auditResult}"}
        return testH0Result
    }
}