package org.cryptobiotic.rlauxe.core

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.util.pfn

// from SuperSimple:
// The number µ is the “diluted margin”: the smallest margin of victory in votes among the contests, divided by the
// total number of ballots cast across all the contests. (p. 1)
// The diluted margin µ is the smallest margin in votes among the contests under audit, divided by the total
// number of ballots cast across all the contests under audit. (p. 4)
// The reported margin of reported winner w ∈ Wc over reported loser l ∈ Lc in contest c is
//    Vwl ≡ Sum (vpw − vpl), p=1..N = Sum (vpw) − Sum( vpl ) = votes(winner) - votes(loser) (p. 5)

// Define v ≡ 2Āc − 1, the reported assorter margin. In a two-candidate plurality contest, v
// is the fraction of ballot cards with valid votes for the reported winner, minus the fraction
// with valid votes for the reported loser. This is the diluted margin of [22,12]. (Margins are
// traditionally calculated as the difference in votes divided by the number of valid votes.
// Diluted refers to the fact that the denominator is the number of ballot cards, which is
// greater than or equal to the number of valid votes.) (SHANGRLA p. 10)

// phantom ballots are re-animated as evil zombies: We suppose that they reflect whatever would
// increase the P-value most: a 2-vote overstatement for a ballot-level comparison audit,
// or a valid vote for every loser in a ballot-polling audit.

interface AssorterIF {
    // usePhantoms=false for avgAssort = reportedMargin, and for the clca overstatement
    // usePhantoms=true for polling assort value
    fun assort(cvr: CvrIF, usePhantoms: Boolean = false) : Double

    fun upperBound(): Double
    fun desc(): String
    fun winner(): Int  // candidate id
    fun loser(): Int   // candidate id

    fun dilutedMargin(): Double
    fun dilutedMean(): Double

    // only used for CLCA
    fun noerror(): Double  {
        val ratio = dilutedMargin() / upperBound()
        return 1.0 / (2.0 - ratio)
    }

    fun hashcodeDesc(): String // Used as unique reference, DO NOT CHANGE!
    fun shortName() = winLose()
    fun winLose() = "${winner()}/${loser()}"

    // reportedMargin : N = Nc
    // dilutedMargin: Npop = sample population size
    // used when you need to calculate margin from some subset of regular votes; cant be used for IRV
    fun calcMarginFromRegVotes(useVotes: Map<Int, Int>?, N: Int): Double
}

/** See SHANGRLA, section 2.1, p.4 */
open class PluralityAssorter(val info: ContestInfo, val winner: Int, val loser: Int): AssorterIF {
    var dilutedMean: Double = 0.0

    fun setDilutedMean(mean: Double): PluralityAssorter {
        this.dilutedMean = mean
        return this
    }

    // If a ballot cannot be found (because the manifest is wrong, either because it lists a ballot that is not there, or
    //   because it does not list all the ballots), pretend that the audit actually finds a ballot, an evil zombie
    //   ballot that shows whatever would increase the P-value the most. For ballot-polling audits, this means
    //   pretending it showed a valid vote for every loser. P2Z section 2 p 3-4.

    // assort in {0, .5, u}
    // usePhantoms = true for polling, but when this is the "primitive assorter" in clca, usePhantoms = false so that
    //   clcaAssorter can handle the phantoms.
    override fun assort(cvr: CvrIF, usePhantoms: Boolean): Double {
        // if (!cvr.hasContest(info.id)) return if (hasStyle) 0.0 else 0.5 TODO use hasStyle?
        if (!cvr.hasContest(info.id)) return 0.5
        if (usePhantoms && cvr.isPhantom()) return 0.0 // worst case
        val w = cvr.hasMarkFor(info.id, winner)
        val l = cvr.hasMarkFor(info.id, loser)
        return (w - l + 1) * 0.5
    }

    override fun upperBound() = 1.0 // upper bound of assorter.assort()
    override fun desc() = " Plurality winner=$winner loser=$loser dilutedMargin=${pfn(dilutedMargin())} dilutedMean=${pfn(dilutedMean)}"
    override fun hashcodeDesc() = "${winLose()} ${info.name}" // must be unique for serialization
    override fun winner() = winner
    override fun loser() = loser
    override fun dilutedMargin() = mean2margin(dilutedMean)
    override fun dilutedMean() = dilutedMean

    override fun calcMarginFromRegVotes(useVotes: Map<Int, Int>?, N: Int): Double {
        if (useVotes == null) {
            val trace = Throwable().stackTraceToString()
            logger.error { "PluralityAssorter.calcMarginFromRegVotes called with useVotes == null\n$trace" }
            return 0.0
        }
        val winnerVotes = useVotes[winner()] ?: 0
        val loserVotes = useVotes[loser()] ?: 0
        return if (N == 0) 0.0 else (winnerVotes - loserVotes) / N.toDouble()
    }

    override fun toString(): String = desc()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PluralityAssorter) return false

        if (winner != other.winner) return false
        if (loser != other.loser) return false
        if (dilutedMean != other.dilutedMean) return false
        if (info != other.info) return false

        return true
    }

    override fun hashCode(): Int {
        var result = winner
        result = 31 * result + loser
        result = 31 * result + dilutedMean.hashCode()
        result = 31 * result + info.hashCode()
        return result
    }

    companion object {
        private val logger = KotlinLogging.logger("PluralityAssorter")

        fun makeWithVotes(contest: ContestIF, winner: Int, loser: Int, Npop: Int?=null): PluralityAssorter {
            val useVotes = contest.votes()!!
            val winnerVotes = useVotes[winner] ?: 0
            val loserVotes = useVotes[loser] ?: 0
            val totalVotes = Npop ?: contest.Nc()
            val dilutedMean = margin2mean((winnerVotes - loserVotes) / totalVotes.toDouble())
            return PluralityAssorter(contest.info(), winner, loser).setDilutedMean(dilutedMean)
        }
    }
}