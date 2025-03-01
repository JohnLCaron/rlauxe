@file:OptIn(ExperimentalSerializationApi::class)
package org.cryptobiotic.rlauxe.persist.json

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.raire.RaireAssorter
import org.cryptobiotic.rlauxe.raire.RaireContest
import org.cryptobiotic.rlauxe.raire.import
import org.cryptobiotic.rlauxe.raire.publishJson
import org.cryptobiotic.rlauxe.util.enumValueOf


// data class ContestInfo(
//    val name: String,
//    val id: Int,
//    val candidateNames: Map<String, Int>, // candidate name -> candidate id
//    val choiceFunction: SocialChoiceFunction,
//    val nwinners: Int = 1,
//    val minFraction: Double? = null, // supermajority only.
//)
@Serializable
data class ContestInfoJson(
    val name: String,
    val id: Int,
    val candidateNames: Map<String, Int>, // candidate name -> candidate id
    val choiceFunction: String,
    val nwinners: Int,
    val minFraction: Double?,
)

fun ContestInfo.publishJson() : ContestInfoJson {
    return ContestInfoJson(
        this.name,
        this.id,
        this.candidateNames,
        this.choiceFunction.name,
        this.nwinners,
        this.minFraction,
    )
}

fun ContestInfoJson.import(): ContestInfo {
    val choiceFunction = enumValueOf(this.choiceFunction, SocialChoiceFunction.entries) ?: SocialChoiceFunction.PLURALITY
    return ContestInfo(
        this.name,
        this.id,
        this.candidateNames,
        choiceFunction,
        this.nwinners,
        this.minFraction,
    )
}

// class Contest(
//        override val info: ContestInfo,
//        voteInput: Map<Int, Int>,   // candidateId -> nvotes;  sum is nvotes or V_c
//        override val Nc: Int,
//        override val Np: Int,       // TODO may not know this, if !hasStyles
//    ): ContestIF {
// data class RaireContest(
//    override val info: ContestInfo,
//    override val winners: List<Int>,
//    override val Nc: Int,
//    override val Np: Int,
//)
@Serializable
data class ContestIFJson(
    val className: String,
    val info: ContestInfoJson,
    val votes: Map<Int, Int>?, // candidate name -> candidate id
    val winners: List<Int>?,
    val Nc: Int,
    val Np: Int,
)

fun ContestIF.publishJson() : ContestIFJson {
    return when (this) {
        is Contest ->
            ContestIFJson(
                "Contest",
                this.info.publishJson(),
                this.votes,
                null,
                this.Nc,
                this.Np,
            )
        is RaireContest ->
            ContestIFJson(
                "RaireContest",
                this.info.publishJson(),
                null,
                this.winners,
                this.Nc,
                this.Np,
            )
        else -> throw RuntimeException("unknown assorter type ${this.javaClass.simpleName} = $this")
    }
}

fun ContestIFJson.import(): ContestIF {
    return when (this.className) {
        "Contest" ->
            Contest(
                this.info.import(),
                this.votes!!,
                this.Nc,
                this.Np,
            )
        "RaireContest" ->
            RaireContest(
                this.info.import(),
                this.winners!!,
                this.Nc,
                this.Np,
            )
        else -> throw RuntimeException()
    }
}

// open class ContestUnderAudit(
//    val contest: ContestIF,
//    val isComparison: Boolean = true, // TODO change to AuditType?
//    val hasStyle: Boolean = true,
//) {
//    val id = contest.info.id
//    val name = contest.info.name
//    val choiceFunction = contest.info.choiceFunction
//    val ncandidates = contest.info.candidateIds.size
//    val Nc = contest.Nc
//    val Np = contest.Np
//
//    var pollingAssertions: List<Assertion> = emptyList()
//    var clcaAssertions: List<ClcaAssertion> = emptyList()
//
//    var actualMvrs = 0 // Actual number of new ballots with this contest contained in this round's sample.
//    var estMvrs = 0 // Estimate of the sample size required to confirm the contest
//    var estNewMvrs = 0 // Estimate of the new sample size
//    var estSampleSizeNoStyles = 0 // number of total samples estimated needed, uniformPolling (Polling, no style only)
//    var done = false
//    var included = true
//    var status = TestH0Status.InProgress // or its own enum ??)
@Serializable
data class ContestUnderAuditJson(
    val contest: ContestIFJson,
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

fun ContestUnderAudit.publishJson() : ContestUnderAuditJson {
    return ContestUnderAuditJson(
        this.contest.publishJson(),
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
}

fun ContestUnderAuditJson.import(): ContestUnderAudit {
    val result = ContestUnderAudit(
        this.contest.import(),
        this.isComparison,
        this.hasStyle,
    )
    result.pollingAssertions = this.pollingAssertions.map { it.import() }
    result.clcaAssertions = this.clcaAssertions.map { it.import() }
    result.estMvrs = this.estMvrs
    result.estSampleSizeNoStyles = this.estSampleSizeNoStyles
    result.done = this.done
    result.included = this.included
    result.status = this.status

    return result
}