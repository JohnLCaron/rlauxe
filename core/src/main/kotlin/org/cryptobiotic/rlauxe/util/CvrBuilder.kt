package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.core.AuditContest
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import kotlin.random.Random

class CvrContest(val name: String, val id: Int) {
    val candidates = mutableMapOf<String, Int>()
    var candidateIdx = 0

    fun getCandidateIdx(name: String): Int {
        return candidates.getOrPut(name) { candidateIdx++ }
    }
}

class CvrBuilders {
    val builders = mutableListOf<CvrBuilder>()
    var id = 0
    val contests = mutableMapOf<String, CvrContest>()
    var contestId = 0

    fun getContest(contestName: String): CvrContest {
        return contests.getOrPut(contestName) { CvrContest( contestName, contestId++) }
    }

    fun addCrv(phantom: Boolean = false): CvrBuilder {
        val cb = CvrBuilder(this, ++id, phantom = phantom)
        builders.add(cb)
        return cb
    }

    fun build(): List<Cvr> {
        return builders.map { it.build() }
    }

    fun show() = buildString {
        val cvrs = this@CvrBuilders.build()
        print(buildString{
            contests.forEach{ appendLine("${it.key}: ${it.value.id}")}
            cvrs.forEach{ appendLine(it) }
        })
    }

}

class CvrBuilder(
    val builders: CvrBuilders,
    val id: Int,
    val phantom: Boolean = false
) {
    val contests = mutableMapOf<Int, ContestBuilder>()

    fun addContest(contestName: String): ContestBuilder {
        val contest = builders.getContest(contestName)
        return contests.getOrPut(contest.id) { ContestBuilder(this, contest) }
    }

    fun addContest(contestName: String, candName: String): ContestBuilder {
        val contest = builders.getContest(contestName)
        val cb = contests.getOrPut(contest.id) { ContestBuilder(this, contest) }
        cb.addCandidate(candName)
        return cb
    }

    fun done() = builders

    fun build() : Cvr {
        val votes: Map<Int, Map<Int, Int>> = contests.values.map { it.build() }.toMap()
        return Cvr("card$id", votes, phantom)
    }
}

class ContestBuilder(
    val builder: CvrBuilder,
    val contest: CvrContest,
) {
    val votes = mutableMapOf<Int, Int>() // Map(candidateIdx, vote))

    fun addCandidate(candName: String, addVote: Int = 1): ContestBuilder {
        val candIdx =  contest.getCandidateIdx(candName)
        votes[candIdx] = addVote
        return this
    }

    fun build(): Pair<Int, Map<Int, Int>> {
        return Pair(contest.id, votes)
    }

    fun done() = builder
    fun ddone() = builder.builders
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


///////////////////////////////////////////////////////////////////////////////
// old, deprecated TODO get rid of?

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
                candidateNames = candidateMap.keys.map { "candidate$it" },
                winnerNames = listOf("candidate$winner"),
            )
        )
    }

    return contests
}
