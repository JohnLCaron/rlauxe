package org.cryptobiotic.rlauxe.corlacvr

import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.corlaInput.CorlaCountyInput
import org.cryptobiotic.rlauxe.corlaInput.CountyManifest
import org.cryptobiotic.rlauxe.corlaInput.ManifestCounts
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.sumContestTabulationsFromCandVotes

// Check manifest and Cvrs without reference to the state files
class CountyCvrs(val corlaCvrs: CorlaRawCvrsIF) {
    val show = false
    // val county = countyInput.countyName

    val infos: Map<Int, ContestInfo>
    val cvrTabs : Map<Int, ContestTabulation>
    val redactTabs : Map<Int, ContestTabulation>

    constructor(countyInput: CorlaCountyInput): this(countyInput.readCorlaCvrs())

    init {
        infos = corlaCvrs.makeContestInfo().map { it ->
            ContestInfo(
                it.name, it.id, it.candidateNames,
                if (it.isIrv) SocialChoiceFunction.IRV else SocialChoiceFunction.PLURALITY,
                it.nwinners
            )
        }.associateBy { it.id }
        val infosByName = infos.mapKeys { it.value.name }

        cvrTabs = tabulateCvrRows(corlaCvrs.cvrs(), infos)

        val sumAccum = mutableMapOf<Int, ContestTabulation>()
        corlaCvrs.redaction().groups().forEach { group ->
            group.candVotes.forEach { (scontestId, candVotes) ->
                sumAccum.sumContestTabulationsFromCandVotes(infos[scontestId]!!, candVotes)
            }
        }
        redactTabs = cvrTabs

        ////////////////////////////////////////////////////
        /* can we use county cvr vote totals to calculate oneaudit subtotals?

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

         */
    }

    fun manifestCounts(manifest: CountyManifest, show: Boolean): ManifestCounts {
        return if (show) {
            val report = mutableListOf<String>()
            val mc = manifest.manifestCounts(corlaCvrs, report)
            report.forEach { println(it) }
            mc
        } else {
            manifest.manifestCounts(corlaCvrs)
        }
    }

    fun showRedactedCvrs() {
        val redaction = corlaCvrs.redaction()
        println("Redacted Cvrs (${redaction.redactedRows().size})")
        redaction.redactedRows().forEach { println("  $it")}
        redaction.groups().forEach { println("  ${it.firstCsv}")}
        println()
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

fun tabulateCvrRows(rows: List<CvrRow>, infos: Map<Int, ContestInfo>): Map<Int, ContestTabulation> {
    val sum = mutableMapOf<Int, ContestTabulation>()
    rows.forEach { row ->
        row.contestVotes.forEach {
            sum.sumContestTabulationsFromCandVotes(infos[it.contestId]!!, it.candVotes())
        }
    }
    return sum
}