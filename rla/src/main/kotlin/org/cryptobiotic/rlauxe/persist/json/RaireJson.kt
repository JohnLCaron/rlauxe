package org.cryptobiotic.rlauxe.persist.json

import kotlinx.serialization.Serializable
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.raire.RaireAssertion
import org.cryptobiotic.rlauxe.raire.RaireAssertionType
import org.cryptobiotic.rlauxe.raire.RaireContest
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit

// class RaireContestUnderAudit(
//    contest: RaireContest,
//    val winner: Int,  // the sum of winner and eliminated must be all the candiates in the contest
//    val rassertions: List<RaireAssertion>,
//): ContestUnderAudit(contest, isComparison=true, hasStyle=true) {

@Serializable
data class RaireContestUnderAuditJson(
        // val info: ContestInfoJson,
        val raireContest: ContestIFJson,
        val winner: Int,
        val rassertions: List<RaireAssertionJson>,
        val contestUA: ContestUnderAuditJson,
    )

fun RaireContestUnderAudit.publishRaireJson() = RaireContestUnderAuditJson(
        // this.contest.info.publishJson(),
        this.contest.publishJson(),
        this.winner,
        this.rassertions.map { it.publishJson() },
        (this as ContestUnderAudit).publishJson(),
    )

fun RaireContestUnderAuditJson.import(): RaireContestUnderAudit {
    // val info = this.info.import()
    val contestUA = this.contestUA.import()
    val raireContest = this.raireContest.import(contestUA.contest.info)

    val result = RaireContestUnderAudit(
        raireContest as RaireContest,
        this.winner,
        this.rassertions.map { it.import() },
        // contestUA.isComparison,
        contestUA.hasStyle,
    )
    // result.pollingAssertions = contestUA.pollingAssertions
    result.clcaAssertions = contestUA.clcaAssertions
    return result
}

// data class RaireAssertion(
//    val winner: Int,
//    val loser: Int,
//    val margin: Int,
//    val assertionType: RaireAssertionType,
//    val alreadyEliminated: List<Int> = emptyList(), // NEN only; already eliminated for the purpose of this assertion
//    val explanation: String? = null,
//)

// data class AssertionJson(
//    val contest: ContestJson,
//    val assorter: AssorterJson,
//    val estSampleSize: Int,   // estimated sample size
//    val estRoundResults: List<EstimationRoundResultJson>,   // first sample when pvalue < riskLimit
//    val roundResults: List<AuditRoundResultJson>,   // first sample when pvalue < riskLimit
//    val status: String, // testH0 status
//    val round: Int,
//)
@Serializable
data class RaireAssertionJson(
    val winner: Int,
    val loser: Int,
    val margin: Int,
    val assertion_type: String,
    val eliminated: List<Int>,
    val votes: Map<Int, Int>,
)

fun RaireAssertion.publishJson() = RaireAssertionJson(
    this.winnerId,
    this.loserId,
    this.marginInVotes,
    this.assertionType.name,
    this.eliminated,
    this.votes,
)

fun RaireAssertionJson.import(): RaireAssertion {
    return RaireAssertion(
        this.winner,
        this.loser,
        this.margin,
        RaireAssertionType.fromString(this.assertion_type),
        this.eliminated,
        this.votes,
    )
}
