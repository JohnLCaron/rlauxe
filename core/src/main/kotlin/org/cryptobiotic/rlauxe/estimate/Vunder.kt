package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.CvrBuilder2
import kotlin.Int
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.random.Random

// This is a way to create test Cvrs that match known vote totals and undervotes and novotes for one population or pool
// ok for voteForN > 1, ok for IRV

// One contest, one pool
// vunder = "votes and undervotes and missing votes"
// missing votes = the cards in the population that dont contain the contest
// voteCounts: Pair(candsVoteFor, count); candsVoteFor is immutable
data class Vunder(val contestId: Int, val poolId: Int?, val voteCounts: List<Pair<IntArray, Int>>, val undervotes: Int, val missing: Int, val voteForN: Int) {
    val nvotes = voteCounts.sumOf { it.second } // candVotes.values.sum()
    val ncards = missing + (undervotes + nvotes) / voteForN

    val undervoteIdx = voteCounts.size
    val missingIdx = voteCounts.size + 1

    // {candId, undervote, missing} -> count
    val vunder: List<Pair<IntArray, Int>> = voteCounts + Pair(intArrayOf(), undervotes) + Pair(intArrayOf(), missing)
    val nvunder = vunder.size  // ncandidates + 2

    // only for non-IRV
    fun cands(): Map<Int, Int> {
        return voteCounts.map{ Pair(it.first[0], it.second) }.sortedBy { it.first }.toMap()
    }

    override fun toString() = buildString {
        append("id=$contestId, voteForN=$voteForN, votes=${cands()}, nvotes=$nvotes ncards=$ncards, undervotes=$undervotes, missing=$missing")
    }

    companion object {
        fun fromNpop(contestId: Int, undervotes: Int, npop: Int, candVotes: Map<Int, Int>, voteForN: Int): Vunder {
            val missing = npop - (undervotes + candVotes.values.sum()) / voteForN
            val voteCounts = candVotes.map { Pair(intArrayOf(it.key), it.value) }
            return Vunder(contestId, -1, voteCounts, undervotes, missing, voteForN)
        }

        // data class Vunder(val contestId: Int, val candVotes: Map<Int, Int>, val undervotes: Int, val missing: Int, val voteForN: Int) {
        fun fromCandVotes(contestId: Int, candVotes: Map<Int, Int>, undervotes: Int, missing: Int, voteForN: Int): Vunder {
            val voteCounts = candVotes.map { Pair(intArrayOf(it.key), it.value) }
            return Vunder(contestId, -1, voteCounts, undervotes, missing, voteForN)
        }

        fun fromContest(contestUA: ContestWithAssertions, poolId: Int): Vunder {
            val contest = contestUA.contest as Contest
            val sumVotes = contest.votes.map{ it.value }.sum()
            val voteCounts = contest.votes.map { Pair(intArrayOf(it.key), it.value) }
            val undervotes = contest.Nundervotes()
            val voteForN = contest.info().voteForN
            val missing = contestUA.Npop - (undervotes + sumVotes) / voteForN
            return Vunder(contestUA.id, poolId, voteCounts, undervotes, missing, voteForN)
        }
    }
}

data class Choice(val vunderIdx: Int, val cands: IntArray, var remaining: Int)

// call this to create a new set of cvrs
class VunderPicker(val vunder: Vunder) {
    val vunderRemaining = mutableListOf<Choice>()  // candId, nvotes
    fun vunderLeft() = vunderRemaining.sumOf { it.remaining }

    init {
        vunder.vunder.forEachIndexed { idx, it -> vunderRemaining.add(Choice(idx,it.first, it.second)) }
    }

    fun isEmpty() = vunderLeft() <= 0
    fun isNotEmpty() = vunderLeft() > 0

    fun pickRandomCandidatesAndDecrement(): IntArray? {
        if (isEmpty()) {
            logger.error { "VunderPicker called when isEmpty" }
            return null
        }

        val result = if (vunder.voteForN == 1) { // IRV always has vunder.voteForN == 1
            val (vunderIdx, voteFor) = chooseCandidateAndDecrement(Random.nextInt(vunderLeft()))
            when (vunderIdx) {
                vunder.missingIdx -> null
                vunder.undervoteIdx -> intArrayOf()
                else -> voteFor
            }
        } else {
            chooseCandidatesAndDecrement(vunder.voteForN)
        }
        return result
    }

    // this is a uniform sampling over the remaining votes and undervotes
    // randomChoice is a number from 0..votesLeft
    // shrink the partition as votes are taken from it
    // return candidateId
    fun chooseCandidateAndDecrement(randomChoice: Int): Pair<Int, IntArray> {
        var sum = 0
        var nvotesLeft = 0
        var idx = 0
        while (idx < vunder.nvunder) {
            nvotesLeft = vunderRemaining[idx].remaining // votes left for this candidate
            sum += nvotesLeft
            if (randomChoice < sum) break
            idx++
        }
        require(nvotesLeft > 0)
        require(idx < vunder.nvunder)
        decrementCandidateByIdx(idx)

        return Pair(idx, vunderRemaining[idx].cands)
    }

    fun decrementCandidateByIdx(vunderIdx: Int) {
        val choice = vunderRemaining[vunderIdx]
        if (choice.remaining > 0) {
            choice.remaining--
        } else {
            println("  *** no votesLeft for $choice")
        }
    }

    // select multiple votes over the remaining votes and undervotes
    // can choose multiple undervotes, but no duplicate candidates
    // if novote gets chosen, put back any selected candidates
    fun chooseCandidatesAndDecrement(voteForN: Int): IntArray? {
        var needVotes = voteForN
        val result = mutableListOf<Pair<Int, Int>>()  // vunderIdx, candId

        // chooseFrom and vunderRemaining share the same Chooses
        val chooseFrom = mutableListOf<Choice>()
        chooseFrom.addAll(vunderRemaining)

        while (needVotes > 0) {
            val (chooseFromIdx, choice) =  chooseFromRemaining(chooseFrom)
            //println("  choice=$choice needVotes=$needVotes vunderLeft=${vunderLeft()}")
            when (choice.vunderIdx) {
                vunder.missingIdx -> {
                    result.forEach { incrementCandidateByIdx(it.first) } // put any chosen candidates back
                    //println("return null")
                    return null
                }

                vunder.undervoteIdx -> {
                    result.add(Pair(vunder.undervoteIdx, -1))
                    needVotes--
                }

                else -> {
                    // cands must have single vote when voteForN > 1
                    require(choice.cands.size == 1)
                    result.add(Pair(choice.vunderIdx, choice.cands[0]))
                    chooseFrom.removeAt(chooseFromIdx) // remove candidate to prevent duplicates
                    needVotes--
                }
            }
        }
        val r = result.filter { it.first != vunder.undervoteIdx }.map { it.second }.toIntArray()
        //println("return ${r.contentToString()}")
        return r
    }

    private fun chooseFromRemaining(remaining: List<Choice>): Pair<Int, Choice> { // vunderIdx, choice
        val nvotes = remaining.map { it.remaining }.sum()
        if (nvotes <= 0) {
            // happens on the last ballot i think
            return Pair(-1, Choice(vunder.undervoteIdx, intArrayOf(), 0))
        }
        // pick a number from 0 to number of votes unchosen
        val randomChoice = Random.nextInt(nvotes)

        // find where that lives in the partition
        var sum = 0
        var nvotesLeft = 0
        var idx = 0  // index into remaining
        while (idx < remaining.size) {
            nvotesLeft = remaining[idx].remaining // votes left for this candidate
            sum += nvotesLeft
            if (randomChoice < sum) break
            idx++
        }
        require(nvotesLeft > 0)
        require(idx < remaining.size)

        val choice = remaining[idx]
        choice.remaining--

        return Pair(idx, choice)
    }

    fun incrementCandidateByIdx(vunderIdx: Int) {
        val choose = vunderRemaining[vunderIdx]
        choose.remaining++
    }

    companion object {
        private val logger = KotlinLogging.logger("VunderPicker")
    }
}

// multiple contests, one pool
// set Vunder.missing to 0 for hasSingleCardStyle=true
fun makeCvrsForOnePool(vunders: Map<Int, Vunder>, poolName: String, poolId: Int?, hasSingleCardStyle: Boolean): List<Cvr> {
    val vunderpool = VunderPool(vunders, poolName, poolId ?: -1, hasSingleCardStyle)

    val rcvrs = mutableListOf<Cvr>()
    var count = 1
    while (!vunderpool.done()) {
        val cvrId = "${poolName}-${count}"
        val cvb2 = CvrBuilder2(cvrId, phantom = false, poolId = poolId)
        vunderpool.simulatePooledCvr(cvb2)
        rcvrs.add(cvb2.build())
        count++
    }

    rcvrs.shuffle()
    return rcvrs
}

/////////////////////////////////////////////
//// use this when you dont have ContestInfo yet

// Number of votes in each contest, return contestId -> candidateId -> nvotes
fun tabulateVotesFromCvrs(cvrs: Iterator<Cvr>): Map<Int, Map<Int, Int>> {
    val votes = mutableMapOf<Int, MutableMap<Int, Int>>()
    for (cvr in cvrs) {
        for ((contestId, conVotes) in cvr.votes) {
            val accumVotes = votes.getOrPut(contestId) { mutableMapOf() }
            for (cand in conVotes) {
                val accum = accumVotes.getOrPut(cand) { 0 }
                accumVotes[cand] = accum + 1
            }
        }
    }
    return votes
}

// non IRV
// return contestId -> ContestTabulation
fun tabulateCvrsWithVoteForNs(cvrIter: Iterator<Cvr>, voteForNs: Map<Int, Int>): Map<Int, ContestTabulation> {
    val votes = mutableMapOf<Int, ContestTabulation>()
    while (cvrIter.hasNext()) {
        val cvr = cvrIter.next()
        for ((contestId, conVotes) in cvr.votes) {
            val voteForN = voteForNs[contestId]
            if (voteForN != null) {
                // class ContestTabulation(val contestId: Int, val voteForN: Int, val isIrv: Boolean, val candidateIdToIndex: Map<Int, Int>): RegVotesIF {
                val tab = votes.getOrPut(contestId) { ContestTabulation(contestId, voteForN, false, emptyList()) }
                tab.addVotes(conVotes, cvr.phantom)
            }
        }
    }
    return votes
}
