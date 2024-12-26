package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.Ballot
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.Cvr

// TODO deal with use_styles

// Stylish p.5-6, paraphrased for clarity:
// 1.a) Let 𝑁𝑐 denote the upper bound on the number of cards that contain contest 𝑐
// 2.b) If there are more CVRs that contain a(ny) contest than the upper bound on the
//    number of cards that contain the contest, stop: something is seriously wrong.
// 2.c) If Nc is greater than the number of CVRs that contain the contest, create a corresponding set
//   of “phantom” CVRs as described in section 3.4 of [SHANGRLA]. The phantom CVRs
//   are generated separately for each contest: each phantom card contains only one contest.
// 2.d) If Nc is greater than the number of physical cards that contain the contest, create enough
//   “phantom” cards to make up the difference.

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
    contests: List<Contest>,
    ncvrs: Map<Int, Int>,
    prefix: String = "phantom-",
): List<Cvr> {

    val phantombs = mutableListOf<PhantomBuilder>()

    for (contest in contests) {
        val phantoms_needed = contest.Nc - ncvrs[contest.id]!!
        while (phantombs.size < phantoms_needed) { // make sure you have enough phantom CVRs
            phantombs.add(PhantomBuilder(id = "${prefix}${phantombs.size + 1}"))
        }
        // include this contest on the first n phantom CVRs
        repeat(phantoms_needed) {
            phantombs[it].contests.add(contest.id)
        }
    }
    return phantombs.map { it.buildCvr() }
}

fun makePhantomBallots(
    contests: List<Contest>,
    ncvrs: Map<Int, Int>,
    prefix: String = "phantom-",
): List<Ballot> {

    val phantombs = mutableListOf<PhantomBuilder>()

    for (contest in contests) {
        val phantoms_needed = contest.Nc - ncvrs[contest.id]!!
        while (phantombs.size < phantoms_needed) { // make sure you have enough phantom CVRs
            phantombs.add(PhantomBuilder(id = "${prefix}${phantombs.size + 1}"))
        }
        // include this contest on the first n phantom CVRs
        repeat(phantoms_needed) {
            phantombs[it].contests.add(contest.id)
        }
    }
    return phantombs.map { it.buildBallot() }
}

class PhantomBuilder(val id: String) {
    val contests = mutableListOf<Int>()
    fun buildCvr(): Cvr {
        val votes = contests.associateWith { IntArray(0) }
        return Cvr(id, votes, phantom = true)
    }
    fun buildBallot(): Ballot {
        return Ballot(id, phantom = true, null, contests)
    }
}