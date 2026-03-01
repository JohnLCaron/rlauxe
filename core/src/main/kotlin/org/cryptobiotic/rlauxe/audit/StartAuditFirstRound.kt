package org.cryptobiotic.rlauxe.audit

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.estimate.OnlyTask
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.readSamplePrnsJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
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
fun createAuditRecord(config: AuditConfig, election: CreateElectionIF, auditDir: String, externalSortDir: String? = null) {
    val publisher = Publisher(auditDir)

    writeAuditConfigJsonFile(config, publisher.auditConfigFile())
    logger.info{"createAuditRecord writeAuditConfigJsonFile to ${publisher.auditConfigFile()}\n  $config"}

    if (externalSortDir == null) {
        writeSortedCardsInternalSort(publisher, config.seed)
    } else {
        writeSortedCardsExternalSort(externalSortDir, publisher, config.seed)
    }

    // cant write the sorted mvrs until after sortedCards is written
    if (config.persistedWorkflowMode == PersistedWorkflowMode.testPrivateMvrs) {
        val unsortedMvrs = election.createUnsortedMvrs()
        writeUnsortedPrivateMvrs(publisher, unsortedMvrs, seed = config.seed)
        logger.info{"createAuditRecord writeUnsortedPrivateMvrs to ${publisher.privateMvrsFile()}"}
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
        // probably should overwrite round1 and delete other rounds ??

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

        // this writes auditEstFile and samplePrns files
        val nextRound = workflow.startNewRound(quiet = false, onlyTask)

        // save testPrivateMvrs if needed
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
}

fun writeExternalSortedCards(topdir: String, outputFile: String, unsortedCards: CloseableIterator<AuditableCard>, seed: Long) {
    val sorter = SortMerge<AuditableCard>("$topdir/sortChunks", outputFile = outputFile, seed = seed)
    sorter.run(
        cardIter = unsortedCards,
        toAuditableCard = { from: AuditableCard, index: Int, prn: Long -> from.copy(index = index, prn = prn) }
    )
    createZipFile(outputFile, delete = true)
}

// uses private/sortedMvrs.cvs
fun writeMvrsForRound(publisher: Publisher, round: Int): Int {
    val resultSamples = readSamplePrnsJsonFile(publisher.samplePrnsFile(round))
    if (resultSamples.isErr) logger.error{"$resultSamples"}
    require(resultSamples.isOk)
    val sampleNumbers = resultSamples.unwrap()

    // TODO this reads entire list into memory: use readCardsCsvIterator I think
    val sortedMvrs = readAuditableCardCsvFile(publisher.privateMvrsFile())

    val sampledMvrs = findSamples(sampleNumbers, Closer(sortedMvrs.iterator()))
    require(sampledMvrs.size == sampleNumbers.size)

    sampledMvrs.forEachIndexed { index, mvr ->
        require(mvr.prn == sampleNumbers[index])
    }

    return writeAuditableCardCsvFile(Closer(sampledMvrs.iterator()), publisher.sampleMvrsFile(round))
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
