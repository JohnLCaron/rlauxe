package org.cryptobiotic.rlauxe.corlaCounty

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.audit.CardPoolBuilder
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.corlaInput.ColoradoInput
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.corlaInput.CorlaCountyInput
import org.cryptobiotic.rlauxe.cvr.CorlaCvrConverter
import org.cryptobiotic.rlauxe.cvr.CvrRow
import org.cryptobiotic.rlauxe.cvr.RedactedGroup
import org.cryptobiotic.rlauxe.cvr.cleanCsvString
import org.cryptobiotic.rlauxe.estimate.VunderPool
import org.cryptobiotic.rlauxe.util.AuditableCardBuilder
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.util.sumContestTabulations
import org.cryptobiotic.rlauxe.util.tabulateCards
import kotlin.math.max
import kotlin.random.Random

private val logger = KotlinLogging.logger("CvrsFromManifest")


class CvrsFromManifest(
    val variant: ElectionVariant,
    val countyInput: CorlaCountyInput,
    val stateInput: ColoradoInput,
    val infos: Map<Int, ContestInfo>,
    val stateElection: Boolean,
) {
    val show = false
    val county = countyInput.countyName

    val converter: CorlaCvrConverter
    val infosByName = infos.mapKeys { it.value.name } //  are the cvr names compatible ?
    val convertedCvrs: List<AuditableCard>
    val cvrStyles: List<StyleIF>
    var nextStyleId: Int

    val fakeManifest = countyInput.manifestSource == "fake"
    val manifestIds: ManifestIds

    val convertedCvrTabs : Map<Int, ContestTabulation>

    val redactedGroups: List<RedactedGroup>
    val redactedTabs : Map<Int, ContestTabulation>
    val redactedPools: List<CardPool>

    init {
        val corlaCvrs = countyInput.readCorlaCvrs()
        redactedGroups = corlaCvrs.redactedGroups()

        /* if (fakeManifest) { // TODO
            manifestIds = emptyList()
        } else {
            countyInput.readCountyManifest().forEach { batch ->
                repeat(batch.nballotCards) { recordId ->
                    val want = "${batch.tabulatorNum}-${batch.batchId}-${recordId + 1}"
                    manifestIdMap[want] = ManifestId(want, batch.location)
                }
            }
            manifestIds = manifestIdMap.values.toList()
        }
        manifestSize = manifestIds.size */

        manifestIds = if (fakeManifest) fakeManifestMatch(corlaCvrs.cvrs().size) else manifestMatch(corlaCvrs.cvrs())

        converter = CorlaCvrConverter(countyInput.countyName, corlaCvrs, infosByName, stateInput)
        cvrStyles = converter.cardStyles.values.toList()
        nextStyleId = cvrStyles.maxOf { it.id } + 1

        convertedCvrs = corlaCvrs.cvrs().map {
            converter.convertToCard(it) { cvrb:AuditableCardBuilder ->
                val correctedId = reverseMunge(cvrb.id)
                cvrb.id = if (stateElection) "$county:${correctedId}" else correctedId
                val manifestEntry = manifestIds.match[correctedId]
                cvrb.location =
                    if (manifestEntry != null) "$county:${manifestEntry.location}"
                    else {
                        // we have a cvr without a manifest entry
                        if (cvrb.location != null) "$county:${cvrb.location}"
                        else county
                    }
            }
        }

        /* convertedCvrs.forEach { card ->
            val manifestMatch = manifestIdMap[card.id]
            if (manifestMatch != null) {
                if (manifestMatch.card != null) countDup++
                manifestMatch.card = card
            } else
                countMiss++
        }

        countUnmatched = manifestIdMap.values.count { it.card == null }
        logger.info{"$county: countMiss=$countMiss countUnmatched=$countUnmatched countDup=$countDup"} */

        tabulateRedactedGroups()
        val redactedPoolBuilders = makeRedactedPools(variant)

        convertedCvrTabs = tabulateCards(convertedCvrs.iterator(), infos)
        redactedTabs = tabulateRedactedGroups()

        // set ncards for each pool; when CardPool is built, the contestTabs are reset accordingly
        if (redactedPoolBuilders.size == 1) {
            redactedPoolBuilders.first().setNcards(manifestIds.unmatched)
        } else {
            setRedactedNCards(convertedCvrTabs, redactedPoolBuilders)
        }

        redactedPools = redactedPoolBuilders.map { it.build() }
        if (show) {
            println("redactedPools")
            redactedPools.forEach { pool ->
                println("  ${pool.poolName}")
                pool.contestTabs.toSortedMap().forEach {
                    println("    $it")
                }
            }
        }
    }

    // id matches the imprintedId, location is the manifest location field
    data class ManifestId(val id: String, val location: String) {
        var matched = false // did we find a match yet?
    }

    data class ManifestIds(
        val unmatched: Int,                    // count of Manifest entries not in the Cvrs; presumed to be == redacted CVRs
        val match: Map<String, ManifestId>, // imprintedId -> ManifestId
        val redactedIds: List<ManifestId>      // didnt match cvr, assume to be in the redactions
    )

    fun manifestMatch(cvrs: List<CvrRow>): ManifestIds {

        val manifestIdMap = mutableMapOf<String, ManifestId>()
        countyInput.readCountyManifest().forEach { batch ->
            repeat(batch.nballotCards) { recordId ->
                val want = "${batch.tabulatorNum}-${batch.batchId}-${recordId + 1}"
                manifestIdMap[want] = ManifestId(want, batch.location)
            }
        }

        var countMiss = 0 // count of Cvrs not in the manifest
        var countDup = 0  // count of duplicate ids in the Cvrs
        cvrs.forEach { card ->
            " 9/1/1986 -> 9-1-1986 jeesh!"
            val correctedId = reverseMunge(card.imprintedId)
            val manifestMatch = manifestIdMap[correctedId]
            if (manifestMatch != null) {
                if (manifestMatch.matched) countDup++
                manifestMatch.matched = true
            } else {
                countMiss++
            }
        }

        var unmatched = 0
        val redactedIDs = mutableListOf<ManifestId>()
        manifestIdMap.values.forEach { mid ->
            if (!mid.matched) {
                redactedIDs.add(mid)
                unmatched++
            }
        }

        logger.info{"$county: countMiss=$countMiss countUnmatched=$unmatched countDup=$countDup"}
        return ManifestIds(unmatched, manifestIdMap, redactedIDs)
    }

    fun reverseMunge(id: String): String {
        val count = id.count { it == '/' }
        return if (count == 2) id.replace('/', '-') else id
    }

    fun fakeManifestMatch(ncvrs: Int): ManifestIds {
        val population = countyInput.countyPopulation()
        val unmatched = population - ncvrs
        val redactedIds = List(unmatched) {
            val idx = it + 1
            ManifestId("redactedballot$idx", "location$idx")
        }
        return ManifestIds(unmatched, emptyMap(), redactedIds)
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

    fun tabulateRedactedGroups(): Map<Int, ContestTabulation> {
        val sumTabs = mutableMapOf<Int, ContestTabulation>()
        redactedGroups.forEach { redacted: RedactedGroup ->
            val groupTab: Map<Int, ContestTabulation> = converter.convertToContestTabulation(redacted)
            sumTabs.sumContestTabulations(groupTab)
        }
        if (show) {
            println("redactedTabulation")
            sumTabs.toSortedMap().forEach { println("  $it") }
        }
        return sumTabs
    }

    fun makeRedactedPools(variant: ElectionVariant): List<CardPoolBuilder> {
        return if (variant.onePool) convertRedactedToOneCardPool() else convertRedactedToCardPool()
    }

    private fun convertRedactedToCardPool(): List<CardPoolBuilder> {
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
    private fun convertRedactedToOneCardPool(): List<CardPoolBuilder> {
        if (redactedGroups.isEmpty()) return emptyList()

        var sumTabs = mutableMapOf<Int, ContestTabulation>()
        redactedGroups.forEach { redacted: RedactedGroup ->
            val groupTab: Map<Int, ContestTabulation> = converter.convertToContestTabulation(redacted)
            sumTabs.sumContestTabulations(groupTab)
        }
        return listOf(CardPoolBuilder("$county-Redacted", nextStyleId++, hasExactContests=false, infos, sumTabs)
            .setNcards(manifestIds.unmatched))
    }

    // make simulated CVRs for the redacted pools, using entries in the manifest that dont have cvrs
    fun makeSimulatedCards() : List<AuditableCard> { // contestId -> candidateId -> nvotes
        val redactedIter = manifestIds.redactedIds.iterator()
        val rcvrs = mutableListOf<AuditableCard>()
        redactedPools.forEach { cardPool ->
            rcvrs.addAll(makeCardsForOnePool(cardPool, redactedIter))
        }
        logger.info {"wanted=${manifestIds.unmatched} got=${rcvrs.size} redactedManifestIds is finished = ${!redactedIter.hasNext()}"}
        return rcvrs
    }

    // make simulated CVRs for one pool, all contests, using the unmatched manifestIds
    private fun makeCardsForOnePool(cardPool: CardPool, redactedIter: Iterator<ManifestId>) : List<AuditableCard> { // contestId -> candidateId -> nvotes
        val vunders = cardPool.possibleContests().associate { Pair(it, cardPool.votesAndUndervotes(it)) }.toMap()
        val vunderPool = VunderPool(vunders, cardPool.poolName, cardPool.poolId, cardPool.hasExactContests)
        val cardsForPool = mutableListOf<AuditableCard>()

        var count = 0
        while (redactedIter.hasNext() && count < cardPool.ncards()) {
            val manifestEntry = redactedIter.next()
            val cvb2 = AuditableCardBuilder(manifestEntry.id, "$county:${manifestEntry.location}", count, 0L, variant.phantoms,
                cardPool.poolId, cardPool.poolId, null, null)
            vunderPool.simulatePooledCard(cvb2) // fill in the vote
            cardsForPool.add(cvb2.build())
            count++
        }

        return cardsForPool
    }

    fun countyCardStyles(): List<StyleIF> = cvrStyles + redactedPools.map { it as StyleIF }
}

/* class RedactedManifestIds(val manifestIds: Iterator<ManifestId>): Iterator<ManifestId> {
    var nextManifestId: ManifestId? = null

    override fun next(): ManifestId {
        val nextId =  nextManifestId!!
        nextManifestId = null
        return nextId
    }

    // note not idempotent
    override fun hasNext(): Boolean {
        if (nextManifestId != null) return true
        while (manifestIds.hasNext()) {
            val mid = manifestIds.next()
            if (mid.card == null) {
                nextManifestId = mid
                return true
            }
        }
        nextManifestId = null
        return false
    }

} */

/////////////////////////////////////////////////////////////////

fun compareCvrsAndManifests(input: CorlaCountyInput, showMissed: Boolean = true, showUnmatched: Boolean = false) {
    val corlaCvrs = input.readCorlaCvrs()

    val nCvrs = corlaCvrs.cvrs().size
    println("${input.cvrsSource}: nrows = ${corlaCvrs.nrows()} cvrs size = ${corlaCvrs.cvrs().size}")

    val redactedGroupSize = corlaCvrs.redactedGroups().size
    val redactedNCards = corlaCvrs.redactedGroups().sumOf { it.ncards() }
    println("\nRedacted groups (${redactedGroupSize})")
    corlaCvrs.redactedGroups().forEach { println("  $it") }

    println("redacted ncards = ${redactedNCards}")
    val totalCvrs = nCvrs + redactedNCards
    println("cvrs + redacted ncards = ${totalCvrs}")

    val manifestBatches = input.readCountyManifest()
    val manifestNCards = manifestBatches.sumOf { it.nballotCards }
    println("\n${input.manifestSource}")
    println("  manifestNCards = $manifestNCards")

    val estRedactedNcards = manifestNCards - corlaCvrs.cvrs().size
    println("  est redacted ncards= ${estRedactedNcards}")
    val missingNcards = manifestNCards - totalCvrs
    print("  sumManifest - totalCvrs = ${missingNcards}; ")
    if (missingNcards == 0) print(" AGREE!")
    else if (missingNcards < 0) print(" manifest not up to date ??")
    else if (redactedGroupSize == 1) print(" single group should be set to $estRedactedNcards, currently $redactedNCards ")
    else print(" need $missingNcards more redacted cards")
    println()

    /////////////////////////////////////////////////////////////////
    val manifestIdMap = mutableMapOf<String, CvrsFromManifest.ManifestId>()
    manifestBatches.forEach { batch ->
        repeat(batch.nballotCards) { recordId ->
            val want = "${batch.tabulatorNum}-${batch.batchId}-${recordId + 1}"
            manifestIdMap[want] = CvrsFromManifest.ManifestId(want, batch.location)
        }
    }

    var countMiss = 0
    var countDup = 0
    corlaCvrs.cvrs().forEach { cvr ->
        val manifestMatch = manifestIdMap[cvr.imprintedId]
        if (manifestMatch != null) {
            if (manifestMatch.matched) countDup++
            manifestMatch.matched = true
        } else {
            countMiss++
            if (showMissed) println("  didnt find cvr imprintedId '${cvr.imprintedId}' in manifest")
        }
    }
    println("\ncards not found in manifest= $countMiss; duplicate imprintedIds= $countDup")

    val countUnmatched = manifestIdMap.values.count { !it.matched }
    println("countMiss=$countMiss countUnmatched=$countUnmatched countDup=$countDup")
}

