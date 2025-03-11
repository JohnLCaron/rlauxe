package org.cryptobiotic.rlauxe.persist.json

import kotlinx.serialization.Serializable
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.OAClcaAssorter
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.OneAuditContest
import org.cryptobiotic.rlauxe.oneaudit.OneAuditStratum

// class OneAuditContest (
//    override val info: ContestInfo,
//    val strata: List<OneAuditStratum>,
//) : ContestIF {
@Serializable
data class OAContestJson(
    // val info: ContestInfoJson,
    val strata: List<OAStratumJson>,
)

fun OneAuditContest.publishOAJson() = OAContestJson(
        // this.info.publishJson(),
        this.strata.map { it.publishJson()},
    )

fun OAContestJson.import(info: ContestInfo): OneAuditContest {
    // val info = this.info.import()
    return OneAuditContest(
        info,
        this.strata.map { it.import(info) },
    )
}

// class OneAuditStratum (
//    val strataName: String,
//    val hasCvrs: Boolean,
//    val info: ContestInfo,
//    val votes: Map<Int, Int>,   // candidateId -> nvotes
//    val Ng: Int,  // upper limit on number of ballots in this strata for this contest
//    val Np: Int,  // number of phantom ballots in this strata for this contest
//)
@Serializable
data class OAStratumJson(
    val strataName: String,
    val hasCvrs: Boolean,
    val votes: Map<Int, Int>,
    val Ng: Int,
    val Np: Int,
)

fun OneAuditStratum.publishJson() = OAStratumJson(
    this.strataName,
    this.hasCvrs,
    this.votes,
    this.Ng,
    this.Np
)

fun OAStratumJson.import(info: ContestInfo) = OneAuditStratum(
    this.strataName,
    this.hasCvrs,
    info,
    this.votes,
    this.Ng,
    this.Np
)

// class OAContestUnderAudit(
//    val contestOA: OneAuditContest,
//): ContestUnderAudit(contestOA.makeContest(), isComparison=true, hasStyle=true) {

@Serializable
data class OAContestUnderAuditJson(
    // val info: ContestInfoJson,
    val contestOA: OAContestJson,
    val contestUA: ContestUnderAuditJson,
)

fun OAContestUnderAudit.publishOAJson() = OAContestUnderAuditJson(
        // this.contestOA.info.publishJson(),
        this.contestOA.publishOAJson(),
        (this as ContestUnderAudit).publishJson(),
    )

fun OAContestUnderAuditJson.import(): OAContestUnderAudit {
    // val contestInfo = this.info.import()
    val contestUA = this.contestUA.import()
    val contestOA = this.contestOA.import(contestUA.contest.info)

    val result = OAContestUnderAudit(contestOA, contestUA.isComparison, contestUA.hasStyle)
    result.pollingAssertions = contestUA.pollingAssertions
    result.clcaAssertions = contestUA.clcaAssertions
    return result
}

// data class OAClcaAssorter(
//    val contestOA: OneAuditContest,
//    val assorter: AssorterIF,
//    val avgCvrAssortValue: Double,
//) : ClcaAssorterIF
@Serializable
data class OAClcaAssorterJson(
    val contestOA: OAContestJson,
    val assorter: AssorterIFJson,
    val avgCvrAssortValue: Double,
)

fun OAClcaAssorter.publishJson() = OAClcaAssorterJson(
    this.contestOA.publishOAJson(),
    this.assorter.publishJson(),
    this.avgCvrAssortValue,
)

fun OAClcaAssorterJson.import(info: ContestInfo): OAClcaAssorter {
    val contestOA = this.contestOA.import(info)
    return OAClcaAssorter(
        contestOA,
        this.assorter.import(info),
        this.avgCvrAssortValue,
    )
}
