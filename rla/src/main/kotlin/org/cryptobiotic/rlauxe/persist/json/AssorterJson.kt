package org.cryptobiotic.rlauxe.persist.json

import kotlinx.serialization.Serializable
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.raire.RaireAssorter

//open class ClcaAssorter(
//    val info: ContestInfo,
//    val assorter: AssorterIF,   // A
//    val avgCvrAssortValue: Double,    // Ā(c) = average CVR assort value
//    val hasStyle: Boolean = true,
//    val check: Boolean = true, // TODO get rid of
//)

// class OAClcaAssorter(
//    val contestOA: OneAuditContest,
//    assorter: AssorterIF,   // A(mvr)
//    avgCvrAssortValue: Double,    // Ā(c) = average CVR assorter value
//) : ClcaAssorter(contestOA.info, assorter, avgCvrAssortValue)

@Serializable
data class ClcaAssorterJson(
    val className: String,
    val contestOA: OAContestJson?, // duplicate storage, argghh
    val assorter: AssorterIFJson,
    val avgCvrAssortValue: Double,
    val hasStyle: Boolean,
)

fun ClcaAssorter.publishJson() : ClcaAssorterJson {
    return if (this is OneAuditAssorter) {
        ClcaAssorterJson(
            "OAClcaAssorter",
            this.contestOA.publishOAJson(),
            this.assorter.publishJson(),
            this.avgCvrAssortValue,
            true, // TODO
        )

    } else {
        ClcaAssorterJson(
            "ClcaAssorter",
            null,
            this.assorter.publishJson(),
            this.avgCvrAssortValue,
            this.hasStyle,
        )
    }
}

fun ClcaAssorterJson.import(info: ContestInfo): ClcaAssorter {
    return when (this.className) {
        "ClcaAssorter" ->
            return ClcaAssorter(
                info,
                this.assorter.import(info),
                this.avgCvrAssortValue,
                this.hasStyle,
            )
        "OAClcaAssorter" ->
            OneAuditAssorter(
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
