package org.cryptobiotic.rlauxe.workflow

import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.persist.CardManifest
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readCardPoolCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.json.readBatchesJsonFile
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.nio.file.Files
import kotlin.io.path.Path

private val logger = KotlinLogging.logger("MvrManagerForTesting")

// simulated cvrs, mvrs for testing are sorted and kept here in memory
// not persistent
// TODO can we redo this as a MvrSource?
class MvrManagerForTesting(
    cvrs: List<Cvr>,
    mvrs: List<Cvr>,
    seed: Long,
    val pools: List<CardPool>? = null,
) : MvrManager, MvrManagerTestIF {

    val sortedCards: List<AuditableCard>
    val sortedMvrs: List<AuditableCard> // the mvrs in the same order as the sorted cards
    private var mvrsForRound: List<AuditableCard> = emptyList()

    init {
        // the order of the sortedCards cannot be changed once set.
        val prng = Prng(seed)
        sortedCards = cvrs.mapIndexed { idx, it -> AuditableCard(it, idx, prng.next()) }.sortedBy { it.prn }
        sortedMvrs = sortedCards.map { AuditableCard(mvrs[it.index], it.index, it.prn) }
    }

    override fun sortedManifest(): CardManifest {
        return CardManifest.createFromAList(sortedCards)
    }

    override fun pools() = pools
    override fun batches() = pools

    override fun makeMvrCardPairsForRound(round: Int): List<Pair<CvrIF, AuditableCard>> {
        if (mvrsForRound.isEmpty()) {
            return sortedMvrs.zip(sortedCards) // all of em, for SingleRoundAudit
        }

        val sampleNumbers = mvrsForRound.map { it.prn }
        val sampledCvrs = findSamples(sampleNumbers, Closer(sortedCards.iterator()))

        // prove that sampledCvrs correspond to mvrs
        require(sampledCvrs.size == mvrsForRound.size)
        val cvruaPairs: List<Pair<AuditableCard, AuditableCard>> = mvrsForRound.zip(sampledCvrs)
        cvruaPairs.forEach { (mvr, cvr) ->
            require(mvr.location == cvr.location)
            require(mvr.index == cvr.index)
            require(mvr.prn == cvr.prn)
        }
        return mvrsForRound.zip(sampledCvrs)
    }

    override fun writeMvrsForRound(round: Int): Int {
        TODO("Not yet implemented")
    }

    // MvrManagerTestIF
    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>, round: Int): List<AuditableCard> {
        val sampledMvrs = findSamples(sampleNumbers, Closer(sortedMvrs.iterator()))
        require(sampledMvrs.size == sampleNumbers.size)

        // debugging sanity check
        var lastRN = 0L
        sampledMvrs.forEach { mvr ->
            require(mvr.prn > lastRN)
            lastRN = mvr.prn
        }

        mvrsForRound = sampledMvrs
        return sampledMvrs
    }

    override fun toString(): String {
        return "MvrManagerForTesting(pools=${pools?.size}, sortedCards=${sortedCards.size}, sortedMvrs=${sortedMvrs.size}, mvrsRound=${mvrsForRound.size})"
    }
}

// runs audit rounds until finished. return last audit round
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
                // TODO set contest status ??
                nextRound = null
                break
            }  // safety net
        }
    }

    return nextRound // workflow.auditRounds().last() // hmm how do we indicate the riskLimit wasnt satisfied?
}

/////////////////////////////////////////////////////////////////
// no AuditRecord, pass in publisher...

fun readSortedManifest(publisher: Publisher, infos: Map<Int, ContestInfo>, ncards: Int): CardManifest {
    val batches = readBatches(publisher) ?: readCardPools(publisher, infos) ?: emptyList() // which is preferrred ?
    // merge batch references into the Card
    val mergedCards =
        MergeBatchesIntoCardManifestIterable(
            CloseableIterable { readCardsCsvIterator(publisher.sortedCardsFile()) },
            batches,
        )

    return CardManifest(mergedCards, ncards)
}

fun readBatches(publisher: Publisher): List<Batch>? {
    return if (!Files.exists(Path(publisher.batchesFile()))) null else {
        val batchesResult = readBatchesJsonFile(publisher.batchesFile())
        if (batchesResult.isOk) batchesResult.unwrap() else {
            logger.error{ "$batchesResult" }
            null
        }
    }
}

fun readCardPools(publisher: Publisher, infos: Map<Int, ContestInfo>): List<CardPool>? {
    return if (!Files.exists(Path(publisher.cardPoolsFile()))) null
    else readCardPoolCsvFile(publisher.cardPoolsFile(), infos)
}
