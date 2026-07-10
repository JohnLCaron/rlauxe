package org.cryptobiotic.rlauxe.auditcenter

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.DominionConverter
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportCsv
import org.cryptobiotic.rlauxe.dominion.GarfieldCsvReader
import org.cryptobiotic.rlauxe.dominion.readCvrExportsFromFile
import org.cryptobiotic.rlauxe.estimate.simulateCards
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeCardCsvFile
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.util.*
import java.nio.file.Path
import kotlin.Int
import kotlin.String
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

private val logger = KotlinLogging.logger("CountyElectionWithCvrs")

open class CountyElectionWithCvrs (
    val counties: Map<String, String>, // countyName -> exportCvrFile
    val coloradoInput: ColoradoInput,
    val topdir: String,
    val hasStyle: Boolean,
    val name: String,
    isUniform: Boolean,
): ElectionBuilder {
    val ncards: Int
    val contestsUA: List<ContestWithAssertions>
    val cvrCardStyles = mutableListOf<StyleIF>()
    val countyPools = mutableListOf<CountyPools>()
    val cvrPools = mutableListOf<CountyPools>()

    val publisher = Publisher(topdir)

    init {
        val contestBuilder = CountyContestBuilder(coloradoInput)
        val infos = contestBuilder.infos
        val infosByName = infos.mapKeys{ it.value.name }

        val countyTabMap = coloradoInput.countyTabsAllContests
        val totalPoolTabs = mutableMapOf<Int, ContestTabulation>() // total over counties

        var totalCvrCardCount = 0
        val totalCvrTabs = mutableMapOf<Int, ContestTabulation>() // total over counties
        var countyPoolId = 1
        counties.forEach { (county, exportFile) ->
            //// the cvrs
            val export: DominionCvrExportCsv = if (county == "Garfield") GarfieldCsvReader(exportFile).read() else
                readCvrExportsFromFile(exportFile)
            val dominionConverter = DominionConverter(county, export, infosByName, coloradoInput)

            val exportCvrs: List<AuditableCard> = export.cvrs.map { dominionConverter.convertToCard(it) }

            val redactedCvrs = mutableListOf<AuditableCard>()
            if (county == "Boulder") {
                dominionConverter.redactedPools.forEach { pool ->
                    redactedCvrs.addAll(simulateCards(pool))
                }
            }
            val allCvrs: List<AuditableCard> = exportCvrs + redactedCvrs

            val cardTabulation = CardTabulation(Closer (allCvrs.iterator() ), infos) { }
            val cvrTabs = cardTabulation.tabs
            val cvrCount = cardTabulation.cvrCount
            // val (cvrTabs, cvrCount) = tabulateCardsAndCount(Closer (allCvrs.iterator() ), infos)
            totalCvrTabs.sumContestTabulations(cvrTabs)
            totalCvrCardCount += cvrCount

            // write them out while we have them in memory
            writeUnsortedMvrs(county, publisher,Closer (allCvrs.iterator() ))

            // Get the card styles from the cvrs
            val countyCardStyles: List<StyleIF> = dominionConverter.cardStyles.values.toList() + dominionConverter.redactedPools.map { it as StyleIF }

            // take ncards from cvrs
            val ncards = cvrTabs.map { (contestId, contestTab) ->
                Pair(infos[contestId]!!.name, contestTab.ncards()) // use cvr ncards is the best we can do
            }.toMap()

            // auditcenter tabulations
            val countyTab: CountyTabAllContests = countyTabMap[county]!! // // for one contest, all counties
            val contestTabs = countyTab.makeContestTabs(coloradoInput.canonicalContests(), infosByName, ncards, )
                                       .associateBy { it.contestId }

            // Note using same countyPoolId for both cvrPools and countyPools
            cvrPools.add(
                CountyPools(county, countyPoolId, cvrTabs, cvrCount, countyCardStyles)
            )
            countyPools.add(
                // make the county pool from auditcenter tabs plus cvr cardStyles and ncards
                CountyPools(county, countyPoolId++, contestTabs, cvrCount, countyCardStyles)
            )

            cvrCardStyles.addAll(countyCardStyles)
            totalPoolTabs.sumContestTabulations(contestTabs)
        }

        // probably a bad idea, in that it would create phantoms for what is (probably) the redacted ballots
        // eg Boulder went from 66393 to 251 missing votes (2646 to 25 missing cards) when redacted ballots were added
        val ncast: Map<Int, Int>  = totalPoolTabs.mapValues { it.value.ncards() }
        // val contests = contestBuilder.contests(ncast)
        // just leave it as Ncast = Nc, then the diff goes into the undervote
        val contests = contestBuilder.contests(emptyMap<Int, Int>())
        contests.forEach {
            val contestCvrTab = totalCvrTabs[it.id]
            if (contestCvrTab != null) {
                it.info().metadata["CvrNcards"] = contestCvrTab.ncards().toString()
                it.info().metadata["CvrNvotes"] = contestCvrTab.nvotes().toString()
                it.info().metadata["CvrNundervotes"] = contestCvrTab.undervotes().toString()
            }

        }
        // where do we get these? difference between the cvr card counts and the contest.Nc = round.contestBallotCardCount
        // can put them is a seperate pool as long as you include them in the unsorted iterator
        val phantoms = makePhantomCards(contests, 0) // TODO

        this.ncards = totalCvrCardCount // or totalPoolCardCount?

        contestsUA = contests.map {
            // use strataSize or Nc as population size
            val NpopIn = if (isUniform) it.info().metadata["CORLAstrataNcards"]!!.toInt() else null
            ContestWithAssertions(it, true, hasStyle, NpopIn = NpopIn).addStandardAssertions()
        }
    }

    override fun electionInfo() =
        ElectionInfo(
            name, AuditType.CLCA, ncards(),
            contestsUA.size,
            mvrSource = MvrSource.testPrivateMvrs
        )

    override fun contestsUA() = contestsUA
    override fun cardStyles(): List<StyleIF> = cvrCardStyles

    override fun cardPools() = null
    override fun countyCardPools(): List<CountyPools> = countyPools
    override fun countyCvrPools(): List<CountyPools> = cvrPools

    override fun unsortedMvrsInternal() = null
    override fun unsortedMvrsExternal() = CardIteratorfromCountyMvrs(publisher, styles = cvrCardStyles)

    // TODO do we need to munge the mvrs for the card manifest? Add the card styles ??
    override fun cards() = CardIteratorfromCountyMvrs(publisher, styles = cvrCardStyles)
    override fun ncards() = ncards
}

fun writeUnsortedMvrs(
    county: String,
    publisher: Publisher,
    countyMvrs: CloseableIterator<AuditableCard>,
    // phantoms: List<AuditableCard>,
): Int {
    val dir = publisher.unsortedMvrsDirectory()
    validateOutputDir(Path(dir))
    val outfile = "$dir/${county}.csv"

    // TODO makePhantomCvrs(contests)
    val cardsWritten = writeCardCsvFile(countyMvrs, outfile)
    logger.info { "write $cardsWritten UnsortedMvrs for $county to ${outfile}" }

    return cardsWritten
}

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

fun countyElectionWithCvrs(
    counties: Map<String, String>, // countyName -> exportFile
    coloradoInput: ColoradoInput,
    topdir: String,
    creation: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    startFirstRound: Boolean = true,
    name: String,
    isUniform: Boolean,
) {
    val stopwatch = Stopwatch()
    clearDirectory(Path(topdir))

    val election =
        CountyElectionWithCvrs(counties, coloradoInput,
            topdir, name=name, hasStyle = roundConfig.sampling.sampling == Sampling.consistent,
            isUniform=isUniform )

    createElectionRecord(election, topdir = topdir, roundConfig.sampling, clear = false)
    val config = Config(election.electionInfo(), creation, roundConfig)

    createAuditRecord(config, election, topdir = topdir, externalSortDir = topdir)

    writeCountyData(topdir, coloradoInput.strataMap.values.toList())
    val contestMap = election.contestsUA.associate { it.contest.info().name to it }
    writeCountyContestData(topdir, contestMap, coloradoInput)

    if (startFirstRound) {
        val result = startFirstRound(topdir)
        if (result.isErr) logger.error { result.toString() }
    }
    logger.info { "createColorado2020 took $stopwatch" }
}

