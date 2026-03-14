package org.cryptobiotic.rlauxe.audit

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.estimate.OnlyTask
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.readSamplePrnsJsonFile
import org.cryptobiotic.rlauxe.persist.validateOutputDirOfFile
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.SortMerge
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.createZipFile
import org.cryptobiotic.rlauxe.verify.VerifyResults
import org.cryptobiotic.rlauxe.verify.checkContestsCorrectlyFormed
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode
import org.cryptobiotic.rlauxe.workflow.findSamples
import java.io.File
import java.nio.file.Files.notExists
import java.nio.file.Path
import kotlin.use

private val logger = KotlinLogging.logger("StartAudit")

// one could rerun this to overwrite config and sorted cards, using the same election record
// one could reverse
fun createAuditRecord(config: AuditConfig, election: CreateElectionIF, auditDir: String, externalSortDir: String? = null) {
    val publisher = Publisher(auditDir)

    // write AuditConfig and AuditCreationConfig
    publisher.writeAuditConfig(config)

    if (externalSortDir == null) {
        sortManifestInternal(publisher, config.seed)
    } else {
        sortManifestExternal(externalSortDir, publisher, config.seed)
    }

    // save Mvrs for testing and diagnostics
    // cant write the sorted mvrs until after sortedCards is written
    if (config.persistedWorkflowMode == PersistedWorkflowMode.testPrivateMvrs) {
        val unsortedMvrs = election.createUnsortedMvrsInternal()
        if (unsortedMvrs != null) {
            writePrivateMvrsInternal(publisher, unsortedMvrs, seed = config.seed)
        } else {
            val unsortedCards = election.createUnsortedMvrsExternal()
            if (unsortedCards != null) {
                writeSortedCardsExternal(externalSortDir!!, publisher.sortedMvrsFile(), unsortedCards, seed = config.seed)
                logger.info { "createAuditRecord wrotePrivateMvrs to ${publisher.sortedMvrsFile()}" }
            } else {
                logger.warn { "createAuditRecord did not create private mvrs not available for ${election.javaClass} auditType ${config.auditType}" }
            }
        }
    }
}

fun startFirstRound(auditDir: String, onlyTask: OnlyTask? = null): Result<AuditRoundIF, ErrorMessages> {
    val errs = ErrorMessages("startFirstRound")

    try {
        if (notExists(Path.of(auditDir))) {
            return errs.add( "audit Directory $auditDir does not exist" )
        }

        // delete any roundX subdirectories
        val auditDirFile = File(auditDir)
        auditDirFile.walkTopDown()
            .filter { it.isDirectory }
            .filter { it.name.startsWith("round") }
            .forEach {
                val ret = it.deleteRecursively()
                println("deleted $it = $ret")
                println()
            }

        val auditRecord = AuditRecord.readFrom(auditDir)
        if (auditRecord == null) {
            return errs.add("directory '$auditDir' does not contain an audit record")
        }
        require(auditRecord is AuditRecord)

        val workflow = PersistedWorkflow(auditRecord)
        val roundIdx = 1

        //// heres where we can remove contests as needed
        // this may change the auditStatus to misformed.
        val results = VerifyResults()
        checkContestsCorrectlyFormed(auditRecord.config, auditRecord.contests, results)
        if (results.hasErrors) {
            logger.warn{ results.toString() }
        } else {
            logger.info{ results.toString() }
        }

        // start next round and estimate sample sizes
        logger.info { "startFirstRound using ${workflow}" }
        val roundStopwatch = Stopwatch()
        val nextRound = workflow.startNewRound(quiet = false, onlyTask)

        // get matching mvrs if needed
        if (auditRecord.config.persistedWorkflowMode == PersistedWorkflowMode.testPrivateMvrs) {
            val publisher = Publisher(auditDir)
            val ncards = writeMvrsForRound(publisher, roundIdx)
            logger.info{"writeMvrsForRound ${ncards} cards to ${publisher.sampleMvrsFile(roundIdx)}"}
        }

        logger.info { "startFirstRound took ${roundStopwatch}: ${nextRound.show()}" }
        return Ok(nextRound)

    } catch (t: Throwable) {
        logger.error(t) { "runRoundResult Exception" }
        return errs.add( t.message ?: t.toString())
    }
}

// assumes that the CardManifest has already been written
fun sortManifestInternal(publisher: Publisher, seed: Long) {
    val unsortedCards = readCardsCsvIterator(publisher.cardManifestFile())
    val sortedCards = createSortedCardsInternal(unsortedCards, seed)
    val countCards = writeAuditableCardCsvFile(Closer(sortedCards.iterator()), publisher.sortedCardsFile())
    createZipFile(publisher.sortedCardsFile(), delete = true)
    logger.info{"writeSortedCardsInternalSort ${countCards} cards to ${publisher.sortedCardsFile()}"}
}

fun createSortedCardsInternal(unsortedCards: CloseableIterator<AuditableCard>, seed: Long) : List<AuditableCard> {
    val prng = Prng(seed)
    val cards = mutableListOf<AuditableCard>()
    unsortedCards.use { cardIter ->
        while (cardIter.hasNext()) {
            cards.add( cardIter.next().copy(prn = prng.next()))
        }
    }
    return cards.sortedBy { it.prn }
}

// assumes that the CardManifest has already been written
fun sortManifestExternal(topdir: String, publisher: Publisher, seed: Long) {
    val unsortedCards = readCardsCsvIterator(publisher.cardManifestFile())
    writeSortedCardsExternal(topdir, publisher.sortedCardsFile(), unsortedCards, seed)
}

fun writeSortedCardsExternal(topdir: String, outputFile: String, unsortedCards: CloseableIterator<AuditableCard>, seed: Long, zip: Boolean = true) {
    val sorter = SortMerge<AuditableCard>("$topdir/sortChunks", outputFile = outputFile, seed = seed)
    sorter.run(
        cardIter = unsortedCards,
        toAuditableCard = { from: AuditableCard, index: Int, prn: Long -> from.copy(index = index, prn = prn) }
    )
    if (zip) createZipFile(outputFile, delete = true)  // TODO delete
}

// uses private/sortedMvrsFile.cvs
fun writeMvrsForRound(publisher: Publisher, round: Int): Int {
    val resultSamples = readSamplePrnsJsonFile(publisher.samplePrnsFile(round))
    if (resultSamples.isErr) logger.error{"$resultSamples"}
    require(resultSamples.isOk)
    val sampleNumbers = resultSamples.unwrap()

    val sortedMvrs = readCardsCsvIterator(publisher.sortedMvrsFile())

    val sampledMvrs = findSamples(sampleNumbers, sortedMvrs)
    require(sampledMvrs.size == sampleNumbers.size)

    sampledMvrs.forEachIndexed { index, mvr ->
        require(mvr.prn == sampleNumbers[index])
    }

    return writeAuditableCardCsvFile(Closer(sampledMvrs.iterator()), publisher.sampleMvrsFile(round))
}

fun writePrivateMvrsInternal(publisher: Publisher, unsortedMvrs: List<Cvr>, seed: Long) {
    val prng = Prng(seed)
    // 0 based index
    val mvrCards = unsortedMvrs.mapIndexed { index, mvr ->
        AuditableCard.fromCvr(mvr, index, prng.next())
    }
    val sortedMvrs = mvrCards.sortedBy { it.prn }
    writePrivateMvrs(publisher, sortedMvrs)
}

fun writePrivateMvrs(publisher: Publisher, sortedMvrs: List<AuditableCard>) {
    validateOutputDirOfFile(publisher.sortedMvrsFile())
    val countMvrs = writeAuditableCardCsvFile(Closer(sortedMvrs.iterator()), publisher.sortedMvrsFile())
    logger.info{"writeSortedMvrs ${countMvrs} mvrs to ${publisher.sortedMvrsFile()}"}
}



/* could test if cards are in memory
fun writeExternalSortedCards(topdir: String, publisher: Publisher, unsortedCards: CloseableIterator<AuditableCard>, seed: Long, zip: Boolean = true) {
    validateOutputDirOfFile(publisher.privateMvrsFile())
    writeExternalSortedCards(topdir, publisher.privateMvrsFile(), unsortedCards, seed)
} */
