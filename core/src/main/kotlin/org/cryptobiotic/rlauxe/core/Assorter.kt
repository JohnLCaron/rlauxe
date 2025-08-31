package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.df
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
    // we need usePhantoms=false for avgAssort = reportedMargin, and for the overstatement
    // we need usePhantoms=true for polling assort
    fun assort(mvr: Cvr, usePhantoms: Boolean = false) : Double

    fun upperBound(): Double
    fun desc(): String
    fun winner(): Int  // candidate id
    fun loser(): Int   // candidate id
    fun reportedMargin(): Double // in (0, 1]
    fun reportedMean() = margin2mean(reportedMargin())  // in (.5, 1]

    // Calculate the assorter margin for the CVRs containing the given contest, including the phantoms,
    //    by treating the phantom CVRs as if they contain no valid vote in the contest
    //    (i.e., the assorter assigns the value 1/2 to phantom CVRs). SHANGRLA section 3.4 p 12.
    // It is not necessary to adjust the margins to account for those omissions. Rather, it is
    //    enough to treat only the ballots that the audit attempts to find but cannot find as votes for the losers
    //    (more generally, in the most pessimistic way) P2Z section 2 p. 3.

    // This only agrees with reportedMargin when the cvrs are complete with undervotes and phantoms.
    // Note that we rely on it.hasContest(contestId), assumes undervotes are in the cvr, ie hasStyle = true.
    fun calcAssorterMargin(contestId: Int, cvrs: Iterable<Cvr>, usePhantoms: Boolean = false, show: Boolean= false): Double {
        return mean2margin(calcAssortAvgFromCvrs(contestId, cvrs, usePhantoms))
    }
    fun calcAssortAvgFromCvrs(contestId: Int, cvrs: Iterable<Cvr>, usePhantoms: Boolean = false): Double {
        return cvrs.filter{ it.hasContest(contestId) }.map {
            val av = assort(it, usePhantoms = usePhantoms)
            av
        }.average()
    }

    fun calcReportedMargin(useVotes: Map<Int, Int>, Nc: Int): Double {
        val winnerVotes = useVotes[winner()] ?: 0
        val loserVotes = useVotes[loser()] ?: 0
        return (winnerVotes - loserVotes) / Nc.toDouble()
    }

}

/** See SHANGRLA, section 2.1, p.4 */
open class PluralityAssorter(val info: ContestInfo, val winner: Int, val loser: Int, val reportedMargin: Double): AssorterIF {

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
    override fun desc() = " winner=$winner loser=$loser reportedMargin=${df(reportedMargin)} reportedMean=${df(margin2mean(reportedMargin))}"
    override fun winner() = winner
    override fun loser() = loser
    override fun reportedMargin() = reportedMargin

    fun shortName() = " winner/loser= $winner/$loser"

    override fun toString(): String = desc()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PluralityAssorter

        if (winner != other.winner) return false
        if (loser != other.loser) return false
        if (reportedMargin != other.reportedMargin) return false
        if (info != other.info) return false

        return true
    }

    override fun hashCode(): Int {
        var result = winner
        result = 31 * result + loser
        result = 31 * result + reportedMargin.hashCode()
        result = 31 * result + info.hashCode()
        return result
    }

    companion object {
        fun makeWithVotes(contest: ContestIF, winner: Int, loser: Int, votes: Map<Int, Int>? = null): PluralityAssorter {
            val useVotes = votes ?: (contest as Contest).votes
            val winnerVotes = useVotes[winner] ?: 0
            val loserVotes = useVotes[loser] ?: 0
            val reportedMargin = (winnerVotes - loserVotes) / contest.Nc().toDouble()
            return PluralityAssorter(contest.info(), winner, loser, reportedMargin)
        }
    }
}

// TODO does this algorithm work for vote4N > 1 ?
//             assorter=Assorter(
//                contest=contest,
//                assort=lambda c, contest_id=contest.id: (
//                    CVR.as_vote(c.get_vote_for(contest.id, winner))
//                    / (2 * contest.share_to_win)
//                    if c.has_one_vote(contest.id, cands)
//                    else 1 / 2
//                ),
//                upper_bound=1 / (2 * contest.share_to_win),

/** See SHANGRLA, section 2.3, p.5. */
data class SuperMajorityAssorter(val info: ContestInfo, val winner: Int, val minFraction: Double, val reportedMargin: Double): AssorterIF {
    private val upperBound = 0.5 / minFraction // 1/2f  in (.5, Inf)

    init {
        require (minFraction > 0.0  && minFraction < 1.0)
    }

    // assort in {0, .5, u}, u > .5
    override fun assort(mvr: Cvr, usePhantoms: Boolean): Double {
        if (!mvr.hasContest(info.id)) return 0.5
        if (usePhantoms && mvr.phantom) return 0.0 // valid vote for every loser
        val w = mvr.hasMarkFor(info.id, winner)
        return if (mvr.hasOneVote(info.id, info.candidateIds)) (w / (2 * minFraction)) else .5
    }

    override fun upperBound() = upperBound
    override fun desc() = "SuperMajorityAssorter winner=$winner minFraction=$minFraction"
    override fun winner() = winner
    override fun loser() = -1 // everyone else is a loser
    override fun reportedMargin() = reportedMargin

    companion object {
        fun makeWithVotes(contest: ContestIF, winner: Int, minFraction: Double, votes: Map<Int, Int>?=null): SuperMajorityAssorter {
            val useVotes = votes ?: (contest as Contest).votes
            val winnerVotes = useVotes[winner] ?: 0
            val loserVotes = useVotes.filter { it.key != winner }.values.sum()
            val nuetralVotes = contest.Nc() - winnerVotes - loserVotes

            val weight = 1 / (2 * minFraction)
            val mean =  (winnerVotes * weight + nuetralVotes * 0.5) / contest.Nc().toDouble()
            val reportedMargin = mean2margin(mean)
            return SuperMajorityAssorter(contest.info(), winner, minFraction, reportedMargin)
        }
    }
}