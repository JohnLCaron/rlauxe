package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.df

open class Assertion(
    val contest: ContestIF,
    val assorter: AssorterIF,
) {
    val winner = assorter.winner()
    val loser = assorter.loser()

    override fun toString() = "'${contest.info.name}' (${contest.info.id}) ${assorter.desc()} margin=${df(assorter.reportedMargin())}"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Assertion

        if (contest != other.contest) return false
        if (assorter != other.assorter) return false

        return true
    }

    override fun hashCode(): Int {
        var result = contest.hashCode()
        result = 31 * result + assorter.hashCode()
        return result
    }

    open fun show() = buildString {
        appendLine(" contest: $contest")
        appendLine(" assorter: ${assorter.desc()}")
    }

}

open class ClcaAssertion(
    contest: ContestIF,
    val cassorter: ClcaAssorterIF,
): Assertion(contest, cassorter.assorter()) {

    override fun toString() = "${cassorter.assorter().desc()}"

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

    //     val avgCvrAssortValue: Double,    // Ā(c) = average CVR assort value = assorter.reportedMargin()? always?
    //    val hasStyle: Boolean = true, // TODO could be on the Contest ??
    //    val check: Boolean = true, // TODO get rid of
    override fun show() = buildString {
        append(super.show())
        appendLine(" cassorter: ${cassorter}")
    }

    fun checkEquals(other: ClcaAssertion) = buildString {
        if (contest != other.contest) {
            append(" contest not equal")
        }
        if (assorter != other.assorter) {
            append(" assorter not equal")
        }
        if (cassorter != other.cassorter) {
            append(" cassorter not equal")
        }
    }
}
