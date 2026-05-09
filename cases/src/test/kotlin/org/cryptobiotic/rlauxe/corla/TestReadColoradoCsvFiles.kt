package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.betting.ClcaErrorRates
import org.cryptobiotic.rlauxe.betting.ClcaErrorTracker
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting
import org.cryptobiotic.rlauxe.betting.populationMeanIfH0
import org.cryptobiotic.rlauxe.shangrla.sampleSize
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.estSamplesFromMarginUpper
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.math.abs
import kotlin.test.Test

class TestReadColoradoCsvFiles {

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

    // data class CanonicalContest(
    //    val contestName: String,
    //    val choices: List<String>
    //) {
    //    val counties =  mutableSetOf<String>()
    //  }
    @Test
    fun readGeneralCanonicalList() {
        val filename = "src/test/data/corla/2024audit/2024GeneralCanonicalList.csv"
        val canonical = readGeneralCanonicalList(filename)
        println("read ${canonical.size} contests from $filename (${canonical.sumOf { it.choices.size }} choices)")
        canonical.sortedBy { it.contestName }.forEach { println(it) }

        val sizedist = mutableMapOf<Int, Int>() // size, count
        canonical.forEach {
            val count = sizedist.getOrDefault(it.counties.size, 0)
            sizedist[it.counties.size] = count+1
        }
        println("ncounties, contestCount")
        sizedist.toSortedMap().forEach { println(it) }

        //contests.forEach {
        //    if (it.counties.size == 2) println(it)
        //}
    }

    // data class CorlaContestRoundCsv(
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
    data class MergedContestInfo(
        val contestName: String,
        val choices: List<String>,
        val counties: Set<String>,

        val auditReason: AuditReason,
        val npop:Int,
        val nc:Int,

        val countyMvrs: Int,
        val stateMvrs: Int,
    )

    // data class CountyMvrs(val countyName: String) {
    //    var countMvr = 0
    //}

    data class MergedCountyInfo(
        val countyName: String,
        val countyMvrs: Int,
        val Npop: Int,
    )

    @Test
    fun mergeContestInfo() {
        val canonicalFile = "src/test/data/corla/2024audit/2024GeneralCanonicalList.csv"
        val canonical = readGeneralCanonicalList(canonicalFile)

        val roundFile = "src/test/data/corla/2024audit/round1/contest.csv"
        val contests = readColoradoContestRoundCsv(roundFile) { contestNameCleanup(it) }

        val compareFile = "src/test/data/corla/2024audit/round3/contestComparison.csv"
        val (contestMvrs, countyMvrs, _) = readContestComparisonCsv(compareFile) { contestNameCleanup(it) }
        val compareMap = contestMvrs.associateBy { it.contestName }
        val countyMap = countyMvrs.associateBy { it.countyName }

        val mergedContestInfo = canonical.sortedBy { it.contestName }.map {
            val round = contests[it.contestName]
            val compare = compareMap[it.contestName]

            MergedContestInfo(
                it.contestName,
                it.choices,
                it.counties,
                round?.auditReason ?: AuditReason.none,
                round?.ballotCardCount ?: 0,
                round?.contestBallotCardCount ?: 0,
                compare ?. countMvr ?: 0,
                compare ?. countStatewide ?: 0,
            )
        }

        println("\nMerged Contest Info")
        println("\n${trunc("contest", -50)}    Npop,      Nc, countyMvrs, stateMvrs, auditReason")
        mergedContestInfo.forEach {
            print("${trunc("${it.contestName}", -50)} ")
            print("${nfn(it.npop, 7)}, ${nfn(it.nc, 7)}, ${nfn(it.countyMvrs, 7)},")
            println(" ${nfn(it.stateMvrs, 7)},      ${it.auditReason}")
        }
        println()

        // pick out the contests that are the targeted ones; should have a single contest
        val mergedCountyInfo = mutableListOf<MergedCountyInfo>()
        val statewideContests = mutableListOf<CorlaContestRoundCsv>()
        canonical.forEach {
            val round = contests[it.contestName]
            if (round != null && round.auditReason == AuditReason.county_wide_contest) {
                if (it.counties.size != 1)
                    println("*** ${it.contestName} has multiple counties: ${it.counties}")
                val county = it.counties.first()
                val countyMvr = countyMap[county]!!

                val countyInfo = MergedCountyInfo(
                    county,
                    countyMvr.countMvr,
                    round.ballotCardCount
                )
                mergedCountyInfo.add(countyInfo)
            }
            if (round != null && round.auditReason == AuditReason.state_wide_contest) {
                statewideContests.add(round)
            }
        }

        println("\nMerged county Info")
        println("\ncounty      nmvrs,  npop")
        mergedCountyInfo.sortedBy { it.countyName }.forEach {
            println("${sfn(it.countyName, -10)}  ${nfn(it.countyMvrs, 5)}, ${nfn(it.Npop, 7)}")
        }
        println()

        println("\n${trunc("statewideContests", -50)}     Npop, Nc,   needSamples, auditReason")
        statewideContests.forEach {
            print("${trunc("${it.contestName}", -50)} ")
            print("${nfn(it.ballotCardCount, 7)}, ${nfn(it.contestBallotCardCount, 7)},  ${nfn(it.optimisticSamplesToAudit, 7)},")
            println(" ${it.auditReason}")
        }

        // return Pair(mergedContestInfo, mergedCountyInfo)
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
        val contests = readResultsReportContest(filename) { contestNameCleanup(it) }
        println("read ${contests.size} contests from $filename")
        println("\n${trunc("contest", -50)} margin, mvrCount, ballotCount,")

        contests.sortedBy { it.contestName }.forEach {
            val star = if (it.targeted) "*" else " "
            print("${trunc("$star${it.contestName}", -50)}   ${trunc(dfn(it.margin, 2), 4)}, ")
            println("${nfn(it.mvrCount, 7)},    ${nfn(it.ballotCount, 7)},")
        }
    }

    @Test
    fun readCountyTabulateFile() {
        val filename = "src/test/data/corla/2024audit/tabulateCounty.csv"
        val contests = readCountyTabulateCsv(filename, { contestNameCleanup(it)}, { candidateNameCleanup(it) })

        println("read ${contests.size} contests from $filename (${contests.values.sumOf { it.choices.size }} choices)")

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
        val filename = "src/test/data/corla/2024audit/round1/contest.csv"
        val contests = readColoradoContestRoundCsv(filename) { contestNameCleanup(it) }
        println("read ${contests.size} contests from $filename")

        println("\n${trunc("contest", -50)}     Npop, Nc,   needSamples, auditReason")
        contests.values.forEach {
            print("${trunc("${it.contestName}", -50)} ")
            print("${nfn(it.ballotCardCount, 7)}, ${nfn(it.contestBallotCardCount, 7)},  ${nfn(it.optimisticSamplesToAudit, 7)},")
            println(" ${it.auditReason}")
        }
    }

    @Test
    fun readContestComparison() {
        val filename = "src/test/data/corla/2024audit/round3/contestComparison.csv"
        val (contestMvrs, countyMvrs, countyStyles) = readContestComparisonCsv(filename) { contestNameCleanup(it) }
        println("read ${countyStyles.size} counties from $filename; totalStyles=${countyStyles.sumOf { it.styles.size }} totalCards=${ countyStyles.sumOf{it.cardCount} }")

        println("Styles by County")
        countyStyles.forEach {
            println(it.show())
        }

        println("\nNmvrs by County")
        println("\ncounty,   county nmvrs")
        countyMvrs.sortedBy { it.countyName }.forEach {
            println("${it.countyName}, ${nfn(it.countMvr, 5)}")
        }

        println("\nNmvrs by Contest")
        println("\n${trunc("contest", -51)}   county nmvrs, statewide nmvrs")
        contestMvrs.sortedBy { it.contestName }.forEach {
            println("${trunc("${it.contestName}", -60)} ${nfn(it.countMvr, 5)}, ${nfn(it.countStatewide, 5)}")
        }
    }

    @Test
    fun readTargetedContests() {
        val filename = "src/test/data/corla/2024audit/targetedContests.csv"
        val targets = readTargetedContestsCsv(filename) { contestNameCleanup(it) }
        println()
        println("${TargetedContestsCsv.header}, calcNeeded")
        targets.forEach {
            val calcNeeded = estSamplesFromMarginUpper(2 / 1.03905, it.dilutedMargin/100, it.riskLimit/100).toInt()
            println("$it,    $calcNeeded")
        }
        println()
        println("totalSamples=${targets.sumOf { it.estimatedSamplesToAudit } } totalCvrs=${targets.sumOf { it.numberOfCvrs }}")
        println("read ${targets.size} targetedContests from $filename")
    }

    @Test
    fun compareTargetedContestsAndTabulateCounty() {
        val targets: List<TargetedContestsCsv> = readTargetedContestsCsv("src/test/data/corla/2024audit/targetedContests.csv") { contestNameCleanup(it) }

        val contestTabsByCounty: Map<String, ContestTabByCounty> =
            readCountyTabulateCsv("src/test/data/corla/2024audit/tabulateCounty.csv",
                { contestNameCleanup(it) },
                { candidateNameCleanup(it) },
                )

        val ccts: Map<String, CountyContestTab> = convertToCountyContestTabs(contestTabsByCounty.values.toList()).associateBy { clean(it.countyName) }

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
        val targets: List<TargetedContestsCsv> = readTargetedContestsCsv("src/test/data/corla/2024audit/targetedContests.csv") { contestNameCleanup(it) }

        val roundContests:  Map<String, CorlaContestRoundCsv> = readColoradoContestRoundCsv( "src/test/data/corla/2024audit/round1/contest.csv") { contestNameCleanup(it) }

        val contestTabsByCounty: Map<String, ContestTabByCounty> = readCountyTabulateCsv("src/test/data/corla/2024audit/tabulateCounty.csv",
            { contestNameCleanup(it) },
            { candidateNameCleanup(it) })

        val ccts: Map<String, CountyContestTab> = convertToCountyContestTabs(contestTabsByCounty.values.toList()).associateBy { it.countyName }

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
                    val npop = roundContest.ballotCardCount
                    val voteMargin = roundContest.minMargin
                    val nsamples = roundContest.optimisticSamplesToAudit
                    val calcMargin = voteMargin / npop.toDouble()
                    val calcNeeded = estSamplesFromMarginUpper(2 / 1.03905, calcMargin, roundContest.riskLimit).toInt()

                    "${nfn(voteMargin, 7)}, ${nfn(npop, 7)}, ${dfn(calcMargin, 3)},     ${nfn(nsamples, 6)},    $calcNeeded"

                } else ""


                println("${trunc("${target.countyName}, ${contestName}", -70)} $contestTabDesc,         $contestRoundDesc")
            }
        }
        println()
    }
}

fun clean(orgName: String) = mutatisMutandi(contestNameCleanup(orgName))

fun CorlaContestRoundCsv.showEstimation() {
    // TODO they use ballotCardCount instead of contestBallotCardCount for some reason
    val dilutedMargin = minMargin.toDouble() / ballotCardCount
    if (dilutedMargin > 0) {
        val est = estimateCorla(riskLimit, dilutedMargin, gamma) // no errors
        val (bet, payoff, samples) = betPayoffSamples(ballotCardCount, risk=riskLimit, assorterMargin=dilutedMargin, 0.0)

        println("dilutedMargin = $dilutedMargin estSamples = ${est} corlaEst=$optimisticSamplesToAudit rauxEst=$samples")
        require(optimisticSamplesToAudit == est)
        println("   rlauxe bet = $bet payoff = $payoff rauxeEst=$samples")
    }
}

// Compare Corla estimate with ours.
// this assumes you get the same bet each time, which is not true because mui is changing.
// Also eps (lower bound on the estimated rate) turns out to be important.
fun betPayoffSamples(N: Int, risk: Double, assorterMargin: Double, error: Double): Triple<Double, Double, Int> {
    val avgCvrAssortValue = margin2mean(assorterMargin)
    val assorterMargin2 = 2.0 * avgCvrAssortValue - 1.0 // reported assorter margin, not clca margin
    // val noerror = 1.0 / (2.0 - assorterMargin / assorter.upperBound())
    val noerror = 1 / (2 - assorterMargin2)

    // assumes upperBound = 1.0
    // class GeneralAdaptiveBetting(
    //    val Npop: Int, // population size for this contest
    //    // val accumErrorCounts: ClcaErrorCounts, // propable illegal to do (cant use prior knowlege of the sample)
    //    val oaErrorRates: OneAuditErrorRates?,
    //    val d: Int = 100,  // trunc weight
    //    val maxRisk: Double, // this bounds how close lam gets to 2.0; TODO study effects of this
    //    val withoutReplacement: Boolean = true,
    //    val debug: Boolean = false,
    //
    // data class GeneralAdaptiveBetting2(
    //    val Npop: Int, // population size for this contest
    //    val aprioriCounts: ClcaErrorCounts, // apriori counts not counting phantoms, non-null so we have noerror and upper
    //    val nphantoms: Int, // number of phantoms in the population
    //    val maxLoss: Double, // between 0 and 1; this bounds how close lam can get to 2.0; maxBet = maxLoss / mui
    //
    //    val oaAssortRates: OneAuditAssortValueRates? = null, // non-null for OneAudit
    //    val d: Int = 100,  // trunc weight
    //    val debug: Boolean = false,
    val bettingFn = GeneralAdaptiveBetting(
        Npop = N,
        aprioriErrorRates = ClcaErrorRates.empty(noerror, 1.0),
        nphantoms = 0,
        oaAssortRates = null,
        d = 100,
        maxLoss = .9,
    )

    val samples = ClcaErrorTracker(noerror, 1.0)
    repeat(10) { samples.addSample(noerror) }
    val bet = bettingFn.bet(samples)
    val mj = populationMeanIfH0(N=N, true, samples)

    val payoff = 1.0 + bet * (noerror - mj)
    val samplesSize = sampleSize(risk, payoff) // fun sampleSize(risk: Double, payoff:Double) = -ln(risk) / ln(payoff)
    return Triple(bet, payoff, roundUp(samplesSize))
}