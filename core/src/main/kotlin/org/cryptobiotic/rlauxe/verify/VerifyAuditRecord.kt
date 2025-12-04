package org.cryptobiotic.rlauxe.verify

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.persist.AuditRecord

private val logger = KotlinLogging.logger("VerifyAuditRecord")

class VerifyAuditRecord(val auditRecordLocation: String) {
    val auditRecord: AuditRecord

    init {
        val auditRecordResult = AuditRecord.readFromResult(auditRecordLocation)
        if (auditRecordResult is Ok) {
            auditRecord = auditRecordResult.unwrap()
        } else {
            println( auditRecordResult.toString() )
            logger.error{ auditRecordResult.toString() }
            throw RuntimeException( auditRecordResult.toString() )
        }
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