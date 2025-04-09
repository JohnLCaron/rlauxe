package org.cryptobiotic.rlauxe.audit

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.csv.IteratorCvrsCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCvrsCsvIterator
import org.cryptobiotic.rlauxe.persist.json.Publisher
import org.cryptobiotic.rlauxe.persist.json.readSampleNumbersJsonFile
import java.nio.file.Files
import java.nio.file.Path

private val checkValidity = false

class MvrManagerClca(val auditDir: String) : MvrManagerClcaIF, MvrManagerTest {
    private var mvrsRound: List<CvrUnderAudit> = emptyList()
    private val cvrFile: String

    init {
        val publisher = Publisher(auditDir)
        cvrFile = if (Files.exists(Path.of("$auditDir/sortedCvrs.zip"))) {
            "$auditDir/sortedCvrs.zip"
        } else if (Files.exists(Path.of(publisher.cvrsCsvFile()))) {
            publisher.cvrsCsvFile()
        } else {
            throw IllegalArgumentException("No cvr file found in $auditDir")
        }
    }

    override fun Nballots(contestUA: ContestUnderAudit) = 0 // TODO ???
    override fun ballotCards() : Iterator<BallotOrCvr> = cvrsUA()

    // this is where you would add the real mvrs
    override fun setMvrsForRound(mvrs: List<CvrUnderAudit>) {
        mvrsRound = mvrs.toList()
    }

    // only used when its an MvrManagerTest with fake mvrs in "$auditDir/private/testMvrs.csv"
    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>): List<CvrUnderAudit> {
        val mvrFile = "$auditDir/private/testMvrs.csv"
        val sampledMvrs = if (Files.exists(Path.of(mvrFile))) {
            val mvrIterator = IteratorCvrsCsvFile(mvrFile)
            findSamples(sampleNumbers, mvrIterator)
        } else {
            findSamples(sampleNumbers, cvrsUA()) // use the cvrs - ie, no errors
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
    override fun setMvrsForRoundIdx(roundIdx: Int): List<CvrUnderAudit> {
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

    // same pairs over all contests (!)
    override fun makeCvrPairsForRound(): List<Pair<Cvr, Cvr>> {
        val sampleNumbers = mvrsRound.map { it.sampleNum }

        val sampledCvrs = findSamples(sampleNumbers, cvrsUA())
        require(sampledCvrs.size == mvrsRound.size)

        if (checkValidity) {
            // prove that sampledCvrs correspond to mvrs
            val cvruaPairs: List<Pair<CvrUnderAudit, CvrUnderAudit>> = mvrsRound.zip(sampledCvrs)
            cvruaPairs.forEach { (mvr, cvr) ->
                require(mvr.id == cvr.id)
                require(mvr.index == cvr.index)
                require(mvr.sampleNumber() == cvr.sampleNumber())
            }
        }

        // why not List<Pair<CvrUnderAudit, CvrUnderAudit>> ??
        return mvrsRound.map{ it.cvr }.zip(sampledCvrs.map{ it.cvr })
    }

    private fun cvrsUA(): Iterator<CvrUnderAudit> = readCvrsCsvIterator(cvrFile)
}