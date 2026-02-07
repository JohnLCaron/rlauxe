package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.BettingMart
import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.betting.ClcaSamplerErrorTracker
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting
import org.cryptobiotic.rlauxe.betting.ErrorTracker
import org.cryptobiotic.rlauxe.betting.SamplerTracker
import org.cryptobiotic.rlauxe.betting.TestH0Result
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskRunnerG

private val logger = KotlinLogging.logger("ClcaAudit")

// run all contests and assertions for one round with the given auditor.
// return isComplete
fun runClcaAuditRound(
    config: AuditConfig,
    auditRound: AuditRound,
    mvrManager: MvrManager,
    roundIdx: Int,
    auditor: ClcaAssertionAuditorIF,
): Boolean {
    val cvrPairs = mvrManager.makeMvrCardPairsForRound(roundIdx)

    // parallelize over contests
    val contestsNotDone = auditRound.contestRounds.filter{ !it.done }
    val auditContestTasks = mutableListOf<RunClcaContestTask>()
    contestsNotDone.forEach { contest ->
        auditContestTasks.add(RunClcaContestTask(config, contest, cvrPairs, auditor, roundIdx))
    }

    // run all tasks
    // logger.debug { "runClcaAuditRound ($roundIdx) ${auditContestTasks.size} tasks for auditor ${auditor.javaClass.simpleName} " }
    // println("---runClcaAuditRound running ${auditContestTasks.size} tasks")
    val complete: List<Boolean> = ConcurrentTaskRunnerG<Boolean>().run(auditContestTasks)

    // given the cvrPairs, and each ContestRound's maxSampleIndexUsed, count the cvrs that were not used
    val contestCounts = mutableMapOf<Int, Int>()
    var countUnused = 0
    cvrPairs.forEachIndexed { idx, mvrCardPair ->
        val card = mvrCardPair.second
        var wasUsed = false
        contestsNotDone.forEach { contest ->
            val count = contestCounts.getOrPut(contest.id) { 0 }
            if (card.hasContest(contest.id)) {
                if (count < contest.countCvrsUsedInAudit()) wasUsed = true
                contestCounts[contest.id] = count + 1
            }
        }
        if (!wasUsed) countUnused++
        //if (idx % 100 == 0)
        //    println("$idx, $countUnused")
    }
    auditRound.samplesNotUsed =  countUnused

    return if (complete.isEmpty()) true else complete.reduce { acc, b -> acc && b }
}

class RunClcaContestTask(
    val config: AuditConfig,
    val contestRound: ContestRound,
    val cvrPairs: List<Pair<CvrIF, AuditableCard>>, // Pair(mvr, card)
    val auditor: ClcaAssertionAuditorIF,
    val roundIdx: Int): ConcurrentTaskG<Boolean> {

    override fun name() = "RunContestTask for ${contestRound.contestUA.name} round $roundIdx nassertions ${contestRound.assertionRounds.size}"

    override fun run(): Boolean {
        val contestAssertionStatus = mutableListOf<TestH0Status>()
        contestRound.assertionRounds.forEach { assertionRound ->
            if (!assertionRound.status.complete) {
                val cassertion = assertionRound.assertion as ClcaAssertion
                val cassorter = cassertion.cassorter

                val sampler = ClcaSamplerErrorTracker.withMaxSample(
                    contestRound.id,
                    cassorter,
                    cvrPairs,
                )

                val testH0Result = auditor.run(config, contestRound, assertionRound, sampler, roundIdx)
                assertionRound.status = testH0Result.status
                if (testH0Result.status.complete) assertionRound.roundProved = roundIdx
            }
            contestAssertionStatus.add(assertionRound.status)
        }
        contestRound.done = contestAssertionStatus.all { it.complete }
        contestRound.status = contestAssertionStatus.minBy { it.rank } // use lowest rank status.
        return contestRound.done
    }
}

// abstraction so ClcaAudit can be used for OneAudit
fun interface ClcaAssertionAuditorIF {
    fun run(
        config: AuditConfig,
        contestRound: ContestRound,
        assertionRound: AssertionRound,
        samplerTracker: SamplerTracker,
        roundIdx: Int,
    ): TestH0Result
}

class ClcaAssertionAuditor(val quiet: Boolean = true): ClcaAssertionAuditorIF {

    override fun run(
        config: AuditConfig,
        contestRound: ContestRound,
        assertionRound: AssertionRound,
        samplerTracker: SamplerTracker,
        roundIdx: Int,
    ): TestH0Result {
        val contestUA = contestRound.contestUA
        val contest = contestUA.contest
        val cassertion = assertionRound.assertion as ClcaAssertion
        val cassorter = cassertion.cassorter
        val clcaConfig = config.clcaConfig

        val bettingFn = // if (clcaConfig.strategy == ClcaStrategyType.generalAdaptive) {
            GeneralAdaptiveBetting(
                contestUA.Npop,
                // the actual audit cant "look ahead" with the measured error rates, so always start empty
                // OTOH, I think you could use apriori rates if they are set independently from the mcrs
                // TODO see Issue #519
                startingErrors = ClcaErrorCounts.empty(cassorter.noerror(), cassorter.assorter.upperBound()),
                contest.Nphantoms(),
                oaAssortRates = null,
                d = clcaConfig.d,
                maxLoss = clcaConfig.maxLoss)

        val testFn = BettingMart(
            bettingFn = bettingFn,
            N = contestUA.Npop,
            sampleUpperBound = cassorter.upperBound(),
            riskLimit = config.riskLimit,
            withoutReplacement = true,
            tracker = samplerTracker
        )
        testFn.setDebuggingSequences()

        val terminateOnNullReject = config.auditSampleLimit == null
        val testH0Result = testFn.testH0(samplerTracker.maxSamples(), terminateOnNullReject = terminateOnNullReject) { samplerTracker.sample() }

        val measuredCounts: ClcaErrorCounts? = if (samplerTracker is ErrorTracker) samplerTracker.measuredClcaErrorCounts() else null
        assertionRound.auditResult = AuditRoundResult(
            roundIdx,
            nmvrs = samplerTracker.maxSamples(),
            // countCvrsUsedInAudit = samplerTracker.countCvrsUsedInAudit(),
            plast = testH0Result.pvalueLast,
            pmin = testH0Result.pvalueMin,
            samplesUsed = testH0Result.sampleCount,
            status = testH0Result.status,
            measuredCounts = measuredCounts,
        )

        if (!quiet) {
            logger.debug{" (${contest.id}) ${contest.name} ${cassertion} ${assertionRound.auditResult}"}
        }
        return testH0Result
    }
}