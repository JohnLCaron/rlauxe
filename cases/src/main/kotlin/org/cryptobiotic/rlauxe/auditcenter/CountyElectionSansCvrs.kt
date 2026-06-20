package org.cryptobiotic.rlauxe.auditcenter

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.VunderBatches
import org.cryptobiotic.rlauxe.estimate.VunderPool
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeCardCsvFile
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.utils.tabulateCardsAndCount
import kotlin.Int
import kotlin.String
import kotlin.io.path.Path

private val logger = KotlinLogging.logger("CountyElectionSansCvrs")

// County election has the cards divided into disjoint county pools
// Each county has its own set of card styles
// Contests may be shared across counties.
open class CountyElectionSansCvrs (
    val coloradoInput: ColoradoInput,
    val auditdir: String,
    val hasStyle: Boolean,
    val name: String,
    val onlyCounty: String? = null,
): ElectionBuilder {
    val publisher = Publisher(auditdir)
    val ncards: Int
    val contestsUA: List<ContestWithAssertions>
    val countyPools: List<CountyPools>
    val styles: List<StyleIF>

    init {
        val contestBuilder = CountyContestBuilder(coloradoInput)
        val makePools = MakeCountyPoolsSansCvrs(contestBuilder.corlaContestBuilders, coloradoInput, onlyCounty)
        val countyPoolBuilders: List<CountyPoolsBuilder> = makePools.countyPools
        styles = countyPoolBuilders.map { it.pools }.flatten()  // use the pools as styles
        countyPools = countyPoolBuilders.map { it.build() }

        // have to save the mvrs and generate the cardManifest from them.
        ncards = createAndSaveUnsortedMvrs(countyPools, publisher)

        // TODO Npop >= Nc
        val infos = contestBuilder.contests.map { it.info() }.associateBy { it.id }

        // read them back in as an Iterator, so we dont have to read all into memory
        val auditableCardIter = CardIteratorfromCountyFiles(countyPools, publisher, styles = styles)
        val (manifestTabs, count) = tabulateCardsAndCount(auditableCardIter, infos)
        val npopMap = manifestTabs.mapValues { it.value.ncardsTabulated }

        contestsUA = ContestWithAssertions.make(contestBuilder.contests, npopMap, true, hasStyle)
    }

    override fun electionInfo() =
        ElectionInfo(
            name , AuditType.CLCA, ncards(),
            contestsUA.size,
            mvrSource = MvrSource.testPrivateMvrs
        )

    override fun cardStyles(): List<StyleIF>? = styles
    override fun cardPools(): List<CardPoolIF>? = null
    override fun countyCardPools(): List<CountyPools>? = countyPools
    override fun contestsUA() = contestsUA
    override fun ncards() = ncards

    override fun cards(): CloseableIterator<AuditableCard> {
        // should we remove the votes?
        return CardIteratorfromCountyFiles(countyPools, publisher, styles = styles)
    }

    override fun unsortedMvrsInternal() = null
    override fun unsortedMvrsExternal() = CardIteratorfromCountyFiles(countyPools, publisher, styles = styles)

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
            val poolIterator = CvrIteratorfromPools(countyPool, totalCards)

            // TODO makePhantomCvrs(contests)
            val unsortedMvrIterator = Closer(poolIterator)
            writeCardCsvFile(unsortedMvrIterator, outfile)
            logger.info { "createAndSaveUnsortedMvrs2 to ${outfile}" }
            totalCards = poolIterator.cardno
        }

        return totalCards
    }

    // this is random, cant do more than once. must do mvrs first
    // instead of iterating over each AdjustableStylePool, we should be able to use VunderBunch
    // to use the county Tabs, which are the only real tabs;  AdjustableStylePool are guesses.
    // but we want to use the AdjustableStylePool ncards to generate cards for each style
    // and use the undervotes as variable; but the vote totals should match.
    class CvrIteratorfromPools(val countyPool: CountyPools, val startCardno: Int) : Iterator<AuditableCard> {
        val vunderBatches: VunderBatches
        var cardPoolIter = countyPool.styles.iterator()
        var innerIter = CardsFromPool(cardPoolIter.next())
        var cardno = startCardno

        init {
            // use tab ncards as npop
            val vunders =
                countyPool.contestTabs.map { Pair(it.contestId, it.votesAndUndervotes(null, it.ncards(), true)) }.toMap()
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
    }
}

////////////////////////////////////////////////////////////////////
// Create audit where pools are from the precinct total. May be CLCA or OneAudit
fun createCountyElectionSansCvrs(
    topdir: String,
    auditdir: String,
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
        CountyElectionSansCvrs(coloradoInput,  auditdir, name=name,
            hasStyle = roundConfig.sampling.sampling == Sampling.consistent,
            onlyCounty = onlyCounty)

    createElectionRecord(election, auditDir = auditdir, roundConfig.sampling, clear = false) // cants clear because we have the mvrs written
    val config = Config(election.electionInfo(), creation, roundConfig)

    createAuditRecord(config, election, auditDir = auditdir, externalSortDir = topdir, sortManifest = true)

    writeCountyData(topdir, coloradoInput.strataMap.values.toList())
    val contestMap = election.contestsUA.associate { it.contest.info().name to it }
    writeCountyContestData(topdir, contestMap, coloradoInput.countyTabAllContests)

    if (startFirstRound) {
        val result = startFirstRound(auditdir)
        if (result.isErr) logger.error { result.toString() }
        logger.info { "createCorlaElection took $stopwatch" }
    }
}
