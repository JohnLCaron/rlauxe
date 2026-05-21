package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.bin.writeFastSamplingCards
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIteratorM
import org.cryptobiotic.rlauxe.persist.csv.writeCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.writeAuditCreationConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeAuditRoundConfigJsonFile
import org.cryptobiotic.rlauxe.persist.protobuf.ProtoCardIteratorM
import org.cryptobiotic.rlauxe.persist.protobuf.writeProtoCards
import org.cryptobiotic.rlauxe.persist.validateOutputDirOfFile
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.SortMerge
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.verify.VerifyAuditCommitment
import kotlin.use

private val logger = KotlinLogging.logger("StartAudit")

fun createAuditRecord(config: Config, election: ElectionBuilder, auditDir: String, externalSortDir: String? = null,
                      validate: Boolean = false, sortManifest: Boolean = true) {
    val publisher = Publisher(auditDir)

    writeAuditCreationConfigJsonFile(config.creation, publisher.auditCreationConfigFile())
    logger.info{"writeAuditCreationConfig to ${publisher.auditCreationConfigFile()}\n  ${config.creation}"}

    writeAuditRoundConfigJsonFile(config.round, publisher.auditRoundProtoFile())
    logger.info{"writeAuditRoundProto to ${publisher.auditRoundProtoFile()}\n  ${config.round}"}

    if (sortManifest) { // sortManifest false for uniform sampling
        if (externalSortDir == null) {
            sortManifestInternal(publisher, config.creation.seed)
        } else {
            sortManifestExternal(externalSortDir, publisher, config.creation.seed)
        }
        if (election.cardStyles() != null) { // TODO cant be optional styles I think
            makeFastCards(publisher, election.cardStyles()!!)
        }

        // save Mvrs for testing and diagnostics
        // cant write the sorted mvrs until after sortedCards is written
        if (config.election.mvrSource == MvrSource.testPrivateMvrs) {
            val unsortedMvrs = election.createUnsortedMvrsInternal() // make electionBuilder add the batches ??
            if (unsortedMvrs != null) {
                writePrivateMvrsInternal(publisher, unsortedMvrs, election.cardStyles(), seed = config.creation.seed)
            } else {
                val unsortedCards = election.createUnsortedMvrsExternal()
                if (unsortedCards != null && externalSortDir != null) {
                    writeSortedCardsExternal(
                        externalSortDir,
                        publisher.sortedMvrsFile(),
                        unsortedCards,
                        seed = config.creation.seed
                    )
                    logger.info { "createAuditRecord wrotePrivateMvrs to ${publisher.sortedMvrsFile()}" }
                } else {
                    logger.warn { "createAuditRecord did not create private mvrs; not available for ${election.javaClass} auditType ${config.auditType}" }
                }
            }
        }
    }

    if (validate) {
        val verifyACResults = VerifyAuditCommitment(auditDir).verify()
        if (verifyACResults.hasErrors) {
            logger.error { "createAuditRecord VerifyAuditCommitment failed: ${verifyACResults}" }
        }
    }
}

// assumes that the CardManifest has already been written
fun sortManifestInternal(publisher: Publisher, seed: Long) {
    val unsortedCards = readCardsCsvIteratorM(publisher.cardManifestFile(), styles=null)
    val sortedCards = createSortedCardsInternal(unsortedCards, seed)
    val countCards = writeCardCsvFile(Closer(sortedCards.iterator()), publisher.sortedCardsFile())
    // createZipFile(publisher.sortedCardsFile(), delete = true)
    logger.info{"sortManifestInternal ${countCards} cards to ${publisher.sortedCardsFile()} seed= $seed"}
}

fun createSortedCardsInternal(unsortedCards: CloseableIterator<AuditableCardM>, seed: Long) : List<AuditableCardM> {
    val prng = Prng(seed)
    val cards = mutableListOf<AuditableCardM>()
    unsortedCards.use { cardIter ->
        while (cardIter.hasNext()) {
            val unsorted = cardIter.next()
            val nextPrn = prng.next()
            cards.add( unsorted.copy(prn = nextPrn))
        }
    }
    return cards.sortedBy { it.prn }
}

// assumes that the CardManifest has already been written
fun sortManifestExternal(topdir: String, publisher: Publisher, seed: Long) {
    val unsortedCards = readCardsCsvIteratorM(publisher.cardManifestFile(), styles=null)
    writeSortedCardsExternal(topdir, publisher.sortedCardsFile(), unsortedCards, seed)
}

fun makeFastCards(publisher: Publisher, styles: List<StyleIF>): Int {
    val stopwatch = Stopwatch()

    // copy sortedCards csv to sortedCards proto file for better performance
    val sortedCards = readCardsCsvIteratorM(publisher.sortedCardsFile(), styles)
    writeProtoCards(sortedCards, publisher.sortedCardsProtoFile())

    // extract some info from sorted proto cards for a super compact "samplingCards" binary file
    val bufferSize = 100_000
    val protoIter = ProtoCardIteratorM(publisher.sortedCardsProtoFile(), bufferSize, styles)  // dont actually need styles i think
    val ncards = writeFastSamplingCards(protoIter, publisher.fastSamplingFile(), styles)

    logger.info{"makeFastCards ${ncards} took ${stopwatch}"}
    return ncards
}

fun writeSortedCardsExternal(topdir: String, outputFile: String, unsortedCards: CloseableIterator<AuditableCardM>, seed: Long) {
    val sorter = SortMerge<AuditableCardM>("$topdir/sortChunks", outputFile = outputFile, seed = seed)
    sorter.run(
        cardIter = unsortedCards,
        toCard = { from: AuditableCardM, index: Int, prn: Long -> from.copy(index = index, prn = prn) }
    )
}

// internal sort of unsortedMvrs (must be in canonical order) to sorted by prn
fun writePrivateMvrsInternal(publisher: Publisher, unsortedMvrs: List<AuditableCardM>, styles: List<StyleIF>?, seed: Long) {
    validateOutputDirOfFile(publisher.sortedMvrsFile())

    // val mvrCardIter = MvrsToCardsWithBatchNameIterator( mvrIter, batches ?: emptyList(), phantomCvrs = null, seed = seed)

    // add the prn
    val prng = Prng(seed)
    val mvrIter = unsortedMvrs.iterator()
    val mvrCards = mutableListOf<AuditableCardM>()
    while (mvrIter.hasNext()) {
        mvrCards.add( mvrIter.next().copy(prn=prng.next()) )
    }

    val sortedMvrs = mvrCards.sortedBy { it.prn }
    val countMvrs = writeCardCsvFile(Closer(sortedMvrs.iterator()), publisher.sortedMvrsFile())
    logger.info{"writeSortedMvrs ${countMvrs} mvrs to ${publisher.sortedMvrsFile()}"}
}