package org.cryptobiotic.rlauxe.corlaCounty

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.auditcenter.BuildCorlaContests
import org.cryptobiotic.rlauxe.corlaInput.ColoradoInput
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.corlaInput.CorlaCountyCvrs
import org.cryptobiotic.rlauxe.corlaInput.ManifestCounts
import org.cryptobiotic.rlauxe.cvr.CorlaCvrConverter
import org.cryptobiotic.rlauxe.cvr.CorlaCvrsIF
import org.cryptobiotic.rlauxe.util.AuditableCardBuilder
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.subtractContestTabulations
import org.cryptobiotic.rlauxe.util.subtractContestTabulationsZ
import org.cryptobiotic.rlauxe.util.sumContestTabulationsFromVotes
import org.cryptobiotic.rlauxe.util.tabulateCards

class CheckCvrsAndManifest(
    val stateInput: ColoradoInput,
    val countyInput: CorlaCountyCvrs,
    val showMatch: Boolean = false,
    val showMissingVotes: Boolean = false,
    val showRedactedCvrs: Boolean = false,
    val compareMissingVotes: Boolean = false,
    startingPoolId: Int = 1,
    val variant: ElectionVariant = ElectionVariant(ElectionVariantEnum.OnePool)
) {
    val show = false
    val county = countyInput.countyName

    val infos: Map<Int, ContestInfo>
    val converter: CorlaCvrConverter
    val convertedCvrs: List<AuditableCard>
    val cvrStyles: List<StyleIF>

    val manifestCounts: ManifestCounts
    val convertedCvrTabs : Map<Int, ContestTabulation>

    val corlaCvrs: CorlaCvrsIF
    val minCards: Int

    init {
        println("---------------------------------------------------------------------------------------")
        println("County $county")
        val contestBuilder = BuildCorlaContests(stateInput)
        infos = contestBuilder.infos
        val infosByName = infos.mapKeys { it.value.name } //  are the cvr names compatible ?

        corlaCvrs = countyInput.readCorlaCvrs()
        val redaction = corlaCvrs.redaction()

        if (showRedactedCvrs) {
            println("Redacted Cvrs (${redaction.redactedRows().size})")
            redaction.redactedRows().forEach { println("  $it")}
            redaction.groups().forEach { println("  ${it.firstCsv}")}
            println()
        }

        val manifest = countyInput.readCountyManifest()
        manifestCounts = manifest.manifestCounts(corlaCvrs)

        converter = CorlaCvrConverter(countyInput.countyName, corlaCvrs, infosByName, stateInput, startingPoolId)
        cvrStyles = converter.cardStyles.values.toList()

        convertedCvrs = corlaCvrs.cvrs().map {
            converter.convertToCard(it) { cvrb:AuditableCardBuilder ->
                val manifestEntry = manifestCounts.match[cvrb.id]
                cvrb.location =
                    if (manifestEntry != null) "$county:${manifestEntry.location()}"
                    else {
                        // we have a cvr without a manifest entry
                        if (cvrb.location != null) "$county:${cvrb.location}"
                        else county
                    }
            }
        }
        convertedCvrTabs = tabulateCards(convertedCvrs.iterator(), infos)

        ////////////////////////////////////////////////////
        // can we use county cvr vote totals to calculate oneaudit subtotals?
        val countyTab = stateInput.countyTabsAllContests()[county]!!
        val convertedCountyTabs: Map<Int, ContestTabulation> = converter.convertToContestTabulation(countyTab)
        val diffz = subtractContestTabulationsZ(convertedCountyTabs, convertedCvrTabs)
        if (showMissingVotes || compareMissingVotes) {
            println("Missing Votes")
            val diff = subtractContestTabulations(convertedCountyTabs, convertedCvrTabs)
            showTabDiffs(diff)
            val minCards = diff.values.maxOf { it.nvotes() }
            println("minCards = $minCards")

            // compare to group accumulations
            if (compareMissingVotes) {
                val sumAccum = mutableMapOf<Int, ContestTabulation>()
                redaction.groups().forEach { group ->
                    group.contestVotes.forEach { (scontestId, votes) ->
                        val contestId = converter.convertContestId(scontestId)
                        sumAccum.sumContestTabulationsFromVotes(infos[contestId]!!, votes)
                    }
                }
                val diff2 = subtractContestTabulations(diff, sumAccum)
                println("Missing Votes - GroupAccum")
                showTabDiffs(diff2)
            }

        } else {
            val prezDiff = diffz[372]!!
            print("Prez votes missing = ")
            prezDiff.votes.forEach { (cand, vote) ->
                if (vote != 0) print("$cand: $vote; ")
            }
            println()
        }

        minCards = diffz.values.maxOf { it.nvotes() }
        println("minCardsZ = $minCards")

        if (showMatch) {
            val report = mutableListOf<String>()
            manifest.manifestCounts(corlaCvrs, report)
            report.forEach { println(it) }
            // compareCvrsAndManifests(countyInput, corlaCvrs)
        }
    }
}

fun showTabDiffs(diff: Map<Int, ContestTabulation>) {
    diff.toSortedMap().forEach{ (contestId, tab) ->
        if (tab.nvotes() != 0) {
            print("  Contest $contestId nvotes=${tab.nvotes()}:  ")
            tab.votes.forEach { (cand, vote) ->
                if (vote != 0) print("$cand: $vote; ")
            }
            println()
        }
    }
}

/*
///////////////////////////////////////////////////////////////
fun compareCvrsAndManifests(input: CorlaCountyCvrs, corlaCvrs: CorlaCvrsIF, showMissed: Boolean = true, showUnmatched: Boolean = false) {
    val allCvrs = corlaCvrs.cvrs() + corlaCvrs.redactedCvrs()

    val nCvrs = allCvrs.size
    println("${input.cvrsSource}: cvrs = ${corlaCvrs.cvrs().size} redactedCvrs= ${corlaCvrs.redactedCvrs().size}")

    val redactedGroupSize = corlaCvrs.redactedGroups().size
    val redactedNCards = corlaCvrs.redactedGroups().sumOf { it.ncards() }
    println("\nRedacted groups (${redactedGroupSize})")
    corlaCvrs.redactedGroups().forEach { println("  $it") }

    println("redacted group ncards = ${redactedNCards}")
    val totalCvrs = nCvrs + redactedNCards
    println("cvrs + redacted ncards = ${totalCvrs}")

    val manifestBatches = input.readCountyManifest()
    val manifestNCards = manifestBatches.totalCards
    println("\n${input.manifestSource}")
    println("  manifestNCards = $manifestNCards")

    val estRedactedNcards = manifestNCards - corlaCvrs.cvrs().size
    println("  est redacted ncards= ${estRedactedNcards}")
    val missingNcards = manifestNCards - totalCvrs
    print("  sumManifest - totalCvrs = ${missingNcards}; ")
    if (missingNcards == 0) print(" AGREE!")
    else if (missingNcards < 0) print(" manifest not up to date ??")
    else if (redactedGroupSize == 1) print(" single group should be set to $estRedactedNcards, currently $redactedNCards ")
    else print(" need $missingNcards more redacted cards")
    println()

    /////////////////////////////////////////////////////////////////
    val manifest = input.readCountyManifest()
    val report = mutableListOf<String>()
    manifest.manifestCounts(corlaCvrs, report)
    report.forEach { println(it) }
} */


