@file:OptIn(ExperimentalSerializationApi::class)
package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.safeEnumValueOf

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

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
    val choiceFunction = safeEnumValueOf(this.choiceFunction) ?: SocialChoiceFunction.PLURALITY
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
//        override val Np: Int,       // may not know this, if !hasStyles
//        // val hasStyles: Boolean,
//        // val Nb: Int?,  // needed to form the factor N / Nc when !hasStyles
//    )
@Serializable
data class ContestJson(
    val info: ContestInfoJson,
    val votes: Map<Int, Int>, // candidate name -> candidate id
    val Nc: Int,
    val Np: Int,
)

fun Contest.publishJson() : ContestJson {
    return ContestJson(
        this.info.publishJson(),
        this.votes,
        this.Nc,
        this.Np,
    )
}

fun ContestJson.import(): Contest {
    return Contest(
        this.info.import(),
        this.votes,
        this.Nc,
        this.Np,
    )
}

// open class ContestUnderAudit(
//    val contest: ContestIF,           // TODO Rlauxe
//    val isComparison: Boolean = true, // TODO change to AuditType?
//    val hasStyle: Boolean = true,
//)
@Serializable
data class ContestUnderAuditJson(
    val contest: ContestJson,
    val isComparison: Boolean,
    val hasStyle: Boolean,
    var pollingAssertions: List<AssertionJson>,
    var clcaAssertions: List<ClcaAssertionJson>,

    val estSampleSize: Int,  // Estimate of the sample size required to confirm the contest
    val done: Boolean,
    val status: TestH0Status, // or its own enum ??
    val estSampleSizeNoStyles: Int, // number of total samples estimated needed, uniformPolling (Polling, no style only)
)

fun ContestUnderAudit.publishJson() : ContestUnderAuditJson {
    return ContestUnderAuditJson(
        (this.contest as Contest).publishJson(),
        this.isComparison,
        this.hasStyle,
        this.pollingAssertions.map { it.publishJson() },
        this.clcaAssertions.map { it.publishJson() },
        this.estSampleSize,
        this.done,
        this.status,
        this.estSampleSizeNoStyles,
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
    result.estSampleSize = this.estSampleSize
    result.done = this.done
    result.status = this.status
    result.estSampleSizeNoStyles = this.estSampleSizeNoStyles

    return result
}