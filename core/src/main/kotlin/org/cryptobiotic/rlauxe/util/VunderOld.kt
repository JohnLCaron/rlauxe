package org.cryptobiotic.rlauxe.util

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.verify.checkEquivilentVotes
import kotlin.Int
import kotlin.random.Random

private val logger = KotlinLogging.logger("VunderBar")

// This is a way to create test Cvrs that match known vote totals and undervotes and novotes for one population or pool
// ok for voteForN > 1, but not for IRV

// vunder = "votes and undervotes and novotes"
// novotes = the cards in the population that dont contain the contest
data class VunderOld(val contestId: Int, val candVotes: Map<Int, Int>, val undervotes: Int, val missing: Int, val voteForN: Int) {
    val nvotes = candVotes.values.sum()
    val ncards = missing + (undervotes + nvotes) / voteForN
    val candVotesSorted: Map<Int, Int> =
        candVotes.toList().sortedBy { it.second }.reversed().toMap() // reverse sort by largest vote

    val undervoteId = if (candVotes.isEmpty()) 1 else candVotes.maxOf { it.key } + 1
    val missingId = undervoteId + 1

    // candId -> count
    val vunder: List<Pair<Int, Int>> = candVotes.toList() + Pair(undervoteId, undervotes) + Pair(missingId, missing)
    val nvunder = vunder.size  // ncandidates + 2

    override fun toString() = buildString {
        append("id=$contestId, voteForN=$voteForN, votes=${candVotes}, nvotes=$nvotes ncards=$ncards, undervotes=$undervotes, missing=$missing")
    }

    companion object {
        fun fromNpop(contestId: Int, undervotes: Int, npop: Int, candVotes: Map<Int, Int>, voteForN: Int): VunderOld {
            val missing = npop - (undervotes + candVotes.values.sum()) / voteForN
            return VunderOld(contestId, candVotes, undervotes, missing, voteForN)
        }
        fun fromContestVotes(contestVotes: ContestVotesIF): VunderOld {
            val missing = contestVotes.ncards() - (contestVotes.undervotes() + contestVotes.votes.values.sum()) / contestVotes.voteForN
            return VunderOld(contestVotes.contestId, contestVotes.votes, contestVotes.undervotes(), missing, contestVotes.voteForN)
        }
    }
}

// call this to create a new set of cvrs
class VunderPickerOld(val vunder: VunderOld) {
    var vunderRemaining = mutableListOf<Pair<Int, Int>>()  // candId, nvotes
    var vunderLeft = 0

    init {
        vunderRemaining = mutableListOf()
        vunderRemaining.addAll(vunder.vunder)
        vunderLeft = vunder.vunder.sumOf { it.second }
    }

    fun isEmpty() = vunderLeft <= 0
    fun isNotEmpty() = vunderLeft > 0

    fun pickRandomCandidatesAndDecrement() : IntArray? {

        if (isEmpty()) {
           logger.error{"Vunder called when isEmpty"}
            return intArrayOf()
        }

        val result = if (vunder.voteForN == 1) {
            val voteFor = chooseCandidateAndDecrement(Random.nextInt(vunderLeft))
            when (voteFor) {
                vunder.missingId -> null
                vunder.undervoteId -> intArrayOf()
                else -> intArrayOf(voteFor)
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
    fun chooseCandidateAndDecrement(randomChoice: Int): Int {
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

        return decrementCandidate(idx)
    }

    fun decrementCandidate(candIdx: Int): Int {
        val candidateId = vunderRemaining[candIdx].first
        val nvotesLeft = vunderRemaining[candIdx].second
        if (nvotesLeft > 0) {
            vunderRemaining[candIdx] = Pair(candidateId, nvotesLeft - 1)
            vunderLeft--
        }
        return candidateId
    }

    // select multiple votes over the remaining votes and undervotes
    // can choose multiple undervotes, but no duplicate candidates
    // if novote gets chosen, put back any selected candidates
    fun chooseCandidatesAndDecrement(voteForN: Int): IntArray? {
        var needVotes = voteForN
        val result = mutableListOf<Int>()
        val useRemaining = mutableListOf<Pair<Int, Int>>()
        useRemaining.addAll(vunderRemaining)

        while (needVotes > 0) {
            val (candId, candIdx) = chooseFromRemaining(useRemaining)
            when (candId) {
                vunder.missingId -> {
                    result.forEach { incrementCandidateById(it) } // put any chosen candidates back
                    return null
                }
                vunder.undervoteId -> {
                    result.add(candId)
                    needVotes--
                }
                else -> {
                    result.add(candId)
                    useRemaining.removeAt(candIdx) // remove candidate to prevent duplicates
                    needVotes--
                }
            }
        }
        return result.filter{ it != vunder.undervoteId }.toIntArray()
    }

    // return chosen candidate id and its index into remaining
    private fun chooseFromRemaining(remaining: List<Pair<Int, Int>>) : Pair<Int, Int> {
        val nvotes = remaining.map { it.second }.sum()
        if (nvotes <= 0) {
            // weve run out of votes
            return Pair(vunder.undervoteId, -1)
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

        val candidateId = remaining[idx].first
        decrementCandidateById(candidateId)
        return Pair(candidateId, idx)
    }

    fun decrementCandidateById(candId: Int): Int {
        val idx = vunderRemaining.indexOfFirst { it.first == candId }
        val vunder = vunderRemaining[idx]
        val candidateId = vunder.first
        val nvotesLeft = vunder.second
        if (nvotesLeft > 0) {
            vunderRemaining[idx] = Pair(candidateId, nvotesLeft - 1) // decr and replace
            vunderLeft--
        }
        return candidateId
    }

    fun incrementCandidateById(candId: Int): Int {
        val idx = vunderRemaining.indexOfFirst { it.first == candId }
        val vunder = vunderRemaining[idx]
        val candidateId = vunder.first
        val nvotesLeft = vunder.second
        vunderRemaining[idx] = Pair(candidateId, nvotesLeft + 1) // decr and replace
        vunderLeft++
        return candidateId
    }
}

// combines Vunder for multiple contests into cvrs for one pool
// make cvrs until we exhaust the votes
// this algorithm puts as many contests as possible on each cvr
// the number of cvrs can vary when there are multiple contests

// vunders: contest id -> Vunder
fun makeVunderCvrs(vunders: Map<Int, VunderOld>, poolName: String, poolId: Int?): List<Cvr> {
    val vunderPickers = vunders.mapValues { VunderPickerOld(it.value) }

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

    // find bug
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
            throw RuntimeException("vunderVotes ${vunder.candVotes.toSortedMap()} != ${fromCvrs} voteFromCvrs")
        }
    }

    rcvrs.shuffle()
    return rcvrs
}

private val show = false

fun checkEquivilent(vunder: VunderOld, contestTab: ContestTabulation): Boolean {
    val tabNcards = (contestTab.nvotes() + contestTab.undervotes) / contestTab.voteForN
    val vncards = (vunder.nvotes + vunder.undervotes) / vunder.voteForN + vunder.missing
    var allOk = true
    allOk = allOk && (vunder.nvotes == contestTab.nvotes())
    //allOk = allOk && (vunder.undervotes == contestTab.undervotes)
    //allOk = allOk && (vunder.ncards - vunder.missing == contestTab.ncards())
    allOk = allOk && checkEquivilentVotes(vunder.candVotes, contestTab.votes)
    return allOk
}

fun check(vunders: Map<Int, VunderOld>, rcvrs: List<Cvr>) {
    // find bug
    val voteForNs = vunders.mapValues { it.value.voteForN }
    val votesFromCvrs = tabulateCvrsWithVoteForNs(rcvrs.iterator(), voteForNs)
    votesFromCvrs.forEach { (id, voteFromCvrs) ->
        if (voteFromCvrs.novote > 0)
            print("here")
        /*
        val fromCvrs = voteFromCvrs.votes.toSortedMap()
        val vunder = vunders[id]!!
        if (!checkEquivilentVotes(vunder.candVotes, fromCvrs)) {
            println("candVotes ${vunder.candVotes.toSortedMap()} != ${fromCvrs} voteFromCvrs")
            println("\nvunder ${vunder}")
            println(voteFromCvrs)
            println()
            throw RuntimeException("candVotes ${vunder.candVotes.toSortedMap()} != ${fromCvrs} voteFromCvrs")
        } */
    }
}


