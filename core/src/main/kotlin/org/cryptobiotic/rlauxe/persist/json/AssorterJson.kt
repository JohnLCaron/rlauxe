package org.cryptobiotic.rlauxe.persist.json

import kotlinx.serialization.Serializable
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dhondt.DHondtAssorterIF
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.raire.RaireAssorter

// open class ClcaAssorter(
//    val info: ContestInfo,
//    val assorter: AssorterIF,   // A
//    val assortAverageFromCvrs: Double?,    // Ā(c) = average assort value measured from CVRs
//    val hasStyle: Boolean = true,
//    val check: Boolean = true,
//)

// class OneAuditClcaAssorter(
//    info: ContestInfo,
//    assorter: AssorterIF,   // A(mvr) Use this assorter for the CVRs: plurality or IRV
//    hasStyle: Boolean = true,
//    val poolAverages: AssortAvgsInPools,
//) : ClcaAssorter(info, assorter, null, hasStyle = hasStyle) {

@Serializable
data class ClcaAssorterJson(
    val className: String,
    val assorter: AssorterIFJson, // TODO duplicate
    val avgCvrAssortValue: Double?, // TODO remove
    val hasStyle: Boolean,
    val poolAverages: AssortAvgsInPoolsJson?,
)

fun ClcaAssorter.publishJson() : ClcaAssorterJson {
    return if (this is OneAuditClcaAssorter) {
        ClcaAssorterJson(
            "OAClcaAssorter",
            this.assorter.publishJson(),
            null,
            true,
            poolAverages.publishJson()
        )

    } else {
        ClcaAssorterJson(
            "ClcaAssorter",
            this.assorter.publishJson(),
            null,
            this.hasStyle,
            null,
        )
    }
}

fun ClcaAssorterJson.import(info: ContestInfo): ClcaAssorter {
    return when (this.className) {
        "ClcaAssorter" ->
            ClcaAssorter(
                info,
                this.assorter.import(info),
                this.hasStyle,
            )

        "OAClcaAssorter" ->
            OneAuditClcaAssorter(
                info,
                this.assorter.import(info),
                this.hasStyle,
                poolAverages!!.import()
            )

        else -> throw RuntimeException()
    }
}

@Serializable
data class AssorterIFJson(
    val className: String,
    val reportedMargin: Double,
    val winner: Int,
    val loser: Int? = null,
    val minFraction: Double? = null,
    val rassertion: RaireAssertionJson? = null,
    val lastSeatWon: Int? = null,
    val firstSeatLost: Int? = null,
)

fun AssorterIF.publishJson() : AssorterIFJson {
    return when (this) {
        is PluralityAssorter ->
            AssorterIFJson(
                "PluralityAssorter",
                this.reportedMargin,
                this.winner,
                this.loser,
            )
        is SuperMajorityAssorter ->
            AssorterIFJson(
                "SuperMajorityAssorter",
                this.reportedMargin,
                this.winner,
                minFraction = this.minFraction,
            )
        is RaireAssorter ->
            AssorterIFJson(
                "RaireAssorter",
                this.reportedMargin,
                this.rassertion.winnerId,
                this.rassertion.loserId,
                rassertion = this.rassertion.publishJson(),
            )
        is DHondtAssorterIF ->
            AssorterIFJson(
                "DHondtAssorterIF",
                this.reportedMean(), // TODO bogus
                this.winner,
                this.loser,
                lastSeatWon = this.lastSeatWon,
                firstSeatLost = this.firstSeatLost,
            )
        else -> throw RuntimeException("unknown assorter type ${this.javaClass.simpleName} = $this")
    }
}

fun AssorterIFJson.import(info: ContestInfo): AssorterIF {
    return when (this.className) {
        "PluralityAssorter" ->
            PluralityAssorter(
                info,
                this.winner,
                this.loser!!,
                this.reportedMargin,
            )
        "SuperMajorityAssorter" ->
            SuperMajorityAssorter(
                info,
                this.winner,
                this.minFraction!!,
                this.reportedMargin,
            )
        "RaireAssorter" ->
            // data class RaireAssorter(val info: ContestInfo, val rassertion: RaireAssertion): AssorterIF {
            RaireAssorter(
                info,
                this.rassertion!!.import(),
                this.reportedMargin,
            )
        "DHondtAssorterIF" ->
            // DHondtAssorterIF(val info: ContestInfo, val winner: Int, val loser: Int, val firstSeatLost: Int, val lastSeatWon: Int, val margin: Double): AssorterIF  {
            DHondtAssorterIF(
                info,
                this.winner,
                this.loser!!,
                lastSeatWon = this.lastSeatWon!!,
                firstSeatLost = this.firstSeatLost!!,
                this.reportedMargin,
            )
        else -> throw RuntimeException()
    }
}
