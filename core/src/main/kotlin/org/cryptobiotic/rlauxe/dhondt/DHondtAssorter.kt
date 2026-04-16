package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.CvrIF
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.mean2margin

// winner,loser: candidate ids
// lastSeatWon: last seat won by winner
// firstSeatLost: last seat lost by loser
data class DHondtAssorter(val info: ContestInfo, val winner: Int, val loser: Int, val lastSeatWon: Int, val firstSeatLost: Int):
    AssorterIF {
    val upperg = 1.0 / lastSeatWon  // upper bound of g = 1/d(WA)  = 1/lastSeatWon   (highest loser)
    val lowerg = -1.0 / firstSeatLost  // lower bound of g = -1/d(WB) = -1/firstSeatLost (lowest winner)
    val c = -1.0 / (2 * lowerg)  // first/2
    private var dilutedMean: Double = 0.0

    fun setDilutedMean(mean: Double): DHondtAssorter {
        this.dilutedMean = mean
        return this
    }

    // Proportional p.15
    // gA,B (b) := bA /d(WA ) − bB /d(LB )
    // where bA (resp. bB ) is 1 if there is a vote for party A (resp. B), 0 otherwise.

    fun g(partyVote: Int): Double {
        return if (partyVote == winner) upperg
            else if (partyVote == loser) lowerg
            else 0.0
    }

    // h(b) = c · g(b) + 1/2
    fun h(partyVote: Int): Double {
        return c * g(partyVote) + 0.5
    }

    // l = h(-1/first) = -1/first * first/2 + 1/2 = 0
    // u = h(1/last) = 1/last * first/2 + 1/2 = (firstSeatLost/lastSeatWon+1)/2
    fun h2(g: Double): Double {
        return c * g + 0.5
    }

    // (first/last+1)/2
    override fun upperBound() = h2(upperg)
    override fun winner() = winner
    override fun loser() = loser
    override fun dilutedMean() = dilutedMean
    override fun dilutedMargin() = mean2margin(dilutedMean)

    // [ 0, .5, u]
    override fun assort(cvr: CvrIF, usePhantoms: Boolean): Double {
        if (!cvr.hasContest(info.id)) return 0.5
        if (usePhantoms && cvr.isPhantom()) return 0.0 // worst case
        val cands = cvr.votes(info.id)
        return if (cands != null && cands.size == 1) h(cands.first()) else 0.5
    }

    override fun desc() = buildString {
        append("${shortName()}: winner '${info.candidateIdToName[winner()]}'/$firstSeatLost")
        append(" loser ${info.candidateIdToName[loser()]}/$lastSeatWon upperBound=${df(upperBound())}")
    }
    override fun shortName() = "DHondt w/l='${info.candidateIdToName[winner()]}'/'${info.candidateIdToName[loser()]}'"
    override fun hashcodeDesc() = "${winLose()} ${info.name}" // must be unique for serialization

    fun winnerNameRound() =  "${info.candidateIdToName[winner()]}/$lastSeatWon"
    fun loserNameRound() =  "${info.candidateIdToName[loser()]}/$firstSeatLost"

    fun showAssertionDifficulty(votesForWinner: Int, votesForLoser: Int): String {
        val winnerScore = votesForWinner / lastSeatWon.toDouble()
        val loserScore = votesForLoser / firstSeatLost.toDouble()
        return "fw=${dfn(winnerScore, 1)} fl=${dfn(loserScore, 1)} fw-fl=${dfn(winnerScore - loserScore, 0)}"
    }

    fun difficulty(votesForWinner: Int, votesForLoser: Int): Double {
        val winnerScore = votesForWinner / lastSeatWon.toDouble()
        val loserScore = votesForLoser / firstSeatLost.toDouble()
        return winnerScore - loserScore
    }

    override fun calcMarginFromRegVotes(useVotes: Map<Int, Int>?, N: Int): Double {
        if (useVotes == null || N <= 0) {
            return 0.0
        } // shouldnt happen

        val winnerVotes = useVotes[winner()] ?: 0
        val loserVotes = useVotes[loser()] ?: 0

        val fw = winnerVotes / lastSeatWon.toDouble()
        val fl = loserVotes / firstSeatLost.toDouble()

        val gmean = (fw - fl)/N
        val hmean = h2(gmean)
        val margin = mean2margin(hmean)

        return margin
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DHondtAssorter) return false

        if (winner != other.winner) return false
        if (loser != other.loser) return false
        if (lastSeatWon != other.lastSeatWon) return false
        if (firstSeatLost != other.firstSeatLost) return false
        if (dilutedMean != other.dilutedMean) return false
        if (lowerg != other.lowerg) return false
        if (upperg != other.upperg) return false
        if (c != other.c) return false
        if (info != other.info) return false

        return true
    }

    override fun hashCode(): Int {
        var result = winner
        result = 31 * result + loser
        result = 31 * result + lastSeatWon
        result = 31 * result + firstSeatLost
        result = 31 * result + dilutedMean.hashCode()
        result = 31 * result + lowerg.hashCode()
        result = 31 * result + upperg.hashCode()
        result = 31 * result + c.hashCode()
        result = 31 * result + info.hashCode()
        return result
    }

    override fun toString() = desc()

    companion object {

        // parties that passed threshold
        fun makeDhondtAssorters(info: ContestInfo, Nc: Int, parties: List<DhondtCandidate>): List<DHondtAssorter> {
            // Let f_e,s = Te /d(s) for entity e and seat s
            // f_A,WA > f_B,LB, so e = A and s = Wa

            // Section 5.2 eq (4)
            // Converting this into the notation of Section 3, expressing Equation 4 as a linear
            // assertion gives us, ∀A s.t. WA !=⊥, ∀B 6= A s.t. LB !=⊥,
            //   TA /d(WA ) − TB /d(LB ) > 0.

            // This is O(n^2)
            val assorters = mutableListOf<DHondtAssorter>()
            parties.forEach { winner ->
                if (winner.lastSeatWon != null) {
                    parties.filter { it.id != winner.id }.forEach { loser ->
                        if (loser.firstSeatLost != null) {
                            val passorter = makeFrom(info, Nc, winner, loser)
                            assorters.add(passorter)
                        }
                    }
                }
            }
            return assorters
        }

        fun makeFrom(info: ContestInfo, Nc: Int, winner: DhondtCandidate, loser: DhondtCandidate): DHondtAssorter {
            // Let f_e,s = Te/d(s) for entity e and seat s
            // f_A,WA > f_B,LB, so e = A and s = Wa

            val fw = winner.votes / winner.lastSeatWon!!.toDouble()
            val fl = loser.votes / loser.firstSeatLost!!.toDouble()
            val gmean = (fw - fl) / Nc

            val lower = -1.0 / loser.firstSeatLost!!  // lower bound of g
            val upper = 1.0 / winner.lastSeatWon!!  // upper bound of g
            val c = -1.0 / (2 * lower)  // affine transform h = c * g + 1/2
            val hmean = c * gmean + 0.5

            return DHondtAssorter(
                info,
                winner.id,
                loser.id,
                lastSeatWon = winner.lastSeatWon!!,
                firstSeatLost = loser.firstSeatLost!!
            ).setDilutedMean(hmean)
        }

    }

}