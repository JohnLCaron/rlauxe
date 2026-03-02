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
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TausRates
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.enumValueOf
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/*
data class AuditRoundConfig(
    val simulation: SimulationControl,
    val sampling: ContestSampleControl,
    val alphaMart: AlphaMartConfig,
    val bettingMart: BettingMartConfig,
) */
@Serializable
data class AuditRoundConfigJson(
    val simulation: SimulationControlJson,
    val sampling: ContestSampleControlJson,
    val alphaMart: AlphaMartConfigJson,
    val bettingMart: BettingMartConfigJson,
)

fun AuditRoundConfig.publishJson() = AuditRoundConfigJson(
    this.simulation.publishJson(),
    this.sampling.publishJson(),
    this.alphaMart.publishJson(),
    this.bettingMart.publishJson(),
)

fun AuditRoundConfigJson.import() = AuditRoundConfig(
    this.simulation.import(),
    this.sampling.import(),
    this.alphaMart.import(),
    this.bettingMart.import(),
)

/*
data class SimulationControl(
    val nsimEst: Int = 100, // number of simulation estimation trials
    val quantile: Double = 0.80, // use this percentile success for estimated sample size
    val simFuzzPct: Double? = null, // for simulating the estimation fuzzing
) */

@Serializable
data class SimulationControlJson(
    val nsimEst: Int = 100, // number of simulation estimation trials
    val quantile: Double = 0.80, // use this percentile success for estimated sample size
    val simFuzzPct: Double? = null, // for simulating the estimation fuzzing
)

fun SimulationControl.publishJson() = SimulationControlJson(
    this.nsimEst,
    this.quantile,
    this.simFuzzPct,
)

fun SimulationControlJson.import() = SimulationControl(
    this.nsimEst,
    this.quantile,
    this.simFuzzPct,
)

/*
data class ContestSampleControl(
    //// checkContestsCorrectlyFormed: preAuditStatus
    val minRecountMargin: Double = 0.005, // do not audit contests less than this recount margin
    val minMargin: Double = 0.0, // do not audit contests less than this margin TODO really it should be noerror?

    //// consistentSampling: contestRound.status, depends on having estimation
    val maxSamplePct: Double = 0.0, // do not audit contests with (estimated nmvrs / contestNc) greater than this
    val removeMaxContests: Int? = null, // remove top n estimated nmvrs contests, for plotting CaseStudiesRemoveNmax
    // conflating maximum in SubsetForEstimation, and maximum sample size per contest and maximum overall sample size
    val contestSampleCutoff: Int? = 30000, // use this number of cvrs in the estimation, set to null to use all
    val removeCutoffContests: Boolean = (contestSampleCutoff != null), // remove contests that need more samples than contestSampleCutoff
) */


@Serializable
data class ContestSampleControlJson(
    val minRecountMargin: Double,
    val minMargin: Double,

    val maxSamplePct: Double,
    val removeMaxContests: Int? = null, // remove top n min-margin contests
    val contestSampleCutoff: Int?,
    val removeCutoffContests: Boolean,
)

fun ContestSampleControl.publishJson() = ContestSampleControlJson(
    this.minRecountMargin,
    this.minMargin,
    this.maxSamplePct,
    this.removeMaxContests,
    this.contestSampleCutoff,
    this.removeCutoffContests,
)

fun ContestSampleControlJson.import() =  ContestSampleControl(
        this.minRecountMargin,
        this.minMargin,
        this.maxSamplePct,
        this.removeMaxContests,
        this.contestSampleCutoff,
        this.removeCutoffContests,
    )

/*
data class AlphaMartConfig(
    val d: Int = 100,  // shrinkTrunc weight
) */

@Serializable
data class AlphaMartConfigJson(
    val d: Int,
)

fun AlphaMartConfig.publishJson() = AlphaMartConfigJson(this.d)
fun AlphaMartConfigJson.import() = AlphaMartConfig(this.d)

/*
data class BettingMartConfig(
    val d: Int = 100,  // shrinkTrunc weight for error rates
    val maxLoss: Double = 0.90,  // max loss on any one bet, 0 < maxLoss < 1
    val apriori: TausRates = TausRates(emptyMap()),
)
 */

@Serializable
data class BettingMartConfigJson(
    val d: Int = 100,  // shrinkTrunc weight for error rates
    val maxLoss: Double = 0.90,  // max loss on any one bet, 0 < maxLoss < 1
    val aprioriRates: Map<String, Double>,
)


fun BettingMartConfig.publishJson() = BettingMartConfigJson(
    this.d,
    this.maxLoss,
    this.apriori.rates,
)

fun BettingMartConfigJson.import() = BettingMartConfig(
        this.d,
        this.maxLoss,
    TausRates(aprioriRates),
    )

/////////////////////////////////////////////////////////////////////////////////

fun writeAuditRoundConfigJsonFile(auditConfig: AuditRoundConfig, filename: String) {
    val json = auditConfig.publishJson()
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    FileOutputStream(filename).use { out ->
        jsonReader.encodeToStream(json, out)
        out.close()
    }
}

fun readAuditRoundConfigJsonFile(filename: String): Result<AuditRoundConfig, ErrorMessages> {
    val errs = ErrorMessages("readAuditRoundConfigJsonFile '${filename}'")
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<AuditRoundConfigJson>(inp)
            val auditConfig = json.import()
            if (errs.hasErrors()) Err(errs) else Ok(auditConfig)
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}

fun readAuditRoundConfigUnwrapped(filename: String): AuditRoundConfig? {
    val result = readAuditRoundConfigJsonFile(filename)
    return if (result.isOk) result.unwrap() else null
}