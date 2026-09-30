package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.betting.estMarginUpperFromSamples
import org.cryptobiotic.rlauxe.betting.estRisk
import org.cryptobiotic.rlauxe.betting.estRiskStandardBet
import org.cryptobiotic.rlauxe.betting.estSampleSize
import org.cryptobiotic.rlauxe.betting.stdBet
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.CvrIF
import org.cryptobiotic.rlauxe.core.PoolRates
import org.cryptobiotic.rlauxe.util.ContestTabulationIF
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.util.roundUp

/* You can transform to Plurality contest with voteForN=nwinners and the candidates are the $candName/$round */

// winner,loser: party ids
// winnerDivisor: last seat won by winner (aka lastSeatWon)
// loserDivisor: first seat lost by loser (aka firstSeatLost)
// why do different DHondts have different upper limits ?? = (first/last+1)/2; because of the dividors
data class DhondtAssorter(val info: ContestInfo, val winnerId: Int, val loserId: Int, val winnerDivisor: Int, val loserDivisor: Int):
    AssorterIF {
    val upperg = 1.0 / winnerDivisor  // upper bound of g = 1/d(WA)  = 1/lastSeatWon   (highest loser)
    val lowerg = -1.0 / loserDivisor  // lower bound of g = -1/d(WB) = -1/firstSeatLost (lowest winner)
    val c = -1.0 / (2 * lowerg)  // firstloser /2

    private var reportedMargin: Double = 0.0
    private var dilutedMargin: Double = 0.0

    fun setMeans(reportedMean: Double, dilutedMean: Double? = null): DhondtAssorter {
        this.reportedMargin = mean2margin(reportedMean)
        this.dilutedMargin = mean2margin(dilutedMean ?: reportedMean)
        return this
    }

    fun wtf () {
        val winnerTotalVotes = 127758
        val loserTotalVotes = 125871
        val Nci = 1083369
        val Nc = Nci.toDouble()

        val winnerDivisord = winnerDivisor.toDouble()
        val loserDivisord = loserDivisor.toDouble()
        val fw = winnerTotalVotes / winnerDivisord
        val fl = loserTotalVotes / loserDivisord
        val voteDiff = (fw - fl)

        val c1 = -1.0 / (2 * -1.0 / loserDivisor)
        val c2 = loserDivisor/ 2.0

        val hmean = c * voteDiff/Nc + 0.5 // = loserDivisor/2 * voteDiff/Nc + 0.5 = 0.5+loserDivisor * voteDiff / 2 * N)
        val hmean1 = (loserDivisord/ 2.0) * (voteDiff)/Nc + 0.5 // = loserDivisor/2 * voteDiff/Nc + 0.5 = 0.5+loserDivisor * voteDiff / 2 * N)
        val margin = mean2margin(hmean)
        println("margin diff = ${margin - reportedMargin}")

        val margin1 = 2.0 * hmean - 1.0
        val margin2 = 2.0 * ((loserDivisord/ 2.0) * (voteDiff)/Nc + 0.5) - 1.0
        val margin3 = (loserDivisord * (voteDiff)/Nc + 1.0) - 1.0
        val margin4 = loserDivisord * (winnerTotalVotes / winnerDivisord - loserTotalVotes / loserDivisord)/Nc
        val margin5 = loserDivisord * (winnerTotalVotes / winnerDivisord - loserTotalVotes / loserDivisord)/Nc
        val margin6 = (winnerTotalVotes * (loserDivisord/winnerDivisord) - loserTotalVotes)/Nc

        val lw = loserDivisord/winnerDivisord
        val margin7 = (winnerTotalVotes * lw - loserTotalVotes)/Nc

        println("margin diff = ${margin - reportedMargin}")

        val upper = upperBound()
        val upperh1 = (-1.0 / (2.0 * lowerg)) * upperg + .5
        val upperh2 = (-1.0 / (2.0 * (-1.0 / loserDivisord))) * upperg + .5
        val upperh3 = (loserDivisord/2.0) * upperg + .5
        val upperh4 = (loserDivisord/2.0) * (1.0 / winnerDivisord) + .5
        val upperh5 = loserDivisord/(2.0 * winnerDivisord) + 0.5
        val upperh6 = loserDivisord/winnerDivisord/(2.0) + 0.5
        val upperh7 = lw/2 + 0.5
        val upperh8 = 0.5 * (lw + 1)
        val upperh9 = (lw + 1)/2

        val upperh = lw/2 + 0.5 // = 0.5 * (1 + loserDivisor/winnerDivisor)
        println("upper diff = ${upperh - upperBound()}")

        val mu =  (margin / upperh)
        val mu1 = (winnerTotalVotes * lw - loserTotalVotes) / Nc / 2 * (lw + 1)
        val mu2 = (2/Nc) * (winnerTotalVotes * lw - loserTotalVotes) / (lw + 1)

        val noerror = 1.0 / (2.0 - margin / upperh4)
        val noerror1 = 1.0 / (2.0 - (2/Nc) * (winnerTotalVotes * lw - loserTotalVotes) / (lw + 1))
        val noerror2 = 1.0 / ((2.0*(lw + 1) - (2/Nc) * (winnerTotalVotes * lw - loserTotalVotes)) / (lw + 1))
        val noerror3 = (lw + 1) / (2.0 * ((lw + 1) - (winnerTotalVotes * lw - loserTotalVotes)/Nc))
        val noerror4 = (lw + 1)/2.0 / ((lw + 1) - (winnerTotalVotes * lw - loserTotalVotes)/Nc)
        val noerror5 = upper / ((lw + 1) - (winnerTotalVotes * lw - loserTotalVotes)/Nc)
        val noerror6 = upper / ((lw + 1) - margin)
        val lw1 = lw + 1

        println("noerror diff = ${noerror - noerror6}")
        println("noerror = $noerror")

        val inoerror = ((lw + 1) - (winnerTotalVotes * lw - loserTotalVotes)/Nc) / ((lw + 1)/2.0 )
        println("inoerror diff = ${noerror - 1/inoerror}")

        val inoerror2 = (lw + 1)/((lw + 1)/2.0 ) - (winnerTotalVotes * lw - loserTotalVotes)/Nc / ((lw + 1)/2.0)
        val inoerror3 = 2 - 2 * (winnerTotalVotes * lw - loserTotalVotes)/Nc / (lw + 1)

        println("inoerror diff = ${noerror - 1/inoerror3}")


        val Z = Nc * (1.0 + lw)

        val estRisk1 = estRiskStandardBet((winnerTotalVotes * lw - loserTotalVotes).toInt(), Nci, upper, 800)
        val estRisk2 = estRisk(Nci, stdBet, noerror, 800)
        println("estRisk = $estRisk1 $estRisk2")

        val estMvrs = estSampleSize(Nci, stdBet, noerror, .05)
        println("estMvrs = $estMvrs")


            //assorterMargin / upperBound = (2/N) (loserDivisor/winnerDivisor * winnerVotes - loserVotes) / (1 + loserDivisor/winnerDivisor)
        //assorterMargin / upperBound = 2 * (lw * winnerVotes - loserVotes) / N * (1 + lw)

        /*
        val noerror = 1.0 / (2.0 - assorterMargin / assorter.upperBound())
        val noerror = 1.0 / (2 - 2 * (lw * winnerVotes - loserVotes) / N * (1 + lw))
        val noerror = 0.5 / (1 - (lw * winnerVotes - loserVotes) / N * (1 + lw))
        val noerror = 0.5 / (N * (1 + lw) - (lw * winnerVotes - loserVotes)) / (N * (1 + lw))
        val noerror = 0.5 * (N * (1 + lw) / (N * (1 + lw) - (lw * winnerVotes - loserVotes)))
        val noerror = 0.5 * Z / (Z - (lw * winnerVotes - loserVotes))

         */


    }

    // Proportional p.15
    // gA,B (b) := bA /d(WA ) − bB /d(LB )
    // where bA (resp. bB ) is 1 if there is a vote for party A (resp. B), 0 otherwise.

    fun g(partyVote: Int): Double {
        return if (partyVote == winnerId) upperg
            else if (partyVote == loserId) lowerg
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
    override fun upperBound() = h2(upperg) // 0.5 + loserDivisor / 2 * winnerDivisor
    override fun winner() = winnerId
    override fun loser() = loserId
    override fun dilutedMargin() = dilutedMargin
    override fun reportedMargin() = reportedMargin

    // [ 0, .5, u]
    override fun assort(cvr: CvrIF, usePhantoms: Boolean): Double {
        if (!cvr.hasContest(info.id)) return 0.5
        if (usePhantoms && cvr.phantom()) return 0.0 // worst case
        val cands = cvr.votes(info.id)
        return if (cands != null && cands.size == 1) h(cands.first()) else 0.5
    }

    override fun desc() = "${shortName()}: noerror=${dfn(noerror(true), 6)}"
    override fun shortName() = "${winnerNameRound()}-${loserNameRound()}"
    fun reverseName() = "${loserNameRound()}-${winnerNameRound()}"

    // Youd like to be able to add new assorters as needed, but the factoring out into contests.json makes that harder
    // we could add new assertionRound, but dont have the new assorters in contests.json
    override fun hashcodeDesc() = "${winnerNameRound()}-${loserNameRound()} ${info.name}" // must be unique for serialization

    fun winnerNameRound() =  "${info.candidateIdToName[winner()]}/$winnerDivisor"
    fun loserNameRound() =  "${info.candidateIdToName[loser()]}/$loserDivisor"

    fun showAssertionDifficulty(votesForWinner: Int, votesForLoser: Int): String {
        val winnerScore = votesForWinner / winnerDivisor.toDouble()
        val loserScore = votesForLoser / loserDivisor.toDouble()
        return "fw=${dfn(winnerScore, 1)} fl=${dfn(loserScore, 1)} fw-fl=${dfn(winnerScore - loserScore, 0)}"
    }

    fun voteDiff(votesForWinner: Int, votesForLoser: Int): Double {
        val winnerScore = votesForWinner / winnerDivisor.toDouble()
        val loserScore = votesForLoser / loserDivisor.toDouble()
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

        val fw = winnerVotes / winnerDivisor.toDouble()
        val fl = loserVotes / loserDivisor.toDouble()

        val gmean = (fw - fl)/N
        val hmean = h2(gmean)
        val margin = mean2margin(hmean)

        return margin
    }

    override fun calcPoolRatesFromPoolTabulation(poolTab: ContestTabulationIF, Npop: Int): PoolRates {
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
        if (other !is DhondtAssorter) return false

        if (winnerId != other.winnerId) return false
        if (loserId != other.loserId) return false
        if (winnerDivisor != other.winnerDivisor) return false
        if (loserDivisor != other.loserDivisor) return false
        if (reportedMargin != other.reportedMargin) return false
        if (dilutedMargin != other.dilutedMargin) return false
        if (lowerg != other.lowerg) return false
        if (upperg != other.upperg) return false
        if (c != other.c) return false
        if (info != other.info) return false

        return true
    }

    override fun hashCode(): Int {
        var result = winnerId
        result = 31 * result + loserId
        result = 31 * result + winnerDivisor
        result = 31 * result + loserDivisor
        result = 31 * result + reportedMargin.hashCode()
        result = 31 * result + dilutedMargin.hashCode()
        result = 31 * result + lowerg.hashCode()
        result = 31 * result + upperg.hashCode()
        result = 31 * result + c.hashCode()
        result = 31 * result + info.hashCode()
        return result
    }

    override fun toString() = desc()

    /*
    companion object {

        // for each winner A and loser B, DH_AB(s(A), s(B) + 1)

        //     // BT(B) OR ( AND(DH_AB) for all A in Winners) for all B in Losers (2.5 prop 1 (2))
        // parties that passed threshold
        fun makeDhondtAssorters(info: ContestInfo, Nc: Int, parties: List<DhondtParty>): List<DhondtAssorter> {
            // Let f_e,s = Te /d(s) for entity e and seat s
            // f_A,WA > f_B,LB, so e = A and s = Wa

            // Section 5.2 eq (4)
            // Converting this into the notation of Section 3, expressing Equation 4 as a linear
            // assertion gives us, ∀A s.t. WA !=⊥, ∀B 6= A s.t. LB !=⊥,
            //   TA /d(WA ) − TB /d(LB ) > 0.

            // This is O(n^2)
            val assorters = mutableListOf<DhondtAssorter>()
            parties.forEach { winner ->
                if (winner.lastSeatWon != null) {
                    parties.filter { it.id != winner.id }.forEach { loser ->
                        if (loser.firstSeatLost != null) {
                            val passorter = makeFrom(info, winner, loser, Nc)
                            assorters.add(passorter)
                        }
                    }
                }
            }
            return assorters
        }

        // for each winner A and loser B, DH_AB(s(A), s(B) + 1)

        // use winner.lastSeatWon / loser.firstSeatLost
        fun makeFrom(info: ContestInfo, winner: DhondtParty, loser: DhondtParty, Nc: Int, Npop: Int?=null): DhondtAssorter {
            // Let f_e,s = Te/d(s) for entity e and seat s
            // f_A,WA > f_B,LB, so e = A and s = Wa

            val fw = winner.totalVotes / winner.lastSeatWon!!.toDouble()
            val fl = loser.totalVotes / loser.firstSeatLost!!.toDouble()
            val voteDiff = (fw - fl)

            val lower = -1.0 / loser.firstSeatLost!!  // lower bound of g
            val upper = 1.0 / winner.lastSeatWon!!  // upper bound of g
            val c = -1.0 / (2 * lower)  // affine transform h = c * g + 1/2
            val hmeanReported = c * voteDiff/Nc + 0.5
            val hmeanDiluted = c * voteDiff/(Npop ?: Nc) + 0.5

            return DhondtAssorter(
                info,
                winner.id,
                loser.id,
                winnerDivisor = winner.lastSeatWon!!,
                loserDivisor = loser.firstSeatLost!!
            ).setMeans(hmeanReported, hmeanDiluted)
        }

        fun calcReportedMargin(info: ContestInfo, winner: DhondtParty, loser: DhondtParty, Nc: Int, Npop: Int?=null): Double {

            // Let f_e,s = Te/d(s) for entity e and seat s
            // f_A,WA > f_B,LB, so e = A and s = Wa

            val fw = winner.totalVotes / winner.lastSeatWon!!.toDouble()
            val fl = loser.totalVotes / loser.firstSeatLost!!.toDouble()
            val voteDiff = (fw - fl)

            val lower = -1.0 / loser.firstSeatLost!!  // lower bound of g
            val upper = 1.0 / winner.lastSeatWon!!  // upper bound of g
            val c = -1.0 / (2 * lower)  // affine transform h = c * g + 1/2
            val hmeanReported = c * voteDiff/Nc + 0.5
            val hmeanDiluted = c * voteDiff/(Npop ?: Nc) + 0.5
            return hmeanReported
        }

    } */

}