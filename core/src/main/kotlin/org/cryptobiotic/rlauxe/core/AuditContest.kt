package org.cryptobiotic.rlauxe.core

import kotlin.random.Random

enum class SocialChoiceFunction{ PLURALITY, APPROVAL, SUPERMAJORITY, IRV }

enum class AuditType { POLLING, CARD_COMPARISON, ONEAUDIT }

data class AuditContest (
    val id: String,
    val idx: Int,
    var candidateNames: List<String>,
    val winnerNames: List<String>,
    val choiceFunction: SocialChoiceFunction = SocialChoiceFunction.PLURALITY,
    val minFraction: Double? = null, // supermajority only.
) {
    val winners = mutableListOf<Int>()
    val losers = mutableListOf<Int>()
    init {
        require(choiceFunction != SocialChoiceFunction.SUPERMAJORITY || minFraction != null)
        candidateNames.forEachIndexed { idx, cand ->
            if (winnerNames.contains(cand)) winners.add(idx) else losers.add(idx)
        }
    }
}

// contest being audited, mutable
class ContestUnderAudit(val contest: AuditContest, var ncards: Int? = null) {
    val id = contest.id
    val idx = contest.idx
    var sampleSize: Int = 0 // Estimate the sample size required to confirm the contest at its risk limit
    var ncvrs: Int = 0
    var sampleThreshold = 0
}

//     CVRs can be assigned to a `tally_pool`, useful for the ONEAudit method or batch-level comparison audits
//    using the `batch` attribute (batch-level comparison audits are not currently implemented)
//
//    CVRs can be flagged for use in ONEAudit "pool" assorter means. When a CVR is flagged this way, the
//    value of the assorter applied to the MVR is compared to the mean value of the assorter applied to the
//    CVRs in the tally batch the CVR belongs to.
//
//    CVRs can include sampling probabilities `p` and sample numbers `sample_num` (pseudo-random numbers
//    to facilitate consistent sampling)
//
//    CVRs can include a sequence number to facilitate ordering, sorting, and permuting
// CVR being audited, mutable
//         self.votes = votes  # contest/vote dict
//        self.id = id  # identifier
//        self.phantom = phantom  # is this a phantom CVR?
//        self.tally_pool = tally_pool  # what tallying pool of cards does this CVR belong to (used by ONEAudit)?
//        self.pool = pool  # pool votes on this CVR within its tally_pool?
//        self.sample_num = sample_num  # pseudorandom number used for consistent sampling
//        self.p = p  # sampling probability
//        self.sampled = sampled  # is this CVR in the sample?
class CvrUnderAudit(val cvr: Cvr) {
    val id = cvr.id
    val phantom = cvr.phantom
    var sampleNum = 0 // # pseudorandom number used for consistent sampling
    var sampled = false //  # is this CVR in the sample?

    fun hasContest(want: Int) = cvr.votes.containsKey(want)

    constructor(id: String, contestIdx: Int): this( Cvr(id, mapOf(contestIdx to emptyMap())))
}


/*
Make phantom CVRs as needed for phantom cards; set contest parameters `cards` (if not set) and `cvrs`

If use_style, phantoms are "per contest": each contest needs enough to account for the difference between
the number of cards that might contain the contest and the number of CVRs that contain the contest. This can
result in having more cards in all (manifest and phantoms) than max_cards, the maximum cast.

If not use_style, phantoms are for the election as a whole: need enough to account for the difference
between the number of cards in the manifest and the number of CVRs that contain the contest. Then, the total
number of cards (manifest plus phantoms) equals max_cards.

Parameters
----------
max_cards : int; upper bound on the number of ballot cards
cvr_list : list of CVR objects; the reported CVRs
contests : dict of contests; information about each contest under audit
prefix : String; prefix for ids for phantom CVRs to be added
use_style : Boolean
does the sampling use style information?

Returns
-------
cvr_list : list of CVR objects; the reported CVRs and the phantom CVRs
n_phantoms : int; number of phantom cards added

Side effects
------------
for each contest in `contests`, sets `cards` to max_cards if not specified by the user
for each contest in `contests`, set `cvrs` to be the number of (real) CVRs that contain the contest
*/
//         phantom_vrs = []
//        n_cvrs = len(cvr_list)
//        for c, v in contests.items():  # set contest parameters
//            v['cvrs'] = np.sum([cvr.has_contest(c) for cvr in cvr_list if not cvr.is_phantom()])
//            v['cards'] = max_cards if v['cards'] is None else v['cards'] // upper bound on cards cast in the contest
//        if not use_style:              #  make (max_cards - len(cvr_list)) phantoms
//            phantoms = max_cards - n_cvrs
//            for i in range(phantoms):
//                phantom_vrs.append(CVR(id=prefix+str(i+1), votes={}, phantom=true))
//        else:                          # create phantom CVRs as needed for each contest
//            for c, v in contests.items():
//                phantoms_needed = v['cards']-v['cvrs']
//                while len(phantom_vrs) < phantoms_needed:
//                    phantom_vrs.append(CVR(id=prefix+str(len(phantom_vrs)+1), votes={}, phantom=true))
//                for i in range(phantoms_needed):
//                    phantom_vrs[i].votes[c]={}  # list contest c on the phantom CVR
//            phantoms = len(phantom_vrs)
//        cvr_list = cvr_list + phantom_vrs
//        return cvr_list, phantoms
fun makePhantoms(cvras: List<CvrUnderAudit>,
                 contestas: List<ContestUnderAudit>,
                 useStyles: Boolean=true,
                 maxCards: Int,  // used when useStyle = false
                 prefix: String = "phantom-")
        : Pair<List<CvrUnderAudit>, Int> {

    val phantombs = mutableListOf<PhantomBuilder>()
    var n_phantoms: Int
    val n_cvrs = cvras.size
    for (contest in contestas) { // } set contest parameters
        contest.ncvrs = cvras.filter { it.hasContest(contest.idx) && !it.phantom }.count() // count or sum ?
        contest.ncards =  if (contest.ncards == null) maxCards else contest.ncards // upper bound on cards cast in the contest TODO
    }
    if (!useStyles) {              //  make (max_cards-len(cvr_list)) phantoms
        n_phantoms = maxCards - n_cvrs
        repeat (n_phantoms) {
            phantombs.add( PhantomBuilder( id = "${prefix}${it + 1}")) // TODO contestIdx=contest.idx))
        }
    } else {                         // create phantom CVRs as needed for each contest
        for (contest in contestas) {
            val phantoms_needed = contest.ncards!! - contest.ncvrs
            while (phantombs.size < phantoms_needed) { // make sure you have enough phantom CVRs
                phantombs.add(PhantomBuilder(id = "${prefix}${phantombs.size + 1}"))
            }
            // list contest on the first n phantom CVRs
            repeat(phantoms_needed) {
                phantombs[it].contests.add( contest.idx)
            }
        }
        n_phantoms = phantombs.size
    }
    val result = cvras + phantombs.map {  it.build() }
    return Pair(result, n_phantoms)
}

class PhantomBuilder(val id:String) {
    val contests = mutableListOf<Int>()
    fun build(): CvrUnderAudit{
        val votes = contests.map { it to emptyMap<Int,Int>() }.toMap()
        return CvrUnderAudit( Cvr( id, votes, true))
    }
}


/*
fun make_phantoms(max_cards: Int, cvr_list: List<CvrBuilder>, contests: List<ContestBuilder>,
                  use_style: Boolean=true, prefix: String = ""): Pair<List<CvrBuilder>, Int> {
    /*
    Make phantom CVRs as needed for phantom cards; set contest parameters `cards` (if not set) and `cvrs`

    If use_style, phantoms are "per contest": each contest needs enough to account for the difference between
    the number of cards that might contain the contest and the number of CVRs that contain the contest. This can
    result in having more cards in all (manifest and phantoms) than max_cards, the maximum cast.

    If not use_style, phantoms are for the election as a whole: need enough to account for the difference
    between the number of cards in the manifest and the number of CVRs that contain the contest. Then, the total
    number of cards (manifest plus phantoms) equals max_cards.

    Parameters
    ----------
    max_cards : int; upper bound on the number of ballot cards
    cvr_list : list of CVR objects; the reported CVRs
    contests : dict of contests; information about each contest under audit
    prefix : String; prefix for ids for phantom CVRs to be added
    use_style : Boolean; does the sampling use style information?

    Returns
    -------
    cvr_list : list of CVR objects; the reported CVRs and the phantom CVRs
    n_phantoms : int; number of phantom cards added

    Side effects
    ------------
    for each contest in `contests`, sets `cards` to max_cards if not specified by the user
    for each contest in `contests`, set `cvrs` to be the number of (real) CVRs that contain the contest
    */
    //        phantom_vrs = []
    //        n_cvrs = len(cvr_list)
    //        for c, v in contests.items():  # set contest parameters
    //            v['cvrs'] = np.sum([cvr.has_contest(c) for cvr in cvr_list if not cvr.is_phantom()])
    //            v['cards'] = max_cards if v['cards'] is None else v['cards'] // upper bound on cards cast in the contest

    val phantom_vrs = mutableListOf<CvrBuilder>()
    var n_phantoms: Int
    val n_cvrs = cvr_list.size
    for (contest in contests) { // } set contest parameters
        // TODO these are intended to be set on the contest
        contest.ncvrs = cvr_list.filter{ !it.phantom && it.has_contest(contest.id) }.count()
        if (contest.cards == null) contest.cards = max_cards // upper bound on cards cast in the contest
    }

    //        if not use_style:              #  make (max_cards - len(cvr_list)) phantoms
    //            phantoms = max_cards - n_cvrs
    //            for i in range(phantoms):
    //                phantom_vrs.append(CVR(id=prefix+str(i+1), votes={}, phantom=True))
    //        else:                          # create phantom CVRs as needed for each contest
    //            for c, v in contests.items():
    //                phantoms_needed = v['cards']-v['cvrs']
    //                while len(phantom_vrs) < phantoms_needed:
    //                    phantom_vrs.append(CVR(id=prefix+str(len(phantom_vrs)+1), votes={}, phantom=True))
    //                for i in range(phantoms_needed):
    //                    phantom_vrs[i].votes[c]={}  # list contest c on the phantom CVR
    //            phantoms = len(phantom_vrs)
    //        cvr_list = cvr_list + phantom_vrs
    //        return cvr_list, phantoms

    if (!use_style) {              //  make (max_cards-len(cvr_list)) phantoms
        n_phantoms = max_cards - n_cvrs
        repeat(n_phantoms) {
            phantom_vrs.add( CvrBuilder("$prefix${it + 1}", true))
        }
    } else {                         // create phantom CVRs as needed for each contest
        for (contest in contests) {
            val phantoms_needed = contest.cards!! - contest.ncvrs!!
            while (phantom_vrs.size < phantoms_needed) {
                phantom_vrs.add( CvrBuilder("$prefix${phantom_vrs.size + 1}", true)) // .addContest(contest.id))
            }
            repeat(phantoms_needed) {
                phantom_vrs[it].votes[contest.id]= mutableMapOf()  // list contest c on the phantom CVR
            }
        }
        n_phantoms = phantom_vrs.size
    }
    val result = cvr_list + phantom_vrs
    return Pair(result, n_phantoms)
}
*/

///////////////////////////////////////////////////////////////////////

fun assignSampleNums(cvrList: MutableList<CvrUnderAudit>, prng: Random): Boolean {
    for (cvr in cvrList) {
        cvr.sampleNum = prng.nextInt()
    }
    return true
}

fun prepComparisonSample(mvrSample: MutableList<CvrUnderAudit>, cvrSample: MutableList<CvrUnderAudit>, sampleOrder: Map<String, Map<String, Any>>) {
    mvrSample.sortBy { sampleOrder[it.id]?.get("selectionOrder") as Int }
    cvrSample.sortBy { sampleOrder[it.id]?.get("selectionOrder") as Int }
    require(mvrSample.size == cvrSample.size) { "Number of cvrs (${cvrSample.size}) and number of mvrs (${mvrSample.size}) differ" }
    for (i in cvrSample.indices) {
        require(mvrSample[i].id == cvrSample[i].id) { "Mismatch between id of cvr (${cvrSample[i].id}) and mvr (${mvrSample[i].id})" }
    }
}

fun prepPollingSample(mvrSample: MutableList<CvrUnderAudit>, sampleOrder: Map<String, Map<String, Any>>){
    mvrSample.sortBy { sampleOrder[it.id]?.get("selectionOrder") as Int }
}

fun sortCvrSampleNum(cvrList: MutableList<CvrUnderAudit>): Boolean  {
    cvrList.sortBy { it.sampleNum }
    return true
}

/*
    Sample CVR ids for contests to attain sample sizes in contests

    Assumes that phantoms have already been generated and sample_num has been assigned
    to every CVR, including phantoms

    Parameters
    ----------
    cvr_list: list
        list of CVR objects
    contests: dict
        dict of Contest objects. Contest sample sizes must be set before calling this function.
    sampled_cvr_indices: list
        indices of cvrs already in the sample

    Returns
    -------
    sampled_cvr_indices: list
        indices of CVRs to sample (0-indexed)
*/
fun consistentSampling(
    cvrList: List<CvrUnderAudit>,
    contests: MutableMap<String, ContestUnderAudit>,
    sampledCvrIndices: MutableList<Int>
): List<Int> {
    //        current_sizes = defaultdict(int)
    //        contest_in_progress = lambda c: (current_sizes[c.id] < c.sample_size)
    val currentSizes = mutableMapOf<String, Int>()
    fun contestInProgress(c: ContestUnderAudit) = (currentSizes[c.id] ?: 0) < c.sampleSize

    //        if sampled_cvr_indices is None:
    //            sampled_cvr_indices = []
    //        else:
    //            for sam in sampled_cvr_indices:
    //                for c, con in contests.items():
    //                    current_sizes[c] += 1 if cvr_list[sam].has_contest(con.id) else 0
    //        sorted_cvr_indices = [
    //            i for i, cv in sorted(enumerate(cvr_list), key=lambda x: x[1].sample_num)
    //        ]
    val sampledIndices = sampledCvrIndices ?: mutableListOf()
    sampledIndices.forEach { sam ->
        contests.forEach { (_, contest) ->
            if (cvrList[sam].hasContest(contest.idx)) {
                currentSizes[contest.id] = currentSizes[contest.id]?.plus(1) ?: 1
            }
        }
    }
    val sortedCvrIndices = cvrList.indices.sortedBy { cvrList[it].sampleNum }

    //        inx = len(sampled_cvr_indices)
    //        while any([contest_in_progress(con) for c, con in contests.items()]):
    //            if any(
    //                [
    //                    (
    //                        contest_in_progress(con)
    //                        and cvr_list[sorted_cvr_indices[inx]].has_contest(con.id)
    //                    )
    //                    for c, con in contests.items()
    //                ]
    //            ):
    //                sampled_cvr_indices.append(sorted_cvr_indices[inx])
    //                for c, con in contests.items():
    //                    if cvr_list[sorted_cvr_indices[inx]].has_contest(
    //                        con.id
    //                    ) and contest_in_progress(con):
    //                        con.sample_threshold = cvr_list[
    //                            sorted_cvr_indices[inx]
    //                        ].sample_num
    //                        current_sizes[c] += 1
    //            inx += 1
    var inx = sampledIndices.size
    while (contests.values.any { contestInProgress(it) }) {
        if (contests.values.any {
                contestInProgress(it) && cvrList.get(sortedCvrIndices[inx]).hasContest(it.idx) }) {
            sampledIndices.add(sortedCvrIndices[inx])
            contests.forEach { (_, contest) ->
                if (contestInProgress(contest) && cvrList[sortedCvrIndices[inx]].hasContest(contest.idx)) {
                    contest.sampleThreshold = cvrList[sortedCvrIndices[inx]]!!.sampleNum
                    currentSizes[contest.id] = currentSizes[contest.id]?.plus(1) ?: 1
                }
            }
        }
        inx++
    }

    //        for i in range(len(cvr_list)):
    //            if i in sampled_cvr_indices:
    //                cvr_list[i].sampled = True
    //        return sampled_cvr_indices
    cvrList.forEachIndexed { idx, cvr ->
        if (sampledIndices.contains(idx)) {
            cvr.sampled = true
        }
    }
    return sampledIndices
}