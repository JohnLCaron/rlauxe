
## Instant Runoff Voting (IRV)

Also known as Ranked Choice Voting, this allows voters to rank their choices by preference.
In each round, the candidate with the fewest first-preferences (among the remaining candidates) is eliminated.
This continues until only one candidate is left. Only 1 winner is allowed.

In principle one could use polling audits for IRV, but the information
needed to create the Raire Assertions all but necessitates CVRs.
So currently we only support IRV with CLCA and OneAudit.

We use the [RAIRE java library](https://github.com/DemocracyDevelopers/raire-java) to generate assertions that fit into the SHANGRLA framework.
We convert the output of the raire library into RaireAssorters, which assigns the assort values. The ClcaAssorter then can be used with
RaireAssorter transparently.

The RaireAssorters function `A_wℓ(bi)` for winner w and loser ℓ operating on the ith ballot bi is

````
if (usePhantoms && mvr.isPhantom) return 0.5

for winner_only assertions:
        val awinner = if (raire_get_rank(rcvr, contestId, rassertion.winnerId) == 1) 1 else 0
        // CVR is a vote for the loser if they appear and the winner does not, or they appear before the winner
        val aloser = raire_loser_vote_wo( rcvr, contestId, rassertion.winnerId, rassertion.loserId)
        return (awinner - aloser + 1) * 0.5 // affine transform from (-1, 1) -> (0, 1)
        
for irv_elimination assertions:    
        // Context is that all candidates in "already_eliminated" have been eliminated and their votes distributed to later preferences
        val awinner = raire_votefor_elim(rcvr, contestId, rassertion.winnerId, remaining)
        val aloser = raire_votefor_elim(rcvr, contestId, rassertion.loserId, remaining)
        return (awinner - aloser + 1) * 0.5 // affine transform from (-1, 1) -> (0, 1)
            
// if candidate not ranked, return 0, else rank (1 based)
fun raire_get_rank(cvr: Cvr, contest: Int, candidate: Int): Int {
    val rankedChoices = cvr.votes[contest]
    return if (rankedChoices == null || !rankedChoices.contains(candidate)) 0
    else rankedChoices.indexOf(candidate) + 1
}

// Check whether vote is a vote for the loser with respect to a 'winner only' assertion.
// Its a vote for the loser if they appear and the winner does not, or they appear before the winner
// return 1 if the given vote is a vote for 'loser' and 0 otherwise
fun raire_loser_vote_wo(cvr: Cvr, contest: Int, winner: Int, loser: Int): Int {
    val rank_winner = raire_get_rank(cvr, contest, winner)
    val rank_loser = raire_get_rank(cvr, contest, loser)

    return when {
        rank_winner == 0 && rank_loser != 0 -> 1
        rank_winner != 0 && rank_loser != 0 && rank_loser < rank_winner -> 1
        else -> 0
    }
}

/**
 * Check whether 'vote' is a vote for the given candidate in the context where only candidates in 'remaining' remain standing.
 * If you reduce the ballot down to only those candidates in 'remaining', and 'cand' is the first preference, return 1; otherwise return 0.
 * @param cand identifier for candidate
 * @param remaining list of identifiers of candidates still standing
 * @return 1 if the given vote for the contest counts as a vote for 'cand' and 0 otherwise.
 */
fun raire_votefor_elim(cvr: Cvr, contest: Int, cand: Int, remaining: List<Int>): Int {
    if (cand !in remaining) return 0
    
    val rank_cand = raire_get_rank(cvr, contest, cand)
    if (rank_cand == 0) return 0

    for (altc in remaining) {
        if (altc == cand) continue
        val rank_altc = raire_get_rank(cvr, contest, altc)
        if (rank_altc != 0 && rank_altc <= rank_cand) return 0
    }
    return 1
}

````
The upper bound is 1.

## Democracy Developers Notes

A Guide to Risk Limiting Audits with RAIRE
Part 1: Auditing IRV Elections with RAIRE

Not Eliminated Before (NEB) Assertions
Alice NEB Bob is an assertion saying that Alice cannot be eliminated before Bob, irrespective of which
other candidates are continuing. In other words, no outcome is possible in which Alice is eliminated
before Bob. When expressed as a comparison of tallies, this assertion says that the smallest number of
votes Alice can have, at any point in counting, is greater than the largest number of votes Bob can ever
have while Alice is continuing. Alice’s smallest tally is equal to her first preference count – the number of
ballots on which she is ranked first. The largest number of votes Bob can have while Alice is continuing
is the number of ballots on which he is ranked higher than Alice, or he is ranked and Alice is not.

    // aka NEB
    fun assortWinnerOnly(rcvr: CvrIF): Double {
        // CVR is a vote for the winner only if it has the winner as its first preference (rank == 1)
        val awinner = if (raire_get_rank(rcvr, contestId, rassertion.winnerId) == 1) 1 else 0
        // CVR is a vote for the loser if they appear and the winner does not, or they appear before the winner
        val aloser = raire_loser_vote_wo( rcvr, contestId, rassertion.winnerId, rassertion.loserId)
        return (awinner - aloser + 1) * 0.5 // affine transform from (-1, 1) -> (0, 1)
    }

Not Eliminated Next (NEN) Assertions
NEN assertions compare the tallies of two candidates under the assumption that a specific set of can-
didates have been eliminated. An instance of this kind of assertion could look like this: NEN: Alice >
Bob if only {Alice, Bob, Diego} remain. This means that in the context where Chuan has been eliminated,
Alice cannot be eliminated next, because Bob has a lower tally. When expressed as a comparison of
tallies, this assertion says that the number of ballots in Alice’s tally pile, in the context where only Alice,
Bob and Diego are continuing, is greater than the number of ballots in Bob’s tally pile in this context.
This example assumes one eliminated candidate – Chuan – however, NEN assertions can be constructed
with contexts involving no eliminated candidates, or more than one eliminated candidate. The assertion
NEN: Alice > Chuan if only {Alice,Bob,Chuan,Diego} remain says that Alice cannot be the first eliminated
candidate, as she has more votes than Chuan when no candidates have yet been eliminated. The as-
sertion NEN: Diego > Bob if only {Bob,Diego} remain says that Diego has more votes than Bob in the
context where those two are the only continuing candidates.

    // aka NEN
    fun assortIrvElimination(rcvr: CvrIF): Double {
        // Context is that all candidates in "already_eliminated" have been
        // eliminated and their votes distributed to later preferences
        val awinner = raire_votefor_elim(rcvr, contestId, rassertion.winnerId, remaining)
        val aloser = raire_votefor_elim(rcvr, contestId, rassertion.loserId, remaining)
        return (awinner - aloser + 1) * 0.5 // affine transform from (-1, 1) -> (0, 1)
    }