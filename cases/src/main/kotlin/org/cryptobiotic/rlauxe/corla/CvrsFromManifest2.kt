package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.corlaInput.ColoradoInput
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.corlaInput.CorlaCountyInput
import org.cryptobiotic.rlauxe.auditcenter.CorlaCvrConverter
import org.cryptobiotic.rlauxe.corlaCounty.ElectionVariant
import org.cryptobiotic.rlauxe.corlaCounty.ManifestCounts
import org.cryptobiotic.rlauxe.corlaCounty.ManifestEntry
import org.cryptobiotic.rlauxe.estimate.VunderPool
import org.cryptobiotic.rlauxe.util.AuditableCardBuilder
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.util.subtractContestTabulationsZ
import org.cryptobiotic.rlauxe.util.tabulateCards
import kotlin.math.max
import kotlin.math.min

private val logger = KotlinLogging.logger("CvrsFromManifest")

// use in CorlaStateElection
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
    val unredactedCvrs = mutableListOf<AuditableCard>()
    val unredactedCvrTabs : Map<Int, ContestTabulation>

    val cvrStyles: List<StyleIF>
    var nextStyleId: Int

    val manifestCounts: ManifestCounts
    val estNcardsByContest: Map<Int, Int>
    val phantomsByContest: Map<Int, Int>

    val redactedPools: List<CardPool>
    val redactedTabs : Map<Int, ContestTabulation>

    init {
        // in order of the CVR file. redacted cvrs are skipped.
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
                unredactedCvrs.add(convertedCvr)
            }
        }
        unredactedCvrTabs = tabulateCards(unredactedCvrs.iterator(), infos)

        // TODO add this calculation to CorlaInput.Counties
        val countyTab = stateInput.countyTabsAllContests()[county]!!
        val convertedCountyTabs: Map<Int, ContestTabulation> = converter.convertToContestTabulation(countyTab)
        val voteDifference: Map<Int, ContestTabulation> = subtractContestTabulationsZ(convertedCountyTabs, unredactedCvrTabs).filter { it.value.nvotes() > 0 }
        estNcardsByContest = estNcardsByContest(unredactedCvrTabs, voteDifference)

        // only use this for phantom variant
        phantomsByContest = voteDifference.mapValues { it.value.ncards() }

        // TODO redacted pools, with possible styles ...
        // put the unmatched manifest entries into a pool, with voteDifference as the pool subtotal
        if (!variant.phantoms && manifestCounts.unmatched > 0) {
            redactedTabs = voteDifference

            val poolId = nextStyleId++
            val onepool = CardPool("$county:UnmatchedManifest", poolId, hasExactContests = false, infos, voteDifference, manifestCounts.unmatched)
            redactedPools = listOf(onepool)

        } else {
            redactedPools = emptyList()
            redactedTabs = emptyMap()
        }
    }

    // make simulated MVRs for the redacted pools, using entries in the manifest that dont have cvrs
    // all cards have the same style, namely all contests with voteDifference > 0; this will make population > Nc
    fun makeSimulatedMvrs() : List<AuditableCard> { // contestId -> candidateId -> nvotes
        if (variant.phantoms) return emptyList()

        val redactedIter = manifestCounts.redactedIds.iterator()
        val rcvrs = mutableListOf<AuditableCard>()
        redactedPools.forEach { cardPool ->
            rcvrs.addAll(makeMvrsForOnePool(cardPool, redactedIter))
        }
        logger.info {"wanted=${manifestCounts.unmatched} got=${rcvrs.size} redactedManifestIds is finished = ${!redactedIter.hasNext()}"}
        return rcvrs
    }

    // make simulated MVRs for one pool, all contests, using the unmatched manifestIds; set styleId, poolId if not sim.
    private fun makeMvrsForOnePool(cardPool: CardPool, redactedIter: Iterator<ManifestEntry>) : List<AuditableCard> { // contestId -> candidateId -> nvotes
        val vunders = cardPool.possibleContests().associate { Pair(it, cardPool.votesAndUndervotes(it)) }.toMap()
        val vunderPool = VunderPool(vunders, cardPool.poolName, cardPool.poolId, cardPool.hasExactContests)
        val cardsForPool = mutableListOf<AuditableCard>()

        var count = 0
        while (redactedIter.hasNext() && count < cardPool.ncards()) {
            val manifestEntry = redactedIter.next()
            val cvb2 = AuditableCardBuilder(
                id = manifestEntry.imprintedId(),
                location = "$county:${manifestEntry.location()}",
                index = count, prn = 0L,
                phantom = false,
                styleId = cardPool.poolId,
                poolId = if (variant.sim) null else cardPool.poolId,
                votesIn = null, style = null)
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
    // are we adding in the unredactedCvrTabs ? cause we have an exact count there
    fun estNcardsByContest(unredactedCvrTabs: Map<Int, ContestTabulation>, voteDifference: Map<Int, ContestTabulation>): Map<Int, Int> {
        val ncardsByContest = mutableMapOf<Int, Int>() // contestId, adjust
        unredactedCvrTabs.forEach { ncardsByContest[it.key] = it.value.ncards() }

        // estimate redacted cards by assuming that the undervote Pct in the redacted Groups is the same as in the unredacted CVRs
        val undervotePct: Map<Int, Double> = unredactedCvrTabs.mapValues {
            // assume that the undervote Pct in the redacted Groups is the same as in the unredacted CVRs
            if (it.value.nvotes() == 0) 0.0 else it.value.undervotes() / it.value.nvotes().toDouble()
        }

        voteDifference.forEach { (id, tab) ->
            val pct = undervotePct[id] ?: 0.1  // what if there are no cvrs for this contest ?
            // but it cant use more cards then there are
            // (tab.nvotes + tab.undervotes) / tab.voteForN < totalCvrs
            // tab.nvotes(1 + pct) < (totalCvrs * tab.voteForN)
            val maxPct = (manifestCounts.totalEntries * tab.voteForN) / tab.nvotes().toDouble() - 1.0
            val usePct = max(0.0, min(pct, maxPct))

            // we are munging redactedTab directly
            tab.undervotes = roundToClosest(tab.nvotes() * usePct)
            tab.ncardsTabulated = roundToClosest((tab.nvotes() + tab.undervotes) / tab.voteForN.toDouble())
            val accum = ncardsByContest.getOrDefault(id, 0)
            ncardsByContest[id] = tab.ncardsTabulated + accum
        }
        return ncardsByContest
    }
}