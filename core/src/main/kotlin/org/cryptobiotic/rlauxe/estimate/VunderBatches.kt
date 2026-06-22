package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.audit.CountyPools
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.util.AuditableCardBuilder
import org.cryptobiotic.rlauxe.util.ContestTabulation

// Vunder: One Contest, one pool
// data class Vunder(val contestId: Int, val poolId: Int?, val voteCounts: List<Pair<IntArray, Int>>, val undervotes: Int, val missing: Int, val voteForN: Int) {

// VunderPool: multiple contests, one pool
// class VunderPool(val vunders: Map<Int, Vunder>, val poolName: String, val poolId: Int, val hasExactContests: Boolean) {

// VunderPools: multiple contests, multiple pools; pass in cards that have a poolId to say what pool to use
// class VunderPools(pools: List<CardPool>) {

// for multiple styles, multiple contests and one "pool" of subtotaled votes
// used for estimation for OneAudit; generating cards for CountyElectionSansCvrs; genererating cards for redacted pools
class VunderBatches(styles: List<StyleIF>, val onePool: VunderPool) {
    val styleMap = styles.associateBy { it.id() }

    // for the given pooled card with no votes, simulate one with votes, using card.styleName
    fun simulatePooledCard(card: AuditableCard): AuditableCard {
        if (card.phantom()) return card

        val style = styleMap[card.styleId]
        val cardb = AuditableCardBuilder.fromCard(card)

        if (style == null) {
            println("style ${card.styleId} not found")
            return cardb.build()
        }

        style.possibleContests().forEach { contestId ->
            val vunderPicker = onePool.vunderPickers[contestId]
            // only contests still needed to audit are in OnePool
            if (vunderPicker != null) {
                if (vunderPicker.isEmpty()) {
                    if (style.hasExactContests()) cardb.replaceContestVotes(
                        contestId,
                        intArrayOf()
                    ) // missing not allowed
                } else {
                    val cands = vunderPicker.pickRandomCandidatesAndDecrement()
                    if (cands != null) {
                        cardb.replaceContestVotes(contestId, cands) // ok if no contests on it ??
                    }
                }
            }
        }
        return cardb.build()
    }
}

// from CountyElectionSansCvrs
// use VunderBatches to constrain the votes to the CountyPools Tabulation
class CvrIteratorfromCountyPools(val countyPool: CountyPools, val startCardno: Int) : Iterator<AuditableCard> {
    val vunderBatches: VunderBatches // tracks all the cvrs for this county
    var cardPoolIter = countyPool.styles.iterator()
    var innerIter = CardsFromStyle(cardPoolIter.next())
    var cardno = startCardno

    init {
        // use tab ncards as npop
        val vunders =
            countyPool.contestTabs.mapValues { it.value.votesAndUndervotes(null, it.value.ncards(), true) }
        val onePool = VunderPool(vunders, countyPool.countyName, countyPool.countyPoolId, true)
        vunderBatches = VunderBatches(countyPool.styles, onePool)
    }

    override fun next(): AuditableCard {
        return innerIter.next()
    }

    override fun hasNext(): Boolean {
        if (innerIter.hasNext()) return true
        if (cardPoolIter.hasNext()) {
            innerIter = CardsFromStyle(cardPoolIter.next())
            return hasNext()
        }
        // should be all done with this CountyPool
        println("done with ${countyPool.countyName} wrote ${cardno - startCardno} cards")
        vunderBatches.onePool.vunderPickers.values.forEach { picker ->
            if (picker.isNotEmpty()) {
                print("  ${picker.vunder.contestId} -> ")
                picker.vunderRemaining.forEach { choice ->
                    if (choice.remaining > 0) print("cand=${choice.cands.contentToString()}: ${choice.remaining}, ")
                }
                println()
            }
        }
        return false
    }

    // create cardPool.ncards() cards of the given style; constrain the votes to a common batch
    inner class CardsFromStyle(val cardPool: StyleIF) : Iterator<AuditableCard> {
        var countCards = 0
        val poolName = cardPool.name()

        override fun next(): AuditableCard {
            countCards++
            val card = AuditableCard.empty(id = "${poolName}.index-${cardno++}", phantom = false, styleId=cardPool.id())
            return vunderBatches.simulatePooledCard(card)
        }

        override fun hasNext() = countCards < cardPool.ncards()
    }
}

fun simulateCards(cardPool: CardPool, startCardno: Int = 0): List<AuditableCard> {
    val cardIter = CvrIteratorFromCardPool(cardPool, startCardno)
    val cards = mutableListOf<AuditableCard>()
    cardIter.forEach { cards.add(it) }
    return cards
}

// use VunderBatches to constrain the votes to the cardPool Tabulations
class CvrIteratorFromCardPool(val cardPool: CardPool, val startCardno: Int) : Iterator<AuditableCard> {
    val vunderBatches: VunderBatches // tracks all the cvrs for this county
    var cardno = startCardno
    val poolName = cardPool.name()
    var countCards = 0

    init {
        // use tab ncards as npop
        val vunders =
            cardPool.contestTabs.mapValues { it.value.votesAndUndervotes(null, it.value.ncards(), true) }
        val onePool = VunderPool(vunders, cardPool.poolName, cardPool.poolId, true)
        vunderBatches = VunderBatches(listOf(cardPool), onePool)
    }

    override fun next(): AuditableCard {
        countCards++
        val card = AuditableCard.empty(id = "${poolName}.index-${cardno++}", phantom = false, styleId=cardPool.id())
        return vunderBatches.simulatePooledCard(card)
    }

    override fun hasNext(): Boolean {
        val more = countCards < cardPool.ncards()
        if (!more) {
            // should be all done with this CountyPool
            if (show) println("done with ${cardPool.poolName} wrote ${cardno - startCardno} cards")
            vunderBatches.onePool.vunderPickers.values.forEach { picker ->
                if (picker.isNotEmpty()) {
                    val mess = buildString {
                        append("  ${picker.vunder.contestId} -> ")
                        picker.vunderRemaining.forEach { choice ->
                            if (choice.remaining > 0) append("cand=${choice.cands.contentToString()}: ${choice.remaining}, ")
                        }
                        appendLine()
                    }
                    logger.info{"picker for contest ${picker.vunder.contestId} didnt finish\n$mess"}
                }
            }
        }
        return more
    }
}

private val logger = KotlinLogging.logger("VunderBatches")
private val show = false