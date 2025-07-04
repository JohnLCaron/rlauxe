package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.core.*

class CvrBuilders {
    val builders = mutableListOf<CvrBuilder>()
    var nextCvrId = 0
    val contests = mutableMapOf<String, CvrContest>()
    var contestId = 0

    fun addContests(rcontests: List<ContestInfo>): CvrBuilders {
        rcontests.forEach {
            val c = CvrContest(it.name, it.id)
            c.candidates.putAll(it.candidateNames)
            contests[it.name] = c
        }
        return this
    }

    fun getContest(contestName: String): CvrContest {
        return contests.getOrPut(contestName) { CvrContest( contestName, contestId++) }
    }

    fun getContest(contestId: Int): CvrContest? {
        return contests.values.find { it.id == contestId }
    }

    fun addCvr(): CvrBuilder {
        this.nextCvrId++
        val cb = CvrBuilder(this, "card${nextCvrId}")
        builders.add(cb)
        return cb
    }

    fun addPhantomCvr(cvrId: String? = null): CvrBuilder {
        val useId = cvrId?: "card${this.nextCvrId++}"
        val cb = CvrBuilder(this, useId, phantom=true)
        builders.add(cb)
        return cb
    }

    fun addCvr(cvrId: String): CvrBuilder {
        val cb = CvrBuilder(this, cvrId)
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

    companion object {
        fun convertCvrs(contests:List<ContestInfo>, cvrs: List<Cvr>): List<CvrBuilder> {
            val cvrsbs = CvrBuilders()
            cvrsbs.addContests( contests)
            cvrs.forEach { CvrBuilder.fromCvr(cvrsbs, it) }
            return cvrsbs.builders
        }
    }
}

class CvrContest(val name: String, val id: Int) {
    val candidates = mutableMapOf<String, Int>()
    var candidateId = 0

    val candidateIds: List<Int> by lazy { candidates.values.toList() }

    fun getCandidateIdx(name: String): Int {
        return candidates.getOrPut(name) { candidateId++ }
    }
}

class CvrBuilder(
    val builders: CvrBuilders,
    val id: String,
    val phantom: Boolean = false,
) {
    val contests = mutableMapOf<Int, ContestVoteBuilder>() // contestId -> ContestVoteBuilder

    fun addContest(contestName: String): ContestVoteBuilder {
        val contest = builders.getContest(contestName)
        return contests.getOrPut(contest.id) { ContestVoteBuilder(this, contest) }
    }

    fun addContest(contestName: String, candidateId: Int?): ContestVoteBuilder {
        val contest = builders.getContest(contestName)
        val cb = contests.getOrPut(contest.id) { ContestVoteBuilder(this, contest) }
        if (candidateId != null) {
            cb.addCandidate(candidateId)
        }
        return cb
    }

    fun addContest(contestName: String, candidateName: String?): ContestVoteBuilder {
        val contest = builders.getContest(contestName)
        val cb = contests.getOrPut(contest.id) { ContestVoteBuilder(this, contest) }
        if (candidateName != null) {
            cb.addCandidate(candidateName)
        }
        return cb
    }

    fun addContest(contestId: Int, votes: IntArray): CvrBuilder  {
        val contest: CvrContest? = builders.getContest(contestId)
        // if contest not present, just skip
        if (contest != null) {
            val cvb: ContestVoteBuilder = contests.getOrPut(contest.id) { ContestVoteBuilder(this, contest) }
            votes.forEach { cvb.votes.add(it) }
        }
        return this
    }

    fun done() = builders

    fun build(poolId: Int?=null) : Cvr {
        val votes: Map<Int, IntArray> = contests.values.map { it.build() }.toMap()
        return Cvr(id, votes, phantom, poolId = poolId)
    }

    companion object {
        fun fromCvr(builders: CvrBuilders, cvr: Cvr): CvrBuilder {
            val cvrb: CvrBuilder = if (cvr.phantom) builders.addPhantomCvr(cvr.id) else builders.addCvr(cvr.id)
            cvr.votes.forEach { contestId, votes ->
                cvrb.addContest(contestId, votes)
            }
            return cvrb
        }
    }
}

class ContestVoteBuilder(
    val builder: CvrBuilder,
    val contest: CvrContest,
) {
    val votes = mutableListOf<Int>() // List(candidateId))

    fun addCandidate(candName: String, addVote: Int = 1): ContestVoteBuilder {
        val candIdx =  contest.getCandidateIdx(candName)
        if (addVote == 1) votes.add(candIdx)
        return this
    }

    fun addCandidate(candId: Int, addVote: Int = 1): ContestVoteBuilder {
        if (addVote == 1) votes.add(candId) // TODO assumes vote = 1
        return this
    }

    fun build(): Pair<Int, IntArray> {
        return Pair(contest.id, votes.toIntArray())
    }

    fun done() = builder
    fun ddone() = builder.builders
}

class CvrBuilder2(
    val id: String,
    val phantom: Boolean = false,
    val poolId: Int? = null,
) {
    val allVotes = mutableMapOf<Int, IntArray>()

    fun addContest(contestId: Int, votes: IntArray): CvrBuilder2  {
        allVotes[contestId] = votes
        return this
    }

    fun build() : Cvr {
        return Cvr(id, allVotes, phantom, poolId=poolId)
    }
}
