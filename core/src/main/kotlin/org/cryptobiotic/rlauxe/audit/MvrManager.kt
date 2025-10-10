package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.CloseableIterable

private val logger = KotlinLogging.logger("MvrManager")

interface MvrManager {
    // either Cvrs (clca) or CardLocations (polling) or both (oneaudit)
    fun sortedCards(): CloseableIterable<AuditableCard>
    fun sortedCvrs(): CloseableIterable<Cvr> = CloseableIterable { CvrIteratorCloser(sortedCards().iterator()) }

    //// for uniformSampling
    fun takeFirst(nmvrs: Int): List<AuditableCard> {
        val result = mutableListOf<AuditableCard>()
        val ballotCardsIter = sortedCards().iterator()
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
    // this is used for audit, not estimation. need List so we can do mvrs[permutedIndex[idx]]
    fun makeMvrsForRound(): List<Cvr>
}

// when the MvrManager supplies the audited mvrs, its a test
// calling this sets the internal state used by makeCvrPairsForRound(), makeMvrsForRound()
interface MvrManagerTest : MvrManager {
    fun setMvrsBySampleNumber(sampleNumbers: List<Long>): List<AuditableCard>
}

////////////////////////////////////////////////////////////

// Iterate through sortedCards to find the AuditableCard that match the samplePrns
// samplePrns must be in the same order as sortedCards
// Note this iterates through sortedCards only until all samplePrns have been found
fun findSamples(samplePrns: List<Long>, sortedCards: CloseableIterator<AuditableCard>): List<AuditableCard> {
    val result = mutableListOf<AuditableCard>()
    sortedCards.use { cardIter ->
        samplePrns.forEach { sampleNum ->
            while (cardIter.hasNext()) {
                val card = cardIter.next()
                if (card.prn == sampleNum) {
                    result.add(card)
                    break
                }
            }
        }
    }
    require(result.size == samplePrns.size)
    return result
}

//// TODO this is a lot of trouble to calculate prevContestCounts; we only need it if contest.auditorWantNewMvrs has been set
// for each contest, return map contestId -> wantSampleSize
fun wantSampleSize(contestsNotDone: List<ContestRound>, previousSamples: Set<Long>, sortedCards : CloseableIterator<AuditableCard>, debug: Boolean = false): Map<Int, Int> {
    //// count how many samples each contest already has
    val prevContestCounts = mutableMapOf<ContestRound, Int>()
    contestsNotDone.forEach { prevContestCounts[it] = 0 }

    // Note this iterates through sortedBorc only until all previousSamples have been found and counted
    sortedCards.use { cardIter ->
        previousSamples.forEach { prevNumber ->
            while (cardIter.hasNext()) {
                val card = cardIter.next() // previousSamples must be in same order as sortedBorc
                if (card.prn == prevNumber) {
                    contestsNotDone.forEach { contest ->
                        if (card.hasContest(contest.id)) { // TODO assumes hasStyles = true
                            prevContestCounts[contest] = prevContestCounts[contest]?.plus(1) ?: 1
                        }
                    }
                    break
                }
            }
        }
    }
    if (debug) {
        val prevContestCountsById = prevContestCounts.entries.associate { it.key.id to it.value }
        logger.debug{"**wantSampleSize prevContestCountsById = $prevContestCountsById"}
    }
    // we need prevContestCounts in order to calculate wantSampleSize if contest.auditorWantNewMvrs has been set
    val wantSampleSizeMap = prevContestCounts.entries.associate { it.key.id to it.key.wantSampleSize(it.value) }
    if (debug) logger.debug{"wantSampleSize = $wantSampleSizeMap"}

    return wantSampleSizeMap
}