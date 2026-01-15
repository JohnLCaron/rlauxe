package org.cryptobiotic.rlauxe.audit

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.betting.ClcaErrorTracker
import org.cryptobiotic.rlauxe.core.CvrIF
import org.cryptobiotic.rlauxe.core.TestH0Result
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import org.cryptobiotic.rlauxe.workflow.ClcaAssertionAuditor
import org.cryptobiotic.rlauxe.workflow.ClcaSampler
import org.cryptobiotic.rlauxe.workflow.OneAuditAssertionAuditor
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import org.cryptobiotic.rlauxe.workflow.PollingSampler
import org.cryptobiotic.rlauxe.workflow.auditPollingAssertion
import java.nio.file.Files.notExists
import java.nio.file.Path
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger("RunAudit")

// from rlauxe-viewer
fun runRound(inputDir: String, onlyTask: String? = null): AuditRound? {
    val roundResult = runRoundResult(inputDir, onlyTask)
    if (roundResult is Err) {
        logger.error{"runRoundResult failed ${roundResult.error}"}
        return null
    }
    return roundResult.unwrap()
}

fun runRoundResult(inputDir: String, onlyTask: String? = null): Result<AuditRound, ErrorMessages> {
    val errs = ErrorMessages("runRoundResult")

    try {
        if (notExists(Path.of(inputDir))) {
            return errs.add( "runRoundResult Audit Directory $inputDir does not exist" )
        }
        logger.info { "runRound on Audit in $inputDir" }

        val rlauxAudit = PersistedWorkflow(inputDir)
        var roundIdx = 0
        var complete = false

        if (!rlauxAudit.auditRounds().isEmpty()) {
            val lastRound = rlauxAudit.auditRounds().last()
            roundIdx = lastRound.roundIdx

            if (!lastRound.auditWasDone) {
                logger.info { "Run audit round ${lastRound.roundIdx}" }
                val roundStopwatch = Stopwatch()
                // run the audit for this round
                complete = rlauxAudit.runAuditRound(lastRound)
                logger.info { "  complete=$complete took ${roundStopwatch.elapsed(TimeUnit.MILLISECONDS)} ms" }

            } else {
                complete = lastRound.auditIsComplete
            }
        }

        if (!complete) {
            roundIdx++
            // start next round and estimate sample sizes
            logger.info { "Start audit round $roundIdx using ${rlauxAudit}" }
            val nextRound = rlauxAudit.startNewRound(quiet = false, onlyTask)
            logger.info { "nextRound ${nextRound.show()}" }
            return Ok(nextRound)

        } else {
            val lastRound = rlauxAudit.auditRounds().last()
            logger.info { "runRound ${lastRound.roundIdx} complete = $complete" }
            return Ok(lastRound)
        }

    } catch (t: Throwable) {
        logger.error(t) { "runRoundResult Exception" }
        return errs.add( t.message ?: t.toString())
    }
}

// for debugging, transparency. rlauxe-viewer
fun runRoundAgain(auditDir: String, contestRound: ContestRound, assertionRound: AssertionRound, auditRoundResult: AuditRoundResult): String {
    val contestId = contestRound.contestUA.id
    try {
        if (notExists(Path.of(auditDir))) {
            logger.warn { "Audit Directory $auditDir does not exist" }
            return "Audit Directory $auditDir does not exist"
        }
        val roundIdx = auditRoundResult.roundIdx
        val assertion = assertionRound.assertion
        logger.info { "runAudit in $auditDir for round $roundIdx, contest $contestId, and assertion $assertion" }

        val workflow = PersistedWorkflow(auditDir, mvrWrite=false)
        val cvrPairs = workflow.mvrManager().makeMvrCardPairsForRound(roundIdx)
        val sampler = PairSampler(contestId, cvrPairs)

        val config = workflow.auditConfig()

        val testH0Result =  when (config.auditType) {
            AuditType.CLCA -> runClcaAudit(config, cvrPairs, contestRound, assertionRound, auditRoundResult)
            AuditType.POLLING -> runPollingAudit(config, cvrPairs, contestRound, assertionRound, auditRoundResult)
            AuditType.ONEAUDIT -> runOneAudit(config, cvrPairs,
                workflow.mvrManager().oapools()!!,
                contestRound, assertionRound, auditRoundResult)
        }

        return if (testH0Result == null) "failed" else buildString {
            appendLine("contest $contestId assertion win/lose = ${assertion.assorter.winLose()}")
            val tracker = testH0Result.tracker
            if (tracker is ClcaErrorTracker && tracker.sequences != null) {
                val seq = tracker.sequences!!
                val pvalues = seq.pvalues()
                val count = seq.xs.size
                append(" i, ${sfn("xs", 6)}, ${sfn("bet", 6)}, ${sfn("tj", 6)}, ${sfn("Tj", 6)}, ${sfn("pvalue", 8)}, ")
                appendLine("${sfn("location", 10)}, ${sfn("mvr votes", 10)}, ${sfn("card", 10)}")
                repeat(count) {
                    append("${nfn(it+1, 2)}, ${df(seq.xs[it])}, ${df(seq.bets[it])}, ${df(seq.tjs[it])}")
                    append(", ${trunc(seq.testStatistics[it].toString(), 6)}, ${trunc(pvalues[it].toString(), 8)}")
                    val pair = sampler.next()
                    val mvrVotes = pair.first.votes(contestId)?.contentToString() ?: "missing"
                    val card = pair.second
                    val cardVotes = card.votes(contestId)?.contentToString() ?: "N/A"
                    append(", ${sfn(pair.first.location(), 10)}")
                    append(", ${sfn(mvrVotes, 10)}")
                    append(", votes=${cardVotes} possible=${card.hasContest(contestId)} pool=${card.poolId()}, ")
                    appendLine()
                }
            }
        }

    } catch (t: Throwable) {
        logger.error {t}
        t.printStackTrace()
        return t.message ?: "none"
    }
}

fun runClcaAudit(config: AuditConfig, cvrPairs: List<Pair<CvrIF, AuditableCard>>, contestRound: ContestRound, assertionRound: AssertionRound, auditRoundResult: AuditRoundResult): TestH0Result? {
    try {
        val auditor = ClcaAssertionAuditor()

        val cassertion = assertionRound.assertion as ClcaAssertion
        val cassorter = cassertion.cassorter
        val sampler = ClcaSampler(contestRound.id, cvrPairs, cassorter, allowReset = false)

        val testH0Result = auditor.run(config, contestRound, assertionRound, sampler, auditRoundResult.roundIdx)

        return testH0Result

    } catch (t: Throwable) {
        logger.error {t}
        t.printStackTrace()
        return null
    }
}

fun runOneAudit(config: AuditConfig, cvrPairs: List<Pair<CvrIF, AuditableCard>>, pools: List<OneAuditPoolIF>, contestRound: ContestRound, assertionRound: AssertionRound, auditRoundResult: AuditRoundResult): TestH0Result? {
    try {
        val auditor = OneAuditAssertionAuditor(pools)
        val cassertion = assertionRound.assertion as ClcaAssertion
        val cassorter = cassertion.cassorter
        val sampler = ClcaSampler(contestRound.id, cvrPairs, cassorter, allowReset = false)

        val testH0Result = auditor.run(config, contestRound, assertionRound, sampler, auditRoundResult.roundIdx)

        return testH0Result

    } catch (t: Throwable) {
        logger.error {t}
        t.printStackTrace()
        return null
    }
}

fun runPollingAudit(config: AuditConfig, cvrPairs: List<Pair<CvrIF, CvrIF>>, contestRound: ContestRound, assertionRound: AssertionRound, auditRoundResult: AuditRoundResult): TestH0Result? {
    try {
        val assertion = assertionRound.assertion
        val assorter = assertion.assorter
        val sampler = PollingSampler(contestRound.id, cvrPairs, assorter, allowReset = false)

        val testH0Result = auditPollingAssertion(config, contestRound.contestUA, assertionRound, sampler, auditRoundResult.roundIdx)

        return testH0Result

    } catch (t: Throwable) {
        logger.error {t}
        t.printStackTrace()
        return null
    }
}

class PairSampler(
    val contestId: Int,
    val cvrPairs: List<Pair<CvrIF, CvrIF>>, // Pair(mvr, card)
): Iterator<Pair<CvrIF, CvrIF>> {
    val maxSamples = cvrPairs.count { it.second.hasContest(contestId) }
    private var idx = 0
    private var count = 0

    override fun next(): Pair<CvrIF, CvrIF> {
        while (idx < cvrPairs.size) {
            val pair = cvrPairs[idx]
            idx++
            if (pair.second.hasContest(contestId)) {
                count++
                return pair
            }
        }
        throw RuntimeException("PairSampler no samples left for ${contestId}")
    }

    override fun hasNext() = (count < maxSamples)
}



