package org.cryptobiotic.rlauxe.persist.json

import kotlinx.serialization.Serializable
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.enumValueOf
import org.cryptobiotic.rlauxe.workflow.AuditRoundResult
import org.cryptobiotic.rlauxe.workflow.EstimationRoundResult

// open class ClcaAssertion(
//    contest: ContestIF,
//    val cassorter: ClcaAssorterIF,
//): Assertion(contest, cassorter.assorter()) {

@Serializable
data class ClcaAssertionJson(
    val cassorter: ClcaAssorterJson,
    val assertion: AssertionJson,
)

fun ClcaAssertion.publishJson() =  ClcaAssertionJson(
        (this.cassorter as ClcaAssorter).publishJson(),
        (this as Assertion).publishJson(),
    )

// TODO make inheritence less clumsy
fun ClcaAssertionJson.import(): ClcaAssertion {
   val assertion = this.assertion.import()
   val result = ClcaAssertion(
       assertion.contest,
       this.cassorter.import(),
    )
    result.estSampleSize = assertion.estSampleSize
    result.estNewSamples = assertion.estNewSamples
    result.estRoundResults.addAll( assertion.estRoundResults)
    result.roundResults.addAll( assertion.roundResults)
    result.status = assertion.status
    result.round = assertion.round
    return result
}

// data class ClcaAssorter(
//    val contest: ContestIF,
//    val assorter: AssorterFunction,   // A
//    val avgCvrAssortValue: Double,    // Ā(c) = average CVR assort value = assorter.reportedMargin()? always?
//    val hasStyle: Boolean = true

@Serializable
data class ClcaAssorterJson(
    val contest: ContestJson,
    val assorter: AssorterJson,
    val avgCvrAssortValue: Double,
    val hasStyle: Boolean,
)

// val contest: ContestIF, val winner: Int, val loser: Int, val reportedMargin: Double
fun ClcaAssorter.publishJson() : ClcaAssorterJson {
    return ClcaAssorterJson(
        (this.contest as Contest).publishJson(),
        this.assorter.publishJson(),
        this.avgCvrAssortValue,
        this.hasStyle,
    )
}

fun ClcaAssorterJson.import(): ClcaAssorter {
    return ClcaAssorter(
            this.contest.import(),
            this.assorter.import(),
            this.avgCvrAssortValue,
            this.hasStyle,
        )
}

// open class Assertion(
//    val contest: ContestIF,
//    val assorter: AssorterFunction,
//) {
//    val winner = assorter.winner()
//    val loser = assorter.loser()
//
//    // these values are set during estimateSampleSizes()
//    var estSampleSize = 0   // estimated sample size for current round
//    var estNewSamples = 0   // estimated new sample size for current round
//
//    // these values are set during runAudit()
//    val roundResults = mutableListOf<AuditRoundResult>()
//    var status = TestH0Status.InProgress
//    // var proved = false
//    var round = 0           // round when set to proved or disproved
//
//    override fun toString() = "'${contest.info.name}' (${contest.info.id}) ${assorter.desc()} margin=${df(assorter.reportedMargin())}"
//}

@Serializable
data class AssertionJson(
    val contest: ContestJson,
    val assorter: AssorterJson,
    val estSampleSize: Int,   // estimated sample size
    val estNewSamples: Int,   // estimated sample size
    val estRoundResults: List<EstimationRoundResultJson>,   // first sample when pvalue < riskLimit
    val roundResults: List<AuditRoundResultJson>,   // first sample when pvalue < riskLimit
    val status: String, // testH0 status
    val round: Int,
)

fun Assertion.publishJson() = AssertionJson(
        (this.contest as Contest).publishJson(),
        this.assorter.publishJson(),
        this.estSampleSize,
        this.estNewSamples,
        this.estRoundResults.map { it.publishJson() },
        this.roundResults.map { it.publishJson() },
        this.status.name,
        this.round,
    )

fun AssertionJson.import() : Assertion {
    val status = enumValueOf(this.status, TestH0Status.entries) ?: TestH0Status.InProgress
    val result = Assertion(
        this.contest.import(),
        this.assorter.import(),
    )
    result.estSampleSize = this.estSampleSize
    result.estNewSamples = this.estNewSamples
    result.roundResults.addAll(this.roundResults.map { it.import() })
    result.estRoundResults.addAll(this.estRoundResults.map { it.import() })
    result.status = status
    result.round = this.round
    return result
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
    val status = enumValueOf(this.status, TestH0Status.entries) ?: TestH0Status.InProgress
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

@Serializable
data class AssorterJson(
    val isPlurality: Boolean = true,
    val contest: ContestJson,
    val winner: Int,   // estimated sample size
    val loser: Int?,   // estimated sample size
    val minFraction: Double?,
    val reportedMargin: Double,
)

fun AssorterFunction.publishJson() : AssorterJson {
    return if (this is PluralityAssorter) publishPluralityJson(this)
            else publishSuperJson(this as SuperMajorityAssorter)
}

// val contest: ContestIF, val winner: Int, val loser: Int, val reportedMargin: Double
fun publishPluralityJson(assorter: PluralityAssorter) : AssorterJson {
    return AssorterJson(
        true,
        (assorter.contest as Contest).publishJson(),
        assorter.winner,
        assorter.loser,
        null,
        reportedMargin = assorter.reportedMargin,
    )
}

// data class SuperMajorityAssorter(val contest: ContestIF, val winner: Int, val minFraction: Double, val reportedMargin: Double): AssorterFunction {
fun publishSuperJson(assorter: SuperMajorityAssorter) : AssorterJson {
    return AssorterJson(
        false,
        (assorter.contest as Contest).publishJson(),
        assorter.winner,
        null,
        assorter.minFraction,
        reportedMargin = assorter.reportedMargin,
    )
}

fun AssorterJson.import(): AssorterFunction {
    return if (this.isPlurality)
        PluralityAssorter(
            this.contest.import(),
            this.winner,
            this.loser!!,
            this.reportedMargin,
        )
    else
        SuperMajorityAssorter(
            this.contest.import(),
            this.winner,
            this.minFraction!!,
            this.reportedMargin,
        )
}
