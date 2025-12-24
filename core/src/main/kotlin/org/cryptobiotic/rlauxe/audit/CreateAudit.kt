package org.cryptobiotic.rlauxe.audit


import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.readAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.readSamplePrnsJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.persist.json.writePopulationsJsonFile
import org.cryptobiotic.rlauxe.persist.validateOutputDirOfFile
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.SortMerge
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.createZipFile
import org.cryptobiotic.rlauxe.verify.VerifyResults
import org.cryptobiotic.rlauxe.verify.checkContestsCorrectlyFormed
import org.cryptobiotic.rlauxe.workflow.findSamples
import kotlin.io.path.Path

interface CreateElectionPIF {
    fun contestsUA(): List<ContestWithAssertions>
    fun populations(): List<PopulationIF>?

    // if you immediately write to disk, you only need one pass through the iterator
    fun cardManifest() : CloseableIterator<AuditableCard>
}

class CreateElectionP(
    val contestsUA: List<ContestWithAssertions>,
    val populations: List<PopulationIF>?,
    val cardManifest: List<AuditableCard>
):  CreateElectionPIF {

    override fun contestsUA() = contestsUA
    override fun populations() = populations
    override fun cardManifest() = Closer( cardManifest.iterator() )
}

private val logger = KotlinLogging.logger("CreateAudit")

class CreateAudit(val name: String, val config: AuditConfig, election: CreateElectionPIF, val auditDir: String, clear: Boolean = true) {

    val stopwatch = Stopwatch()

    init {
        if (clear) clearDirectory(Path(auditDir))

        val publisher = Publisher(auditDir)
        writeAuditConfigJsonFile(config, publisher.auditConfigFile())
        logger.info{"CreateAudit writeAuditConfigJsonFile to ${publisher.auditConfigFile()}\n  $config"}

        if (election.populations() != null) {
            writePopulationsJsonFile(election.populations()!!, publisher.populationsFile())
            logger.info { "CreateAudit write ${election.populations()!!.size} populations, to ${publisher.populationsFile()}" }
        }

        val cards = election.cardManifest()
        val countCvrs = writeAuditableCardCsvFile(cards, publisher.cardManifestFile())
        createZipFile(publisher.cardManifestFile(), delete = true)
        logger.info { "CreateAudit write ${countCvrs} cards to ${publisher.cardManifestFile()}" }

        // this may change the auditStatus to misformed
        val contestsUA = election.contestsUA()

        val results = VerifyResults()
        checkContestsCorrectlyFormed(config, contestsUA, results)
        if (results.hasErrors) {
            logger.warn{ results.toString() }
        } else {
            logger.info{ results.toString() }
        }

        // write contests
        writeContestsJsonFile(contestsUA, publisher.contestsFile())
        logger.info{"CreateAudit write ${contestsUA.size} contests to ${publisher.contestsFile()}"}

        // cant write the sorted cards until after seed is generated, after committment to cardManifest
    }
}

fun writeSortedCardsInternalSort(publisher: Publisher, seed: Long) {
    val unsortedCards = readCardsCsvIterator(publisher.cardManifestFile())
    val sortedCards = createSortedCards(unsortedCards, seed)
    val countCards = writeAuditableCardCsvFile(Closer(sortedCards.iterator()), publisher.sortedCardsFile())
    createZipFile(publisher.sortedCardsFile(), delete = true)
    logger.info{"writeSortedCardsInternalSort ${countCards} cards to ${publisher.sortedCardsFile()}"}
}

fun createSortedCards(unsortedCards: CloseableIterator<AuditableCard>, seed: Long) : List<AuditableCard> {
    val prng = Prng(seed)
    val cards = mutableListOf<AuditableCard>()
    unsortedCards.use { cardIter ->
        while (cardIter.hasNext()) {
            cards.add( cardIter.next().copy(prn = prng.next()))
        }
    }
    return cards.sortedBy { it.prn }
}

fun writeSortedCardsExternalSort(topdir: String, publisher: Publisher, seed: Long) {
    val unsortedCards = readCardsCsvIterator(publisher.cardManifestFile())
    writeExternalSortedCards(topdir, publisher.sortedCardsFile(), unsortedCards, seed)
    // logger.info{"write ${unsortedCards.size} cards to ${publisher.sortedCardsFile()}"}
}

fun writeExternalSortedCards(topdir: String, outputFile: String, unsortedCards: CloseableIterator<AuditableCard>, seed: Long) {
    val sorter = SortMerge<AuditableCard>("$topdir/sortChunks", outputFile = outputFile, seed = seed)
    sorter.run(
        cardIter = unsortedCards,
        cvrs = emptyList(),
        toAuditableCard = { from: AuditableCard, index: Int, prn: Long -> from.copy(index = index, prn = prn) }
    )
    createZipFile(outputFile, delete = true)
}

// uses private/sortedMvrs.cvs
fun writeMvrsForRound(publisher: Publisher, round: Int) {
    val resultSamples = readSamplePrnsJsonFile(publisher.samplePrnsFile(round))
    if (resultSamples is Err) logger.error{"$resultSamples"}
    require(resultSamples is Ok)
    val sampleNumbers = resultSamples.unwrap()

    val sortedMvrs = readAuditableCardCsvFile(publisher.privateMvrsFile())

    val sampledMvrs = findSamples(sampleNumbers, Closer(sortedMvrs.iterator()))
    require(sampledMvrs.size == sampleNumbers.size)

    sampledMvrs.forEachIndexed { index, mvr ->
        require(mvr.prn == sampleNumbers[index])
    }

    val countCards = writeAuditableCardCsvFile(Closer(sampledMvrs.iterator()), publisher.sampleMvrsFile(round))
    logger.info{"writeMvrsForRound ${countCards} cards to ${publisher.sampleMvrsFile(round)}"}
}

fun writePrivateMvrs(publisher: Publisher, sortedMvrs: List<AuditableCard>) {
    validateOutputDirOfFile(publisher.privateMvrsFile())
    val countMvrs = writeAuditableCardCsvFile(Closer(sortedMvrs.iterator()), publisher.privateMvrsFile())
    logger.info{"writeSortedMvrs ${countMvrs} mvrs to ${publisher.privateMvrsFile()}"}
}

fun writeUnsortedPrivateMvrs(publisher: Publisher, unsortedMvrs: List<Cvr>, seed: Long) {
    val prng = Prng(seed)
    // 0 based index
    val mvrCards = unsortedMvrs.mapIndexed { index, mvr ->
        AuditableCard.fromCvr(mvr, index, prng.next())
    }
    val sortedMvrs = mvrCards.sortedBy { it.prn }
    writePrivateMvrs(publisher, sortedMvrs)
}
