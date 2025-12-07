package org.cryptobiotic.rlauxe.verify

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.AuditableCardCsvReader
import org.cryptobiotic.rlauxe.persist.csv.readAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFile
import org.cryptobiotic.rlauxe.util.CloseableIterable

private val logger = KotlinLogging.logger("VerifyAuditRecord")

class VerifyAuditRecord(val auditRecordLocation: String) {
    val auditRecord: AuditRecord
    val publisher: Publisher
    val config: AuditConfig
    val contests: List<ContestUnderAudit>
    val allInfos: Map<Int, ContestInfo>?
    val cards: CloseableIterable<AuditableCard>

    init {
        val auditRecordResult = AuditRecord.readFromResult(auditRecordLocation)
        if (auditRecordResult is Ok) {
            auditRecord = auditRecordResult.unwrap()
        } else {
            println( auditRecordResult.toString() )
            logger.error{ auditRecordResult.toString() }
            throw RuntimeException( auditRecordResult.toString() )
        }

        publisher = Publisher(auditRecordLocation)
        val auditConfigResult = readAuditConfigJsonFile(publisher.auditConfigFile())
        config = auditConfigResult.unwrap()

        val contestsResults = readContestsJsonFile(publisher.contestsFile())
        contests = if (contestsResults is Ok) contestsResults.unwrap().sortedBy { it.id } else {
            println(contestsResults)
            logger.error{ contestsResults.toString() }
            throw RuntimeException( contestsResults.toString() )
        }

        allInfos = contests?.map{ it.contest.info() }?.associateBy { it.id }
        cards = AuditableCardCsvReader(publisher.sortedCardsFile())
    }

    fun verify(): VerifyResults {
        val result = VerifyResults()
        result.addMessage("VerifyAuditRecord on $auditRecordLocation ")
        auditRecord.rounds.forEach { verifyRound(it, result) }

        verifyMultipleRoundSampling(result)

        return result
    }

    fun verifyRound(round: AuditRound, result: VerifyResults) {
        result.addMessage(" verify round = ${round.roundIdx}")
        round.contestRounds.forEach { verifyContest(it, result) }
    }

    fun verifyContest(contest: ContestRound, result: VerifyResults) {
        result.addMessage("  verify contest = ${contest.id}")
        contest.assertionRounds.forEach { verifyAssertion(it, result) }

        val contestUA = contest.contestUA
        contest.assertionRounds.forEach { assertionRound ->
            require(contestUA.assertions().contains(assertionRound.assertion))
        }
    }

    fun verifyAssertion(assertion: AssertionRound, result: VerifyResults) {
        if (assertion.prevAuditResult != null) {
            verifyRoundResult(assertion.prevAuditResult!!, result)
        }
        if (assertion.estimationResult != null) {
            verifyEstimationResult(assertion.estimationResult!!, result)
        }
        if (assertion.auditResult != null) {
            verifyRoundResult(assertion.auditResult!!, result)
        }
    }

    fun verifyRoundResult(auditResult: AuditRoundResult, result: VerifyResults) {
    }

    fun verifyEstimationResult(estResult: EstimationRoundResult, result: VerifyResults) {
    }

    fun verifyMultipleRoundSampling(result: VerifyResults) {
        val nrounds = auditRecord.rounds.size
        if (nrounds < 2) result.addMessage("no need to verify sampling across $nrounds rounds")
        else {
            result.addMessage("--- verify multiple round sampling across $nrounds rounds")
            contests.forEach { contest ->
                verifySamplingForContest(contest, result)
            }
        }

    }

    fun verifySamplingForContest(contest: ContestUnderAudit, result: VerifyResults) {
        val firstRound = auditRecord.rounds.first()
        val contestRound = firstRound.contestRounds.find { it.id == contest.contest.id }
        if (contestRound == null) return

        result.addMessage(" verify sampling for contest ${contest.id}")
        val cards = readAuditableCardCsvFile(publisher.sampleCardsFile(firstRound.roundIdx))
        var nextRoundIdx = 1
        while (nextRoundIdx < auditRecord.rounds.size) {
            val nextRound = auditRecord.rounds[nextRoundIdx]
            val nextContestRound = nextRound.contestRounds.find { it.id == contest.contest.id }
            if (nextContestRound == null) break
            val nextCards = readAuditableCardCsvFile(publisher.sampleCardsFile(nextRound.roundIdx))
            result.addMessage("   verify sampling for contest ${contest.id} round ${firstRound.roundIdx} vs round ${nextRound.roundIdx}")
            verifySamplingForContest(contest, cards,  nextCards, result)
            nextRoundIdx++
        }
    }

    fun verifySamplingForContest(contest: ContestUnderAudit, cards: List<AuditableCard>, nextCards: List<AuditableCard>, result: VerifyResults): Boolean {
        val mycards = cards.filter { it.contests().contains(contest.id)}.iterator()
        val nextcards = nextCards.filter { it.contests().contains(contest.id)}.iterator()

        mycards.forEach { mycard ->
            val nextcard = nextcards.next()
            if (mycard != nextcard) {
                result.addError("  failed ${mycard.location()} != ${nextcard.location}")
                return false
            }
        }
        return true
    }
}