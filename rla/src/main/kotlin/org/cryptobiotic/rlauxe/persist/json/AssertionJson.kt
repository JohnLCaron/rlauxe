package org.cryptobiotic.rlauxe.persist.json

import kotlinx.serialization.Serializable
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.safeEnumValueOf

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
    val roundResults: List<AuditRoundResultJson>,   // first sample when pvalue < riskLimit
    val status: String, // testH0 status
    val round: Int,
)

fun Assertion.publishJson() = AssertionJson(
        (this.contest as Contest).publishJson(),
        this.assorter.publishJson(),
        this.estSampleSize,
        this.roundResults.map { it.publishJson() },
        this.status.name,
        this.round,
    )

fun AssertionJson.import() : Assertion {
    val status = safeEnumValueOf(this.status) ?: TestH0Status.InProgress
    val result = Assertion(
        this.contest.import(),
        this.assorter.import(),
    )
    result.estSampleSize = this.estSampleSize
    result.roundResults.addAll(this.roundResults.map { it.import() })
    result.status = status
    result.round = this.round
    return result
}

// data class AuditRoundResult( val roundIdx: Int,
//                        val estSampleSize: Int,   // estimated sample size
//                        val samplesNeeded: Int,   // first sample when pvalue < riskLimit
//                        val samplesUsed: Int,     // sample count when testH0 terminates
//                        val pvalue: Double,       // last pvalue when testH0 terminates
//                        val status: TestH0Status, // testH0 status
//                        val errorRates: ErrorRates? = null, // measured error rates (clca only)
//    )

@Serializable
data class AuditRoundResultJson(
    val desc: String,
    val roundIdx: Int,
    val estSampleSize: Int,   // estimated sample size
    val samplesNeeded: Int,   // first sample when pvalue < riskLimit
    val samplesUsed: Int,     // sample count when testH0 terminates, usually maxSamples
    val pvalue: Double,       // last pvalue when testH0 terminates
    val status: String, // testH0 status
    val errorRates: List<Double>?,
)

fun AuditRoundResult.publishJson() = AuditRoundResultJson(
        this.toString(),
        this.roundIdx,
        this.estSampleSize,
        this.samplesNeeded,
        this.samplesUsed,
        this.pvalue,
        this.status.name,
        this.errorRates?.toList(),
    )

fun AuditRoundResultJson.import() : AuditRoundResult {
    val status = safeEnumValueOf(this.status) ?: TestH0Status.InProgress
    return AuditRoundResult(
        this.roundIdx,
        this.estSampleSize,
        this.samplesNeeded,
        this.samplesUsed,
        this.pvalue,
        status,
        if (this.errorRates != null) ErrorRates.fromList(this.errorRates) else null,
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
