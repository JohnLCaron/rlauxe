package org.cryptobiotic.rlauxe.audit

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.csv.IteratorCvrsCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCvrsCsvFile
import org.cryptobiotic.rlauxe.persist.json.*
import java.nio.file.Files
import java.nio.file.Path

class AuditRecord(
    val location: String,
    val auditConfig: AuditConfig,
    val contests: List<ContestUnderAudit>,
    val rounds: List<AuditRound>,
    val mvrs: Set<CvrUnderAudit>,
) {

    // TODO TIMING taking 15%
    val ballotCards: BallotCards by lazy {
        if (auditConfig.isClca) {
            BallotCardsClcaRecord(this, cvrsUA, cvrsUA.size)
        } else {
            BallotCardsPollingRecord(this, ballotsUA, ballotsUA.size)
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
    fun getMvrsForRound(ballotCards: BallotCards, roundIdx: Int, mvrFile: String?): List<CvrUnderAudit> {
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
        ballotCards.setMvrs(sampledMvrs)
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

    companion object {

        fun readFrom(location: String): AuditRecord {
            val publisher = Publisher(location)
            val auditConfigResult = readAuditConfigJsonFile(publisher.auditConfigFile())
            val auditConfig = auditConfigResult.unwrap()

            val contestsResults = readContestsJsonFile(publisher.contestsFile())
            val contests = if (contestsResults is Ok) contestsResults.unwrap()
                else throw RuntimeException("Cannot read contests from ${publisher.contestsFile()}")

            val mvrs = mutableSetOf<CvrUnderAudit>()

            val rounds = mutableListOf<AuditRound>()
            for (roundIdx in 1..publisher.rounds()) {
                val sampledNumbers = readSampleNumbersJsonFile(publisher.sampleNumbersFile(roundIdx)).unwrap()

                val auditRound = readAuditRoundJsonFile(contests, sampledNumbers, publisher.auditRoundFile(roundIdx)).unwrap()

                // may not exist yet
                val sampleMvrsFile = Path.of(publisher.sampleMvrsFile(roundIdx))
                if (Files.exists(sampleMvrsFile)) {
                    val sampledMvrs = readCvrsCsvFile(publisher.sampleMvrsFile(roundIdx))
                    mvrs.addAll(sampledMvrs) // cumulative
                }

                rounds.add(auditRound)
            }
            return AuditRecord(location, auditConfig, contests, rounds, mvrs)
        }
    }
}

class BallotCardsClcaRecord(val auditRecord: AuditRecord, private val cvrsUA: Iterable<CvrUnderAudit>, val nballotCards: Int) : BallotCardsClca {
    private var mvrsForRound: List<CvrUnderAudit> = emptyList()

    override fun nballotCards() = nballotCards
    override fun ballotCards() : Iterable<BallotOrCvr> = cvrsUA
    override fun setMvrs(mvrs: List<CvrUnderAudit>) {
        mvrsForRound = mvrs
    }
    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>) {
        val sampleMvrs = auditRecord.getMvrsBySampleNumber(sampleNumbers, null)
        setMvrs(sampleMvrs)
    }

    override fun makeSampler(contestId: Int, hasStyles: Boolean, cassorter: ClcaAssorterIF, allowReset: Boolean): Sampler {
        val sampleNumbers = mvrsForRound.map { it.sampleNum }

        val sampledCvrs = findSamples(sampleNumbers, cvrsUA.iterator()) // TODO use IteratorCvrsCsvFile?
        require(sampledCvrs.size == mvrsForRound.size)

        if (checkValidity) {
            // prove that sampledCvrs correspond to mvrs
            val cvruaPairs: List<Pair<CvrUnderAudit, CvrUnderAudit>> = mvrsForRound.zip(sampledCvrs)
            cvruaPairs.forEach { (mvr, cvr) ->
                require(mvr.id == cvr.id)
                require(mvr.index == cvr.index)
                require(mvr.sampleNumber() == cvr.sampleNumber())
            }
        }

        // why not List<Pair<CvrUnderAudit, CvrUnderAudit>> ??
        val cvrPairs = mvrsForRound.map{ it.cvr }.zip(sampledCvrs.map{ it.cvr })
        return ClcaWithoutReplacement(contestId, hasStyles, cvrPairs, cassorter, allowReset = allowReset)
    }
}

private const val checkValidity : Boolean= false

class BallotCardsPollingRecord(val auditRecord: AuditRecord, private val ballotsUA: Iterable<BallotUnderAudit>, val nballotCards: Int) :
    BallotCardsPolling {
    var mvrsForRound: List<CvrUnderAudit> = emptyList()

    override fun nballotCards() = nballotCards
    override fun ballotCards() : Iterable<BallotOrCvr> = ballotsUA
    override fun setMvrs(mvrs: List<CvrUnderAudit>) {
        mvrsForRound = mvrs
    }
    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>) {
        val sampleMvrs = auditRecord.getMvrsBySampleNumber(sampleNumbers, null)
        setMvrs(sampleMvrs)
    }

    override fun makeSampler(contestId: Int, hasStyles: Boolean, assorter: AssorterIF, allowReset: Boolean): Sampler {
        // // TODO why not CvrUnderAudit ?
        return PollWithoutReplacement(contestId, hasStyles, mvrsForRound.map { it.cvr } , assorter, allowReset=allowReset)
    }
}
