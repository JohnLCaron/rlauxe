package org.cryptobiotic.rlauxe.core

data class AssertionOld(
    val contest: AuditContest,
    val assorter: AssorterFunction,
) {
    override fun toString() = buildString {
        appendLine("Assertion")
        appendLine("   $contest)")
        appendLine("   assorter=$assorter)")
    }
}

class ComparisonAssertionOld(
    val contest: AuditContest,
    val assorter: ComparisonAssorter,
) {

    override fun toString() = buildString {
        appendLine("ComparisonAssertion")
        appendLine("   $contest)")
        appendLine("   assorter=$assorter)")
    }
}