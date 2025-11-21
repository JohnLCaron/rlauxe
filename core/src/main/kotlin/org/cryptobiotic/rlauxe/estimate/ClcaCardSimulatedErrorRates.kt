package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.pfn
import kotlin.random.Random

private val show = true
private val logger = KotlinLogging.logger("ClcaCardSimulatedErrorRates")

// only for one contest at atime, though cards can be multicontest. input cards are not changed.
// TODO assumes plurality assorter
// TODO handle OneAudit, only change the crvs. ?? or just change them randomly ...

/** 
 * Create Sampler with internal cvrs, and simulated mvrs with that match the given error rates.
 * Specific to a contest. The cvrs may be real or themselves simulated to match a Contest's vote.
 * Only used for estimating the sample size, not auditing.
 * Call reset to get a new permutation.
 */
class ClcaCardSimulatedErrorRates(
    cards: List<AuditableCard>, // may have phantoms
    val contest: ContestIF,
    val cassorter: ClcaAssorter,
    val errorRates: PluralityErrorRates,
): Sampler {
    val Ncards = cards.size
    val maxSamples = cards.count { it.hasContest(contest.id) }
    val isIRV = contest.isIrv()
    val mvrs: List<AuditableCard>
    val cvrs: List<AuditableCard>
    val changedCards = mutableSetOf<String>()

    val permutedIndex = MutableList(Ncards) { it }
    val flippedVotesP1o: Int
    val flippedVotesP2o: Int
    val flippedVotesP1u: Int
    val flippedVotesP2u: Int
    val noerror = cassorter.noerror()

    private var idx = 0
    private var count = 0

    init {
        // reset() we use the original order unless reset() is called, then we use a permutation
        // we want to flip the exact number of votes, for reproducibility
        // note we only do this on construction, reset just uses a different permutation
        val mmvrs = mutableListOf<AuditableCard>()
        mmvrs.addAll(cards)
        val ccvrs = mutableListOf<AuditableCard>()
        ccvrs.addAll(cards)

        flippedVotesP1o = flipP1o(mmvrs, needToChange = (maxSamples * errorRates.p1o).toInt())
        flippedVotesP2o = flipP2o(mmvrs, needToChange = (maxSamples * errorRates.p2o).toInt())
        flippedVotesP2u = flipP2u(mmvrs, needToChange = (maxSamples * errorRates.p2u).toInt())
        flippedVotesP1u = if (isIRV) flipP1uIRV(mmvrs, needToChange = (maxSamples * errorRates.p1u).toInt()) else
                          flipP1uP(mmvrs, ccvrs, needToChange = (maxSamples * errorRates.p1u).toInt())

        mvrs = mmvrs.toList()
        cvrs = ccvrs.toList()
    }

    override fun maxSamples() = maxSamples
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = mvrs.size

    override fun reset() {
        permutedIndex.shuffle(Random)
        idx = 0
        count = 0
    }

    override fun sample(): Double {
        while (idx < Ncards) {
            val card = cvrs[permutedIndex[idx]]
            val mvr = mvrs[permutedIndex[idx]]
            idx++
            if (card.hasContest(contest.id)) {
                val hasStyle = card.poolId == null // TODO may not be right
                val result = cassorter.bassort(mvr.cvr(), card.cvr(), hasStyle)
                count++
                return result
            }
        }
        throw RuntimeException("ClcaSimulation: no samples left for contest=${contest.id} and ComparisonAssorter ${cassorter}")
    }

    override fun hasNext() = (count < maxSamples)
    override fun next() = sample()

    fun showFlips() = buildString {
        appendLine("  flippedVotesP1o = $flippedVotesP1o = ${pfn(flippedVotesP1o/Ncards.toDouble())} expect=${needToChange(0)}")
        appendLine("  flippedVotesP2o = $flippedVotesP2o = ${pfn(flippedVotesP2o/Ncards.toDouble())} expect=${needToChange(1)}")
        appendLine("  flippedVotesP1u = $flippedVotesP1u = ${pfn(flippedVotesP1u/Ncards.toDouble())} expect=${needToChange(2)}")
        appendLine("  flippedVotesP2u = $flippedVotesP2u = ${pfn(flippedVotesP2u/Ncards.toDouble())} expect=${needToChange(3)}")
    }

    fun needToChange(idx:Int): Int {
        val err = errorRates.toList()[idx]
        return (maxSamples * err).toInt()
    }

    //  plurality:  two vote overstatement: cvr has winner (1), mvr has loser (0)
    //  NEB two vote overstatement: cvr has winner as first pref (1), mvr has loser preceeding winner (0)
    //  NEN two vote overstatement: cvr has winner as first pref among remaining (1), mvr has loser as first pref among remaining (0)
    fun flipP2o(mcvrs: MutableList<AuditableCard>, needToChange: Int): Int {
        if (needToChange == 0) return 0
        val ncards = mcvrs.size
        // val startingAvotes = mcvrs.filter { cassorter.assorter().assort(it) == 1.0 }.count() // TODO HERE
        var changed = 0

        var cardIdx = 0
        while (changed < needToChange && cardIdx < ncards) {
            val card = mcvrs[cardIdx] // this is the cvr
            if ((card.poolId == null) && !changedCards.contains(card.location) && card.votedForWinner(cassorter)) { // find a winner
                val votes = if (isIRV)
                    moveToFront(card.votes!!, contest.id, cassorter.assorter().loser())
                else
                    changeToCandidate(card.votes!!, contest.id, cassorter.assorter().loser())

                val alteredMvr = makeNewCard(card, votes) // change mvr to loser
                require(alteredMvr.votedForLoser(cassorter))
                mcvrs[cardIdx] = alteredMvr
                changed++
            }
            cardIdx++
        }
        /*
        val checkAvotes = mcvrs.filter { cassorter.assorter().assort(it) == 1.0 }.count()
        if (checkAvotes != startingAvotes - needToChange)
            logger.warn { "contest ${contest.id} flipP2o could only flip $changed, wanted $needToChange"}
        // require(checkAvotes == startingAvotes - needToChange) */
        if (changed != needToChange)
            println(" ***needed ${needToChange} changed=$changed diff=${needToChange-changed}")
        return changed
    }

    //  plurality: two vote understatement: cvr has loser (0), mvr has winner (1)
    //  NEB two vote understatement: cvr has loser preceeding winner (0), mvr has winner as first pref (1)
    //  NEN two vote understatement: cvr has loser as first pref among remaining (0), mvr has winner as first pref among remaining (1)
    fun flipP2u(mcvrs: MutableList<AuditableCard>, needToChange: Int): Int {
        if (needToChange == 0) return 0
        val ncards = mcvrs.size
        // val startingAvotes = mcvrs.filter { cassorter.assorter().assort(it) == 0.0 }.count()
        var changed = 0

        var cardIdx = 0
        while (changed < needToChange && cardIdx < ncards) {
            val card = mcvrs[cardIdx]
            if ((card.poolId == null) && !changedCards.contains(card.location) && card.votedForLoser(cassorter)) { // find a loser
                val votes = if (isIRV)
                    moveToFront(card.votes!!, contest.id, cassorter.assorter().winner())
                else
                    changeToCandidate(card.votes!!, contest.id, cassorter.assorter().winner())

                val alteredMvr = makeNewCard(card, votes) // change to winner
                require(alteredMvr.votedForWinner(cassorter))
                mcvrs[cardIdx] = alteredMvr
                changed++
            }
            cardIdx++
        }

        /* val checkAvotes = mcvrs.filter { cassorter.assorter().assort(it) == 0.0 }.count()
        if (checkAvotes != startingAvotes - needToChange)
            logger.warn {"contest ${contest.id} flipP2u could only flip $changed, wanted $needToChange"}
        // require(checkAvotes == startingAvotes - needToChange) */
        return changed
    }

    //  plurality: one vote overstatement: cvr has winner (1), mvr has other (1/2)
    //  NEB one vote overstatement: cvr has winner as first pref (1), mvr has winner preceding loser, but not first (1/2)
    //  NEN one vote overstatement: cvr has winner as first pref among remaining (1), mvr has neither winner nor loser as first pref among remaining (1/2)
    fun flipP1o(mcvrs: MutableList<AuditableCard>, needToChange: Int): Int {
        if (needToChange == 0) return 0
        val ncards = mcvrs.size
        var changed = 0

        // we need winner -> other vote, needToChangeVotesFromA > 0
        // val startingAvotes = mcvrs.filter { cassorter.assorter().assort(it) == 1.0 }.count()
        var cardIdx = 0
        while (changed < needToChange && cardIdx < ncards) {
            val card = mcvrs[cardIdx]
            if ((card.poolId == null) && !changedCards.contains(card.location) && card.votedForWinner(cassorter)) {  // find a winner
                val votes = changeToUndervote(card.votes!!, contest.id)

                val alteredMvr = makeNewCard(card, votes) // change to undervote
                mcvrs[cardIdx] = alteredMvr
                require(alteredMvr.votedForNeither(cassorter))
                changed++
            }
            cardIdx++
        }
        /* val checkAvotes = mcvrs.filter { cassorter.assorter().assort(it) == 1.0 }.count()
        if (checkAvotes != startingAvotes - needToChange)
            logger.warn { "contest ${contest.id} flipP1o could only flip $changed, wanted $needToChange"}
        // require(checkAvotes == startingAvotes - needToChange) */

        return changed
    }

    //  plurality: one vote understatement: cvr has other (1/2), mvr has winner (1)
    //  NEB one vote understatement: cvr has winner preceding loser (1/2), but not first, mvr has winner as first pref (1)
    //  NEN one vote understatement: cvr has neither winner nor loser as first pref among remaining (1/2), mvr has winner as first pref among remaining (1)
    fun flipP1uIRV(mcvrs: MutableList<AuditableCard>, needToChange: Int): Int {
        if (needToChange == 0) return 0
        val ncards = mcvrs.size
        var changed = 0

        // val startingAvotes = mcvrs.filter { cassorter.assorter().assort(it) == 0.5 }.count()
        var cardIdx = 0
        while (changed < needToChange && cardIdx < ncards) {
            val card = mcvrs[cardIdx]
            if ((card.poolId == null) && !changedCards.contains(card.location) && card.votedForNeither(cassorter)) { // find a neutral
                val votes = moveToFront(card.votes!!, contest.id, cassorter.assorter().winner()) // TODO test

                val alteredMvr = makeNewCard(card, votes) // change to winner
                mcvrs[cardIdx] = alteredMvr
                require(alteredMvr.votedForWinner(cassorter))
                changed++
            }
            cardIdx++
        }
        /* val checkAvotes = mcvrs.filter { cassorter.assorter().assort(it) == 0.5 }.count()
        if (checkAvotes != startingAvotes - needToChange)
            logger.warn {"contest ${contest.id} flipP1uIRV could only flip $changed, wanted $needToChange"}
        // require(checkAvotes == startingAvotes - needToChange) */

        return changed
    }

    // TODO note we change the cvr here. side effects??
    // plurality: one vote understatement: cvr has other (1/2), mvr has winner (1). have to change cvr to other
    fun flipP1uP(mcvrs: MutableList<AuditableCard>, cvrs: MutableList<AuditableCard>, needToChange: Int): Int {
        if (needToChange == 0) return 0
        val ncards = mcvrs.size
        // val otherCandidate = max(cassorter.assorter().winner(), cassorter.assorter().loser()) + 1
        var changed = 0

        // val startingAvotes = cvrs.filter { cassorter.assorter().assort(it) == 1.0 }.count()
        var cardIdx = 0
        while (changed < needToChange && cardIdx < ncards) {
            val card = mcvrs[cardIdx]
            if ((card.poolId == null) && !changedCards.contains(card.location) && card.votedForWinner(cassorter)) { // find a winner in the mvrs
                val votes = changeToUndervote(card.votes!!, contest.id) // , cassorter.assorter().winner())mapOf(contest.id to intArrayOf(otherCandidate)) // TODO wrong, multiple contests! Yikes

                val alteredCvr = makeNewCard(card, votes) // change the cvr to undervote
                require(alteredCvr.votedForNeither(cassorter))
                cvrs[cardIdx] = alteredCvr // Note we are changing the cvr, not the mvr
                changed++
            }
            cardIdx++
        }
        /* val checkAvotes = cvrs.count { cassorter.assorter().assort(it) == 1.0 }
        if (checkAvotes != startingAvotes - needToChange)
            logger.warn {"contest ${contest.id} flipP1uP could only flip $changed, wanted $needToChange"}
       // require(checkAvotes == startingAvotes - needToChange) */

        return changed
    }

    fun moveToFront(votes: Map<Int, IntArray>, contestId: Int, toFront: Int) : Map<Int, IntArray> {
        // TODO test: all we have to do is put a candidate that is not the winner or the loser, aka other
        val result = votes.toMutableMap()
        val c = result[contestId]!!
        val removed = c.filterNot{ it == toFront }
        result[contestId] = (listOf(toFront) + removed).toIntArray()
        return result
    }

    fun changeToUndervote(votes: Map<Int, IntArray>, contestId: Int) : Map<Int, IntArray> {
        val result = votes.toMutableMap()
        result[contestId] = intArrayOf()
        return result
    }

    fun changeToCandidate(votes: Map<Int, IntArray>, contestId: Int, candId: Int) : Map<Int, IntArray> {
        val result = votes.toMutableMap()
        result[contestId] = intArrayOf(candId)
        return result
    }

    fun makeNewCard(old: AuditableCard, newVotes: Map<Int, IntArray>): AuditableCard {
        changedCards.add(old.location)
        return old.copy(votes=newVotes)
    }
}

fun AuditableCard.votedForWinner(cassorter: ClcaAssorter): Boolean {
    val assortVal = cassorter.assorter().assort(this.cvr())
    return assortVal > .5
}
fun AuditableCard.votedForLoser(cassorter: ClcaAssorter): Boolean {
    val assortVal = cassorter.assorter().assort(this.cvr())
    return assortVal < .5
}
fun AuditableCard.votedForNeither(cassorter: ClcaAssorter): Boolean {
    val assortVal = cassorter.assorter().assort(this.cvr())
    return doubleIsClose(assortVal, .5)
}



