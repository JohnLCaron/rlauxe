package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.betting.ClcaSamplerErrorTracker
import org.cryptobiotic.rlauxe.betting.PollingSamplerTracker
import org.cryptobiotic.rlauxe.betting.Taus
import org.cryptobiotic.rlauxe.core.CvrIF
import org.cryptobiotic.rlauxe.betting.TestH0Result
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CompositeRecord
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import org.cryptobiotic.rlauxe.workflow.ClcaAssertionAuditor
import org.cryptobiotic.rlauxe.workflow.OneAuditAssertionAuditor
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import org.cryptobiotic.rlauxe.workflow.auditPollingAssertion
import java.nio.file.Files.notExists
import java.nio.file.Path

private val logger = KotlinLogging.logger("RunAuditRoundAgain")

// for debugging, transparency. rlauxe-viewer
fun runRoundAgain(auditDir: String, contestRound: ContestRound, assertionRound: AssertionRound): String {
    val contestId = contestRound.contestUA.id
    val contestName = contestRound.contestUA.name
    try {
        if (notExists(Path.of(auditDir))) {
            logger.warn { "Audit Directory $auditDir does not exist" }
            return "Audit Directory $auditDir does not exist"
        }
        val roundIdx = assertionRound.roundIdx
        val assertion = assertionRound.assertion
        val cassertion: ClcaAssertion? = if (assertion is ClcaAssertion) assertion else null // only for clca ??
        val noerror = cassertion?.cassorter?.noerror()
        val taus = Taus(assertion.assorter.upperBound())
        val oaAssorter: OneAuditClcaAssorter? = if (cassertion?.cassorter is OneAuditClcaAssorter) cassertion.cassorter else null

        val auditRecord = AuditRecord.read(auditDir)
        if (auditRecord == null) {
            return "directory '$auditDir' does not contain an audit record"
        }
        logger.info { "runRoundAgain in $auditDir for round $roundIdx, contest '$contestName', and assertion $assertion" }

        val useAuditRecord = if (auditRecord is CompositeRecord) {
            auditRecord.findComponentWithContest(contestRound.contestUA.name)
        } else auditRecord
        if (useAuditRecord == null) return "Cant find contest named ${contestRound.contestUA.name}"

        require( useAuditRecord is AuditRecord)
        val workflow = PersistedWorkflow(useAuditRecord, mvrWrite = false)
        val cvrPairs = workflow.mvrManager().makeMvrCardPairsForRound(roundIdx)
        val sampler = PairSampler(contestId, cvrPairs)

        val config = workflow.config()

        // run the audit, capture the sequences
        val testH0Result =  when (config.auditType) {
            AuditType.CLCA -> runClcaAudit(config, cvrPairs, contestRound, assertionRound)
            AuditType.POLLING -> runPollingAudit(config, cvrPairs, contestRound, assertionRound)
            AuditType.ONEAUDIT -> runOneAudit(config, cvrPairs, workflow.mvrManager().pools()!!, contestRound, assertionRound)
        }

        return if (testH0Result == null) "failed" else buildString {
            appendLine("contest $contestId assertion = ${assertion.assorter.shortName()}")
            var countPoolCards = 0
            var countPoolCardsMissing = 0
            val seq = testH0Result.sequences
            if (seq != null) {
                val pvalues = seq.pvalues()
                val count = seq.xs.size
                append("${sfn("idx", 4)}, ${sfn("xs", 8)}, ${sfn("bet", 8)}, ${sfn("payoff", 8)}, ${sfn("Tj", 8)}, ${sfn("pvalue", 8)}, ")
                appendLine("${sfn("location", 25)}, ${sfn("mvr votes", 10)}, ${sfn("card", 10)}")
                repeat(count) {
                    val x = seq.xs[it]
                    val pair = sampler.next()
                    val mvrVotes = pair.first.votes(contestId)?.contentToString() ?: "missing"
                    val card = pair.second
                    val cardVotes = card.votes(contestId)?.contentToString() ?: "N/A"
                    val err = if (noerror == null || x == noerror || (card.poolId() != null)) "" else "*${taus.nameOf(x/noerror)}"
                    if (card.poolId() != null) {
                        countPoolCards++
                        if (mvrVotes == "missing") countPoolCardsMissing++
                    }

                    append("${nfn(it+1, 4)}, ${dfn(x, 8)}$err, ${dfn(seq.bets[it], 8)}, ${dfn(seq.tjs[it], 8)}")
                    append(", ${trunc(seq.testStatistics[it].toString(), 8)}, ${trunc(pvalues[it].toString(), 8)}")
                    append(", ${sfn(pair.first.id(), 25)}")
                    append(", ${sfn(mvrVotes, 10)}")
                    if (card.poolId() != null) append(", pool=${card.poolId()}, poolAvg=${df(oaAssorter?.poolAverage(card.poolId()))}")
                        else append(", votes=${cardVotes}")
                    appendLine()
                    if (!card.hasContest(contestId))
                        logger.warn{"possible=${card.hasContest(contestId)}"}
                }
                appendLine("\ncardsInPool $countPoolCards pct= ${countPoolCards/seq.xs.size.toDouble()}")
                appendLine("cardsInPoolMissing $countPoolCardsMissing pct= ${countPoolCardsMissing/countPoolCards.toDouble()}")
                appendLine("cardsInPoolNotMissing ${countPoolCards-countPoolCardsMissing} pct= ${(countPoolCards-countPoolCardsMissing) / seq.xs.size.toDouble()}")
            }
        }

    } catch (t: Throwable) {
        logger.error {t}
        t.printStackTrace()
        return t.message ?: "none"
    }
}

fun runClcaAudit(config: Config, cvrPairs: List<Pair<CvrIF, AuditableCard>>, contestRound: ContestRound, assertionRound: AssertionRound): TestH0Result? {
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

fun runOneAudit(config: Config, cvrPairs: List<Pair<CvrIF, AuditableCard>>, pools: List<CardPoolIF>, contestRound: ContestRound, assertionRound: AssertionRound): TestH0Result? {
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

fun runPollingAudit(config: Config, cvrPairs: List<Pair<CvrIF, CvrIF>>, contestRound: ContestRound, assertionRound: AssertionRound): TestH0Result? {
    try {
        val assertion = assertionRound.assertion
        val assorter = assertion.assorter

        val sampler = PollingSamplerTracker.withMaxSample(
            contestRound.id,
            assorter,
            cvrPairs,
            maxSampleIndex = contestRound.maxSampleAllowed!!
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



