package org.cryptobiotic.rlauxe.corlaCounty

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.audit.CardPoolBuilder
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.auditcenter.BuildCorlaContests
import org.cryptobiotic.rlauxe.auditcenter.CountyTabAllContests
import org.cryptobiotic.rlauxe.corlaInput.ColoradoInput
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.corlaInput.CorlaCountyInput
import org.cryptobiotic.rlauxe.corlaInput.ManifestEntry
import org.cryptobiotic.rlauxe.corlaInput.ManifestId2
import org.cryptobiotic.rlauxe.corlaInput.ManifestIds2
import org.cryptobiotic.rlauxe.cvr.CorlaCvrConverter
import org.cryptobiotic.rlauxe.cvr.CorlaCvrs
import org.cryptobiotic.rlauxe.cvr.CorlaCvrsIF
import org.cryptobiotic.rlauxe.cvr.CvrRow
import org.cryptobiotic.rlauxe.cvr.RedactedGroup
import org.cryptobiotic.rlauxe.cvr.cleanCsvString
import org.cryptobiotic.rlauxe.estimate.VunderPool
import org.cryptobiotic.rlauxe.util.AuditableCardBuilder
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.nfz
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.util.subtractContestTabulations
import org.cryptobiotic.rlauxe.util.sumContestTabulations
import org.cryptobiotic.rlauxe.util.tabulateCards
import kotlin.math.max
import kotlin.random.Random

class CheckCvrsAndManifest(
    val stateInput: ColoradoInput,
    val countyInput: CorlaCountyInput,
    // val grabInfo: MutableList<String>? = null,
    startingPoolId: Int = 1,
    val variant: ElectionVariant = ElectionVariant(ElectionVariantEnum.OnePool),
    ) {
    val show = false
    val showLines = false
    val county = countyInput.countyName

    val infos: Map<Int, ContestInfo>
    val converter: CorlaCvrConverter
    // val infosByName = infos.mapKeys { it.value.name } //  are the cvr names compatible ?
    val convertedCvrs: List<AuditableCard>
    val cvrStyles: List<StyleIF>
    var nextStyleId: Int

    val manifestIds: ManifestIds2

    val convertedCvrTabs : Map<Int, ContestTabulation>

    // val redactedGroups: List<RedactedGroup>
    val redactedPools: List<CardPool>
    val redactedTabs : Map<Int, ContestTabulation>

    init {
        val contestBuilder = BuildCorlaContests(stateInput)
        infos = contestBuilder.infos
        val infosByName = infos.mapKeys { it.value.name } //  are the cvr names compatible ?

        val corlaCvrs = countyInput.readCorlaCvrs()
        // corlaCvrs.redactedGroups()


        val manifest = countyInput.readCountyManifest()
        manifestIds = manifest.checkManifestVsCvrs(corlaCvrs.cvrs())

        converter = CorlaCvrConverter(countyInput.countyName, corlaCvrs, infosByName, stateInput, startingPoolId)
        cvrStyles = converter.cardStyles.values.toList()
        nextStyleId = startingPoolId + cvrStyles.size

        convertedCvrs = corlaCvrs.cvrs().map {
            converter.convertToCard(it) { cvrb:AuditableCardBuilder ->
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

        ////////////////////////////////////////////////////
        // can we use county cvr vote totals to calculate oneaudit subtotals?
        val countyTab = stateInput.countyTabsAllContests()[county]!!
        val convertedCountyTabs: Map<Int, ContestTabulation> = converter.convertToContestTabulation(countyTab)
        val diff = subtractContestTabulations(convertedCountyTabs, convertedCvrTabs)
        diff.toSortedMap().forEach{
            if (it.value.nvotes() != 0) {
                print("  ${it.key} == ${it.value.nvotes()}; ")
                it.value.votes.filter { it.value != 0 }.forEach { print("${it}, ") }
                println()
            }
        }
        val diffnz = diff.filter { it.value.nvotes() > 0 }

        val minCards = diff.values.maxOf { it.nvotes() }
        println("minCards = $minCards")

        ////////////////////////////////////////////////

        val redactedPoolBuilders = makeRedactedPools(variant, corlaCvrs)

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
        redactedTabs = tabulateRedactedPools(redactedPools)

        compareCvrsAndManifests(countyInput, corlaCvrs)
    }

    // id matches the imprintedId, location is the manifest location field
    data class ManifestId(val tab: Int, val batch: String, val record: Int, val location: String) {
        var matched = false // did we find a match yet?
        val id = "$tab-$batch-$record"
        val sorter: String

        init {
            var tsorter = ""
            try {
                val batchAsInt = batch.toInt()
                tsorter = (1000_000 * tab + 1000 * batchAsInt + record).toString()
            } catch (e: Throwable) {
                tsorter = nfz(tab,4) + batch + nfz(record,4)
            }
            sorter = tsorter
        }
        constructor(cvr: CvrRow) : this(cvr.tabulatorNum, cvr.batchId, cvr.recordId, "")
    }

    data class ManifestIds(
        val unmatched: Int,                    // count of Manifest entries not in the Cvrs; presumed to be == redacted CVRs
        val match: Map<String, ManifestId>,    // imprintedId -> ManifestId
        val redactedIds: List<ManifestId>      // didnt match cvr, assume to be in the redactions
    )

    ///////////////////////////////////////////////////////////////////////////

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
        adjust(poolBuilders.associateBy{ it.poolId }, adjustPool)
    }

    data class PoolAdjustment(val poolId: Int)

    fun adjust(poolBuilderMap: Map<Int, CardPoolBuilder>, adjustPool: Map<Int, Int>) {
        // we want the sum of redacted cards to equal countUnmatched
        val sumCardsBefore = poolBuilderMap.values.sumOf { it.ncards() }
        val adjust = sumCardsBefore - manifestIds.unmatched
        println("sumCardsBefore=$sumCardsBefore countUnmatched=${manifestIds.unmatched} adjust=$adjust")

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
            println("adjust $adjust")
        }

        val sumCardsAfter = poolBuilderMap.values.sumOf { it.ncards() }
        println("sumCardsAfter=$sumCardsAfter countUnmatched=${manifestIds.unmatched}")

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

    /////////////////////////////////////////////////////////////////////////////

    fun makeRedactedPools(variant: ElectionVariant, corlaCvrs: CorlaCvrsIF): List<CardPoolBuilder> {
        val result = mutableListOf<CardPoolBuilder>()
        val g = makeGroupWithLines(corlaCvrs)
        if (g != null) result.add(g)
        if (corlaCvrs.redactedGroups().isNotEmpty()) {
            if (variant.onePool) result.add(convertRedactedToOneCardPool(corlaCvrs.redactedGroups()))
            else result.addAll(convertRedactedToCardPool(corlaCvrs.redactedGroups()))
        }
        return result
    }

    fun makeGroupWithLines(corlaCvrs: CorlaCvrsIF): CardPoolBuilder? {
        // do the simple thing - all rows into one group, use vote diff as the subtotal
        val groupWithLines = corlaCvrs.groupWithLines() ?: return null
        if (showLines) {
            groupWithLines.redactedRows.forEach { row: CvrRow ->
                print("ballotType = ${row.ballotType}")
                val style = corlaCvrs.cardStyles().find { it.name == row.ballotType }
                if (style != null) println(" has $style") else println()
            }
            println()
        }

        val countyTab = stateInput.countyTabsAllContests()[county]!!
        val convertedCountyTabs: Map<Int, ContestTabulation> = converter.convertToContestTabulation(countyTab)
        val missingVoteTab = subtractContestTabulations(convertedCountyTabs, convertedCvrTabs)
        /* if (grabInfo != null) {
            // fun showMissingVotes(countyTab: CountyTabAllContests, converter: CorlaCvrConverter, convertedCvrTabs: Map<Int, ContestTabulation>):
            grabInfo.add(showMissingVotes(missingVoteTab))
        } */
        return CardPoolBuilder(
            "$county-RedactedLines",
            nextStyleId++,
            hasExactContests = false,
            infos,
            missingVoteTab
        ).setNcards(groupWithLines.redactedRows.size)
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
        println("wanted=${manifestIds.unmatched} got=${rcvrs.size} redactedManifestIds is finished = ${!redactedIter.hasNext()}")
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


///////////////////////////////////////////////////////////////
fun compareCvrsAndManifests(input: CorlaCountyInput, corlaCvrs: CorlaCvrsIF, showMissed: Boolean = true, showUnmatched: Boolean = false) {
    val allCvrs = corlaCvrs.cvrs() + corlaCvrs.redactedCvrs()

    val nCvrs = allCvrs.size
    println("${input.cvrsSource}: nrows = ${corlaCvrs.nrows()} cvrs size = ${nCvrs}")

    val redactedGroupSize = corlaCvrs.redactedGroups().size
    val redactedNCards = corlaCvrs.redactedGroups().sumOf { it.ncards() }
    println("\nRedacted groups (${redactedGroupSize})")
    corlaCvrs.redactedGroups().forEach { println("  $it") }

    println("redacted ncards = ${redactedNCards}")
    val totalCvrs = nCvrs + redactedNCards
    println("cvrs + redacted ncards = ${totalCvrs}")

    val manifestBatches = input.readCountyManifest()
    val manifestNCards = manifestBatches.totalCards
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
    val manifest = input.readCountyManifest()
    val report = mutableListOf<String>()
    val manifestIds = manifest.checkManifestVsCvrs(corlaCvrs.cvrs(), report)
    report.forEach { println(it) }
}

fun showMissingVotes(missingVoteTab: Map<Int, ContestTabulation>) = buildString {
    missingVoteTab.forEach { (id, tab) ->
        if (tab.nvotes() != 0) {
            append("${id} == ${tab.nvotes()}; ")
            tab.votes.filter { it.value != 0 }.forEach { append("${it}, ") }
            appendLine()
        }
    }
}


