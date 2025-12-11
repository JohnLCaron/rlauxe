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
private val checkValidity = true

// assumes that the mvrs have been set externally into the election record, eg by EnterMvrsCli.
// skip writing when doing runRoundAgain
open class PersistedMvrManager(val auditDir: String, val config: AuditConfig, val contestsUA: List<ContestUnderAudit>, val mvrWrite: Boolean = true): MvrManager {
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


    override fun makeMvrCardPairsForRound(round: Int): List<Pair<CardIF, CardIF>>  {
        val mvrsForRound = readMvrsForRound(round)
        val sampleNumbers = mvrsForRound.map { it.prn }

        val sampledCards = findSamples(sampleNumbers, auditableCards())
        require(sampledCards.size == mvrsForRound.size)

        if (checkValidity) {
            // prove that sampledCvrs correspond to mvrs
            val cvruaPairs: List<Pair<AuditableCard, AuditableCard>> = mvrsForRound.zip(sampledCards)
            cvruaPairs.forEach { (mvr, card) ->
                require(mvr.location == card.location) { "mvr location ${mvr.location} != card.location ${card.location}"}
                require(mvr.index == card.index)  { "mvr index ${mvr.index} != card.index ${card.index}"}
                require(mvr.prn == card.prn)  { "mvr prn ${mvr.prn} != card.prn ${card.prn}"}
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
    private fun readMvrsForRound(round: Int): List<AuditableCard> {
        val publisher = Publisher(auditDir)
        return readAuditableCardCsvFile(publisher.sampleMvrsFile(round))
    }

    fun auditableCards(): CloseableIterator<AuditableCard> = readCardsCsvIterator(publisher.sortedCardsFile())
}