package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.core.Cvr

abstract class RaireAssertion(val contest_name: String, val winner: String, val loser: String): Comparable<RaireAssertion> {
    /*
        Initializes a RAIRE assertion involving a comparison between
        the tallies of a candidate labelled 'winner' and a candidate
        labelled 'loser'. This assertion 'asserts' that the tally of
        the winner is larger than the tally of the loser in some context.

        Each assertion will have an estimated 'difficulty' related to
        the anticipated number of ballot checks required to audit it.

        Each assertion will have a margin defined as the difference in
        tallies ascribed to 'winner' and 'loser'
        */

    var votes_for_winner = 0
    var votes_for_loser = 0

    var margin = -1
    var difficulty = Double.POSITIVE_INFINITY

    val rules_out = mutableSetOf<RaireAssertion>()

    abstract fun is_vote_for_winner(cvr: Cvr): Int

    abstract fun is_vote_for_loser(cvr: Cvr): Int

    abstract fun subsumes(other: NENAssertion): Boolean

    abstract fun same_as(other: RaireAssertion): Boolean

    /*  Assertions are ordered in terms of how many alternate outcomes that they are able to rule out.
    fun __lt__(other: RaireAssertion) {
        val self_rule_out = if (!this.rules_out) -1 else min([len(ro) for ro in this.rules_out])

        other_rule_out = -1 if not other . rules_out else
        min([len(ro) for ro in other.rules_out])

        return self_rule_out < other_rule_out
    }
    fun __gt__(other: RaireAssertion) {
        self_rule_out = -1 if not self . rules_out else
        min([len(ro) for ro in this.rules_out])

        other_rule_out = -1 if not other . rules_out else
        min([len(ro) for ro in other.rules_out])

        return self_rule_out > other_rule_out
    }
     */
    // I think that __lt__ and __gt__ can be replaced by compareTo. This allows us to create sorted lists, and thus a "rank"
    override fun compareTo(other: RaireAssertion): Int {
        //         val self_rule_out = if (!this.rules_out) -1 else min([len(ro) for ro in this.rules_out])
        val self_rule_out = if (this.rules_out.isEmpty()) -1 else 0 // this.rules_out.map { it.size }.min() TODO
        val other_rule_out = if (other.rules_out.isEmpty()) -1 else 0 // other.rules_out.map { it.size }.min()

        return self_rule_out - other_rule_out
    }


    open fun to_str(): String {
        return "TODO"
    }
}

class NEBAssertion(contest_name: String, winner: String, loser: String) : RaireAssertion(contest_name, winner, loser) {

    /*
    A Not-Eliminated-Before (NEB) assertion between a candidate 'winner' and
    a candidate 'loser' compares the minimum possible tally 'winner' could
    have (their first preference tally) with the maximum possible tally
    candidate 'loser' could have while 'winner' is still standing.

    We give 'winner' only those votes that rank 'winner' first.

    We give 'loser' ALL votes in which 'loser' appears in the ranking and
    'winner' does not, or 'loser' is ranked higher than 'winner'.

    This assertion "asserts" that the tally of 'winner' is larger than the
    tally of the 'loser'. This means that 'winner' could never be eliminated
    prior to 'loser'.
    */

    override fun is_vote_for_winner(cvr: Cvr): Int {
        if (!cvr.hasContest(this.contest_name)) return 0

        return if (ranking(this.winner, cvr.votes[this.contest_name]!!) == 0) 1 else 0
    }

    override fun is_vote_for_loser(cvr: Cvr): Int {
        if (!cvr.hasContest(this.contest_name)) return 0

        val w_idx = ranking(this.winner, cvr.votes[this.contest_name]!!)
        val l_idx = ranking(this.loser, cvr.votes[this.contest_name]!!)

        // return 1 if l_idx != -1 and (w_idx == -1 or (w_idx != -1 and l_idx < w_idx)) else 0
        return if (l_idx != -1 && (w_idx == -1 || (w_idx != -1 && l_idx < w_idx))) 1 else 0
    }

    override fun same_as(other: RaireAssertion): Boolean {
        return this.contest_name == other.contest_name && this.winner == other.winner
                && this.loser == other.loser
    }

    override fun subsumes(other: NENAssertion): Boolean {
        /*
        An NEBAssertion 'A' subsumes an assertion 'other' if:
        - 'other' is not an NEBAssertion
        - Both assertions have the same winner & loser
        - 'other' rules out an outcome with the tail 'Tail' and either the
            winner of this NEBAssertion assertion appears before the loser in
            'Tail' or the loser appears and the winner does not.
            */

        //         if type(other) == NEBAssertion:
        //            return False
        //
        //        if self.winner == other.winner and self.loser == other.loser:
        //            return True
        //
        //        if self.winner == other.winner and not(self.loser in \
        //            other.eliminated):
        //            return True
        //
        //        elif self.winner in other.eliminated and not(self.loser in \
        //            other.eliminated):
        //            return True
        //
        //        else:
        //            # For all outcomes that 'other' is ruling out, this NEB rules them all out.
        //            for ro in other.rules_out:
        //                idxw = -1 if not self.winner in ro else ro.index(self.winner)
        //                idxl = -1 if not self.loser in ro else ro.index(self.loser)
        //
        //                if idxw == idxl or (idxl < idxw):
        //                    return False
        //
        //            return True
        //
        //        return False

        if (this.winner == other.winner && this.loser == other.loser) return true

        if (this.winner == other.winner && !(this.loser in other.eliminated)) {
            return true

        } else if (this.winner in other.eliminated && this.loser !in other.eliminated) {
            return true

        } else {
            // TODO this seems to treat Assertion as a collection, not sure wtf
            /*  For all outcomes that 'other' is ruling out, this NEB rules them all out.
            for (ro: RaireAssertion in other.rules_out) {
                val idxw: Int = if (ro.contains(this.winner)) -1 else ro.indexOf(this.winner)
                val idxl: Int = if (ro.contains(this.loser)) -1 else ro.indexOf(this.loser)

                if (idxw == idxl || (idxl < idxw)) {
                    return false
                }
            } */
            return true
        }
        return false
    }

    override fun to_str(): String {
        return "NEB Winner ${this.winner},Loser ${this.loser},diff est ${this.difficulty}"
    }
}

// Returns true if listb = some_list + lista
fun is_suffix(lista: List<Any>, listb: List<Any>): Boolean {
    val len_lista = lista.size
    val len_listb = listb.size

    if (len_listb < len_lista) return false

    val ss = listb.subList(len_listb - len_lista, listb.size)
    return ss == lista
}


class NENAssertion(contest_name: String, winner: String, loser: String, val eliminated: List<String>) :
    RaireAssertion(contest_name, winner, loser) {
    /*
    A Not-Eliminated-Next (NEN) assertion between a candidate 'winner' and
    a candidate 'loser' compares the tally of the two candidates in the
    context where a given set of candidates have been eliminated.

    We give 'winner' all votes in which they are preferenced first AFTER
    the candidates in 'eliminated' are removed from the ranking.

    We give 'loser' all votes in which they are preferenced first AFTER
    the candidates in 'eliminated' are removed from the ranking.

    This assertion "asserts" that the tally of the 'winner' in this context,
    where the specified candidates have been eliminated, is larger than that
    of 'loser'.
    */

    override fun is_vote_for_winner(cvr: Cvr): Int {
        if (!cvr.hasContest(this.contest_name)) return 0
        return vote_for_cand(this.winner, this.eliminated, cvr.votes[this.contest_name]!!)
    }

    override fun is_vote_for_loser(cvr: Cvr): Int {
        if (!cvr.hasContest(this.contest_name)) return 0
        return vote_for_cand(this.loser, this.eliminated, cvr.votes[this.contest_name]!!)
    }

    override fun same_as(other: RaireAssertion): Boolean {
        if (other !is NENAssertion) return false
        return this.contest_name == other.contest_name
                && this.winner == other.winner
                && this.loser == other.loser
                && this.eliminated == other.eliminated
    }

    override fun subsumes(other: NENAssertion): Boolean {
        /*
        An NENAssertion 'A' subsumes an assertion 'other' if 'other' is
        not an NEBAssertion, the outcomes that 'A' rules out are suffixes of
        the outcomes that 'B' rules out.
        */

        //         if type(other) == NEBAssertion:
        //            return False
        //
        //        other_ro = set(other.rules_out)
        //
        //        for ro in self.rules_out:
        //            other_ro = [o for o in other_ro if not(is_suffix(ro, o))]
        //
        //        return other_ro == []

        // TODO this seems to treat Assertion as a collection, not sure wtf
        //   need some tests to compare against python
        val other_ro = mutableListOf(other.rules_out)

        // for ro in this.rules_out: other_ro = [o for o in other_ro if not(is_suffix(ro, o))]
        /*
        this.rules_out.forEach { ro ->
            other_ro.forEach { oro ->
                if (!is_suffix(ro, oro)) other_ro.add(oro)
            }
        }

         */

        return other_ro.isEmpty()
    }

    /*
        override fun to_str() = buildString {
            append( "NEN Winner= ${winner} Loser= ${loser} Eliminated= ")

            for (cand in this.eliminated:
            result += ",{}".format(cand)

            result += ",diff est {}, rules out: {}".format(this.difficulty, \
            this.rules_out)
            return result
        }

     */
}
