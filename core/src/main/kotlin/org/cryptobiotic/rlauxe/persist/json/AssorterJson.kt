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
                reportedMean = this.reportedMean(),
                this.winner,
                this.loser,
            )
        is SuperMajorityAssorter ->
            AssorterIFJson(
                "SuperMajorityAssorter",
                reportedMean = this.reportedMean(),
                this.candId,
                minFraction = this.minFraction,
            )
        is RaireAssorter ->
            AssorterIFJson(
                "RaireAssorter",
                reportedMean = this.reportedMean(),
                this.rassertion.winnerId,
                this.rassertion.loserId,
                rassertion = this.rassertion.publishJson(),
            )
        is DHondtAssorter ->
            AssorterIFJson(
                "DHondtAssorter",
                reportedMean = this.reportedMean(),
                this.winner,
                this.loser,
                lastSeatWon = this.lastSeatWon,
                firstSeatLost = this.firstSeatLost,
            )
        is OverThreshold ->
            AssorterIFJson(
                "OverThreshold",
                reportedMean = this.reportedMean(),
                winner = this.winner,
                minFraction = this.t,
            )
        is UnderThreshold ->
            AssorterIFJson(
                "UnderThreshold",
                reportedMean = this.reportedMean(),
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
            .setReportedMean(this.reportedMean)

        "SuperMajorityAssorter" ->
            SuperMajorityAssorter(
                info,
                this.winner,
                this.minFraction!!)
            .setReportedMean(this.reportedMean)

        "RaireAssorter" ->
            // data class RaireAssorter(val info: ContestInfo, val rassertion: RaireAssertion): AssorterIF {
            RaireAssorter(
                info,
                this.rassertion!!.import())
            .setReportedMean(this.reportedMean)

        "DHondtAssorterIF",
        "DHondtAssorter" ->
            DHondtAssorter(
                info,
                this.winner,
                this.loser!!,
                lastSeatWon = this.lastSeatWon!!,
                firstSeatLost = this.firstSeatLost!!)
            .setReportedMean(this.reportedMean)

        "OverThreshold" ->
            OverThreshold(
                info,
                this.winner,
                this.minFraction!!)
                .setReportedMean(this.reportedMean)

        "UnderThreshold" ->
            UnderThreshold(
                info,
                this.winner,
                this.minFraction!!)
            .setReportedMean(this.reportedMean)

        else -> throw RuntimeException()
    }
}

@Serializable
data class AssortAvgsInPoolsJson(
    val contest: Int?, // TODO remove
    val assortAverage: Map<Int, Double>,
)

fun AssortAvgsInPools.publishJson() = AssortAvgsInPoolsJson(
    contest = null,
    assortAverage,
)

fun AssortAvgsInPoolsJson.import() = AssortAvgsInPools(
    assortAverage,
)
