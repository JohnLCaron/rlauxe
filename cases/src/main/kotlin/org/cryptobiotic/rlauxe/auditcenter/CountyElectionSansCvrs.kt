package org.cryptobiotic.rlauxe.auditcenter

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.CvrIteratorfromCountyPools
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeCardCsvFile
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.util.*
import kotlin.Int
import kotlin.String
import kotlin.io.path.Path

private val logger = KotlinLogging.logger("CountyElectionSansCvrs")

// We want to synnthesis cvrs and use them as the cvrPools
// generate countyPools from auditcenter
open class CountyElectionSansCvrs (
    val coloradoInput: ColoradoInput,
    val topdir: String,
    val hasStyle: Boolean,
    val name: String,
    val onlyCounty: String? = null,
): ElectionBuilder {
    val publisher = Publisher(topdir)
    val ncards: Int
    val contestsUA: List<ContestWithAssertions>
    val countyPools: List<CountyPools>
    val allStyles = mutableListOf<StyleIF>() // both countyPools and cvrPools

    val cvrPools = mutableListOf<CountyPools>()

    init {
        // contests made from auditcenter
        val contestBuilder = CountyContestBuilder(coloradoInput)
        val infos = contestBuilder.infos

        // countyTabs from auditcenter was already used by CountyContestBuilder to set official votes, Nc
        // val countyTabs = coloradoInput.countyTabAllContests.associateBy { it.countyName }

        // make the pools from the auditcenter
        val makePools = CountyPoolsSansCvrs(contestBuilder.corlaContestBuilders, coloradoInput, onlyCounty)
        val countyPoolBuilders: List<CountyPoolsBuilder> = makePools.countyPools
        countyPools = countyPoolBuilders.map { it.build() }
        countyPools.forEach {
            allStyles.addAll ( it.styles)
        }
        val lastStyleId = allStyles.maxOf { it.id() }

        // synthesize the cvrs; these have auditcenter styles
        ncards = createAndSaveUnsortedMvrs(countyPools, publisher)

        // read back one county at a time, create the CvrCountyPools and recalc their styles
        var cvrCardCount = 0
        val countyIterator = CvrPoolIteratorfromCountyFiles(countyPools, publisher, infos, lastStyleId+11)
        countyIterator.forEach { cvrPool: CountyPools ->
            cvrCardCount += cvrPool.cardCount
            allStyles.addAll ( cvrPool.styles)
            cvrPools.add(cvrPool)
        }
        if( ncards != cvrCardCount)
            print("$ncards != $cvrCardCount")
        val totalCvrTabs = countyIterator.totalCvrTabs
        val ncast: Map<Int, Int>  = totalCvrTabs.mapValues { it.value.ncards() }
        // just leave it as Ncast = Nc, then the diff goes into the undervote
        val contests = contestBuilder.contests(emptyMap<Int, Int>())

        // where do we get these? difference between the cvr card counts and the contest.Nc = round.contestBallotCardCount
        // can put them is a seperate pool as long as you include them in the unsorted iterator
        val phantoms = makePhantomCards(contests, 0) // TODO

        // use Nc as Npop
        contestsUA = contests.map {
            val contestCvrTab = totalCvrTabs[it.id]
            if (contestCvrTab != null) {
                it.info().metadata["CvrNcards"] = contestCvrTab.ncards().toString()
                it.info().metadata["CvrNvotes"] = contestCvrTab.nvotes().toString()
            }
            ContestWithAssertions(it, true, hasStyle).addStandardAssertions()
        }
    }

    override fun electionInfo() =
        ElectionInfo(
            name , AuditType.CLCA, ncards(),
            contestsUA.size,
            mvrSource = MvrSource.testPrivateMvrs
        )

    override fun cardStyles(): List<StyleIF>? = allStyles
    override fun cardPools(): List<CardPoolIF>? = null
    override fun countyCardPools(): List<CountyPools>? = countyPools
    override fun countyCvrPools(): List<CountyPools>? = cvrPools
    override fun contestsUA() = contestsUA
    override fun ncards() = ncards

    override fun cards(): CloseableIterator<AuditableCard> {
        // should we remove the votes?
        return CardIteratorfromCountyFiles(countyPools, publisher, styles = allStyles)
    }

    override fun unsortedMvrsInternal() = null
    override fun unsortedMvrsExternal() = CardIteratorfromCountyFiles(countyPools, publisher, styles = allStyles)

    // read each county's generated cvrs in and create the "cvr" CountyPool out of it
    // since the cvr generation is approximate, we can compare it with the original
    class CvrPoolIteratorfromCountyFiles(
        acPools: List<CountyPools>, // from the auditcenter
        publisher: Publisher,
        val infos: Map<Int, ContestInfo>,
        startingStyleId: Int
    ) : Iterator<CountyPools> {

        val dir = publisher.unsortedMvrsDirectory()
        val countyPoolIterator = acPools.iterator()
        var cardStyleId = startingStyleId

        val totalCvrTabs = mutableMapOf<Int, ContestTabulation>() // total over counties

        override fun next(): CountyPools {
            val countyPool = countyPoolIterator.next()
            val countyName = countyPool.countyName

            // these are the cvrs we generated
            val cardIter = readCardsCsvIterator("$dir/${countyName}.csv", styles = null)

            // the styles are from the CountyPools, but we want to replace them with the ones in the cvrs, and accurate count.
            // TODO also we want to replace the CountyPools styles with the cvr styles I think ??
            // in order to do that we will do the tabulateAuditableCards here and accumulate the styles actually used

            // could also make CardTabulationAndStyles, just need to supply the style name and id ??
            val cvrCardStyles = mutableMapOf<Set<Int>, CardStyle>()
            var cvrCount = 0
            val tabulous = CardTabulation(cardIter, infos) { card: AuditableCard ->
                val contestIdSet = card.contestIds.toSet()
                val cvrCardStyle = cvrCardStyles.getOrPut(contestIdSet) { CardStyle("$countyName-cvrstyle$cardStyleId", cardStyleId++, card.contestIds, true) }
                cvrCardStyle.ncards++
                cvrCount++
            }
            totalCvrTabs.sumContestTabulations(tabulous.tabs)

            return CountyPools(countyName, countyPool.countyPoolId, tabulous.tabs, cvrCount, cvrCardStyles.values.toList())
        }

        override fun hasNext(): Boolean {
            return countyPoolIterator.hasNext()
        }
    }

    class CardIteratorfromCountyFiles(
        countyPools: List<CountyPools>,
        publisher: Publisher,
        val styles: List<StyleIF>
    ) : CloseableIterator<AuditableCard> {

        val dir = publisher.unsortedMvrsDirectory()
        val counties = countyPools.map { it.countyName }.iterator()
        var innerIter = readCardsCsvIterator("$dir/${counties.next()}.csv", styles = null)

        override fun next(): AuditableCard {
            return innerIter.next()
        }

        override fun hasNext(): Boolean {
            if (innerIter.hasNext()) return true
            if (counties.hasNext()) {
                innerIter = readCardsCsvIterator("$dir/${counties.next()}.csv", styles = null)
                return hasNext()
            }
            return false
        }

        override fun close() {
            // NOOP
        }
    }

    fun createAndSaveUnsortedMvrs(
        countyPools: List<CountyPools>,
        publisher: Publisher
    ): Int {
        val dir = publisher.unsortedMvrsDirectory()
        validateOutputDir(Path(dir))
        var totalCards = 0
        countyPools.forEach { countyPool ->
            val outfile = "$dir/${countyPool.countyName}.csv"
            val poolIterator = CvrIteratorfromCountyPools(countyPool, totalCards)

            // TODO makePhantomCvrs(contests)
            val unsortedMvrIterator = Closer(poolIterator)
            writeCardCsvFile(unsortedMvrIterator, outfile)
            logger.info { "createAndSaveUnsortedMvrs to ${outfile}" }
            totalCards = poolIterator.cardno
        }

        return totalCards
    }

    // this is random, cant do more than once. must do mvrs first
    // instead of iterating over each AdjustableStylePool, we can use VunderBatches
    // to constrain the votes to the county Tabulation, which are the only real tabs;  (AdjustableStylePool are guesses).
    // but we want to use the AdjustableStylePool ncards to generate cards for each style
    // and use the undervotes as variable; but the vote totals should match.
    /* class CvrIteratorfromPools(val countyPool: CountyPools, val startCardno: Int) : Iterator<AuditableCard> {
        val vunderBatches: VunderBatches // tracks all the cvrs for this county
        var cardPoolIter = countyPool.styles.iterator()
        var innerIter = CardsFromPool(cardPoolIter.next())
        var cardno = startCardno

        init {
            // use tab ncards as npop
            val vunders =
                countyPool.contestTabs.mapValues { it.value.votesAndUndervotes(null, it.value.ncards(), true) }
            val onePool = VunderPool(vunders, countyPool.countyName, countyPool.countyPoolId, true)
            vunderBatches = VunderBatches(countyPool.styles, onePool)
        }

        override fun next(): AuditableCard {
            return innerIter.next()
        }

        override fun hasNext(): Boolean {
            if (innerIter.hasNext()) return true
            if (cardPoolIter.hasNext()) {
                innerIter = CardsFromPool(cardPoolIter.next())
                return hasNext()
            }
            // should be all done with this county
            println("done with ${countyPool.countyName} wrote ${cardno - startCardno} cards")
            vunderBatches.onePool.vunderPickers.values.forEach { picker ->
                if (picker.isNotEmpty()) {
                    print("  ${picker.vunder.contestId} -> ")
                    picker.vunderRemaining.forEach { choice ->
                        if (choice.remaining > 0) print("cand=${choice.cands.contentToString()}: ${choice.remaining}, ")
                    }
                    println()
                }
            }
            return false
        }

        // create n cards of the given style; constrain the votes to a common batch
        inner class CardsFromPool(val cardPool: StyleIF) : Iterator<AuditableCard> {
            var countCards = 0
            val poolName = cardPool.name()

            override fun next(): AuditableCard {
                countCards++
                val card = AuditableCard.empty(id = "${poolName}.index-${cardno++}", phantom = false, styleId=cardPool.id())
                return vunderBatches.simulatePooledCard(card)
            }

            override fun hasNext() = countCards < cardPool.ncards()
        }
    } */
}

////////////////////////////////////////////////////////////////////
// Create audit where pools are from the precinct total. May be CLCA or OneAudit
fun createCountyElectionSansCvrs(
    topdir: String,
    coloradoInput: ColoradoInput,
    creation: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    startFirstRound: Boolean = true,
    name: String,
    onlyCounty: String? = null,
) {
    val stopwatch = Stopwatch()
    clearDirectory(Path(topdir))

    val election =
        CountyElectionSansCvrs(coloradoInput,  topdir, name=name,
            hasStyle = roundConfig.sampling.sampling == Sampling.consistent,
            onlyCounty = onlyCounty)

    createElectionRecord(election, topdir = topdir, roundConfig.sampling, clear = false) // cants clear because we have the mvrs written
    val config = Config(election.electionInfo(), creation, roundConfig)

    createAuditRecord(config, election, topdir = topdir, externalSortDir = topdir, sortManifest = true)

    writeCountyData(topdir, coloradoInput.strataMap.values.toList())
    val contestMap = election.contestsUA.associate { it.contest.info().name to it }
    writeCountyContestData(topdir, contestMap, coloradoInput)

    if (startFirstRound) {
        val result = startFirstRound(topdir)
        if (result.isErr) logger.error { result.toString() }
        logger.info { "createCorlaElection took $stopwatch" }
    }
}
