package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.secureRandom
import kotlin.math.max

private val show = true

// create internal cvr and mvr with the correct under/over statements.
// specific to a contest. only used for estimating the sample size
class ComparisonSamplerSimulation(
        rcvrs: List<CvrUnderAudit>,
        val contestUA: ContestUnderAudit,
        val cassorter: ComparisonAssorter,
        val p1: Double = 1.0e-2, // apriori rate of 1-vote overstatements; voted for other, cvr has winner
        val p2: Double = 1.0e-4, // apriori rate of 2-vote overstatements; voted for loser, cvr has winner
        val p3: Double = 1.0e-2, // apriori rate of 1-vote understatements; voted for winner, cvr has other
        val p4: Double = 1.0e-4, // apriori rate of 2-vote understatements; voted for winner, cvr has loser
    ): GenSampleFn {

    val N = rcvrs.size
    val isIRV = contestUA.contest.choiceFunction == SocialChoiceFunction.IRV
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
        // reset() we use the original order unless reset() is called, then we use a permutation

        // we want to flip the exact number of votes, for reproducibility
        // note we only do this on construction, reset just uses a different permutation
        val mmvrs = mutableListOf<CvrUnderAudit>()
        mmvrs.addAll(rcvrs)
        val ccvrs = mutableListOf<CvrUnderAudit>()
        ccvrs.addAll(rcvrs)

        flippedVotes1 = flip1votes(mmvrs, needToChange = (N * p1).toInt())
        flippedVotes2 = flip2votes(mmvrs, needToChange = (N * p2).toInt())
        flippedVotes4 = flip4votes(mmvrs, needToChange = (N * p4).toInt())
        flippedVotes3 = if (isIRV) flip3votes(mmvrs, needToChange = (N * p3).toInt())
                        else flip3votesP(mmvrs, ccvrs, needToChange = (N * p3).toInt())

        mvrs = mmvrs.toList()
        cvrs = ccvrs.toList()

        sampleCount = rcvrs.filter { it.hasContest(contestUA.id) }.mapIndexed { idx, it -> cassorter.bassort(mvrs[idx], it) }.sum()
        sampleMean = sampleCount / N
    }

    override fun sampleMean() = sampleMean
    override fun sampleCount() = sampleCount
    override fun N() = N
    override fun reset() {
        permutedIndex.shuffle(secureRandom)
        idx = 0
    }

    override fun sample(): Double {
        while (idx < N) {
            val cvr = cvrs[permutedIndex[idx]]
            val mvr = mvrs[permutedIndex[idx]]
            if (cvr.hasContest(contestUA.id) && (cvr.sampleNum <= contestUA.sampleThreshold || contestUA.sampleThreshold == 0L)) {
                val result = cassorter.bassort(mvr, cvr)
                idx++
                return result
            }
            idx++
        }
        throw RuntimeException("no samples left for contest=${contestUA.id} and ComparisonAssorter ${cassorter}")
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
    fun flip2votes(mcvrs: MutableList<CvrUnderAudit>, needToChange: Int): Int {
        if (needToChange == 0) return 0
        val ncards = mcvrs.size
        val startingAvotes = mcvrs.filter { cassorter.assorter.assort(it) == 1.0 }.count()
        var changed = 0

        var cardIdx = 0
        while (changed < needToChange && cardIdx < ncards) {
            val cvr = mcvrs[cardIdx] // this is the cvr
            if (!cvr.used && cassorter.assorter.assort(cvr) == 1.0) {
                val votes = if (isIRV) moveToFront(cvr.votes, contestUA.id, cassorter.assorter.loser())
                            else mapOf(contestUA.id to intArrayOf(cassorter.assorter.loser()))

                val alteredMvr = makeNewCvr(cvr, votes) // this is the altered mvr
                mcvrs[cardIdx] = alteredMvr
                if (show && cassorter.assorter.assort(alteredMvr) != 0.0) {
                    println("  flip2votes ${cassorter.assorter.assort(alteredMvr)} != 0.0")
                    println("    cvr=${cvr.cvr}")
                    println("    alteredMvr=${alteredMvr.cvr}")
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
        require(checkAvotes == startingAvotes - needToChange)
        return changed
    }

    //  plurality: two vote understatement: cvr has loser (0), mvr has winner (1)
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
                val votes = if (isIRV) moveToFront(cvr.votes, contestUA.id, cassorter.assorter.winner())
                            else mapOf(contestUA.id to intArrayOf(cassorter.assorter.winner()))
                val alteredMvr = makeNewCvr(cvr, votes)
                mcvrs[cardIdx] = alteredMvr
                if (show && cassorter.assorter.assort(alteredMvr) != 1.0) {
                    println("  flip4votes ${cassorter.assorter.assort(alteredMvr)} != 1.0")
                    println("    cvr=${cvr.cvr}")
                    println("    alteredMvr=${alteredMvr.cvr}")
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
        require(checkAvotes == startingAvotes - needToChange)
        return changed
    }

    //  plurality: one vote overstatement: cvr has winner (1), mvr has other (1/2)
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
                val votes = emptyList(cvr.votes, contestUA.id)
                val alteredMvr = makeNewCvr(cvr, votes)
                mcvrs[cardIdx] = alteredMvr
                if (show && cassorter.assorter.assort(alteredMvr) != 0.5) {
                    println("  flip1votes ${cassorter.assorter.assort(alteredMvr)} != 0.5")
                    println("    cvr=${cvr.cvr}")
                    println("    alteredMvr=${alteredMvr.cvr}")
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
        require(checkAvotes == startingAvotes - needToChange)

        return changed
    }

    //  plurality: one vote understatement: cvr has other (1/2), mvr has winner (1)
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
        require(checkAvotes == startingAvotes - needToChange)

        return changed
    }

    //  plurality: one vote understatement: cvr has other (1/2), mvr has winner (1). have to change cvr to other
    fun flip3votesP(mcvrs: MutableList<CvrUnderAudit>, cvrs: MutableList<CvrUnderAudit>, needToChange: Int): Int {
        if (needToChange == 0) return 0
        val ncards = mcvrs.size
        val otherCandidate = max(cassorter.assorter.winner(), cassorter.assorter.loser()) + 1
        var changed = 0

        val startingAvotes = cvrs.filter { cassorter.assorter.assort(it) == 1.0 }.count()
        var cardIdx = 0
        while (changed < needToChange && cardIdx < ncards) {
            val mvr = mcvrs[cardIdx]
            if (!mvr.used && (mvr.hasMarkFor(contestUA.id, cassorter.assorter.winner()) == 1)) { // aka cassorter.assorter.assort(it) == 1.0
                val votes = mapOf(contestUA.id to intArrayOf(otherCandidate))

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
        val checkAvotes = cvrs.filter { cassorter.assorter.assort(it) == 1.0 }.count()
        if (checkAvotes != startingAvotes - needToChange)
            println("flip3votesP could only flip $changed, wanted $needToChange")
        require(checkAvotes == startingAvotes - needToChange)

        return changed
    }

    // previous
    fun flip3votes(mcvrs: MutableList<CvrUnderAudit>, cvrs: MutableList<CvrUnderAudit>, changeCvrToOther: Int): Int {
        if (changeCvrToOther == 0) return 0
        val ncards = mcvrs.size
        var changed = 0

        // random other candidate, just cant be winner or loser
        val otherCandidate = max(cassorter.assorter.winner(), cassorter.assorter.loser()) + 1

        val startingAvotes = cvrs.sumOf { it.hasMarkFor(contestUA.id, cassorter.assorter.winner()) }
        while (changed < changeCvrToOther) {
            val cvrIdx = secureRandom.nextInt(ncards)
            val mvr = mcvrs[cvrIdx]
            val cvr = cvrs[cvrIdx]
            if ((cvr.hasMarkFor(contestUA.id, cassorter.assorter.winner()) == 1) && (mvr.votes == cvr.votes)) {
                val votes = mapOf(contestUA.id to intArrayOf(otherCandidate))
                val altered = makeNewCvr(cvr, votes)
                cvrs[cvrIdx] = altered // Note we are changing the cvr, not the mvr
                require(cassorter.bassort(mvr, altered) == 1.5 * cassorter.noerror) // p3 loser -> other
                changed++
            }
        }
        val checkAvotes = cvrs.sumOf { it.hasMarkFor(contestUA.id, cassorter.assorter.winner()) }
        require(checkAvotes == startingAvotes - changeCvrToOther)

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

    fun makeNewCvr(old: CvrUnderAudit, votes: Map<Int, IntArray>): CvrUnderAudit {
        val result = CvrUnderAudit(Cvr(old.cvr, votes), old.phantom, old.sampleNum)
        result.used =  true
        return result
    }
}