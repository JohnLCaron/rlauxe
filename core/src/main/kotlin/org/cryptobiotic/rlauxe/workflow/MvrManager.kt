package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.PopulationIF
import org.cryptobiotic.rlauxe.core.CvrIF
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.CloseableIterable

private val logger = KotlinLogging.logger("MvrManager")

class CardManifest(val cards: CloseableIterable<AuditableCard>, val ncards: Int, val populations: List<PopulationIF>) {
    val popMap = populations.associateBy{ it.id() }
    fun population(populationId: Int) = popMap[populationId]

    companion object {
        fun createFromList(cards: List<AuditableCard>, populations: List<PopulationIF>?) : CardManifest {
            return CardManifest(CloseableIterable { cards.iterator() }, cards.size, populations ?: emptyList())
        }
    }
}

// use MvrManager for auditing, not creating an audit
interface MvrManager {
    fun cardManifest(): CardManifest
    // fun sortedCards(): CloseableIterable<AuditableCard>  // most uses will just need the first n samples
    fun oapools(): List<OneAuditPoolFromCvrs>?
    // fun populations(): List<PopulationIF>?
    fun makeMvrCardPairsForRound(round: Int): List<Pair<CvrIF, AuditableCard>>  // Pair(mvr, cvr)
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