package org.cryptobiotic.rlauxe.core

import kotlin.random.Random

enum class SocialChoiceFunction { PLURALITY, APPROVAL, SUPERMAJORITY, IRV }

data class Contest(
    val name: String,
    val id: Int,
    var candidateNames: Map<String, Int>, // candidate name -> candidate id
    val winnerNames: List<String>,
    val choiceFunction: SocialChoiceFunction,
    val minFraction: Double? = null, // supermajority only.
) {
    val winners: List<Int>
    val losers: List<Int>
    val candidates: List<Int>

    init {
        require(choiceFunction != SocialChoiceFunction.SUPERMAJORITY || minFraction != null)
        val mwinners = mutableListOf<Int>()
        val mlosers = mutableListOf<Int>()
        candidateNames.forEach { (name, id) ->
            if (winnerNames.contains(name)) mwinners.add(id) else mlosers.add(id)
        }
        winners = mwinners.toList()
        losers = mlosers.toList()
        candidates = winners + losers
    }
}

// contest being audited, mutable
class ContestUnderAudit(val contest: Contest, var ncards: Int? = null) {
    val id = contest.name
    val idx = contest.id
    var sampleSize: Int = 0 // Estimate the sample size required to confirm the contest at its risk limit
    var ncvrs: Int = 0
    var sampleThreshold = 0 // seems to be the highest sample.sampleNum used for this contest
}

//    CVRs can be assigned to a `tally_pool`, useful for the ONEAudit method or batch-level comparison audits
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
//        self.votes = votes  # contest/vote dict
//        self.id = id  # identifier
//        self.phantom = phantom  # is this a phantom CVR?
//        self.tally_pool = tally_pool  # what tallying pool of cards does this CVR belong to (used by ONEAudit)?
//        self.pool = pool  # pool votes on this CVR within its tally_pool?
//        self.sample_num = sample_num  # pseudorandom number used for consistent sampling
//        self.p = p  # sampling probability
//        self.sampled = sampled  # is this CVR in the sample?
//     var sampleNum = 0 // # pseudorandom number used for consistent sampling
class CvrUnderAudit(val cvr: Cvr, var sampleNum: Int = 0) {
    val id = cvr.id
    val phantom = cvr.phantom
    var sampled = false //  # is this CVR in the sample?
    var p: Double = 0.0

    fun hasContest(want: Int) = cvr.hasContest(want)

    constructor(id: String, contestIdx: Int) : this(Cvr(id, mapOf(contestIdx to IntArray(0))))
}

fun makeCvras(cvrs: List<Cvr>, random: Random): List<CvrUnderAudit> {
    // just assigns a random number, then sorts it
    // otherwise, could create a permutation of ncards
    return cvrs.map { CvrUnderAudit(it, random.nextInt()) }
}

/*
SHANGRLA Audit.make_phantoms()

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
fun makePhantoms(
    cvras: List<CvrUnderAudit>,
    contestas: List<ContestUnderAudit>,
    useStyles: Boolean = true,
    maxCards: Int,  // used when useStyle = false
    prefix: String = "phantom-",
)
        : Pair<List<CvrUnderAudit>, Int> {

    val phantombs = mutableListOf<PhantomBuilder>()
    var n_phantoms: Int
    val n_cvrs = cvras.size
    for (contest in contestas) { // } set contest parameters
        contest.ncvrs = cvras.filter { it.hasContest(contest.idx) && !it.phantom }.count() // count or sum ?
        contest.ncards =
            if (contest.ncards == null) maxCards else contest.ncards // upper bound on cards cast in the contest TODO where?
    }
    if (!useStyles) {              //  make (max_cards-len(cvr_list)) phantoms
        n_phantoms = maxCards - n_cvrs
        repeat(n_phantoms) {
            phantombs.add(PhantomBuilder(id = "${prefix}${it + 1}"))
        }
    } else {                         // create phantom CVRs as needed for each contest
        for (contest in contestas) {
            val phantoms_needed = contest.ncards!! - contest.ncvrs
            while (phantombs.size < phantoms_needed) { // make sure you have enough phantom CVRs
                phantombs.add(PhantomBuilder(id = "${prefix}${phantombs.size + 1}"))
            }
            // list contest on the first n phantom CVRs
            repeat(phantoms_needed) {
                phantombs[it].contests.add(contest.idx)
            }
        }
        n_phantoms = phantombs.size
    }
    val result = cvras + phantombs.map { it.build() }
    return Pair(result, n_phantoms)
}

private class PhantomBuilder(val id: String) {
    val contests = mutableListOf<Int>()
    fun build(): CvrUnderAudit {
        val votes = contests.map { it to IntArray(0) }.toMap()
        return CvrUnderAudit(Cvr(id, votes, true))
    }
}

///////////////////////////////////////////////////////////////////////

fun assignSampleNums(cvrList: MutableList<CvrUnderAudit>, prng: Random): Boolean {
    for (cvr in cvrList) {
        cvr.sampleNum = prng.nextInt()
    }
    return true
}

// prepare the MVRs and CVRs for comparison by putting them into the same (random) order in which the CVRs were selected
fun prepComparisonSample(
    mvrSample: MutableList<CvrUnderAudit>,
    cvrSample: MutableList<CvrUnderAudit>,
    sampleOrder: Map<String, Map<String, Any>>,
) {
    mvrSample.sortBy { sampleOrder[it.id]?.get("selectionOrder") as Int }
    cvrSample.sortBy { sampleOrder[it.id]?.get("selectionOrder") as Int }
    require(mvrSample.size == cvrSample.size) { "Number of cvrs (${cvrSample.size}) and number of mvrs (${mvrSample.size}) differ" }
    for (i in cvrSample.indices) {
        require(mvrSample[i].id == cvrSample[i].id) { "Mismatch between id of cvr (${cvrSample[i].id}) and mvr (${mvrSample[i].id})" }
    }
}

// Put the mvr sample back into the random selection order.
fun prepPollingSample(mvrSample: MutableList<CvrUnderAudit>, sampleOrder: Map<String, Map<String, Any>>) {
    mvrSample.sortBy { sampleOrder[it.id]?.get("selectionOrder") as Int }
}

fun sortCvrSampleNum(cvrList: MutableList<CvrUnderAudit>): Boolean {
    cvrList.sortBy { it.sampleNum }
    return true
}

/*
    Sample CVR ids for contests to attain contests.sampleSize samples in sampledCvrIndices

    Assumes that phantoms have already been generated and sampleSize calculated,
    and sampleNum has been assigned to every CVR, including phantoms

    Parameters
    ----------
    cvr_list: list of CVR objects
    contests: Contest sample sizes must be set before calling this function.
    sampled_cvr_indices: indices of cvrs already in the sample

    Returns
    -------
    sampled_cvr_indices: indices of CVRs to sample
*/
fun consistentSampling(
    cvrList: List<CvrUnderAudit>,
    contests: Map<String, ContestUnderAudit>,
    sampledCvrIndices: MutableList<Int>,
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
    // look through the already sampled cvs and add up the contest counts
    sampledIndices.forEach { sam ->
        contests.forEach { (_, contest) ->
            if (cvrList[sam].hasContest(contest.idx)) {
                currentSizes[contest.id] = currentSizes[contest.id]?.plus(1) ?: 1
            }
        }
    }
    // get list of cvr indexes sorted by sampleNum
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

    // go through the sortedCvrIndices, starting after the existing sampledIndices
    var inx = sampledIndices.size
    // do we need more samples?
    while (contests.values.any { contestInProgress(it) }) {
        val sidx = sortedCvrIndices[inx]
        val cvr = cvrList[sidx]
        // does this cvr contribute to one or more contests that need more samples?
        if (contests.values.any { contestInProgress(it) && cvr.hasContest(it.idx) }) {
            // then use it
            sampledIndices.add(sidx)
            contests.forEach { (_, contest) ->
                if (contestInProgress(contest) && cvr.hasContest(contest.idx)) {
                    contest.sampleThreshold = cvr.sampleNum // track the largest sample used
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
    // mark that cvr is sampled. TODO seems inefficient
    cvrList.forEachIndexed { idx, cvr ->
        if (sampledIndices.contains(idx)) {
            cvr.sampled = true
        }
    }
    return sampledIndices
}


/*
Estimate sample size for each contest && overall to allow the audit to complete.
Uses simulations. For speed, uses the numpy.r&&om Mersenne Twister instead of cryptor&&om, a higher-quality
PRNG used to select the actual audit sample.

Parameters
----------
contests: dict of dicts; the contest data structure. outer keys are contest identifiers; inner keys are assertions
    TODO wrong, this is now just dict of Contests
cvrs: list of CVR objects; the full set of CVRs
mvr_sample: list of CVR objects; manually ascertained votes
cvr_sample: list of CVR objects; CVRs corresponding to the cards that were manually inspected

Returns
-------
new_size: int
new sample size

Side effects
------------
sets c.sample_size for each Contest in contests
if use_style, sets cvr.p for each CVR
*/
//         if len(self.strata) > 1:
//            raise NotImplementedError('Stratified audits are not currently implemented.')
//        stratum = next(iter(self.strata.values())) # the only stratum
//        if stratum.use_style and cvrs is None:
//            raise ValueError("stratum.use_style==True but cvrs were not provided.")
//        # unless style information is being used, the sample size is the same for every contest.
//        old = (0 if stratum.use_style
//               else len(mvr_sample))
//        old_sizes = {c:old for c in contests.keys()}

/*
// useStyles = true, no stratum
fun find_sample_size(
    contests: Map<String, ContestUnderAudit>,
    cvrs: List<CvrUnderAudit>,
    mvr_sample: List<Cvr> = emptyList(),
    cvr_sample: List<Cvr> = emptyList(),
): Int {
    val use_style = true

    // unless style information is being used, the sample size is the same for every contest.
    val old = if (use_style) 0 else mvr_sample.size
    val old_sizes: MutableMap<String, Int> =
        contests.keys.associate { it to old }.toMutableMap()  // old_sizes = {c:old for c in contests.keys()}

//        for c, con in contests.items():
//            if stratum.use_style:
//                old_sizes[c] = np.sum(np.array([cvr.sampled for cvr in cvrs if cvr.has_contest(c)]))
//            new_size = 0
//            for a, asn in con.assertions.items():
//                if not asn.proved:
//                    if mvr_sample is not None: # use MVRs to estimate the next sample size. Set `prefix=True` to use data
//                        data, u =  asn.mvrs_to_data(mvr_sample, cvr_sample)
//                        new_size = max(new_size, asn.find_sample_size(data=data, prefix=True,
//                                                                  reps=self.reps, quantile=self.quantile,
//                                                                  seed=self.sim_seed))
//                    else:
//                        data=None
//                        new_size = max(new_size, asn.find_sample_size(data=data, rate_1=self.error_rate_1,
//                                                                  rate_2=self.error_rate_2,
//                                                                  reps=self.reps, quantile=self.quantile,
//                                                                  seed=self.sim_seed))
//            con.sample_size = new_size

    requireNotNull(cvrs)

    for ((contestId, contestAudit) in contests) {
        val contest = contestAudit.contest
            // old_sizes[c] = np.sum(np.array([cvr.sampled for cvr in cvrs if cvr.has_contest(c)]))
            // TODO summing booleans?? Maybe 0 or 1 ??
            old_sizes[contestId] = cvrs.filter { it.hasContest(contest.idx) }.map { if (it.sampled) 1 else 0 }.sum()
        var new_size = 0
        for ((_, asn) in contestAudit.assertions) {
            if (!asn.proved) {
                if (mvr_sample.isNotEmpty()) { // use MVRs to estimate the next sample size. Set `prefix=True` to use data
                    val (data, _) = asn.mvrs_to_data(mvr_sample, cvr_sample)
                    new_size = max(
                        new_size,
                        asn.find_sample_size(
                            data = data, prefix = true,
                            reps = this.reps, quantile = this.quantile,
                            seed = this.sim_seed
                        )
                    )
                }
            } else {
                new_size = max(
                    new_size, asn.find_sample_size(
                        data = null, rate1 = this.error_rate_1,
                        rate2 = this.error_rate_2,
                        reps = this.reps, quantile = this.quantile,
                        seed = this.sim_seed
                    )
                )
            }
        }
        contestAudit.sampleSize = new_size
    }

//        if stratum.use_style:
//            for cvr in cvrs:
//                if cvr.sampled:
//                    cvr.p=1
//                else:
//                    cvr.p=0
//                    for c, con in contests.items():
//                        if cvr.has_contest(c) and not cvr.sampled:
//                            cvr.p = max(con.sample_size/(con.cards - old_sizes[c]), cvr.p)
//            total_size = math.ceil(np.sum([x.p for x in cvrs if not x.phantom]))
//        else:
//            total_size = np.max(np.array([con.sample_size for con in contests.values()]))
//        return total_size
    var total_size: Int
    if (use_style) {
        requireNotNull(cvrs)
        for (cvr in cvrs) {
            if (cvr.sampled) {
                cvr.p = 1.0
            } else {
                cvr.p = 0.0
                for ((name, con) in contests) {
                    if (cvr.hasContest(con.idx) && !cvr.sampled) {
                        val p1 = con.sampleSize.toDouble() / (con.ncards!! - old_sizes[name]!!)
                        cvr.p = max(p1, cvr.p!!) // TODO nullability
                    }
                }
            }
        }
        // total_size = ceil(np.sum([x.p for x in cvrs if !x.phantom))
        val summ: Double = cvrs.filter { !it.phantom }.map { it.p!! }.sum()
        total_size = ceil(summ).toInt()
    } else {
        // total_size = np.max(np.array([con.sample_size for con in contests.values()]))
        total_size = contests.values.map { it.sampleSize!! }.max()
    }
    return total_size
}

 */