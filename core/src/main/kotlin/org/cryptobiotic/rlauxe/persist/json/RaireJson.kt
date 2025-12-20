package org.cryptobiotic.rlauxe.persist.json

import kotlinx.serialization.Serializable
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.raire.RaireAssertion
import org.cryptobiotic.rlauxe.raire.RaireAssertionType
import org.cryptobiotic.rlauxe.raire.RaireContest
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit

// class RaireContestUnderAudit(
//    contest: RaireContest,
//    val rassertions: List<RaireAssertion>,
//    NpopIn: Int,
//): ContestUnderAudit(contest, isClca=true, NpopIn) {

@Serializable
data class RaireContestUnderAuditJson(
        val raireContest: ContestIFJson,
        val rassertions: List<RaireAssertionJson>,
        val contestUA: ContestUnderAuditJson,
    )

fun RaireContestUnderAudit.publishRaireJson() = RaireContestUnderAuditJson(
        this.contest.publishJson(),
        this.rassertions.map { it.publishJson() },
        (this as ContestUnderAudit).publishJson(),
    )

fun RaireContestUnderAuditJson.import(): RaireContestUnderAudit {
    val contestUA = this.contestUA.import()
    val raireContest = this.raireContest.import(contestUA.contest.info())

    val result = RaireContestUnderAudit(
        raireContest as RaireContest,
        this.rassertions.map { it.import() },
        contestUA.Npop,
    )
    result.clcaAssertions = contestUA.clcaAssertions // TODO wonky
    return result
}

// data class RaireAssertion(
//    val winnerId: Int, // this must be the candidate ID, in order to match with Cvr.votes
//    val loserId: Int,  // ditto
//    var difficulty: Double,
//    var marginInVotes: Int,
//    val assertionType: RaireAssertionType,
//    val eliminated: List<Int> = emptyList(), // candidate Ids; NEN only; already eliminated for the purpose of this assertion
//    val votes: Map<Int, Int> = emptyMap(), // votes for winner, loser depending on assertion type
//)

@Serializable
data class RaireAssertionJson(
    val winner: Int,
    val loser: Int,
    val difficulty: Double,
    val marginInVotes: Int,
    val assertion_type: String,
    val eliminated: List<Int>,
    val votes: Map<Int, Int>,
)

fun RaireAssertion.publishJson() = RaireAssertionJson(
    this.winnerId,
    this.loserId,
    this.difficulty,
    this.marginInVotes,
    this.assertionType.name,
    this.eliminated,
    this.votes,
)

fun RaireAssertionJson.import(): RaireAssertion {
    return RaireAssertion(
        this.winner,
        this.loser,
        this.difficulty,
        this.marginInVotes,
        RaireAssertionType.fromString(this.assertion_type),
        this.eliminated,
        this.votes,
    )
}
