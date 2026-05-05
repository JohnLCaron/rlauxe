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

    @Test
    fun testResultsReportContest() {
        val filename = "src/test/data/corla/2024audit/round1/ResultsReportSummary.csv"
        val contests = readResultsReportContest(filename)
        println("read ${contests.size} contests from $filename")
    }

    @Test
    fun readGeneralCanonicalList() {
        val filename = "src/test/data/corla/2024audit/2024GeneralCanonicalList.csv"
        val contests = readGeneralCanonicalList(filename)
        println("read ${contests.size} contests from $filename (${contests.sumOf { it.choices.size }} choices)")
        // contests.forEach { println(it) }
        val sizedist = mutableMapOf<Int, Int>() // size, count
        contests.forEach {
            val count = sizedist.getOrDefault(it.counties.size, 0)
            sizedist[it.counties.size] = count+1
        }
        println("ncounties, contestCount")
        sizedist.toSortedMap().forEach { println(it) }

        //contests.forEach {
        //    if (it.counties.size == 2) println(it)
        //}
    }

    @Test
    fun testCountyTabulateCsv() {
        val filename = "src/test/data/corla/2024audit/tabulateCounty.csv"
        val contests = readCountyTabulateCsv(filename)
        println("read ${contests.size} contests from $filename (${contests.sumOf { it.choices.size }} choices)")
        contests.forEach { println(it) }
        contests.forEach { contest ->
            val counties = contest.counties()
            val sumCounties = counties.sumOf { contest.countyVotes(it) }
            assertEquals(contest.totalVotesAllCounties, sumCounties)
        }

        println("\nconvertToCountyContestTabs")
        val cct = convertToCountyContestTabs(contests)
        cct.forEach {
            println(it)
        }
    }

    @Test
    fun testColoradoContestRoundCsv() {
        val filename = "src/test/data/corla/2024audit/round1/contest.csv"
        val contests = readColoradoContestRoundCsv(filename)
        println("read ${contests.size} contests from $filename")
        contests.forEach { it.showEstimation() }
    }

    @Test
    fun testCountyStyles() {
        val filename = "src/test/data/corla/2024audit/round3/contestComparison.csv"
        val countyStyles = readContestComparisonCsv(filename)
        println("read ${countyStyles.size} countyStyles from $filename totalStyles=${countyStyles.sumOf { it.styles.size }} totalCards=${ countyStyles.sumOf{it.cardCount} }")
        countyStyles.forEach {
            println(it.show())
        }
        countyStyles.forEach {
            println("${it.countyName} ${it.styles.size}")
            it.styles.values.forEach { println("  ${it.id} ${it.contests.size}") }
        }
    }

    @Test
    fun testTargetedContests() {
        val filename = "src/test/data/corla/2024audit/targetedContests.csv"
        val targets = readTargetedContestsCsv(filename)
        println()
        println("${TargetedContestsCsv.header}, calcNeeded")
        targets.forEach {
            val calcNeeded = estSamplesFromMarginUpper(2 / 1.03905, it.dilutedMargin/100, it.riskLimit/100).toInt()
            println("$it,    $calcNeeded")
        }
        println()
        println("totalSamples=${targets.sumOf { it.estimatedSamplesToAudit } } totalCvrs=${targets.sumOf { it.numberOfCvrs }}")
        println("read ${targets.size} countyStyles from $filename")
    }

    @Test
    fun compareTargetedContestsAndTabulateCounty() {
        val targets: List<TargetedContestsCsv> = readTargetedContestsCsv("src/test/data/corla/2024audit/targetedContests.csv")

        val contestTabsByCounty: Map<String, ContestTabByCounty> =
            readCountyTabulateCsv("src/test/data/corla/2024audit/tabulateCounty.csv").associateBy { clean(it.contestName) }
        val ccts: Map<String, CountyContestTab> = convertToCountyContestTabs(contestTabsByCounty.values.toList()).associateBy { clean(it.countyName) }

        println()
        println(trunc("from tabulateCounty", 113))
        println("${trunc(TargetedContestsCsv.header, 93)}  winner, loser, voteMargin, diff%")
        targets.filter { it.countyName != "Colorado" }.forEach { it ->
            /* val contestCountyTab = contestTabsByCounty[it.contestName]
            if (contestCountyTab == null) {
                println("cant find ${it.contestName}")
                throw RuntimeException()
            }

            if (contestCountyTab.counties().size > 1)
                println("$it has ${contestCountyTab.counties()}")

             */

            val contestName = clean(it.contestName)
            val cct = ccts[it.countyName]!!
            val cctContests = cct.contests.mapKeys { clean(it.key) }
            //println("${it.countyName} = ${cctContests.keys}")
            //cctContests.keys.forEach { println("  $it") }

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
        // use targetedContests only for teh county and contest name
        val targets: List<TargetedContestsCsv> = readTargetedContestsCsv("src/test/data/corla/2024audit/targetedContests.csv")

        val roundContests:  Map<String,CorlaContestRoundCsv> = readColoradoContestRoundCsv( "src/test/data/corla/2024audit/round1/contest.csv")
            .associateBy { clean(it.contestName) }

        val contestTabsByCounty: Map<String, ContestTabByCounty> = readCountyTabulateCsv("src/test/data/corla/2024audit/tabulateCounty.csv")
                .associateBy { clean(it.contestName) }
        val ccts: Map<String, CountyContestTab> = convertToCountyContestTabs(contestTabsByCounty.values.toList()).associateBy { it.countyName }

        println()
        println("${trunc("---from tabulateCounty---", 100)}     --------from contestRound-----")

        println("${trunc("contestName, countyName", -70)}  winner,  loser, voteMargin,   voteMargin,    npop, margin, nsamples, calcSamples")
        targets.filter { it.countyName != "Colorado" }.forEach { target ->
            val contestName = clean(target.contestName)
            val roundContest = roundContests[contestName]!!

            val cct = ccts[target.countyName]!!
            val cctContests = cct.contests.mapKeys { clean(it.key) }

            val contestTab = cctContests[contestName]
            if (contestTab == null) {
                println("*** cant find '${contestName}'")
                println()
            } else {
                val choices = contestTab.choices.map { it.value }.sorted().reversed()
                val contestTabDesc = "${nfn(choices[0], 7)}, ${nfn(choices[1], 7)}, ${nfn(choices[0] - choices[1], 6)}"

                val npop = roundContest.ballotCardCount
                val voteMargin = roundContest.minMargin
                val nsamples = roundContest.optimisticSamplesToAudit
                val calcMargin = voteMargin/npop.toDouble()
                val contestRoundDesc = "${nfn(voteMargin, 7)}, ${nfn(npop, 7)}, ${dfn(calcMargin, 3)},     ${nfn(nsamples, 6)}"
                val calcNeeded = estSamplesFromMarginUpper(2 / 1.03905, calcMargin, roundContest.riskLimit).toInt()

                println("${trunc("${target.countyName}, ${contestName}", -70)} $contestTabDesc,         $contestRoundDesc,    $calcNeeded")
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