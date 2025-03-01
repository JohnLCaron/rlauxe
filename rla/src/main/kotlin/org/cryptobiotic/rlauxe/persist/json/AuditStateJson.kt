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
import org.cryptobiotic.rlauxe.core.ClcaErrorRates
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAuditJson
import org.cryptobiotic.rlauxe.raire.import
import org.cryptobiotic.rlauxe.raire.publishRaireJson
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.workflow.*

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

// per round
//data class AuditState(
//    val name: String,
//    val nmvrs: Int,
//    val contests: List<ContestUnderAudit>,
//    val done: Boolean,
//)

// one in each roundXX subdirectory
@Serializable
data class AuditStateJson(
    val name: String,
    val roundIdx: Int,
    val nmvrs: Int,
    val auditWasDone: Boolean,
    val auditIsComplete: Boolean,
    val contests: List<ContestUnderAuditJson>,
    val rcontests: List<RaireContestUnderAuditJson>,
)

fun AuditState.publishJson() : AuditStateJson {
    val ncontests = this.contests.filter { it !is RaireContestUnderAudit }
    val rcontests= this.contests.filter { it is RaireContestUnderAudit }
    return AuditStateJson(
        this.name,
        this.roundIdx,
        this.nmvrs,
        this.auditWasDone,
        this.auditIsComplete,
        ncontests.map { it.publishJson() },
        rcontests.map { (it as RaireContestUnderAudit).publishRaireJson() },
    )
}

fun AuditStateJson.import(): AuditState {
    return AuditState(
        this.name,
        this.roundIdx,
        this.nmvrs,
        this.auditWasDone,
        this.auditIsComplete,
        this.contests.map { it.import() } + this.rcontests.map { it.import() },
    )
}

// data class EstimationRoundResult(
//    val roundIdx: Int,
//    val fuzzPct: Double,
//    val startingTestStatistic: Double,
//    val startingRates: ClcaErrorRates? = null, // aprioti error rates (clca only)
//    val sampleDeciles: List<Int>,   // distribution of estimated sample size as deciles
//)

@Serializable
data class EstimationRoundResultJson(
    val roundIdx: Int,
    val strategy: String,
    val fuzzPct: Double,
    val startingTestStatistic: Double,
    val startingRates: List<Double>?,
    val estimatedDistribution: List<Int>,
)

fun EstimationRoundResult.publishJson() = EstimationRoundResultJson(
    this.roundIdx,
    this.strategy,
    this.fuzzPct,
    this.startingTestStatistic,
    this.startingRates?.toList(),
    this.estimatedDistribution,
)

fun EstimationRoundResultJson.import() : EstimationRoundResult {
    return EstimationRoundResult(
        this.roundIdx,
        this.strategy,
        this.fuzzPct,
        this.startingTestStatistic,
        if (this.startingRates != null) ClcaErrorRates.fromList(this.startingRates) else null,
        this.estimatedDistribution,
    )
}

// data class AuditRoundResult(
//    val roundIdx: Int,
//    val estSampleSize: Int,   // estimated sample size
//    val maxBallotsUsed: Int,  // maximum ballot index (for multicontest audits) TODO needed?
//    val pvalue: Double,       // last pvalue when testH0 terminates
//    val samplesNeeded: Int,   // first sample when pvalue < riskLimit
//    val samplesUsed: Int,     // sample count when testH0 terminates
//    val status: TestH0Status, // testH0 status
//    val measuredMean: Double, // measured population mean
//    val startingRates: ClcaErrorRates? = null, // aprioti error rates (clca only)
//    val measuredRates: ClcaErrorRates? = null, // measured error rates (clca only)
//)

@Serializable
data class AuditRoundResultJson(
    val desc: String,
    val roundIdx: Int,
    val estSampleSize: Int,   // estimated sample size
    val maxBallotIndexUsed: Int,   // max index used
    val pvalue: Double,       // last pvalue when testH0 terminates
    val samplesNeeded: Int,   // first sample when pvalue < riskLimit
    val samplesUsed: Int,     // sample count when testH0 terminates, usually maxSamples
    val status: String, // testH0 status
    val measuredMean: Double,     // measured population mean
    val startingRates: List<Double>?,
    val measuredRates: List<Double>?,
)

fun AuditRoundResult.publishJson() = AuditRoundResultJson(
    this.toString(),
    this.roundIdx,
    this.estSampleSize,
    this.maxBallotIndexUsed,
    this.pvalue,
    this.samplesNeeded,
    this.samplesUsed,
    this.status.name,
    this.measuredMean,
    this.startingRates?.toList(),
    this.measuredRates?.toList(),
)

fun AuditRoundResultJson.import() : AuditRoundResult {
    val status = org.cryptobiotic.rlauxe.util.enumValueOf(this.status, TestH0Status.entries) ?: TestH0Status.InProgress
    return AuditRoundResult(
        this.roundIdx,
        this.estSampleSize,
        this.maxBallotIndexUsed,
        this.pvalue,
        this.samplesNeeded,
        this.samplesUsed,
        status,
        this.measuredMean,
        if (this.startingRates != null) ClcaErrorRates.fromList(this.startingRates) else null,
        if (this.measuredRates != null) ClcaErrorRates.fromList(this.measuredRates) else null,
    )
}

/////////////////////////////////////////////////////////////////////////////////

fun writeAuditStateJsonFile(auditState: AuditState, filename: String) {
    val json = auditState.publishJson()
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    FileOutputStream(filename).use { out ->
        jsonReader.encodeToStream(json, out)
        out.close()
    }
}

fun readAuditStateJsonFile(filename: String): Result<AuditState, ErrorMessages> {
    val errs = ErrorMessages("readAuditConfigJsonFile '${filename}'")
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<AuditStateJson>(inp)
            val auditState = json.import()
            if (errs.hasErrors()) Err(errs) else Ok(auditState)
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}