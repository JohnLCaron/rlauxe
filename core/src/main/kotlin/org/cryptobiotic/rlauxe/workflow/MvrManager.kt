package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.audit.PopulationIF
import org.cryptobiotic.rlauxe.core.CvrIF
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.CloseableIterable

private val logger = KotlinLogging.logger("MvrManager")

// use MvrManager for auditing, not creating an audit
interface MvrManager {
    fun sortedCards(): CloseableIterable<AuditableCard>  // most uses will just need the first n samples
    fun populations(): List<PopulationIF>?
    fun makeMvrCardPairsForRound(round: Int): List<Pair<CvrIF, AuditableCard>>  // Pair(mvr, cvr)

    fun oapools(): List<OneAuditPoolIF>? {
        val pop = populations()
        if (pop != null && pop.size > 0 && pop[0] is OneAuditPoolIF) {
            return pop as List<OneAuditPoolIF>
        }
        return null
    }

}

// when the MvrManager supplies the audited mvrs, its a test
// calling this sets the internal state used by makeMvrCardPairsForRound()
interface MvrManagerTestIF : MvrManager {
    fun setMvrsBySampleNumber(sampleNumbers: List<Long>, round: Int): List<AuditableCard>
}

////////////////////////////////////////////////////////////

// TODO add ErrorMessages ?
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

//// TODO  this is a lot of trouble to calculate prevContestCounts; we only need it if contest.auditorWantNewMvrs has been set
// for each contest, return map contestId -> wantSampleSize. used in ConsistentSampling
fun wantSampleSize(contestsNotDone: List<ContestRound>, previousSamples: Set<Long>, sortedCards : CloseableIterator<AuditableCard>, debug: Boolean = false): Map<Int, Int> {
    //// count how many samples each contest already has
    val prevContestCounts = mutableMapOf<ContestRound, Int>()
    contestsNotDone.forEach { prevContestCounts[it] = 0 }

    // Note this iterates through sortedCards only until all previousSamples have been found and counted
    sortedCards.use { cardIter ->
        previousSamples.forEach { prevNumber ->
            while (cardIter.hasNext()) {
                val card = cardIter.next() // previousSamples must be in same order as sortedBorc
                if (card.prn == prevNumber) {
                    contestsNotDone.forEach { contest ->
                        if (card.hasContest(contest.id)) {
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