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
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.enumValueOf

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

// data class AuditRound(
//    val roundIdx: Int,
//    val contestRounds: List<ContestRound>,
//
//    var auditWasDone: Boolean = false,
//    var auditIsComplete: Boolean = false,
//    var sampleNumbers: List<Long>, // ballot indices to sample for this round
//    var nmvrs: Int = 0,
//    var newmvrs: Int = 0,
//    var auditorWantNewMvrs: Int = -1,
//)

// one in each roundXX subdirectory
// note dont store samplePrns: List<Long>, these are kept im seperate file
@Serializable
data class AuditRoundJson(
    val roundIdx: Int,
    val contestRounds: List<ContestRoundJson>,
    val auditWasDone: Boolean,
    val auditIsComplete: Boolean,
    val nmvrs: Int,
    var newmvrs: Int,
    var auditorWantNewMvrs: Int,
)

fun AuditRoundIF.publishJson() : AuditRoundJson {
    return AuditRoundJson(
        this.roundIdx,
        contestRounds.map { it.publishJson() },
        this.auditWasDone,
        this.auditIsComplete,
        this.nmvrs,
        this.newmvrs,
        this.auditorWantNewMvrs,
    )
}

fun AuditRoundJson.import(contestUAs: List<ContestWithAssertions>, samplePrns: List<Long>): AuditRound {
    val contestUAmap = contestUAs.associateBy { it.id }
    val contestRounds = this.contestRounds.map {
        it.import( contestUAmap[it.id]!! )
    }
    // TODO Composite ??
    return AuditRound(
        this.roundIdx,
        contestRounds,
        this.auditWasDone,
        this.auditIsComplete,
        samplePrns,
        this.nmvrs,
        this.newmvrs,
        this.auditorWantNewMvrs,
    )
}

// data class ContestRound(val contestUA: ContestWithAssertions, val assertionRounds: List<AssertionRound>, val roundIdx: Int) {
//    val id = contestUA.id
//    val name = contestUA.name
//    val Npop = contestUA.Npop
//
//    var estCardsNeeded = 0 // initial estiimate of cards for the contests
//
//    var actualMvrs = 0 // Actual number of ballots with this contest contained in this round's sample.
//    var actualNewMvrs = 0 // Actual number of new ballots with this contest contained in this round's sample.
//
//    var estNewSamples = 0 // Estimate of the new sample size required to confirm the contest
//    var estSampleSize = 0 // number of total samples estimated needed
//    var auditorWantNewMvrs: Int = -1 // Auditor has set the new sample size for this audit round. rlauxe-viewer
//    var done = false
//    var included = true
//    var status = contestUA.preAuditStatus

@Serializable
data class ContestRoundJson(
    val id: Int,
    var assertionRounds: List<AssertionRoundJson>,
    val roundIdx: Int,

    val maxSampleIndex: Int,
    val estMvrs: Int,
    val estNewMvrs: Int,
    val actualMvrs: Int,
    val actualNewMvrs: Int,
    val auditorWantNewMvrs: Int,

    val done: Boolean,
    val included: Boolean,
    val status: TestH0Status, // or its own enum ??
)

fun ContestRound.publishJson() : ContestRoundJson {
    return ContestRoundJson(
        this.id,
        assertionRounds.map { it.publishJson() },
        roundIdx = this.roundIdx,
        maxSampleIndex = this.maxSampleIndex,
        estMvrs = this.estMvrs,
        estNewMvrs = this.estNewMvrs,
        actualMvrs = this.actualMvrs,
        actualNewMvrs = this.actualNewMvrs,
        auditorWantNewMvrs = this.auditorWantNewMvrs,
        this.done,
        this.included,
        this.status,
    )
}

fun ContestRoundJson.import(contestUA: ContestWithAssertions): ContestRound {
    val assertionMap = contestUA.assertions().associateBy { it.assorter.hashcodeDesc() }
    val assertionRounds = assertionRounds.map {
        val ref = assertionMap[it.assorterDesc]
        if (ref == null) {
            assertionMap.forEach { key, value -> println("$key = ${value.assorter}") }
            throw RuntimeException("ContestRoundJson.assorter desc='${it.assorterDesc}' is missing")
        }
        it.import( ref )
    }
    val contestRound = ContestRound(contestUA, assertionRounds, this.roundIdx)

    contestRound.maxSampleIndex = this.maxSampleIndex
    contestRound.estMvrs = this.estMvrs
    contestRound.estNewMvrs = this.estNewMvrs
    contestRound.actualMvrs = this.actualMvrs
    contestRound.actualNewMvrs = this.actualNewMvrs
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
    val assorterDesc: String,
    val roundIdx: Int,
    val estSampleSize: Int,
    val estNewSampleSize: Int,
    val estimationResult: EstimationRoundResultJson?,
    val auditResult: AuditRoundResultJson?,
    val prevAuditResult: AuditRoundResultJson?,

    val status: TestH0Status, // or its own enum ??
    val roundProved: Int,
)

fun AssertionRound.publishJson() : AssertionRoundJson {
    return AssertionRoundJson(
        this.assertion.assorter.hashcodeDesc(),
        this.roundIdx,
        this.estMvrs,
        this.estNewMvrs,
        this.estimationResult?.publishJson(),
        this.auditResult?.publishJson(),
        this.prevAuditResult?.publishJson(),
        this.status,
        this.roundProved,
    )
}

fun AssertionRoundJson.import(assertion: Assertion): AssertionRound {
    val prevAuditResult = this.prevAuditResult?.import()
    val assertionRound = AssertionRound(assertion, this.roundIdx, prevAuditResult)
    //    if (this.assertion != null) AssertionRound(this.assertion.import(), this.roundIdx, prevAuditResult)
   //     else AssertionRound(this.clcaAssertion!!.import(), this.roundIdx, prevAuditResult)

    assertionRound.estMvrs = this.estSampleSize
    assertionRound.estNewMvrs = this.estNewSampleSize
    assertionRound.estimationResult = this.estimationResult?.import()
    assertionRound.auditResult = this.auditResult?.import()
    assertionRound.prevAuditResult = this.prevAuditResult?.import()
    assertionRound.status = this.status
    assertionRound.roundProved = this.roundProved

    return assertionRound
}

//data class EstimationRoundResult(
//    val roundIdx: Int,
//    val strategy: String,
//    val fuzzPct: Double?,
//    val startingTestStatistic: Double,
//    val startingRates: Map<Double, Double>? = null, // error rates used for estimation
//    val estimatedDistribution: List<Int>,   // distribution of estimated sample size; currently deciles
//    val firstSample: Int,
//)

@Serializable
data class EstimationRoundResultJson(
    val roundIdx: Int,
    val strategy: String,
    val fuzzPct: Double?,
    val startingTestStatistic: Double,
    val startingErrorRates: Map<Double, Double>? = null, // error rates used for estimation
    val estimatedDistribution: List<Int>,
    val firstSample: Int,
    val estNewMvrs: Int = 0,
)

fun EstimationRoundResult.publishJson() = EstimationRoundResultJson(
    this.roundIdx,
    this.strategy,
    this.fuzzPct,
    this.startingTestStatistic,
    this.startingErrorRates,
    this.estimatedDistribution,
    this.firstSample,
    this.estNewMvrs,
)

fun EstimationRoundResultJson.import() : EstimationRoundResult {
    val rr = EstimationRoundResult(
        this.roundIdx,
        this.strategy,
        this.fuzzPct,
        this.startingTestStatistic,
        this.startingErrorRates,
        this.estimatedDistribution,
        this.firstSample,
    )
    rr.estNewMvrs = this.estNewMvrs
    return rr
}

// data class AuditRoundResult(
//    val roundIdx: Int,
//    val nmvrs: Int,               // number of mvrs available for this contest for this round
//    val maxBallotIndexUsed: Int,  // maximum ballot index (for multicontest audits)
//    val pvalue: Double,       // last pvalue when testH0 terminates
//    val samplesUsed: Int,     // sample count when testH0 terminates
//    val status: TestH0Status, // testH0 status
//    val measuredMean: Double, // measured population mean TODO used?
//    val startingRates: ClcaErrorCounts? = null, // starting error rates (clca only)
//    val measuredCounts: ClcaErrorCounts? = null, // measured error counts (clca only)
//    val params: Map<String, Double> = emptyMap(),
//)

@Serializable
data class AuditRoundResultJson(
    val desc: String,
    val roundIdx: Int,
    val nmvrs: Int,   // estimated sample size
    val maxBallotIndexUsed: Int,   // max index used
    val pvalue: Double,       // last pvalue when testH0 terminates
    val samplesUsed: Int,     // sample count when testH0 terminates, usually maxSamples
    val status: String, // testH0 status
    // val startingRates: Map<Double, Double>?,
    val measuredCounts: ClcaErrorCountsJson?,
    val params: Map<String, Double>
)

fun AuditRoundResult.publishJson() = AuditRoundResultJson(
    this.toString(),
    this.roundIdx,
    this.nmvrs,
    this.maxBallotIndexUsed,
    this.pvalue,
    this.samplesUsed,
    this.status.name,
    // this.startingRates,
    this.measuredCounts?.publishJson(),
    params,
)

fun AuditRoundResultJson.import() : AuditRoundResult {
    val status = enumValueOf(this.status, TestH0Status.entries) ?: TestH0Status.InProgress
    return AuditRoundResult(
        this.roundIdx,
        this.nmvrs,
        this.maxBallotIndexUsed,
        this.pvalue,
        this.samplesUsed,
        status,
        // this.startingRates,
        this.measuredCounts?.import(),
        params,
    )
}

//data class ClcaErrorCounts(val errorCounts: Map<Double, Int>, val totalSamples: Int, val noerror: Double, val upper: Double): ClcaErrorRatesIF {

@Serializable
data class ClcaErrorCountsJson(
    val errorCounts: Map<Double, Int>,
    val totalSamples: Int,
    val noerror: Double,
    val upper: Double
)

fun ClcaErrorCounts.publishJson() = ClcaErrorCountsJson(
        errorCounts,
        totalSamples,
        noerror,
        upper,
    )

fun ClcaErrorCountsJson.import() = ClcaErrorCounts(
    errorCounts,
    totalSamples,
    noerror,
    upper,
)

/////////////////////////////////////////////////////////////////////////////////

fun writeAuditRoundJsonFile(auditRound: AuditRoundIF, filename: String) {
    val json = auditRound.publishJson()
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    FileOutputStream(filename).use { out ->
        jsonReader.encodeToStream(json, out)
        out.close()
    }
}

fun readAuditRoundJsonFile(
    auditRoundFile: String,
    contests: List<ContestWithAssertions>,
    samplePrns: List<Long>,
): Result<AuditRoundIF, ErrorMessages> {

    val errs = ErrorMessages("readAuditRoundJsonFile '${auditRoundFile}'")
    val filepath = Path.of(auditRoundFile)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<AuditRoundJson>(inp)
            val AuditRound = json.import(contests, samplePrns)
            if (errs.hasErrors()) Err(errs) else Ok(AuditRound)
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}