package org.cryptobiotic.rlauxe.workflow

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCardsFrom
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.existsOrZip
import org.cryptobiotic.rlauxe.persist.json.readSamplePrnsJsonFile

private val logger = KotlinLogging.logger("MvrManagerTestFromRecord")
private val checkValidity = true

// assumes testMvrs are in "$auditDir/private/testMvrs.csv"
class MvrManagerTestFromRecord(auditDir: String, val config: AuditConfig, val contestsUA: List<ContestUnderAudit>) : MvrManagerTestIF, MvrManagerFromRecord(auditDir) {

    //// MvrManagerTest
    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>): List<AuditableCard> {
        val mvrFile = publisher.sortedMvrsFile() // "$auditDir/private/sortedMvrs.csv"
        val sampledMvrs = if (existsOrZip(mvrFile)) {
            val mvrIterator = readCardsCsvIterator(mvrFile)
            findSamples(sampleNumbers, mvrIterator)
        } else {
            val cards = findSamples(sampleNumbers, auditableCards())
            if (config.simFuzzPct() == null) {
                cards // use the cvrs - ie, no errors
            } else { // fuzz the cvrs
                val fuzzedCards = makeFuzzedCardsFrom(contestsUA, cards, config.simFuzzPct()!!)
                fuzzedCards
            }
        }

        if (checkValidity) {
            require(sampledMvrs.size == sampleNumbers.size)
            var lastRN = 0L
            sampledMvrs.forEach { mvr ->
                require(mvr.prn > lastRN)
                lastRN = mvr.prn
            }
        }

        val publisher = Publisher(auditDir)
        writeAuditableCardCsvFile(sampledMvrs, publisher.sampleMvrsFile(publisher.currentRound()))
        logger.info{"setMvrsBySampleNumber write sampledMvrs to '${publisher.sampleMvrsFile(publisher.currentRound())}"}
        return sampledMvrs
    }

    // this is to implement mvrManager.setMvrsBySampleNumber(sampledMvrs)
    fun setMvrsForRoundIdx(roundIdx: Int): List<AuditableCard> {
        val publisher = Publisher(auditDir)
        val resultSamples = readSamplePrnsJsonFile(publisher.samplePrnsFile(roundIdx))
        if (resultSamples is Err) logger.error{"$resultSamples"}
        require(resultSamples is Ok)
        val sampleNumbers = resultSamples.unwrap() // these are the samples we are going to audit.

        return if (sampleNumbers.isEmpty()) {
            logger.error{"***Error sampled Indices are empty for round $roundIdx"}
            emptyList()
        } else {
            setMvrsBySampleNumber(sampleNumbers)
        }
    }

}