package org.cryptobiotic.rlauxe.verifier

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.persist.AuditRecord

class VerifyAuditRecord(val auditRecordLocation: String) {
    val auditRecord: AuditRecord

    init {
        auditRecord = AuditRecord.readFrom(auditRecordLocation)
    }

    fun verify(): VerifyResults {
        val result = VerifyResults()
        result.addMessage("VerifyAuditRecord on $auditRecordLocation ")
        auditRecord.rounds.forEach { verify(it, result) }
        return result
    }

    fun verify(round: AuditRound, result: VerifyResults) {
        result.addMessage(" verify round = ${round.roundIdx}")
        round.contestRounds.forEach { verify(it, result) }
    }

    fun verify(contest: ContestRound, result: VerifyResults) {
        result.addMessage("  verify contest = ${contest.id}")
        val minAssertion = contest.minAssertion()
        requireNotNull(minAssertion) {"    minAssertion = $minAssertion"}

        contest.assertionRounds.forEach { verify(it, result) }

        val contestUA = contest.contestUA
        contest.assertionRounds.forEach { assertionRound ->
            require(contestUA.assertions().contains(assertionRound.assertion))
        }
    }

    fun verify(assertion: AssertionRound, result: VerifyResults) {
        if (assertion.prevAuditResult != null) {
            verify(assertion.prevAuditResult!!, result)
        }
        if (assertion.estimationResult != null) {
            verify(assertion.estimationResult!!, result)
        }
        if (assertion.auditResult != null) {
            verify(assertion.auditResult!!, result)
        }
    }

    fun verify(auditResult: AuditRoundResult, result: VerifyResults) {
    }

    fun verify(estResult: EstimationRoundResult, result: VerifyResults) {
    }
}