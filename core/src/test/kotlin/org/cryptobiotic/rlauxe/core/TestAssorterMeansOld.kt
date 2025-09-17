package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.estimate.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.estimate.makePhantomCvrs
import org.cryptobiotic.rlauxe.util.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TestAssorterMeansOld {

    // @Test
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

    // @Test
    fun testPluralityNwinners() {
        val Nc = 1000
        // val cvrMean = 0.55

        val testData = ContestTestDataNWinnersOld(0,
            Nc = Nc,
            ncands = 3,
            voteForN = 2,
            // margin = mean2margin(cvrMean),
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
        val contestAU = ContestUnderAudit(contest, isComparison = false)

        contestAU.pollingAssertions.forEach {
            println(" ${it.assorter}")
            val assertion = contestAU.minPollingAssertion()!!
            val assorter = assertion.assorter
            println("   assorter reportedMargin = ${assorter.reportedMargin()}")
            println("   assorter reportedMean = ${margin2mean(assorter.reportedMargin())}")

            val assortAvg = cvrs.map { assorter.assort(it) }.average()
            println("   assorter assort mean = $assortAvg")

            assertEquals(assortAvg, margin2mean(assorter.reportedMargin()), doublePrecision)
        }
        // assertEquals(cvrMean, assortAvg)
    }
}

data class ContestTestDataNWinnersOld(
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
        underCount = roundToClosest(this.Nc * undervotePct * voteForN)
        phantomCount = roundToClosest(this.Nc * phantomPct)
        totalVotes = (Nc - phantomCount) * voteForN - underCount
        ncvrs = (Nc - phantomCount)
    }

    var adjustedVotes: List<Pair<Int, Int>> = emptyList() // (cand, nvotes) includes undervotes
    var cvrVoteTracker = PartitionTrackerOld(mutableListOf()) // for making cvrs

    fun makeContest(): Contest {
        // partition totalVotes into ids.size partitions
        val votes: List<Pair<Int, Int>> = org.cryptobiotic.rlauxe.estimate.partition(totalVotes, ncands) // candIdx -> nvotes
        // this only works because cand id == cand index
        var svotes = votes.sortedBy { it.second }.reversed().toMutableList()
        svotes.forEach { println(it) }

        // create contest before you add the undervotes
        val contest = Contest(this.info, svotes.toMap(), Nc, this.phantomCount)

        // undervotes are a candidate whose partitionIndex = ncands
        if (underCount > 0) {
            svotes.add(Pair(ncands, underCount)) // the adjusted votes include the undervotes
        }
        this.adjustedVotes = svotes

        return contest
    }

    /* maybe adjust doesnt need the undervotes?
    fun adjust(svotes: MutableList<Pair<Int, Int>>, Nc: Int): Int {
        val winner = svotes[0]
        val loser = svotes[1]
        val wantMarginDiffD = margin * Nc
        val wantMarginDiff = roundToInt(wantMarginDiffD)
        val haveMarginDiff = (winner.second - loser.second)
        val adjust: Int = roundToInt((wantMarginDiff - haveMarginDiff) * 0.5) // can be positive or negetive
        svotes[0] = Pair(winner.first, winner.second + adjust)
        svotes[1] = Pair(loser.first, loser.second - adjust)
        return adjust // will be 0 when done
    } */

    //// create new partitions each time this is called
    // includes undervotes and phantoms, size = totalBallots + phantom count
    fun resetTracker() {
        cvrVoteTracker = PartitionTrackerOld(adjustedVotes.toMutableList())
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
        println(cvrVoteTracker.show())

        val phantoms = makePhantomCvrs(info.id, phantomCount)
        return result + phantoms
    }

    // choose Candidate, add contest, including undervote (no candidate selected)
    fun makeCvr(cvrb: CvrBuilder) {
        val chosen = mutableSetOf<Int>()
        cvrb.addContest(info.name)
        while (chosen.size < voteForN) {
            val candidateIdx = cvrVoteTracker.chooseCandidateIdx(chosen)
            if (candidateIdx != ncands) {  // ignore undervote
               cvrb.addContest(info.name, candidateIdx)
            }
            chosen.add(candidateIdx)
        }
    }
}

class PartitionTrackerOld(val trackVotesRemaining: MutableList<Pair<Int, Int>>, val checkVoteCount: Boolean = true) {
    val underVoteCandidate = trackVotesRemaining.size
    var totalVotesLeft = trackVotesRemaining.map { it.second }.sum()

    // add another partition
    fun addPartition (partitionIdx: Int, nthings: Int) {
        trackVotesRemaining.add(Pair(partitionIdx, nthings))
        totalVotesLeft = trackVotesRemaining.map { it.second}.sum()
    }

    fun chooseCandidateIdx(chosen: Set<Int>): Int {
        if (trackVotesRemaining.size == 0)
            return underVoteCandidate

        // if theres only one candidate left, it might already be chosen
        if (trackVotesRemaining.size == 1) {
            val lastCandidate = trackVotesRemaining.first().first
            if (chosen.contains(lastCandidate)) return underVoteCandidate else {
                decrement(lastCandidate)
                return lastCandidate
            }
        }

        for (count in 0..100) {
            val choice = Random.nextInt(totalVotesLeft)
            val (candidateIdx, nvotes) = findPartitionIdx(choice)
            if (!chosen.contains(candidateIdx)) {
                decrement(candidateIdx)
                return candidateIdx
            }
        }

         // just pick the first non-chosen candidate
        trackVotesRemaining.forEach {
            if (!chosen.contains(it.first) && it.second > 0) {
                decrement(it.first)
                return it.first
            }
        }

        throw IllegalStateException("should not get here")
    }

    fun decrement(partitionId: Int) {
        // once we start deleting...maybe we shouldnt delete
        val partitionIdx = trackVotesRemaining.indexOfFirst { it.first == partitionId }
        if (partitionIdx < 0)
            print("why")
        val nvotes = trackVotesRemaining[partitionIdx].second
        if (nvotes == 1) { // remove empty partition
            trackVotesRemaining.removeAt(partitionIdx)
        } else {
            trackVotesRemaining[partitionIdx] = Pair(partitionIdx, nvotes - 1)
        }
        totalVotesLeft--
        if (checkVoteCount) {
            val checkVoteCount = trackVotesRemaining.sumOf { it.second }
            require(checkVoteCount == totalVotesLeft)
        }
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
        trackVotesRemaining.forEach { append(it) }
    }
}
