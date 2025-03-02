package org.cryptobiotic.rlauxe.raire

import kotlinx.serialization.Serializable
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.persist.json.*

// data class RaireContest(
//    override val info: ContestInfo,
//    override val winners: List<Int>,
//    override val Nc: Int,
//    override val Np: Int,
//)
// TODO duplicated in ContestIFJson
@Serializable
data class RaireContestJson(
    val info: ContestInfoJson,
    val winners: List<Int>,
    val Nc: Int,
    val Np: Int,
)

fun RaireContest.publishJson() = RaireContestJson(
        this.info.publishJson(),
        this.winners,
        this.Nc,
        this.Np,
    )

fun RaireContestJson.import() = RaireContest(
        this.info.import(),
        this.winners,
        this.Nc,
        this.Np,
    )

// class RaireContestUnderAudit(
//    contest: RaireContest,
//    val winner: Int,  // the sum of winner and eliminated must be all the candiates in the contest
//    val rassertions: List<RaireAssertion>,
//): ContestUnderAudit(contest, isComparison=true, hasStyle=true) {
// TODO make inheritence less clumsy

@Serializable
data class RaireContestUnderAuditJson(
    val contest: RaireContestJson,
    val winner: Int,
    val rassertions: List<RaireAssertionJson>,
    val isComparison: Boolean,
    val hasStyle: Boolean,
    var pollingAssertions: List<AssertionJson>,
    var clcaAssertions: List<ClcaAssertionJson>,

    val estMvrs: Int,  // Estimate of the sample size required to confirm the contest
    val estSampleSizeNoStyles: Int, // number of total samples estimated needed, uniformPolling (Polling, no style only)
    val done: Boolean,
    val included: Boolean,
    val status: TestH0Status, // or its own enum ??
)

fun RaireContestUnderAudit.publishRaireJson() = RaireContestUnderAuditJson(
        (this.contest as RaireContest).publishJson(),
        this.winner,
        this.rassertions.map { it.publishJson() },
        this.isComparison,
        this.hasStyle,
        this.pollingAssertions.map { it.publishJson() },
        this.clcaAssertions.map { it.publishJson() },
        this.estMvrs,
        this.estSampleSizeNoStyles,
        this.done,
        this.included,
        this.status,
    )

fun RaireContestUnderAuditJson.import(): RaireContestUnderAudit {
    val result = RaireContestUnderAudit(
        this.contest.import(),
        this.winner,
        this.rassertions.map { it.import() },
    )
    result.pollingAssertions = this.pollingAssertions.map{ it.import() }
    result.clcaAssertions = this.clcaAssertions.map{ it.import() }
    result.estMvrs = this.estMvrs
    result.estSampleSizeNoStyles = this.estSampleSizeNoStyles
    result.done = this.done
    result.included = this.included
    result.status = this.status
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
    val already_eliminated: List<String>,
    val votes: Map<Int, Int>,
    val explanation: String?,
)

fun RaireAssertion.publishJson() = RaireAssertionJson(
    this.winner,
    this.loser,
    this.margin,
    this.assertionType.name,
    this.alreadyEliminated.map { it.toString() },
    this.votes,
    this.explanation,
)

fun RaireAssertionJson.import(): RaireAssertion {
    return RaireAssertion(
        this.winner,
        this.loser,
        this.margin,
        RaireAssertionType.fromString(this.assertion_type),
        this.already_eliminated.map { it.toInt() },
        this.votes,
        this.explanation,
    )
}
