package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.*

class MvrManagerClcaRecord(val auditRecord: AuditRecord, private val cvrsUA: Iterable<CvrUnderAudit>, val nballotCards: Int) : MvrManagerClca, MvrManagerTest {
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

class MvrManagerPollingRecord(val auditRecord: AuditRecord, private val ballotsUA: Iterable<BallotUnderAudit>, val nballotCards: Int) :
    MvrManagerPolling, MvrManagerTest {
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
