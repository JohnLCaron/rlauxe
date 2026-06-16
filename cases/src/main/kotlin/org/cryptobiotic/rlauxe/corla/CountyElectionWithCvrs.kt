package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.DominionConverter
import org.cryptobiotic.rlauxe.dominion.DominionCvrExport
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportReader
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIteratorM
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
    val contestBuilder: CountyContestBuilder, // TODO how does export schema compare to this?
    val auditdir: String,
    val hasStyle: Boolean,
    val name: String,
): ElectionBuilder {
    val ncards: Int
    val contestsUA: List<ContestWithAssertions>
    val cardStyles = mutableListOf<CardStyle>()
    val countyCardPools = mutableListOf<CountyPoolsIF>()
    val publisher = Publisher(auditdir)

    init {
        val contests = contestBuilder.contests
        val infos = contests.map { it.info() }.associateBy{ it.id }
        val phantoms = makePhantomCards(contests, 0) // TODO
        var totalCardCount = 0
        val totalTabs = mutableMapOf<Int, ContestTabulation>()
        var countyPoolId = 1

        counties.forEach { (county, exportFile) ->
            val export: DominionCvrExport = DominionCvrExportReader(exportFile).read()
            val dominionConverter = DominionConverter(county, export, contests, coloradoInput)

            // read into memory
            val exportCvrs: List<AuditableCard> = export.cvrs.map { dominionConverter.convertToCard(it) }
            val (tabs, cardCount) = tabulateCardsAndCount(Closer (exportCvrs.iterator() ), infos)

            // write them out while we have them
            val ncards = writeUnsortedMvrs(county, publisher,Closer (exportCvrs.iterator() ))
            require(ncards == cardCount)
            totalCardCount += cardCount
            totalTabs.sumContestTabulations(tabs)

            val countyCardStyles = dominionConverter.cardStyles.values.toList()
            countyCardPools.add(
                CountyPools(county, countyPoolId++, tabs.values.toList(), cardCount, countyCardStyles)
            )
            cardStyles.addAll(countyCardStyles)
        }

        this.ncards = totalCardCount
        val npopMap = totalTabs.mapValues { it.value.ncardsTabulated }
        contestsUA = ContestWithAssertions.make(contestBuilder.contests, npopMap, true, hasStyle)
    }

    override fun electionInfo() =
        ElectionInfo(
            name, AuditType.CLCA, ncards(),
            contestsUA.size,
            mvrSource = MvrSource.testPrivateMvrs
        )

    override fun contestsUA() = contestsUA
    override fun cardStyles() = cardStyles

    override fun cardPools() = null
    override fun countyCardPools(): List<CountyPoolsIF>? = countyCardPools

    override fun unsortedMvrsInternal() = null
    override fun unsortedMvrsExternal() = CardIteratorfromCountyMvrs(publisher, styles = cardStyles)

    // TODO do we need to munge the mvrs for the card manifest? Add the card styles ??
    override fun cards() = CardIteratorfromCountyMvrs(publisher, styles = cardStyles)
    override fun ncards() = ncards
}

fun writeUnsortedMvrs(
    county: String,
    publisher: Publisher,
    countyMvrs: CloseableIterator<AuditableCard>
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
    var innerIter = readCardsCsvIteratorM(counties.next().toString(), styles = styles)  // TODO do we need styles ??

    override fun next(): AuditableCard {
        return innerIter.next()
    }

    override fun hasNext(): Boolean {
        if (innerIter.hasNext()) return true
        if (counties.hasNext()) {
            innerIter = readCardsCsvIteratorM(counties.next().toString(), styles = styles)
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

