package org.cryptobiotic.rlauxe.audit

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.json.Publisher
import org.cryptobiotic.rlauxe.persist.json.readSampleNumbersJsonFile
import java.nio.file.Files
import java.nio.file.Path

val checkValidity = false

class MvrManagerClca(val auditDir: String) : MvrManagerClcaIF, MvrManagerTest {
    private var mvrsRound: List<AuditableCard> = emptyList()
    private val cardFile: String

    init {
        val publisher = Publisher(auditDir)
        cardFile = if (Files.exists(Path.of(publisher.cardsCsvZipFile()))) {
            publisher.cardsCsvZipFile()
        } else if (Files.exists(Path.of(publisher.cardsCsvFile()))) {
            publisher.cardsCsvFile()
        } else {
            throw IllegalArgumentException("No cvr file found in $auditDir")
        }
    }

    override fun Nballots(contestUA: ContestUnderAudit) = 0 // TODO ???
    override fun ballotCards() : Iterator<AuditableCard> = auditableCards()

    // this is where you would add the real mvrs
    override fun setMvrsForRound(mvrs: List<AuditableCard>) {
        mvrsRound = mvrs.toList()
    }

    // same pairs over all contests (!)
    override fun makeCvrPairsForRound(): List<Pair<Cvr, Cvr>> {
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

    private fun auditableCards(): Iterator<AuditableCard> = readCardsCsvIterator(cardFile)

    //// MvrManagerTest TODO too complicated!
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
                require(mvr.sampleNumber() > lastRN)
                lastRN = mvr.sampleNumber()
            }
        }
        setMvrsForRound(sampledMvrs)
        return sampledMvrs
    }

    // this is all to implement mvrManager.setMvrsBySampleNumber(sampledMvrs)
    override fun setMvrsForRoundIdx(roundIdx: Int): List<AuditableCard> {
        val publisher = Publisher(auditDir)
        val resultSamples = readSampleNumbersJsonFile(publisher.sampleNumbersFile(roundIdx))
        if (resultSamples is Err) println(resultSamples)
        require(resultSamples is Ok)
        val sampleNumbers = resultSamples.unwrap() // these are the samples we are going to audit.

        return if (sampleNumbers.isEmpty()) {
            println("***Error sampled Indices are empty for round $roundIdx")
            emptyList()
        } else {
            setMvrsBySampleNumber(sampleNumbers)
        }
    }
}