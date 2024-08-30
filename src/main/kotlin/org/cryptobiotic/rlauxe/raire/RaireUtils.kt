package org.cryptobiotic.rlauxe.raire

import kotlin.math.max

fun ranking(cand: String, ballot: Map<String, Int>): Int {
    /*
    Input:
    cand           -   identifier for candidate
    ballot         -   mapping between candidate name and their position in the ranking for a relevant contest on a given ballot.

    Output:
    Returns the position of candidate 'cand' in the ranking of the
    given ballot 'ballot'. Returns -1 if 'cand' is not preferenced on the ballot.
    */
    return ballot[cand] ?: -1
}

fun vote_for_cand(cand: String, eliminated: List<String>, ballot: Map<String, Int>): Int {
    /*
    Input:
    cand - identifier for candidate
    eliminated : list-identifiers of eliminated candidates
    ballot - mapping between candidate name and their
            position in the ranking for a relevant contest on a given ballot .
    Output:
    Returns 1 if the given 'ballot' is a vote for the given candidate 'cand'
    in the context where candidates in 'eliminated' have been eliminated.
    Otherwise, return 0 as the 'ballot' is not a vote for 'cand'.
    */

//  If 'cand' is not in the set of candidates assumed still standing,
//  'cand' does not get this vote.
    if (cand in eliminated) return 0

//  If 'cand' does not appear on the ballot, they do not get this vote.
    val c_idx = ranking(cand, ballot)
    if (c_idx == -1) return 0

    for ((alt_c, a_idx) in ballot) {
        if (alt_c == cand) continue
        if (alt_c in eliminated) continue

        if (a_idx < c_idx) return 0
    }

    return 1
}

class RaireNode(val tail: List<String>) {
    //  Tail of an "imagined" elimination sequence representing the
    //  outcome of an IRV election. The last candidate in the tail is
    //  the "imagined" winner of the election.

    //  Lowest cost assertion that, if true, can rule out any election
    //  outcome that *ends* with the given tail.
    var best_assertion: RaireAssertion? = null

    //  An "ancestor" of this node is a node whose tail equals the latter
    //  part of val tail (i.e., if this.tail is ["A", "B", "C"], the node
    //  will have an ancestor with tail ["B", "C"].
    var best_ancestor: RaireNode? = null

    //  If there are candidates not mentioned in this.tail, this node is not a leaf and it can be expanded.
    var expandable = true

    //  Estimate of difficulty of ruling out the outcome this node represents.
    var estimate = Double.NaN

    //  Record of the children of this node that have already been
    //  considered (for example, through diving). These children are
    //  represented by the candidate that was added to the front of
    //  val tail when the child was created.
    var explored = mutableListOf<String>()

    //  Flag to indicate if node was created as part of a dive.
    var dive_node = false

    fun is_descendent_of(node: RaireNode): Boolean {
        /*
        Determines if the given 'node' is an ancestor of this node in a
        tree of possible election outcomes. A node with a tail equal to
        [a,b,c,d] has ancestors with tails [b,c,d], [c,d], and [d].

        Input:
        node: RaireNode     -  Potential ancestor

        Output:
        Returns true  if the input 'node' is an ancestor of this node, and
        false  otherwise.
        */
        val l1 = this.tail.size
        val l2 = node.tail.size

        if (l1 <= l2) return false
        return (this.tail.subList(l1 - l2, l1) == node.tail)
    }

    /*
    fun display(self, stream = sys.stdout) {
        print("{} | ".format(this.tail[0]), file = stream, end = '')

        for i in range(1, len(this.tail)):
        print("{} ".format(this.tail[i]), file = stream, end = '')

        print("[{}]".format(this.estimate), file = stream, end = '')

        if this.best_ancestor != null :
        print(
            " (Best Ancestor {} | ".format(this.best_ancestor.tail[0]),
            file = stream, end = ''
        )

        for i in range(1, len(this.best_ancestor.tail)):
        print(
            "{} ".format(this.best_ancestor.tail[i]), file = stream,
            end = ''
        )
        print(
            "[{}])".format(this.best_ancestor.estimate), file = stream,
            end = ''
        )

        print("")
    }

     */
}


class RaireFrontier {
    val nodes = mutableListOf<RaireNode>()

    fun replace_descendents(parent: RaireNode, log: Boolean) {
        /*
        Remove all descendents of the input 'node' from the frontier, and
        insert 'node' to the frontier in the appropriate position.

        If 'log' is true, print logging statements to given 'stream'.
        */
        val descendents = mutableListOf<Int>()

        /*
        if (log) {
            print("Replacing descendents of ", file = stream, end = '')
            node.display(stream = stream)
        }
         */

        this.nodes.forEachIndexed { index, node ->
            //  Is node a descendent of the given node?
            if (node.is_descendent_of(parent)) {
                descendents.add(index)
            }
        }

        descendents.reverse()
        for (i in descendents) {
            this.nodes.removeAt(i)
        }

        this.insert_node(parent)
    }


    fun insert_node(node: RaireNode) {
        /*
        Insert given node into the frontier in the right position. Nodes
        that are not associated with an "invalidating" assertion are placed
        at the front of the frontier. After these nodes, nodes in frontier
        are ordered from most difficult to invalidate to easiest to
        invalidate. Leaf nodes -- nodes whose "tail" contains all candidates
        -- are placed at the end of the frontier.

        Input:
        node: RaireNode   - node, representing an alternate election outcome, to add to the frontier.
        */
        if (!node.expandable) {
            this.nodes.add(node)
        } else if (node.estimate == Double.POSITIVE_INFINITY) {
            this.nodes.add(0, node)
        } else {
            var i = 0
            while (i < this.nodes.size) {
                val n_est = this.nodes[i].estimate
                if (n_est <= node.estimate) break
                i += 1
            }
            this.nodes.add(i, node)
        }
    }
}

fun find_best_audit(
    raireContest: RaireContest,
    ballots: List<Map<String, Int>>,
    neb_matrix: Map<String, MutableMap<String, NEBAssertion?>>,
    node: RaireNode,
    asn_func: EstimatorFn
) {
    /*
    Input:
    node: RaireNode    -  A node in the tree of alternate election outcomes.
                          The node represents an election outcome that ends in the sequence node.tail.

    contest: Contest   -  Contest being audited.

    ballots            - this is the list of cvrs.votes for this contest, candidateID -> rank
                        "Details of reported ballots for this contest"

    neb_matrix         -  |Candidates| x |Candidates| dictionary where
                        neb_matrix[c1][c2] returns a NEBAssertion stating
                        that c1 cannot be eliminated before c2 (if one
                        exists) and null  otherwise.

    asn_func: Callable -  Function that takes an assertion margin and
        returns an estimate of how "difficult" it will
        be to audit that assertion.

    Output:
    Finds the least cost assertion that can be used to rule out all election
    outcomes that end with the sequence node.tail, and assigns that assertion
    to node.best_assertion. If no such assertion can be found, node.assertion
    will equal null  after this function is called.
    */

    val ntail = node.tail.size
    val first_in_tail = node.tail[0]

    var best_asrtn: RaireAssertion? = null

    //  We first consider if we can invalidate this outcome by showing that
    //  'first_in_tail' can not-be-eliminated-before a candidate that
    //  appears later in tail.
    for (later_cand in node.tail.subList(1, ntail)) {
        //  Can we show that the candidate 'later_cand' must come before
        //  candidate 'first_in_tail' in the elimination sequence?
        val neb = neb_matrix[first_in_tail]!![later_cand]
        if (neb != null && (best_asrtn == null || neb.difficulty < best_asrtn.difficulty)) best_asrtn = neb
    }

    //  'eliminated' is the list of candidates that are not mentioned in 'tail'.
    //   eliminated = [c for c in contest.candidates if not c in node . tail]
    val eliminated = raireContest.candidates.filter { !node.tail.contains(it) }

    //  We now look at whether there is a candidate not mentioned in
    //  'tail' (this means they are assumed to be eliminated at some prior
    //  point in the elimination sequence), that can not-be-eliminated-before
    //  'first_in_tail'.
    for (cand in eliminated) {
        for (cand_in_tail in node.tail) {
            val neb = neb_matrix[cand]!![cand_in_tail]
            if (neb != null && (best_asrtn == null || neb.difficulty < best_asrtn.difficulty)) best_asrtn = neb
        }
    }

    //  We now consider whether we can find a better NEN assertion. We
    //  want to show that at the point where all the candidates in 'tail'
    //  remain, 'first_in_tail' is not the candidate with the least number
    //  of votes. This means that 'first_in_tail' should not be eliminated next.

    //  Tally of the candidate 'first_in_tail'
    // val tally_first_in_tail = sum([vote_for_cand(first_in_tail, eliminated, blt) for blt in ballots])
    val tally_first_in_tail = ballots.map { vote_for_cand(first_in_tail, eliminated, it) }.sum()

    for (later_cand in node.tail.subList(1, node.tail.size)) {
        // tally_later_cand = sum([vote_for_cand(later_cand, eliminated, blt) for blt in ballots])
        val tally_later_cand = ballots.map { vote_for_cand(later_cand, eliminated, it) }.sum()

        if (tally_first_in_tail > tally_later_cand) {
            //  We can create a NEN assertion that says "first_in_cand"
            //  should not be eliminated next, after "eliminated" are
            //  eliminated, because "later_cand" actually has less votes at this point.
            val estimate = asn_func(
                tally_first_in_tail,
                tally_later_cand,
                raireContest.tot_ballots - (tally_first_in_tail + tally_later_cand),
                raireContest.tot_ballots
            )

            if (best_asrtn == null || estimate < best_asrtn.difficulty) {
                val nen = NENAssertion(raireContest.name, first_in_tail, later_cand, eliminated)

                // AFAICT, this is the only place that rules_out is added to?
                // rules_out has assewrtions, node.tail has Strings
                // nen.rules_out.add(tuple(node.tail))
                // nen.rules_out.addAll(node.tail) TODO
                nen.difficulty = estimate

                nen.votes_for_winner = tally_first_in_tail
                nen.votes_for_loser = tally_later_cand

                best_asrtn = nen
            }
        }
    }

    node.best_assertion = best_asrtn

    if (best_asrtn != null) {
        node.estimate = best_asrtn.difficulty
    }
}

data class ManageNodeReturn(val audit_not_possible: Boolean, val lowerBound: Double, val terminus: Boolean)

fun manage_node(newn: RaireNode, frontier: RaireFrontier, lowerbound: Double, log: Boolean): ManageNodeReturn {

    /*
    Input:

    newn: RaireNode    -  A node in the tree of alternate election outcomes that
    has just been created and evaluated, but not yet
    added to our frontier. We need to determine what this
    node's evaluation means for our frontier.

    frontier           -  Current frontier of our set of alternate outcome
    trees.

    lowerbound         -  Current lower bound on audit difficulty.

    log                -  Flag indicating if logging statements should
    be printed during the algorithm.

    stream             -  Stream to which logging statements should
    be printed.


    Output:

    Returns a triple:
    audit_not_possible (Boolean), new lower bound, terminus (Boolean)

    The first element of this triple is a boolean indicating whether or not
    we have established that the audit is not possible. If so, this boolean
    will be true , otherwise it will be false .

    The second element indicates the new lower bound on audit difficulty
    as a result of the node's evaluation (note it may not have changed from
    the prior lower bound).

    The third element indicates whether or not we will need to continue
    exploring children of this node. The boolean 'terminus' will be set to
    true  if we do not need to continue to explore children of this node, and
    false  otherwise.

    */

    //     if not newn.expandable:
    //        # 'newn' is a leaf.
    //        if newn.estimate == np.inf and newn.best_ancestor.estimate == np.inf:
    //
    //            if log:
    //                print("Found branch that cannot be pruned.", file=stream)
    //
    //            return True, np.inf, True
    //
    //        if newn.best_ancestor.estimate <= newn.estimate:
    //            next_lowerbound = max(lowerbound, newn.best_ancestor.estimate)
    //            frontier.replace_descendents(newn.best_ancestor,log,stream=stream)
    //
    //            return False, next_lowerbound, True
    //
    //        else:
    //            next_lowerbound = max(lowerbound, newn.estimate)
    //            frontier.insert_node(newn)
    //
    //            if log:
    //                print("    Best audit ", file=stream, end='')
    //                newn.best_assertion.display(stream=stream)
    //
    //            return False, next_lowerbound, True
    //    else:
    //        frontier.insert_node(newn)
    //
    //        if log:
    //            if newn.best_assertion != None:
    //                print("    Best audit ", file=stream, end='')
    //                newn.best_assertion.display(stream=stream)
    //            else:
    //                print("    Cannot be disproved", file=stream)
    //
    //        return False, lowerbound, False

    if (!newn.expandable) {
        //  'newn' is a leaf.
        if (newn.estimate == Double.POSITIVE_INFINITY && newn.best_ancestor!!.estimate == Double.POSITIVE_INFINITY) {
            if (log) println("Found branch that cannot be pruned.")
            return ManageNodeReturn(true, Double.POSITIVE_INFINITY, true)
        }

        if (newn.best_ancestor!!.estimate <= newn.estimate) {
            val next_lowerbound = max(lowerbound, newn.best_ancestor!!.estimate)
            frontier.replace_descendents(newn.best_ancestor!!, log)
            return ManageNodeReturn(false, next_lowerbound, true)
        } else {
            val next_lowerbound = max(lowerbound, newn.estimate)
            frontier.insert_node(newn)
            if (log) {
                print("    Best audit ")
                //newn.best_assertion.display(stream = stream)
            }
            return ManageNodeReturn(false, next_lowerbound, true)
        }

    } else {
        frontier.insert_node(newn)

        if (log) {
            if (newn.best_assertion != null) println("    Best audit ") else println("    Cannot be disproved")
        }

        return ManageNodeReturn(false, lowerbound, false)
    }
}

fun perform_dive(
    node: RaireNode,
    raireContest: RaireContest,
    ballots: List<Map<String, Int>>,
    neb_matrix: Map<String, MutableMap<String, NEBAssertion?>>,
    asn_func: EstimatorFn,
    lower_bound: Double,
    frontier: RaireFrontier,
    log: Boolean
): Double {

    /*
    Input:
    node: RaireNode    -  A node in the tree of alternate election outcomes.
    Starting point of dive to a leaf.

    contest: Contest   -  Contest being audited.

    ballots:           -  Details of reported ballots for this contest.

    neb_matrix         -  |Candidates| x |Candidates| dictionary where
    neb_matrix[c1][c2] returns a NEBAssertion stating
    that c1 cannot be eliminated before c2 (if one
    exists) and null  otherwise.

    asn_func: Callable -  Function that takes an assertion margin and
    returns an estimate of how "difficult" it will
    be to audit that assertion.

    lower_bound        -  Current lower bound on audit difficulty.

    frontier           -  Current frontier of our set of alternate outcome
    trees.

    log                -  Flag indicating if logging statements should
    be printed during the algorithm.


    Output:
    Returns the difficulty estimate of the least-difficult-to-audit
    assertion that can be used to rule out at least one of the branches
    starting at the input 'node'. As this function dives from the given 'node'
    it will add nodes to the current frontier of our set of alternate outcome
    trees.
    */

//    ncands = len(contest.candidates)
//
//    rem_cands = [c for c in contest.candidates if not c in node.tail]
//
//    # sort rem_cands by position in contest.order if it is defined
//    next_cand = rem_cands[0]
//    if contest.outcome != []:
//        npos = contest.outcome.index(next_cand)
//
//        for i in range(1, len(rem_cands)):
//            c = rem_cands[i]
//            ipos = contest.outcome.index(c)
//
//            if ipos > npos:
//                next_cand = c
//                npos = ipos
//
//    newn = RaireNode([next_cand] + node.tail)
//    newn.expandable = False if len(newn.tail) == ncands else True
//    newn.dive_node = True
//
//    node.explored.append(next_cand)
//
//    # Assign a 'best ancestor' to the new node.
//    newn.best_ancestor = node.best_ancestor if \
//        node.best_ancestor != None and node.best_ancestor.estimate <= \
//        node.estimate else node
//
//    find_best_audit(contest, ballots, neb_matrix, newn, asn_func)
//
//    if log:
//        print("DIVE TESTED ", file=stream, end='')
//        newn.display(stream=stream)
//
//    audit_not_possible, next_lowerbound, dive_complete = manage_node(newn, \
//        frontier, lower_bound, log, stream=stream)
//
//    if audit_not_possible:
//        return np.inf
//
//    if dive_complete:
//        return next_lowerbound
//
//    return perform_dive(newn, contest, ballots, neb_matrix, asn_func, \
//            next_lowerbound, frontier, log, stream=stream)
    val ncands = raireContest.candidates.size

    // rem_cands = [c for c in contest.candidates if not c in node.tail]
    val rem_cands = raireContest.candidates.filter { !node.tail.contains(it) }

    //  sort rem_cands by position in contest.order if it is defined
    var next_cand = rem_cands[0] // TODO empty ?
    /* if (contest.outcome.isNotEmpty()) {
        var npos = contest.outcome.index(next_cand)

        for (i in 1..rem_cands.size) { // TODO check
            val c = rem_cands[i]
            val ipos = contest.outcome.indexOf(c)

            if (ipos > npos) {
                next_cand = c
                npos = ipos
            }
        }
    } */ // TODO

    val newn = RaireNode(listOf(next_cand) + node.tail)
    newn.expandable = (newn.tail.size != ncands)
    newn.dive_node = true

    node.explored.add(next_cand)

    //  Assign a 'best ancestor' to the new node.
    newn.best_ancestor = if (node.best_ancestor != null && node.best_ancestor!!.estimate <= node.estimate) node.best_ancestor else node

    find_best_audit(raireContest, ballots, neb_matrix, newn, asn_func)

    if (log) {
        println("DIVE TESTED ")
        //newn.display(stream = stream)
    }

    val (audit_not_possible, next_lowerbound, dive_complete) = manage_node(newn, frontier, lower_bound, log)

    if (audit_not_possible) {
        return Double.POSITIVE_INFINITY
    }

    if (dive_complete) {
        return next_lowerbound
    }

    return perform_dive(
        newn, raireContest, ballots, neb_matrix, asn_func,
        next_lowerbound, frontier, log,
    )
}
