package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.*

private val debugWantSampleSize = false

// TODO can we get rid of this in favor of AuditableCard?
interface BallotOrCvr {
    fun hasContest(contestId: Int): Boolean
    fun sampleNumber(): Long
    fun index(): Int
}

interface MvrManager {
    fun ballotCards() : Iterator<BallotOrCvr>

    // this is where you would add the real mvrs
    fun setMvrsForRound(mvrs: List<AuditableCard>)

    //// for uniformSampling
    fun takeFirst(nmvrs: Int): List<BallotOrCvr> {
        val result = mutableListOf<BallotOrCvr>()
        val ballotCardsIter = ballotCards()
        while (ballotCardsIter.hasNext() && result.size < nmvrs) {
            result.add(ballotCardsIter.next())
        }
        return result
    }
    fun Nballots(contestUA: ContestUnderAudit): Int // TODO where does this come from ?
}

interface MvrManagerClcaIF : MvrManager {
    // this is used for audit, not estimation
    fun makeCvrPairsForRound(): List<Pair<Cvr, Cvr>>
}

interface MvrManagerPollingIF : MvrManager {
    // this is used for audit, not estimation
    fun makeSampler(contestId: Int, hasStyles: Boolean, assorter: AssorterIF, allowReset: Boolean): Sampler
}

interface MvrManagerTest : MvrManager {
    fun setMvrsForRoundIdx(roundIdx: Int): List<AuditableCard>
    fun setMvrsBySampleNumber(sampleNumbers: List<Long>): List<AuditableCard>
}

////////////////////////////////////////////////////////////

// Iterate through sortedCvrUAs to find the cvrUAs that match the sampleNumbers
// sampleNumbers must in same order as sortedCvrUAs
// Note this iterates through sortedCvrUAs only until all sampleNumbers have been found
fun findSamples(sampleNumbers: List<Long>, sortedCvrUAs: Iterator<AuditableCard>): List<AuditableCard> {
    val result = mutableListOf<AuditableCard>()
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