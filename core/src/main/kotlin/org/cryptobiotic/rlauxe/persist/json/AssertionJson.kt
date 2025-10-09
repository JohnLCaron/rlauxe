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

