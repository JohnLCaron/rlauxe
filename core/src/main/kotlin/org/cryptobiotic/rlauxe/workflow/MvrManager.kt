package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.core.CvrIF
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.persist.CardManifest
import org.cryptobiotic.rlauxe.util.CloseableIterator

// use MvrManager for auditing, not creating an audit
interface MvrManager {
    fun sortedManifest(): CardManifest
    fun pools(): List<CardPool>?
    fun batches(): List<StyleIF>?
    fun makeMvrCardPairsForRound(round: Int): List<Pair<CvrIF, AuditableCard>>  // Pair(mvr, cvr)
    fun writeMvrsForRound(round: Int): Int
    fun auditdir() = "none"
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