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
import org.cryptobiotic.rlauxe.core.ClcaAssertion
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

//data class AuditRound(
//    val name: String,
//    val roundIdx: Int,
//    val contests: List<ContestRound>,
//
//    val auditWasDone: Boolean = false,
//    val auditIsComplete: Boolean = false,
//    var sampledIndices: List<Int>, // TODO this is stored separately
//    val nmvrs: Int = 0, // TODO whats ?

// data class AuditRound(
//    val roundIdx: Int,
//    val contests: List<ContestRound>,
//
//    val auditWasDone: Boolean = false,
//    var auditIsComplete: Boolean = false,
//    var sampledIndices: List<Int>, // TODO this is stored separately; called when user chooses new algorithm for sampling
//    val nmvrs: Int = 0, // TODO whats ?

// one in each roundXX subdirectory
@Serializable
data class AuditRoundJson(
    val roundIdx: Int,
    val contests: List<ContestRoundJson>,
    val auditWasDone: Boolean,
    val auditIsComplete: Boolean,
    val sampledIndices: List<Int>,
    val nmvrs: Int,
    )

fun AuditRound.publishJson() : AuditRoundJson {
    return AuditRoundJson(
        this.roundIdx,
        contests.map { it.publishJson() },
        this.auditWasDone,
        this.auditIsComplete,
        this.sampledIndices,
        this.nmvrs,
    )
}

fun AuditRoundJson.import(): AuditRound {
    return AuditRound(
        this.roundIdx,
        contests.map { it.import() },
        this.auditWasDone,
        this.auditIsComplete,
        this.sampledIndices,
        this.nmvrs,
    )
}

// data class ContestRound(val contestUA: ContestUnderAudit, val assertions: List<AssertionRound>, val roundIdx: Int,) {
//    val id = contestUA.id
//    val name = contestUA.name
//    val Nc = contestUA.Nc
//
//    var actualMvrs = 0 // Actual number of new ballots with this contest contained in this round's sample.
//    var actualNewMvrs = 0 // Estimate of the new samples required to confirm the contest
//
//    var estMvrs = 0 // Estimate of the sample size required to confirm the contest
//    var estSampleSizeNoStyles = 0 // number of total samples estimated needed, uniformPolling (Polling, no style only)
//    var done = false
//    var included = true
//    var status = TestH0Status.InProgress // or its own enum ??

@Serializable
data class ContestRoundJson(
    val contestUA: ContestUnderAuditJson?,
    val raireContestUA: RaireContestUnderAuditJson?,
    var assertions: List<AssertionRoundJson>,
    val roundIdx: Int,

    val estMvrs: Int,  // Estimate of the sample size required to confirm the contest
    val estSampleSizeNoStyles: Int, // number of total samples estimated needed, uniformPolling (Polling, no style only)
    val done: Boolean,
    val included: Boolean,
    val status: TestH0Status, // or its own enum ??
)

fun ContestRound.publishJson() : ContestRoundJson {
    val isRaire = (this.contestUA is RaireContestUnderAudit)
    val isClca = (this.contestUA.isComparison)

    return ContestRoundJson(
        if (!isRaire) this.contestUA.publishJson() else null,
        if (isRaire) (this.contestUA as RaireContestUnderAudit).publishRaireJson() else null,
        assertions.map { it.publishJson() },
        this.roundIdx,
        this.estMvrs,
        this.estSampleSizeNoStyles,
        this.done,
        this.included,
        this.status,
    )
}

fun ContestRoundJson.import(): ContestRound {
    val contest = if (this.contestUA != null) this.contestUA.import()
    else this.raireContestUA!!.import()

    val contestRound = ContestRound(contest, assertions.map { it.import() }, this.roundIdx)

    contestRound.estMvrs = this.estMvrs
    contestRound.estSampleSizeNoStyles = this.estSampleSizeNoStyles
    contestRound.done = this.done
    contestRound.included = this.included
    contestRound.status = this.status

    return contestRound
}

// data class AssertionRound(val assertion: Assertion, val roundIdx: Int) {
//    val prevAuditResult: AuditRoundResult? = null // TODO who sets this?
//
//    // these values are set during estimateSampleSizes()
//    var estSampleSize = 0   // estimated sample size for current round
//    var estimationResult: EstimationRoundResult? = null
//
//    // these values are set during runAudit()
//    var auditResult: AuditRoundResult? = null
//    var status = TestH0Status.InProgress
//    var round = 0           // round when set to proved or disproved
//}
@Serializable
data class AssertionRoundJson(
    val assertion: AssertionJson?,
    val clcaAssertion: ClcaAssertionJson?,
    val roundIdx: Int,
    val estSampleSize: Int,
    val estimationResult: EstimationRoundResultJson?,
    val auditResult: AuditRoundResultJson?,
    val prevAuditResult: AuditRoundResultJson?,

    val status: TestH0Status, // or its own enum ??
    val round: Int,
)

fun AssertionRound.publishJson() : AssertionRoundJson {
    val isClca = this.assertion is ClcaAssertion
    return AssertionRoundJson(
        if (!isClca) this.assertion.publishJson() else null,
        if (isClca) (this.assertion as ClcaAssertion).publishJson() else null,
        this.roundIdx,
        this.estSampleSize,
        this.estimationResult?.publishJson(),
        this.auditResult?.publishJson(),
        this.prevAuditResult?.publishJson(),
        this.status,
        this.round,
    )
}

fun AssertionRoundJson.import(): AssertionRound {
    val prevAuditResult = this.prevAuditResult?.import()
    val assertionRound =
        if (this.assertion != null) AssertionRound(this.assertion.import(), this.roundIdx, prevAuditResult)
        else AssertionRound(this.clcaAssertion!!.import(), this.roundIdx, prevAuditResult)

    assertionRound.estSampleSize = this.estSampleSize
    assertionRound.estimationResult = this.estimationResult?.import()
    assertionRound.auditResult = this.auditResult?.import()
    assertionRound.prevAuditResult = this.prevAuditResult?.import()
    assertionRound.status = this.status
    assertionRound.round = this.round

    return assertionRound
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

fun writeAuditRoundJsonFile(AuditRound: AuditRound, filename: String) {
    val json = AuditRound.publishJson()
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    FileOutputStream(filename).use { out ->
        jsonReader.encodeToStream(json, out)
        out.close()
    }
}

fun readAuditRoundJsonFile(filename: String): Result<AuditRound, ErrorMessages> {
    val errs = ErrorMessages("readAuditConfigJsonFile '${filename}'")
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<AuditRoundJson>(inp)
            val AuditRound = json.import()
            if (errs.hasErrors()) Err(errs) else Ok(AuditRound)
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}