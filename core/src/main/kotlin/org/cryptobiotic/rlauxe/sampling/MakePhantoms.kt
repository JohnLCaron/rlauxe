package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.util.Prng

// TODO deal with use_styles

// SHANGRLA.make_phantoms(). Probably 2.d ?
//     @classmethod
//    def make_phantoms(
//        cls,
//        audit: dict = None,
//        contests: dict = None,
//        cvr_list: "list[CVR]" = None,
//        prefix: str = "phantom-",
//    ) -> Tuple[list, int]:
//        """
//        Make phantom CVRs as needed for phantom cards; set contest parameters `cards` (if not set) and `cvrs`
//
//        **Currently only works for unstratified audits.**
//        If `audit.strata[s]['use_style']`, phantoms are "per contest": each contest needs enough to account for the
//        difference between the number of cards that might contain the contest and the number of CVRs that contain
//        the contest. This can result in having more cards in all (manifest and phantoms) than max_cards, the maximum cast.
//
//        If `not use_style`, phantoms are for the election as a whole: need enough to account for the difference
//        between the number of cards in the manifest and the number of CVRs that contain the contest. Then, the total
//        number of cards (manifest plus phantoms) equals max_cards.
//
//        If `not use_style` sets `cards = max_cards` for each contest
//
//        Parameters
//        ----------
//        cvr_list: list of CVR objects
//            the reported CVRs
//        contests: dict of contests
//            information about each contest under audit
//        prefix: String
//            prefix for ids for phantom CVRs to be added
//
//        Returns
//        -------
//        cvr_list: list of CVR objects
//            the reported CVRs and the phantom CVRs
//        n_phantoms: int
//            number of phantom cards added
//
//        Side effects
//        ------------
//        for each contest in `contests`, sets `cards` to max_cards if not specified by the user or if `not use_style`
//        for each contest in `contests`, set `cvrs` to be the number of (real) CVRs that contain the contest
//        """
//        if len(audit.strata) > 1:
//            raise NotImplementedError("stratified audits not implemented")
//        stratum = next(iter(audit.strata.values()))
//        use_style = stratum.use_style
//        max_cards = stratum.max_cards
//        phantom_vrs = []
//        n_cvrs = len(cvr_list)
//        for c, con in contests.items():  # set contest parameters
//            con.cvrs = np.sum(
//                [cvr.has_contest(con.id) for cvr in cvr_list if not cvr.phantom]
//            )
//            con.cards = (
//                max_cards if ((con.cards is None) or (not use_style)) else con.cards
//            )
//        # Note: this will need to change for stratified audits
//        if not use_style:  #  make (max_cards - len(cvr_list)) phantoms
//            phantoms = max_cards - n_cvrs
//            for i in range(phantoms):
//                phantom_vrs.append(CVR(id=prefix + str(i + 1), votes={}, phantom=True))
//        else:  # create phantom CVRs as needed for each contest
//            for c, con in contests.items():
//                phantoms_needed = con.cards - con.cvrs
//                while len(phantom_vrs) < phantoms_needed:  # creat additional phantoms
//                    phantom_vrs.append(
//                        CVR(
//                            id=prefix + str(len(phantom_vrs) + 1),
//                            votes={},
//                            phantom=True,
//                        )
//                    )
//                for i in range(phantoms_needed):
//                    phantom_vrs[i].votes[
//                        con.id
//                    ] = {}  # list contest c on the phantom CVR
//            phantoms = len(phantom_vrs)
//        cvr_list = cvr_list + phantom_vrs
//        return cvr_list, phantoms

fun makePhantomCvrs(
    contestsUA: List<ContestUnderAudit>,
    prefix: String = "phantom-",
    prng: Prng,
): List<CvrUnderAudit> {
    // code assertRLA.ipynb
    // + Prepare ~2EZ:
    //    - `N_phantoms = max_cards - cards_in_manifest`
    //    - If `N_phantoms < 0`, complain
    //    - Else create `N_phantoms` phantom cards
    //    - For each contest `c`:
    //        + `N_c` is the input upper bound on the number of cards that contain `c`
    //        + if `N_c is None`, `N_c = max_cards - non_c_cvrs`, where `non_c_cvrs` is #CVRs that don't contain `c`
    //        + `C_c` is the number of CVRs that contain the contest
    //        + if `C_c > N_c`, complain
    //        + else if `N_c - C_c > N_phantoms`, complain
    //        + else:
    //            - Consider contest `c` to be on the first `N_c - C_c` phantom CVRs
    //            - Consider contest `c` to be on the first `N_c - C_c` phantom ballots

    // 3.4 SHANGRLA
    // If N_c > ncvrs, create N − n “phantom ballots” and N − n “phantom CVRs.”

    // create phantom CVRs as needed for each contest
    val phantombs = mutableListOf<PhantomBuilder>()

    for (contest in contestsUA) {
        val phantoms_needed = contest.Nc - contest.ncvrs
        while (phantombs.size < phantoms_needed) { // make sure you have enough phantom CVRs
            phantombs.add(PhantomBuilder(id = "${prefix}${phantombs.size + 1}"))
        }
        // include this contest on the first n phantom CVRs
        repeat(phantoms_needed) {
            phantombs[it].contests.add(contest.id)
        }
    }
    return phantombs.map { it.build(prng) }
}

class PhantomBuilder(val id: String) {
    val contests = mutableListOf<Int>()
    fun build(prng: Prng): CvrUnderAudit {
        val votes = contests.associateWith { IntArray(0) }
        return CvrUnderAudit(Cvr(id, votes, phantom = true), prng.next())
    }
}