package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.core.Assertion
import org.cryptobiotic.rlauxe.core.Cvr
import kotlin.math.max

typealias EstimatorFn = (winner: Int, loser: Int, other: Int, total: Int) -> Double // estimate of difficulty

fun compute_raire_assertions(
    raireContest: RaireContest,
    cvrs: List<Cvr>,
    winner: String,
    asn_func: EstimatorFn,
    log: Boolean,
    agap: Int = 0,
    seed: Int = 123456
): List<Assertion> {

    /*
    Inputs:
        contest        - the contest being audited (Contest structure)

        // TODO
        cvrs           - mapping of ballot_id to votes:
                {
                    'ballot_id': {
                        'contest': {
                            'candidate1': 1,
                            'candidate2': 0,
                            'candidate3': 2,
                            'candidate4': 3,
                            ...
                        }
                    ...
                }

        winner         - reported winner of the contest

        // TODO takes 4: (winner, loser, other, total) -> estimate of difficulty
        asn_func       - function that takes three values as input: tally for 
                         the winner of an assertion; the loser; and the total 
                         number of auditable ballots. Returns an estimate of 
                         how difficult a RAIRE assertion with that margin will
                         be to audit.

        log            - flag indicating if logging statements should
                         be printed during the algorithm.

        stream         - stream to which logging statements should
                         be printed.
        
        agap           - allowed gap between the lower and upper bound
                         on expected audit difficulty. Once these bounds
                         converge (to within 'agap') algorithm can stop
                         and return  audit configuration found. Generally,
                         keep this at 0 unless the algorithm is not 
                         terminating in a reasonable time. Then set it to
                         as small a value as possible, and increase, until
                         the algorithm terminates. For some instances, the
                         difference between the lower and upper bound on 
                         expected audit difficulty gets to a point where it
                         is quite small, but doesn't converge. 

    Outputs:
        A list of RaireAssertions to be audited. If this collection of
        assertions is found to hold, then all alternate outcomes, in which
        an alternate candidate to 'winner' wins, can be ruled out. 
   */

    val ncands = raireContest.candidates.size

// First look at all of the NEB assertions that could be formed for
// this contest. We will refer to this matrix when examining the best
// way to prune branches of the "alternate outcome space".

    //     nebs = {c : { d : None for d in contest.candidates}
    //        for c in contest.candidates}

    val nebs: Map<String, MutableMap<String, NEBAssertion?>> =
        raireContest.candidates.associate { it to
            raireContest.candidates.associate{ it to null as NEBAssertion? }
                .toMutableMap() }.toMap()

    //    for c in contest.candidates:
    //        for d in contest.candidates:
    //            if c == d:
    //                continue
    //
    //            asrn = NEBAssertion(contest.name, c, d)
    //
    //            tally_c = 0
    //            tally_d = 0
    //            for _,r in cvrs.items():
    //                tally_c += asrn.is_vote_for_winner(r)
    //                tally_d += asrn.is_vote_for_loser(r)
    //
    //            if tally_c > tally_d:
    //                asrn.difficulty = asn_func(tally_c, tally_d, \
    //                    contest.tot_ballots - (tally_c + tally_d), \
    //                    contest.tot_ballots)
    //
    //                asrn.votes_for_winner = tally_c
    //                asrn.votes_for_loser = tally_d
    //
    //                nebs[c][d] = asrn

    for (c in raireContest.candidates) {
        for (d in raireContest.candidates) {
            if (c == d) continue

            val asrn = NEBAssertion(raireContest.name, c, d)

            var tally_c = 0
            var tally_d = 0
            for (r in cvrs) {
                tally_c += asrn.is_vote_for_winner(r)
                tally_d += asrn.is_vote_for_loser(r)
            }

            if (tally_c > tally_d) {
                asrn.difficulty = asn_func(
                    tally_c, tally_d,
                    raireContest.tot_ballots - (tally_c + tally_d),
                    raireContest.tot_ballots
                )

                asrn.votes_for_winner = tally_c
                asrn.votes_for_loser = tally_d

                nebs[c]!![d] = asrn
            }
        }
    }

    // The RAIRE algorithm progressively searches through the space of
    // alternate election outcomes, viewing this space as a tree. We store
    // the current leaves of this tree, at any point in the search, in a
    // list called 'frontier'. Each leaf is a (potentially) partial election
    // outcome, describing the tail of the elimination sequence and eventual
    // winner. All candidates not mentioned in this tail are assumed to have
    // already been eliminated.

    // val ballots = [blt[contest.name] for _, blt in cvrs.items() if contest.name in blt]
    // this is the list of cvrs.votes for this contest, candidateID -> rank
    val ballots: List<Map<String, Int>> = cvrs.filter{ cvr -> cvr.hasContest(raireContest.name) }.map { cvr -> cvr.votes[raireContest.name]!! }

    // This is a running lowerbound on the overall difficulty of the
    // election audit.
    var lowerbound = -10.0

// Construct initial frontier.
    val frontier = RaireFrontier()

// Our frontier initially has a node for each alternate election outcome
// tail of size two. The last candidate in the tail is the ultimate winner.
    for (c in raireContest.candidates) {
        if (c == winner) continue

        for (d in raireContest.candidates) {
            if (c == d) continue

            val newn = RaireNode(listOf(d, c))
            newn.expandable = (ncands > 2)

            find_best_audit(raireContest, ballots, nebs, newn, asn_func)

            /*
            if (log) {
                println("TESTED ", file = stream, end = '')
                newn.display(stream = stream)
                if (newn.best_assertion != null) print("   Best audit ", file = stream, end = '')
                newn.best_assertion.display(stream = stream)
            }
             */

            frontier.insert_node(newn)
        }
    }

    // Flag to keep track of whether a full manual recount will be required
    var audit_not_possible = false

    /*
    if (log) {
        print("===============================================", file = stream)
        print("Initial Frontier", file = stream)
        frontier.display(stream = stream)
        print("===============================================", file = stream)
    }
     */

// -------------------- Find Assertions -----------------------------------
    while (!audit_not_possible) {
        // Check whether we can stop searching for assertions.
        // val max_on_frontier = max([node.estimate for node in frontier.nodes])
        val max_on_frontier = frontier.nodes.map { it.estimate }.max()

        if (agap > 0 && lowerbound > 0 && max_on_frontier - lowerbound <= agap) {
            // We can rule out all branches of the tree with assertions that
            // have a difficulty that is <= lowerbound.
            break
        }

        val to_expand = frontier.nodes[0]

        // We can also stop searching if all nodes on our frontier are leaves.
        if (!to_expand.expandable) break

        frontier.nodes.removeAt(0)

        val bestEstimate = to_expand.best_ancestor?.estimate
        if (bestEstimate != null && bestEstimate <= lowerbound) {
            frontier.replace_descendents(to_expand.best_ancestor!!, log)
            continue
        }

        if (to_expand.estimate <= lowerbound) {
            to_expand.expandable = false
            frontier.insert_node(to_expand)
            continue
        }

        //--------------------------------------------------------------------
        // "Dive" straight from "to_expand" down to a leaf -- one of its
        // decendants -- and find the least cost assertion to rule out the
        // branch of the alternate outcomes tree that ends in that leaf. We
        // know that this assertion will be part of the audit, as we have
        // to rule out all branches.

        //         if not to_expand.dive_node:
        //            dive_lb = perform_dive(to_expand, contest, ballots, nebs, \
        //                asn_func, lowerbound, frontier, log, stream=stream)
        //
        //            if dive_lb == np.inf:
        //                # The particular branch we dived along cannot be ruled out
        //                # with an assertion.
        //                audit_not_possible = True
        //                if log:
        //                    print("Diving finds that audit is not possible",
        //                        file=stream)
        //                break
        //
        //            if log:
        //                print("Diving LB {}, Current LB {}".format(dive_lb,
        //                    lowerbound), file=stream)
        //
        //            # We can use our new knowledge of the "best" way to rule out
        //            # the branch to update our "lowerbound" on the overall "difficulty"
        //            # of the eventual audit.
        //            lowerbound = max(lowerbound, dive_lb)
        //
        //            if to_expand.best_ancestor != None and \
        //                to_expand.best_ancestor.estimate <= lowerbound:
        //                frontier.replace_descendents(to_expand.best_ancestor, log,
        //                    stream=stream)
        //
        //                continue
        //
        //            if to_expand.estimate <= lowerbound:
        //                to_expand.expandable = False
        //                frontier.insert_node(to_expand)
        //                continue

        if (!to_expand.dive_node) {
            val dive_lb =
                perform_dive(to_expand, raireContest, ballots, nebs, asn_func, lowerbound, frontier, log)

            if (dive_lb == Double.POSITIVE_INFINITY) {
                // The particular branch we dived along cannot be ruled out with an assertion.
                audit_not_possible = true
                // if (log) print("Diving finds that audit is not possible", file = stream)
                break
            }

            // if (log) print("Diving LB {}, Current LB {}".format(dive_lb, lowerbound), file = stream)

            // We can use our new knowledge of the "best" way to rule out
            // the branch to update our "lowerbound" on the overall "difficulty"
            // of the eventual audit.
            lowerbound = max(lowerbound, dive_lb)

            val bestEstimate = to_expand.best_ancestor?.estimate
            if (bestEstimate != null && bestEstimate<= lowerbound) {
                frontier.replace_descendents(to_expand.best_ancestor!!, log) // LOOK mutable
                continue
            }

            if (to_expand.estimate <= lowerbound) {
                to_expand.expandable = false
                frontier.insert_node(to_expand)
                continue
            }
        }

//--------------------------------------------------------------------

        // if (log) print("  Expanding node ", file = stream, end = ''); to_expand.display(stream = stream)

        // Find children of current node, and find the best assertions that
        // could be used to prune those nodes from the tree of alternate outcomes.
        for (c in raireContest.candidates) {
            if (!(c in to_expand.tail) && !(c in to_expand.explored)) {
                val newn = RaireNode(listOf(c) + to_expand.tail)
                newn.expandable = newn.tail.size <= ncands

//                 # Assign a 'best ancestor' to the new node.
//                newn.best_ancestor = to_expand.best_ancestor if \
//                    to_expand.best_ancestor != None and \
//                    to_expand.best_ancestor.estimate <= to_expand.estimate \
//                    else to_expand

                val bestEstimate = to_expand.best_ancestor?.estimate
                newn.best_ancestor = if (bestEstimate != null && bestEstimate <= lowerbound)
                    to_expand.best_ancestor else to_expand

                find_best_audit(raireContest, ballots, nebs, newn, asn_func)

                /*
                if (log) {
                    print("TESTED ", file = stream, end = '')
                    newn.display(stream = stream)
                }
                 */

                val (anp, lb, _) = manage_node(newn, frontier, lowerbound, log)
                audit_not_possible = anp
                lowerbound = lb

                if (audit_not_possible) break
            }


            if (log) print("Size of frontier {}, current lower bound {}".format(frontier.nodes.size, lowerbound))

            if (audit_not_possible) break
        }

        // If a full recount is required, return empty list.
        if (audit_not_possible) {
            // if (log) print("AUDIT NOT POSSIBLE", file = stream)
            return emptyList()
        }
    }

    // ------------------------------------------------------------------------
    // Some assertions will be used to rule out multiple branches of our
    // alternate outcome tree. Form a list of all these assertions, without
    // duplicates.
    val assertions : MutableList<RaireAssertion> = mutableListOf()

    //     for node in frontier.nodes:
    //        skip = False
    //        for assrtn in assertions:
    //            if node.best_assertion.same_as(assrtn):
    //                assrtn.rules_out.update(node.best_assertion.rules_out)
    //                skip = True
    //                break
    //
    //        if not skip:
    //            assertions.append(node.best_assertion)

    for (node in frontier.nodes) {
        var skip = false
        for (assrtn in assertions) {
            if (node.best_assertion != null && node.best_assertion!!.same_as(assrtn)) {
                // add all the elements of best_assertion.rules_out to assrtn.rules_out
                assrtn.rules_out.addAll(node.best_assertion!!.rules_out)
                skip = true
                break
            }
        }
        if (!skip && node.best_assertion != null) assertions.add(node.best_assertion!!)
    }

    // Assertions will be sorted in order of how much of the alternate
    // outcome space they rule out (most to least).
    assertions.sortWith{ o1, o2 -> 0 }

    val final_audit = mutableListOf<Assertion>()

    //     if sorted_assertions != []:
    //        final_audit = [sorted_assertions.pop(0)]
    //
    //        for assertion in sorted_assertions:
    //            subsumed = False
    //            for fasrtn in final_audit:
    //                if fasrtn.subsumes(assertion):
    //                    fasrtn.rules_out.update(assertion.rules_out)
    //                    if log:
    //                        print("{} SUBSUMES {}".format(fasrtn.to_str(),
    //                            assertion.to_str()), file=stream)
    //
    //                    subsumed = True
    //                    break
    //
    //            if not subsumed:
    //                final_audit.append(assertion)

    if (assertions.isNotEmpty()) {
        val final_audit = mutableListOf(assertions.removeAt(0))

        assertions.filter { it is NENAssertion }.forEach {
            val assertion = it as NENAssertion
            var subsumed = false
            for (fasrtn in final_audit) {
                if (fasrtn.subsumes(assertion)) {
                    fasrtn.rules_out.addAll(assertion.rules_out)
                    // if (log) print("{} SUBSUMES {}".format(fasrtn.to_str(), assertion.to_str()), file = stream)
                    subsumed = true
                    break
                }
            }

            if (!subsumed) {
                final_audit.add(assertion)
            }
        }
    }

    /*
    if (log) {
        print("===============================================", file = stream)
        print("ASSERTIONS:", file = stream)
        for (assertion in final_audit) assertion.display(stream = stream)
        print("===============================================", file = stream)
    }
     */

    return final_audit
}
