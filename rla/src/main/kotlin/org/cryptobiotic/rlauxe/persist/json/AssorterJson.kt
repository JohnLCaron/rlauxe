package org.cryptobiotic.rlauxe.persist.json

import kotlinx.serialization.Serializable
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.raire.RaireAssorter
import org.cryptobiotic.rlauxe.raire.RaireAssertionJson
import org.cryptobiotic.rlauxe.raire.import
import org.cryptobiotic.rlauxe.raire.publishJson

// data class ClcaAssorter(
//    val contest: ContestInfo,
//    val assorter: AssorterFunction,   // A
//    val avgCvrAssortValue: Double,    // Ā(c) = average CVR assort value = assorter.reportedMargin()? always?
//    val hasStyle: Boolean = true

// data class OAClcaAssorter(
//    val contestOA: OneAuditContest,
//    val assorter: AssorterIF,   // A(mvr)
//    val avgCvrAssortValue: Double,    // Ā(c) = average CVR assorter value TODO why?
//)

@Serializable
data class ClcaAssorterIFJson(
    val className: String,
    val contestOA: OAContestJson?, // duplicate storage, argghh
    val assorter: AssorterIFJson,
    val avgCvrAssortValue: Double,
    val hasStyle: Boolean,
)

fun ClcaAssorterIF.publishJson() : ClcaAssorterIFJson {
    return when (this) {
        is ClcaAssorter ->
            ClcaAssorterIFJson(
                "ClcaAssorter",
                null,
                this.assorter.publishJson(),
                this.avgCvrAssortValue,
                this.hasStyle,
            )

        is OAClcaAssorter ->
            ClcaAssorterIFJson(
                "OAClcaAssorter",
                this.contestOA.publishOAJson(),
                this.assorter.publishJson(),
                this.avgCvrAssortValue,
                true, // TODO
            )

        else -> throw RuntimeException("unknown assorter type ${this.javaClass.simpleName} = $this")
    }
}

fun ClcaAssorterIFJson.import(info: ContestInfo): ClcaAssorterIF {
    return when (this.className) {
        "ClcaAssorter" ->
            return ClcaAssorter(
                info,
                this.assorter.import(info),
                this.avgCvrAssortValue,
                this.hasStyle,
            )
        "OAClcaAssorter" ->
            OAClcaAssorter(
                this.contestOA!!.import(info),
                this.assorter.import(info),
                this.avgCvrAssortValue,
            )
        else -> throw RuntimeException()
    }
}

@Serializable
data class AssorterIFJson(
    val className: String,
    // val info: ContestInfoJson,
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
                // this.info.publishJson(),
                this.reportedMargin,
                this.winner,
                this.loser,
            )
        is SuperMajorityAssorter ->
            AssorterIFJson(
                "SuperMajorityAssorter",
                // this.info.publishJson(),
                this.reportedMargin,
                this.winner,
                minFraction = this.minFraction,
            )
        is RaireAssorter ->
            AssorterIFJson(
                "RaireAssorter",
                // this.info.publishJson(),
                this.reportedMargin,
                this.rassertion.winnerId,
                this.rassertion.loserId,
                rassertion = this.rassertion.publishJson(),
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
        else -> throw RuntimeException()
    }
}
