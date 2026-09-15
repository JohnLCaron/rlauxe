package org.cryptobiotic.rlauxe.corlaCounty

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.corlaInput.ColoradoInput
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.corlaInput.CorlaCountyInput
import org.cryptobiotic.rlauxe.corlaInput.ManifestCounts
import org.cryptobiotic.rlauxe.corlaInput.ManifestEntry
import org.cryptobiotic.rlauxe.corlacvr.CorlaCvrConverter
import org.cryptobiotic.rlauxe.estimate.VunderPool
import org.cryptobiotic.rlauxe.util.AuditableCardBuilder
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.util.subtractContestTabulationsZ
import org.cryptobiotic.rlauxe.util.tabulateCards
import kotlin.math.max
import kotlin.math.min

private val logger = KotlinLogging.logger("CvrsFromManifest")

class CvrsFromManifest2(
    val variant: ElectionVariant,
    val countyInput: CorlaCountyInput,
    val stateInput: ColoradoInput,
    val infos: Map<Int, ContestInfo>,
    startingStyleId: Int,
) {
    val show = false
    val county = countyInput.countyName

    val converter: CorlaCvrConverter
    val convertedCvrs = mutableListOf<AuditableCard>()
    val convertedCvrTabs : Map<Int, ContestTabulation>

    val cvrStyles: List<StyleIF>
    var nextStyleId: Int

    val manifestCounts: ManifestCounts
    val estNcardsByContest: Map<Int, Int>
    val phantomsByContest: Map<Int, Int>

    val redactedPools: List<CardPool>
    val redactedTabs : Map<Int, ContestTabulation>
    // val redactedCards = mutableListOf<AuditableCard>()

    init {
        val corlaCvrs = countyInput.readCorlaCvrs()

        val manifest = countyInput.readCountyManifest()
        manifestCounts = manifest.manifestCounts(corlaCvrs)

        val infosByName = infos.mapKeys { it.value.name } //  canonical names
        converter = CorlaCvrConverter(countyInput.countyName, corlaCvrs, infosByName, stateInput, startingStyleId)
        cvrStyles = converter.cardStyles.values.toList()
        nextStyleId = startingStyleId + cvrStyles.size

        corlaCvrs.cvrs().map {
            val manifestEntry = manifestCounts.match[it.imprintedId]
            if (manifestEntry != null) { // must be in the manifest
                val convertedCvr = converter.convertToCard(it) { cvrb: AuditableCardBuilder ->
                    cvrb.location = "$county:${manifestEntry.location()}"
                }
                convertedCvrs.add(convertedCvr)
            }
        }
        convertedCvrTabs = tabulateCards(convertedCvrs.iterator(), infos)

        val countyTab = stateInput.countyTabsAllContests()[county]!!
        val convertedCountyTabs: Map<Int, ContestTabulation> = converter.convertToContestTabulation(countyTab)
        val voteDifference = subtractContestTabulationsZ(convertedCountyTabs, convertedCvrTabs)
        estNcardsByContest = estNcardsByContest(convertedCvrTabs, voteDifference)
        phantomsByContest = voteDifference.mapValues { it.value.ncards() }

        if (!variant.phantoms && manifestCounts.unmatched > 0) {
            redactedTabs = voteDifference

            val poolId = nextStyleId++
            val onepool = CardPool("$county:RedactedPool", poolId, false, infos, voteDifference, manifestCounts.unmatched)
            redactedPools = listOf(onepool)

            /* problem is that we need simulated mvr, then remove the votes in the card
            manifestCounts.redactedIds.forEach { manifestEntry ->
                val card = AuditableCard(manifestEntry.imprintedId(), "$county:${manifestEntry.location()}",
                    0, 0L, phantom=true, styleId = poolId,
                    IntArray(0), IntArray(0),IntArray(0), poolId)
                redactedCards.add(card)
            } */
        } else {
            redactedPools = emptyList()
            redactedTabs = emptyMap()
        }
    }

    // make simulated MVRs for the redacted pools, using entries in the manifest that dont have cvrs
    fun makeSimulatedMvrs() : List<AuditableCard> { // contestId -> candidateId -> nvotes
        if (variant.phantoms) return emptyList()

        val redactedIter = manifestCounts.redactedIds.iterator()
        val rcvrs = mutableListOf<AuditableCard>()
        redactedPools.forEach { cardPool ->
            rcvrs.addAll(makeCardsForOnePool(cardPool, redactedIter))
        }
        logger.info {"wanted=${manifestCounts.unmatched} got=${rcvrs.size} redactedManifestIds is finished = ${!redactedIter.hasNext()}"}
        return rcvrs
    }

    // make simulated CVRs for one pool, all contests, using the unmatched manifestIds
    private fun makeCardsForOnePool(cardPool: CardPool, redactedIter: Iterator<ManifestEntry>) : List<AuditableCard> { // contestId -> candidateId -> nvotes
        val vunders = cardPool.possibleContests().associate { Pair(it, cardPool.votesAndUndervotes(it)) }.toMap()
        val vunderPool = VunderPool(vunders, cardPool.poolName, cardPool.poolId, cardPool.hasExactContests)
        val cardsForPool = mutableListOf<AuditableCard>()

        var count = 0
        while (redactedIter.hasNext() && count < cardPool.ncards()) {
            val manifestEntry = redactedIter.next()
            val cvb2 = AuditableCardBuilder(manifestEntry.imprintedId(), "$county:${manifestEntry.location()}", count, 0L, false,
                cardPool.poolId,
                if (variant.sim) null else cardPool.poolId,
                null, null)
            vunderPool.simulatePooledCard(cvb2) // fill in the vote
            cardsForPool.add(cvb2.build())
            count++
        }

        return cardsForPool
    }

    fun countyCardStyles(): List<StyleIF> = cvrStyles + redactedPools.map { it as StyleIF }

    ///////////////////////////////////////////////////////////////////////////
    // TODO sometimes we know the redacted styles, so can calculate ncards directlry

    // meanwhile, down the rabbit hole again
    // estimate ncards(county, contest), return contestId -> ncards
    fun estNcardsByContest(cvrTabs: Map<Int, ContestTabulation>, redactedTab: Map<Int, ContestTabulation>): Map<Int, Int> {
        val undervotePct: Map<Int, Double> = cvrTabs.mapValues {
            // assume that the undervote Pct in the redacted Groups is the same as in the unredacted CVRs
            if (it.value.nvotes() == 0) 0.0 else it.value.undervotes() / it.value.nvotes().toDouble()
        }

        val ncardsByContest = mutableMapOf<Int, Int>() // contestId, adjust

        redactedTab.forEach { (id, tab) ->
            val pct = undervotePct[id] ?: 0.1  // what if there are no cvrs for this contest ?
            // but it cant use more cards then there are
            // (tab.nvotes + tab.undervotes) / tab.voteForN < totalCvrs
            // tab.nvotes(1 + pct) < (totalCvrs * tab.voteForN)
            val maxPct = (manifestCounts.totalEntries * tab.voteForN) / tab.nvotes().toDouble() - 1.0
            val usePct = max(0.0, min(pct, maxPct))

            // we are munging redactedTab directly
            tab.undervotes = roundToClosest(tab.nvotes() * usePct)
            tab.ncardsTabulated = roundToClosest((tab.nvotes() + tab.undervotes) / tab.voteForN.toDouble())
            ncardsByContest[id] = tab.ncardsTabulated + (cvrTabs[id]?.ncards() ?: 0)
        }
        return ncardsByContest
    }
}