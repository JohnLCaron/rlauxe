package org.cryptobiotic.rlauxe.core

import kotlin.random.Random

// default one contest, two candidates ("A" and "B"), no phantoms, plurality
// margin = percent margin of victory of A over B (between += .5)
fun makeCvrsByMargin(ncards: Int, margin: Double = 0.0) : List<Cvr> {
    val result = mutableListOf<Cvr>()
    repeat(ncards) {
        val votes = mutableMapOf<String, Map<String, Int>>()
        val random = Random.nextDouble(1.0)
        val cand = if (random < .5 + margin) "A" else "B"
        votes["AB"] = mapOf(cand to 1)
        result.add(Cvr("card-$it", votes))
    }
    return result
}

fun makeCvrsByCount(ncards: Int, count: Int) : List<Cvr> {
    val result = mutableListOf<Cvr>()
    repeat(ncards) {
        val votes = mutableMapOf<String, Map<String, Int>>()
        val cand = if (it < count) "A" else "B"
        votes["AB"] = mapOf(cand to 1)
        result.add(Cvr("card-$it", votes))
    }
    return result
}

fun tabulateVotes(cvrs: List<Cvr>): Map<String, Map<String, Int>> {
    val r = mutableMapOf<String, MutableMap<String, Int>>()
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
fun cardsPerContest(cvrs: List<Cvr>): Map<String, Int> {
    val d = mutableMapOf<String, Int>()
    for (cvr in cvrs) {
        for (con in cvr.votes.keys) {
            val accum = d.getOrPut(con) { 0 }
            d[con] = accum + 1
        }
    }
    return d
}

fun makeContestsFromCvrs(
    votes: Map<String, Map<String, Int>>,  // contestId -> candidate -> votes
    cards: Map<String, Int>, // contestId -> ncards
    choiceFunction: SocialChoiceFunction = SocialChoiceFunction.PLURALITY,
    ): List<AuditContest> {

    val contests = mutableListOf<AuditContest>()

    for ((contestId, candidateMap) in votes) {
        val winner = candidateMap.maxBy { it.value }.key

        contests.add(
            AuditContest(
                id = contestId,
                choiceFunction = choiceFunction,
                ncards = cards[contestId]!!,
                candidates = candidateMap.keys.toList(),
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
        cb.tally_pool = tally_pool
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
        cb.tally_pool = tally_pool
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

    //         val pool_set = set(c.tally_pool for c in cvr_list)
    fun poolSet(): Set<String> =
        builders.filter { it.tally_pool != null }. map { it.tally_pool!! }.toSet()

    fun poolContests(pool: String): Set<String> =
        builders.filter { it.tally_pool == pool }.map{ it.votes.keys }.flatten().toSet()

    fun poolContests(): Set<String> =
        builders.map{ it.votes.keys }.flatten().toSet()

    fun cvrsForPool(wantPool: String): List<CvrBuilder> =
        builders.filter { it.tally_pool == wantPool }

    fun add_pool_contests(tally_pools: Map<String, Set<String>>): Boolean {
        /*
        for each tally_pool, ensure every CVR in that pool has every contest in that pool

        Parameters
        ----------
        cvrs : list of CVR objects
        the set to update with additional contests as needed

        tally_pools : dict
        keys are tally_pool ids, values are sets of contests every CVR in that pool should have

        Returns
        -------
        bool : true if any contest is added to any CVR
        */
        //         added = False
        //        for c in cvrs:
        //            added = (
        //                c.update_votes({con: {} for con in tally_pools[c.tally_pool]}) or added
        //            )  # note: order of terms matters!
        //        return added
        var added = false
        for (cvrb in builders) {
            // added = c.update_votes({ con: {} for con in tally_pools[c.tally_pool] }) || added
            val tally_pool: Set<String> = tally_pools[cvrb.tally_pool!!]!!
            val tally_map = mutableMapOf<String, MutableMap<String, Int>>()
            tally_pool.forEach { tally_map[it] = mutableMapOf() }

            added = cvrb.update_votes(tally_map) || added
        } // note: order of terms matters! TODO
        return added
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
    val votes = mutableMapOf<String, MutableMap<String, Int>>() // Map(contestId, Map(candidate, vote))
    var tally_pool: String? = null
    var pool: Boolean = false
    var p: Double? = null
    var sampled: Boolean? = null
    var sample_num: Int? = null

    fun setTallyPool(pool: String): CvrBuilder {
        this.tally_pool = pool
        return this
    }

    fun setSamplingProbability(p: Double): CvrBuilder {
        this.p = p
        return this
    }

    fun addContest(contestId: String): CvrBuilder {
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

    fun addVote(contestName: String, candidateName: String, addVote: Int = 1): CvrBuilder {
        val contest = votes.getOrPut(contestName) { mutableMapOf() }
        val vote: Int = contest.getOrPut(candidateName) { 0 }
        contest[candidateName] = vote + addVote
        return this
    }

    // ContestId -> (Candidate, vote)
    fun update_votes(tally_map: Map<String, MutableMap<String, Int>>): Boolean {
        /*
        Update the votes for any contests the CVR already contains; add any contests and votes not already contained

        Parameters
        ----------
        votes: key is a contest id; value is a dict of votes--keys and values (map of candidate name to vote)

        Returns
        -------
        added: bool; true if the contest was already present; else false TODO true if any contests were added.

        Side effects
        ------------
        updates the CVR to add the contest if it was not already present and to update the votes
        */

        //        added = false
        //        for c, v in votes.items():
        //            if self.has_contest(c):
        //                self.votes[c].update(v)
        //            else:
        //                self.votes[c] = v
        //                added = true
        //        return added

        var added = false
        for ((c, v) in tally_map) {
                if (this.has_contest(c)) {
                    val currentMap = this.votes[c]!!
                    this.votes[c] = (currentMap + v).toMutableMap()
                } else {
                    this.votes[c] = v
                    added = true
                }
        }
        return added
    }

    //     var tally_pool: String? = null
    //    var pool: Boolean = false
    //    var p: Double? = null
    //    var sampled: Boolean? = null
    //    var sample_num: Double? = null
    fun build() : Cvr {
        return Cvr(id, votes)
    }

    fun has_contest(contestId: String): Boolean {
        return votes[contestId] != null
    }

    fun show() = buildString {
        appendLine("CVR $id, pool = $tally_pool")
        for ((contestId, votes) in votes) {
            appendLine("  Contest $contestId, votes = $votes")
        }
    }
}

fun cvrFromVote(candidateId: String, cvrId: String = "crv${Random.nextInt(9999)}", contestId: String = "AvB"): Cvr {
    val builder =  CvrBuilder(cvrId, false)
    builder.addVote( contestId, candidateId )
    return builder.build()
}

data class ContestVotes(val contestId: String, val votes: List<Vote>) {
    constructor(contestId: String) : this(contestId, emptyList())
    constructor(contestId: String, candidateId: String) : this(contestId, listOf(Vote(candidateId, 1)))
    constructor(contestId: String, candidateId: String, vote: Int) : this(contestId, listOf(Vote(candidateId, vote)))
    constructor(contestId: String, candidateId: String, vote: Boolean) : this(contestId, listOf(Vote(candidateId, vote)))
    constructor(contestId: String, vararg votes: Vote) : this(contestId, votes.toList())

    companion object {
        // TODO test we dont have duplicate candidates
        fun add(contestId: String, vararg vs: Vote): ContestVotes {
            return ContestVotes(contestId, vs.toList())
        }
    }
}

// TODO vote count vs true/false
data class Vote(val candidateId: String, val vote: Int = 1) {
    constructor(candidateId: String, vote: Boolean): this(candidateId, if (vote) 1 else 0)
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