package org.cryptobiotic.rlauxe.corlaCounty

import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.corla.ManifestCounts
import org.cryptobiotic.rlauxe.corla.ManifestEntries
import org.cryptobiotic.rlauxe.corlaInput.CountyInput
import org.cryptobiotic.rlauxe.corlacvr.sumContestTabulationsFromCandVotes
import org.cryptobiotic.rlauxe.corlacvr.tabulateCvrRows
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.sumContestTabulations
import org.cryptobiotic.rlauxe.util.sumContestTabulationsFromCandVotes
import org.cryptobiotic.rlauxe.util.trunc

// Match manifest and Cvrs without reference to the state files
class MatchCountyCvrs(val countyInput: CountyInput) {
    val show = false
    val corlaCvrs = countyInput.readCorlaCvrs()
    val county = countyInput.countyName

    val infos: Map<Int, CorlaContestInfo>
    val cvrTabs : Map<Int, ContestTabulation>
    val redactTabs : Map<Int, ContestTabulation>

    init {
        infos = corlaCvrs.makeContestInfo()

        cvrTabs = tabulateCvrRows(corlaCvrs.cvrs().iterator(), infos)

        val sumAccum = mutableMapOf<Int, ContestTabulation>()
        corlaCvrs.redaction().groups().forEach { group ->
            group.candVotes.forEach { (scontestId, candVotes) ->
                sumAccum.sumContestTabulationsFromCandVotes(infos[scontestId]!!, candVotes)
            }
        }
        redactTabs = sumAccum
    }

    fun manifestCounts(show: Boolean): ManifestCounts {
        val manifest: ManifestEntries = countyInput.readManifestEntries()
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
        println("Redacted Cvr Rows (${redaction.redactedRows().size})")
        redaction.redactedRows().forEach { println("  $it")}
        println("Redacted Cvr Groups (${redaction.groups().size})")
        redaction.groups().forEach { println("  ${it.firstCsv}")}
        println()
    }

    fun showTabulations() {
        val allContests = (cvrTabs.keys.toSet() + redactTabs.keys.toSet()).toSortedSet()
        val totalTabs = sumContestTabulations(listOf(cvrTabs, redactTabs))
        val nameWidth = 70
        println("${sfn("contest", -(nameWidth-2))} voteForN,     cvr,   redact,   total")
        allContests.forEach { contestId ->
            val cvrNvotes = cvrTabs[contestId]?.nvotes() ?: -1
            val redactNvotes = redactTabs[contestId]?.nvotes() ?: -1
            val totalNvotes = totalTabs[contestId]?.nvotes() ?: -1
            val info = infos[contestId]!!
            println("${nfn(contestId, 3)} ${trunc(info.name, -nameWidth)} ${info.voteForN},  ${nfn(cvrNvotes, 7)},  ${nfn(redactNvotes, 7)}, ${nfn(totalNvotes, 7)}")
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