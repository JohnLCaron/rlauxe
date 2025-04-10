package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.Prng

class MvrManagerPolling(val ballots: List<Ballot>, seed: Long) : MvrManagerPollingIF {
    val ballotsUA: List<BallotUnderAudit>
    var mvrsRound: List<AuditableCard> = emptyList()

    init {
        val prng = Prng(seed)
        ballotsUA = ballots.mapIndexed { idx, it -> BallotUnderAudit(it, idx, prng.next()) }
            .sortedBy { it.sampleNumber() }
    }

    fun ballots() = ballots
    override fun Nballots(contestUA: ContestUnderAudit) = ballots.size
    override fun ballotCards() : Iterator<BallotOrCvr> = ballotsUA.iterator()
    override fun setMvrsForRound(mvrs: List<AuditableCard>) {
        mvrsRound = mvrs.toList()
    }

    override fun makeSampler(contestId: Int, hasStyles: Boolean, assorter: AssorterIF, allowReset: Boolean): Sampler {
        return PollWithoutReplacement(contestId, hasStyles, mvrsRound.map { it.cvr() }, assorter, allowReset=allowReset)
    }
}