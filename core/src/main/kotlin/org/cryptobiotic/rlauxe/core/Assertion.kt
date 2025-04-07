package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.df

open class Assertion(
    val info: ContestInfo,
    val assorter: AssorterIF,
) {
    val winner = assorter.winner()
    val loser = assorter.loser()

    override fun toString() = "'${info.name}' (${info.id}) ${assorter.desc()} margin=${df(assorter.reportedMargin())}"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Assertion

        if (info != other.info) return false
        if (assorter != other.assorter) return false

        return true
    }

    override fun hashCode(): Int {
        var result = info.hashCode()
        result = 31 * result + assorter.hashCode()
        return result
    }

    open fun show() = buildString {
        appendLine(" contestInfo: $info")
        appendLine(" assorter: ${assorter.desc()}")
    }

}

class ClcaAssertion(
    info: ContestInfo,
    val cassorter: ClcaAssorter,
): Assertion(info, cassorter.assorter()) {

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

    override fun show() = buildString {
        append(super.show())
        appendLine(" cassorter: ${cassorter}")
    }

    fun checkEquals(other: ClcaAssertion) = buildString {
        if (info != other.info) {
            append(" contestInfo not equal")
        }
        if (assorter != other.assorter) {
            append(" assorter not equal")
        }
        if (cassorter != other.cassorter) {
            append(" cassorter not equal")
        }
    }
}
