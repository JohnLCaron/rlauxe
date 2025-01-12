package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.sampling.ContestSimulation
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.workflow.AuditType
import kotlin.math.min

class ContestOA (
    override val info: ContestInfo,
    val strata: List<ContestStratum>,
) : ContestIF {
    override val id = info.id
    val name = info.name
    override val choiceFunction = info.choiceFunction
    override val ncandidates = info.candidateIds.size

    val votes: Map<Int, Int>
    override val winnerNames: List<String>
    override val winners: List<Int>
    override val losers: List<Int>

    override val Nc: Int  // upper limit on number of ballots for all strata for this contest
    override val Np: Int  // number of phantom ballots for all strata for this contest
    val minMargin: Double  // TODO should we remove Np in this calculation? Do we need this?

    init {
        val svotes = strata.map { it.votes }
        val voteBuilder = mutableMapOf<Int, Int>()
        voteBuilder.mergeReduce(svotes)

        // add 0 votes if needed
        info.candidateIds.forEach {
            if (!voteBuilder.contains(it)) {
                voteBuilder[it] = 0
            }
        }
        votes = voteBuilder.toMap()

        //// find winners, check that the minimum value is satisfied
        // This works for PLURALITY, APPROVAL, SUPERMAJORITY.  IRV handled by RaireContest
        val useMin = info.minFraction ?: 0.0
        val nvotes = votes.values.sum() // this is plurality of the votes, not of the cards or the ballots

        // todo why use totalVotes instead of Nc?
        val overTheMin = votes.toList().filter{ it.second.toDouble()/nvotes >= useMin }.sortedBy{ it.second }.reversed()
        val useNwinners = min(overTheMin.size, info.nwinners)
        winners = overTheMin.subList(0, useNwinners).map { it.first }
        // invert the map
        val mapIdToName: Map<Int, String> = info.candidateNames.toList().associate { Pair(it.second, it.first) }
        winnerNames = winners.map { mapIdToName[it]!! }

        // find losers
        val mlosers = mutableListOf<Int>()
        // could require that all candidates are in votes, but this way, it allows candidates with no votes
        info.candidateNames.forEach { (_, id) ->
            if (!winners.contains(id)) mlosers.add(id)
        }
        losers = mlosers.toList()

        Nc = strata.sumOf { it.Nc }
        Np = strata.sumOf { it.Np }
        require(nvotes <= Nc) { "Nc $Nc must be >= totalVotes ${nvotes}"}

        val sortedVotes = votes.toList().sortedBy{ it.second }.reversed()
        minMargin = (sortedVotes[0].second - sortedVotes[1].second) / Nc.toDouble()
    }

    fun makeContestUnderAudit(cvrs: List<Cvr>): ContestUnderAudit {
        strata.forEach { it.makeContestUnderAudit(cvrs)}
        val contest = Contest(info, votes, Nc, Np)
        val isComparison = cvrs.isNotEmpty()
        val contestUA = ContestUnderAudit(contest, isComparison = isComparison)
        if (isComparison) contestUA.makeComparisonAssertions(cvrs) else contestUA.makePollingAssertions()
        return contestUA
    }

    fun makeContest() = Contest(info, votes, Nc, Np)

    fun makeCvrs(): List<Cvr> {
        val cvrs = mutableListOf<Cvr>()
        strata.forEach { stratum ->
            if (stratum.strataType == AuditType.CARD_COMPARISON) {
                val sim = ContestSimulation(stratum.makeContest())
                cvrs.addAll(sim.makeCvrs())
            } else {
                cvrs.addAll(stratum.makeCvrs())
            }
        }
        return cvrs
    }

    override fun toString() = buildString {
        append("$name ($id) Nc=$Nc Np=$Np votes=${votes} minMargin=${df(minMargin)}")
    }
}

class ContestStratum (
    val strataName: String,
    val strataType: AuditType, // Comparison (has Cvrs) or Polling (no Cvrs). maybe
    val info: ContestInfo,
    val votes: Map<Int, Int>,   // candidateId -> nvotes
    val Nc: Int,  // upper limit on number of ballots in this strata for this contest
    val Np: Int,  // number of phantom ballots in this strata for this contest
) {
    init {
        votes.forEach {
            require(info.candidateIds.contains(it.key)) { "'${it.key}' not found in contestInfo candidateIds ${info.candidateIds}"}
        }
    }

    fun makeContest() = Contest(info, votes, Nc, Np)

    fun makeContestUnderAudit(cvrs: List<Cvr>): ContestUnderAudit {
        val contest = makeContest()
        val isComparison = strataType == AuditType.CARD_COMPARISON
        val contestUA = ContestUnderAudit(contest, isComparison = isComparison)
        if (isComparison) contestUA.makeComparisonAssertions(cvrs) else contestUA.makePollingAssertions()
        return contestUA
    }

    fun makeCvrs(): List<Cvr> {
        val cvrs = mutableListOf<Cvr>()
        votes.forEach { (candId, nvotes) ->
            repeat(nvotes) {
                cvrs.add(makeCvr(info.id, candId))
            }
        }
        val nu = this.Nc - cvrs.size
        repeat(nu) {
            cvrs.add(makeCvr(info.id))
        }
        return cvrs
    }

    fun makeCvr(contestId: Int, winner: Int? = null): Cvr {
        val votes = mutableMapOf<Int, IntArray>()
        votes[contestId] = if (winner != null) intArrayOf(winner) else IntArray(0)
        return Cvr(strataName, votes)
    }
}

fun MutableMap<Int, Int>.mergeReduce(others: List<Map<Int, Int>>) =
    others.forEach { other -> other.forEach { merge(it.key, it.value) { a, b -> a + b } } }



