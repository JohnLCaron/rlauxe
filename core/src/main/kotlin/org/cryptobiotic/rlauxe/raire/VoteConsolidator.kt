package org.cryptobiotic.rlauxe.raire

import au.org.democracydevelopers.raire.irv.Vote
import kotlin.collections.getOrPut

/*
class Vote(
    val n: Int, // The number of votes that expressed the ranking 'prefs' on their ballot.
    val prefs: IntArray, // A preference ranking. Note that prefs[0] denotes the first (highest) ranked candidate.
)
 */

/**
 * For a single Contest.
 *
 * A utility class for building an array of Vote[] structures
 * from provided preference lists. The main purpose is to convert
 * a large number of weight votes, possibly the same, into a
 * set of unique votes with multiplicities.
 *
 * It is also (optionally) capable of converting a preference list of
 * strings into the array of integer preferences used by Raire.
 */
class VoteConsolidator {
    private val preferences = mutableMapOf<HashableIntArray, Int>()

    fun addVote(pref: IntArray) {
        val key = HashableIntArray(pref)
        preferences[key] = preferences.getOrPut(key) { 0 } + 1
    }

    fun makeVotes(): Array<Vote> {
        val voteList = preferences.map { Vote(it.value, it.key.array) }
        return Array<Vote>(voteList.size) { voteList[it] }
    }
}

/** A wrapper around int[] that works as a key in a hash map  */
private class HashableIntArray(val array: IntArray) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as HashableIntArray
        return array.contentEquals(that.array)
    }

    override fun hashCode(): Int {
        return array.contentHashCode()
    }
}
