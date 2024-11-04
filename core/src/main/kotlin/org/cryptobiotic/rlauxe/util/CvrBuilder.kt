package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.core.Cvr

// for testing, here to share between modules

class CvrContest(val name: String, val id: Int) {
    val candidates = mutableMapOf<String, Int>()
    var candidateId = 0

    fun getCandidateIdx(name: String): Int {
        return candidates.getOrPut(name) { candidateId++ }
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
        val votes: Map<Int, IntArray> = contests.values.map { it.build() }.toMap()
        return Cvr("card$id", votes, phantom)
    }
}

class ContestBuilder(
    val builder: CvrBuilder,
    val contest: CvrContest,
) {
    val votes = mutableListOf<Int>() // List(candidateId))

    fun addCandidate(candName: String, addVote: Int = 1): ContestBuilder {
        val candIdx =  contest.getCandidateIdx(candName)
        if (addVote == 1) votes.add(candIdx)
        return this
    }

    fun build(): Pair<Int, IntArray> {
        return Pair(contest.id, votes.toIntArray())
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
