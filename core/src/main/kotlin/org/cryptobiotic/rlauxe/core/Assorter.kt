package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.mean2margin


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
    fun assort(mvr: Cvr, usePhantoms: Boolean = false) : Double

    fun upperBound(): Double
    fun desc(): String
    fun winner(): Int  // candidate id
    fun loser(): Int   // candidate id
    fun reportedMargin(): Double // in (0, 1]
    fun reportedMean(): Double  // in (.5, 1]

    fun shortName() = "${winner()}/${loser()}"
    fun winLose() = "${winner()}/${loser()}"
    fun hashcodeDesc(): String // Used as unique reference, DO NOT CHANGE!

    // used when you need to calculate reportedMargin from some subset of votes
    fun calcReportedMargin(useVotes: Map<Int, Int>, Nc: Int): Double {
        val winnerVotes = useVotes[winner()] ?: 0
        val loserVotes = useVotes[loser()] ?: 0
        return if (Nc == 0) 0.0 else (winnerVotes - loserVotes) / Nc.toDouble()
    }
}

/** See SHANGRLA, section 2.1, p.4 */
open class PluralityAssorter(val info: ContestInfo, val winner: Int, val loser: Int): AssorterIF {
    var reportedMean: Double = 0.0

    fun setReportedMean(mean: Double): PluralityAssorter {
        this.reportedMean = mean
        return this
    }

    // If a ballot cannot be found (because the manifest is wrong—either because it lists a ballot that is not there, or
    //   because it does not list all the ballots), pretend that the audit actually finds a ballot, an evil zombie
    //   ballot that shows whatever would increase the P-value the most. For ballot-polling audits, this means
    //   pretending it showed a valid vote for every loser. P2Z section 2 p 3-4.

    // assort in {0, .5, 1}
    // usePhantoms = true for polling, but when this is the "primitive assorter" in clca, usePhantoms = false so that
    //   clcaAssorter can handle the phantoms.
    override fun assort(mvr: Cvr, usePhantoms: Boolean): Double {
        if (!mvr.hasContest(info.id)) return 0.5
        if (usePhantoms && mvr.phantom) return 0.0 // worst case
        val w = mvr.hasMarkFor(info.id, winner)
        val l = mvr.hasMarkFor(info.id, loser)
        return (w - l + 1) * 0.5
    }

    override fun upperBound() = 1.0
    override fun desc() = " winner=$winner loser=$loser reportedMargin=${dfn(reportedMargin(), 8)} reportedMean=${dfn(reportedMean, 8)}"
    override fun hashcodeDesc() = "${winLose()} ${info.hashCode()}" // must be unique for serialization
    override fun winner() = winner
    override fun loser() = loser
    override fun reportedMargin() = mean2margin(reportedMean)
    override fun reportedMean() = reportedMean

    override fun toString(): String = desc()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PluralityAssorter

        if (winner != other.winner) return false
        if (loser != other.loser) return false
        if (reportedMean != other.reportedMean) return false
        if (info != other.info) return false

        return true
    }

    override fun hashCode(): Int {
        var result = winner
        result = 31 * result + loser
        result = 31 * result + reportedMean.hashCode()
        result = 31 * result + info.hashCode()
        return result
    }

    companion object {
        fun makeWithVotes(contest: ContestIF, winner: Int, loser: Int, votes: Map<Int, Int>? = null): PluralityAssorter {
            val useVotes = votes ?: (contest as Contest).votes
            val winnerVotes = useVotes[winner] ?: 0
            val loserVotes = useVotes[loser] ?: 0
            val reportedMean = margin2mean((winnerVotes - loserVotes) / contest.Nc().toDouble())
            return PluralityAssorter(contest.info(), winner, loser).setReportedMean(reportedMean)
        }
    }
}

/** See SHANGRLA, section 2.3, p.5. */
data class SuperMajorityAssorter(val info: ContestInfo, val candId: Int, val minFraction: Double): AssorterIF {
    private val upperBound = 0.5 / minFraction // 1/2f  in (.5, Inf)
    var reportedMean: Double = 0.0

    fun setReportedMean(mean: Double): SuperMajorityAssorter {
        this.reportedMean = mean
        return this
    }

    init {
        require (minFraction > 0.0  && minFraction < 1.0)
    }

    // assort in {0, .5, u}, u > .5
    override fun assort(mvr: Cvr, usePhantoms: Boolean): Double {
        if (!mvr.hasContest(info.id)) return 0.5
        if (usePhantoms && mvr.phantom) return 0.0 // valid vote for every loser
        val w = mvr.hasMarkFor(info.id, candId)
        return if (mvr.hasOneVote(info.id, info.candidateIds)) (w / (2 * minFraction)) else .5
    }

    override fun upperBound() = upperBound
    override fun desc() = "SuperMajorityAssorter winner=$candId minFraction=$minFraction"
    override fun hashcodeDesc() = "winner=$candId minFraction=$minFraction ${info.hashCode()}" // must be unique for serialization
    override fun winner() = candId
    override fun loser() = -1
    override fun reportedMargin() = mean2margin(reportedMean)
    override fun reportedMean() = reportedMean

    override fun toString(): String {
        return "SuperMajorityAssorter(candId=$candId, minFraction=$minFraction, reportedMargin=${reportedMargin()}, upperBound=$upperBound)"
    }

    companion object {
        fun makeWithVotes(contest: ContestIF, winner: Int, minFraction: Double, votes: Map<Int, Int>?=null): SuperMajorityAssorter {
            val useVotes = votes ?: (contest as Contest).votes
            val winnerVotes = useVotes[winner] ?: 0
            val loserVotes = useVotes.filter { it.key != winner }.values.sum()
            val nuetralVotes = contest.Nc() - winnerVotes - loserVotes

            val weight = 1 / (2 * minFraction)
            val mean =  (winnerVotes * weight + nuetralVotes * 0.5) / contest.Nc().toDouble()
            return SuperMajorityAssorter(contest.info(), winner, minFraction).setReportedMean(mean)
        }
    }
}
