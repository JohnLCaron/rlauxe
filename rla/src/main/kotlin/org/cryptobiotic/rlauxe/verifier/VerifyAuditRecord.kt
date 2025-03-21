package org.cryptobiotic.rlauxe.verifier

import org.cryptobiotic.rlauxe.audit.*

class VerifyAuditRecord(val auditRecordLocation: String) {
    val auditRecord: AuditRecord

    init {
        auditRecord = AuditRecord.readFrom(auditRecordLocation)
    }

    fun verify() = buildString {
        appendLine("verify auditRecord in ${auditRecordLocation}")
        auditRecord.rounds.forEach { append(verify(it)) }
    }

    fun verify(round: AuditRound) = buildString {
        appendLine(" verify round = ${round.roundIdx}")
        round.contestRounds.forEach { append(verify(it)) }
    }

    fun verify(contest: ContestRound) = buildString {
        appendLine("  verify contest = ${contest.id}")
        val minAssertion = contest.minAssertion()
        requireNotNull(minAssertion) {"    minAssertion = $minAssertion"}

        contest.assertionRounds.forEach { append(verify(it)) }

        val contestUA = contest.contestUA
        contest.assertionRounds.forEach { assertionRound ->
            require(contestUA.assertions().contains(assertionRound.assertion))
        }
    }

    fun verify(assertion: AssertionRound) = buildString {
        if (assertion.prevAuditResult != null) {
            verify(assertion.prevAuditResult!!)
        }
        if (assertion.estimationResult != null) {
            verify(assertion.estimationResult!!)
        }
        if (assertion.auditResult != null) {
            verify(assertion.auditResult!!)
        }
    }

    fun verify(auditResult: AuditRoundResult) = buildString {
    }

    fun verify(estResult: EstimationRoundResult) = buildString {
    }
}