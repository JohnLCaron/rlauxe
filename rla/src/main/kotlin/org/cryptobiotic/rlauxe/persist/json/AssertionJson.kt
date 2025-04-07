package org.cryptobiotic.rlauxe.persist.json

import kotlinx.serialization.Serializable
import org.cryptobiotic.rlauxe.core.*

@Serializable
data class AssertionIFJson(
    val className: String,
    val cassorter: ClcaAssorterJson?,
    val assorter: AssorterIFJson?,
)

fun Assertion.publishIFJson(): AssertionIFJson {
    return when (this) {
        is ClcaAssertion ->
            AssertionIFJson(
                "ClcaAssertion",
                this.cassorter.publishJson(),
                null
            )
        else ->
            AssertionIFJson(
                this.javaClass.name,
                null,
                this.assorter.publishJson(),
            )
    }
}

fun AssertionIFJson.import(info: ContestInfo): Assertion {
    return when (this.className) {
        "ClcaAssertion" ->
            ClcaAssertion(
                info,
                this.cassorter!!.import(info),
            )

        else ->
            Assertion(
                info,
                this.assorter!!.import(info),
            )
    }
}

@Serializable
data class ClcaAssertionJson(
    val cassorter: ClcaAssorterJson,
)

fun ClcaAssertion.publishJson() = ClcaAssertionJson(
        this.cassorter.publishJson(),
    )

fun ClcaAssertionJson.import(info: ContestInfo): ClcaAssertion {
   val result = ClcaAssertion(
       info,
       this.cassorter.import(info),
    )
    return result
}
@Serializable
data class AssertionJson(
    //val contest: ContestIFJson,
    val assorter: AssorterIFJson,
)

fun Assertion.publishJson() = AssertionJson(
    //this.contest.publishJson(),
    this.assorter.publishJson(),
)

fun AssertionJson.import(info: ContestInfo) : Assertion {
    val result = Assertion(
        info,
        this.assorter.import(info),
    )
    return result
}
