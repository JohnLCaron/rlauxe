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
import org.cryptobiotic.rlauxe.betting.ClcaErrorTracker
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.Welford

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
//)

// one in each roundXX subdirectory
// note dont store samplePrns: List<Long>, these are kept in seperate file
@Serializable
data class AuditRoundJson(
    val roundIdx: Int,
    val contestRounds: List<ContestRoundJson>,
    val auditWasDone: Boolean,
    val auditIsComplete: Boolean,
    val nmvrs: Int,
    var newmvrs: Int,
    var mvrsUnused: Int,
    var mvrsUsed: Int,
)

fun AuditRoundIF.publishJson() : AuditRoundJson {
    return AuditRoundJson(
        this.roundIdx,
        contestRounds.map { it.publishJson() },
        this.auditWasDone,
        this.auditIsComplete,
        this.nmvrs,
        this.newmvrs,
        this.mvrsUsed,
        this.mvrsUnused,
    )
}

fun AuditRoundJson.import(contestUAs: List<ContestWithAssertions>, samplePrns: List<Long>, prevAuditRound: AuditRound?): AuditRound {
    val contestUAmap = contestUAs.associateBy { it.id }
    val prevContestMap = prevAuditRound?.contestRounds?.associateBy { it.id } ?: emptyMap()
    val contestRounds = this.contestRounds.map {
        it.import( contestUAmap[it.id]!!, prevContestMap[it.id])
    }
    return AuditRound(
        this.roundIdx,
        contestRounds,
        this.auditWasDone,
        this.auditIsComplete,
        samplePrns,
        this.nmvrs,
        this.newmvrs,
        this.mvrsUsed,
        this.mvrsUnused,
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
//    var done = false
//    var included = true
//    var status = contestUA.preAuditStatus

@Serializable
data class ContestRoundJson(
    val id: Int,
    var assertionRounds: List<AssertionRoundJson>,
    val roundIdx: Int,

    val maxSampleAllowed: Int?,
    val estMvrs: Int,
    val estNewMvrs: Int,

    val done: Boolean,
    val included: Boolean,
    val status: TestH0Status, // or its own enum ??
)

fun ContestRound.publishJson() : ContestRoundJson {
    return ContestRoundJson(
        this.id,
        assertionRounds.map { it.publishJson() },
        roundIdx = this.roundIdx,
        maxSampleAllowed = this.maxSampleAllowed,
        estMvrs = this.estMvrs,
        estNewMvrs = this.estNewMvrs,
        this.done,
        this.included,
        this.status,
    )
}

fun ContestRoundJson.import(contestUA: ContestWithAssertions, prevContestRound: ContestRound?): ContestRound {
    val prevAssertionMap = prevContestRound?.assertionRounds?.associateBy { it.assertion.assorter.hashcodeDesc() } ?: emptyMap()
    val assorterMap = contestUA.assertions().associateBy { it.assorter.hashcodeDesc() } // tricky
    val assertionRounds = assertionRounds.map { assertionRound ->
        val ref = assorterMap[assertionRound.assorterDesc]
        if (ref == null) {
            assorterMap.forEach { key, value -> println("$key = ${value.assorter}") }
            throw RuntimeException("ContestRoundJson.assorter desc='${assertionRound.assorterDesc}' is missing")
        }
        assertionRound.import( ref, prevAssertionMap[assertionRound.assorterDesc] )
    }
    val contestRound = ContestRound(contestUA, assertionRounds, this.roundIdx)

    contestRound.maxSampleAllowed = this.maxSampleAllowed
    contestRound.estMvrs = this.estMvrs
    contestRound.estNewMvrs = this.estNewMvrs

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
    // val prevAuditResult: AuditRoundResultJson?,

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
        // this.prevAuditResult?.publishJson(),
        this.status,
        this.roundProved,
    )
}

fun AssertionRoundJson.import(assertion: Assertion, prevRound: AssertionRound?): AssertionRound {
    val assertionRound = AssertionRound(assertion, this.roundIdx, prevRound)

    assertionRound.estMvrs = this.estSampleSize
    assertionRound.estNewMvrs = this.estNewSampleSize
    assertionRound.estimationResult = this.estimationResult?.import()
    assertionRound.auditResult = this.auditResult?.import()
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
    val calcMvrsNeeded: Int,
    val startingTestStatistic: Double,
    val startingErrorRates: Map<Double, Double>? = null, // error rates used for estimation
    val estimatedDistribution: List<Int>,
    val lastIndex: Int = 0,
    val quantile: Int = 0,
    val ntrials: Int,
    val simNewMvrs: Int = 0,
    val simMvrs: Int = 0,
)

fun EstimationRoundResult.publishJson() = EstimationRoundResultJson(
    this.roundIdx,
    this.strategy,
    this.calcNewMvrsNeeded,
    this.startingTestStatistic,
    this.startingErrorRates,
    this.estimatedDistribution,
    this.lastIndex,
    this.percentile,
    this.ntrials,
    this.simNewMvrsNeeded,
    this.simMvrsNeeded,
)

fun EstimationRoundResultJson.import() = EstimationRoundResult(
        this.roundIdx,
        this.strategy,
        this.calcMvrsNeeded,
        this.startingTestStatistic,
        this.startingErrorRates,
        this.estimatedDistribution,
        this.lastIndex,
        this.quantile,
        this.ntrials,
        this.simNewMvrs,
        this.simMvrs,
    )

// data class AuditRoundResult(
//    val roundIdx: Int,
//    val nmvrs: Int,                 // number of mvrs available for this contest for this round
//    val plast: Double,              // last pvalue when testH0 terminates
//    val pmin: Double,               // minimum pvalue reached
//    val samplesUsed: Int,           // sample count when testH0 terminates
//    val status: TestH0Status,       // testH0 status
//    val clcaErrorTracker: ClcaErrorTracker?, // measured error counts (clca only)
//    val params: Map<String, Double> = emptyMap(),
//)

@Serializable
data class AuditRoundResultJson(
    val desc: String,
    val roundIdx: Int,
    val nmvrs: Int,   // estimated sample size
    val plast: Double,       // last pvalue when testH0 terminates
    val pmin: Double,       // minimum pvalue reached
    val samplesUsed: Int,     // sample count when testH0 terminates, usually maxSamples
    val status: TestH0Status, // testH0 status
    val clcaErrorTracker: ClcaErrorTrackerJson?,
    val params: Map<String, Double>
)

fun AuditRoundResult.publishJson() = AuditRoundResultJson(
    this.toString(),
    this.roundIdx,
    nmvrs = this.nmvrs,
    plast = this.plast,
    pmin = this.pmin,
    samplesUsed = this.samplesUsed,
    status = this.status,
    clcaErrorTracker = this.clcaErrorTracker?.publishJson(),
    params = params,
)

fun AuditRoundResultJson.import() : AuditRoundResult {
    return AuditRoundResult(
        this.roundIdx,
        nmvrs=this.nmvrs,
        plast=this.plast,
        pmin=this.pmin,
        samplesUsed=this.samplesUsed,
        status=this.status,
        clcaErrorTracker=this.clcaErrorTracker?.import(),
        params=params,
    )
}

/**
 * class ClcaErrorTracker(
 * val noerror: Double,
 * val upper: Double,
 * val welford:Welford,
 * val errorCounts: MutableMap<Double, Int>)
 */
@Serializable
data class ClcaErrorTrackerJson(
    val noerror: Double,
    val upper: Double,
    val errorCounts: Map<Double, Int>,
    val count: Int,
    val mean: Double,
    val M2: Double,
)

// looks like you just needf welford?
// data class Welford(
//    var count: Int = 0,      // number of samples
//    var mean: Double = 0.0,  // mean accumulates the mean of the entire sequence
//    var M2: Double = 0.0,    // M2 aggregates the squared distance from the mean
//)
fun ClcaErrorTracker.publishJson() = ClcaErrorTrackerJson(
    noerror,
    upper,
    errorCounts,
    welford.count,
    welford.mean,
    welford.M2,
)

fun ClcaErrorTrackerJson.import() = ClcaErrorTracker(
    noerror,
    upper,
    Welford(count, mean, M2),
    errorCounts.toMutableMap(),
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
    prevAuditRound: AuditRound?,
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
            val AuditRound = json.import(contests, samplePrns, prevAuditRound)
            if (errs.hasErrors()) Err(errs) else Ok(AuditRound)
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}