package org.cryptobiotic.rlauxe.workflow

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.persist.csv.readAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.readCardPoolsJsonFile
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.Closer

private val logger = KotlinLogging.logger("PersistedMvrManager")
private val checkValidity = false

// assumes that the mvrs have been set externally into the election record, eg by EnterMvrsCli.
open class PersistedMvrManager(val auditDir: String, val config: AuditConfig, val contestsUA: List<ContestUnderAudit>, val nowrite: Boolean = false): MvrManager {
    val publisher = Publisher(auditDir)

    override fun sortedCards() = CloseableIterable{ auditableCards() }

    override fun cardPools(): List<CardPoolIF>?  {
        if (!config.isOA) return null
        val infos = contestsUA.associate{ it.id to it.contest.info() }
        val cardPoolResult = readCardPoolsJsonFile(publisher.cardPoolsFile(), infos)
        if (cardPoolResult is Err) {
            logger.error{ "$cardPoolResult" }
            return null
        }
        return cardPoolResult.unwrap()
    }


    override fun makeMvrCardPairsForRound(round: Int): List<Pair<CardIF, CardIF>>  { // Pair(mvr, card)
        val mvrsRound = readMvrsForRound(round)
        val sampleNumbers = mvrsRound.map { it.prn }

        val sampledCvrs = findSamples(sampleNumbers, auditableCards())
        require(sampledCvrs.size == mvrsRound.size)

        if (checkValidity) {
            // prove that sampledCvrs correspond to mvrs
            val cvruaPairs: List<Pair<AuditableCard, AuditableCard>> = mvrsRound.zip(sampledCvrs)
            cvruaPairs.forEach { (mvr, card) ->
                require(mvr.location == card.location) { "mvr location ${mvr.location} != card.location ${card.location}"}
                require(mvr.index == card.index)  { "mvr index ${mvr.index} != card.index ${card.index}"}
                require(mvr.prn == card.prn)  { "mvr prn ${mvr.prn} != card.prn ${card.prn}"}
            }
        }

        if (!nowrite) { // TODO
            val round = publisher.currentRound()
            val countCards = writeAuditableCardCsvFile(Closer(sampledCvrs.iterator()), publisher.sampleCardsFile(round))
            logger.info { "write ${countCards} cards to ${publisher.sampleCardsFile(round)}" }
        }

        return mvrsRound.zip(sampledCvrs)
    }

    // the sampleMvrsFile is added externally for real audits, and by MvrManagerTestFromRecord for test audits
    // it must be in the same order as the sorted cards
    // it is placed into publisher.sampleMvrsFile, and this just reads from that file.
    private fun readMvrsForRound(round: Int): List<AuditableCard> {
        val publisher = Publisher(auditDir)
        return readAuditableCardCsvFile(publisher.sampleMvrsFile(round))
    }

    fun auditableCards(): CloseableIterator<AuditableCard> = readCardsCsvIterator(publisher.sortedCardsFile())
}