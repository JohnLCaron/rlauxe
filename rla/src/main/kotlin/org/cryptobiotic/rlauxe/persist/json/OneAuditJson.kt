package org.cryptobiotic.rlauxe.persist.json

import kotlinx.serialization.Serializable
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.raire.RaireContest
import kotlin.Int

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
    this.contestId,
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

// class OAIrvContestUA(
//    contest: RaireContest,
//    hasStyle: Boolean = true,
//    val rassertions: List<RaireAssertion>,
//): OAContestUnderAudit(contest, hasStyle=hasStyle) {

@Serializable
data class OAIrvContestJson(
    val raireContest: ContestIFJson,
    val rassertions: List<RaireAssertionJson>,
    val contestOA: ContestUnderAuditJson,
)

fun OAIrvContestUA.publishOAIrvJson() = OAIrvContestJson(
    this.contest.publishJson(),
    rassertions.map { it.publishJson() },
    (this as ContestUnderAudit).publishJson(),
)

fun OAIrvContestJson.import(): OAIrvContestUA {
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



