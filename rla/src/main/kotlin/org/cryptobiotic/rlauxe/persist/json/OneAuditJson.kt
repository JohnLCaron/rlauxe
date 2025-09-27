package org.cryptobiotic.rlauxe.persist.json

import kotlinx.serialization.Serializable
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.raire.RaireContest
import kotlin.Int

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
//    contest: ContestIF,
//    hasStyle: Boolean = true
//): ContestUnderAudit(contest, isComparison=true, hasStyle=hasStyle) {

/*
@Serializable
data class OAContestUnderAuditJson(
    val contest: ContestIFJson,
    val contestOA: ContestIFJson,
)

fun OAContestUnderAudit.publishOAJson() = OAContestUnderAuditJson(
    this.contest.publishJson(),
        this.contestOA.publishJson(),
    )

fun OAContestUnderAuditJson.import(): OAContestUnderAudit {
    val contest = this.contest.import()
    val contestOA = this.contestOA.import(contestUA.contest.info()) as OneAuditContest

    val result = OAContestUnderAudit(contest, contestUA.hasStyle)
    result.pollingAssertions = contestUA.pollingAssertions
    result.clcaAssertions = contestUA.clcaAssertions
    result.preAuditStatus = contestUA.preAuditStatus
    return result
} */

//////////////////
// class OAIrvContestUA(
//    contest: RaireContest,
//    hasStyle: Boolean = true,
//    val rassertions: List<RaireAssertion>,
//): OAContestUnderAudit(contest, hasStyle=hasStyle) {

@Serializable
data class OAIrvJson(
    val raireContest: ContestIFJson,
    val rassertions: List<RaireAssertionJson>,
    val contestOA: ContestUnderAuditJson,
)

fun OAIrvContestUA.publishOAIrvJson() = OAIrvJson(
    this.contest.publishJson(),
    rassertions.map { it.publishJson() },
    (this as ContestUnderAudit).publishJson(),
)

fun OAIrvJson.import(): OAIrvContestUA {
    val contestOA = this.contestOA.import(isOA = true) as OAContestUnderAudit
    val info = contestOA.contest.info()
    val raireContest = this.raireContest.import(info) as RaireContest

    val result = OAIrvContestUA(raireContest, contestOA.hasStyle, rassertions.map { it.import() })
    result.pollingAssertions = contestOA.pollingAssertions
    result.clcaAssertions = contestOA.clcaAssertions
    result.preAuditStatus = contestOA.preAuditStatus
    return result
}

// data class AssortAvgsInPools (
//    val assortAverage: Map<Int, Double>, // poolId -> average assort value, for one assorter
//)
@Serializable
data class AssortAvgsInPoolsJson(
    val contest: Int?, // TODO remove
    val assortAverage: Map<Int, Double>,
)

fun AssortAvgsInPools.publishJson() = AssortAvgsInPoolsJson(
    contest = null,
    assortAverage,
)

fun AssortAvgsInPoolsJson.import() = AssortAvgsInPools(
    assortAverage,
)



