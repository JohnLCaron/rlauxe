package org.cryptobiotic.rlauxe.persist.json

import kotlinx.serialization.Serializable
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.*

/*
// class OneAuditContest (
//    val contest: ContestIF,
//
//    val cvrVotes: Map<Int, Int>,   // candidateId -> nvotes (may be empty) from the crvs
//    val cvrNc: Int,                // may be 0
//    val pools: Map<Int, BallotPool>, // pool id -> pool
//) : ContestIF {
@Serializable
data class OAContestJson(
    val cvrVotes: Map<Int, Int>,
    val cvrNc: Int,
    val pools: List<BallotPoolJson>,
    val Np: Int?,
)

fun OneAuditContest.publishOAJson() = OAContestJson(
        this.cvrVotes,
        this.cvrNc,
        this.pools.values.map { it.publishJson()},
        this.Np(),
    )

fun OAContestJson.import(contest: ContestIF): OneAuditContest {
    val pools = this.pools.map { it.import() }

    return OneAuditContest.make(
        contest,
        this.cvrVotes,
        this.cvrNc,
        pools,
    )
}

 */

// data class BallotPool(val name: String, val id: Int, val contest:Int, val ncards: Int, val votes: Map<Int, Int>) {
@Serializable
data class BallotPoolJson(
    val name: String,
    val id: Int,
    val contest: Int,
    val ncards: Int,
    val votes: Map<Int, Int>,
)

fun BallotPool.publishJson() = BallotPoolJson(
    this.name,
    this.poolId,
    this.contest,
    this.ncards,
    this.votes,
)

fun BallotPoolJson.import() = BallotPool(
    this.name,
    this.id,
    this.contest,
    this.ncards,
    this.votes,
)

// open class OAContestUnderAudit(
//    val contestOA: OneAuditContest,
//    hasStyle: Boolean = true
//): ContestUnderAudit(contestOA, isComparison=true, hasStyle=hasStyle) {

@Serializable
data class OAContestUnderAuditJson(
    val contestUA: ContestUnderAuditJson,
    val contestOA: ContestIFJson,
)

fun OAContestUnderAudit.publishOAJson() = OAContestUnderAuditJson(
    (this as ContestUnderAudit).publishJson(),
        this.contestOA.publishJson(),
    )

fun OAContestUnderAuditJson.import(): OAContestUnderAudit {
    val contestUA = this.contestUA.import()
    val contestOA = this.contestOA.import(contestUA.contest.info()) as OneAuditContest

    val result = OAContestUnderAudit(contestOA, contestUA.hasStyle)
    result.pollingAssertions = contestUA.pollingAssertions
    result.clcaAssertions = contestUA.clcaAssertions
    result.preAuditStatus = contestUA.preAuditStatus
    return result
}

//////////////////
// class OneAuditIrvContest(
//    contestOA: OneAuditContest,
//    hasStyle: Boolean = true,
//    val rassertions: List<RaireAssertion>,
//): OAContestUnderAudit(contestOA, hasStyle=hasStyle) {
@Serializable
data class OAIrvJson(
    val contestUA: ContestUnderAuditJson,
    val contestOA: ContestIFJson,
    val rassertions: List<RaireAssertionJson>,
)

fun OneAuditIrvContest.publishOAIrvJson() = OAIrvJson(
    (this as ContestUnderAudit).publishJson(),
    this.contestOA.publishJson(),
    rassertions.map { it.publishJson() }
)

fun OAIrvJson.import(): OneAuditIrvContest {
    val contestUA = this.contestUA.import()
    val contestOA = this.contestOA.import(contestUA.contest.info()) as OneAuditContest

    val result = OneAuditIrvContest(contestOA, contestUA.hasStyle, rassertions.map { it.import() })
    result.pollingAssertions = contestUA.pollingAssertions
    result.clcaAssertions = contestUA.clcaAssertions
    result.preAuditStatus = contestUA.preAuditStatus
    return result
}

