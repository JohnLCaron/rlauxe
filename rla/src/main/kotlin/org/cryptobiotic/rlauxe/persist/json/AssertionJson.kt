package org.cryptobiotic.rlauxe.persist.json

import kotlinx.serialization.Serializable
import org.cryptobiotic.rlauxe.core.*

// open class ClcaAssertion(
//    contest: ContestIF,
//    val cassorter: ClcaAssorterIF,
//): Assertion(contest, cassorter.assorter()) {

@Serializable
data class ClcaAssertionJson(
    val cassorter: ClcaAssorterJson,
    val assertion: AssertionJson,
)

fun ClcaAssertion.publishJson() = ClcaAssertionJson(
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
    return result
}

// open class Assertion(
//    val contest: ContestIF,
//    val assorter: AssorterIF,
//)

@Serializable
data class AssertionJson(
    val contest: ContestIFJson,
    val assorter: AssorterIFJson,
)

fun Assertion.publishJson() = AssertionJson(
        this.contest.publishJson(),
        this.assorter.publishJson(),
    )

fun AssertionJson.import() : Assertion {
    val result = Assertion(
        this.contest.import(),
        this.assorter.import(),
    )
    return result
}
