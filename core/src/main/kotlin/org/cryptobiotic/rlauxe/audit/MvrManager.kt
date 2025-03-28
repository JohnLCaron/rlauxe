package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.Prng

private val debugWantSampleSize = false

interface BallotOrCvr {
    fun hasContest(contestId: Int): Boolean
    fun sampleNumber(): Long
    fun index(): Int
}

interface MvrManager {
    fun ballotCards() : Iterator<BallotOrCvr>
    fun setMvrsForRound(mvrs: List<CvrUnderAudit>)
    fun setMvrsForRoundIdx(roundIdx: Int): List<CvrUnderAudit>
    fun takeFirst(nmvrs: Int): List<BallotOrCvr> {
        val result = mutableListOf<BallotOrCvr>()
        while (ballotCards().hasNext() && result.size < nmvrs) {
            result.add(ballotCards().next())
        }
        return result
    }
}

interface MvrManagerClcaIF : MvrManager {
    // this is used for audit, not estimation
    fun makeSampler(contestId: Int, hasStyles: Boolean, cassorter: ClcaAssorterIF, allowReset: Boolean = false): Sampler
}

interface MvrManagerPollingIF : MvrManager {
    // used in uniformSampling TODO bogus i think
    fun Nballots(): Int
    // this is used for audit, not estimation
    fun makeSampler(contestId: Int, hasStyles: Boolean, assorter: AssorterIF, allowReset: Boolean): Sampler
}

// Iterate through sortedCvrUAs to find the cvrUAs that match the sampleNumbers
// sampleNumbers must in same order as sortedCvrUAs
// Note this iterates through sortedCvrUAs only until all sampleNumbers have been found
fun findSamples(sampleNumbers: List<Long>, sortedCvrUAs: Iterator<CvrUnderAudit>): List<CvrUnderAudit> {
    val result = mutableListOf<CvrUnderAudit>()
    sampleNumbers.forEach { sampleNum ->
        while (sortedCvrUAs.hasNext()) {
            val boc = sortedCvrUAs.next()
            if (boc.sampleNumber() == sampleNum) {
                result.add(boc)
                break
            }
        }
    }
    require(result.size == sampleNumbers.size)
    return result
}

interface MvrManagerTest : MvrManager {
    fun setMvrsBySampleNumber(sampleNumbers: List<Long>): List<CvrUnderAudit>
}

fun createSortedCvrs(cvrs: List<Cvr>, seed: Long) : List<CvrUnderAudit> {
    val prng = Prng(seed)
    return cvrs.mapIndexed { idx, it -> CvrUnderAudit(it, idx, prng.next()) }.sortedBy { it.sampleNumber() }
}

//// TODO this is a lot of trouble to calculate prevContestCounts; we only need it if contest.auditorWantNewMvrs has been set
// for each contest, return map contestId -> wantSampleSize
fun wantSampleSize(contestsNotDone: List<ContestRound>, previousSamples: Set<Long>, sortedBorc : Iterator<BallotOrCvr>): Map<Int, Int> {
    //// count how many samples each contest already has
    val prevContestCounts = mutableMapOf<ContestRound, Int>()
    contestsNotDone.forEach { prevContestCounts[it] = 0 }

    // Note this iterates through sortedBorc only until all previousSamples have been found and counted
    val sortedBorcIter = sortedBorc
    previousSamples.forEach { prevNumber ->
        while (sortedBorcIter.hasNext()) {
            val boc = sortedBorcIter.next() // previousSamples must be in same order as sortedBorc
            if (boc.sampleNumber() == prevNumber) {
                contestsNotDone.forEach { contest ->
                    if (boc.hasContest(contest.id)) {
                        prevContestCounts[contest] = prevContestCounts[contest]?.plus(1) ?: 1
                    }
                }
                break
            }
        }
    }
    if (debugWantSampleSize) {
        val prevContestCountsById = prevContestCounts.entries.map { it.key.id to it.value }.toMap()
        println("**wantSampleSize prevContestCountsById = ${prevContestCountsById}")
    }

    // we need prevContestCounts in order to calculate wantSampleSize if contest.auditorWantNewMvrs has been set
    val wantSampleSizeMap = prevContestCounts.entries.map { it.key.id to it.key.wantSampleSize(it.value) }.toMap()
    if (debugWantSampleSize) println("**wantSampleSize = $wantSampleSizeMap")

    return wantSampleSizeMap
}