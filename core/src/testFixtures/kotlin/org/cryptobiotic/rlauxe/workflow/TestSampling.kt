package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardIF
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ClcaAssorter
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestIF
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.PluralityErrorRates
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.pfn
import kotlin.math.max
import kotlin.random.Random

private val logger = KotlinLogging.logger("TestSampling")

//// For clca audits with styles and no errors
class ClcaNoErrorIterator(
    val contestId: Int,
    val contestNc: Int,
    val cassorter: ClcaAssorter,
    val cvrIterator: Iterator<CardIF>,
): Sampling, Iterator<Double> {
    private var idx = 0
    private var count = 0
    private var done = false

    override fun sample(): Double {
        while (cvrIterator.hasNext()) {
            val cvr = cvrIterator.next()
            idx++
            if (cvr.hasContest(contestId)) {
                val result = cassorter.bassort(cvr, cvr)
                count++
                return result
            }
        }
        done = true
        if (!warned) {
            logger.warn { "ClcaNoErrorIterator no samples left for ${contestId} and ComparisonAssorter ${cassorter}" }
            warned = true
        }
        return 0.0
    }

    override fun reset() {
        logger.error{"ClcaNoErrorIterator reset not allowed"}
        throw RuntimeException("ClcaNoErrorIterator reset not allowed")
    }

    override fun maxSamples() = contestNc
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = contestNc

    override fun hasNext() = !done && cvrIterator.hasNext()
    override fun next() = sample()

    companion object {
        var warned = false
    }
}

// what about a function to fuzz the cvr or mvr on the fly ??
class OneAuditNoErrorIterator(
    val contestId: Int,
    val contestNc: Int,
    val contestSampleCutoff: Int?,
    val cassorter: ClcaAssorter,
    cvrIter: Iterator<Cvr>,
): Sampling, Iterator<Double> {
    val cvrs = mutableListOf<Cvr>()
    var permutedIndex = mutableListOf<Int>()

    private var idx = 0
    private var count = 0
    private var done = false

    init {
        while ((contestSampleCutoff == null || cvrs.size < contestSampleCutoff) && cvrIter.hasNext()) {
            val cvr = cvrIter.next()
            if (cvr.hasContest(contestId)) cvrs.add(cvr)
        }
        permutedIndex = MutableList(cvrs.size) { it }
    }

    override fun sample(): Double {
        while (idx < cvrs.size) {
            val cvr = cvrs[permutedIndex[idx]]
            idx++
            val result = cassorter.bassort(cvr, cvr)
            count++
            return result
        }
        if (!warned) {
            logger.warn { "OneAuditNoErrorIterator no samples left for ${contestId} and ComparisonAssorter ${cassorter}" }
            warned = true
        }
        return 0.0
    }

    override fun reset() {
        permutedIndex.shuffle(Random)
        idx = 0
        count = 0
    }

    override fun maxSamples() = contestNc
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = contestNc

    override fun hasNext() = !done && (count < contestNc)
    override fun next() = sample()

    companion object {
        var warned = false
    }
}

private val show = true

// TODO assumes plurality assorter
/**
 * Create Sampler with internal cvrs, and simulated mvrs with that match the given error rates.
 * Specific to a contest. The cvrs may be real or themselves simulated to match a Contest's vote.
 * Only used for estimating the sample size, not auditing.
 * Call reset to get a new permutation.
 */
class ClcaSimulatedErrorRates(
    rcvrs: List<Cvr>, // may have phantoms
    val contest: ContestIF,
    val cassorter: ClcaAssorter,
    val errorRates: PluralityErrorRates,
): Sampling {
    val Ncvrs = rcvrs.size
    val maxSamples = rcvrs.count { it.hasContest(contest.id) }
    val isIRV = contest.isIrv()
    val mvrs: List<Cvr>
    val cvrs: List<Cvr>
    val usedCvrs = mutableSetOf<String>()

    val permutedIndex = MutableList(Ncvrs) { it }
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
        val mmvrs = mutableListOf<Cvr>()
        mmvrs.addAll(rcvrs)
        val ccvrs = mutableListOf<Cvr>()
        ccvrs.addAll(rcvrs)

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
        while (idx < Ncvrs) {
            val cvr = cvrs[permutedIndex[idx]]
            val mvr = mvrs[permutedIndex[idx]]
            idx++
            if (cvr.hasContest(contest.id)) { // TODO ??
                val result = cassorter.bassort(mvr, cvr)
                count++
                return result
            }
        }
        throw RuntimeException("ClcaSimulation: no samples left for contest=${contest.id} and ComparisonAssorter ${cassorter}")
    }

    override fun hasNext() = (count < maxSamples)
    override fun next() = sample()

    /*
    fun showFlips() = buildString {
        appendLine(" flippedVotesP1o = $flippedVotesP1o = ${df(1.0*flippedVotesP1o/Nc)}")
        appendLine(" flippedVotesP2o = $flippedVotesP2o = ${df(1.0*flippedVotesP2o/Nc)}")
        appendLine(" flippedVotesP1u = $flippedVotesP1u = ${df(1.0*flippedVotesP1u/Nc)}")
        appendLine(" flippedVotesP2u = $flippedVotesP2u = ${df(1.0*flippedVotesP2u/Nc)}")
    }
     */

    //  plurality:  two vote overstatement: cvr has winner (1), mvr has loser (0)
    //  NEB two vote overstatement: cvr has winner as first pref (1), mvr has loser preceeding winner (0)
    //  NEN two vote overstatement: cvr has winner as first pref among remaining (1), mvr has loser as first pref among remaining (0)
    fun flipP2o(mcvrs: MutableList<Cvr>, needToChange: Int): Int {
        if (needToChange == 0) return 0
        val ncards = mcvrs.size
        val startingAvotes = mcvrs.count { cassorter.assorter().assort(it) == 1.0 }
        var changed = 0

        var cardIdx = 0
        while (changed < needToChange && cardIdx < ncards) {
            val cvr = mcvrs[cardIdx] // this is the cvr
            if (!usedCvrs.contains(cvr.id) && cassorter.assorter().assort(cvr) == 1.0) {
                val votes = if (isIRV) moveToFront(cvr.votes, contest.id, cassorter.assorter().loser()) else
                    mapOf(contest.id to intArrayOf(cassorter.assorter().loser()))

                val alteredMvr = makeNewCvr(cvr, votes) // this is the altered mvr
                mcvrs[cardIdx] = alteredMvr
                /* if (show && cassorter.assorter().assort(alteredMvr) != 0.0) {
                    println("  flipP2o ${cassorter.assorter().assort(alteredMvr)} != 0.0")
                    println("    cvr=${cvr}")
                    println("    alteredMvr=${alteredMvr}")
                } */
                require(cassorter.assorter().assort(alteredMvr) == 0.0)
                /* val bassort = cassorter.bassort(alteredMvr, cvr) // mvr, cvr
                if (bassort != 0.0 * cassorter.noerror()) { // p1
                    cassorter.bassort(alteredMvr, cvr)
                } */
                require(cassorter.bassort(alteredMvr, cvr) == 0.0 * cassorter.noerror())
                changed++
            }
            cardIdx++
        }
        val checkAvotes = mcvrs.count { cassorter.assorter().assort(it) == 1.0 }
        if (checkAvotes != startingAvotes - needToChange)
            logger.warn { "contest ${contest.id} flipP2o could only flip $changed, wanted $needToChange"}
        // require(checkAvotes == startingAvotes - needToChange)
        return changed
    }

    //  plurality: two vote understatement: cvr has loser (0), mvr has winner (1)
    //  NEB two vote understatement: cvr has loser preceeding winner (0), mvr has winner as first pref (1)
    //  NEN two vote understatement: cvr has loser as first pref among remaining (0), mvr has winner as first pref among remaining (1)
    fun flipP2u(mcvrs: MutableList<Cvr>, needToChange: Int): Int {
        if (needToChange == 0) return 0
        val ncards = mcvrs.size
        val startingAvotes = mcvrs.count { cassorter.assorter().assort(it) == 0.0 }
        var changed = 0

        var cardIdx = 0
        while (changed < needToChange && cardIdx < ncards) {
            val cvr = mcvrs[cardIdx]
            if (!usedCvrs.contains(cvr.id) && cassorter.assorter().assort(cvr) == 0.0) {
                val votes = if (isIRV) moveToFront(cvr.votes, contest.id, cassorter.assorter().winner()) else
                    mapOf(contest.id to intArrayOf(cassorter.assorter().winner()))
                val alteredMvr = makeNewCvr(cvr, votes)
                mcvrs[cardIdx] = alteredMvr
                if (show && cassorter.assorter().assort(alteredMvr) != 1.0) {
                    println("  flipP2u ${cassorter.assorter().assort(alteredMvr)} != 1.0")
                    println("    cvr=${cvr}")
                    println("    alteredMvr=${alteredMvr}")
                }
                require(cassorter.assorter().assort(alteredMvr) == 1.0)
                /* val bassort = cassorter.bassort(alteredMvr, cvr)
                if (bassort != 2.0 * cassorter.noerror()) { // p1
                    cassorter.bassort(alteredMvr, cvr) // mvr, cvr
                } */
                require(cassorter.bassort(alteredMvr, cvr) == 2.0 * cassorter.noerror())
                changed++
            }
            cardIdx++
        }

        val checkAvotes = mcvrs.count { cassorter.assorter().assort(it) == 0.0 }
        if (checkAvotes != startingAvotes - needToChange)
            logger.warn {"contest ${contest.id} flipP2u could only flip $changed, wanted $needToChange"}
        // require(checkAvotes == startingAvotes - needToChange)
        return changed
    }

    //  plurality: one vote overstatement: cvr has winner (1), mvr has other (1/2)
    //  NEB one vote overstatement: cvr has winner as first pref (1), mvr has winner preceding loser, but not first (1/2)
    //  NEN one vote overstatement: cvr has winner as first pref among remaining (1), mvr has neither winner nor loser as first pref among remaining (1/2)
    fun flipP1o(mcvrs: MutableList<Cvr>, needToChange: Int): Int {
        if (needToChange == 0) return 0
        val ncards = mcvrs.size
        var changed = 0

        // we need winner -> other vote, needToChangeVotesFromA > 0
        val startingAvotes = mcvrs.count { cassorter.assorter().assort(it) == 1.0 }
        var cardIdx = 0
        while (changed < needToChange && cardIdx < ncards) {
            val cvr = mcvrs[cardIdx]
            if (!usedCvrs.contains(cvr.id) && cassorter.assorter().assort(cvr) == 1.0) {
                val votes = changeToUndervote(cvr.votes, contest.id)
                val alteredMvr = makeNewCvr(cvr, votes)
                mcvrs[cardIdx] = alteredMvr
                if (show && cassorter.assorter().assort(alteredMvr) != 0.5) {
                    println("  flipP1o ${cassorter.assorter().assort(alteredMvr)} != 0.5")
                    println("    cvr=${cvr}")
                    println("    alteredMvr=${alteredMvr}")
                }
                require(cassorter.assorter().assort(alteredMvr) == 0.5)
                /* val bassort = cassorter.bassort(alteredMvr, cvr)
                if (bassort != 0.5 * cassorter.noerror()) { // p1
                    cassorter.bassort(alteredMvr, cvr)
                } */
                require(cassorter.bassort(alteredMvr, cvr) == 0.5 * cassorter.noerror())
                changed++
            }
            cardIdx++
        }
        val checkAvotes = mcvrs.count { cassorter.assorter().assort(it) == 1.0 }
        if (checkAvotes != startingAvotes - needToChange)
            logger.warn { "contest ${contest.id} flipP1o could only flip $changed, wanted $needToChange"}
        // require(checkAvotes == startingAvotes - needToChange)

        return changed
    }

    //  plurality: one vote understatement: cvr has other (1/2), mvr has winner (1)
    //  NEB one vote understatement: cvr has winner preceding loser (1/2), but not first, mvr has winner as first pref (1)
    //  NEN one vote understatement: cvr has neither winner nor loser as first pref among remaining (1/2), mvr has winner as first pref among remaining (1)
    fun flipP1uIRV(mcvrs: MutableList<Cvr>, needToChange: Int): Int {
        if (needToChange == 0) return 0
        val ncards = mcvrs.size
        var changed = 0

        val startingAvotes = mcvrs.count { cassorter.assorter().assort(it) == 0.5 }
        var cardIdx = 0
        while (changed < needToChange && cardIdx < ncards) {
            val cvr = mcvrs[cardIdx]
            if (!usedCvrs.contains(cvr.id) && cassorter.assorter().assort(cvr) == 0.5) {
                val votes = moveToFront(cvr.votes, contest.id, cassorter.assorter().winner())
                val alteredMvr = makeNewCvr(cvr, votes)
                mcvrs[cardIdx] = alteredMvr
                require(cassorter.assorter().assort(alteredMvr) == 1.0)
                val bassort = cassorter.bassort(alteredMvr, cvr)
                if (bassort != 1.5 * cassorter.noerror()) { // p3
                    cassorter.bassort(alteredMvr, cvr)
                }
                require(cassorter.bassort(alteredMvr, cvr) == 1.5 * cassorter.noerror())
                changed++
            }
            cardIdx++
        }
        val checkAvotes = mcvrs.count { cassorter.assorter().assort(it) == 0.5 }
        if (checkAvotes != startingAvotes - needToChange)
            logger.warn {"contest ${contest.id} flipP1uIRV could only flip $changed, wanted $needToChange"}
        // require(checkAvotes == startingAvotes - needToChange)

        return changed
    }

    //  plurality: one vote understatement: cvr has other (1/2), mvr has winner (1). have to change cvr to other
    fun flipP1uP(mcvrs: MutableList<Cvr>, cvrs: MutableList<Cvr>, needToChange: Int): Int {
        if (needToChange == 0) return 0
        val ncards = mcvrs.size
        val otherCandidate = max(cassorter.assorter().winner(), cassorter.assorter().loser()) + 1
        var changed = 0

        val startingAvotes = cvrs.count { cassorter.assorter().assort(it) == 1.0 }
        var cardIdx = 0
        while (changed < needToChange && cardIdx < ncards) {
            val mvr = mcvrs[cardIdx]
            if (!usedCvrs.contains(mvr.id) && (mvr.hasMarkFor(contest.id, cassorter.assorter().winner()) == 1)) { // aka cassorter.assorter().assort(it) == 1.0
                val votes = mapOf(contest.id to intArrayOf(otherCandidate))

                val alteredCvr = makeNewCvr(mvr, votes)
                require(cassorter.assorter().assort(alteredCvr) == 0.5)
                val bassort = cassorter.bassort(mvr, alteredCvr)
                if (bassort != 1.5 * cassorter.noerror()) { // p3
                    cassorter.bassort(mvr, alteredCvr)
                }
                require(cassorter.bassort(mvr, alteredCvr) == 1.5 * cassorter.noerror())
                cvrs[cardIdx] = alteredCvr // Note we are changing the cvr, not the mvr
                changed++
            }
            cardIdx++
        }
        val checkAvotes = cvrs.count { cassorter.assorter().assort(it) == 1.0 }
        if (checkAvotes != startingAvotes - needToChange)
            logger.warn {"contest ${contest.id} flipP1uP could only flip $changed, wanted $needToChange"}
        // require(checkAvotes == startingAvotes - needToChange)

        return changed
    }

    fun moveToFront(votes: Map<Int, IntArray>, contestId: Int, toFront: Int) : Map<Int, IntArray> {
        // all we have to do is put a candidate that is not the winner or the loser, aka other
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

    fun makeNewCvr(old: Cvr, votes: Map<Int, IntArray>): Cvr {
        usedCvrs.add(old.id)
        return Cvr(old, votes)
    }
}


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
): Sampling {
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


// for one contest, this takes a list of cvrs and fuzzes them
class ClcaFuzzSampler(
    val fuzzPct: Double,
    val cvrs: List<Cvr>,
    val contest: ContestIF,
    val cassorter: ClcaAssorter
): Sampling, Iterator<Double> {
    val maxSamples = cvrs.count { it.hasContest(contest.id) }
    val N = cvrs.size
    val permutedIndex = MutableList(N) { it }
    val welford = Welford()
    var cvrPairs: List<Pair<Cvr, Cvr>> // (mvr, cvr)
    var idx = 0

    init {
        val mvrs = remakeFuzzed()  // TODO could do fuzzing on the fly ??
        cvrPairs = mvrs.zip(cvrs)
    }

    override fun sample(): Double {
        while (idx < N) {
            val (mvr, cvr) = cvrPairs[permutedIndex[idx]]
            idx++
            if (cvr.hasContest(contest.id)) {
                val result = cassorter.bassort(mvr, cvr)
                welford.update(result)
                return result
            }
        }
        throw RuntimeException("no samples left for ${contest.id} and ComparisonAssorter ${cassorter}")
    }

    override fun reset() {
        val mvrs = remakeFuzzed()
        cvrPairs = mvrs.zip(cvrs)
        permutedIndex.shuffle(Random)
        idx = 0
    }

    fun remakeFuzzed(): List<Cvr> {
        return makeFuzzedCvrsFrom(listOf(contest.info()), cvrs, fuzzPct)
    }

    override fun maxSamples() = maxSamples
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = cvrPairs.size

    override fun hasNext(): Boolean = (idx < N)
    override fun next(): Double = sample()
}

// for one contest, this takes a list of cvrs and fuzzes them
class PollingFuzzSampler(
    val fuzzPct: Double,
    val cvrs: List<Cvr>,
    val contest: Contest,
    val assorter: AssorterIF
): Sampling, Iterator<Double> {
    val maxSamples = cvrs.count { it.hasContest(contest.id) } // dont need this is its single contest
    val N = cvrs.size
    val welford = Welford()
    val permutedIndex = MutableList(N) { it }
    private var mvrs: List<Cvr>
    private var idx = 0

    init {
        mvrs = remakeFuzzed() // TODO could do fuzzing on the fly ??
    }

    override fun sample(): Double {
        while (idx < N) {
            val mvr = mvrs[permutedIndex[idx]]
            idx++
            if (mvr.hasContest(contest.id)) {
                val result = assorter.assort(mvr, usePhantoms = true)
                welford.update(result)
                return result
            }
        }
        throw RuntimeException("no samples left for ${contest.id} and Assorter ${assorter}")
    }

    override fun reset() {
        mvrs = remakeFuzzed()
        permutedIndex.shuffle(Random)
        idx = 0
    }

    fun remakeFuzzed(): List<Cvr> {
        return makeFuzzedCvrsFrom(listOf(contest.info()), cvrs, fuzzPct) // single contest
    }

    override fun maxSamples() = maxSamples
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = mvrs.size

    override fun hasNext(): Boolean = (idx < N)
    override fun next(): Double = sample()
}


/* TODO no longer used by simulateSampleSizeOneAuditAssorter
class OneAuditFuzzSampler(
    val fuzzPct: Double,
    val cvrs: List<Cvr>,
    val contestUA: OAContestUnderAudit,
    val cassorter: ClcaAssorter
): Sampling, Iterator<Double> {
    val maxSamples = cvrs.count { it.hasContest(contestUA.id) }
    val N = cvrs.size
    val permutedIndex = MutableList(N) { it }
    val welford = Welford()
    val stratumNames : Set<String>
    var cvrPairs: List<Pair<Cvr, Cvr>> // (mvr, cvr)
    var idx = 0

    init {
        stratumNames = emptySet() // TODO contestUA.contest.pools.values.map { it.name }.toSet() // TODO
        val mvrs = remakeFuzzed()
        cvrPairs = mvrs.zip(cvrs)
    }

    override fun sample(): Double {
        while (idx < N) {
            val (mvr, cvr) = cvrPairs[permutedIndex[idx]]
            idx++
            if (cvr.hasContest(contestUA.id)) {
                val result = cassorter.bassort(mvr, cvr)
                welford.update(result)
                return result
            }
        }
        throw RuntimeException("no samples left for ${contestUA.id} and ComparisonAssorter ${cassorter}")
    }

    override fun reset() {
        val mvrs = remakeFuzzed()
        cvrPairs = mvrs.zip(cvrs)
        permutedIndex.shuffle(Random)
        idx = 0
    }

    fun remakeFuzzed(): List<Cvr> {
        return makeFuzzedCvrsFrom(listOf(contestUA.contest), cvrs, fuzzPct) { !stratumNames.contains(it.id) }
    }

    override fun maxSamples() = maxSamples
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = idx // TODO

    override fun hasNext(): Boolean = (idx < N)
    override fun next(): Double = sample()
} */

