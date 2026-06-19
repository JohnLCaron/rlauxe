package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.DominionConverter
import org.cryptobiotic.rlauxe.dominion.DominionCvrCsvSummary
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportCsvReader
import org.cryptobiotic.rlauxe.dominion.GarfieldCsvReader
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeCardCsvFile
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.utils.tabulateCardsAndCount
import java.nio.file.Path
import kotlin.Int
import kotlin.String
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

private val logger = KotlinLogging.logger("CountyElectionWithCvrs")

open class CountyElectionWithCvrs (
    val counties: Map<String, String>, // countyName -> exportFile
    val coloradoInput: ColoradoInput,
    val contestBuilder: CountyContestBuilder, // from coloradoInput (auditcenter data)
    val auditdir: String,
    val hasStyle: Boolean,
    val name: String,
): ElectionBuilder {
    val ncards: Int
    val contestsUA: List<ContestWithAssertions>
    val cardStyles = mutableListOf<CardStyle>()
    val countyPools = mutableListOf<CountyPools>()

    //val styles = mutableListOf<CardStyle>()
    //val countyPools = mutableListOf<CountyPools>()
    val publisher = Publisher(auditdir)

    init {
        val contests = contestBuilder.contests
        val infos = contests.map { it.info() }.associateBy{ it.id }
        val infosByName = contests.map { it.info() }.associateBy{ it.name }

        // from auditcenter, duplicate SansCvrs
        // val makePools = MakeCountyPoolsSansCvrs(contestBuilder.corlaContestBuilders, coloradoInput)
        // val countyPoolBuilders: List<CountyPoolsBuilder> = makePools.countyPools
        // styles = countyPoolBuilders.map { it.pools }.flatten()  // use the pools as styles
        // countyPools = countyPoolBuilders.map { it.build() }

        val countyTabMap = coloradoInput.countyContestTabs.associateBy { it.countyName }

        val totalPoolTabs = mutableMapOf<Int, ContestTabulation>() // total over counties
         //countyPools.forEach { countyPool -> totalPoolTabs.sumContestTabulations(  countyPool.contestTabs.associateBy { it.contestId } ) }
        //val totalPoolCardCount = countyPools.sumOf { it.cardCount }

        var totalCvrCardCount = 0
        val totalCvrTabs = mutableMapOf<Int, ContestTabulation>() // total over counties
        var countyPoolId = 1
        counties.forEach { (county, exportFile) ->
            //// the cvrs
            val export: DominionCvrCsvSummary = if (county == "Garfield") GarfieldCsvReader(exportFile).read() else
                DominionCvrExportCsvReader(exportFile).read()
            val dominionConverter = DominionConverter(county, export, contests, coloradoInput)
            val exportCvrs: List<AuditableCard> = export.cvrs.map { dominionConverter.convertToCard(it) }
            val (cvrTabs, cvrCount) = tabulateCardsAndCount(Closer (exportCvrs.iterator() ), infos)
            totalCvrTabs.sumContestTabulations(cvrTabs)
            totalCvrCardCount += cvrCount

            // write them out while we have them
            writeUnsortedMvrs(county, publisher,Closer (exportCvrs.iterator() ))

            // Get the card styles from the cvrs
            val countyCardStyles: List<CardStyle> = dominionConverter.cardStyles.values.toList()

            // make the county pool from auditcenter plus cvr cardStyles
            // cvrTabs: Map<Int, ContestTabulation>
            val ncards = cvrTabs.map { (contestId, contestTab) ->
                Pair(infos[contestId]!!.name, contestTab.ncards()) // use cvr ncards is the best we can do
            }.toMap()
            val countyTab: CountyContestTabs = countyTabMap[county]!!
            val contestTabs = countyTab.makeContestTabs(infosByName, ncards)
            countyPools.add(
                CountyPools(county, countyPoolId++, contestTabs, cvrCount, countyCardStyles)
            )
            cardStyles.addAll(countyCardStyles)
            totalPoolTabs.sumContestTabulations(contestTabs.associateBy { it.contestId })
        }

        // where do we get these? difference between the cvr card counts and the contest.Nc = round.contestBallotCardCount
        // can put them is a seperate pool as long as you include them in the unsorted iterator
        val phantoms = makePhantomCards(contests, 0) // TODO

        this.ncards = totalCvrCardCount // or totalPoolCardCount?
        // use Nc as Npop
        contestsUA = contestBuilder.contests.map {
            ContestWithAssertions(it, true, hasStyle).addStandardAssertions()
        }
    }

    override fun electionInfo() =
        ElectionInfo(
            name, AuditType.CLCA, ncards(),
            contestsUA.size,
            mvrSource = MvrSource.testPrivateMvrs
        )

    override fun contestsUA() = contestsUA
    override fun cardStyles(): List<StyleIF> = cardStyles

    override fun cardPools() = null
    override fun countyCardPools(): List<CountyPools> = countyPools

    override fun unsortedMvrsInternal() = null
    override fun unsortedMvrsExternal() = CardIteratorfromCountyMvrs(publisher, styles = cardStyles)

    // TODO do we need to munge the mvrs for the card manifest? Add the card styles ??
    override fun cards() = CardIteratorfromCountyMvrs(publisher, styles = cardStyles)
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
) {
    val stopwatch = Stopwatch()
    val auditdir = "$topdir/audit"
    clearDirectory(Path(topdir))

    val contestBuilder = CountyContestBuilder(coloradoInput)

    val election =
        CountyElectionWithCvrs(counties, coloradoInput, contestBuilder,
            auditdir, name=name, hasStyle = roundConfig.sampling.sampling == Sampling.consistent)

    createElectionRecord(election, auditDir = auditdir, roundConfig.sampling, clear = false)
    val config = Config(election.electionInfo(), creation, roundConfig)

    createAuditRecord(config, election, auditDir = auditdir, externalSortDir = topdir)

    writeCountyData(topdir, coloradoInput.strataMap.values.toList())
    val contestMap = election.contestsUA.associate { it.contest.info().name to it }
    writeCountyContestData(topdir, contestMap, coloradoInput.countyContestTabs)

    if (startFirstRound) {
        val result = startFirstRound(auditdir)
        if (result.isErr) logger.error { result.toString() }
    }
    logger.info { "createColorado2020 took $stopwatch" }
}

