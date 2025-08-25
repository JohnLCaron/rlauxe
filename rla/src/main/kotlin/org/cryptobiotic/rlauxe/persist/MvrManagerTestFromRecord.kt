package org.cryptobiotic.rlauxe.persist

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.csv.readAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.json.readSamplePrnsJsonFile
import java.nio.file.Files
import java.nio.file.Path

private val logger = KotlinLogging.logger("MvrManagerTestFromRecord")
private val checkValidity = true

// stores the testMvrs in "$auditDir/private/testMvrs.csv"
class MvrManagerTestFromRecord(val auditDir: String) : MvrManagerClcaIF, MvrManagerPollingIF, MvrManagerTest {
    private val cardFile: String

    init {
        val publisher = Publisher(auditDir)
        cardFile = if (Files.exists(Path.of(publisher.cardsCsvZipFile()))) {
            publisher.cardsCsvZipFile()
        } else if (Files.exists(Path.of(publisher.cardsCsvFile()))) {
            publisher.cardsCsvFile()
        } else {
            logger.error{ "No cvr file found in $auditDir" }
            throw IllegalArgumentException("No cvr file found in $auditDir")
        }
    }

    override fun Nballots(contestUA: ContestUnderAudit) = 0 // TODO ???
    override fun sortedCards() : Iterator<AuditableCard> = auditableCards()

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
                require(mvr.desc == cvr.desc)
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

    private fun auditableCards(): Iterator<AuditableCard> = readCardsCsvIterator(cardFile)

    //// MvrManagerTest
    // only used when its an MvrManagerTest with fake mvrs in "$auditDir/private/testMvrs.csv"
    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>): List<AuditableCard> {
        val mvrFile = "$auditDir/private/testMvrs.csv"
        val sampledMvrs = if (Files.exists(Path.of(mvrFile))) {
            val mvrIterator = readCardsCsvIterator(mvrFile)
            findSamples(sampleNumbers, mvrIterator)
        } else {
            findSamples(sampleNumbers, auditableCards()) // use the cvrs - ie, no errors
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

    private fun readMvrsForRound(): List<AuditableCard> {
        val publisher = Publisher(auditDir)
        return readAuditableCardCsvFile(publisher.sampleMvrsFile(publisher.currentRound()))
    }
}