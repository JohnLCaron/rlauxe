package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.core.Cvr

data class RaireCvrs(
    val contests: List<RaireCvrContest>,
    val cvrs: List<Cvr>,
    val filename: String,
)

data class RaireCvrContest(
    val contestNumber: Int,
    val candidates: List<Int>,
    val ncvrs: Int,
    val winner: Int = -1,
) {
    fun show() = buildString {
        appendLine(" ncvrs=${ncvrs} RaireCvrContest $contestNumber candidates=$candidates} winner=$winner")
    }
}

// TODO relation to the original CVRs ??
// "RaireCvr is always for one contest" probably an artifact of raire processing
// probably doesnt have to be seperate class, exept for method override hasMarkFor / hasOneVote ?
/** Duplicating the math from SHANGRLA CVR in Audit.py */
class RaireCvr(val cvr: Cvr) {

    //     def get_vote_for(self, contest_id: str, candidate: str):
    //        return (
    //            False
    //            if (contest_id not in self.votes or candidate not in self.votes[contest_id])
    //            else self.votes[contest_id][candidate]
    //        )

    /** if candidate not ranked, 0, else rank (1 based) */
    fun get_vote_for(contest: Int, candidate: Int): Int {
        val rankedChoices = cvr.votes[contest]
        return if (rankedChoices == null || !rankedChoices.contains(candidate)) 0
               else rankedChoices.indexOf(candidate) + 1
    }

    //        rank_winner = self.get_vote_for(contest_id, winner)
    //        rank_loser = self.get_vote_for(contest_id, loser)
    //
    //        if not bool(rank_winner) and bool(rank_loser):
    //            return 1
    //        elif bool(rank_winner) and bool(rank_loser) and rank_loser < rank_winner:
    //            return 1
    //        else:
    //            return 0
    /**
     * Check whether vote is a vote for the loser with respect to a 'winner only' assertion.
     * Its a vote for the loser if they appear and the winner does not, or they appear before the winner
     *
     * @param winner identifier for winning candidate
     * @param loser identifier for losing candidate
     * @return 1 if the given vote is a vote for 'loser' and 0 otherwise
     */
    fun rcv_lfunc_wo(contest: Int, winner: Int, loser: Int): Int {
        val rank_winner = get_vote_for(contest, winner)
        val rank_loser = get_vote_for(contest, loser)

        return when {
            rank_winner == 0 && rank_loser != 0 -> 1
            rank_winner != 0 && rank_loser != 0 && rank_loser < rank_winner -> 1
            else -> 0
        }
    }

    //         if not cand in remaining:
    //            return 0
    //
    //        if not bool(rank_cand := self.get_vote_for(contest_id, cand)):
    //            return 0
    //        else:
    //            for altc in remaining:
    //                if altc == cand:
    //                    continue
    //                rank_altc = self.get_vote_for(contest_id, altc)
    //                if bool(rank_altc) and rank_altc <= rank_cand:
    //                    return 0
    //            return 1
    /**
     * Check whether 'vote' is a vote for the given candidate in the context
     * where only candidates in 'remaining' remain standing.
     *
     * @param cand identifier for candidate
     * @param remaining list of identifiers of candidates still standing
     * @return 1 if the given vote for the contest counts as a vote for 'cand' and 0 otherwise.
     * Essentially, if you reduce the ballot down to only those candidates in 'remaining',
     * and 'cand' is the first preference, return 1; otherwise return 0.
     */
    fun rcv_votefor_cand(contest: Int, cand: Int, remaining: List<Int>): Int {
        if (cand !in remaining) {
            return 0
        }

        val rank_cand = get_vote_for(contest, cand)
        if (rank_cand == 0) return 0

        for (altc in remaining) {
            if (altc == cand) continue

            val rank_altc = get_vote_for(contest, altc)
            if (rank_altc != 0 && rank_altc <= rank_cand) {
                return 0
            }
        }
        return 1
    }
}