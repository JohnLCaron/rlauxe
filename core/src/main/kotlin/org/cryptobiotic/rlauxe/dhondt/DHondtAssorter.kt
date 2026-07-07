package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.betting.estMarginUpperFromSamples
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.CvrIF
import org.cryptobiotic.rlauxe.core.PoolRates
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.util.roundUp

/* You can transform to Plurality contest with voteForN=nwinners and the candidates are the $candName/$round */

// winner,loser: candidate ids
// lastSeatWon: last seat won by winner
// firstSeatLost: last seat lost by loser
// why do different DHondts have different upper limits ?? = (first/last+1)/2
data class DHondtAssorter(val info: ContestInfo, val winner: Int, val loser: Int, val lastSeatWon: Int, val firstSeatLost: Int):
    AssorterIF {
    val upperg = 1.0 / lastSeatWon  // upper bound of g = 1/d(WA)  = 1/lastSeatWon   (highest loser)
    val lowerg = -1.0 / firstSeatLost  // lower bound of g = -1/d(WB) = -1/firstSeatLost (lowest winner)
    val c = -1.0 / (2 * lowerg)  // first/2

    private var reportedMargin: Double = 0.0
    private var dilutedMargin: Double = 0.0

    fun setMeans(reportedMean: Double, dilutedMean: Double? = null): DHondtAssorter {
        this.reportedMargin = mean2margin(reportedMean)
        this.dilutedMargin = mean2margin(dilutedMean ?: reportedMean)
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
    override fun dilutedMargin() = dilutedMargin
    override fun reportedMargin() = reportedMargin

    // [ 0, .5, u]
    override fun assort(cvr: CvrIF, usePhantoms: Boolean): Double {
        if (!cvr.hasContest(info.id)) return 0.5
        if (usePhantoms && cvr.phantom()) return 0.0 // worst case
        val cands = cvr.votes(info.id)
        return if (cands != null && cands.size == 1) h(cands.first()) else 0.5
    }

    override fun desc() = "${shortName()}: upperBound=${df(upperBound())}"
    override fun shortName() = "DHondt w-l=${winnerNameRound()}-${loserNameRound()}"
    fun reverseName() = "DHondt w-l=${loserNameRound()}-${winnerNameRound()}"

    // Youd like to be able to add new assorters as needed, but the factoring out into contests.json makes that harder
    // we could add new assertionRound, but dont have the new assorters in contests.json
    override fun hashcodeDesc() = "${winnerNameRound()}-${loserNameRound()} ${info.name}" // must be unique for serialization

    fun winnerNameRound() =  "${info.candidateIdToName[winner()]}/$lastSeatWon"
    fun loserNameRound() =  "${info.candidateIdToName[loser()]}/$firstSeatLost"

    fun showAssertionDifficulty(votesForWinner: Int, votesForLoser: Int): String {
        val winnerScore = votesForWinner / lastSeatWon.toDouble()
        val loserScore = votesForLoser / firstSeatLost.toDouble()
        return "fw=${dfn(winnerScore, 1)} fl=${dfn(loserScore, 1)} fw-fl=${dfn(winnerScore - loserScore, 0)}"
    }

    fun voteDiff(votesForWinner: Int, votesForLoser: Int): Double {
        val winnerScore = votesForWinner / lastSeatWon.toDouble()
        val loserScore = votesForLoser / firstSeatLost.toDouble()
        return winnerScore - loserScore
    }

    fun scoreRange(Npop: Int, nsamples: Int, alpha: Double) : Int {
        val stdBet = 2.0 / 1.03905
        val marginUpper = estMarginUpperFromSamples(stdBet, nsamples, alpha)
        val margin = marginUpper * upperBound()  // this would be the difference in scores except for the affine transform
        val hmean = margin2mean(margin)

        // hmean = c * voteDiff/Npop + 0.5     // see makeFrom, below
        // (hmean - .5) * Npop / c = voteDiff
        return roundUp((hmean - .5) * Npop / c)
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

    override fun calcPoolRatesFromPoolTabulation(poolTab: ContestTabulation, Npop: Int): PoolRates {
        val winnerVotes = poolTab.votes[winner()] ?: 0
        val loserVotes = poolTab.votes[loser()] ?: 0
        val nuetralCounts = poolTab.ncards() - winnerVotes - loserVotes // undervotes

        //  winner, nuetral, loser
        return PoolRates(winnerVotes/Npop.toDouble(),
            nuetralCounts/Npop.toDouble(),
            loserVotes/Npop.toDouble(),
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DHondtAssorter) return false

        if (winner != other.winner) return false
        if (loser != other.loser) return false
        if (lastSeatWon != other.lastSeatWon) return false
        if (firstSeatLost != other.firstSeatLost) return false
        if (reportedMargin != other.reportedMargin) return false
        if (dilutedMargin != other.dilutedMargin) return false
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
        result = 31 * result + reportedMargin.hashCode()
        result = 31 * result + dilutedMargin.hashCode()
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
                            val passorter = makeFrom(info, winner, loser, Nc) // TODO use Npop
                            assorters.add(passorter)
                        }
                    }
                }
            }
            return assorters
        }

        fun makeFrom(info: ContestInfo, winner: DhondtCandidate, loser: DhondtCandidate, Nc: Int, Npop: Int?=null): DHondtAssorter {
            // Let f_e,s = Te/d(s) for entity e and seat s
            // f_A,WA > f_B,LB, so e = A and s = Wa

            val fw = winner.votes / winner.lastSeatWon!!.toDouble()
            val fl = loser.votes / loser.firstSeatLost!!.toDouble()
            val voteDiff = (fw - fl)

            val lower = -1.0 / loser.firstSeatLost!!  // lower bound of g
            val upper = 1.0 / winner.lastSeatWon!!  // upper bound of g
            val c = -1.0 / (2 * lower)  // affine transform h = c * g + 1/2
            val hmeanReported = c * voteDiff/Nc + 0.5
            val hmeanDiluted = c * voteDiff/(Npop ?: Nc) + 0.5

            return DHondtAssorter(
                info,
                winner.id,
                loser.id,
                lastSeatWon = winner.lastSeatWon!!,
                firstSeatLost = loser.firstSeatLost!!
            ).setMeans(hmeanReported, hmeanDiluted)
        }

        fun calcReportedMargin(info: ContestInfo, winner: DhondtCandidate, loser: DhondtCandidate, Nc: Int, Npop: Int?=null): Double {

            // Let f_e,s = Te/d(s) for entity e and seat s
            // f_A,WA > f_B,LB, so e = A and s = Wa

            val fw = winner.votes / winner.lastSeatWon!!.toDouble()
            val fl = loser.votes / loser.firstSeatLost!!.toDouble()
            val voteDiff = (fw - fl)

            val lower = -1.0 / loser.firstSeatLost!!  // lower bound of g
            val upper = 1.0 / winner.lastSeatWon!!  // upper bound of g
            val c = -1.0 / (2 * lower)  // affine transform h = c * g + 1/2
            val hmeanReported = c * voteDiff/Nc + 0.5
            val hmeanDiluted = c * voteDiff/(Npop ?: Nc) + 0.5
            return hmeanReported
        }

    }

}