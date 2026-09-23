package org.cryptobiotic.rlauxe.corlaCounty

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.audit.CardPoolBuilder
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.corlaInput.ColoradoInput
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.corlaInput.CorlaCountyInput
import org.cryptobiotic.rlauxe.auditcenter.CorlaCvrConverter
import org.cryptobiotic.rlauxe.corlacvr.RedactedGroup
import org.cryptobiotic.rlauxe.corlacvr.cleanCsvString
import org.cryptobiotic.rlauxe.estimate.VunderPool
import org.cryptobiotic.rlauxe.util.AuditableCardBuilder
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.util.sumContestTabulations
import org.cryptobiotic.rlauxe.util.tabulateCards
import kotlin.math.max
import kotlin.random.Random

private val logger = KotlinLogging.logger("CvrsFromManifest")

// used in CorlaCountyElection
class CvrsFromManifest(
    val variant: ElectionVariant,
    val countyInput: CorlaCountyInput,
    val stateInput: ColoradoInput,
    val infos: Map<Int, ContestInfo>,
    startingPoolId: Int,
) {
    val show = false
    val showLines = false
    val county = countyInput.countyName

    val converter: CorlaCvrConverter
    val infosByName = infos.mapKeys { it.value.name } //  are the cvr names compatible ?
    val convertedCvrs: List<AuditableCard>
    val cvrStyles: List<StyleIF>
    var nextStyleId: Int

    val fakeManifest = countyInput.manifestSource == "fake"
    val manifestIds: ManifestCounts

    val convertedCvrTabs : Map<Int, ContestTabulation>

    // val redactedGroups: List<RedactedGroup>
    val redactedPools = emptyList<CardPool>()
    val redactedTabs = emptyMap<Int, ContestTabulation>()

    init {
        val corlaCvrs = countyInput.readCorlaCvrs()
        // corlaRawCvrs.redactedGroups()

        val manifest = countyInput.readCountyManifest()
        manifestIds = manifest.manifestCounts(corlaCvrs)

        converter = CorlaCvrConverter(countyInput.countyName, corlaCvrs, infosByName, stateInput, startingPoolId)
        cvrStyles = converter.cardStyles.values.toList()
        nextStyleId = startingPoolId + cvrStyles.size

        convertedCvrs = corlaCvrs.cvrs().map {
            converter.convertToCard(it) { cvrb: AuditableCardBuilder ->
                val manifestEntry = manifestIds.match[cvrb.id]
                cvrb.location =
                    if (manifestEntry != null) "$county:${manifestEntry.location()}"
                    else {
                        // we have a cvr without a manifest entry
                        if (cvrb.location != null) "$county:${cvrb.location}"
                        else county
                    }
            }
        }
        convertedCvrTabs = tabulateCards(convertedCvrs.iterator(), infos)
    }

    fun setRedactedNCards(cvrTabs: Map<Int, ContestTabulation>, poolBuilders: List<CardPoolBuilder>) {
        val adjustPool = mutableMapOf<Int, Int>() // poolId, adjust pools
        val undervotePct: Map<Int, Double> = cvrTabs.mapValues { it.value.undervotes() / it.value.ncards().toDouble() }
        poolBuilders.filter{ !it.ncardsAreFixed }.forEach { poolb ->
            var maxCards = 0
            poolb.contestTabs.forEach { (contestId, contestTab) ->
                // assume that the undervote Pct in the redacted Groups is the same as in the unredacted CVRs
                val uvPct = undervotePct[contestId] // its possible there are no cvrs for a contest
                if (uvPct != null) contestTab.undervotes = roundToClosest(uvPct * contestTab.ncards())
                contestTab.ncardsTabulated = contestTab.undervotes + contestTab.nvotes()
                maxCards = max(maxCards, contestTab.ncardsTabulated)
            }
            adjustPool[poolb.poolId] = maxCards - poolb.ncards() // added or subtracted from pool
            poolb.setNcards(maxCards)
        }
        if (!fakeManifest) // TODO make them agree with population
            adjust(poolBuilders.associateBy{ it.poolId }, adjustPool)
    }

    data class PoolAdjustment(val poolId: Int)

    fun adjust(poolBuilderMap: Map<Int, CardPoolBuilder>, adjustPool: Map<Int, Int>) {
        // we want the sum of redacted cards to equal countUnmatched
        val sumCardsBefore = poolBuilderMap.values.sumOf { it.ncards() }
        val adjust = sumCardsBefore - manifestIds.unmatched
        logger.info {"sumCardsBefore=$sumCardsBefore countUnmatched=${manifestIds.unmatched} adjust=$adjust"}

        // adjust by adding or subtracting cards from a random pool
        if (adjust > 0) {
            val adjList = mutableListOf<PoolAdjustment>()
            adjustPool.forEach { (poolId, ncards) ->
                if (ncards > 0) repeat(ncards) { adjList.add(PoolAdjustment(poolId)) }
            }
            repeat (adjust) {
                val randomIdx = Random.nextInt(adjList.size)
                val randomAdj = adjList.get(randomIdx)
                val poolb = poolBuilderMap[randomAdj.poolId]!!
                poolb.setNcards(poolb.ncards() - 1)
                adjList.removeAt(randomIdx)
            }
        } else if (adjust < 0) { // what if its minus ??
            logger.warn{ }
        }

        val sumCardsAfter = poolBuilderMap.values.sumOf { it.ncards() }
        logger.info {"sumCardsAfter=$sumCardsAfter countUnmatched=${manifestIds.unmatched}"}

        // ContestTabulationIF
        //     val votes: MutableMap<Int, Int>  // candidateId -> nvotes
        //    fun ncards(): Int
        //    fun undervotes(): Int
        //    fun nvotes(): Int

    }

    fun tabulateRedactedPools(redactedPools: List<CardPool>): Map<Int, ContestTabulation> {
        val sumTabs = mutableMapOf<Int, ContestTabulation>()
        redactedPools.forEach { pool: CardPool ->
            sumTabs.sumContestTabulations(pool.contestTabs)
        }
        if (show) {
            println("redactedTabulation")
            sumTabs.toSortedMap().forEach { println("  $it") }
        }
        return sumTabs
    }

    private fun convertRedactedToCardPool(redactedGroups: List<RedactedGroup>): List<CardPoolBuilder> {
        return redactedGroups.map { redacted: RedactedGroup ->
            //// the redacted groups dont have undervotes, so we should try to generate reasonable undervote counts
            // but... now we are just setting the vote totals, ignoring ncards and undervotes.

            // val contestTabs = redacted.contestVotes.mapValues{ ContestTabulation(infos[it.key]!!, it.value, ncards=0) }
            val contestTabs: Map<Int, ContestTabulation> = converter.convertToContestTabulation(redacted)

            val name = cleanCsvString(redacted.groupName)
            // TODO this is not cathing names like
            val hasExactContests = !(redacted.groupName.contains("&") || redacted.groupName.contains("and"))

            CardPoolBuilder("$county-${name}R", nextStyleId++, hasExactContests=hasExactContests, infos, contestTabs)
                .setNcards(redacted.ncards()).setNcardsAreFixed(redacted.fixedNcards != null)
        }
    }

    // note that ncards is not set here
    private fun convertRedactedToOneCardPool(redactedGroups: List<RedactedGroup>): CardPoolBuilder {
        val sumTabs = mutableMapOf<Int, ContestTabulation>()
        redactedGroups.forEach { redacted: RedactedGroup ->
            val groupTab: Map<Int, ContestTabulation> = converter.convertToContestTabulation(redacted)
            sumTabs.sumContestTabulations(groupTab)
        }
        return CardPoolBuilder("$county-Redacted", nextStyleId++, hasExactContests=false, infos, sumTabs)
            .setNcards(manifestIds.unmatched)
    }

    // make simulated CVRs for the redacted pools, using entries in the manifest that dont have cvrs
    fun makeSimulatedCards() : List<AuditableCard> { // contestId -> candidateId -> nvotes
        val redactedIter = manifestIds.redactedIds.iterator()
        val rcvrs = mutableListOf<AuditableCard>()
        redactedPools.forEach { cardPool ->
            rcvrs.addAll(makeCardsForOnePool(cardPool, redactedIter))
        }
        logger.debug {"wanted=${manifestIds.unmatched} got=${rcvrs.size} redactedManifestIds is finished = ${!redactedIter.hasNext()}"}
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
            val cvb2 = AuditableCardBuilder(manifestEntry.imprintedId(), "$county:${manifestEntry.location()}", count, 0L, variant.phantoms,
                cardPool.poolId, cardPool.poolId, null, null)
            vunderPool.simulatePooledCard(cvb2) // fill in the vote
            cardsForPool.add(cvb2.build())
            count++
        }

        return cardsForPool
    }

    fun countyCardStyles(): List<StyleIF> = cvrStyles + redactedPools.map { it as StyleIF }
}