package org.cryptobiotic.rlauxe.persist.json

import kotlinx.serialization.Serializable
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dhondt.DHondtAssorter
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
    val assorter: AssorterIFJson,
    val hasStyle: Boolean,
    val dilutedMargin: Double,
    val poolAverages: AssortAvgsInPoolsJson?,
)

fun ClcaAssorter.publishJson() : ClcaAssorterJson {
    return if (this is ClcaAssorterOneAudit) {
        ClcaAssorterJson(
            "OAClcaAssorter",
            this.assorter.publishJson(),
            this.hasCompleteCvrs,
            this.dilutedMargin,
            poolAverages.publishJson()
        )

    } else {
        ClcaAssorterJson(
            "ClcaAssorter",
            this.assorter.publishJson(),
            this.hasCompleteCvrs,
            this.dilutedMargin,
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
                this.dilutedMargin,
            )

        "OAClcaAssorter" ->
            ClcaAssorterOneAudit(
                info,
                this.assorter.import(info),
                this.hasStyle,
                this.dilutedMargin,
                poolAverages!!.import()
            )

        else -> throw RuntimeException()
    }
}

@Serializable
data class AssorterIFJson(
    val className: String,
    val reportedMean: Double,
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
                reportedMean = this.dilutedMean(),
                this.winner,
                this.loser,
            )
        is RaireAssorter ->
            AssorterIFJson(
                "RaireAssorter",
                reportedMean = this.dilutedMean(),
                this.rassertion.winnerId,
                this.rassertion.loserId,
                rassertion = this.rassertion.publishJson(),
            )
        is DHondtAssorter ->
            AssorterIFJson(
                "DHondtAssorter",
                reportedMean = this.dilutedMean(),
                this.winner,
                this.loser,
                lastSeatWon = this.lastSeatWon,
                firstSeatLost = this.firstSeatLost,
            )
        is AboveThreshold ->
            AssorterIFJson(
                "AboveThreshold",
                reportedMean = this.dilutedMean(),
                winner = this.winner,
                minFraction = this.t,
            )
        is BelowThreshold ->
            AssorterIFJson(
                "UnderThreshold",
                reportedMean = this.dilutedMean(),
                winner = this.candId,
                minFraction = this.t,
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
                this.loser!!)
            .setDilutedMean(this.reportedMean)

        "RaireAssorter" ->
            // data class RaireAssorter(val info: ContestInfo, val rassertion: RaireAssertion): AssorterIF {
            RaireAssorter(
                info,
                this.rassertion!!.import())
            .setDilutedMean(this.reportedMean)

        "DHondtAssorterIF",
        "DHondtAssorter" ->
            DHondtAssorter(
                info,
                this.winner,
                this.loser!!,
                lastSeatWon = this.lastSeatWon!!,
                firstSeatLost = this.firstSeatLost!!)
            .setDilutedMean(this.reportedMean)

        "AboveThreshold" ->
            AboveThreshold(
                info,
                this.winner,
                this.minFraction!!)
             .setDilutedMean(this.reportedMean)

        "UnderThreshold" ->
            BelowThreshold(
                info,
                this.winner,
                this.minFraction!!)
            .setDilutedMean(this.reportedMean)

        else -> throw RuntimeException()
    }
}

@Serializable
data class AssortAvgsInPoolsJson(
    val assortAverage: Map<Int, Double>,
)

fun AssortAvgsInPools.publishJson() = AssortAvgsInPoolsJson(
    assortAverage,
)

fun AssortAvgsInPoolsJson.import() = AssortAvgsInPools(
    assortAverage,
)
