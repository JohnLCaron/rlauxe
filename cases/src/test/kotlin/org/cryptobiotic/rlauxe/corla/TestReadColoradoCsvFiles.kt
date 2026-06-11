package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.auditcenter.Colorado2024AuditCenterInput
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.noerror
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.math.abs
import kotlin.test.Test

class TestReadColoradoCsvFiles {
    val input: ColoradoInput = Colorado2024AuditCenterInput()

    // data class CanonicalContest(
    //    val contestName: String,
    //    val choices: List<String>
    //) {
    //    val counties =  mutableSetOf<String>()
    //  }
    @Test
    fun readGeneralCanonicalList() {
        val canonical = readGeneralCanonicalList(input.generalCanonicalFile)
        println("read ${canonical.size} contests from ${input.generalCanonicalFile} (${canonical.sumOf { it.choices.size }} choices)")
        canonical.sortedBy { it.contestName }.forEach { println("  $it") }

        val sizedist = mutableMapOf<Int, Int>() // size, count
        canonical.forEach {
            val count = sizedist.getOrDefault(it.counties.size, 0)
            sizedist[it.counties.size] = count+1
        }
        println("There are ${canonical.size} contests")
        println("There are ${input.canonicalContests().size} canonicalContests")

        println("\nMulticounty contests")
        sizedist.toSortedMap().forEach { println("  ${nfn(it.value, 3)} contests are in ${it.key} counties") }
    }

    //data class CorlaContestRoundCsv(
    //    val contestName: String,
    //    val auditReason: AuditReason,
    //    val nwinners: Int,
    //    val ballotCardCount: Int,
    //    val contestBallotCardCount: Int,
    //    val winners: String,
    //    val minMargin: Int,
    //    val riskLimit: Double,
    //    val gamma: Double,
    //    val optimisticSamplesToAudit: Int,
    //    val estimatedSamplesToAudit: Int,
    //)
    @Test
    fun readColoradoContestRoundFile() {
        // no name cleanup
        val contests = readColoradoContestRoundCsv(input.contestRoundFile)
        // val contests = readColoradoContestRoundCsv(input.contestRoundFile) { contestNameCleanup(it) }
        println("read ${contests.size} contests from ${input.contestRoundFile}")

        println("\n${trunc("contest", -50)}   Npop,   Nc,   needSamples, auditReason")
        contests.values.forEach {
            print("${trunc("${it.contestName}", -50)} ")
            print("${nfn(it.ballotCardCount, 7)}, ${nfn(it.contestBallotCardCount, 7)},  ${nfn(it.optimisticSamplesToAudit, 7)},")
            println(" ${it.auditReason}")
        }
    }

    @Test
    fun readCountyTabulateFile() {
        // no name cleanup
        val contests = readCountyTabulateCsv(input.tabulateCountyFile)
        // val contests = readCountyTabulateCsv(input.tabulateCountyFile, { contestNameCleanup(it)}, { candidateNameCleanup(it) })

        println("read ${contests.size} contests from ${input.tabulateCountyFile} (${contests.values.sumOf { it.choices.size }} choices)")

        println("totalVotesAllCounties")
        contests.values.forEach { contest ->
            val counties = contest.counties()
            val sumCounties = counties.sumOf { contest.countyVotes(it) }
            assertEquals(contest.totalVotesAllCounties, sumCounties)
            println(" ${nfn(contest.totalVotesAllCounties,7)}, ${contest.contestName}")
        }

        println("\nconvertToCountyContestTabs")
        val cct = convertToCountyContestTabs(contests.values.toList())
        cct.forEach {
            println(it)
        }
    }

    @Test
    fun readContestComparison() {
        val (contestMvrs, countyMvrs, countyStyles) = readContestComparisonCsv(input.mvrComparisonFile)
        // val (contestMvrs, countyMvrs, countyStyles) = readContestComparisonCsv(input.mvrComparisonFile) { contestNameCleanup(it) }
        println("read ${countyStyles.size} counties from ${input.mvrComparisonFile}; totalStyles=${countyStyles.sumOf { it.styles.size }} totalCards=${ countyStyles.sumOf{it.cardCount} }")

        println("Styles by County")
        countyStyles.forEach {
            println(it.show())
        }

        var countMvrs = 0
        println("\nNmvrs by County")
        println("\ncounty,     county nmvrs")
        countyMvrs.sortedBy { it.countyName }.forEach {
            println("${trunc(it.countyName, -11)}, ${nfn(it.countMvr, 5)}")
            countMvrs += it.countMvr
        }
        println("total mvrs = $countMvrs")

        println("\nNmvrs by Contest")
        println("\n${trunc("contest", -51)}   county nmvrs, statewide nmvrs")
        contestMvrs.sortedBy { it.contestName }.forEach {
            println("${trunc(it.contestName, -60)} ${nfn(it.countMvr, 5)}, ${nfn(it.countStatewide, 5)}")
        }
    }

    // data class MergedContestInfo(
    //    // canonical
    //    val contestName: String,
    //    val choices: List<String>,
    //    val counties: Set<String>,
    //
    //    // contestRound
    //    val auditReason: AuditReason,
    //    val npop:Int,
    //    val nc:Int,
    //    val voteForN: Int,
    //    val nsamples: Int,
    //    val marginInVotes: Int,
    //
    //    // mvr file
    //    val countyMvrs: Int,
    //    val statewideMvrs: Int,
    //)
    //
    //data class MergedCountyInfo(
    //    val countyName: String,
    //    val countyMvrs: Int,
    //    val Npop: Int,
    //)
    //
    //data class MergedInfo(
    //    val mergedContestInfo: List<MergedContestInfo>,
    //    val mergedCountyInfo: List<MergedCountyInfo>,
    //    val statewideContests: List<CorlaContestRoundCsv>,
    //)
    @Test
    fun showMergeContestInfo() {
        val (mergedContestInfo, mergedCountyInfo, statewideContests) = mergeContestInfo(Colorado2024AuditCenterInput())

        println("\nMerged Contest Info")
        println("\n${trunc("contest", -50)}    Npop,      Nc, voteMargin, countyMvrs, stateMvrs, Ncounties, auditReason")
        mergedContestInfo.forEach {
            print("${trunc("${it.contestName}", -50)} ")
            print("${nfn(it.npop, 7)}, ${nfn(it.nc, 7)}, ${nfn(it.marginInVotes, 7)},")
            print("   ${nfn(it.countyMvrs, 7)},  ${nfn(it.statewideMvrs, 7)}, ")
            println("         ${nfn(it.counties.size, 3)},   ${it.auditReason}")
        }
        println()

        println("\nStrata Info")
        println("\ncounty      nmvrs,  npop")
        mergedCountyInfo.sortedBy { it.strataName }.forEach {
            println("${sfn(it.strataName, -10)}  ${nfn(it.nmvrs, 5)}, ${nfn(it.ncards, 7)}")
        }
        println()

        println("\n${trunc("statewideContests", -50)}     Npop, Nc,   needSamples, auditReason")
        statewideContests.forEach {
            print("${trunc("${it.contestName}", -50)} ")
            print("${nfn(it.ballotCardCount, 7)}, ${nfn(it.contestBallotCardCount, 7)},  ${nfn(it.optimisticSamplesToAudit, 7)},")
            println(" ${it.auditReason}")
        }
    }

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
        val contests = readResultsReportContest(filename) { input.contestNameCleanup(it) }
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
        val targets = readTargetedContestsCsv(filename) { input.contestNameCleanup(it) }
        println()
        println("${TargetedContestsCsv.header}, calcNeeded")
        targets.forEach {
            val noerror = noerror(it.dilutedMargin/100, 1.0)
            val calcNeeded = estSampleSizeStandardBet(it.numberOfCvrs, noerror, it.riskLimit/100)
            // val calcNeeded = estSamplesFromMarginUpper(2 / 1.03905, it.dilutedMargin/100, it.riskLimit/100).toInt()
            println("$it,    $calcNeeded")
        }
        println()
        println("totalSamples=${targets.sumOf { it.estimatedSamplesToAudit } } totalCvrs=${targets.sumOf { it.numberOfCvrs }}")
        println("read ${targets.size} targetedContests from $filename")
    }

    @Test
    fun compareTargetedContestsAndTabulateCounty() {
        val targets: List<TargetedContestsCsv> = readTargetedContestsCsv("src/test/data/corla/2024audit/targetedContests.csv") { input.contestNameCleanup(it) }

        val contestTabsByCounty: Map<String, ContestTabByCounty> = readCountyTabulateCsv("src/test/data/corla/2024audit/tabulateCounty.csv")

        val ccts: Map<String, CountyContestTabs> = convertToCountyContestTabs(contestTabsByCounty.values.toList()).associateBy { clean(it.countyName) }

        println()
        println(trunc("from tabulateCounty", 113))
        println("${trunc(TargetedContestsCsv.header, 93)}  winner, loser, voteMargin, diff%")
        targets.filter { it.countyName != "Colorado" }.forEach { it ->
            val contestName = clean(it.contestName)
            val cct = ccts[it.countyName]!!
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
        val targets: List<TargetedContestsCsv> = readTargetedContestsCsv("src/test/data/corla/2024audit/targetedContests.csv",
            { input.contestNameCleanup(it) } )

        val roundContests = input.roundContests

        val contestTabsByCounty = input.contestTabsByCounty
        val ccts: Map<String, CountyContestTabs> = convertToCountyContestTabs(contestTabsByCounty.values.toList()).associateBy { it.countyName }

        println()
        println("${trunc("---from tabulateCounty---", 100)}     --------from contestRound-----")

        println("${trunc("countyName, contestName", -70)}  winner,  loser, voteMargin,   voteMargin,    npop, margin, nsamples, calcSamples")
        targets.filter { it.countyName != "Colorado" }.forEach { target ->
            val contestName = clean(target.contestName)

            val roundContest = roundContests[contestName]

            val cct = ccts[target.countyName]!!
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

    //////////////////////////////////////

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
