package org.cryptobiotic.rlauxe.persist.json

import kotlinx.serialization.Serializable
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.*

// class OneAuditContest (
//    override val info: ContestInfo,
//    cvrVotes: Map<Int, Int>,   // candidateId -> nvotes;  sum is nvotes or V_c
//    cvrNc: Int,
//    val pools: Map<Int, OneAuditPool>, // pool id -> pool
//)
// class OneAuditContest (
//    info: ContestInfo,
//    voteInput: Map<Int, Int>,
//    Nc: Int,
//    Np: Int,
//
//    val cvrVotes: Map<Int, Int>,   // candidateId -> nvotes (may be empty)
//    val cvrNc: Int,                // may be 0
//    val pools: Map<Int, BallotPool>, // pool id -> pool
//)
@Serializable
data class OAContestJson1(
    val cvrVotes: Map<Int, Int>,
    val cvrNc: Int,
    val pools: List<BallotPoolJson>,
    val Np: Int?,
)

fun OneAuditContest1.publishOAJson() = OAContestJson1(
        this.cvrVotes,
        this.cvrNc,
        this.pools.values.map { it.publishJson()},
        this.Np,
    )

fun OAContestJson1.import(info: ContestInfo): OneAuditContest1 {
    val pools = this.pools.map { it.import() }
    return OneAuditContest1.make(
        info,
        this.cvrVotes,
        this.cvrNc,
        pools,
        this.Np ?: 0,
    )
}

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

// class OAContestUnderAudit(
//    val contestOA: OneAuditContest,
//): ContestUnderAudit(contestOA.makeContest(), isComparison=true, hasStyle=true) {

@Serializable
data class OAContestUnderAuditJson1(
    val contestOA: OAContestJson1,
    val contestUA: ContestUnderAuditJson,
)

fun OAContestUnderAudit1.publishOAJson() = OAContestUnderAuditJson1(
        this.contestOA.publishOAJson(),
        (this as ContestUnderAudit).publishJson(),
    )

fun OAContestUnderAuditJson1.import(): OAContestUnderAudit1 {
    val contestUA = this.contestUA.import()
    val contestOA = this.contestOA.import(contestUA.contest.info())

    val result = OAContestUnderAudit1(contestOA, contestUA.hasStyle)
    result.pollingAssertions = contestUA.pollingAssertions
    result.clcaAssertions = contestUA.clcaAssertions
    result.preAuditStatus = contestUA.preAuditStatus
    return result
}

