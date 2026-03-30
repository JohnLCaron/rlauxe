package org.cryptobiotic.rlauxe.audit

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.OnlyTask
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.writeAuditCreationConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeAuditRoundConfigJsonFile
import org.cryptobiotic.rlauxe.persist.validateOutputDirOfFile
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.SortMerge
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.verify.VerifyResults
import org.cryptobiotic.rlauxe.verify.checkContestsCorrectlyFormed
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import java.io.File
import java.nio.file.Files.notExists
import java.nio.file.Path
import kotlin.use

private val logger = KotlinLogging.logger("StartAudit")

// TO pass in creation, round config
fun createAuditRecord(config: Config, election: ElectionBuilder, auditDir: String, externalSortDir: String? = null) {
    val publisher = Publisher(auditDir)

    writeAuditCreationConfigJsonFile(config.creation, publisher.auditCreationConfigFile())
    logger.info{"writeAuditCreationConfig to ${publisher.auditCreationConfigFile()}\n  ${config.creation}"}

    writeAuditRoundConfigJsonFile(config.round, publisher.auditRoundProtoFile())
    logger.info{"writeAuditCreationConfig to ${publisher.auditRoundProtoFile()}\n  ${config.round}"}

    if (externalSortDir == null) {
        sortManifestInternal(publisher, config.creation.seed)
    } else {
        sortManifestExternal(externalSortDir, publisher, config.creation.seed)
    }

    // save Mvrs for testing and diagnostics
    // cant write the sorted mvrs until after sortedCards is written
    if (config.election.mvrSource == MvrSource.testPrivateMvrs) {
        val unsortedMvrs = election.createUnsortedMvrsInternal() // make electionBuilder add the batches ??
        if (unsortedMvrs != null) {
            writePrivateMvrsInternal(publisher, unsortedMvrs, election.batches(), seed = config.creation.seed)
        } else {
            val unsortedCards = election.createUnsortedMvrsExternal()
            if (unsortedCards != null && externalSortDir != null) {
                writeSortedCardsExternal(externalSortDir, publisher.sortedMvrsFile(), unsortedCards, seed = config.creation.seed)
                logger.info { "createAuditRecord wrotePrivateMvrs to ${publisher.sortedMvrsFile()}" }
            } else {
                logger.warn { "createAuditRecord did not create private mvrs; not available for ${election.javaClass} auditType ${config.auditType}" }
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
        checkContestsCorrectlyFormed(auditRecord.config.round.sampling, auditRecord.contests, results)
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
        if (auditRecord.config.mvrSource == MvrSource.testPrivateMvrs) {
            val publisher = Publisher(auditDir)
            val ncards = workflow.mvrManager().writeMvrsForRound(roundIdx)
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
    val countCards = writeCardCsvFile(Closer(sortedCards.iterator()), publisher.sortedCardsFile())
    // createZipFile(publisher.sortedCardsFile(), delete = true)
    logger.info{"sortManifestInternal ${countCards} cards to ${publisher.sortedCardsFile()}"}
}

fun createSortedCardsInternal(unsortedCards: CloseableIterator<CardWithBatchName>, seed: Long) : List<CardWithBatchName> {
    val prng = Prng(seed)
    val cards = mutableListOf<CardWithBatchName>()
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

fun writeSortedCardsExternal(topdir: String, outputFile: String, unsortedCards: CloseableIterator<CardWithBatchName>, seed: Long) {
    val sorter = SortMerge<CardWithBatchName>("$topdir/sortChunks", outputFile = outputFile, seed = seed)
    sorter.run(
        cardIter = unsortedCards,
        toCard = { from: CardWithBatchName, index: Int, prn: Long -> from.copy(index = index, prn = prn) }
    )
}

// internal sort
fun writePrivateMvrsInternal(publisher: Publisher, unsortedMvrs: List<Cvr>, batches: List<BatchIF>?, seed: Long) {
    validateOutputDirOfFile(publisher.sortedMvrsFile())

    val mvrIter = Closer(unsortedMvrs.iterator())
    val mvrCardIter = MvrsToCardsWithBatchNameIterator( mvrIter, batches ?: emptyList(), phantomCvrs = null, seed = seed)
    val mvrCards = mutableListOf<CardWithBatchName>()
    while (mvrCardIter.hasNext()) { mvrCards.add(mvrCardIter.next()) }

    val sortedMvrs = mvrCards.sortedBy { it.prn }

    val countMvrs = writeCardCsvFile(Closer(sortedMvrs.iterator()), publisher.sortedMvrsFile())
    logger.info{"writeSortedMvrs ${countMvrs} mvrs to ${publisher.sortedMvrsFile()}"}
}
