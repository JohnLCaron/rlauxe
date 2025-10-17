package org.cryptobiotic.rlauxe.persist

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.persist.csv.readAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.util.CloseableIterable
import java.nio.file.Files
import java.nio.file.Path

private val logger = KotlinLogging.logger("MvrManagerFromRecord")
private val checkValidity = true

// assumes that the mvrs have been set externally into the election record.
class MvrManagerFromRecord(val auditDir: String) : MvrManagerClcaIF, MvrManagerPollingIF {
    private val cardFile: String

    init {
        val publisher = Publisher(auditDir)
        cardFile = publisher.cardsCsvFile()
    }

    override fun Nballots(contestUA: ContestUnderAudit) = 0 // TODO ???
    override fun sortedCards() = CloseableIterable{ auditableCards() }

    // same pairs over all contests (!)
    override fun makeCvrPairsForRound(): List<Pair<Cvr, Cvr>> {
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
        return mvrsRound.map{ it.cvr() }.zip(sampledCvrs.map{ it.cvr() })
    }

    override fun makeMvrsForRound(): List<Cvr> {
        val mvrsRound = readMvrsForRound()
        val sampleNumbers = mvrsRound.map { it.prn }

        val sampledCvrs = findSamples(sampleNumbers, auditableCards())
        require(sampledCvrs.size == mvrsRound.size)

        return sampledCvrs.map{ it.cvr() }
    }

    private fun readMvrsForRound(): List<AuditableCard> {
        val publisher = Publisher(auditDir)
        return readAuditableCardCsvFile(publisher.sampleMvrsFile(publisher.currentRound()))
    }

    private fun auditableCards(): CloseableIterator<AuditableCard> = readCardsCsvIterator(cardFile)
}