package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.*
import kotlin.math.max
import kotlin.random.Random

private val show = true
private val logger = KotlinLogging.logger("ClcaSimulation")

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
): Sampler {
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
        val startingAvotes = mcvrs.filter { cassorter.assorter().assort(it) == 1.0 }.count()
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
        val checkAvotes = mcvrs.filter { cassorter.assorter().assort(it) == 1.0 }.count()
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
        val startingAvotes = mcvrs.filter { cassorter.assorter().assort(it) == 0.0 }.count()
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

        val checkAvotes = mcvrs.filter { cassorter.assorter().assort(it) == 0.0 }.count()
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
        val startingAvotes = mcvrs.filter { cassorter.assorter().assort(it) == 1.0 }.count()
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
        val checkAvotes = mcvrs.filter { cassorter.assorter().assort(it) == 1.0 }.count()
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

        val startingAvotes = mcvrs.filter { cassorter.assorter().assort(it) == 0.5 }.count()
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
        val checkAvotes = mcvrs.filter { cassorter.assorter().assort(it) == 0.5 }.count()
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

        val startingAvotes = cvrs.filter { cassorter.assorter().assort(it) == 1.0 }.count()
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