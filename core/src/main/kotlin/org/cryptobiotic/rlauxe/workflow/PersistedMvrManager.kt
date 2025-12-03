package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.persist.csv.readAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.Closer

private val logger = KotlinLogging.logger("PersistedMvrManager")
private val checkValidity = true

// assumes that the mvrs have been set externally into the election record, eg by EnterMvrsCli.
open class PersistedMvrManager(val auditDir: String, val nowrite: Boolean = false): MvrManager {
    val publisher = Publisher(auditDir)

    override fun sortedCards() = CloseableIterable{ auditableCards() }

    override fun makeMvrCardPairsForRound(): List<Pair<CardIF, CardIF>>  { // Pair(mvr, card)
        val mvrsRound = readMvrsForRound()
        val sampleNumbers = mvrsRound.map { it.prn }

        val sampledCvrs = findSamples(sampleNumbers, auditableCards())
        require(sampledCvrs.size == mvrsRound.size)

        if (checkValidity) {
            // prove that sampledCvrs correspond to mvrs
            val cvruaPairs: List<Pair<AuditableCard, AuditableCard>> = mvrsRound.zip(sampledCvrs)
            cvruaPairs.forEach { (mvr, cvr) ->
                require(mvr.location == cvr.location)
                require(mvr.index == cvr.index)
                require(mvr.prn == cvr.prn)
            }
        }

        if (!nowrite) {
            val round = publisher.currentRound()
            val countCards = writeAuditableCardCsvFile(Closer(sampledCvrs.iterator()), publisher.sampleCardsFile(round))
            logger.info { "write ${countCards} cards to ${publisher.sampleMvrsFile(round)}" }
        }

        return mvrsRound.zip(sampledCvrs)
    }

    // the sampleMvrsFile is added externally for real audits, and by MvrManagerTestFromRecord for test audits
    // it must be in the same order as the sorted cards
    // it is placed into publisher.sampleMvrsFile, and this just reads from that file.
    private fun readMvrsForRound(): List<AuditableCard> {
        val publisher = Publisher(auditDir)
        return readAuditableCardCsvFile(publisher.sampleMvrsFile(publisher.currentRound()))
    }

    fun auditableCards(): CloseableIterator<AuditableCard> = readCardsCsvIterator(publisher.sortedCardsFile())
}