package org.cryptobiotic.rlauxe.reader

import org.cryptobiotic.rlauxe.*

/*
fun make_assertions_from_json(
    contest: Contest,
    candidates: List<String>,
    json_assertions: Map<String, Assertion>,
    test: TestFn?,
    estim: EstimatorFn?,
    bet: BetFn?
): Map<String, Assertion> {
    /*
    dict of Assertion objects from a RAIRE-style json representations of assertions.

    The assertion_type for each assertion must be one of the JSON_ASSERTION_TYPES
    (class constants).

    Parameters
    ----------
    contest: contest to which the assorter applies
    candidates: list of identifiers for all candidates in relevant contest.
    json_assertions: Assertions to be tested for the relevant contest.
    test: instance of NonnegMean; risk function for the contest
    estim: an estimation method of NonnegMean; estimator the test uses for the alternative

    Returns
    -------
    dict of assertions for each assertion specified in 'json_assertions'.
    */
    //         assertions = {}
    //        for assrtn in json_assertions:
    //            winr = assrtn['winner']
    //            losr = assrtn['loser']
    //            if assrtn['assertion_type'] == cls.WINNER_ONLY:
    //                # CVR is a vote for the winner only if it has the
    //                # winner as its first preference
    //                winner_func = lambda v, contest_id=contest.id, winr=winr: 1 \
    //                              if v.get_vote_for(contest_id, winr) == 1 else 0
    //
    //                # CVR is a vote for the loser if they appear and the
    //                # winner does not, or they appear before the winner
    //                loser_func = lambda v, contest_id=contest.id, winr=winr, losr=losr: \
    //                             v.rcv_lfunc_wo(contest_id, winr, losr)
    //
    //                wl_pair = winr + ' v ' + losr
    //                _test = NonnegMean(test=test, estim=estim, bet=bet, u=1, N=contest.cards, t=1/2, random_order=True)
    //                assertions[wl_pair] = Assertion(contest,
    //                                                Assorter(contest=contest, winner=winner_func,
    //                                                   loser=loser_func, upper_bound=1), winner=winr, loser=losr, test=_test)
    //
    //            elif assrtn['assertion_type'] == cls.IRV_ELIMINATION:
    //                # Context is that all candidates in 'eliminated' have been
    //                # eliminated and their votes distributed to later preferences
    //                elim = [e for e in assrtn['already_eliminated']]
    //                remn = [c for c in candidates if c not in elim]
    //                # Identifier for tracking which assertions have been proved
    //                wl_given = winr + ' v ' + losr + ' elim ' + ' '.join(elim)
    //                _test = NonnegMean(test=test, estim=estim, bet=bet, u=1, N=contest.cards, t=1/2, random_order=True)
    //                assertions[wl_given] = Assertion(contest, Assorter(contest=contest,
    //                                       assort = lambda v, contest_id=contest.id, winner=winr, loser=losr, remn=remn:
    //                                       ( v.rcv_votefor_cand(contest.id, winner, remn)
    //                                       - v.rcv_votefor_cand(contest.id, loser, remn) +1)/2,
    //                                       upper_bound=1), winner=winr, loser=losr, test=_test)
    //            else:
    //                raise NotImplemented(f'JSON assertion type {assrtn["assertion_type"]} not implemented.')
    //        return assertions

    val assertions = mutableMapOf<String, Assertion>()
    for ((contestId, assrtn) in json_assertions) {
        val winr = assrtn.winner
        val losr = assrtn.loser
        if (assrtn.assertion_type == "WINNER_ONLY") {
            // CVR is a vote for the winner only if it has the winner as its first preference
            val winner_func = { cvr: CVR -> if (cvr.get_vote_for(contestId, winr) == 1) 1 else 0 }

            // CVR is a vote for the loser if they appear and the winner does not, or they appear before the
            // winner
            val loser_func = { cvr: CVR -> cvr.rcv_lfunc_wo(contestId, winr, losr) }

            val wl_pair = winr + " v " + losr
            val _test = NonnegMean(
                test = test,
                estim = estim,
                bet = bet,
                u = 1.0f,
                N = contest.cards,
                t = 0.5f,
                random_order = true
            )
            assertions[wl_pair] = Assertion(
                contest,
                Assorter(
                    contest = contest, winner = winner_func,
                    loser = loser_func, upper_bound = 1
                ), winner = winr, loser = losr, test = _test
            )

        } else if (assrtn.assertion_type == "IRV_ELIMINATION") {
            // Context is that all candidates in "eliminated" have been
            // eliminated and their votes distributed to later preferences
            val elim = [e for e in assrtn["already_eliminated"]]
            val remn = [c for c in candidates if c not in elim]
            // Identifier for tracking which assertions have been proved
            val wl_given = winr + " v " + losr + " elim " + " ".join(elim)
            val _test = NonnegMean(
                test = test,
                estim = estim,
                bet = bet,
                u = 1,
                N = contest.cards,
                t = 1 / 2,
                random_order = true
            )
            assertions[wl_given] = Assertion(
                contest, Assorter(
                    contest = contest,
                    assort = lambda v, contest_id = contest.id, winner = winr, loser = losr, remn = remn:
                    (
                    v.rcv_votefor_cand(contest.id, winner, remn)
                            - v.rcv_votefor_cand(contest.id, loser, remn) + 1
                ) / 2,
                upper_bound = 1
            ), winner = winr, loser = losr, test = _test)
        } else {
            throw Exception("JSON assertion type ${assrtn["assertion_type"]} not implemented.")
        }
    }
    return assertions
}
 */