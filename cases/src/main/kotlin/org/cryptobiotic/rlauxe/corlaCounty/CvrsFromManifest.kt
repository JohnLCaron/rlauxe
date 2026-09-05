package org.cryptobiotic.rlauxe.corlaCounty

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.audit.CardPoolBuilder
import org.cryptobiotic.rlauxe.auditcenter.ColoradoInput
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.cvr.CorlaCvrConverter
import org.cryptobiotic.rlauxe.cvr.CvrRow
import org.cryptobiotic.rlauxe.cvr.RedactedGroup
import org.cryptobiotic.rlauxe.cvr.cleanCsvString
import org.cryptobiotic.rlauxe.estimate.VunderPool
import org.cryptobiotic.rlauxe.util.AuditableCardBuilder
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.util.sumContestTabulations
import org.cryptobiotic.rlauxe.util.tabulateCards
import kotlin.math.max

class CvrsFromManifest(val variant: ElectionVariant,
                       val countyInput: CorlaCountyInput,
                       val stateInput: ColoradoInput,
                       val infos: Map<Int, ContestInfo>,
                       ) {
    val show = false

    val converter: CorlaCvrConverter
    val infosByName = infos.mapKeys { it.value.name } //  are the cvr names compatible ?
    val convertedCvrs: List<AuditableCard>

    var countMiss = 0 // count of Cvrs not in the manifest
    var countDup = 0  // count of duplicate ids in the Cvrs
    val countUnmatched : Int // count of Manifest not in the Cvrs; presumed to be == redacted CVRs

    val manifestIds: List< ManifestId>
    val manifestSize : Int
    val convertedCvrTabs : Map<Int, ContestTabulation>

    val redactedGroups: List<RedactedGroup>
    val redactedTabs : Map<Int, ContestTabulation>
    val redactedPools: List<CardPool>

    init {
        val corlaCvrs = countyInput.readCorlaCvrs()
        redactedGroups = corlaCvrs.redactedGroups()

        converter = CorlaCvrConverter(countyInput.countyName, corlaCvrs, infosByName, stateInput)
        convertedCvrs = corlaCvrs.cvrs().map { converter.convertToCard(it) }

        val manifestIdMap = mutableMapOf<String, ManifestId>()
        countyInput.readCountyManifest().forEach { batch ->
            repeat(batch.nballotCards) { recordId ->
                val want = "${batch.tabulatorNum}-${batch.batchId}-${recordId + 1}"
                manifestIdMap[want] = ManifestId(want, batch.location)
            }
        }
        manifestIds = manifestIdMap.values.toList()
        manifestSize = manifestIdMap.size

        convertedCvrs.forEach { card ->
            val manifestMatch = manifestIdMap[card.id]
            if (manifestMatch != null) {
                if (manifestMatch.card != null) countDup++
                manifestMatch.card = card
            } else
                countMiss++
        }

        countUnmatched = manifestIdMap.values.count { it.card == null }
        println("countMiss=$countMiss countUnmatched=$countUnmatched countDup=$countDup")

        redactedTabulation()
        val redactedPoolBuilders = makeRedactedPools(variant)

        convertedCvrTabs = tabulateCards(convertedCvrs.iterator(), infos)
        redactedTabs = redactedTabulation()

        // set ncards for each pool; when CardPool is built, the contestTabs are reset accordingly
        if (redactedPoolBuilders.size == 1) {
            redactedPoolBuilders.first().setNcards(countUnmatched)
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

        val usedCards = redactedPools.sumOf { it.ncards() }
        println("usedCards=$usedCards countUnmatched=$countUnmatched")
    }

    fun setRedactedNCards(cvrTabs: Map<Int, ContestTabulation>, poolBuilders: List<CardPoolBuilder>) {
        // assume that the undervote Pct in the redacted Groups is the same as in the unredacted CVRs
        val undervotePct: Map<Int, Double> = cvrTabs.mapValues { it.value.undervotes() / it.value.ncards().toDouble() }
        poolBuilders.forEach { poolb ->
            var maxCards = 0
            poolb.contestTabs.forEach { (contestId, contestTab) ->
                val uvPct = undervotePct[contestId] ?: 1.0 // its possible there are no cvrs for a contest
                contestTab.undervotes = roundToClosest(uvPct * contestTab.ncards()) // TODO 1.0
                contestTab.ncardsTabulated = contestTab.undervotes + contestTab.nvotes()
                maxCards = max(maxCards, contestTab.ncardsTabulated)
            }
            poolb.setNcards(maxCards)
        }
        val usedCards = poolBuilders.sumOf { it.ncards() }
        println("redactedCvrs=$usedCards countUnmatched=$countUnmatched")
        // should adjust
    }

    fun redactedTabulation(): Map<Int, ContestTabulation> {
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
        return if (variant.onePool) listOf(convertRedactedToOneCardPool())
               else convertRedactedToCardPool()
    }

    private fun convertRedactedToCardPool(): List<CardPoolBuilder> {
        var id = 1
        return redactedGroups.map { redacted: RedactedGroup ->
            //// the redacted groups dont have undervotes, so we should try to generate reasonable undervote counts
            // but... now we are just setting the vote totals, ignoring ncards and undervotes.

            // val contestTabs = redacted.contestVotes.mapValues{ ContestTabulation(infos[it.key]!!, it.value, ncards=0) }
            val contestTabs: Map<Int, ContestTabulation> = converter.convertToContestTabulation(redacted)

            val name = cleanCsvString(redacted.ballotType)
            val hasExactContests = !redacted.ballotType.contains("&") // has multiple card styles
            // TODO role of redacted.ncards() ?
            CardPoolBuilder.fromMinVotesNeeded(name, id++, hasExactContests=hasExactContests, infos, contestTabs)
                .setNcards(redacted.ncards())
        }
    }

    // note that ncards is not set here
    private fun convertRedactedToOneCardPool(): CardPoolBuilder {
        var sumTabs = mutableMapOf<Int, ContestTabulation>()
        redactedGroups.forEach { redacted: RedactedGroup ->
            val groupTab: Map<Int, ContestTabulation> = converter.convertToContestTabulation(redacted)
            sumTabs.sumContestTabulations(groupTab)
        }
        return CardPoolBuilder.fromMinVotesNeeded("RedactedPool", 1, hasExactContests=false, infos, sumTabs)
    }


    // make simulated CVRs for all the pools
    fun makeSimulatedCards() : List<AuditableCard> { // contestId -> candidateId -> nvotes
        val redactedManifestIds = RedactedManifestIds(manifestIds.iterator())
        val rcvrs = mutableListOf<AuditableCard>()
        redactedPools.forEach { cardPool ->
            rcvrs.addAll(makeCardsForOnePool(cardPool, redactedManifestIds))
        }
        println("wanted=$countUnmatched got=${rcvrs.size} redactedManifestIds is finished = ${!redactedManifestIds.hasNext()}")
        return rcvrs
    }

    // make simulated CVRs for one pool, all contests, using the unmatched manifestIds
    private fun makeCardsForOnePool(cardPool: CardPool, manifestIds: RedactedManifestIds) : List<AuditableCard> { // contestId -> candidateId -> nvotes
        val vunders = cardPool.possibleContests().associate { Pair(it, cardPool.votesAndUndervotes(it)) }.toMap()
        val vunderPool = VunderPool(vunders, cardPool.poolName, cardPool.poolId, cardPool.hasExactContests)
        val cardsForPool = mutableListOf<AuditableCard>()

        var count = 0
        while (manifestIds.hasNext() && count < cardPool.ncards()) {
            val manifestEntry = manifestIds.next()
            val cvb2 = AuditableCardBuilder(manifestEntry.id, manifestEntry.location, count, 0L, variant.phantoms,
                cardPool.poolId, cardPool.poolId, null, null)
            vunderPool.simulatePooledCard(cvb2) // fill in the vote
            cardsForPool.add(cvb2.build())
            count++
        }

        return cardsForPool
    }
}

data class ManifestId(val id: String, val location: String) {
    var card : AuditableCard? = null
    var matched = false
}

class RedactedManifestIds(val manifestIds: Iterator<ManifestId>): Iterator<ManifestId> {
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

}

// used by CvrsFromManifest
fun fromPctUndervotes(
    poolName: String,
    poolId: Int,
    hasExactContests: Boolean,    // aka single style
    infos: Map<Int, ContestInfo>, // do we really need this ??
    contestTabs: Map<Int, ContestTabulation>,  // contestId -> ContestTabulation
    undervotePct: Map<Int, Double>, // undervotePct for each contest
): CardPoolBuilder {

    val minCardsNeeded = mutableMapOf<Int, Int>() // contestId -> minCardsNeeded
    contestTabs.forEach { (contestId, contestTab) ->
        val voteSum = contestTab.nvotes()
        val info = infos[contestId]!!
        // based on the contest's votes, you need at least this many cards for this contest
        minCardsNeeded[contestId] = roundUp(voteSum.toDouble() / info.voteForN)
    }
    return CardPoolBuilder(poolName, poolId, hasExactContests, infos, contestTabs, minCardsNeeded)
}


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
    val manifestIdMap = mutableMapOf<String, ManifestId>()
    manifestBatches.forEach { batch ->
        repeat(batch.nballotCards) { recordId ->
            val want = "${batch.tabulatorNum}-${batch.batchId}-${recordId + 1}"
            manifestIdMap[want] = ManifestId(want, batch.location)
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

    /*
    val cvrMap = corlaCvrs.cvrs().associate { it.imprintedId to CvrId(it) }
    println("\nmatch manifest cards to cvrs")

    var countMiss = 0
    var countDup = 0
    manifestBatches.forEach { batch ->
        repeat(batch.nballotCards) { recordId ->
            val want = "${batch.tabulatorNum}-${batch.batchId}-${recordId + 1}"
            val cvr = cvrMap[want]
            if (cvr != null) {
                if (cvr.matched) countDup++
                cvr.matched = true
            } else {
                countMiss++
                if (showMissed) println("  didnt find cvr $want in manifest")
            }
        }
    }
    println("  # manifest cards not found in cvrs == $countMiss countDup == $countDup")

    println("\nmatch cvrs to manifest")
    var count = 0
    cvrMap.forEach { (key, id) ->
        if (!id.matched) {
            if (showUnmatched) println("  $key")
            count++
        }
    }
    println("  # cvrs not found in manifest == $count")
    if (count > 0) println("   probably manifest file was updated but not in auditcenter")

    // println("  countMiss - missing from cvrs == ${countMiss - missing} redactedCards == $redactedCards")

     */
}

data class CvrId(val tabulatorNum: Int, val batchId: String, val recordId: Int) {
    var matched = false

    constructor(cvr: CvrRow) : this(cvr.tabulatorNum, cvr.batchId, cvr.recordId) {
        if (cvr.imprintedId != "${cvr.tabulatorNum}-${cvr.batchId}-${cvr.recordId}")
            print("")
        require(cvr.imprintedId == "${cvr.tabulatorNum}-${cvr.batchId}-${cvr.recordId}")
    }
}