package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.workflow.AuditRoundResult
import org.cryptobiotic.rlauxe.workflow.EstimationRoundResult

open class Assertion(
    val contest: ContestIF,
    val assorter: AssorterIF,
) {
    val winner = assorter.winner()
    val loser = assorter.loser()

    // these values are set during estimateSampleSizes()
    var estSampleSize = 0   // estimated sample size for current round
    val estRoundResults = mutableListOf<EstimationRoundResult>()

    // these values are set during runAudit()
    val roundResults = mutableListOf<AuditRoundResult>()
    var status = TestH0Status.InProgress
    var round = 0           // round when set to proved or disproved

    override fun toString() = "'${contest.info.name}' (${contest.info.id}) ${assorter.desc()} margin=${df(assorter.reportedMargin())}"

    open fun show() = buildString {
        appendLine(" assertion: ${assorter.desc()}, estSampleSize=$estSampleSize, status=$status, round=$round)")
        roundResults.forEach {
            appendLine("    $it")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Assertion

        if (estSampleSize != other.estSampleSize) return false
        if (round != other.round) return false
        if (contest != other.contest) return false
        if (assorter != other.assorter) return false
        if (estRoundResults != other.estRoundResults) return false
        if (roundResults != other.roundResults) return false
        if (status != other.status) return false

        return true
    }

    override fun hashCode(): Int {
        var result = estSampleSize
        result = 31 * result + round
        result = 31 * result + contest.hashCode()
        result = 31 * result + assorter.hashCode()
        result = 31 * result + estRoundResults.hashCode()
        result = 31 * result + roundResults.hashCode()
        result = 31 * result + status.hashCode()
        return result
    }

}

open class ClcaAssertion(
    contest: ContestIF,
    val cassorter: ClcaAssorterIF,
): Assertion(contest, cassorter.assorter()) {

    override fun toString() = "${cassorter.assorter().desc()} estSampleSize=$estSampleSize"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as ClcaAssertion

        return cassorter == other.cassorter
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + cassorter.hashCode()
        return result
    }
}
