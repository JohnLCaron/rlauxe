package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.Prng

class MvrManagerPolling(val ballots: List<Ballot>, seed: Long) : MvrManagerPollingIF {
    val ballotsUA: List<BallotUnderAudit>
    var mvrsRound: List<CvrUnderAudit> = emptyList()

    init {
        val prng = Prng(seed)
        ballotsUA = ballots.mapIndexed { idx, it -> BallotUnderAudit(it, idx, prng.next()) }
            .sortedBy { it.sampleNumber() }
    }

    fun ballots() = ballots
    override fun Nballots(contestUA: ContestUnderAudit) = ballots.size
    override fun ballotCards() : Iterator<BallotOrCvr> = ballotsUA.iterator()
    override fun setMvrsForRound(mvrs: List<CvrUnderAudit>) {
        mvrsRound = mvrs.toList()
    }

    override fun setMvrsForRoundIdx(roundIdx: Int): List<CvrUnderAudit> {
        TODO("Not yet implemented")
    }

    override fun makeSampler(contestId: Int, hasStyles: Boolean, assorter: AssorterIF, allowReset: Boolean): Sampler {
        return PollWithoutReplacement(contestId, hasStyles, mvrsRound.map { it.cvr }, assorter, allowReset=allowReset)
    }
}

/*
class MvrManagerPollingRecord(val auditRecord: AuditRecord, private val ballotsUA: Iterable<BallotUnderAudit>, val nballotCards: Int) :
    MvrManagerPollingIF, MvrManagerTest {
    var mvrsForRound: List<CvrUnderAudit> = emptyList()

    override fun Nballots() = nballotCards
    override fun ballotCards() : Iterator<BallotOrCvr> = ballotsUA.iterator()
    override fun setMvrsForRound(mvrs: List<CvrUnderAudit>) {
        mvrsForRound = mvrs
    }
    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>) {
        val sampleMvrs = auditRecord.getMvrsBySampleNumber(sampleNumbers, null)
        setMvrsForRound(sampleMvrs)
    }

    override fun makeSampler(contestId: Int, hasStyles: Boolean, assorter: AssorterIF, allowReset: Boolean): Sampler {
        // // TODO why not CvrUnderAudit ?
        return PollWithoutReplacement(contestId, hasStyles, mvrsForRound.map { it.cvr } , assorter, allowReset=allowReset)
    }
} */
