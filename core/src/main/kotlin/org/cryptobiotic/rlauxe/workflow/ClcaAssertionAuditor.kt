package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.BettingMart
import org.cryptobiotic.rlauxe.betting.ClcaSamplerErrorTracker
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting
import org.cryptobiotic.rlauxe.betting.TestH0Result
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.ConcurrentTask
import org.cryptobiotic.rlauxe.util.ConcurrentTaskRunner
import org.cryptobiotic.rlauxe.util.OnlyTask

private val logger = KotlinLogging.logger("ClcaAudit")

// run all contests and assertions for one round with the given auditor.
// return isComplete
fun runClcaAuditRound(
    config: Config,
    auditRound: AuditRound,
    mvrManager: MvrManager,
    roundIdx: Int,
    auditor: ClcaAssertionAuditorIF,
    parameters: Map<String, Any> = emptyMap(),
    onlyTask: OnlyTask? = null,
): Boolean {
    val cvrPairs = mvrManager.makeMvrCardPairsForRound(roundIdx)

    // parallelize over contests
    val contestsNotDone = auditRound.contestRounds.filter{ !it.done }
    val auditContestTasks = mutableListOf<RunClcaContestTask>()
    contestsNotDone.forEach { contest ->
        auditContestTasks.add( RunClcaContestTask(config, contest, cvrPairs, auditor, roundIdx, parameters, onlyTask) )
    }

    // run all tasks
    // logger.debug { "runClcaAuditRound ($roundIdx) ${auditContestTasks.size} tasks for auditor ${auditor.javaClass.simpleName} " }
    // println("---runClcaAuditRound running ${auditContestTasks.size} tasks")
    val complete: List<Boolean> = ConcurrentTaskRunner<Boolean>().run(auditContestTasks)

    // given the cvrPairs, and each ContestRound's maxSamplesUsed, count the cvrs that were not used
    val contestCounts = mutableMapOf<Int, Int>()
    var countUsed = 0
    var countUnused = 0
    cvrPairs.forEach { mvrCardPair ->
        val card = mvrCardPair.second
        var wasUsed = false
        contestsNotDone.forEach { contest ->
            val count = contestCounts.getOrPut(contest.id) { 0 }
            if (card.hasContest(contest.id)) {
                if (count < contest.maxSamplesUsed()) wasUsed = true
                contestCounts[contest.id] = count + 1
            }
        }
        if (wasUsed) countUsed++ else countUnused++
    }
    auditRound.mvrsUnused =  countUnused
    auditRound.mvrsUsed =  countUsed

    return if (complete.isEmpty()) true else complete.reduce { acc, b -> acc && b }
}

class RunClcaContestTask(
    val config: Config,
    val contestRound: ContestRound,
    val cvrPairs: List<Pair<CvrIF, AuditableCard>>, // Pair(mvr, card)
    val auditor: ClcaAssertionAuditorIF,
    val roundIdx: Int,
    val parameters: Map<String, Any>,
    val onlyTask: OnlyTask? = null,
): ConcurrentTask<Boolean> {

    override fun name() = "RunContestTask for ${contestRound.contestUA.name} round $roundIdx nassertions ${contestRound.assertionRounds.size}"

    override fun run(): Boolean {
        val contestAssertionStatus = mutableListOf<TestH0Status>()
        contestRound.assertionRounds.forEach { assertionRound ->
            val taskName = "${contestRound.contestUA.id}-${assertionRound.assertion.assorter.shortName()}"
            if (onlyTask == null || onlyTask.taskName == taskName) {

                if (!assertionRound.status.complete) {
                    val cassertion = assertionRound.assertion as ClcaAssertion
                    val cassorter = cassertion.cassorter

                    val debuggingId = parameters["cat"]?.toString() + "-" + parameters["phantom"]?.toString() // debugging
                    val sampler = ClcaSamplerErrorTracker.withMaxSample(
                        contestRound.id,
                        cassorter,
                        cvrPairs,
                        maxSampleIndex = contestRound.maxSampleAllowed,
                        name = debuggingId,
                    )

                    val testH0Result = auditor.run(config, contestRound, assertionRound, sampler, roundIdx)
                    assertionRound.status = testH0Result.status
                    if (testH0Result.status.complete) assertionRound.roundProved = roundIdx
                }
            }
            contestAssertionStatus.add(assertionRound.status)
        }
        if (contestAssertionStatus.isNotEmpty()) {
            contestRound.done = contestAssertionStatus.all { it.complete }
            contestRound.status = contestAssertionStatus.minBy { it.rank } // use lowest rank status.
        }
        return contestRound.done
    }
}

// abstraction so ClcaAudit can be used for OneAudit
fun interface ClcaAssertionAuditorIF {
    fun run(
        config: Config,
        contestRound: ContestRound,
        assertionRound: AssertionRound,
        samplerTracker: ClcaSamplerErrorTracker,
        roundIdx: Int,
    ): TestH0Result
}

class ClcaAssertionAuditor(val quiet: Boolean = true): ClcaAssertionAuditorIF {

    override fun run(
        config: Config,
        contestRound: ContestRound,
        assertionRound: AssertionRound,
        samplerTracker: ClcaSamplerErrorTracker,
        roundIdx: Int,
    ): TestH0Result {
        val contestUA = contestRound.contestUA
        val contest = contestUA.contest
        val cassertion = assertionRound.assertion as ClcaAssertion
        val cassorter = cassertion.cassorter
        val clcaConfig = config.round.clcaConfig!!

        val noerror=cassorter.noerror()
        val upper=cassorter.assorter.upperBound()
        val apriori = clcaConfig.apriori.makeErrorRates(noerror, upper)

        val bettingFn =
            // the actual audit cant "look ahead" with the measured error rates, so always start empty
            // OTOH, I think you could use apriori rates if they are set independently from the mvrs, see Issue #519
            GeneralAdaptiveBetting(
                contestUA.Npop,
                aprioriErrorRates = apriori,
                nphantoms = contest.Nphantoms(),
                maxLoss = clcaConfig.maxLoss,
                oaAssortRates=null,
                d = clcaConfig.d,
            )

        val testFn = BettingMart(
            bettingFn = bettingFn,
            N = contestUA.Npop,
            sampleUpperBound = cassorter.upperBound(),
            riskLimit = config.riskLimit,
            withoutReplacement = true,
            tracker = samplerTracker
        )
        testFn.setDebuggingSequences()

        val terminateOnNullReject = !config.creation.isRiskMeasuringAudit()
        val testH0Result = testFn.testH0(samplerTracker.maxSamples(), terminateOnNullReject = terminateOnNullReject) { samplerTracker.sample() }
        if (!testH0Result.status.success && showFail) {
            println("TestH0Result est ${assertionRound.estMvrs} max= ${samplerTracker.maxSamples()} used=${testH0Result.sampleCount} plast = ${testH0Result.pvalueLast}")
            println("   errors = ${samplerTracker.measuredClcaErrorCounts()}")
        }

        assertionRound.auditResult = AuditRoundResult(
            roundIdx,
            nmvrs = samplerTracker.maxSamples(),
            plast = testH0Result.pvalueLast,
            pmin = testH0Result.pvalueMin,
            samplesUsed = testH0Result.sampleCount,
            status = testH0Result.status,
            clcaErrorTracker = samplerTracker.clcaErrorTracker,
        )

        if (!quiet) {
            logger.debug{" (${contest.id}) ${contest.name} ${cassertion} ${assertionRound.auditResult}"}
        }
        return testH0Result
    }
}

val showFail = false