package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.Prng

//// in memory, simulated mvrs for testing

class MvrManagerClcaForTesting(val cvrs: List<Cvr>, mvrs: List<Cvr>, seed: Long) : MvrManagerClcaIF, MvrManagerTest {
    val cvrsUA: List<AuditableCard>
    val mvrsUA: List<AuditableCard>
    private var mvrsRound: List<AuditableCard> = emptyList()

    init {
        // the order of the cvrs cannot be changed.
        val prng = Prng(seed)
        cvrsUA = cvrs.mapIndexed { idx, it -> AuditableCard.fromCvr(it, idx, prng.next()) }.sortedBy { it.prn }
        mvrsUA = cvrsUA.map { AuditableCard.fromCvr(mvrs[it.index], it.index, it.prn) }
    }

    fun cvrs() = cvrs
    override fun Nballots(contestUA: ContestUnderAudit) = cvrs.size
    override fun ballotCards() : Iterator<AuditableCard> = cvrsUA.iterator()
    override fun setMvrsForRound(mvrs: List<AuditableCard>) {
        mvrsRound = mvrs.toList()
    }

    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>): List<AuditableCard> {
        val sampledMvrs = findSamples(sampleNumbers, mvrsUA.iterator()) // TODO use IteratorCvrsCsvFile?
        require(sampledMvrs.size == sampleNumbers.size)

        // debugging sanity check
        var lastRN = 0L
        sampledMvrs.forEach { mvr ->
            require(mvr.prn > lastRN)
            lastRN = mvr.prn
        }

        setMvrsForRound(sampledMvrs)
        return sampledMvrs
    }

    override fun setMvrsForRoundIdx(roundIdx: Int): List<AuditableCard> {
        TODO("Not yet implemented")
    }

    override fun makeCvrPairsForRound(): List<Pair<Cvr, Cvr>>  {
        if (mvrsRound.isEmpty()) {
            return mvrsUA.map { it.cvr() }.zip(cvrsUA.map { it.cvr() }) // all of em, for SingleRoundAudit
        }

        val sampleNumbers = mvrsRound.map { it.prn }
        val sampledCvrs = findSamples(sampleNumbers, cvrsUA.iterator())

        // prove that sampledCvrs correspond to mvrs
        require(sampledCvrs.size == mvrsRound.size)
        val cvruaPairs: List<Pair<AuditableCard, AuditableCard>> = mvrsRound.zip(sampledCvrs)
        cvruaPairs.forEach { (mvr, cvr) ->
            require(mvr.desc == cvr.desc)
            require(mvr.index == cvr.index)
            require(mvr.prn== cvr.prn)
        }
        return mvrsRound.map{ it.cvr() }.zip(sampledCvrs.map{ it.cvr() })
    }

    fun makeSampler(contestId: Int, hasStyles: Boolean, cassorter: ClcaAssorter, allowReset: Boolean): Sampler  {
        if (mvrsRound.isEmpty()) return makeOneRoundSampler(contestId, hasStyles, cassorter, allowReset) // TODO ???
        val cvrPairs = makeCvrPairsForRound()
        return ClcaWithoutReplacement(contestId, hasStyles,cvrPairs, cassorter, allowReset = allowReset)
    }

    // just use the entire cvrs/mvrs
    fun makeOneRoundSampler(contestId: Int, hasStyles: Boolean, cassorter: ClcaAssorter, allowReset: Boolean): Sampler {
        val cvrPairs = mvrsUA.map{ it.cvr() }.zip(cvrsUA.map{ it.cvr() })
        return ClcaWithoutReplacement(contestId, hasStyles, cvrPairs, cassorter, allowReset = allowReset)
    }
}

class MvrManagerPollingForTesting(val cardLocations: List<CardLocation>, mvrs: List<Cvr>, seed: Long) : MvrManagerPollingIF, MvrManagerTest {
    val ballotsUA: List<AuditableCard>
    val mvrsUA: List<AuditableCard>
    var mvrsRound: List<AuditableCard> = emptyList()

    init {
        val prng = Prng(seed)
        ballotsUA = cardLocations.mapIndexed { idx, it -> AuditableCard.fromBallot(it, idx, prng.next()) }
            .sortedBy { it.prn}
        mvrsUA = ballotsUA.map { AuditableCard.fromCvr(mvrs[it.index], it.index, it.prn) }
    }

    override fun Nballots(contestUA: ContestUnderAudit) = cardLocations.size
    override fun ballotCards() : Iterator<AuditableCard> = ballotsUA.iterator()
    override fun setMvrsForRound(mvrs: List<AuditableCard>) {
        mvrsRound = mvrs.toList()
    }

    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>): List<AuditableCard> {
        val sampledMvrs = findSamples(sampleNumbers, mvrsUA.iterator()) // TODO use IteratorCvrsCsvFile?
        require(sampledMvrs.size == sampleNumbers.size)

        // debugging sanity check
        var lastRN = 0L
        sampledMvrs.forEach { mvr ->
            require(mvr.prn > lastRN)
            lastRN = mvr.prn
        }

        setMvrsForRound(sampledMvrs)
        return sampledMvrs
    }

    override fun setMvrsForRoundIdx(roundIdx: Int): List<AuditableCard> {
        TODO("Not yet implemented")
    }

    override fun makeMvrsForRound(): List<Cvr> {
        val sampledCvrs = if (mvrsRound.isEmpty())
            mvrsUA.map { it.cvr() }
        else
            mvrsRound.map { it.cvr() }

        return sampledCvrs
    }
}