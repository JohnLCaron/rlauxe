package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.auditcenter.Colorado2024General
import org.cryptobiotic.rlauxe.auditcenter.ColoradoInput
import org.cryptobiotic.rlauxe.auditcenter.ContestTabAllCounties
import org.cryptobiotic.rlauxe.auditcenter.CountyTabAllContests
import org.cryptobiotic.rlauxe.auditcenter.readCountyTabulateCsv
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.noerror
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.math.abs
import kotlin.test.Test

class TestReadColoradoCsvFiles {
    val input: ColoradoInput = Colorado2024General()

    // data class ResultsReportContest(
    //        val contestName: String,
    //        val targeted: Boolean,
    //        val winner: String,
    //        val risk: Double,
    //        val margin: Double,
    //        val mvrCount: Int,  // "audited sample count"
    //        val ballotCount: Int, // "ballot count"
    //        val voteMargin: Int, // test winner - loser
    //        val winnerVotes: Int,
    //        val loserVotes: Int,
    //        val totalVotes: Int, // just the sum of the winner and loser
    //    )

    @Test
    fun readResultsReportContest() {
        val filename = "src/test/data/corla/2024audit/round1/ResultsReportSummary.csv"
        val contests = readResultsReportContest(filename) { it }
        println("read ${contests.size} contests from $filename")
        println("\n${trunc("contest", -50)} margin, mvrCount, ballotCount,")

        contests.sortedBy { it.contestName }.forEach {
            val star = if (it.targeted) "*" else " "
            print("${trunc("$star${it.contestName}", -50)}   ${trunc(dfn(it.margin, 2), 4)}, ")
            println("${nfn(it.mvrCount, 7)},    ${nfn(it.ballotCount, 7)},")
        }
    }

    @Test
    fun readTargetedContests() {
        val filename = "src/test/data/corla/2024audit/targetedContests.csv"
        val targets = readTargetedContestsCsv(filename) { it }
        println()
        println("${TargetedContestsCsv.header}, calcNeeded")
        targets.forEach {
            val noerror = noerror(it.dilutedMargin / 100, 1.0)
            val calcNeeded = estSampleSizeStandardBet(it.numberOfCvrs, noerror, it.riskLimit / 100)
            // val calcNeeded = estSamplesFromMarginUpper(2 / 1.03905, it.dilutedMargin/100, it.riskLimit/100).toInt()
            println("$it,    $calcNeeded")
        }
        println()
        println("totalSamples=${targets.sumOf { it.estimatedSamplesToAudit } } totalCvrs=${targets.sumOf { it.numberOfCvrs }}")
        println("read ${targets.size} targetedContests from $filename")
    }

    @Test
    fun compareTargetedContestsAndTabulateCounty() {
        val targets: List<TargetedContestsCsv> =
            readTargetedContestsCsv("src/test/data/corla/2024audit/targetedContests.csv") { it }

        val countyTabAllContests: Map<String, CountyTabAllContests> =
            readCountyTabulateCsv("src/test/data/corla/2024audit/tabulateCounty.csv")

        val contestTabAllCounties: Map<String, ContestTabAllCounties> = input.contestTabsAllCounties

        println()
        println(trunc("from tabulateCounty", 113))
        println("${trunc(TargetedContestsCsv.header, 93)}  winner, loser, voteMargin, diff%")
        targets.filter { it.countyName != "Colorado" }.forEach { it ->
            val contestName = clean(it.contestName)
            val cct = countyTabAllContests[it.countyName]!!
            val cctContests = cct.contests.mapKeys { clean(it.key) }

            val contestTab = cctContests[contestName]
            if (contestTab == null) {
                println("*** cant find '${contestName}'")
                println()
            } else {
                val choices = contestTab.choices.map { it.value }.sorted().reversed()
                //if (choices.size > 2)
                //  println("$it has ${contestTab.choices}")

                val desc = "${nfn(choices[0], 7)}, ${nfn(choices[1], 7)}, ${nfn(choices[0] - choices[1], 6)}"
                val diff = abs(it.voteMargin - (choices[0] - choices[1])) / it.voteMargin.toDouble()
                val star = if (diff > .05) "***" else ""

                println("${it.short()}   $desc     ${dfn((100.0 * diff), 1)} $star")
            }
        }
        println()
    }


    @Test
    fun compareTabulateCountyAndRoundContest() {
        // use targetedContests only for the county and contest name
        val targets: List<TargetedContestsCsv> = readTargetedContestsCsv(
            "src/test/data/corla/2024audit/targetedContests.csv",
            { it })

        val roundContests = input.roundContests

        val contestTabsByCounty = input.contestTabsAllCounties
        val countyTabAllContests: Map<String, CountyTabAllContests> = input.countyTabsAllContests

        println()
        println("${trunc("---from tabulateCounty---", 100)}     --------from contestRound-----")

        println("${trunc("countyName, contestName", -70)}  winner,  loser, voteMargin,   voteMargin,    npop, margin, nsamples, calcSamples")
        targets.filter { it.countyName != "Colorado" }.forEach { target ->
            val contestName = clean(target.contestName)

            val roundContest = roundContests[contestName]

            val cct = countyTabAllContests[target.countyName]!!
            val cctContests = cct.contests.mapKeys { clean(it.key) }

            val contestTab = cctContests[contestName]
            if (contestTab == null) {
                println("*** cant find '${contestName}'")
                println()
            } else {
                val choices = contestTab.choices.map { it.value }.sorted().reversed()
                val contestTabDesc =
                    "${nfn(choices[0], 7)}, ${nfn(choices[1], 7)}, ${nfn(choices[0] - choices[1], 6)}"

                val contestRoundDesc = if (roundContest != null) {
                    val Npop = roundContest.ballotCardCount
                    val voteMargin = roundContest.minMargin
                    val nsamples = roundContest.optimisticSamplesToAudit
                    val calcMargin = voteMargin / Npop.toDouble()
                    val noerror = noerror(calcMargin, 1.0)
                    val calcNeeded = estSampleSizeStandardBet(Npop, noerror, roundContest.riskLimit)

                    "${nfn(voteMargin, 7)}, ${nfn(Npop, 7)}, ${dfn(calcMargin, 3)},     ${nfn(nsamples, 6)},    $calcNeeded"

                } else ""


                println("${trunc("${target.countyName}, ${contestName}", -70)} $contestTabDesc,         $contestRoundDesc")
            }
        }
        println()
    }

    // class ColoradoElectionContestSummary(
    //    val contestName: String,
    //    val overVotes: Int,
    //    val underVotes: Int,
    //) {
    //    val candidates = mutableListOf<ColoradoElectionCandidateLine>()
    //  }
    // data class ColoradoElectionCandidateLine(
    //    val lineNumber: Int,
    //    val contestName: String,
    //    val choiceName: String,
    //    val partyName: String,
    //    val totalVotes: Int,
    //    val percentVotes: Double,
    //    val registeredVoters: Int,
    //    val ballotsCast: Int,
    //    val numAreaTotal: Int,
    //    val numAreaRptg: Int,
    //    val overVotes: Int,
    //    val underVotes: Int,
    //)
    @Test
    fun testColoradoElectionSummary() {
        val filename = "src/test/data/corla/2024election/summary.csv"
        val contests = readColoradoElectionSummaryCsv(filename)
        println("read ${contests.size} contests in $filename")
        println("read ${ contests.sumOf { it.candidates.size } } candidates")
        contests.forEach { it.complete() }
        println("--------------------------------------------------------------")
        println("contest sort by reversed underVote percentage\n")
        contests.sortedBy { it.underPct }.reversed().forEach { println(it) }
        println("--------------------------------------------------------------")
        println("contest sort by dilutedMargin percentage\n")
        contests.filter{ it.dilutedMargin != 0.0 }.sortedBy { it.dilutedMargin }.forEach { print(it.show()) }
        println("--------------------------------------------------------------")
    }
}