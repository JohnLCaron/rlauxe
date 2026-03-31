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

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
/*
data class AuditRoundConfig(
    val simulation: SimulationControl,
    val sampling: ContestSampleControl,
    val clcaConfig: ClcaConfig?,
    val pollingConfig: PollingConfig?,

    val other: Map<String, String> = emptyMap(),    // soft parameters
) */
@Serializable
data class AuditRoundConfigJson(
    val simulation: SimulationControlJson,
    val sampling: ContestSampleControlJson,
    val clcaConfig: ClcaConfigJson?,
    val pollingConfig: PollingConfigJson?,
)

fun AuditRoundConfig.publishJson() = AuditRoundConfigJson(
    this.simulation.publishJson(),
    this.sampling.publishJson(),
    this.clcaConfig?.publishJson(),
    this.pollingConfig?.publishJson(),
)

fun AuditRoundConfigJson.import() = AuditRoundConfig(
    this.simulation.import(),
    this.sampling.import(),
    this.clcaConfig?.import(),
    this.pollingConfig?.import(),
)

/*
data class SimulationControl(
    val nsimEst: Int = 20, // number of simulation estimation trials
    val estPercentSuccess: List<Double> = listOf(50, 80), // use this percentile success for estimated sample size in round idx+1
    val simFuzzPct: Double? = null, // for estimation fuzzing
    val simulationStrategy: SimulationStrategy = SimulationStrategy.optimistic
)*/

@Serializable
data class SimulationControlJson(
    val nsimEst: Int, // number of simulation estimation trials
    val estPercentile: List<Int>, // use this percentile success for estimated sample size
    val simFuzzPct: Double?, // for simulating the estimation fuzzing
    val simulationStrategy: SimulationStrategy, // for simulating the estimation fuzzing
)

fun SimulationControl.publishJson() = SimulationControlJson(
    this.nsimTrials,
    this.estPercentile,
    this.simFuzzPct,
    this.simulationStrategy,
)

fun SimulationControlJson.import() = SimulationControl(
    this.nsimEst,
    this.estPercentile,
    this.simFuzzPct,
    this.simulationStrategy,
)

/*
data class ContestSampleControl(
    //// checkContestsCorrectlyFormed: preAuditStatus
    val minRecountMargin: Double = 0.005, // do not audit contests less than this recount margin
    val minMargin: Double = 0.0, // do not audit contests less than this margin TODO really it should be noerror for clca?

    //// consistentSampling: contestRound.status, depends on having estimation
    val maxSamplePct: Double = 0.0, // do not audit contests with (estimated nmvrs / Npop) greater than this
    val contestSampleCutoff: Int? = 1000, // max number of cvrs for any one contest, set to null to use all
    val auditSampleCutoff: Int? = 10000, // max number of cvrs in the audit, set to null to use all

    // soft parameters
    val other: Map<String, String> = emptyMap(),    // soft parameters to ease migration
    // val removeMaxContests: Int? = null, // remove top n estimated nmvrs contests, for plotting CaseStudiesRemoveNmax
) */


@Serializable
data class ContestSampleControlJson(
    val minRecountMargin: Double,
    val minMargin: Double,

    val maxSamplePct: Double,
    val contestSampleCutoff: Int?,
    val auditSampleCutoff: Int?,
    // val removeCutoffContests: Boolean,
)

fun ContestSampleControl.publishJson() = ContestSampleControlJson(
    this.minRecountMargin,
    this.minMargin,
    this.maxSamplePct,
    this.contestSampleCutoff,
    this.auditSampleCutoff,
    // this.removeCutoffContests,
)

fun ContestSampleControlJson.import() =  ContestSampleControl(
        this.minRecountMargin,
        this.minMargin,
        this.maxSamplePct,
        this.contestSampleCutoff,
        this.auditSampleCutoff,
        // this.removeCutoffContests,
    )


@Serializable
data class PollingConfigJson(
    val d: Int,
)

fun PollingConfig.publishJson() = PollingConfigJson(this.d)
fun PollingConfigJson.import() = PollingConfig(this.d)

// enum class ClcaStrategyType { generalAdaptive, generalAdaptive2}
//data class ClcaConfig(
//    val strategy: ClcaStrategyType = ClcaStrategyType.generalAdaptive2,
//    val fuzzMvrs: Double? = null, // used by PersistedMvrManagerTest to fuzz mvrs when persistedWorkflowMode=testSimulate
//    val d: Int = 100,  // shrinkTrunc weight for error rates
//    val maxLoss: Double = 0.90,  // max loss on any one bet, 0 < maxLoss < 1
//    val cvrsContainUndervotes: Boolean = true,
//    val apriori: TausRates = TausRates(emptyMap()),
//)
@Serializable
data class ClcaConfigJson(
    val strategy: ClcaStrategyType,
    val fuzzPct: Double?,
    val d: Int,
    val maxLoss: Double?,
    val apriori:  Map<String, Double>?,
)

fun ClcaConfig.publishJson() = ClcaConfigJson(
    this.strategy,
    this.fuzzMvrs,
    this.d,
    this.maxLoss,
    this.apriori.rates,
)

fun ClcaConfigJson.import() = ClcaConfig(
    this.strategy,
    this.fuzzPct,
    this.d,
    this.maxLoss ?: 0.90,
    apriori=TausRates(this.apriori ?: emptyMap()),
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