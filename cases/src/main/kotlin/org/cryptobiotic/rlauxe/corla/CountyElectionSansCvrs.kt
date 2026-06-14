package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.VunderBatches
import org.cryptobiotic.rlauxe.estimate.VunderPool
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIteratorM
import org.cryptobiotic.rlauxe.persist.csv.writeCardCsvFile
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.utils.tabulateCardsAndCount
import kotlin.Int
import kotlin.String
import kotlin.io.path.Path

private val logger = KotlinLogging.logger("CreateCorlaElection")

// County election has the cards divided into disjoint county pools
// Each county has its own set of card styles
// Contests may be shared across counties.
open class CountyElectionSansCvrs (
    val coloradoInput: ColoradoInput,
    val countyElection: CountyContestBuilder,
    val auditType: AuditType,
    val auditdir: String,
    val hasStyle: Boolean,
    val pollingMode: PollingMode?,
    val name: String? = null,
    val onlyCounty: String? = null,
): ElectionBuilder {
    val publisher = Publisher(auditdir)
    val ncards: Int
    val contestsUA: List<ContestWithAssertions>
    val countyPools: List<CountyPools>
    val styles: List<StyleIF>

    init {
        val makePools = MakeCountyPools(countyElection.corlaContestBuilders, coloradoInput, onlyCounty)
        val countyPoolBuilders: List<CountyPoolsBuilder> = makePools.countyPools
        styles = countyPoolBuilders.map { it.pools }.flatten()  // use the pools as styles
        countyPools = countyPoolBuilders.map { it.build() }

        // have to save the mvrs and generate the cardManifest from them.
        ncards = createAndSaveUnsortedMvrs(countyPools, publisher)

        // TODO Npop >= Nc
        val infos = countyElection.contests.map { it.info() }.associateBy { it.id }

        // read them back in as an Iterator, so we dont have to read all into memory
        val auditableCardIter = CardIteratorfromCountyFiles(countyPools, publisher, styles = styles)
        val (manifestTabs, count) = tabulateCardsAndCount(auditableCardIter, infos)
        val npopMap = manifestTabs.mapValues { it.value.ncardsTabulated }

        contestsUA = ContestWithAssertions.make(countyElection.contests, npopMap, auditType.isClca(), hasStyle)
    }

    override fun electionInfo() =
        ElectionInfo(
            name ?: "Corla24$auditType$pollingMode", auditType, ncards(),
            contestsUA.size, pollingMode = pollingMode,
            mvrSource = MvrSource.testPrivateMvrs
        )

    override fun cardStyles(): List<StyleIF>? = styles
    override fun cardPools(): List<CardPoolIF>? = null
    override fun countyCardPools(): List<CountyPoolsIF>? = countyPools
    override fun contestsUA() = contestsUA
    override fun ncards() = ncards

    override fun cards(): CloseableIterator<AuditableCardIF> {
        // should we remove the votes?
        return CardIteratorfromCountyFiles(countyPools, publisher, styles = styles)
    }

    // StartAuditFirstRound will create the sorted MVRs
    override fun createUnsortedMvrsExternal() = CardIteratorfromCountyFiles(countyPools, publisher, styles = styles)
    override fun createUnsortedMvrsInternal() = null

    class CardIteratorfromCountyFiles(
        countyPools: List<CountyPools>,
        publisher: Publisher,
        val styles: List<StyleIF>
    ) : CloseableIterator<AuditableCardM> {

        val dir = publisher.unsortedMvrsDirectory()
        val counties = countyPools.map { it.countyName }.iterator()
        var innerIter = readCardsCsvIteratorM("$dir/${counties.next()}.csv", styles = null)

        override fun next(): AuditableCardM {
            return innerIter.next()
        }

        override fun hasNext(): Boolean {
            if (innerIter.hasNext()) return true
            if (counties.hasNext()) {
                innerIter = readCardsCsvIteratorM("$dir/${counties.next()}.csv", styles = null)
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
    class CvrIteratorfromPools(val countyPool: CountyPools, val startCardno: Int) : Iterator<AuditableCardIF> {
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

        override fun next(): AuditableCardIF {
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

        inner class CardsFromPool(val cardPool: StyleIF) : Iterator<AuditableCardIF> {
            var countCards = 0
            val poolName = cardPool.name()

            override fun next(): AuditableCardIF {
                countCards++
                val card = AuditableCardM.empty(id = "${poolName}.index-${cardno++}", phantom = false, poolName)
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
    name: String? = null,
    onlyCounty: String? = null,
) {
    val stopwatch = Stopwatch()
    clearDirectory(Path(topdir))

    val countyElection = CountyContestBuilder(coloradoInput)

    val election =
        CountyElectionSansCvrs(coloradoInput, countyElection,
            creation.auditType, auditdir, pollingMode=null, name=name,
            hasStyle = roundConfig.sampling.sampling == Sampling.consistent,
            onlyCounty = onlyCounty)

    createElectionRecord(election, auditDir = auditdir, roundConfig.sampling, clear = false) // cants clear because we have the mvrs written
    val config = Config(election.electionInfo(), creation, roundConfig)

    createAuditRecord(config, election, auditDir = auditdir, externalSortDir = topdir, sortManifest = true)

    writeCountyData(topdir, coloradoInput.strataMap.values.toList())
    val contestMap = election.contestsUA.associate { it.contest.info().name to it }
    writeCountyContestData(topdir, contestMap, coloradoInput.countyContestTabs)

    if (startFirstRound) {
        val result = startFirstRound(auditdir)
        if (result.isErr) logger.error { result.toString() }
        logger.info { "createCorlaElection took $stopwatch" }
    }
}


/*
*** District Attorney - 18th Judicial District has multiple counties: [Arapahoe, Elbert]
*** Contest 'State Representative - District 9' has 39637 total cards, but CorlaContestRoundCsv.contestBallotCardCount is 32425 - using totalVotes
number of contestBuilders = 723
done with Adams wrote 508886 cards
  154 -> cand=[0]: 436, cand=[1]: 129, cand=[]: 76,
  494 -> cand=[1]: 59, cand=[0]: 25, cand=[2]: 5, cand=[]: 6,
  548 -> cand=[1]: 53, cand=[0]: 37, cand=[]: 4,
  553 -> cand=[0]: 854, cand=[1]: 569, cand=[]: 106,
  557 -> cand=[0]: 127, cand=[]: 103,
  558 -> cand=[1]: 754, cand=[0]: 657, cand=[]: 118,
  559 -> cand=[1]: 709, cand=[0]: 648, cand=[2]: 31, cand=[3]: 29, cand=[]: 112,
  604 -> cand=[0]: 45, cand=[1]: 30, cand=[2]: 6, cand=[]: 6,
  720 -> cand=[0]: 964, cand=[1]: 392, cand=[]: 173,
2026-06-13 07:54:08.262 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Adams.csv
done with Broomfield wrote 52814 cards
  223 -> cand=[0]: 5, cand=[]: 6,
  717 -> cand=[0]: 1,
  718 -> cand=[0]: 1,
2026-06-13 07:54:09.420 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Broomfield.csv
done with Arapahoe wrote 400993 cards
  480 -> cand=[2]: 174, cand=[1]: 135, cand=[0]: 69, cand=[]: 208,
  484 -> cand=[0]: 224, cand=[]: 142,
  566 -> cand=[0]: 840, cand=[1]: 569, cand=[2]: 37, cand=[]: 128,
  568 -> cand=[0]: 3202, cand=[]: 1235,
  589 -> cand=[0]: 2636, cand=[]: 1802,
  608 -> cand=[0]: 2563, cand=[1]: 1470, cand=[2]: 90, cand=[]: 313,
2026-06-13 07:54:17.574 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Arapahoe.csv
done with Chaffee wrote 15075 cards
2026-06-13 07:54:17.881 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Chaffee.csv
done with Alamosa wrote 16064 cards
2026-06-13 07:54:18.032 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Alamosa.csv
done with Conejos wrote 4685 cards
  21 -> cand=[1]: 2, cand=[0]: 1, cand=[]: 1,
  168 -> cand=[0]: 2, cand=[1]: 1, cand=[]: 1,
  169 -> cand=[0]: 21, cand=[1]: 15, cand=[]: 14,
2026-06-13 07:54:18.246 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Conejos.csv
done with Archuleta wrote 9518 cards
2026-06-13 07:54:18.614 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Archuleta.csv
done with Baca wrote 2333 cards
2026-06-13 07:54:18.698 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Baca.csv
done with Bent wrote 2448 cards
2026-06-13 07:54:18.786 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Bent.csv
done with Boulder wrote 416259 cards
  92 -> cand=[0]: 327, cand=[1]: 226, cand=[]: 115,
  93 -> cand=[0]: 308, cand=[1]: 208, cand=[]: 152,
  94 -> cand=[1]: 242, cand=[0]: 261, cand=[]: 165,
  135 -> cand=[0]: 1350, cand=[1]: 809, cand=[]: 427,
  140 -> cand=[0]: 512, cand=[1]: 198, cand=[]: 30,
  526 -> cand=[0]: 520, cand=[1]: 149, cand=[]: 63,
  527 -> cand=[0]: 3323, cand=[1]: 579, cand=[]: 357,
  528 -> cand=[0]: 388, cand=[1]: 1, cand=[]: 186,
  533 -> cand=[1]: 3246, cand=[0]: 599, cand=[]: 414,
  575 -> cand=[1]: 2060, cand=[0]: 486, cand=[]: 214,
  641 -> cand=[0]: 2212, cand=[1]: 1772, cand=[]: 275,
  699 -> cand=[2]: 750, cand=[1]: 711, cand=[0]: 660, cand=[6]: 667, cand=[3]: 395, cand=[4]: 150, cand=[5]: 120, cand=[]: 1191,
2026-06-13 07:54:24.078 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Boulder.csv
done with Cheyenne wrote 1152 cards
2026-06-13 07:54:24.125 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Cheyenne.csv
done with Clear Creek wrote 6966 cards
2026-06-13 07:54:24.287 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Clear Creek.csv
done with Costilla wrote 2187 cards
2026-06-13 07:54:24.336 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Costilla.csv
done with Crowley wrote 1935 cards
2026-06-13 07:54:24.376 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Crowley.csv
done with Custer wrote 4154 cards
2026-06-13 07:54:24.459 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Custer.csv
done with Delta wrote 24450 cards
2026-06-13 07:54:25.056 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Delta.csv
done with Denver wrote 1173204 cards
2026-06-13 07:54:38.305 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Denver.csv
done with Dolores wrote 1503 cards
2026-06-13 07:54:38.367 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Dolores.csv
done with Douglas wrote 316012 cards
  139 -> cand=[0]: 8, cand=[1]: 11, cand=[]: 2,
  180 -> cand=[1]: 174, cand=[0]: 195, cand=[3]: 92, cand=[4]: 94, cand=[2]: 78, cand=[6]: 57, cand=[5]: 29, cand=[]: 178,
  569 -> cand=[0]: 152, cand=[1]: 119, cand=[]: 28,
  691 -> cand=[1]: 142, cand=[0]: 115, cand=[]: 42,
2026-06-13 07:54:45.166 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Douglas.csv
done with Eagle wrote 32242 cards
  632 -> cand=[1]: 7,
2026-06-13 07:54:45.753 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Eagle.csv
done with Elbert wrote 21936 cards
  169 -> cand=[0]: 9, cand=[1]: 9, cand=[]: 14,
  170 -> cand=[0]: 15, cand=[1]: 14, cand=[]: 10,
  250 -> cand=[0]: 43, cand=[1]: 53, cand=[]: 30,
  251 -> cand=[0]: 42, cand=[1]: 42, cand=[]: 27,
  252 -> cand=[0]: 63, cand=[1]: 37, cand=[]: 44,
  253 -> cand=[0]: 29, cand=[1]: 33, cand=[]: 24,
  254 -> cand=[0]: 48, cand=[1]: 52, cand=[]: 38,
  255 -> cand=[0]: 48, cand=[1]: 57, cand=[]: 42,
  315 -> cand=[1]: 166, cand=[0]: 162, cand=[]: 78,
  316 -> cand=[0]: 45, cand=[1]: 44, cand=[2]: 26, cand=[3]: 19, cand=[]: 72,
  317 -> cand=[0]: 94, cand=[2]: 71, cand=[1]: 68, cand=[]: 95,
  637 -> cand=[0]: 194, cand=[1]: 178, cand=[]: 34,
2026-06-13 07:54:46.208 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Elbert.csv
done with El Paso wrote 421226 cards
  303 -> cand=[1]: 349, cand=[0]: 236, cand=[]: 33,
  321 -> cand=[0]: 461, cand=[1]: 134, cand=[]: 60,
  491 -> cand=[0]: 1466, cand=[1]: 346, cand=[3]: 56, cand=[2]: 36, cand=[4]: 9, cand=[]: 86,
  530 -> cand=[1]: 1547, cand=[0]: 320, cand=[]: 149,
  539 -> cand=[0]: 504, cand=[1]: 469, cand=[]: 68,
  540 -> cand=[1]: 1029, cand=[0]: 819, cand=[]: 168,
  541 -> cand=[0]: 1004, cand=[1]: 874, cand=[]: 138,
  678 -> cand=[0]: 350, cand=[]: 202,
2026-06-13 07:54:56.248 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/El Paso.csv
done with Fremont wrote 29272 cards
  217 -> cand=[0]: 274, cand=[]: 116,
  692 -> cand=[0]: 2,
2026-06-13 07:54:56.886 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Fremont.csv
done with Garfield wrote 31972 cards
  238 -> cand=[0]: 274, cand=[]: 194,
  289 -> cand=[0]: 165, cand=[1]: 95, cand=[]: 90,
2026-06-13 07:54:57.524 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Garfield.csv
done with Gilpin wrote 4282 cards
2026-06-13 07:54:57.602 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Gilpin.csv
done with Grand wrote 10325 cards
  22 -> cand=[0]: 54, cand=[1]: 30, cand=[]: 6,
  23 -> cand=[0]: 57, cand=[1]: 65, cand=[]: 5,
  25 -> cand=[0]: 34, cand=[1]: 10, cand=[]: 4,
  27 -> cand=[0]: 37, cand=[1]: 19, cand=[]: 6,
  28 -> cand=[0]: 41, cand=[1]: 38, cand=[]: 3,
  334 -> cand=[0]: 11, cand=[1]: 5, cand=[]: 1,
  335 -> cand=[0]: 11, cand=[1]: 3, cand=[]: 3,
  336 -> cand=[0]: 7, cand=[1]: 10,
  458 -> cand=[1]: 190, cand=[0]: 76, cand=[]: 16,
  459 -> cand=[0]: 25, cand=[1]: 25, cand=[]: 7,
  460 -> cand=[0]: 32, cand=[1]: 46, cand=[]: 4,
  461 -> cand=[0]: 46, cand=[1]: 36, cand=[]: 5,
  463 -> cand=[0]: 39, cand=[1]: 60, cand=[]: 15,
  464 -> cand=[0]: 123, cand=[1]: 38, cand=[]: 5,
  465 -> cand=[0]: 67, cand=[1]: 76, cand=[]: 6,
  648 -> cand=[1]: 121, cand=[2]: 68, cand=[0]: 68, cand=[4]: 68, cand=[5]: 59, cand=[3]: 29, cand=[]: 109,
  649 -> cand=[0]: 102, cand=[1]: 44, cand=[]: 28,
2026-06-13 07:54:57.781 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Grand.csv
done with Gunnison wrote 11232 cards
  22 -> cand=[0]: 425, cand=[1]: 178, cand=[]: 32,
  23 -> cand=[0]: 205, cand=[1]: 258, cand=[]: 26,
  25 -> cand=[0]: 340, cand=[1]: 126, cand=[]: 40,
  26 -> cand=[0]: 274, cand=[1]: 101, cand=[]: 39,
  27 -> cand=[0]: 330, cand=[1]: 127, cand=[]: 42,
  28 -> cand=[0]: 458, cand=[1]: 191, cand=[]: 35,
  29 -> cand=[1]: 66, cand=[0]: 70, cand=[]: 15,
  194 -> cand=[0]: 356, cand=[1]: 101, cand=[]: 158,
  337 -> cand=[1]: 376, cand=[0]: 206, cand=[]: 33,
  338 -> cand=[1]: 360, cand=[0]: 223, cand=[]: 32,
  339 -> cand=[1]: 342, cand=[0]: 239, cand=[]: 34,
  426 -> cand=[5]: 4,
  457 -> cand=[0]: 348, cand=[1]: 216, cand=[7]: 15, cand=[3]: 3, cand=[4]: 3, cand=[2]: 1, cand=[]: 12,
  458 -> cand=[1]: 466, cand=[0]: 209, cand=[]: 46,
  459 -> cand=[0]: 236, cand=[1]: 216, cand=[]: 33,
  460 -> cand=[0]: 207, cand=[1]: 215, cand=[]: 37,
  461 -> cand=[0]: 198, cand=[1]: 249, cand=[]: 41,
  463 -> cand=[0]: 215, cand=[1]: 233, cand=[]: 30,
  464 -> cand=[0]: 465, cand=[1]: 98, cand=[]: 33,
  465 -> cand=[0]: 350, cand=[1]: 262, cand=[]: 22,
  475 -> cand=[1]: 26, cand=[0]: 26, cand=[3]: 3, cand=[2]: 1, cand=[]: 5,
  476 -> cand=[0]: 30, cand=[1]: 44, cand=[]: 10,
  490 -> cand=[1]: 160, cand=[0]: 292, cand=[2]: 6, cand=[3]: 1, cand=[]: 16,
  529 -> cand=[0]: 106, cand=[1]: 164, cand=[]: 19,
  585 -> cand=[1]: 154, cand=[0]: 312, cand=[]: 36,
  613 -> cand=[1]: 176, cand=[0]: 259, cand=[]: 33,
2026-06-13 07:54:57.999 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Gunnison.csv
done with Hinsdale wrote 637 cards
2026-06-13 07:54:58.014 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Hinsdale.csv
done with Huerfano wrote 4835 cards
2026-06-13 07:54:58.104 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Huerfano.csv
done with Jackson wrote 874 cards
2026-06-13 07:54:58.121 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Jackson.csv
done with Jefferson wrote 436627 cards
  158 -> cand=[0]: 1463, cand=[1]: 397, cand=[]: 273,
  159 -> cand=[0]: 1309, cand=[1]: 616, cand=[]: 208,
  362 -> cand=[1]: 1348, cand=[0]: 668, cand=[]: 117,
  548 -> cand=[1]: 1135, cand=[0]: 864, cand=[]: 133,
  549 -> cand=[0]: 904, cand=[1]: 818, cand=[]: 102,
  560 -> cand=[0]: 126, cand=[1]: 88, cand=[]: 21,
2026-06-13 07:55:07.937 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Jefferson.csv
done with Kiowa wrote 1068 cards
2026-06-13 07:55:07.984 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Kiowa.csv
done with Kit Carson wrote 4028 cards
2026-06-13 07:55:08.079 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Kit Carson.csv
done with Lake wrote 4514 cards
2026-06-13 07:55:08.205 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Lake.csv
done with La Plata wrote 37648 cards
2026-06-13 07:55:09.123 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/La Plata.csv
done with Larimer wrote 521612 cards
  120 -> cand=[0]: 844, cand=[1]: 261, cand=[]: 92,
  121 -> cand=[0]: 684, cand=[1]: 357, cand=[]: 156,
  122 -> cand=[0]: 661, cand=[1]: 350, cand=[]: 186,
  123 -> cand=[0]: 609, cand=[1]: 388, cand=[]: 200,
  491 -> cand=[0]: 417, cand=[1]: 477, cand=[3]: 31, cand=[2]: 15, cand=[4]: 4, cand=[]: 41,
  530 -> cand=[1]: 591, cand=[0]: 520, cand=[]: 86,
  575 -> cand=[1]: 495, cand=[0]: 607, cand=[]: 95,
  579 -> cand=[0]: 640, cand=[1]: 395, cand=[]: 162,
  607 -> cand=[0]: 798, cand=[1]: 35, cand=[]: 364,
2026-06-13 07:55:15.196 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Larimer.csv
done with Las Animas wrote 9247 cards
2026-06-13 07:55:15.411 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Las Animas.csv
done with Lincoln wrote 2791 cards
2026-06-13 07:55:15.468 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Lincoln.csv
done with Logan wrote 10976 cards
2026-06-13 07:55:15.680 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Logan.csv
done with Mesa wrote 106325 cards
2026-06-13 07:55:17.662 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Mesa.csv
done with Mineral wrote 777 cards
2026-06-13 07:55:17.685 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Mineral.csv
done with Moffat wrote 7043 cards
2026-06-13 07:55:17.810 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Moffat.csv
done with Montezuma wrote 17134 cards
2026-06-13 07:55:18.136 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Montezuma.csv
done with Montrose wrote 28552 cards
  196 -> cand=[0]: 1,
  614 -> cand=[1]: 17, cand=[0]: 2, cand=[]: 1,
2026-06-13 07:55:18.736 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Montrose.csv
done with Morgan wrote 15047 cards
  45 -> cand=[1]: 1,
2026-06-13 07:55:19.110 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Morgan.csv
done with Otero wrote 10224 cards
  166 -> cand=[0]: 263, cand=[1]: 164, cand=[]: 143,
  167 -> cand=[0]: 247, cand=[1]: 205, cand=[]: 154,
  168 -> cand=[0]: 276, cand=[1]: 193, cand=[]: 152,
  169 -> cand=[0]: 273, cand=[1]: 185, cand=[]: 148,
  170 -> cand=[0]: 267, cand=[1]: 204, cand=[]: 177,
  171 -> cand=[0]: 177, cand=[1]: 131, cand=[]: 92,
  172 -> cand=[0]: 151, cand=[1]: 130, cand=[]: 113,
  173 -> cand=[0]: 179, cand=[1]: 137, cand=[]: 84,
2026-06-13 07:55:19.303 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Otero.csv
done with Ouray wrote 4225 cards
2026-06-13 07:55:19.395 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Ouray.csv
done with Park wrote 14874 cards
2026-06-13 07:55:19.621 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Park.csv
done with Phillips wrote 2403 cards
  85 -> cand=[2]: 11,
2026-06-13 07:55:19.670 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Phillips.csv
done with Pitkin wrote 12743 cards
2026-06-13 07:55:19.957 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Pitkin.csv
done with Prowers wrote 5503 cards
2026-06-13 07:55:20.060 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Prowers.csv
done with Pueblo wrote 184280 cards
2026-06-13 07:55:22.318 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Pueblo.csv
done with Rio Blanco wrote 5036 cards
2026-06-13 07:55:22.410 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Rio Blanco.csv
done with Rio Grande wrote 6804 cards
2026-06-13 07:55:22.548 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Rio Grande.csv
done with Routt wrote 34891 cards
  651 -> cand=[1]: 8, cand=[0]: 7,
2026-06-13 07:55:22.898 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Routt.csv
done with Saguache wrote 3453 cards
2026-06-13 07:55:22.966 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Saguache.csv
done with San Miguel wrote 5526 cards
2026-06-13 07:55:23.087 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/San Miguel.csv
done with Sedgwick wrote 1379 cards
  457 -> cand=[1]: 4,
2026-06-13 07:55:23.117 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Sedgwick.csv
done with Summit wrote 18209 cards
2026-06-13 07:55:23.522 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Summit.csv
done with Teller wrote 17641 cards
  541 -> cand=[0]: 1,
  598 -> cand=[1]: 1,
  704 -> cand=[0]: 6, cand=[1]: 7, cand=[]: 1,
2026-06-13 07:55:23.935 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Teller.csv
done with Washington wrote 3059 cards
  45 -> cand=[1]: 1,
  219 -> cand=[0]: 17, cand=[]: 4,
  244 -> cand=[0]: 14, cand=[1]: 2, cand=[]: 1,
  591 -> cand=[0]: 4,
2026-06-13 07:55:23.992 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Washington.csv
done with Weld wrote 201431 cards
  129 -> cand=[0]: 2688, cand=[1]: 1577, cand=[]: 317,
  130 -> cand=[0]: 2807, cand=[1]: 1379, cand=[]: 396,
  131 -> cand=[0]: 2428, cand=[1]: 1596, cand=[]: 558,
  132 -> cand=[0]: 2327, cand=[1]: 1626, cand=[]: 629,
  478 -> cand=[0]: 97, cand=[1]: 40, cand=[]: 14,
  479 -> cand=[0]: 158, cand=[1]: 131, cand=[]: 40,
  489 -> cand=[0]: 1138, cand=[1]: 892, cand=[4]: 26, cand=[2]: 10, cand=[3]: 5, cand=[5]: 1, cand=[]: 120,
  526 -> cand=[0]: 2929, cand=[1]: 1301, cand=[]: 348,
  527 -> cand=[0]: 1037, cand=[1]: 388, cand=[]: 112,
  528 -> cand=[0]: 368, cand=[1]: 4, cand=[]: 205,
  577 -> cand=[1]: 1742, cand=[0]: 1660, cand=[]: 228,
  599 -> cand=[0]: 960, cand=[1]: 778, cand=[]: 146,
  602 -> cand=[1]: 769, cand=[0]: 671, cand=[]: 113,
  639 -> cand=[0]: 909, cand=[3]: 796, cand=[1]: 890, cand=[2]: 605, cand=[]: 1402,
  642 -> cand=[1]: 26, cand=[0]: 22, cand=[]: 5,
  670 -> cand=[1]: 1118, cand=[0]: 997, cand=[]: 116,
  671 -> cand=[1]: 1293, cand=[0]: 827, cand=[]: 111,
  672 -> cand=[1]: 909, cand=[4]: 639, cand=[2]: 633, cand=[6]: 625, cand=[0]: 508, cand=[3]: 403, cand=[5]: 337, cand=[]: 2639,
2026-06-13 07:55:28.126 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Weld.csv
done with Yuma wrote 5396 cards
  161 -> cand=[1]: 16, cand=[0]: 17, cand=[]: 1,
  346 -> cand=[0]: 2,
2026-06-13 07:55:28.236 INFO  createAndSaveUnsortedMvrs2 to /home/stormy/rla/cases/auditcenter/County2024General/audit/private/Yuma.csv

 */

