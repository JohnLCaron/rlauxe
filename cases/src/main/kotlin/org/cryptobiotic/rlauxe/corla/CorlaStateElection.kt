package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.corlaInput.writeCountyContestData
import org.cryptobiotic.rlauxe.corlaInput.writeCountyData
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.corlaCounty.ElectionVariant
import org.cryptobiotic.rlauxe.corlaCounty.ElectionVariantEnum
import org.cryptobiotic.rlauxe.corlaCounty.makeContestWAs
import org.cryptobiotic.rlauxe.corlaInput.ColoradoInputWithCvrs
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeCardCsvFile
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.util.*
import java.nio.file.Path
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.collections.plus
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

private val logger = KotlinLogging.logger("CorlaStateElection")

// A Corla state election with CVRS. Creates a CountyAuditRecord.
class CorlaStateElection(
    val topdir: String,
    val stateInput: ColoradoInputWithCvrs,
    val mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    val hasStyle: Boolean, // TODO
    variantEnum: ElectionVariantEnum,
    votedatabase: Map<String, String>? = null,
): ElectionBuilder {
    val variant = ElectionVariant(variantEnum)
    val publisher = Publisher(topdir)
    val electionName = stateInput.javaClass.simpleName

    val allStyles = mutableListOf<StyleIF>()
    val allPools = mutableListOf<CardPool>()
    val countyPools = mutableListOf<CountyPools>()
    val cvrPools = mutableListOf<CountyPools>()
    val contestsUA: List<ContestWithAssertions>
    val ncards: Int

    init {
        val contestBuilder = CorlaStateContestInfoBuilder(stateInput)
        val infos = contestBuilder.infos
        val totalPoolTabs = mutableMapOf<Int, ContestTabulation>() // total over counties
        val stateNCardsByContest = mutableMapOf<Int, Int>()
        val statePhantoms = mutableMapOf<Int, Int>()
        val statePops = mutableMapOf<Int, Int>()

        var totalCvrCardCount = 0
        val stateCvrTabs = mutableMapOf<Int, ContestTabulation>() // total over counties
        var nextStyleId = 1
        var countyPoolId = 1

        stateInput.counties().forEach { countyName ->
            val countyInput = stateInput.corlaCountyInput(countyName, votedatabase)!!
            val countyPopulation = countyInput.countyPopulation()

            val cvrsFromManifest = CvrsFromManifest2(variant, countyInput, stateInput, infos, nextStyleId)

            // only needed for (variant.phantoms)
            val phantomCards = if (variant.phantoms) makePhantomCards(cvrsFromManifest.phantomsByContest, countyName)
                else emptyList()
            if (variant.phantoms) logger.info { "phantomsByContest for $countyName\n  ${cvrsFromManifest.phantomsByContest}" }
            cvrsFromManifest.phantomsByContest.forEach { (id, value) ->
                statePhantoms.merge(id, value, Int::plus)
            }

            val totalCountyTabs = mutableMapOf<Int, ContestTabulation>() // total over counties
            totalCountyTabs.sumContestTabulations(cvrsFromManifest.unredactedCvrTabs)
            totalCountyTabs.sumContestTabulations(cvrsFromManifest.redactedTabs)
            stateCvrTabs.sumContestTabulations(totalCountyTabs)

            cvrsFromManifest.estNcardsByContest.forEach { (id, value) ->
                stateNCardsByContest.merge(id, value, Int::plus)
            }

            // TODO make the mvrs/cvrs seperate so can skip that if possible.
            // TODO do we need to match the corla canonical ordering ??

            // these are mvrs; for all but phantoms, use simulated mvrs that match the pool totals.
            // for phantom variant, makeSimulatedMvrs is empty; add phantom cards
            val allCountyMvrs = cvrsFromManifest.unredactedCvrs + cvrsFromManifest.makeSimulatedMvrs() + phantomCards // in memory

            // write them out while we have them in memory
            writeUnsortedMvrs(countyName, publisher, Closer(allCountyMvrs.iterator()))
            totalCvrCardCount += allCountyMvrs.size

            // how do the cvrs relate to the mvrs ?? Remove simulated votes in pools
            writeUnsortedCountyCvrs(countyName, publisher, Closer(allCountyMvrs.iterator()), variant)

            val countyPops = tabulateNpops(allCountyMvrs, infos.values.toList())
            countyPops.forEach { (id, value) ->
                statePops.merge(id, value, Int::plus)
            }

            // TODO to use fastSampling, all cvrs must have stylesIds (no "fromCvr" or "phantoms")
            //  styles cant be optional; all styles must be in styleMap when reading
            val countyCardStyles = cvrsFromManifest.countyCardStyles()
            allStyles.addAll(countyCardStyles)
            nextStyleId += countyCardStyles.size

            // TODO I dont think this is needed ??
            cvrPools.add(
                CountyPools(countyName, countyPoolId, cvrsFromManifest.unredactedCvrTabs, cvrsFromManifest.unredactedCvrs.size, countyCardStyles)
            )
            countyPools.add(
                CountyPools(countyName, countyPoolId++, totalCountyTabs, countyPopulation, countyCardStyles)
            )

            allPools.addAll(cvrsFromManifest.redactedPools)
            // totalPoolTabs.sumContestTabulations(contestTabs)
        }

        val contests = stateCvrTabs.map { (contestId, contestTab) ->
            var Nc = stateNCardsByContest[contestId]!!
            val Ncast = contestTab.ncardsTabulated
            if (Ncast > Nc) {
                logger.warn{"$contestId: Ncast $Ncast > $Nc Nc diff = ${Ncast-Nc}"}
                Nc = Ncast
            }
            val info = infos[contestId]!!
            if (variant.isOA()) {
                val poolTotalCards = allPools.filter { it.hasContest(info.id) }.sumOf { it.ncards() }
                info.metadata["PoolPct"] = if (Nc == 0) "0" else (100.0 * poolTotalCards / Nc).toInt().toString()
            }
            if (variant.phantoms) {
                val phantomsForContest = statePhantoms[info.id] ?: 0
                info.metadata["PhantomPct"] = if (Nc == 0) "" else (100.0 * phantomsForContest / Nc).toInt().toString()
            }
            Contest(info, contestTab.votes, Nc, Ncast)
        }
        // TODO need Irv tabs
        contestsUA = makeContestWAs(contests, statePops, emptyMap(), allPools, variant, hasStyle)

        // stick all the phantoms at the end? or put them by county ??
        if (variant.phantoms) println("phantomsByContest = ${statePhantoms}")

        this.ncards = totalCvrCardCount // or totalPoolCardCount?
    }

    override fun electionInfo() =
        ElectionInfo(electionName, variant.auditType, ncards(), contestsUA.size, true, mvrSource=mvrSource)

    override fun contestsUA() = contestsUA
    override fun cardStyles() = allStyles
    override fun cardPools() = allPools

    override fun countyCardPools(): List<CountyPools> = countyPools
    override fun countyCvrPools(): List<CountyPools> = cvrPools

    override fun unsortedMvrsInternal() = null
    override fun unsortedMvrsExternal() = CardIteratorfromCountyMvrs(publisher, styles = allStyles)

    // TODO do we need to munge the mvrs for the card manifest? Add the card styles ??
    override fun cards() = removeVotesFromMvrs(unsortedMvrsExternal(), variant)
    override fun ncards() = ncards

    fun addIndexToMvrs(mvrs: List<AuditableCard>): List<AuditableCard> {
        var cardIndex = 1 // 1 based index
        val result = mutableListOf<AuditableCard>()
        mvrs.forEach { org ->
            result.add(org.copy(index = cardIndex ))
            cardIndex++
        }
        return result
    }
}

// all cards, including phantoms are already in the iterator
fun writeUnsortedMvrs(
    county: String,
    publisher: Publisher,
    countyMvrs: CloseableIterator<AuditableCard>,
): Int {
    val dir = publisher.unsortedMvrsDirectory()
    validateOutputDir(Path(dir))
    val outfile = "$dir/${county}.csv"

    val cardsWritten = writeCardCsvFile(countyMvrs, outfile)
    logger.info { "write $cardsWritten UnsortedMvrs for $county to ${outfile}" }

    return cardsWritten
}

// remove votes for cards in the pools
fun removeVotesFromMvrs(mvrs: CloseableIterator<AuditableCard>, variant: ElectionVariant): CloseableIterator<AuditableCard> {
    val transformer = TransformingIterator<AuditableCard, AuditableCard>(mvrs) { org ->
        if (org.poolId != null && variant.isOA()) AuditableCard.removeVotes(org) else org
    }
    return transformer
}

fun writeUnsortedCountyCvrs(
    county: String,
    publisher: Publisher,
    countyMvrs: CloseableIterator<AuditableCard>,
    variant: ElectionVariant,
): Int {
    val dir = publisher.unsortedCountyCvrDirectory()
    validateOutputDir(Path(dir))
    val outfile = "$dir/${county}.csv"

    val cvrIter = removeVotesFromMvrs(countyMvrs, variant)
    val cardsWritten = writeCardCsvFile(cvrIter, outfile)
    logger.info { "write $cardsWritten UnsortedCountyCvrs for $county to ${outfile}" }

    return cardsWritten
}

// create an iterator over all the cards unsortedMvrsDirectory
class CardIteratorfromCountyMvrs(
    publisher: Publisher,
    val styles: List<StyleIF>
) : CloseableIterator<AuditableCard> {

    val dir = publisher.unsortedMvrsDirectory()
    val path = Path(dir)
    val countyPaths: List<Path> = path.listDirectoryEntries().filter { !it.isDirectory() && it.fileName.toString().endsWith(".csv")}

    val counties = countyPaths.iterator()
    var innerIter = readCardsCsvIterator(counties.next().toString(), styles = styles)  // TODO do we need styles ??

    override fun next(): AuditableCard {
        return innerIter.next()
    }

    override fun hasNext(): Boolean {
        if (innerIter.hasNext()) return true
        if (counties.hasNext()) {
            innerIter = readCardsCsvIterator(counties.next().toString(), styles = styles)
            return hasNext()
        }
        return false
    }

    override fun close() {
        // NOOP
    }
}

////////////////////////////////////////////////////////////////////
// variant.Sim: CLCA with simulated cvrs for the redacted groups
// variant.Styles: OneAudit with redacted cards in a multiple pools by style
// variant.OnePool: OneAudit with redacted cards in a single pool
// variant.Phantoms: OneAudit with redacted cards set to isPhantom

fun createCorlaStateElection(
    topdir: String,
    stateInput: ColoradoInputWithCvrs,
    creation: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    hasStyle: Boolean = true, // TODO wtf ??
    variant: ElectionVariantEnum,
    votedatabase: Map<String, String>? = null
 ) {
    val stopwatch = Stopwatch()

    clearDirectory(Path(topdir))
    Logging.addFileAppender("cases", "$topdir/logs.log")
    logger.info {"-------------- createCorlaStateElection ${stateInput.javaClass.name} in $topdir"}

    val election = CorlaStateElection(topdir, stateInput, mvrSource = mvrSource, hasStyle = hasStyle, variant, votedatabase)

    createElectionRecord(election, topdir = topdir)

    val config = Config(election.electionInfo(), creation, roundConfig)
    createAuditRecord(config, election, topdir = topdir, externalSortDir = topdir, fastSampling = true)

    // TODO maybe just chosen counties ?
    writeCountyData(topdir, stateInput.strataMap.values.toList())
    val contestMap = election.contestsUA.associate { it.contest.info().name to it }
    writeCountyContestData(topdir, contestMap, stateInput)

    val result = startFirstRound(topdir)
    if (result.isErr) logger.error{ result.toString() }

    logger.info{"createCorlaCountyElection took $stopwatch"}
}