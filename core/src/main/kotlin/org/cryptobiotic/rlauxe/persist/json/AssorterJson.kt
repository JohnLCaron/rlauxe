package org.cryptobiotic.rlauxe.persist.json

import kotlinx.serialization.Serializable
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dhondt.DHondtAssorter
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.irv.RaireAssorter
import org.cryptobiotic.rlauxe.util.margin2mean

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
    val assorter: AssorterIFJson, // replicating the passorter
    val poolAverages: AssortAvgsInPoolsJson?, // consider putting these in another file ??
    val oaAssortRates: OneAuditAssortValueRatesJson?, // TODO may be very large, perhaps rehydrate from cardPool.csv ??
    val hasStyle: Boolean = true,
)

fun ClcaAssorter.publishJson() : ClcaAssorterJson {
    return if (this is OneAuditClcaAssorter) {
        ClcaAssorterJson(
            "ClcaAssorterOneAudit",
            this.assorter.publishJson(),
            poolAverages.publishJson(),
            oaAssortRates.publishJson(),
            this.hasStyle,
        )

    } else {
        ClcaAssorterJson(
            "ClcaAssorter",
            this.assorter.publishJson(),
            null,
            null,
            this.hasStyle,
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

        "ClcaAssorterOneAudit" -> {
            val oaClcaAssorter = OneAuditClcaAssorter(
                info,
                this.assorter.import(info),
                poolAverages!!.import(),
                // this.hasStyle,
            )
            oaClcaAssorter.oaAssortRates = this.oaAssortRates!!.import()
            oaClcaAssorter
        }
        else -> throw RuntimeException("unknown class name ${this.className}")
    }
}

// data class OneAuditAssortValueRates(val rates: Map<Double, Double>, val totalInPools: Int)
@Serializable
data class OneAuditAssortValueRatesJson(
    val rates: Map<Double, Double>,
    val totalInPools: Int,
)

fun OneAuditAssortValueRates.publishJson() = OneAuditAssortValueRatesJson(
    this.rates,
    this.ncardsInPools,
)

fun OneAuditAssortValueRatesJson.import() = OneAuditAssortValueRates(
    this.rates,
    this.totalInPools,
)


@Serializable
data class AssorterIFJson(
    val className: String,
    val reportedMargin: Double,
    val dilutedMargin: Double,
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
                reportedMargin = this.reportedMargin(),
                dilutedMargin = this.dilutedMargin(),
                this.winner,
                this.loser,
            )
        is RaireAssorter ->
            AssorterIFJson(
                "RaireAssorter",
                reportedMargin = this.reportedMargin(),
                dilutedMargin = this.dilutedMargin(),
                this.rassertion.winnerId,
                this.rassertion.loserId,
                rassertion = this.rassertion.publishJson(),
            )
        is DHondtAssorter ->
            AssorterIFJson(
                "DHondtAssorter",
                reportedMargin = this.reportedMargin(),
                dilutedMargin = this.dilutedMargin(),
                this.winner,
                this.loser,
                lastSeatWon = this.lastSeatWon,
                firstSeatLost = this.firstSeatLost,
            )
        is AboveThreshold ->
            AssorterIFJson(
                "AboveThreshold",
                reportedMargin = this.reportedMargin(),
                dilutedMargin = this.dilutedMargin(),
                winner = this.candId,
                minFraction = this.t,
            )
        is BelowThreshold ->
            AssorterIFJson(
                "UnderThreshold",
                reportedMargin = this.reportedMargin(),
                dilutedMargin = this.dilutedMargin(),
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
            .setMargins(this.reportedMargin, this.dilutedMargin)

        "RaireAssorter" ->
            // data class RaireAssorter(val info: ContestInfo, val rassertion: RaireAssertion): AssorterIF {
            RaireAssorter(
                info,
                this.rassertion!!.import())
            .setMeans(margin2mean(this.reportedMargin), margin2mean(this.dilutedMargin))

        "DHondtAssorterIF",
        "DHondtAssorter" ->
            DHondtAssorter(
                info,
                this.winner,
                this.loser!!,
                lastSeatWon = this.lastSeatWon!!,
                firstSeatLost = this.firstSeatLost!!)
           .setMeans(margin2mean(this.reportedMargin), margin2mean(this.dilutedMargin))

        "AboveThreshold" ->
            AboveThreshold(
                info,
                this.winner,
                this.minFraction!!)
            .setMeans(margin2mean(this.reportedMargin), margin2mean(this.dilutedMargin))

        "UnderThreshold" ->
            BelowThreshold(
                info,
                this.winner,
                this.minFraction!!)
            .setMeans(margin2mean(this.reportedMargin), margin2mean(this.dilutedMargin))

        else -> throw RuntimeException("unknown class name ${this.className}")
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
