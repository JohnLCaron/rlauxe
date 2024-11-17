package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.secureRandom
import kotlin.math.max

private val show = true

/*
Not Eliminated Next (NEN) Assertions. "IRV Elimination"
NEN assertions compare the tallies of two candidates under the assumption that a specific set of
candidates have been eliminated. An instance of this kind of assertion could look like this:
  NEN: Alice > Bob if only {Alice, Bob, Diego} remain.

Not Eliminated Before (NEB) Assertions. "Winner Only"
Alice NEB Bob is an assertion saying that Alice cannot be eliminated before Bob, irrespective of which
other candidates are continuing.
 */

/*
Assertion                   RaireScore  ShangrlaScore            Notes
NEB (winner_only)
  c1 NEB ck where k > 1           1       1                 Supports 1st prefs for c1
  cj NEB ck where k > j > 1       0       1/2               cj precedes ck but is not first
  cj NEB ck where k < j          -1       0                 a mention of ck preceding cj

NEN(irv_elimination): ci > ck if only {S} remain
  where ci = first(pS(b))           1     1                 counts for ci (expected)
  where first(pS(b)) !∈ {ci , ck }  0     1/2               counts for neither cj nor ck
  where ck = first(pS(b))          -1     0                 counts for ck (unexpected)
 */

/*
  NEB two vote overstatement: cvr has winner as first pref (1), mvr has loser preceeding winner (0)
  NEB one vote overstatement: cvr has winner as first pref (1), mvr has winner preceding loser, but not first (1/2)
  NEB two vote understatement: cvr has loser preceeding winner(0), mvr has winner as first pref (1)
  NEB one vote understatement: cvr has winner preceding loser, but not first (1/2), mvr has winner as first pref (1)

  NEN two vote overstatement: cvr has winner as first pref among remaining (1), mvr has loser as first pref among remaining (0)
  NEN one vote overstatement: cvr has winner as first pref among remaining (1), mvr has neither winner nor loser as first pref among remaining (1/2)
  NEN two vote understatement: cvr has loser as first pref among remaining (0), mvr has winner as first pref among remaining (1)
  NEN one vote understatement: cvr has neither winner nor loser as first pref among remaining (1/2), mvr has winner as first pref among remaining  (1)
 */

// create internal cvr and mvr with the correct under/over statements.
// specific to a contest. only used for estimating the sample size
class ComparisonSamplerForRaire(
    rcvrs: List<CvrUnderAudit>,
    val contestUA: ContestUnderAudit,
    val cassorter: ComparisonAssorter,
    val p1: Double = 1.0e-2, // apriori rate of 1-vote overstatements; voted for other, cvr has winner
    val p2: Double = 1.0e-4, // apriori rate of 2-vote overstatements; voted for loser, cvr has winner
    val p3: Double = 1.0e-2, // apriori rate of 1-vote understatements; voted for winner, cvr has other
    val p4: Double = 1.0e-4, // apriori rate of 2-vote understatements; voted for winner, cvr has loser
    ): GenSampleFn {

    val N = rcvrs.size
    val mvrs: List<CvrUnderAudit>
    val cvrs: List<CvrUnderAudit>

    val permutedIndex = MutableList(N) { it }
    val sampleMean: Double
    val sampleCount: Double
    val flippedVotes1: Int
    val flippedVotes2: Int
    val flippedVotes3: Int
    val flippedVotes4: Int

    var idx = 0

    init {
        reset()

        // we want to flip the exact number of votes, for reproducibility
        // note we only do this on construction, reset just uses a different permutation
        val mmvrs = mutableListOf<CvrUnderAudit>()
        mmvrs.addAll(rcvrs)
        val ccvrs = mutableListOf<CvrUnderAudit>()
        ccvrs.addAll(rcvrs)

        flippedVotes1 = flip1votes(mmvrs, needToChange = (N * p1).toInt())
        flippedVotes2 = flip2votes(mmvrs, needToChange = (N * p2).toInt())
        flippedVotes4 = flip4votes(mmvrs, needToChange = (N * p4).toInt())
        flippedVotes3 = flip3votes(mmvrs, needToChange = (N * p3).toInt())

        mvrs = mmvrs.toList()
        cvrs = ccvrs.toList()

        sampleCount = rcvrs.filter { it.hasContest(contestUA.id) }.mapIndexed { idx, it -> cassorter.bassort(mvrs[idx], it) }.sum()
        sampleMean = sampleCount / N
    }

    override fun sampleMean() = sampleMean
    override fun sampleCount() = sampleCount
    override fun N() = N
    override fun reset() {
        permutedIndex.shuffle(secureRandom) // TODO ok to permute?
        idx = 0
    }

    override fun sample(): Double {
        while (idx < N) {
            val cvr = cvrs[permutedIndex[idx]]
            val mvr = mvrs[permutedIndex[idx]]
            if (cvr.hasContest(contestUA.id) && (cvr.sampleNum <= contestUA.sampleThreshold || contestUA.sampleThreshold == 0L)) {
                val result = cassorter.bassort(mvr, cvr) // TODO not sure of cvr vs cvr.cvr, w/re raire; not sure of permute
                idx++
                return result
            }
            idx++
        }
        throw RuntimeException("no samples left for contest=${contestUA.id} and ComparisonAssorter ${cassorter}")
    }

    //  NEB two vote overstatement: cvr has winner as first pref (1), mvr has loser preceeding winner (0)
    //  NEN two vote overstatement: cvr has winner as first pref among remaining (1), mvr has loser as first pref among remaining (0)
    fun flip2votes(mcvrs: MutableList<CvrUnderAudit>, needToChange: Int): Int {
        if (needToChange == 0) return 0
        val ncards = mcvrs.size
        val startingAvotes = mcvrs.filter { cassorter.assorter.assort(it) == 1.0 }.count()
        var changed = 0

        var cardIdx = 0
        while (changed < needToChange && cardIdx < ncards) {
            val cvr = mcvrs[cardIdx] // this is the cvr
            if (!cvr.used && cassorter.assorter.assort(cvr) == 1.0) {
                val votes = moveToFront(cvr.votes, contestUA.id, cassorter.assorter.loser())
                val alteredMvr = makeNewCvr(cvr, votes) // this is the altered mvr
                mcvrs[cardIdx] = alteredMvr
                if (show && cassorter.assorter.assort(alteredMvr) != 0.0) {
                    println("  flip2votes ${cassorter.assorter.assort(alteredMvr)} != 0.0")
                }
                require(cassorter.assorter.assort(alteredMvr) == 0.0)
                // require(cassorter.bassort(mvr, altered) == 0.0) // p2
                val bassort = cassorter.bassort(alteredMvr, cvr) // mvr, cvr
                // require(cassorter.bassort(mvr, altered) == 0.5 * cassorter.noerror) // p1
                if (bassort != 0.0 * cassorter.noerror) { // p1
                    cassorter.bassort(alteredMvr, cvr)
                }
                changed++
            }
            cardIdx++
        }
        val checkAvotes = mcvrs.filter { cassorter.assorter.assort(it) == 1.0 }.count()
        if (checkAvotes != startingAvotes - needToChange)
            println("flip2votes could only flip $changed, wanted $needToChange")
        // require(checkAvotes == startingAvotes - needToChangeWinnerToLoser)
        return changed
    }

    //  NEB two vote understatement: cvr has loser preceeding winner (0), mvr has winner as first pref (1)
    //  NEN two vote understatement: cvr has loser as first pref among remaining (0), mvr has winner as first pref among remaining (1)
    fun flip4votes(mcvrs: MutableList<CvrUnderAudit>, needToChange: Int): Int {
        if (needToChange == 0) return 0
        val ncards = mcvrs.size
        val startingAvotes = mcvrs.filter { cassorter.assorter.assort(it) == 0.0 }.count()
        var changed = 0

        var cardIdx = 0
        while (changed < needToChange && cardIdx < ncards) {
            val cvr = mcvrs[cardIdx]
            if (!cvr.used && cassorter.assorter.assort(cvr) == 0.0) {
                val votes = moveToFront(cvr.votes, contestUA.id, cassorter.assorter.winner())
                val alteredMvr = makeNewCvr(cvr, votes)
                mcvrs[cardIdx] = alteredMvr
                require(cassorter.assorter.assort(alteredMvr) == 1.0)
                // require(cassorter.bassort(mvr, altered) == 2.0 * cassorter.noerror) // p4
                val bassort = cassorter.bassort(alteredMvr, cvr)
                // require(cassorter.bassort(mvr, altered) == 0.5 * cassorter.noerror) // p1
                if (bassort != 2.0 * cassorter.noerror) { // p1
                    cassorter.bassort(alteredMvr, cvr) // mvr, cvr
                }
                changed++
            }
            cardIdx++
        }

        val checkAvotes = mcvrs.filter { cassorter.assorter.assort(it) == 0.0 }.count()
        if (checkAvotes != startingAvotes - needToChange)
            println("flip4votes could only flip $changed, wanted $needToChange")
        // require(checkAvotes == startingAvotes - needToChangeLoserToWinner)
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

    //  NEB one vote overstatement: cvr has winner as first pref (1), mvr has winner preceding loser, but not first (1/2)
    //  NEN one vote overstatement: cvr has winner as first pref among remaining (1), mvr has neither winner nor loser as first pref among remaining (1/2)
    fun flip1votes(mcvrs: MutableList<CvrUnderAudit>, needToChange: Int): Int {
        if (needToChange == 0) return 0
        val ncards = mcvrs.size
        var changed = 0

        // we need winner -> other vote, needToChangeVotesFromA > 0
        val startingAvotes = mcvrs.filter { cassorter.assorter.assort(it) == 1.0 }.count()
        var cardIdx = 0
        while (changed < needToChange && cardIdx < ncards) {
            val cvr = mcvrs[cardIdx]
            if (!cvr.used && cassorter.assorter.assort(cvr) == 1.0) {
                val votes = flipCandidate(cvr.votes, contestUA.id, cassorter.assorter.winner(), cassorter.assorter.loser())
                val alteredMvr = makeNewCvr(cvr, votes)
                mcvrs[cardIdx] = alteredMvr
                if (show && cassorter.assorter.assort(alteredMvr) != 0.5) {
                    println("  flip1votes ${cassorter.assorter.assort(alteredMvr)} != 0.5}")
                }
                require(cassorter.assorter.assort(alteredMvr) == 0.5)
                val bassort = cassorter.bassort(alteredMvr, cvr)
                // require(cassorter.bassort(mvr, altered) == 0.5 * cassorter.noerror) // p1
                if (bassort != 0.5 * cassorter.noerror) { // p1
                    cassorter.bassort(alteredMvr, cvr)
                }
                changed++
            }
            cardIdx++
        }
        val checkAvotes = mcvrs.filter { cassorter.assorter.assort(it) == 1.0 }.count()
        if (checkAvotes != startingAvotes - needToChange)
            println("flip1votes could only flip $changed, wanted $needToChange")
        // require(checkAvotes == startingAvotes - changeWinnerToOther)

        return changed
    }

    fun makeNewCvr(old: CvrUnderAudit, votes: Map<Int, IntArray>): CvrUnderAudit {
        val result = CvrUnderAudit(Cvr(old.cvr, votes), old.phantom, old.sampleNum)
        result.used =  true
        return result
    }

    //  NEB one vote understatement: cvr has winner preceding loser (1/2), but not first, mvr has winner as first pref (1)
    //  NEN one vote understatement: cvr has neither winner nor loser as first pref among remaining (1/2), mvr has winner as first pref among remaining (1)
    fun flip3votes(mcvrs: MutableList<CvrUnderAudit>, needToChange: Int): Int {
        if (needToChange == 0) return 0
        val ncards = mcvrs.size
        var changed = 0

        val startingAvotes = mcvrs.filter { cassorter.assorter.assort(it) == 0.5 }.count()
        var cardIdx = 0
        while (changed < needToChange && cardIdx < ncards) {
            val cvr = mcvrs[cardIdx]
            if (!cvr.used && cassorter.assorter.assort(cvr) == 0.5) {
                val votes = moveToFront(cvr.votes, contestUA.id, cassorter.assorter.winner())
                val alteredMvr = makeNewCvr(cvr, votes)
                mcvrs[cardIdx] = alteredMvr // Note we are changing the cvr, not the mvr
                require(cassorter.assorter.assort(alteredMvr) == 1.0)
                // require(cassorter.bassort(mvr, altered) == 1.5 * cassorter.noerror) // p3
                val bassort = cassorter.bassort(alteredMvr, cvr)
                // require(cassorter.bassort(mvr, altered) == 0.5 * cassorter.noerror) // p1
                if (bassort != 1.5 * cassorter.noerror) { // p3
                    cassorter.bassort(alteredMvr, cvr)
                }
                changed++
            }
            cardIdx++
        }
        val checkAvotes = mcvrs.filter { cassorter.assorter.assort(it) == 0.5 }.count()
        if (checkAvotes != startingAvotes - needToChange)
            println("flip3votes could only flip $changed, wanted $needToChange")
        // require(checkAvotes == startingAvotes - changeCvrToOther)

        return changed
    }

    fun flipCandidate(votes: Map<Int, IntArray>, contestId: Int, winner: Int, loser: Int) : Map<Int, IntArray> {
        val result = votes.toMutableMap()
        val c = result[contestId]!!
        result[contestId] = intArrayOf(loser, winner)
        return result
    }
}