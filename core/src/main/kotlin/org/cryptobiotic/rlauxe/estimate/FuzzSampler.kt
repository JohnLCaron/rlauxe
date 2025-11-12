package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.random.Random

// for one contest, this takes a list of cvrs and fuzzes them
class ClcaFuzzSampler(
    val fuzzPct: Double,
    val cvrs: List<Cvr>,
    val contest: ContestIF,
    val cassorter: ClcaAssorter
): Sampler, Iterator<Double> {
    val maxSamples = cvrs.count { it.hasContest(contest.id) }
    val N = cvrs.size
    val permutedIndex = MutableList(N) { it }
    val welford = Welford()
    var cvrPairs: List<Pair<Cvr, Cvr>> // (mvr, cvr)
    var idx = 0

    init {
        val mvrs = remakeFuzzed()  // TODO could do fuzzing on the fly ??
        cvrPairs = mvrs.zip(cvrs)
    }

    override fun sample(): Double {
        while (idx < N) {
            val (mvr, cvr) = cvrPairs[permutedIndex[idx]]
            idx++
            if (cvr.hasContest(contest.id)) {
                val result = cassorter.bassort(mvr, cvr)
                welford.update(result)
                return result
            }
        }
        throw RuntimeException("no samples left for ${contest.id} and ComparisonAssorter ${cassorter}")
    }

    override fun reset() {
        val mvrs = remakeFuzzed()
        cvrPairs = mvrs.zip(cvrs)
        permutedIndex.shuffle(Random)
        idx = 0
    }

    fun remakeFuzzed(): List<Cvr> {
        return makeFuzzedCvrsFrom(listOf(contest), cvrs, fuzzPct)
    }

    override fun maxSamples() = maxSamples
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = cvrPairs.size

    override fun hasNext(): Boolean = (idx < N)
    override fun next(): Double = sample()
}

// for one contest, this takes a list of cvrs and fuzzes them
class PollingFuzzSampler(
    val fuzzPct: Double,
    val cvrs: List<Cvr>,
    val contest: Contest,
    val assorter: AssorterIF
): Sampler, Iterator<Double> {
    val maxSamples = cvrs.count { it.hasContest(contest.id) } // dont need this is its single contest
    val N = cvrs.size
    val welford = Welford()
    val permutedIndex = MutableList(N) { it }
    private var mvrs: List<Cvr>
    private var idx = 0

    init {
        mvrs = remakeFuzzed() // TODO could do fuzzing on the fly ??
    }

    override fun sample(): Double {
        while (idx < N) {
            val mvr = mvrs[permutedIndex[idx]]
            idx++
            if (mvr.hasContest(contest.id)) {
                val result = assorter.assort(mvr, usePhantoms = true)
                welford.update(result)
                return result
            }
        }
        throw RuntimeException("no samples left for ${contest.id} and Assorter ${assorter}")
    }

    override fun reset() {
        mvrs = remakeFuzzed()
        permutedIndex.shuffle(Random)
        idx = 0
    }

    fun remakeFuzzed(): List<Cvr> {
        return makeFuzzedCvrsFrom(listOf(contest), cvrs, fuzzPct) // single contest
    }

    override fun maxSamples() = maxSamples
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = mvrs.size

    override fun hasNext(): Boolean = (idx < N)
    override fun next(): Double = sample()
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////

fun makeFuzzedCardsFrom(contestsUA: List<ContestUnderAudit>, cards: List<AuditableCard>, fuzzPct: Double) : List<AuditableCard> {
    if (fuzzPct == 0.0) return cards
    val limit = fac / fuzzPct

    val contests = contestsUA.map { it.contest }
    val isIRV = contests.associate { it.id to (it.isIrv()) }
    val infos = contests.associate { it.id to (it.info()) }
    var count = 0
    val cardBuilders = cards.map { CardBuilder.fromCard(it) }

    cardBuilders.filter { !it.phantom }.forEach { cardb: CardBuilder ->
        val r = Random.nextDouble(limit)
        cardb.possibleContests.forEach { contestId ->
            val info = infos[contestId]!!
            if (r < fac) {
                if (isIRV[contestId]?:false) {
                    val currentVotes = cardb.votes[contestId]?.toList()?.toMutableList() ?: mutableListOf<Int>()
                    switchCandidateRankings(currentVotes, info.candidateIds)
                    cardb.replaceContestVotes(contestId, currentVotes.toIntArray())
                } else {
                    val votes = cardb.votes[contestId]
                    val currId: Int? = if (votes == null || votes.size == 0) null else votes[0] // TODO only one vote allowed, cant use on Raire
                    // choose a different candidate, or none.
                    val ncandId = chooseNewCandidate(currId, info.candidateIds)
                    cardb.replaceContestVote(contestId, ncandId)
                }
                count++
            }
        }
    }
    return cardBuilders.map { it.build() }
}


// includes undervotes i think
private val fac = 10000.0 // this allows us to use fuzzPct to 5 decimal places
fun makeFuzzedCvrsFrom(contests: List<ContestIF>, cvrs: List<Cvr>, fuzzPct: Double, welford: Welford? = null, filter: ((CvrBuilder) -> Boolean)? = null): List<Cvr> {
    if (fuzzPct == 0.0) return cvrs
    val limit = fac / fuzzPct

    val isIRV = contests.associate { it.name to (it.isIrv()) }.toMap()
    var count = 0
    val cvrbs: List<CvrBuilder> = CvrBuilders.convertCvrsToBuilders(contests.map { it.info() }, cvrs)

    cvrbs.filter { !it.phantom && (filter == null || filter(it)) }.forEach { cvrb: CvrBuilder ->
        val r = Random.nextDouble(limit)
        cvrb.contests.forEach { (_, cvb) ->
            if (r < fac) {
                val ccontest: CvrContest = cvb.contest
                if (isIRV[ccontest.name]!!) {
                    switchCandidateRankings(cvb, ccontest.candidateIds)
                } else {
                    val currId: Int? = if (cvb.votes.size == 0) null else cvb.votes[0] // TODO only one vote allowed, cant use on Raire
                    cvb.votes.clear()
                    // choose a different candidate, or none.
                    val ncandId = chooseNewCandidate(currId, ccontest.candidateIds)
                    if (ncandId != null) {
                        cvb.votes.add(ncandId)
                    }
                }
                count++
            }
        }
    }

    val expect = cvrs.size * fuzzPct
    val got = (count / cvrs.size.toDouble())
    if (welford != null) { welford.update(fuzzPct - got) }
    // println("   limit=$limit fuzzPct=$fuzzPct expect=$expect count: $count")
    return cvrbs.map { it.build() }
}

fun chooseNewCandidate(currId: Int?, candidateIds: List<Int>): Int? {
    val size = candidateIds.size
    while (true) {
        val ncandIdx = Random.nextInt(size + 1)
        if (ncandIdx == size)
            return null // choose none
        val candId = candidateIds[ncandIdx]
        if (candId != currId) {
            return candId
        }
    }
}

// for IRV
fun switchCandidateRankings(cvb: ContestVoteBuilder, candidateIds: List<Int>) {
    val ncands = candidateIds.size
    val size = cvb.votes.size
    if (size == 0) { // no votes -> random one vote
        val candIdx = Random.nextInt(ncands)
        cvb.votes.add(candidateIds[candIdx])
    } else if (size == 1) { // one votes -> no votes
        cvb.votes.clear()
    } else { // switch two randomly selected votes
        val ncandIdx1 = Random.nextInt(size)
        val ncandIdx2 = Random.nextInt(size)
        val save = cvb.votes[ncandIdx1]
        cvb.votes[ncandIdx1] = cvb.votes[ncandIdx2]
        cvb.votes[ncandIdx2] = save
    }
}

fun switchCandidateRankings(votes: MutableList<Int>, candidateIds: List<Int>) {
    val ncands = candidateIds.size
    val size = votes.size
    if (size == 0) { // no votes -> random one vote
        val candIdx = Random.nextInt(ncands)
        votes.add(candidateIds[candIdx])
    } else if (size == 1) { // one vote -> no votes
        votes.clear()
    } else { // switch two randomly selected votes
        val ncandIdx1 = Random.nextInt(size)
        val ncandIdx2 = Random.nextInt(size)
        val save = votes[ncandIdx1]
        votes[ncandIdx1] = votes[ncandIdx2]
        votes[ncandIdx2] = save
    }
}