package org.cryptobiotic.rlauxe.util

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.verify.checkEquivilentVotes
import kotlin.Int
import kotlin.random.Random

private val logger = KotlinLogging.logger("VunderBar")

// This is a way to create test Cvrs that match known vote totals and undervotes and novotes for one population or pool
// ok for voteForN > 1,

// vunder = "votes and undervotes and novotes"
// novotes = the cards in the population that dont contain the contest
// voteCounts: Pair(candsVoteFor, count); candsVoteFor is immutable
data class Vunder2(val contestId: Int, val poolId: Int, val voteCounts: List<Pair<IntArray, Int>>, val undervotes: Int, val missing: Int, val voteForN: Int) {
    val nvotes = voteCounts.sumOf { it.second } // candVotes.values.sum()
    val ncards = missing + (undervotes + nvotes) / voteForN
    //val candVotesSorted: Map<Int, Int> =
    //    candVotes.toList().sortedBy { it.second }.reversed().toMap() // reverse sort by largest vote

    val undervoteIdx = voteCounts.size
    val missingIdx = voteCounts.size + 1

    // candId -> count
    val vunder: List<Pair<IntArray, Int>> = voteCounts + Pair(intArrayOf(), undervotes) + Pair(intArrayOf(), missing)
    val nvunder = vunder.size  // ncandidates + 2

    fun cands(): Map<Int, Int> {
        return voteCounts.associate{ it.first[0] to it.second }.toMap()
    }

    override fun toString() = buildString {
        append("id=$contestId, voteForN=$voteForN, votes=${cands()}, nvotes=$nvotes ncards=$ncards, undervotes=$undervotes, missing=$missing")
    }

    companion object {
        fun fromNpop(contestId: Int, undervotes: Int, npop: Int, candVotes: Map<Int, Int>, voteForN: Int): Vunder2 {
            val missing = npop - (undervotes + candVotes.values.sum()) / voteForN
            val voteCounts = candVotes.map { Pair(intArrayOf(it.key), it.value) }
            return Vunder2(contestId, -1, voteCounts, undervotes, missing, voteForN)
        }
        fun fromContestVotes(contestVotes: ContestVotesIF): Vunder2 {
            val missing = contestVotes.ncards() - (contestVotes.undervotes() + contestVotes.votes.values.sum()) / contestVotes.voteForN
            val voteCounts = contestVotes.votes.map { Pair(intArrayOf(it.key), it.value) }
            return Vunder2(contestVotes.contestId, -1, voteCounts, contestVotes.undervotes(), missing, contestVotes.voteForN)
        }
        // data class Vunder(val contestId: Int, val candVotes: Map<Int, Int>, val undervotes: Int, val missing: Int, val voteForN: Int) {
        fun fromCandVotes(contestId: Int, candVotes: Map<Int, Int>, undervotes: Int, missing: Int, voteForN: Int): Vunder2 {
            val voteCounts = candVotes.map { Pair(intArrayOf(it.key), it.value) }
            return Vunder2(contestId, -1, voteCounts, undervotes, missing, voteForN)
        }
    }
}

// call this to create a new set of cvrs
class VunderPicker2(val vunder: Vunder2) {
    var vunderRemaining = mutableListOf<Pair<IntArray, Int>>()  // candId, nvotes
    var vunderLeft = 0

    init {
        vunderRemaining = mutableListOf()
        vunderRemaining.addAll(vunder.vunder)
        vunderLeft = vunder.vunder.sumOf { it.second }
    }

    fun isEmpty() = vunderLeft <= 0
    fun isNotEmpty() = vunderLeft > 0

    fun pickRandomCandidatesAndDecrement() : IntArray? {
        if ((vunder.poolId == 3744) && (vunder.contestId == 18))
            print("")

        if (isEmpty()) {
           logger.error{"Vunder2 called when isEmpty"}
            return intArrayOf()
        }

        val result = if (vunder.voteForN == 1) { // IRV always has voteForN == 1
            val (vunderIdx, voteFor) = chooseCandidateAndDecrement(Random.nextInt(vunderLeft))
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
        val check = vunderRemaining.sumOf { it.second }
        require(check == vunderLeft)

        var sum = 0
        var nvotesLeft = 0
        var idx = 0
        while (idx < vunder.nvunder) {
            nvotesLeft = vunderRemaining[idx].second // votes left for this candidate
            sum += nvotesLeft
            if (randomChoice < sum) break
            idx++
        }
        require(nvotesLeft > 0)
        require(idx < vunder.nvunder)
        decrementCandidateByIdx(idx)

        return Pair(idx, vunderRemaining[idx].first)
    }

    fun decrementCandidateByIdx(vunderIdx: Int): Int {
        val nvotesLeft = vunderRemaining[vunderIdx].second
        if (nvotesLeft > 0) {
            vunderRemaining[vunderIdx] = decrVotes(vunderRemaining[vunderIdx])
            vunderLeft--
        }
        return vunderIdx
    }

    fun decrVotes(votes: Pair<IntArray, Int>) : Pair<IntArray, Int> {
        return Pair(votes.first, votes.second-1)
    }

    // select multiple votes over the remaining votes and undervotes
    // can choose multiple undervotes, but no duplicate candidates
    // if novote gets chosen, put back any selected candidates
    fun chooseCandidatesAndDecrement(voteForN: Int): IntArray? {
        var needVotes = voteForN
        val result = mutableListOf<Pair<Int, Int>>()  // vunderIdx, candId
        val useRemaining = mutableListOf<Pair<IntArray, Int>>()
        useRemaining.addAll(vunderRemaining)

        while (needVotes > 0) {
            val choice = chooseFromRemaining(useRemaining)
            val (vunderIdx, candId) = choice
            when (vunderIdx) {
                vunder.missingIdx -> {
                    result.forEach { incrementCandidateByIdx(it.first) } // put any chosen candidates back
                    return null
                }
                vunder.undervoteIdx -> {
                    result.add(choice)
                    needVotes--
                }
                else -> {
                    if (vunderIdx < 0)
                        print("")
                    result.add(choice)
                    useRemaining.removeAt(vunderIdx) // remove candidate to prevent duplicates
                    needVotes--
                }
            }
        }
        return result.filter{ it.first != vunder.undervoteIdx }.map{ it.second }.toIntArray()
    }

    // return chosen candidate idx and id
    private fun chooseFromRemaining(remaining: List<Pair<IntArray, Int>>) : Pair<Int, Int> {
        val nvotes = remaining.map { it.second }.sum()
        if (nvotes <= 0) {
            // weve run out of votes
            return Pair(vunder.undervoteIdx, -1)
        }
        // pick a number from 0 to number of votes unchosen
        val randomChoice = Random.nextInt(nvotes)

        // find where that lives in the partition
        var sum = 0
        var nvotesLeft = 0
        var idx = 0
        while (idx < remaining.size) {
            nvotesLeft = remaining[idx].second // votes left for this candidate
            sum += nvotesLeft
            if (randomChoice < sum) break
            idx++
        }
        require(nvotesLeft > 0)
        require(idx < remaining.size)

        decrementCandidateByIdx(idx)

        // require single votes when voteForN > 1
        val cands = vunderRemaining[idx].first
        require(cands.size <= 1)
        return if (cands.size == 0) Pair(idx, -1) else Pair(idx, cands[0])
    }

    fun incrementCandidateByIdx(vunderIdx: Int): Int {
        val nvotesLeft = vunderRemaining[vunderIdx].second
        if (nvotesLeft > 0) {
            vunderRemaining[vunderIdx] = incrVotes(vunderRemaining[vunderIdx])
            vunderLeft--
        }
        return vunderIdx
    }

    fun incrVotes(votes: Pair<IntArray, Int>) : Pair<IntArray, Int> {
        return Pair(votes.first, votes.second+1)
    }
}

// combines Vunder for multiple contests into cvrs for one pool
// make cvrs until we exhaust the votes
// this algorithm puts as many contests as possible on each cvr
// the number of cvrs can vary when there are multiple contests

// vunders: contest id -> Vunder
fun makeVunderCvrs(vunders: Map<Int, Vunder2>, poolName: String, poolId: Int?): List<Cvr> {
    val vunderPickers = vunders.mapValues { VunderPicker2(it.value) }

    val rcvrs = mutableListOf<Cvr>()
    var count = 1
    var done = false
    while (!done) {
        val cvrId = "${poolName}-${count}"
        val cvb2 = CvrBuilder2(cvrId, phantom = false, poolId = poolId)
        vunderPickers.entries.forEach { (contestId, vunderPicker) ->
            if (vunderPicker.isNotEmpty()) {
                // pick random candidates for the contest
                val useCandidates = vunderPicker.pickRandomCandidatesAndDecrement()
                // add the contest to cvr unless its a novote
                if (useCandidates != null) {
                    cvb2.addContest(contestId, useCandidates)
                }
            }
        }
        rcvrs.add(cvb2.build())
        // check(vunders, rcvrs)

        count++
        done = vunderPickers.values.all { it.isEmpty() }
    }

    /* find bug
    val voteForNs = vunders.mapValues { it.value.voteForN }
    val votesFromCvrs = tabulateCvrsWithVoteForNs(rcvrs.iterator(), voteForNs)
    votesFromCvrs.forEach { (id, voteFromCvrs) ->
        val fromCvrs = voteFromCvrs.votes.toSortedMap()
        val vunder = vunders[id]!!
        if (show) {
            println("                       vunder ${vunder}")
            println(voteFromCvrs)
        }
        if (!checkEquivilent(vunder, voteFromCvrs)) {
            println("\nfail")
            println("                       vunder ${vunder}")
            println(voteFromCvrs)
            // rcvrs.forEach { println(it) }
            throw RuntimeException("vunderVotes ${vunder} != ${fromCvrs} voteFromCvrs")
        }
    } */

    rcvrs.shuffle()
    return rcvrs
}

/* private val show = false

fun checkEquivilent(vunder: Vunder2, contestTab: ContestTabulation): Boolean {
    val tabNcards = (contestTab.nvotes() + contestTab.undervotes) / contestTab.voteForN
    val vncards = (vunder.nvotes + vunder.undervotes) / vunder.voteForN + vunder.missing
    var allOk = true
    allOk = allOk && (vunder.nvotes == contestTab.nvotes())
    //allOk = allOk && (vunder.undervotes == contestTab.undervotes)
    //allOk = allOk && (vunder.ncards - vunder.missing == contestTab.ncards())
    allOk = allOk && checkEquivilentVotes(vunder.candVotes, contestTab.votes) // TODO IRV
    return allOk
} */
