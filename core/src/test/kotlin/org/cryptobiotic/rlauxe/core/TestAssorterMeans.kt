package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.estimate.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.estimate.makePhantomCvrs
import org.cryptobiotic.rlauxe.util.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TestAssorterMeans {

    @Test
    fun testPluralityAssort() {
        val N = 1000
        val cvrMean = 0.55

        val info = ContestInfo("standard", 0, listToMap("A", "B"),
            choiceFunction = SocialChoiceFunction.PLURALITY)

        val cvrs = makeCvrsByExactMean(N, cvrMean)
        val contest = makeContestFromCvrs(info, cvrs)
        println("\n$contest")

        testMeanAssort(contest, cvrs)
    }

    @Test
    fun testPluralityNwinners() {
        val Nc = 1000

        val testData = ContestTestDataNWinners(0,
            Nc = Nc,
            ncands = 3,
            voteForN = 2,
            undervotePct = 0.0,
            phantomPct = 0.0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
        )
        val contest = testData.makeContest()
        println("\n$contest")

        val cvrs = testData.makeCvrs()

        testMeanAssort(contest, cvrs)
    }

    fun testMeanAssort(contest: Contest, cvrs: List<Cvr>) {
        val contestAU = ContestUnderAudit(contest, isComparison = false).makePollingAssertions()

        contestAU.pollingAssertions.forEach { assertion ->
            val assorter = assertion.assorter
            println(" ${assorter}")

            println("   assorter reportedMargin = ${assorter.reportedMargin()}")
            println("   assorter reportedMean = ${margin2mean(assorter.reportedMargin())}")

            val assortAvg = cvrs.map { assorter.assort(it) }.average()
            println("   assorter assort mean = $assortAvg")

            // assertEquals(assortAvg, margin2mean(assorter.reportedMargin()), doublePrecision)
        }
        // assertEquals(cvrMean, assortAvg)
    }
}

data class ContestTestDataNWinners(
    val contestId: Int,
    val Nc: Int,
    val ncands: Int,
    val voteForN: Int,
    // val margin: Double, // margin of top highest vote getters, not counting undervotePct, phantomPct
    val undervotePct: Double, // needed to set Nc
    val phantomPct: Double,
    val choiceFunction: SocialChoiceFunction = SocialChoiceFunction.PLURALITY,
) {
    val candidateNames: List<String> = List(ncands) { it }.map { "cand$it" }
    val info = ContestInfo("contest$contestId", contestId,
        candidateNames = listToMap(candidateNames),
        choiceFunction = choiceFunction,
        nwinners = voteForN,
        voteForN = voteForN
    )

    val underCount: Int  // (Nc - Np) * voteForN - undercount = totalVotes
    val phantomCount: Int
    val totalVotes: Int
    val ncvrs: Int  // number of cvrs

    init {
        underCount = roundToInt(this.Nc * undervotePct)
        phantomCount = roundToInt(this.Nc * phantomPct)
        totalVotes = (Nc - phantomCount) - underCount
        ncvrs = (Nc - phantomCount)
    }

    var adjustedVotes: List<Pair<IntArray, Int>> = emptyList() // (cand, nvotes) includes undervotes
    var cvrVoteTracker = PartitionTracker(mutableListOf()) // for making cvrs

    fun makeContest(): Contest {
        // should be "choose voteForN of ncands", just 2 for now
        val candIds = choose2ofN(ncands)

        // partition totalVotes into ids.size partitions
        val votesByIdx: List<Pair<Int, Int>> = org.cryptobiotic.rlauxe.estimate.partition(totalVotes, candIds.size)
        val votesByCandIds: List<Pair<IntArray, Int>> = votesByIdx.mapIndexed { idx, votes -> Pair(candIds[idx], votes.second)} // candIds -> nvotes
        println(PartitionTracker(votesByCandIds.toMutableList()).show())

        val voteInput = mutableMapOf<Int, Int>()
        votesByCandIds.forEach {  (candIds, nvotes) ->
            candIds.forEach {
                val candCont = voteInput.getOrPut(it) { 0 }
                voteInput[it] = candCont + nvotes
            }
        }
        println("voteInput = $voteInput, totalVotes = ${voteInput.map{ it.value }.sum()}, ")

        val contest = Contest(this.info, voteInput, Nc, this.phantomCount)
        println(contest)
        adjustedVotes = votesByCandIds
        return contest
    }

    //// create new partitions each time this is called
    // includes undervotes and phantoms, size = totalBallots + phantom count
    fun resetTracker() {
        cvrVoteTracker = PartitionTracker(adjustedVotes.toMutableList())
    }

    fun makeCvrs(): List<Cvr> {
        resetTracker()
        val cvrbs = CvrBuilders().addContests(listOf(info))

        val result = mutableListOf<Cvr>()
        repeat(this.ncvrs) {
            val cvrb = cvrbs.addCvr()
            makeCvr(cvrb)
            result.add(cvrb.build())
        }
        cvrVoteTracker.trackVotesRemaining.forEach { require(it.second == 0) }
        val tabCvrs = tabulateVotes(result.toList().iterator())
        println("tabulate Cvrs = $tabCvrs")

        val phantoms = makePhantomCvrs(info.id, phantomCount)
        return result + phantoms
    }

    // choose Candidate, add contest, including undervote (no candidate selected)
    fun makeCvr(cvrb: CvrBuilder) {
        cvrb.addContest(info.name)

        val candidates = cvrVoteTracker.chooseCandidates()
        candidates.forEach { candidateId ->
            cvrb.addContest(info.name, candidateId)
        }
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
        trackVotesRemaining.forEach { appendLine(" ${it.first.joinToString(",")} = ${it.second}") }
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
    println("choose2ofN: ${result.size}")
    return result
}
