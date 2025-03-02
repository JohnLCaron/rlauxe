package org.cryptobiotic.rlauxe.persist.json

import kotlinx.serialization.Serializable
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.raire.RaireAssorter
import org.cryptobiotic.rlauxe.raire.RaireAssertionJson
import org.cryptobiotic.rlauxe.raire.import
import org.cryptobiotic.rlauxe.raire.publishJson

// data class ClcaAssorter(
//    val contest: ContestInfo,
//    val assorter: AssorterFunction,   // A
//    val avgCvrAssortValue: Double,    // Ā(c) = average CVR assort value = assorter.reportedMargin()? always?
//    val hasStyle: Boolean = true

// think of it as serializing a ClcaAssorterIF?
@Serializable
data class ClcaAssorterJson(
    val info: ContestInfoJson,
    val assorter: AssorterIFJson,
    val avgCvrAssortValue: Double,
    val hasStyle: Boolean,
)

// val contest: ContestIF, val winner: Int, val loser: Int, val reportedMargin: Double
// TODO add OneAuditClcaAssorter
fun ClcaAssorter.publishJson() : ClcaAssorterJson {
    return ClcaAssorterJson(
            this.info.publishJson(),
            this.assorter.publishJson(),
            this.avgCvrAssortValue,
            this.hasStyle,
        )
}

fun ClcaAssorterJson.import(): ClcaAssorter {
    return ClcaAssorter(
        this.info.import(),
        this.assorter.import(),
        this.avgCvrAssortValue,
        this.hasStyle,
    )
}

@Serializable
data class AssorterIFJson(
    val className: String,
    val info: ContestInfoJson,
    val reportedMargin: Double,
    val winner: Int,   // estimated sample size
    val loser: Int? = null,   // estimated sample size
    val minFraction: Double? = null,
    val rassertion: RaireAssertionJson? = null,
)

fun AssorterIF.publishJson() : AssorterIFJson {
    return when (this) {
        is PluralityAssorter ->
            AssorterIFJson(
                "PluralityAssorter",
                this.info.publishJson(),
                this.reportedMargin,
                this.winner,
                this.loser,
            )
        is SuperMajorityAssorter ->
            AssorterIFJson(
                "SuperMajorityAssorter",
                this.info.publishJson(),
                this.reportedMargin,
                this.winner,
                minFraction = this.minFraction,
            )
        is RaireAssorter ->
            AssorterIFJson(
                "RaireAssorter",
                this.info.publishJson(),
                this.reportedMargin,
                this.rassertion.winner,
                this.rassertion.loser,
                rassertion = this.rassertion.publishJson(),
            )
        else -> throw RuntimeException("unknown assorter type ${this.javaClass.simpleName} = $this")
    }
}

fun AssorterIFJson.import(): AssorterIF {
    return when (this.className) {
        "PluralityAssorter" ->
            PluralityAssorter(
                this.info.import(),
                this.winner,
                this.loser!!,
                this.reportedMargin,
            )
        "SuperMajorityAssorter" ->
            SuperMajorityAssorter(
                this.info.import(),
                this.winner,
                this.minFraction!!,
                this.reportedMargin,
            )
        "RaireAssorter" ->
            // data class RaireAssorter(val info: ContestInfo, val rassertion: RaireAssertion): AssorterIF {
            RaireAssorter(
                this.info.import(),
                this.rassertion!!.import(),
                this.reportedMargin,
            )
        else -> throw RuntimeException()
    }
}
