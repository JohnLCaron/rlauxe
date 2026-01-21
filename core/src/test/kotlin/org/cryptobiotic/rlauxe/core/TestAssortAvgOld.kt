package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.estimate.calcAssorterMargin
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import org.cryptobiotic.rlauxe.util.doublePrecision
import org.cryptobiotic.rlauxe.estimate.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.util.makePhantomCvrs
import org.cryptobiotic.rlauxe.util.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

// Does assorter.dilutedMargin()) equals cvr assort average ?
class TestAssortAvgOld {

    //// first pass
    @Test
    fun testPluralityAssorter() {
        val cvrs = CvrBuilders()
            .addCvr().addContest("AvB", "0").ddone()
            .addCvr().addContest("AvB", "1").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            // artifact of creating Contests and candidates from cvrs.
            .addCvr().addContest("AvB").addCandidate("3", 0).ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .build()

        val contestInfo = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "0", "1", "2", "3", "4"),
        )
        val contest = makeContestFromCvrs(contestInfo, cvrs) // Nc is set as number of cvrs with that contest
        println(contest)
        val contestUA = ContestWithAssertions(contest, isClca = false).addStandardAssertions()

        val assertions = contestUA.assertions
        assertNotNull(assertions)
        assertEquals(contest.winners.size * contest.losers.size, assertions.size)
        assertions.forEach {
            assertIs<Assertion>(it)
            assertIs<PluralityAssorter>(it.assorter)
            assertEquals(1.0, it.assorter.upperBound())

            val assortAvgMean = cvrs.map { cvr -> it.assorter.assort(cvr)}.average()
            val reportedMean = margin2mean(it.assorter.dilutedMargin())
            println("${it.assorter.shortName()}: assortAvgMean=${assortAvgMean} reportedMean=${reportedMean}")
            assertEquals(assortAvgMean, reportedMean, doublePrecision)

            val calcMargin = it.assorter.calcAssorterMargin(contest.id, cvrs)
            assertEquals(assortAvgMean, margin2mean(calcMargin), doublePrecision)
            assertEquals(it.assorter.dilutedMargin(), calcMargin, doublePrecision)
        }
    }

    @Test
    fun testPluralityAssorterWithPhantoms() {
        val cvrs = CvrBuilders()
            .addCvr().addContest("AvB", "0").ddone()
            .addCvr().addContest("AvB", "1").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            // artifact of creating Contests and candidates from cvrs.
            .addCvr().addContest("AvB").addCandidate("3", 0).ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addPhantomCvr().addContest("AvB").ddone()
            .build()

        val contestInfo = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "0", "1", "2", "3", "4"),
        )
        val contest = makeContestFromCvrs(contestInfo, cvrs)
        val contestUA = ContestWithAssertions(contest, isClca = false).addStandardAssertions()

        val assertions = contestUA.assertions
        assertNotNull(assertions)
        assertEquals(contest.winners.size * contest.losers.size, assertions.size)
        assertions.forEach {
            assertIs<Assertion>(it)
            assertIs<PluralityAssorter>(it.assorter)
            assertEquals(1.0, it.assorter.upperBound())

            val assortAvg = cvrs.map { cvr -> it.assorter.assort(cvr, usePhantoms = false)}.average()
            val mean = margin2mean(it.assorter.dilutedMargin())
            println("$it: assortAvg=${assortAvg} mean=${mean}")
            assertEquals(assortAvg, mean, doublePrecision)

            val calcMargin = it.assorter.calcAssorterMargin(contest.id, cvrs)
            assertEquals(assortAvg, margin2mean(calcMargin), doublePrecision)
            assertEquals(it.assorter.dilutedMargin(), calcMargin, doublePrecision)

            val Ncd = contest.Nc.toDouble()
            val expectWithPhantoms = (assortAvg * Ncd - 0.5) / Ncd
            val assortWithPhantoms = cvrs.map { cvr -> it.assorter.assort(cvr, usePhantoms = true)}.average()
            println("$it: assortWithPhantoms=${assortWithPhantoms} expectWithPhantoms=${expectWithPhantoms}")
            assertEquals(expectWithPhantoms, assortWithPhantoms, doublePrecision)
        }
    }

    @Test
    fun testPluralityAssorterWithMissingContests() {
        val cvrs = CvrBuilders()
            .addCvr().addContest("AvB", "0").ddone()
            .addCvr().addContest("AvB", "1").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            // artifact of creating Contests and candidates from cvrs.
            .addCvr().addContest("AvB").addCandidate("3", 0).ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addPhantomCvr().addContest("AvB").ddone()
            // a cvr that doesnt have the contest on it; if you include it in the assortAvg, then assortAvg != reportedMean
            .addCvr().addContest("other", "1").ddone()

            .build()

        val contestInfo = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "0", "1", "2", "3", "4"),
        )
        val contest = makeContestFromCvrs(contestInfo, cvrs)
        val contestUA = ContestWithAssertions(contest, isClca = false).addStandardAssertions()

        val assertions = contestUA.assertions
        assertNotNull(assertions)
        assertEquals(contest.winners.size * contest.losers.size, assertions.size)
        assertions.forEach {
            println(it)
            assertIs<Assertion>(it)
            assertIs<PluralityAssorter>(it.assorter)
            assertEquals(1.0, it.assorter.upperBound())

            // if you include all cvr assorts, the
            val assortAvg = cvrs.map { cvr -> it.assorter.assort(cvr, usePhantoms = false)}.average()
            val reportedMean = margin2mean(it.assorter.dilutedMargin())
            println("  allcvrs: assortAvg=${assortAvg} reportedMean=${reportedMean} equals = ${doubleIsClose(assortAvg, reportedMean, doublePrecision)}")
            // assertEquals(assortAvg, reportedMean, doublePrecision)

            // this skips cvrs that dont have the contest
            val skipMargin = it.assorter.calcAssorterMargin(contest.id, cvrs)
            println("$it: assortAvg=${assortAvg} skipMean=${margin2mean(skipMargin)}")
            assertEquals(it.assorter.dilutedMargin(), skipMargin, doublePrecision)
        }
    }

    //// second pass

    @Test
    fun testMakeContestFromCvrsPlurality() {
        val N = 1000
        val cvrMean = 0.55

        val info = ContestInfo("standard", 0, listToMap("A", "B"),
            choiceFunction = SocialChoiceFunction.PLURALITY)

        val cvrs = makeCvrsByExactMean(N, cvrMean)
        val contest = makeContestFromCvrs(info, cvrs)
        println("\n$contest")

        testMeanAssort(cvrs, contest)
    }

    @Test
    fun testMakeContestFromCvrsAboveThreshold() {
        val N = 1000
        val cvrMean = 0.60

        val info = ContestInfo("standard", 0, listToMap("A", "B"),
            choiceFunction = SocialChoiceFunction.THRESHOLD, minFraction = .56)

        val cvrs = makeCvrsByExactMean(N, cvrMean)
        val contest = makeContestFromCvrs(info, cvrs)
        println("\n$contest winners=${contest.winners} losers=${contest.losers}")

        testMeanAssort(cvrs, contest)
    }

    @Test
    fun testPluralityNwinners() {
        val Nc = 1776
        val ncands = 3
        val nwinners = 2

        val candidateNames: List<String> = List(ncands) { it }.map { "cand$it" }
        val info = ContestInfo("contest0", 0,
            candidateNames = listToMap(candidateNames),
            choiceFunction = SocialChoiceFunction.PLURALITY,
            nwinners = nwinners,
            voteForN = nwinners,
        )

        val testData = ContestTestDataNWinners(info, Nc = Nc, phantomPct = 0.01)
        val (cvrs, contest) = testData.makeCvrsAndContest()

        testMeanAssort(cvrs, contest)
    }

    fun testMeanAssort(cvrs: List<Cvr>, contest: Contest) {
        val contestAU = ContestWithAssertions(contest, isClca = false).addStandardAssertions()

        contestAU.assertions.forEach { assertion ->
            val assorter = assertion.assorter
            println("=== ${assorter}")

            // val reportedMargin = (winnerVotes - loserVotes) / (contest.info.voteForN * contest.Nc.toDouble())
            // println("   assorter reportedMargin = ${assorter.reportedMargin()}")
            println("   assorter dilutedMargin = ${margin2mean(assorter.dilutedMargin())}")

            val assortAvg = cvrs.map { assorter.assort(it, usePhantoms = false) }.average()
            println("   assorter assort mean = $assortAvg")
            // println("   assorter assort margin = ${mean2margin(assortAvg)}")
            // cvrs.forEach{ println(" $it == ${assorter.assort(it)}")}

            assertEquals(assortAvg, margin2mean(assorter.dilutedMargin()), doublePrecision)
        }
    }
}

//////////////////////////////////////////////////////////////////////////////
// Candidate for removal

// TODO how does this compare with ContestSimulation ??
// TODO currently estimation wont be accurate for nwinners > 1 ??
//    but this doesnt control margin or undercount. Then back to just using cvrs for estimation ??
private data class ContestTestDataNWinners(
    val info: ContestInfo,
    val Nc: Int,
    val phantomPct: Double,
) {

    val phantomCount: Int
    val ncvrs: Int  // number of cvrs

    init {
        phantomCount = roundToClosest(this.Nc * phantomPct)
        ncvrs = (Nc - phantomCount)
    }

    fun makeCvrsAndContest(): Pair<List<Cvr>, Contest> {
        val ncands = info.candidateIds.size
        println("makeCvrsAndContest Nc= $Nc, ncvrs=$ncvrs, ncands=$ncands")

        // should be "choose voteForN of ncands", just 2 for now
        val candIds = choose2ofN(ncands+1)

        // partition totalVotes into ids.size partitions
        val votesByIdx: List<Pair<Int, Int>> = org.cryptobiotic.rlauxe.estimate.partition(ncvrs, candIds.size)
        val votesByCandIds: List<Pair<IntArray, Int>> = votesByIdx.mapIndexed { idx, votes -> Pair(candIds[idx], votes.second)} // candIds -> nvotes
        println("  choose2ofN: ${candIds.size}")
        println(PartitionTracker(votesByCandIds.toMutableList()).show())

        val voteInput = mutableMapOf<Int, Int>()
        votesByCandIds.forEach {  (candIds, nvotes) ->
            candIds.forEach {
                val candCont = voteInput.getOrPut(it) { 0 }
                voteInput[it] = candCont + nvotes
            }
        }
        println("  voteInput = $voteInput, totalVotes = ${voteInput.map{ it.value }.sum()}, ")

        val cvrVoteTracker = PartitionTracker(votesByCandIds.toMutableList())
        val cvrs = makeCvrs(cvrVoteTracker, ncands) // last candidate is the undervote
        println("  Number of cvrs = ${cvrs.size}")
        val tabs = tabulateCvrs(cvrs.iterator(), mapOf(info.id to info))
        assert(tabs.size == 1)
        val contestTab = tabs[0]!!
        println("  contestTab= $contestTab")
        val nvotes = contestTab.votes.map{ it.value }.sum()
        println("    nvotes = ${nvotes} undervotes= ${2*contestTab.ncards-nvotes-2*phantomCount}")

        val votesFiltered = contestTab.votes.filter { it.key != ncands }
        val contest = Contest(this.info, votesFiltered, contestTab.ncards, contestTab.ncards - this.phantomCount)

        return Pair(cvrs, contest)
    }

    fun makeCvrs(cvrVoteTracker: PartitionTracker, overvote: Int): List<Cvr> {
        var cvrCount = 0
        val result = mutableListOf<Cvr>()
        repeat(this.ncvrs) {
            val cvrb = CvrBuilder2("cvr$cvrCount")
            cvrCount++
            val candidates = cvrVoteTracker.chooseCandidates()

            val candidatesFiltered = candidates.filter { it != overvote }.toIntArray()
            cvrb.addContest(info.id, candidatesFiltered)
            result.add(cvrb.build())
        }
        cvrVoteTracker.trackVotesRemaining.forEach { require(it.second == 0) }

        val phantoms = makePhantomCvrs(info.id, phantomCount)
        result.addAll(phantoms)
        return result
    }
}

class PartitionTracker(val trackVotesRemaining: MutableList<Pair<IntArray, Int>>, val checkVoteCount: Boolean = true) {
    val underVoteCandidate = trackVotesRemaining.size
    var totalVotesLeft = trackVotesRemaining.map { it.second }.sum()

    fun chooseCandidates(): IntArray {
        val choice = Random.nextInt(totalVotesLeft)
        val partitionIdx = findPartitionIdx(choice)

        val partition = trackVotesRemaining[partitionIdx.first]
        trackVotesRemaining[partitionIdx.first] = Pair(partition.first, partition.second - 1)
        totalVotesLeft--
        if (checkVoteCount) {
            val checkVoteCount = trackVotesRemaining.sumOf { it.second }
            require(checkVoteCount == totalVotesLeft)
        }
        return partition.first
    }

    // find the partition index this choice is in. return Pair(partitionIdx, nvotes)
    fun findPartitionIdx(choice: Int): Pair<Int, Int> {
        // find the partition
        var sum = 0
        var nvotes = 0
        var partitionIdx = 0
        while (partitionIdx <= trackVotesRemaining.size) {
            nvotes = trackVotesRemaining[partitionIdx].second
            sum += nvotes
            if (choice < sum) break
            partitionIdx++
        }
        require(nvotes > 0)

        return Pair(partitionIdx, nvotes)
    }

    fun show() = buildString {
        trackVotesRemaining.forEach { appendLine("    ${it.first.joinToString(",")} = ${it.second}") }
    }
}

// here k = 2
fun choose2ofN(n:Int): List<IntArray> {
    val result = mutableListOf<IntArray>()
    for (id1 in 0 until n) {
        for (id2 in id1 until n) {
            if (id2 != id1) {
                result.add(intArrayOf(id1, id2))
            }
        }
    }
    return result
}
