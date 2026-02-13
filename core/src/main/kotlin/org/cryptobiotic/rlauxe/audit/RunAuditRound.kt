package org.cryptobiotic.rlauxe.audit

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.betting.ClcaSamplerErrorTracker
import org.cryptobiotic.rlauxe.betting.PollingSamplerTracker
import org.cryptobiotic.rlauxe.betting.Taus
import org.cryptobiotic.rlauxe.core.CvrIF
import org.cryptobiotic.rlauxe.betting.TestH0Result
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import org.cryptobiotic.rlauxe.workflow.ClcaAssertionAuditor
import org.cryptobiotic.rlauxe.workflow.OneAuditAssertionAuditor
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode
import org.cryptobiotic.rlauxe.workflow.auditPollingAssertion
import java.nio.file.Files.notExists
import java.nio.file.Path

private val logger = KotlinLogging.logger("RunAuditRound")

// called from cli and rlauxe-viewer
fun runRound(inputDir: String, onlyTask: String? = null): AuditRoundIF? {
    val roundResult = runRoundResult(inputDir, onlyTask)
    if (roundResult.isErr) {
        logger.error{"runRoundResult failed ${roundResult.component2()}"}
        return null
    }
    return roundResult.unwrap()
}

fun runRoundResult(auditDir: String, onlyTask: String? = null): Result<AuditRoundIF, ErrorMessages> {
    val errs = ErrorMessages("runRoundResult")

    try {
        if (notExists(Path.of(auditDir))) {
            return errs.add( "audit Directory $auditDir does not exist" )
        }
        val auditRecord = AuditRecord.readFrom(auditDir)
        if (auditRecord == null) {
            return errs.add("directory '$auditDir' does not contain an audit record")
        }

        val workflow = PersistedWorkflow(auditRecord)
        var roundIdx = 0
        var complete = false

        if (!workflow.auditRounds().isEmpty()) {
            val lastRound = workflow.auditRounds().last()
            roundIdx = lastRound.roundIdx

            if (!lastRound.auditWasDone) {
                logger.info { "Start runAuditRound ${lastRound.roundIdx}" }
                val roundStopwatch = Stopwatch()
                // run the audit for this round
                complete = workflow.runAuditRound(lastRound as AuditRound, onlyTask)
                logger.info { "End runAuditRound ${lastRound.roundIdx} complete=$complete took ${roundStopwatch}" }

            } else {
                complete = lastRound.auditIsComplete
            }
        }

        if (!complete) {
            roundIdx++
            // start next round and estimate sample sizes
            logger.info { "Start startNewRound $roundIdx using ${workflow}" }
            val roundStopwatch = Stopwatch()
            val nextRound = workflow.startNewRound(quiet = false, onlyTask)

            if (auditRecord.config.persistedWorkflowMode == PersistedWorkflowMode.testPrivateMvrs) {
                val publisher = Publisher(auditDir)
                val ncards = writeMvrsForRound(publisher, roundIdx)
                logger.info{"writeMvrsForRound ${ncards} cards to ${publisher.sampleMvrsFile(roundIdx)}"}
            }
            logger.info { "End startNewRound $roundIdx took ${roundStopwatch}: ${nextRound.show()}" }

            return Ok(nextRound)

        } else {
            val lastRound = workflow.auditRounds().last()
            logger.info { "runRound ${lastRound.roundIdx} complete = $complete" }
            return Ok(lastRound)
        }

    } catch (t: Throwable) {
        logger.error(t) { "runRoundResult Exception" }
        return errs.add( t.message ?: t.toString())
    }
}

// for debugging, transparency. rlauxe-viewer
fun runRoundAgain(auditDir: String, contestRound: ContestRound, assertionRound: AssertionRound): String {
    val contestId = contestRound.contestUA.id
    try {
        if (notExists(Path.of(auditDir))) {
            logger.warn { "Audit Directory $auditDir does not exist" }
            return "Audit Directory $auditDir does not exist"
        }
        val roundIdx = assertionRound.roundIdx
        val cassertion = assertionRound.assertion as ClcaAssertion // only for clca ??
        val noerror = cassertion.cassorter.noerror()
        val taus = Taus(cassertion.assorter.upperBound())

        val auditRecord = AuditRecord.readFrom(auditDir)
        if (auditRecord == null) {
            return "directory '$auditDir' does not contain an audit record"
        }
        logger.info { "runRoundAgain in $auditDir for round $roundIdx, contest $contestId, and assertion $cassertion" }

        val workflow = PersistedWorkflow(auditRecord, mvrWrite = false)
        val cvrPairs = workflow.mvrManager().makeMvrCardPairsForRound(roundIdx)
        val sampler = PairSampler(contestId, cvrPairs)

        val config = workflow.auditConfig()

        // run the audit, capture the sequences
        val testH0Result =  when (config.auditType) {
            AuditType.CLCA -> runClcaAudit(config, cvrPairs, contestRound, assertionRound)
            AuditType.POLLING -> runPollingAudit(config, cvrPairs, contestRound, assertionRound)
            AuditType.ONEAUDIT -> runOneAudit(config, cvrPairs,
                workflow.mvrManager().oapools()!!,
                contestRound, assertionRound)
        }

        return if (testH0Result == null) "failed" else buildString {
            appendLine("contest $contestId assertion win/lose = ${cassertion.assorter.winLose()}")
            val seq = testH0Result.sequences
            if (seq != null) {
                val pvalues = seq.pvalues()
                val count = seq.xs.size
                append("${sfn("idx", 4)}, ${sfn("xs", 6)}, ${sfn("bet", 6)}, ${sfn("payoff", 6)}, ${sfn("Tj", 6)}, ${sfn("pvalue", 8)}, ")
                appendLine("${sfn("location", 25)}, ${sfn("mvr votes", 10)}, ${sfn("card", 10)}")
                repeat(count) {
                    val x = seq.xs[it]
                    val err = if (x == noerror) "" else "*${taus.nameOf(x/noerror)}"
                    append("${nfn(it+1, 4)}, ${df(x)}$err, ${df(seq.bets[it])}, ${df(seq.tjs[it])}")
                    append(", ${trunc(seq.testStatistics[it].toString(), 6)}, ${trunc(pvalues[it].toString(), 8)}")
                    val pair = sampler.next()
                    val mvrVotes = pair.first.votes(contestId)?.contentToString() ?: "missing"
                    val card = pair.second
                    val cardVotes = card.votes(contestId)?.contentToString() ?: "N/A"
                    append(", ${sfn(pair.first.location(), 25)}")
                    append(", ${sfn(mvrVotes, 10)}")
                    if (card.poolId() != null) append(", pool=${card.poolId()}, ")  // TODO show pool average
                        else append(", votes=${cardVotes}")
                    appendLine()
                    if (!card.hasContest(contestId))
                        logger.warn{"possible=${card.hasContest(contestId)}"}
                }
            }
        }

    } catch (t: Throwable) {
        logger.error {t}
        t.printStackTrace()
        return t.message ?: "none"
    }
}

fun runClcaAudit(config: AuditConfig, cvrPairs: List<Pair<CvrIF, AuditableCard>>, contestRound: ContestRound, assertionRound: AssertionRound): TestH0Result? {
    try {
        val auditor = ClcaAssertionAuditor()

        val cassertion = assertionRound.assertion as ClcaAssertion
        val cassorter = cassertion.cassorter

        val sampler = ClcaSamplerErrorTracker.withMaxSample(
            contestRound.id,
            cassorter,
            cvrPairs,
            maxSampleIndex = contestRound.maxSampleAllowed,
        )
        val testH0Result = auditor.run(config, contestRound, assertionRound, sampler, assertionRound.roundIdx)
        return testH0Result

    } catch (t: Throwable) {
        logger.error {t}
        t.printStackTrace()
        return null
    }
}

fun runOneAudit(config: AuditConfig, cvrPairs: List<Pair<CvrIF, AuditableCard>>, pools: List<OneAuditPoolIF>, contestRound: ContestRound, assertionRound: AssertionRound): TestH0Result? {
    try {
        val auditor = OneAuditAssertionAuditor(pools)
        val cassertion = assertionRound.assertion as ClcaAssertion
        val cassorter = cassertion.cassorter

        val sampler = ClcaSamplerErrorTracker.withMaxSample(
            contestRound.id,
            cassorter,
            cvrPairs,
            maxSampleIndex = contestRound.maxSampleAllowed,
        )
        val testH0Result = auditor.run(config, contestRound, assertionRound, sampler, assertionRound.roundIdx)
        return testH0Result

    } catch (t: Throwable) {
        logger.error {t}
        t.printStackTrace()
        return null
    }
}

fun runPollingAudit(config: AuditConfig, cvrPairs: List<Pair<CvrIF, CvrIF>>, contestRound: ContestRound, assertionRound: AssertionRound): TestH0Result? {
    try {
        val assertion = assertionRound.assertion
        val assorter = assertion.assorter
        val sampler = PollingSamplerTracker(
            contestRound.id,
            assorter,
            cvrPairs,
            maxSampleIndex = contestRound.maxSampleAllowed
        )

        val testH0Result = auditPollingAssertion(config, contestRound.contestUA, assertionRound, sampler, assertionRound.roundIdx)

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



