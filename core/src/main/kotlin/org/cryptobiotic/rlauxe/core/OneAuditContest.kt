package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.sampling.ContestSimulation
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.mean2margin
import kotlin.math.min

// TODO regular Contest is a special case of ContestOA with only one stratum?
// TODO is it better to always use "batches" because Nc may be smaller ?? just for nostyle??
class OneAuditContest (
    override val info: ContestInfo,
    val strata: List<OneAuditStratum>,
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
    val stratumCvr : OneAuditStratum

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

        stratumCvr = strata.find { it.hasCvrs }!!
    }

    fun makeContestUnderAudit(cvrs: List<Cvr>) = OneAuditContestUnderAudit(this).makeComparisonAssertions(cvrs)

    fun makeContest() = Contest(info, votes, Nc, Np)

    fun makeTestCvrs(): List<Cvr> {
        val cvrs = mutableListOf<Cvr>()
        strata.forEach { stratum ->
            if (stratum.hasCvrs) {
                val sim = ContestSimulation(stratum.contest)
                cvrs.addAll(sim.makeCvrs()) // makes a new, independent set of simulated Cvrs with the contest's votes, undervotes, and phantoms.
            } else {
                cvrs.addAll(stratum.makeFakeCvrs())
            }
        }
        cvrs.shuffle()
        return cvrs
    }

    fun reportedMargin(winner: Int, loser:Int): Double {
        val winnerVotes = votes[winner] ?: 0
        val loserVotes = votes[loser] ?: 0
        return (winnerVotes - loserVotes) / Nc.toDouble()
    }

    override fun toString() = buildString {
        append("$name ($id) Nc=$Nc Np=$Np votes=${votes} minMargin=${df(minMargin)}")
    }
}

class OneAuditStratum (
    val strataName: String,
    val hasCvrs: Boolean,
    val info: ContestInfo,
    val votes: Map<Int, Int>,   // candidateId -> nvotes
    val Nc: Int,  // upper limit on number of ballots in this strata for this contest
    val Np: Int,  // number of phantom ballots in this strata for this contest
) {
    val contest = Contest(info.copy(name=strataName), votes, Nc, Np)

    init {
        votes.forEach {
            require(info.candidateIds.contains(it.key)) { "'${it.key}' not found in contestInfo candidateIds ${info.candidateIds}"}
        }
    }

    fun makeFakeCvrs(): List<Cvr> {
        val cvrs = mutableListOf<Cvr>()
        votes.forEach { (candId, nvotes) ->
            repeat(nvotes) { cvrs.add(makeCvr(info.id, candId)) }
        }
        // undervotes
        val nu = this.Nc - cvrs.size
        repeat(nu) {
            cvrs.add(makeCvr(info.id))
        }
        // TODO phantoms
        return cvrs
    }

    fun makeCvr(contestId: Int, winner: Int? = null): Cvr {
        val votes = mutableMapOf<Int, IntArray>()
        votes[contestId] = if (winner != null) intArrayOf(winner) else IntArray(0)
        return Cvr(strataName, votes) // TODO ok to glom onto cvr.id to indicate statum ??
    }
}

fun MutableMap<Int, Int>.mergeReduce(others: List<Map<Int, Int>>) =
    others.forEach { other -> other.forEach { merge(it.key, it.value) { a, b -> a + b } } }

/////////////////////////////////////////////////////////////////////////////////////////////

class OneAuditContestUnderAudit(
    val contestOA: OneAuditContest,
): ContestUnderAudit(contestOA.makeContest(), isComparison=true, hasStyle=true) {

    override fun makeComparisonAssertions(cvrs : Iterable<Cvr>): ContestUnderAudit {
        // primitive assertions
        val assertions = when (contest.info.choiceFunction) {
            SocialChoiceFunction.APPROVAL,
            SocialChoiceFunction.PLURALITY, -> makePluralityAssertions() // TODO supermaj
            else -> throw RuntimeException("choice function ${contest.info.choiceFunction} is not supported")
        }

        // turn into comparison assertions
        this.comparisonAssertions = assertions.map { assertion ->
            val margin = assertion.assorter.calcAssorterMargin(id, cvrs)
            val comparisonAssorter = ComparisonAssorter(contest, assertion.assorter, margin2mean(margin), hasStyle=hasStyle)
            ComparisonAssertion(contest, comparisonAssorter)
        }

        return this
    }

    fun makePluralityAssertions(): List<Assertion> {
        // test that every winner beats every loser. SHANGRLA 2.1
        val assertions = mutableListOf<Assertion>()
        contest.winners.forEach { winner ->
            contest.losers.forEach { loser ->
                var assorterCvr : AssorterFunction? = null
                val strataAssorters = contestOA.strata.map { stratum ->
                    val assorter = PluralityAssorter.makeWithVotes(stratum.contest, winner, loser, stratum.votes) // TODO wrong
                    if (stratum == contestOA.stratumCvr) assorterCvr = assorter // TODO not always one??
                    Pair(stratum.strataName, assorter)
                }
                assertions.add( Assertion( this.contest,
                    CompositeAssorter(this.contest.info, winner, loser, 1.0,
                        contestOA.reportedMargin(winner, loser),
                        strataAssorters.toMap(),
                        assorterCvr!!)) )
            }
        }
        return assertions
    }
}

data class CompositeAssorter(val info: ContestInfo, val winner: Int, val loser: Int, val upperBound: Double,
        val reportedMargin: Double, // TODO where do we get this?
        val assorters: Map<String, AssorterFunction>,
        val assorterCvr: AssorterFunction,
    ): AssorterFunction {

    override fun upperBound() = upperBound
    override fun winner() = winner
    override fun loser() = loser
    override fun reportedMargin() = reportedMargin
    override fun desc() = buildString {
        append("CompositeAssorter winner/loser=${winner}/${loser}")
    }

    override fun assort(cvr: Cvr, usePhantoms: Boolean): Double {
        val useStratum = assorters[cvr.id]?: assorterCvr // find the correct stratum TODO cvr stratum
        val result =  useStratum.assort(cvr, usePhantoms)
        //val id = (useStratum as PluralityAssorter).contest.info.name
        //println(" assort with ${id} got result $result")
        return result
    }

    // TODO need to filter strata?
    override fun calcAssorterMargin(contestId: Int, cvrs: Iterable<Cvr>): Double {
        val mean = cvrs.filter{ it.hasContest(contestId) }
            .map { assort(it, usePhantoms = false) }.average()
        return mean2margin(mean)
    }

    override fun toString() = buildString {
        appendLine("CompositeAssorter: info=$info")
        appendLine("  winner=$winner, loser=$loser, upperBound=$upperBound, reportedMargin=$reportedMargin")
        assorters.forEach {
            appendLine("  $it")
        }
    }


}


