package org.cryptobiotic.rlauxe.verifier

import org.cryptobiotic.rlauxe.audit.AuditRecord
import org.cryptobiotic.rlauxe.workflow.*

class VerifyAuditRecord(val auditRecordLocation: String) {
    val auditRecord: AuditRecord

    init {
        auditRecord = AuditRecord.readFrom(auditRecordLocation)
    }

    fun verify() {
        println("verify auditRecord in ${auditRecordLocation}")
        auditRecord.rounds.forEach { verify(it) }
    }

    fun verify(round: AuditRound) {
        println(" verify round = ${round.roundIdx}")
        round.contests.forEach { verify(it) }
    }

    fun verify(contest: ContestRound) {
        println("  verify contest = ${contest.id}")
        val minAssertion = contest.minAssertion()
        requireNotNull(minAssertion) {"    minAssertion = $minAssertion"}

        contest.assertions.forEach { verify(it) }

        val contestUA = contest.contestUA
        contest.assertions.forEach { assertionRound ->
            require(contestUA.assertions().contains(assertionRound.assertion))
        }
    }

    fun verify(assertion: AssertionRound) {
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

    fun verify(auditResult: AuditRoundResult) {
    }

    fun verify(estResult: EstimationRoundResult) {
    }
}