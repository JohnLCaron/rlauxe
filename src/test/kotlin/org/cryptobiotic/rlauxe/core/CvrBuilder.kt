package org.cryptobiotic.rlauxe.core

import kotlin.random.Random

fun makeCvrsByExactCount(counts : List<Int>) : List<Cvr> {
    val cvrs = mutableListOf<Cvr>()
    var total = 0
    counts.forEachIndexed { idx, it ->
        repeat(it) {
            val votes = mutableMapOf<Int, Map<Int, Int>>()
            votes[0] = mapOf(idx to 1)
            cvrs.add(Cvr("card-$total", votes))
            total++
        }
    }
    cvrs.shuffle( Random)
    return cvrs
}

fun makeCvr(idx: Int): Cvr {
    val votes = mutableMapOf<Int, Map<Int, Int>>()
    votes[0] = mapOf(idx to 1)
    return Cvr("card", votes)
}

// default one contest, two candidates ("A" and "B"), no phantoms, plurality
// margin = percent margin of victory of A over B (between += .5)
fun makeCvrsByMargin(ncards: Int, margin: Double = 0.0) : List<Cvr> {
    val result = mutableListOf<Cvr>()
    repeat(ncards) {
        val votes = mutableMapOf<Int, Map<Int, Int>>()
        val random = Random.nextDouble(1.0)
        val cand = if (random < .5 + margin/2.0) 0 else 1
        votes[0] = mapOf(cand to 1)
        result.add(Cvr("card-$it", votes))
    }
    return result
}

fun margin2theta(margin: Double) = (.5 + margin/2)
fun theta2margin(theta: Double) = (2.0 * (theta - .5))

fun makeCvrsByExactMargin(ncards: Int, margin: Double = 0.0) : List<Cvr> {
    return makeCvrsByExactTheta(ncards, margin2theta(margin))
}

fun makeCvrsByExactTheta(ncards: Int, theta: Double = 0.0) : List<Cvr> {
    val randomCvrs = mutableListOf<Cvr>()
    repeat(ncards) {
        val votes = mutableMapOf<Int, Map<Int, Int>>()
        val random = Random.nextDouble(1.0)
        val cand = if (random < theta) 0 else 1
        votes[0] = mapOf(cand to 1)
        randomCvrs.add(Cvr("card-$it", votes))
    }
    val expectedAVotes = (ncards * theta).toInt()
    val actualAvotes = randomCvrs.map {  it.hasMarkFor(0, 0)}.sum()
    val needToChangeVotesToA = expectedAVotes - actualAvotes
    var changed = 0
    // we need more A votes, needToChangeVotesToA > 0
    if (needToChangeVotesToA > 0) {
        while (changed < needToChangeVotesToA) {
            val cvrIdx = Random.nextInt(ncards)
            val cvr = randomCvrs[cvrIdx]
            if (cvr.hasMarkFor(0, 1) == 1) {
                val votes = mutableMapOf<Int, Map<Int, Int>>()
                votes[0] = mapOf(0 to 1)
                randomCvrs[cvrIdx] = Cvr("card-$cvrIdx",  votes)
                changed++
            }
        }
    } else {
        // we need more B votes, needToChangeVotesToA < 0
        while (changed > needToChangeVotesToA) {
            val cvrIdx = Random.nextInt(ncards)
            val cvr = randomCvrs[cvrIdx]
            if (cvr.hasMarkFor(0, 0) == 1) {
                val votes = mutableMapOf<Int, Map<Int, Int>>()
                votes[0] = mapOf(1 to 1)
                randomCvrs[cvrIdx] = Cvr("card-$cvrIdx",  votes)
                changed--
            }
        }
    }
    val checkAvotes = randomCvrs.map {  it.hasMarkFor(0, 0)}.sum()
    require(checkAvotes == expectedAVotes)
    return randomCvrs
}

fun makeCvrsByCount(ncards: Int, count: Int) : List<Cvr> {
    val result = mutableListOf<Cvr>()
    repeat(ncards) {
        val votes = mutableMapOf<Int, Map<Int, Int>>()
        val cand = if (it < count) 0 else 0
        votes[0] = mapOf(cand to 1)
        result.add(Cvr("card-$it", votes))
    }
    return result
}

fun tabulateVotes(cvrs: List<Cvr>): Map<Int, Map<Int, Int>> {
    val r = mutableMapOf<Int, MutableMap<Int, Int>>()
    for (cvr in cvrs) {
        for ((con, conVotes) in cvr.votes) {
            val accumVotes = r.getOrPut(con) { mutableMapOf() }
            for ((cand, vote) in conVotes) {
                val accum = accumVotes.getOrPut(cand) { 0 }
                accumVotes[cand] = accum + vote
            }
        }
    }
    return r
}

// Number of cards in each contest, return contestId -> ncards
fun cardsPerContest(cvrs: List<Cvr>): Map<Int, Int> {
    val d = mutableMapOf<Int, Int>()
    for (cvr in cvrs) {
        for (con in cvr.votes.keys) {
            val accum = d.getOrPut(con) { 0 }
            d[con] = accum + 1
        }
    }
    return d
}

fun makeContestsFromCvrs(
    votes: Map<Int, Map<Int, Int>>,  // contestId -> candidate -> votes
    cards: Map<Int, Int>, // contestId -> ncards
    choiceFunction: SocialChoiceFunction = SocialChoiceFunction.PLURALITY,
    ): List<AuditContest> {

    val contests = mutableListOf<AuditContest>()

    for ((contestId, candidateMap) in votes) {
        val winner = candidateMap.maxBy { it.value }.key

        contests.add(
            AuditContest(
                id = "contest$contestId",
                idx = contestId,
                choiceFunction = choiceFunction,
                // ncards = cards[contestId]!!,
                candidates = candidateMap.keys.map { it },
                winners = listOf(winner),
            )
        )
    }

    return contests
}

class CvrBuilders {
    val builders = mutableListOf<CvrBuilder>()

    fun add(id: String, tally_pool: String? = null, pool: Boolean = false, sampled: Boolean = false, sample_num: Int? = null, p: Double? = null): CvrBuilders {
        val cb = CvrBuilder(id)
        cb.pool = pool
        cb.sampled = sampled
        cb.sample_num = sample_num
        cb.p = p
        builders.add(cb)
        return this
    }

    fun addBuilder(builder: CvrBuilder): CvrBuilders {
        builders.add(builder)
        return this
    }

    fun add(id: String, tally_pool: String, vararg contests: ContestVotes): CvrBuilders {
        val cb = CvrBuilder(id)
        contests.forEach{
            cb.addContestVotes(it)
        }
        builders.add(cb)
        return this
    }

    fun add(id: String, vararg contests: ContestVotes): CvrBuilders {
        val cb = CvrBuilder(id)
        contests.forEach{
            cb.addContestVotes(it)
        }
        builders.add(cb)
        return this
    }

    fun setContestVotes(id: String, vararg contests: ContestVotes): CvrBuilders {
        val cb = builders.find { it.id == id }!!
        contests.forEach { cb.addContestVotes(it) }
        return this
    }

    fun build() : List<Cvr> {
        return builders.map { it.build() }
    }

    fun show() = buildString {
        builders.forEach { append(it.show()) }
    }
}

class CvrBuilder(
    val id: String,
    val phantom: Boolean = false,
) {
    val votes = mutableMapOf<Int, MutableMap<Int, Int>>() // Map(contestId, Map(candidate, vote))
    var pool: Boolean = false
    var p: Double? = null
    var sampled: Boolean? = null
    var sample_num: Int? = null

    fun setSamplingProbability(p: Double): CvrBuilder {
        this.p = p
        return this
    }

    fun addContest(contestId: Int): CvrBuilder {
        votes.getOrPut(contestId) { mutableMapOf() }
        return this
    }

    fun addContestVotes(cv: ContestVotes): CvrBuilder {
        val contest = votes.getOrPut(cv.contestId) { mutableMapOf() }
        cv.votes.forEach { (candidateName, vote) ->
            val accum: Int = contest.getOrPut(candidateName) { 0 }
            contest[candidateName] = accum + vote
        }
        return this
    }

    fun addVote(contestIdx: Int, candidateIdx: Int, addVote: Int = 1): CvrBuilder {
        val contest = votes.getOrPut(contestIdx) { mutableMapOf() }
        val vote: Int = contest.getOrPut(candidateIdx) { 0 }
        contest[candidateIdx] = vote + addVote
        return this
    }

    fun build() : Cvr {
        return Cvr(id, votes)
    }

    fun has_contest(contestIdx: Int): Boolean {
        return votes[contestIdx] != null
    }

    fun show() = buildString {
        appendLine("CVR $id")
        for ((contestId, votes) in votes) {
            appendLine("  Contest $contestId, votes = $votes")
        }
    }
}

fun cvrFromVote(candidateIdx: Int, cvrId: String = "crv${Random.nextInt(9999)}", contestIdx: Int = 0): Cvr {
    val builder =  CvrBuilder(cvrId, false)
    builder.addVote( contestIdx, candidateIdx )
    return builder.build()
}

data class ContestVotes(val contestId: Int, val votes: List<Vote>) {
    constructor(contestIdx: Int) : this(contestIdx, emptyList())
    constructor(contestIdx: Int, candidateIdx: Int) : this(contestIdx, listOf(Vote(candidateIdx, 1)))
    constructor(contestIdx: Int, candidateIdx: Int, vote: Int) : this(contestIdx, listOf(Vote(candidateIdx, vote)))
    constructor(contestIdx: Int, candidateIdx: Int, vote: Boolean) : this(contestIdx, listOf(Vote(candidateIdx, vote)))
    constructor(contestIdx: Int, vararg votes: Vote) : this(contestIdx, votes.toList())

    companion object {
        // TODO test we dont have duplicate candidates
        fun add(contestIdx: Int, vararg vs: Vote): ContestVotes {
            return ContestVotes(contestIdx, vs.toList())
        }
    }
}

// TODO vote count vs true/false
data class Vote(val candidateIdx: Int, val vote: Int = 1) {
    constructor(candidateIdx: Int, vote: Boolean): this(candidateIdx, if (vote) 1 else 0)
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