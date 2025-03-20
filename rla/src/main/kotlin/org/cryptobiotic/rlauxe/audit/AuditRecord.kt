package org.cryptobiotic.rlauxe.audit

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.csv.readCvrsCsvFile
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.workflow.*
import java.nio.file.Files
import java.nio.file.Path

class AuditRecord(
    val location: String,
    val auditConfig: AuditConfig,
    val rounds: List<AuditRound>,
    val mvrs: Set<CvrUnderAudit>,
) {

    fun ballotCards(): BallotCards {
        return if (auditConfig.isClca) {
            BallotCardsClcaRecord(cvrsUA, cvrsUA.size)
        } else {
            BallotCardsPollingRecord(ballotsUA, ballotsUA.size)
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
    fun getMvrsForRound(ballotCards: BallotCards, roundIdx: Int, mvrFile: String): List<CvrUnderAudit> {
        val publisher = Publisher(location)
        val resultIndices = readSampleNumbersJsonFile(publisher.sampleNumbersFile(roundIdx))
        if (resultIndices is Err) println(resultIndices)
        require(resultIndices is Ok)
        val sampleIndices = resultIndices.unwrap() // these are the samples we are going to audit.

        if (sampleIndices.isEmpty()) {
            println("***Error sampled Indices are empty for round $roundIdx")
            return emptyList()
        }

        // the only place privy to private data
        val testMvrs = readCvrsCsvFile(mvrFile)

        val sampledMvrs = findSamples(sampleIndices, testMvrs)
        require(sampledMvrs.size == sampleIndices.size)

        // debugging sanity check
        var lastRN = 0L
        sampledMvrs.forEach { mvr ->
            require(mvr.sampleNumber() > lastRN)
            lastRN = mvr.sampleNumber()
        }

        ballotCards.setMvrs(sampledMvrs)
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
            return AuditRecord(location, auditConfig, rounds, mvrs)
        }
    }
}

class BallotCardsClcaRecord(private val cvrsUA: Iterable<CvrUnderAudit>, val nballotCards: Int) : BallotCardsClca {
    private var mvrsForRound: List<CvrUnderAudit> = emptyList()

    override fun nballotCards() = nballotCards
    override fun ballotCards() : Iterable<BallotOrCvr> = cvrsUA
    override fun setMvrs(mvrs: List<CvrUnderAudit>) {
        mvrsForRound = mvrs
    }
    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>) {
        TODO("Unimplemented")
    }

    override fun makeSampler(contestId: Int, cassorter: ClcaAssorterIF, allowReset: Boolean): Sampler {
        val sampleNumbers = mvrsForRound.map { it.sampleNum }
        val sampledCvrs = findSamples(sampleNumbers, cvrsUA)
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
        return ClcaWithoutReplacement(contestId, cvrPairs, cassorter, allowReset = allowReset)
    }
}

private const val checkValidity : Boolean= false

class BallotCardsPollingRecord(private val ballotsUA: Iterable<BallotUnderAudit>, val nballotCards: Int) :
    BallotCardsPolling {
    var mvrsForRound: List<CvrUnderAudit> = emptyList()

    override fun nballotCards() = nballotCards
    override fun ballotCards() : Iterable<BallotOrCvr> = ballotsUA
    override fun setMvrs(mvrs: List<CvrUnderAudit>) {
        mvrsForRound = mvrs
    }
    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>) {
        TODO("Unimplemented")
    }

    override fun makeSampler(contestId: Int, assorter: AssorterIF, allowReset: Boolean): Sampler {
        // // TODO why not CvrUnderAudit ?
        return PollWithoutReplacement(contestId, mvrsForRound.map { it.cvr } , assorter, allowReset=allowReset)
    }
}
