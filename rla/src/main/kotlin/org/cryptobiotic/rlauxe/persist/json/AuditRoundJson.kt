@file:OptIn(ExperimentalSerializationApi::class)
package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.workflow.*

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

// data class AuditRound(
//    val roundIdx: Int,
//    val contests: List<ContestRound>,
//    val auditWasDone: Boolean = false,
//    var auditIsComplete: Boolean = false,
//    var sampledIndices: List<Int>,
//    var nmvrs: Int = 0,
//    var newmvrs: Int = 0,
//    var auditorSetNewMvrs: Int = -1,
//) {

// one in each roundXX subdirectory
@Serializable
data class AuditRoundJson(
    val roundIdx: Int,
    val contestRounds: List<ContestRoundJson>,
    val auditWasDone: Boolean,
    val auditIsComplete: Boolean,
    // val sampledIndices: List<Int>,
    val nmvrs: Int,
    var newmvrs: Int,
    var auditorWantNewMvrs: Int,
)

fun AuditRound.publishJson() : AuditRoundJson {
    return AuditRoundJson(
        this.roundIdx,
        contestRounds.map { it.publishJson() },
        this.auditWasDone,
        this.auditIsComplete,
        // this.sampledIndices,
        this.nmvrs,
        this.newmvrs,
        this.auditorWantNewMvrs,
    )
}

fun AuditRoundJson.import(contestUAs: List<ContestUnderAudit>, sampledIndices: List<Int>): AuditRound {
    val contestUAmap = contestUAs.associateBy { it.id }
    val contestRounds = this.contestRounds.map {
        it.import( contestUAmap[it.id]!! )
    }
    return AuditRound(
        this.roundIdx,
        contestRounds,
        this.auditWasDone,
        this.auditIsComplete,
        sampledIndices,
        this.nmvrs,
        this.newmvrs,
        this.auditorWantNewMvrs,
    )
}

// data class ContestRound(val contestUA: ContestUnderAudit, val assertions: List<AssertionRound>, val roundIdx: Int,) {
//    val id = contestUA.id
//    val name = contestUA.name
//    val Nc = contestUA.Nc
//
//    var actualMvrs = 0 // Actual number of ballots with this contest contained in this round's sample.
//    var actualNewMvrs = 0 // Actual number of new ballots with this contest contained in this round's sample.
//
//    var estNewSamples = 0 // Estimate of the new sample size required to confirm the contest
//    var estSampleSize = 0 // number of total samples estimated needed, consistentSampling
//    var estSampleSizeNoStyles = 0 // number of total samples estimated needed, uniformSampling
//    var done = false
//    var included = true
//    var status = TestH0Status.InProgress // or its own enum ??

@Serializable
data class ContestRoundJson(
    // val contestUA: ContestUnderAuditJson?,
    // val raireContestUA: RaireContestUnderAuditJson?,
    val id: Int,
    var assertionRounds: List<AssertionRoundJson>,
    val roundIdx: Int,

    val actualMvrs: Int,
    val actualNewMvrs: Int,  // Estimate of new sample size required to confirm the contest
    val estNewSamples: Int,
    val estSampleSize: Int,  // Estimate of total sample size required to confirm the contest
    val estSampleSizeNoStyles: Int, // number of total samples estimated needed, uniformPolling (Polling, no style only)
    val auditorWantNewMvrs: Int,

    val done: Boolean,
    val included: Boolean,
    val status: TestH0Status, // or its own enum ??
)

fun ContestRound.publishJson() : ContestRoundJson {
    if (this.contestUA is RaireContestUnderAudit)
        println()

    return ContestRoundJson(
        // if (!isRaire) this.contestUA.publishJson() else null,
        // if (isRaire) (this.contestUA as RaireContestUnderAudit).publishRaireJson() else null,
        this.id,
        assertionRounds.map { it.publishJson() },
        this.roundIdx,
        this.actualMvrs,
        this.actualNewMvrs,
        this.estNewSamples,
        this.estSampleSize,
        this.estSampleSizeNoStyles,
        this.auditorWantNewMvrs,
        this.done,
        this.included,
        this.status,
    )
}

fun ContestRoundJson.import(contestUA: ContestUnderAudit): ContestRound {
    if (contestUA is RaireContestUnderAudit)
        println()

    //val contest = if (this.contestUA != null) this.contestUA.import()
    //else this.raireContestUA!!.import()

    // not exactly type safe TODO fails on Raire
    val assertionMap = contestUA.assertions().associateBy { it.assorter.desc() }
    val assertionRounds = assertionRounds.map {
        it.import( assertionMap[it.assorterDesc]!! )
    }
    val contestRound = ContestRound(contestUA, assertionRounds, this.roundIdx)

    contestRound.actualMvrs = this.actualMvrs
    contestRound.actualNewMvrs = this.actualNewMvrs
    contestRound.estNewSamples = this.estNewSamples
    contestRound.estSampleSize = this.estSampleSize
    contestRound.estSampleSizeNoStyles = this.estSampleSizeNoStyles
    contestRound.auditorWantNewMvrs = this.auditorWantNewMvrs

    contestRound.done = this.done
    contestRound.included = this.included
    contestRound.status = this.status

    return contestRound
}

// data class AssertionRound(val assertion: Assertion, val roundIdx: Int, var prevAuditResult: AuditRoundResult?) {
//    // these values are set during estimateSampleSizes()
//    var estSampleSize = 0   // estimated sample size for current round
//    var estNewSampleSize = 0   // estimated new sample size for current round
//    var estimationResult: EstimationRoundResult? = null
//
//    // these values are set during runAudit()
//    var auditResult: AuditRoundResult? = null
//    var status = TestH0Status.InProgress
//    var round = 0           // round when set to proved or disproved
//}
@Serializable
data class AssertionRoundJson(
    // val assertion: AssertionJson?,
    // val clcaAssertion: ClcaAssertionJson?,
    val assorterDesc: String,
    val roundIdx: Int,
    val estSampleSize: Int,
    val estNewSampleSize: Int,
    val estimationResult: EstimationRoundResultJson?,
    val auditResult: AuditRoundResultJson?,
    val prevAuditResult: AuditRoundResultJson?,

    val status: TestH0Status, // or its own enum ??
    val round: Int,
)

fun AssertionRound.publishJson() : AssertionRoundJson {
    val isClca = this.assertion is ClcaAssertion
    return AssertionRoundJson(
        // if (!isClca) this.assertion.publishJson() else null,
        // if (isClca) (this.assertion as ClcaAssertion).publishJson() else null,
        this.assertion.assorter.desc(),
        this.roundIdx,
        this.estSampleSize,
        this.estNewSampleSize,
        this.estimationResult?.publishJson(),
        this.auditResult?.publishJson(),
        this.prevAuditResult?.publishJson(),
        this.status,
        this.round,
    )
}

fun AssertionRoundJson.import(assertion: Assertion): AssertionRound {
    val prevAuditResult = this.prevAuditResult?.import()
    val assertionRound = AssertionRound(assertion, this.roundIdx, prevAuditResult)
    //    if (this.assertion != null) AssertionRound(this.assertion.import(), this.roundIdx, prevAuditResult)
   //     else AssertionRound(this.clcaAssertion!!.import(), this.roundIdx, prevAuditResult)

    assertionRound.estSampleSize = this.estSampleSize
    assertionRound.estNewSampleSize = this.estNewSampleSize
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
    val nmvrs: Int,   // estimated sample size
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
    this.nmvrs,
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
        this.nmvrs,
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

fun readAuditRoundJsonFile(contestsFile: String, samplesFile: String, auditRoundFile: String): Result<AuditRound, ErrorMessages> {
    val contestsResult = readContestsJsonFile(contestsFile)
    if (contestsResult is Err) return contestsResult
    val contests = contestsResult.unwrap()

    val sampleResult = readSampleIndicesJsonFile(samplesFile)
    if (sampleResult is Err) return sampleResult
    val samples = sampleResult.unwrap()

    return readAuditRoundJsonFile(contests, samples, auditRoundFile)
}

fun readAuditRoundJsonFile(contests: List<ContestUnderAudit>, sampledIndices: List<Int>, auditRoundFile: String): Result<AuditRound, ErrorMessages> {
    val errs = ErrorMessages("readAuditConfigJsonFile '${auditRoundFile}'")
    val filepath = Path.of(auditRoundFile)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<AuditRoundJson>(inp)
            val AuditRound = json.import(contests, sampledIndices)
            if (errs.hasErrors()) Err(errs) else Ok(AuditRound)
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}