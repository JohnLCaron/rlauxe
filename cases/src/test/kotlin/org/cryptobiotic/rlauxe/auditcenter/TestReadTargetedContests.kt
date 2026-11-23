package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.corlaInput.Statewide2024General
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.noerror
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.math.abs
import kotlin.test.Test

// targetedContests.csv
// compare targetedContests with tabulateCounty.csv
// compare targetedContests with contestRound, eg round_1/contest.csv

class TestReadTargetedContests {
    val input: StatewideInput = Statewide2024General()

    // data class TargetedContestsCsv(
    //    val countyName: String,
    //    val contestName: String,
    //    val nwinners: Int,
    //    val lowestWinner: Int,
    //    val highestLoser: Int,
    //    val voteMargin: Int,
    //    val dilutedMargin: Double,
    //    val riskLimit: Double,
    //    val estimatedSamplesToAudit: Int,
    //    val numberOfCvrs: Int,
    //)

    @Test
    fun readTargetedContests2026() {
        val filename = "/home/joncifer/rla/cases/corla/2026/cvrs/2026GeneralRLAContestList.csv"
        val targets = readTargetedContestsCsv(filename) { it }
        println()
        println("${TargetedContestsCsv.header}, calcNeeded, diff")
        targets.forEach {
            val noerror = noerror(it.dilutedMargin / 100, 1.0)
            val calcNeeded = estSampleSizeStandardBet(it.numberOfCvrs, noerror, it.riskLimit / 100)
            println("$it,     ${nfn(calcNeeded,4)},  ${it.estimatedSamplesToAudit - calcNeeded}")
        }
        println()
        println("totalSamples=${targets.sumOf { it.estimatedSamplesToAudit } } totalCvrs=${targets.sumOf { it.numberOfCvrs }}")
        println("read ${targets.size} targetedContests from $filename")

        println()
        println("${TargetedContestsCsv.header}, calcNeeded, diff")
        targets.sortedBy{ it.dilutedMargin }.forEach {
            val noerror = noerror(it.dilutedMargin / 100, 1.0)
            val calcNeeded = estSampleSizeStandardBet(it.numberOfCvrs, noerror, it.riskLimit / 100)
            println("$it,     ${nfn(calcNeeded,4)},  ${it.estimatedSamplesToAudit - calcNeeded}")
        }
    }

    // El Paso, Representative to the 120th United States Congress - District 5        , 1,  140713,  127946,  12767,   0.0222,   3.0,     327,    566056,      327,  0
    // Arapahoe, Arapahoe County Commissioner - District 2                             , 1,   31327,   19435,  11892,   0.0278,   3.0,     262,    105986,      260,  2
    // Jefferson, State Senator - District 20                                          , 1,   50117,   33081,  17036,   0.0301,   3.0,     242,    174578,      241,  1

    // from Colorado Election Results:
    // congress 5: Crank = 141,984; Killin = 157,799; vote margin = 15815, pop = 566056 what is ncards?
    // state senate 20: Mancuso = 36,535, Lieder = 54,579, diff = 18044, pop = 174578

    @Test
    fun readTargetedContests2024() {
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
            readCountyTabulateCsvFromFile("src/test/data/corla/2024audit/tabulateCounty.csv")

        val contestTabAllCounties = input.contestTabsAllCounties()

        println()
        println(trunc("from tabulateCounty", 113))
        println("${trunc(TargetedContestsCsv.header, 93)}  winner, loser, voteMargin, diff%")
        targets.filter { it.countyName != "Colorado" }.forEach { it ->
            val contestName = clean(it.contestName)
            val cct = countyTabAllContests[it.countyName]!!
            val cctContests = cct.contests.mapKeys { clean(it.key) }

            val contestTab = cctContests[contestName]
            if (contestTab == null) {
                // println("*** cant find '${contestName}'")
                // println()
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

        val roundContests = input.roundContests()
        val countyTabAllContests = input.countyTabsAllContests()

        println()
        println("${trunc("---from tabulateCounty---", 100)}     --------from contestRound-----")

        println("${trunc("countyName, contestName", -70)}  winner,  loser, voteMargin,   voteMargin,    npop, margin, nsamples, calcSamples")
        targets.filter { it.countyName != "Colorado" }.forEach { target ->
            val contestName = clean(target.contestName)

            val roundContest = roundContests[contestName]

            val cct = countyTabAllContests[target.countyName]!!
            val cctContests = cct.mapKeys { clean(it.key) }

            val contestTab = cctContests[contestName]
            if (contestTab == null) {
                //println("*** cant find '${contestName}'")
                //println()
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