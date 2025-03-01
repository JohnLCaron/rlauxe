package org.cryptobiotic.rlauxe.persist.json

import kotlinx.serialization.Serializable
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.enumValueOf

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
    result.estRoundResults.addAll( assertion.estRoundResults)
    result.roundResults.addAll( assertion.roundResults)
    result.status = assertion.status
    result.round = assertion.round
    return result
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
    val contest: ContestIFJson,
    val assorter: AssorterIFJson,
    val estSampleSize: Int,   // estimated sample size
    val estRoundResults: List<EstimationRoundResultJson>,   // first sample when pvalue < riskLimit
    val roundResults: List<AuditRoundResultJson>,   // first sample when pvalue < riskLimit
    val status: String, // testH0 status
    val round: Int,
)

fun Assertion.publishJson() = AssertionJson(
        this.contest.publishJson(),
        this.assorter.publishJson(),
        this.estSampleSize,
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
    result.roundResults.addAll(this.roundResults.map { it.import() })
    result.estRoundResults.addAll(this.estRoundResults.map { it.import() })
    result.status = status
    result.round = this.round
    return result
}
