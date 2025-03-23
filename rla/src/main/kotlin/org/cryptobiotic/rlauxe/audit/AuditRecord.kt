package org.cryptobiotic.rlauxe.audit

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.csv.IteratorCvrsCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCvrsCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeCvrsCsvFile
import org.cryptobiotic.rlauxe.persist.json.*
import java.nio.file.Files
import java.nio.file.Path

class AuditRecord(
    val location: String,
    val auditConfig: AuditConfig,
    val contests: List<ContestUnderAudit>,
    val rounds: List<AuditRound>,
    val mvrs: List<CvrUnderAudit> // mvrs already sampled
) {
    val previousMvrs = mutableMapOf<Long, CvrUnderAudit>()

    init {
        mvrs.forEach { previousMvrs[it.sampleNum] = it } // cumulative
    }

    // TODO TIMING taking 15%
    val mvrManager: MvrManager by lazy {
        if (auditConfig.isClca) {
            MvrManagerClcaRecord(this, cvrsUA, cvrsUA.size)
        } else {
            MvrManagerPollingRecord(this, ballotsUA, ballotsUA.size)
        }
    }

    private val cvrsUA: List<CvrUnderAudit> by lazy {
        val publisher = Publisher(location)
        readCvrsCsvFile(publisher.cvrsCsvFile()) // TODO wrap in Result ??
    }

    private val ballotsUA: List<BallotUnderAudit> by lazy {
        val publisher = Publisher(location)
        val bmResult = readBallotManifestJsonFile(publisher.ballotManifestFile())
        if (bmResult is Ok) bmResult.unwrap().ballots else emptyList()
    }

    // read the sampleNumbers for this round and fetch the corresponding mvrs from the private file, add to ballotCards
    // TODO in a real audit, these are added by the audit process, not from a private file
    fun getMvrsForRound(mvrManager: MvrManager, roundIdx: Int, mvrFile: String?): List<CvrUnderAudit> {
        val publisher = Publisher(location)
        val resultSamples = readSampleNumbersJsonFile(publisher.sampleNumbersFile(roundIdx))
        if (resultSamples is Err) println(resultSamples)
        require(resultSamples is Ok)
        val sampleNumbers = resultSamples.unwrap() // these are the samples we are going to audit.

        if (sampleNumbers.isEmpty()) {
            println("***Error sampled Indices are empty for round $roundIdx")
            return emptyList()
        }

        val sampledMvrs = getMvrsBySampleNumber(sampleNumbers, mvrFile)
        mvrManager.setMvrs(sampledMvrs)
        return sampledMvrs
    }

    // TODO TIMING taking 8% of sample record
    fun getMvrsBySampleNumber(sampleNumbers: List<Long>, mvrFile: String?): List<CvrUnderAudit>  {
        val useMvrFile = mvrFile?: "$location/private/testMvrs.csv"

        //val testMvrs = readCvrsCsvFile(useMvrFile)
        //val sampledMvrs = findSamples(sampleNumbers, testMvrs.iterator())

        val mvrIterator = IteratorCvrsCsvFile(useMvrFile) // TODO should we cache these ?
        val sampledMvrs = findSamples(sampleNumbers, mvrIterator)
        mvrIterator.close()

        // debugging sanity check
        require(sampledMvrs.size == sampleNumbers.size)
        var lastRN = 0L
        sampledMvrs.forEach { mvr ->
            require(mvr.sampleNumber() > lastRN)
            lastRN = mvr.sampleNumber()
        }
        return sampledMvrs
    }

    // TODO new mvrs vs mvrs. Build interfacce to manage this process
    fun enterMvrs(mvrFile: String): Boolean {
        val mvrs = readCvrsCsvFile(mvrFile)
        val mvrMap = mvrs.associateBy { it.sampleNum }.toMap()

        val publisher = Publisher(location)
        val lastRound = rounds.last()
        val lastRoundIdx = lastRound.roundIdx

        // get complete match with sampleNums
        var missing = false
        val sampledNumbers = readSampleNumbersJsonFile(publisher.sampleNumbersFile(lastRoundIdx)).unwrap()
        sampledNumbers.forEach { sampleNumber ->
            var mvr = previousMvrs[sampleNumber]
            if (mvr == null) {
                mvr = mvrMap[sampleNumber]
                if (mvr == null) {
                    println("Missing MVR for sampleNumber $sampleNumber")
                    missing = true
                } else {
                    previousMvrs[sampleNumber] = mvr
                }
            }
        }
        if (missing) return false

        val sampledMvrs = sampledNumbers.map{ sampleNumber -> previousMvrs[sampleNumber]!! }
        writeCvrsCsvFile(sampledMvrs , publisher.sampleMvrsFile(lastRoundIdx))
        println("    write sampledMvrs to '${publisher.sampleMvrsFile(lastRoundIdx)}' for round $lastRoundIdx")
        return true
    }

    companion object {

        fun readFrom(location: String): AuditRecord {
            val publisher = Publisher(location)
            val auditConfigResult = readAuditConfigJsonFile(publisher.auditConfigFile())
            val auditConfig = auditConfigResult.unwrap()

            val contestsResults = readContestsJsonFile(publisher.contestsFile())
            val contests = if (contestsResults is Ok) contestsResults.unwrap()
                else throw RuntimeException("Cannot read contests from ${publisher.contestsFile()}")

            val sampledMvrsAll = mutableListOf<CvrUnderAudit>()

            val rounds = mutableListOf<AuditRound>()
            for (roundIdx in 1..publisher.rounds()) {
                val sampledNumbers = readSampleNumbersJsonFile(publisher.sampleNumbersFile(roundIdx)).unwrap()

                val auditRound = readAuditRoundJsonFile(contests, sampledNumbers, publisher.auditRoundFile(roundIdx)).unwrap()

                // may not exist yet
                val mvrsForRoundFile = Path.of(publisher.sampleMvrsFile(roundIdx))
                if (Files.exists(mvrsForRoundFile)) {
                    val sampledMvrs = readCvrsCsvFile(publisher.sampleMvrsFile(roundIdx))
                    sampledMvrsAll.addAll(sampledMvrs) // cumulative
                }

                rounds.add(auditRound)
            }
            return AuditRecord(location, auditConfig, contests, rounds, sampledMvrsAll)
        }
    }
}
