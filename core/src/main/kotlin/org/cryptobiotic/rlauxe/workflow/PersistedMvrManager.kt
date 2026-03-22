package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.core.CvrIF
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.persist.csv.readAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.verify.verifyMvrCardPairs

// assumes that the mvrs have been set externally into the election record, eg by EnterMvrsCli.
// skip writing when doing runRoundAgain
open class PersistedMvrManager(val auditRecord: AuditRecord, val mvrWrite: Boolean = true): MvrManager {
    val config = auditRecord.config
    val contestsUA = auditRecord.contests.filter { it.preAuditStatus == TestH0Status.InProgress }
    val publisher = Publisher(auditRecord.location)

    val batches = auditRecord.readBatches() ?: auditRecord.readCardPools()
    val sortedManifest = auditRecord.readSortedManifest(batches)

    override fun sortedManifest() = sortedManifest
    override fun batches() = batches
    override fun pools() = auditRecord.readCardPools() // could test if batches are cardPools

    override fun makeMvrCardPairsForRound(round: Int): List<Pair<CvrIF, AuditableCard>>  {
        val mvrsForRound = readMvrsForRound(round)
        val sampleNumbers = mvrsForRound.map { it.prn }

        val sampledCards = findSamples(sampleNumbers, auditableCards())
        require(sampledCards.size == mvrsForRound.size)
        val mvrCardPairs = mvrsForRound.zip(sampledCards)

        if (checkValidity) {
            val errs = ErrorMessages("PersistedMvrManager")
            verifyMvrCardPairs(mvrCardPairs, errs)
            if (errs.hasErrors()) {
                logger.error{ " ${auditRecord.showName()} verifyMvrCardPairs " }
            }
        }

        if (mvrWrite) {
            val countCards = writeAuditableCardCsvFile(Closer(sampledCards.iterator()), publisher.sampleCardsFile(round)) // sampleCards
            logger.info { "write ${countCards} cards to ${publisher.sampleCardsFile(round)}" }
        }

        return mvrCardPairs
    }

    // the sampleMvrsFile is added externally for real audits, and by MvrManagerTestFromRecord for test audits
    // it must be in the same order as the sorted cards
    // it is placed into publisher.sampleMvrsFile(round), and this method just reads from that file.
    // return complete list of mvrs used for this round
    private fun readMvrsForRound(round: Int): List<AuditableCard> {
        return readAuditableCardCsvFile(publisher.sampleMvrsFile(round))
    }

    fun auditableCards(): CloseableIterator<AuditableCard> = sortedManifest.cards.iterator()

    companion object {
        private val logger = KotlinLogging.logger("PersistedMvrManager")
        private val checkValidity = true
    }
}

// for viewer
fun readMvrsForRound(publisher: Publisher, roundIdx: Int): List<AuditableCard> {
    return readAuditableCardCsvFile(publisher.sampleMvrsFile(roundIdx))
}


