package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.CvrIF
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.persist.csv.readAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCardPoolCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.readPopulationsJsonFileUnwrapped
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.Closer
import java.nio.file.Files
import kotlin.io.path.Path

private val logger = KotlinLogging.logger("PersistedMvrManager")
private val checkValidity = true

// assumes that the mvrs have been set externally into the election record, eg by EnterMvrsCli.
// skip writing when doing runRoundAgain
open class PersistedMvrManager(val auditDir: String, val electionInfo: ElectionInfo, val config: AuditConfig,
                               val contestsUA: List<ContestWithAssertions>, val mvrWrite: Boolean = true): MvrManager {
    val publisher = Publisher(auditDir)
    val cardManifest = readCardManifest(publisher, electionInfo.ncards)

    override fun cardManifest() = cardManifest

    override fun oapools(): List<OneAuditPoolFromCvrs>?  {
        val infos = contestsUA.associate { it.id to it.contest.info()  }
        return readCardPools(publisher, infos)
    }

    // AuditableCardCsv, complete mvrs used for this round; matches samplePrnsX.csv
    override fun makeMvrCardPairsForRound(round: Int): List<Pair<CvrIF, AuditableCard>>  {
        val mvrsForRound = readMvrsForRound(round)
        val sampleNumbers = mvrsForRound.map { it.prn }

        val sampledCards = findSamples(sampleNumbers, auditableCards())
        require(sampledCards.size == mvrsForRound.size)

        if (checkValidity) {
            // prove that sampledCvrs correspond to mvrsForRound
            mvrsForRound.forEachIndexed { index, mvr ->
                val card = sampledCards[index]
                require(mvr.location == card.location) { "mvr location ${mvr.location} != card.location ${card.location}"}
                require(mvr.prn == card.prn)  { "mvr prn ${mvr.prn} != card.prn ${card.prn}"}
                require(mvr.index == card.index)  { "mvr index ${mvr.index} != card.index ${card.index}"}
            }
        }

        if (mvrWrite) {
            val countCards = writeAuditableCardCsvFile(Closer(sampledCards.iterator()), publisher.sampleCardsFile(round)) // sampleCards
            logger.info { "write ${countCards} cards to ${publisher.sampleCardsFile(round)}" }
        }

        return mvrsForRound.zip(sampledCards)
    }

    // the sampleMvrsFile is added externally for real audits, and by MvrManagerTestFromRecord for test audits
    // it must be in the same order as the sorted cards
    // it is placed into publisher.sampleMvrsFile, and this just reads from that file.
    // AuditableCardCsv, complete mvrs used for this round; matches samplePrnsX.csv
    private fun readMvrsForRound(round: Int): List<AuditableCard> {
        return readAuditableCardCsvFile(publisher.sampleMvrsFile(round))
    }

    fun auditableCards(): CloseableIterator<AuditableCard> = cardManifest.cards.iterator()
}

fun makeCardIter(publisher: Publisher): CloseableIterator<AuditableCard> {

    if (Files.exists(Path(publisher.populationsFile()))) {
        val populations = readPopulationsJsonFileUnwrapped(publisher.populationsFile())
        if (populations.isNotEmpty()) {
            // merge population references into the Card
            return MergePopulationsFromIterator(
                    readCardsCsvIterator(publisher.sortedCardsFile()),
                    populations,
                )
        }
    }

    return readCardsCsvIterator(publisher.sortedCardsFile())
}

fun readCardManifest(publisher: Publisher, ncards: Int): CardManifest {

    if (Files.exists(Path(publisher.populationsFile()))) {
        val populations = readPopulationsJsonFileUnwrapped(publisher.populationsFile())
        if (populations.isNotEmpty()) {
            // merge population references into the Card
            val mergedCards =
                MergePopulationsFromIterable(
                    CloseableIterable { readCardsCsvIterator(publisher.sortedCardsFile()) },
                    populations,
                )

            return CardManifest(mergedCards, ncards, populations)
        }
    }

    val sortedCards = CloseableIterable { readCardsCsvIterator(publisher.sortedCardsFile()) }
    return CardManifest(CloseableIterable { sortedCards.iterator() }, ncards, emptyList())
}

fun readPopulations(publisher: Publisher): List<PopulationIF>? {
    return if (!Files.exists(Path(publisher.populationsFile()))) null else
        readPopulationsJsonFileUnwrapped(publisher.populationsFile())
}

fun readCardPools(publisher: Publisher, infos: Map<Int, ContestInfo>): List<OneAuditPoolFromCvrs>? {
    return if (!Files.exists(Path(publisher.cardPoolsFile()))) null else
        readCardPoolCsvFile(publisher.cardPoolsFile(), infos)
}

// for viewer
fun readMvrsForRound(publisher: Publisher, roundIdx: Int): List<AuditableCard> {
    return readAuditableCardCsvFile(publisher.sampleMvrsFile(roundIdx))
}