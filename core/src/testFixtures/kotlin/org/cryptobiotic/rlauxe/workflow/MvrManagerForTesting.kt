package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.Stopwatch

private val logger = KotlinLogging.logger("MvrManagerForTesting")

// simulated cvrs, mvrs for testing are sorted and kept here in memory
class MvrManagerForTesting(
    cvrs: List<Cvr>,
    mvrs: List<Cvr>,
    seed: Long,
    val pools: List<OneAuditPoolFromCvrs>? = null,
) : MvrManager, MvrManagerTestIF {

    val sortedCards: List<AuditableCard>
    val mvrsUA: List<AuditableCard> // the mvrs in the same order as the sorted cards
    private var mvrsRound: List<AuditableCard> = emptyList()

    init {
        // the order of the sortedCards cannot be changed once set.
        val prng = Prng(seed)
        sortedCards = cvrs.mapIndexed { idx, it -> AuditableCard.fromCvr(it, idx, prng.next()) }.sortedBy { it.prn }
        mvrsUA = sortedCards.map { AuditableCard.fromCvr(mvrs[it.index], it.index, it.prn) }
    }

    override fun cardManifest() :CardManifest {
        return CardManifest.createFromIterator(sortedCards.iterator(), sortedCards.size, pools)
    }

    override fun oapools(): List<OneAuditPoolFromCvrs>? {
        return pools
    }

    override fun makeMvrCardPairsForRound(round: Int): List<Pair<CvrIF, AuditableCard>>  {
        if (mvrsRound.isEmpty()) {
            return mvrsUA.zip(sortedCards) // all of em, for SingleRoundAudit
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
        return mvrsRound.zip(sampledCvrs)
    }

    // MvrManagerTestIF
    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>, round: Int): List<AuditableCard> {
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

// runs audit rounds until finished. return last audit round
// Can only use this if the MvrManager implements MvrManagerTest
// otherwise run one round at a time with PersistentAudit
fun runTestAuditToCompletion(name: String, workflow: AuditWorkflow, quiet: Boolean=true, maxRounds:Int=10): AuditRoundIF? {
    val stopwatch = Stopwatch()

    var nextRound: AuditRoundIF? = null
    var complete = false
    while (!complete) {
        nextRound = workflow.startNewRound(quiet=quiet)
        val roundidx = nextRound.roundIdx
        if (nextRound.samplePrns.isEmpty()) {
            complete = true

        } else {
            stopwatch.start()

            // workflow MvrManager must implement MvrManagerTest, else Exception
            (workflow.mvrManager() as MvrManagerTestIF).setMvrsBySampleNumber(nextRound.samplePrns, nextRound.roundIdx)

            logger.info {"runTestAuditToCompletion $name round=${roundidx}"}
            complete = workflow.runAuditRound(nextRound, quiet=quiet)
            nextRound.auditWasDone = true
            nextRound.auditIsComplete = complete
            // println(" runAudit $name ${nextRound.roundIdx} done=$complete samples=${nextRound.samplePrns.size}")
            if (nextRound.roundIdx > maxRounds) {
                logger.warn {" runAudit $name ${roundidx} exceeded maxRounds = $maxRounds"}
                nextRound = null
                break
            }  // safety net
        }
    }

    return nextRound
}
