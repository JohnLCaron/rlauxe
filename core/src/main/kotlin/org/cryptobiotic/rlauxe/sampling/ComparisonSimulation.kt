package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.secureRandom
import kotlin.math.max

private val show = true

// create internal cvr and mvr with the correct under/over statements that match the passed in error rates.
// specific to a contest. only used for estimating the sample size
class ComparisonSimulation(
        rcvrs: List<Cvr>,
        val contest: ContestIF,
        val cassorter: ComparisonAssorter,
        errorRates: List<Double>,
    ): SampleGenerator {
    val p1: Double = errorRates[0] // rate of 1-vote overstatements; voted for other, cvr has winner
    val p2: Double = errorRates[1] // rate of 2-vote overstatements; voted for loser, cvr has winner
    val p3: Double = errorRates[2] // rate of 1-vote understatements; voted for winner, cvr has other
    val p4: Double = errorRates[3] // rate of 2-vote understatements; voted for winner, cvr has loser

    val maxSamples = rcvrs.count { it.hasContest(contest.id) }
    val N = rcvrs.size
    val isIRV = contest.choiceFunction == SocialChoiceFunction.IRV
    val mvrs: List<Cvr>
    val cvrs: List<Cvr>
    val usedCvrs = mutableSetOf<String>()

    val permutedIndex = MutableList(N) { it }
    val sampleMean: Double
    val sampleCount: Double
    val flippedVotes1: Int
    val flippedVotes2: Int
    val flippedVotes3: Int
    val flippedVotes4: Int

    var idx = 0

    init {
        // reset() we use the original order unless reset() is called, then we use a permutation

        // we want to flip the exact number of votes, for reproducibility
        // note we only do this on construction, reset just uses a different permutation
        val mmvrs = mutableListOf<Cvr>()
        rcvrs.forEach{ mmvrs.add(it) }
        val ccvrs = mutableListOf<Cvr>()
        ccvrs.addAll(rcvrs)

        flippedVotes1 = flip1votes(mmvrs, needToChange = (N * p1).toInt())
        flippedVotes2 = flip2votes(mmvrs, needToChange = (N * p2).toInt())
        flippedVotes4 = flip4votes(mmvrs, needToChange = (N * p4).toInt())
        flippedVotes3 = if (isIRV) flip3votes(mmvrs, needToChange = (N * p3).toInt())
                        else flip3votesP(mmvrs, ccvrs, needToChange = (N * p3).toInt())

        mvrs = mmvrs.toList()
        cvrs = ccvrs.toList()

        sampleCount = rcvrs.filter { it.hasContest(contest.id) }.mapIndexed { idx, it -> cassorter.bassort(mvrs[idx], it) }.sum()
        sampleMean = sampleCount / N
    }

    fun sampleMean() = sampleMean
    fun sampleCount() = sampleCount
    override fun maxSamples() = maxSamples

    override fun reset() {
        permutedIndex.shuffle(secureRandom)
        idx = 0
    }

    override fun sample(): Double {
        while (idx < N) {
            val cvr = cvrs[permutedIndex[idx]]
            val mvr = mvrs[permutedIndex[idx]]
            if (cvr.hasContest(contest.id)) {
                val result = cassorter.bassort(mvr, cvr)
                idx++
                return result
            }
            idx++
        }
        throw RuntimeException("no samples left for contest=${contest.id} and ComparisonAssorter ${cassorter}")
    }

    fun showFlips() = buildString {
        appendLine(" flippedVotes1 = $flippedVotes1 = ${df(100.0*flippedVotes1/N)}")
        appendLine(" flippedVotes2 = $flippedVotes2 = ${df(100.0*flippedVotes2/N)}")
        appendLine(" flippedVotes3 = $flippedVotes3 = ${df(100.0*flippedVotes3/N)}")
        appendLine(" flippedVotes4 = $flippedVotes4 = ${df(100.0*flippedVotes4/N)}")
    }

    //  plurality:  two vote overstatement: cvr has winner (1), mvr has loser (0)
    //  NEB two vote overstatement: cvr has winner as first pref (1), mvr has loser preceeding winner (0)
    //  NEN two vote overstatement: cvr has winner as first pref among remaining (1), mvr has loser as first pref among remaining (0)
    fun flip2votes(mcvrs: MutableList<Cvr>, needToChange: Int): Int {
        if (needToChange == 0) return 0
        val ncards = mcvrs.size
        val startingAvotes = mcvrs.filter { cassorter.assorter.assort(it) == 1.0 }.count()
        var changed = 0

        var cardIdx = 0
        while (changed < needToChange && cardIdx < ncards) {
            val cvr = mcvrs[cardIdx] // this is the cvr
            if (!usedCvrs.contains(cvr.id) && cassorter.assorter.assort(cvr) == 1.0) {
                val votes = if (isIRV) moveToFront(cvr.votes, contest.id, cassorter.assorter.loser())
                            else mapOf(contest.id to intArrayOf(cassorter.assorter.loser()))

                val alteredMvr = makeNewCvr(cvr, votes) // this is the altered mvr
                mcvrs[cardIdx] = alteredMvr
                if (show && cassorter.assorter.assort(alteredMvr) != 0.0) {
                    println("  flip2votes ${cassorter.assorter.assort(alteredMvr)} != 0.0")
                    println("    cvr=${cvr}")
                    println("    alteredMvr=${alteredMvr}")
                }
                require(cassorter.assorter.assort(alteredMvr) == 0.0)
                val bassort = cassorter.bassort(alteredMvr, cvr) // mvr, cvr
                if (bassort != 0.0 * cassorter.noerror) { // p1
                    cassorter.bassort(alteredMvr, cvr)
                }
                require(cassorter.bassort(alteredMvr, cvr) == 0.0 * cassorter.noerror)
                changed++
            }
            cardIdx++
        }
        val checkAvotes = mcvrs.filter { cassorter.assorter.assort(it) == 1.0 }.count()
        if (checkAvotes != startingAvotes - needToChange)
            println("flip2votes could only flip $changed, wanted $needToChange")
        // require(checkAvotes == startingAvotes - needToChange)
        return changed
    }

    //  plurality: two vote understatement: cvr has loser (0), mvr has winner (1)
    //  NEB two vote understatement: cvr has loser preceeding winner (0), mvr has winner as first pref (1)
    //  NEN two vote understatement: cvr has loser as first pref among remaining (0), mvr has winner as first pref among remaining (1)
    fun flip4votes(mcvrs: MutableList<Cvr>, needToChange: Int): Int {
        if (needToChange == 0) return 0
        val ncards = mcvrs.size
        val startingAvotes = mcvrs.filter { cassorter.assorter.assort(it) == 0.0 }.count()
        var changed = 0

        var cardIdx = 0
        while (changed < needToChange && cardIdx < ncards) {
            val cvr = mcvrs[cardIdx]
            if (!usedCvrs.contains(cvr.id) && cassorter.assorter.assort(cvr) == 0.0) {
                val votes = if (isIRV) moveToFront(cvr.votes, contest.id, cassorter.assorter.winner())
                            else mapOf(contest.id to intArrayOf(cassorter.assorter.winner()))
                val alteredMvr = makeNewCvr(cvr, votes)
                mcvrs[cardIdx] = alteredMvr
                if (show && cassorter.assorter.assort(alteredMvr) != 1.0) {
                    println("  flip4votes ${cassorter.assorter.assort(alteredMvr)} != 1.0")
                    println("    cvr=${cvr}")
                    println("    alteredMvr=${alteredMvr}")
                }
                require(cassorter.assorter.assort(alteredMvr) == 1.0)
                val bassort = cassorter.bassort(alteredMvr, cvr)
                if (bassort != 2.0 * cassorter.noerror) { // p1
                    cassorter.bassort(alteredMvr, cvr) // mvr, cvr
                }
                require(cassorter.bassort(alteredMvr, cvr) == 2.0 * cassorter.noerror)
                changed++
            }
            cardIdx++
        }

        val checkAvotes = mcvrs.filter { cassorter.assorter.assort(it) == 0.0 }.count()
        if (checkAvotes != startingAvotes - needToChange)
            println("flip4votes could only flip $changed, wanted $needToChange")
        // require(checkAvotes == startingAvotes - needToChange)
        return changed
    }

    //  plurality: one vote overstatement: cvr has winner (1), mvr has other (1/2)
    //  NEB one vote overstatement: cvr has winner as first pref (1), mvr has winner preceding loser, but not first (1/2)
    //  NEN one vote overstatement: cvr has winner as first pref among remaining (1), mvr has neither winner nor loser as first pref among remaining (1/2)
    fun flip1votes(mcvrs: MutableList<Cvr>, needToChange: Int): Int {
        if (needToChange == 0) return 0
        val ncards = mcvrs.size
        var changed = 0

        // we need winner -> other vote, needToChangeVotesFromA > 0
        val startingAvotes = mcvrs.filter { cassorter.assorter.assort(it) == 1.0 }.count()
        var cardIdx = 0
        while (changed < needToChange && cardIdx < ncards) {
            val cvr = mcvrs[cardIdx]
            if (!usedCvrs.contains(cvr.id) && cassorter.assorter.assort(cvr) == 1.0) {
                val votes = emptyList(cvr.votes, contest.id)
                val alteredMvr = makeNewCvr(cvr, votes)
                mcvrs[cardIdx] = alteredMvr
                if (show && cassorter.assorter.assort(alteredMvr) != 0.5) {
                    println("  flip1votes ${cassorter.assorter.assort(alteredMvr)} != 0.5")
                    println("    cvr=${cvr}")
                    println("    alteredMvr=${alteredMvr}")
                }
                require(cassorter.assorter.assort(alteredMvr) == 0.5)
                val bassort = cassorter.bassort(alteredMvr, cvr)
                if (bassort != 0.5 * cassorter.noerror) { // p1
                    cassorter.bassort(alteredMvr, cvr)
                }
                require(cassorter.bassort(alteredMvr, cvr) == 0.5 * cassorter.noerror)
                changed++
            }
            cardIdx++
        }
        val checkAvotes = mcvrs.filter { cassorter.assorter.assort(it) == 1.0 }.count()
        if (checkAvotes != startingAvotes - needToChange)
            println("flip1votes could only flip $changed, wanted $needToChange")
        // require(checkAvotes == startingAvotes - needToChange)

        return changed
    }

    //  plurality: one vote understatement: cvr has other (1/2), mvr has winner (1)
    //  NEB one vote understatement: cvr has winner preceding loser (1/2), but not first, mvr has winner as first pref (1)
    //  NEN one vote understatement: cvr has neither winner nor loser as first pref among remaining (1/2), mvr has winner as first pref among remaining (1)
    fun flip3votes(mcvrs: MutableList<Cvr>, needToChange: Int): Int {
        if (needToChange == 0) return 0
        val ncards = mcvrs.size
        var changed = 0

        val startingAvotes = mcvrs.filter { cassorter.assorter.assort(it) == 0.5 }.count()
        var cardIdx = 0
        while (changed < needToChange && cardIdx < ncards) {
            val cvr = mcvrs[cardIdx]
            if (!usedCvrs.contains(cvr.id) && cassorter.assorter.assort(cvr) == 0.5) {
                val votes = moveToFront(cvr.votes, contest.id, cassorter.assorter.winner())
                val alteredMvr = makeNewCvr(cvr, votes)
                mcvrs[cardIdx] = alteredMvr
                require(cassorter.assorter.assort(alteredMvr) == 1.0)
                val bassort = cassorter.bassort(alteredMvr, cvr)
                if (bassort != 1.5 * cassorter.noerror) { // p3
                    cassorter.bassort(alteredMvr, cvr)
                }
                require(cassorter.bassort(alteredMvr, cvr) == 1.5 * cassorter.noerror)
                changed++
            }
            cardIdx++
        }
        val checkAvotes = mcvrs.filter { cassorter.assorter.assort(it) == 0.5 }.count()
        if (checkAvotes != startingAvotes - needToChange)
            println("flip3votes could only flip $changed, wanted $needToChange")
        // require(checkAvotes == startingAvotes - needToChange)

        return changed
    }

    //  plurality: one vote understatement: cvr has other (1/2), mvr has winner (1). have to change cvr to other
    fun flip3votesP(mcvrs: MutableList<Cvr>, cvrs: MutableList<Cvr>, needToChange: Int): Int {
        if (needToChange == 0) return 0
        val ncards = mcvrs.size
        val otherCandidate = max(cassorter.assorter.winner(), cassorter.assorter.loser()) + 1
        var changed = 0

        val startingAvotes = cvrs.filter { cassorter.assorter.assort(it) == 1.0 }.count()
        var cardIdx = 0
        while (changed < needToChange && cardIdx < ncards) {
            val mvr = mcvrs[cardIdx]
            if (!usedCvrs.contains(mvr.id) && (mvr.hasMarkFor(contest.id, cassorter.assorter.winner()) == 1)) { // aka cassorter.assorter.assort(it) == 1.0
                val votes = mapOf(contest.id to intArrayOf(otherCandidate))

                val alteredCvr = makeNewCvr(mvr, votes)
                require(cassorter.assorter.assort(alteredCvr) == 0.5)
                val bassort = cassorter.bassort(mvr, alteredCvr)
                if (bassort != 1.5 * cassorter.noerror) { // p3
                    cassorter.bassort(mvr, alteredCvr)
                }
                require(cassorter.bassort(mvr, alteredCvr) == 1.5 * cassorter.noerror)
                cvrs[cardIdx] = alteredCvr // Note we are changing the cvr, not the mvr
                changed++
            }
            cardIdx++
        }
        val checkAvotes = cvrs.count { cassorter.assorter.assort(it) == 1.0 }
        if (checkAvotes != startingAvotes - needToChange)
            println("flip3votesP could only flip $changed, wanted $needToChange")
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

    fun emptyList(votes: Map<Int, IntArray>, contestId: Int) : Map<Int, IntArray> {
        val result = votes.toMutableMap()
        result[contestId] = intArrayOf()
        return result
    }

    fun makeNewCvr(old: Cvr, votes: Map<Int, IntArray>): Cvr {
        usedCvrs.add(old.id)
        return Cvr(old, votes)
    }
}