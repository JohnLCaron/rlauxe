package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.Stopwatch

// simulated cvrs, mvrs for testing are sorted and kept here in memory
class MvrManagerClcaForTesting(cvrs: List<Cvr>, mvrs: List<Cvr>, seed: Long) : MvrManagerClcaIF, MvrManagerTestIF {
    val sortedCards: List<AuditableCard>
    val mvrsUA: List<AuditableCard> // the mvrs in the same order as the sorted cards
    private var mvrsRound: List<AuditableCard> = emptyList()

    init {
        // the order of the sortedCards cannot be changed once set.
        val prng = Prng(seed)
        sortedCards = cvrs.mapIndexed { idx, it -> AuditableCard.fromCvr(it, idx, prng.next()) }.sortedBy { it.prn }
        mvrsUA = sortedCards.map { AuditableCard.fromCvr(mvrs[it.index], it.index, it.prn) }
    }

    override fun sortedCards() = CloseableIterable { Closer(sortedCards.iterator()) }

    override fun makeCvrPairsForRound(): List<Pair<Cvr, Cvr>>  {
        if (mvrsRound.isEmpty()) { // TODO filter ??
            return mvrsUA.map { it.cvr() }.zip(sortedCards.map { it.cvr() }) // all of em, for SingleRoundAudit
        }

        val sampleNumbers = mvrsRound.map { it.prn }
        val sampledCvrs = findSamples(sampleNumbers, Closer(sortedCards.iterator()))

        // prove that sampledCvrs correspond to mvrs
        require(sampledCvrs.size == mvrsRound.size)
        val cvruaPairs: List<Pair<AuditableCard, AuditableCard>> = mvrsRound.zip(sampledCvrs)
        cvruaPairs.forEach { (mvr, cvr) ->
            require(mvr.location == cvr.location)
            require(mvr.index == cvr.index)
            require(mvr.prn== cvr.prn)
        }
        return mvrsRound.map{ it.cvr() }.zip(sampledCvrs.map{ it.cvr() })
    }

    // MvrManagerTest
    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>): List<AuditableCard> {
        val sampledMvrs = findSamples(sampleNumbers, Closer(mvrsUA.iterator()))
        require(sampledMvrs.size == sampleNumbers.size)

        // debugging sanity check
        var lastRN = 0L
        sampledMvrs.forEach { mvr ->
            require(mvr.prn > lastRN)
            lastRN = mvr.prn
        }

        mvrsRound = sampledMvrs
        return sampledMvrs
    }

}

// simulated cardLocations, mvrs for testing are sorted and kept here in memory
class MvrManagerPollingForTesting(cvrs: List<Cvr>, mvrs: List<Cvr>, seed: Long) : MvrManagerPollingIF, MvrManagerTestIF {
    val sortedCards: List<AuditableCard>
    val mvrsUA: List<AuditableCard>
    var mvrsRound: List<AuditableCard> = emptyList()

    init {
        val prng = Prng(seed)
        sortedCards = cvrs.mapIndexed { idx, it -> AuditableCard.fromCvr(it, idx, prng.next()) }.sortedBy { it.prn}
        mvrsUA = sortedCards.map { AuditableCard.fromCvr(mvrs[it.index], it.index, it.prn) }
    }

    override fun sortedCards() = CloseableIterable { Closer(sortedCards.iterator()) }

    override fun makeMvrsForRound(): List<Cvr> {
        val sampledCvrs = if (mvrsRound.isEmpty())
            mvrsUA.map { it.cvr() }
        else
            mvrsRound.map { it.cvr() }

        return sampledCvrs
    }

    //MvrManagerTest
    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>): List<AuditableCard> {
        val sampledMvrs = findSamples(sampleNumbers, Closer(mvrsUA.iterator())) // TODO use IteratorCvrsCsvFile?
        require(sampledMvrs.size == sampleNumbers.size)

        // debugging sanity check
        var lastRN = 0L
        sampledMvrs.forEach { mvr ->
            require(mvr.prn > lastRN)
            lastRN = mvr.prn
        }

        mvrsRound =  sampledMvrs
        return sampledMvrs
    }
}

// runs audit rounds until finished. return last audit round
// Can only use this if the MvrManager implements MvrManagerTest
// otherwise run one round at a time with PersistentAudit
fun runTestAuditToCompletion(name: String, workflow: AuditWorkflow, quiet: Boolean=true, maxRounds:Int=10): AuditRound? {
    val stopwatch = Stopwatch()

    var nextRound: AuditRound? = null
    var complete = false
    while (!complete) {
        nextRound = workflow.startNewRound(quiet=quiet)
        if (nextRound.samplePrns.isEmpty()) {
            complete = true

        } else {
            stopwatch.start()

            // workflow MvrManager must implement MvrManagerTest, else Exception
            (workflow.mvrManager() as MvrManagerTestIF).setMvrsBySampleNumber(nextRound.samplePrns)

            if (!quiet) println("\nrunAudit $name ${nextRound.roundIdx}")
            complete = workflow.runAuditRound(nextRound, quiet)
            nextRound.auditWasDone = true
            nextRound.auditIsComplete = complete
            println(" runAudit $name ${nextRound.roundIdx} done=$complete samples=${nextRound.samplePrns.size}")
            if (nextRound.roundIdx > maxRounds) {
                println(" runAudit $name ${nextRound.roundIdx} exceedewd maxRounds = $maxRounds")
                break
            }  // safety net
        }
    }

    return nextRound
}
