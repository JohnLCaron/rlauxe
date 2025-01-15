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

        Nc = strata.sumOf { it.Ng }
        Np = strata.sumOf { it.Np }
        require(nvotes <= Nc) { "Nc $Nc must be >= totalVotes ${nvotes}"}

        val sortedVotes = votes.toList().sortedBy{ it.second }.reversed()
        minMargin = (sortedVotes[0].second - sortedVotes[1].second) / Nc.toDouble()
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
        appendLine("$name ($id) Nc=$Nc Np=$Np votes=${votes} minMargin=${df(minMargin)}")
        strata.forEach {
            appendLine("$it")
        }
    }
}

class OneAuditStratum (
    val strataName: String,
    val hasCvrs: Boolean,
    val info: ContestInfo,
    val votes: Map<Int, Int>,   // candidateId -> nvotes
    val Ng: Int,  // upper limit on number of ballots in this strata for this contest
    val Np: Int,  // number of phantom ballots in this strata for this contest
) {
    val contest = Contest(info.copy(name=strataName), votes, Ng, Np)

    init {
        votes.forEach {
            require(info.candidateIds.contains(it.key)) { "'${it.key}' not found in contestInfo candidateIds ${info.candidateIds}"}
        }
    }

    fun reportedMargin(winner: Int, loser:Int): Double {
        val winnerVotes = votes[winner] ?: 0
        val loserVotes = votes[loser] ?: 0
        return (winnerVotes - loserVotes) / Ng.toDouble()
    }

    fun makeFakeCvrs(): List<Cvr> {
        val cvrs = mutableListOf<Cvr>()
        votes.forEach { (candId, nvotes) ->
            repeat(nvotes) { cvrs.add(makeCvr(info.id, candId)) }
        }
        // undervotes
        val nu = this.Ng - cvrs.size
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

    override fun toString() = buildString {
        append("  strata $strataName hasCvrs=$hasCvrs Nc=$Ng Np=$Np votes=${votes}")
    }
}

fun MutableMap<Int, Int>.mergeReduce(others: List<Map<Int, Int>>) =
    others.forEach { other -> other.forEach { merge(it.key, it.value) { a, b -> a + b } } }

/////////////////////////////////////////////////////////////////////////////////////////////

class OneAuditContestUnderAudit(
    val contestOA: OneAuditContest,
): ContestUnderAudit(contestOA.makeContest(), isComparison=true, hasStyle=true) {

    override fun makeComparisonAssertions(cvrs : Iterable<Cvr>): ContestUnderAudit {
        // TODO assume its plurality for now
        val assertions = mutableListOf<Assertion>()
        contest.winners.forEach { winner ->
            contest.losers.forEach { loser ->
                val baseAssorter = PluralityAssorter.makeWithVotes(this.contest, winner, loser, contestOA.votes)
                assertions.add( Assertion( this.contest, baseAssorter))
            }
        }
        // turn into comparison assertions
        this.comparisonAssertions = assertions.map { assertion ->
            val margin = assertion.assorter.calcAssorterMargin(id, cvrs)
            ComparisonAssertion(contest, OneAuditComparisonAssorter(this.contestOA, assertion.assorter, margin2mean(margin)))
        }
        return this
    }
}

// "assorter" here is the plurality assorter
// from oa_polling.ipynb
// assorter_mean_all = (whitmer-schuette)/N
// v = 2*assorter_mean_all-1
// u_b = 2*u/(2*u-v)  # upper bound on the overstatement assorter
// noerror = u/(2*u-v)

// OneAudit, section 2.3
// "compares the manual interpretation of individual cards to the implied “average” CVR of the reporting batch each card belongs to"
//
// Let bi denote the true votes on the ith ballot card; there are N cards in all.
// Let ci denote the voting system’s interpretation of the ith card, for ballots in C, cardinality |C|.
// Ballot cards not in C are partitioned into G ≥ 1 disjoint groups {G_g}, g=1..G for which reported assorter subtotals are available.
//
//     Ā(c) ≡ Sum(A(ci))/N be the average CVR assort value
//     margin ≡ 2Ā(c) − 1, the _reported assorter margin_
//
//     ωi ≡ A(ci) − A(bi)   overstatementError
//     τi ≡ (1 − ωi /upper) ≥ 0, since ωi <= upper
//     B(bi, ci) ≡ τi / (2 − margin/upper) = (1 − ωi /upper) / (2 − margin/upper)

//    Ng = |G_g|
//    Ā(g) ≡ assorter_mean_poll = (winner total - loser total) / Ng
//    margin ≡ 2Ā(g) − 1 ≡ v = 2*assorter_mean_poll − 1
//    mvr has loser vote = (1-assorter_mean_poll)/(2-v/u)
//    mvr has winner vote = (2-assorter_mean_poll)/(2-v/u)
//    otherwise = 1/2

data class OneAuditComparisonAssorter(
    val contestOA: OneAuditContest,
    val assorter: AssorterFunction,   // A(mvr)
    val avgCvrAssortValue: Double,    // Ā(c) = average CVR assorter value TODO why?
) : ComparisonAssorterIF {
    val stratumInfos: Map<String, StratumInfo>   // strataName -> average batch assorter value
    val clcaMargin: Double // estimated assorter mean, if all cards agree; used for alphaMart
    var cvrStrata: StratumInfo? = null

    // TODO what is this used for??
    private val margin = 2.0 * avgCvrAssortValue - 1.0 // reported assorter margin
    val noerror = 1.0 / (2.0 - margin / assorter.upperBound())  // assort value when there's no error
    val upperBound = 2.0 * noerror  // maximum assort value
    override fun noerror() = noerror
    override fun upperBound() = upperBound
    override fun assorter() = assorter

    init {
        var weightedMeanAssortValue = 0.0
        stratumInfos = contestOA.strata.map { stratum ->
            val stratumMargin = stratum.reportedMargin(assorter.winner(), assorter.loser())
            val avgBatchAssortValue = margin2mean(stratumMargin)

            if (stratum.hasCvrs) {
                val cassorter = ComparisonAssorter(contestOA.makeContest(), assorter, avgBatchAssortValue)
                weightedMeanAssortValue += cassorter.noerror * stratum.Ng
                Pair(stratum.strataName, StratumInfo(avgBatchAssortValue, cassorter))
            } else {
                val stratumNoError = 1.0 / (2.0 - stratumMargin / assorter.upperBound())
                weightedMeanAssortValue += stratumNoError * stratum.Ng
                Pair(stratum.strataName, StratumInfo(avgBatchAssortValue, null))
            }
        }.toMap()
        val clcaMean = weightedMeanAssortValue / contestOA.Nc
        clcaMargin = mean2margin(clcaMean)
        cvrStrata = stratumInfos.values.find { it.cassorter != null }
    }

    fun calcAssorterMargin(cvrPairs: Iterable<Pair<Cvr, Cvr>>): Double {
        val mean = cvrPairs.filter{ it.first.hasContest(contestOA.id) }
            .map { bassort(it.first, it.second) }.average()
        return mean2margin(mean)
    }

    // B(bi, ci)
    override fun bassort(mvr: Cvr, cvr:Cvr): Double {
        val stratumInfo = stratumInfos[cvr.id]
        if (stratumInfo == null) {
            if (cvrStrata != null) {
                return cvrStrata!!.cassorter!!.bassort(mvr, cvr)
            } else {
                throw IllegalStateException()
            }
        }
        if (stratumInfo.cassorter != null) {
            return stratumInfo.cassorter.bassort(mvr, cvr)
        }

        val avgBatchAssortValue = stratumInfo.avgBatchAssortValue
        val overstatement = overstatementError(mvr, cvr, avgBatchAssortValue) // ωi
        val tau = (1.0 - overstatement / this.assorter.upperBound())
        val margin = 2.0 * avgBatchAssortValue - 1.0 // reported assorter margin
        val noerror = 1.0 / (2.0 - margin / assorter.upperBound())  // assort value when there's no error
        val result =  tau * noerror
        return result
    }

    fun overstatementError(mvr: Cvr, cvr: Cvr, avgBatchAssortValue: Double, hasStyle: Boolean = true): Double {
        if (hasStyle and !cvr.hasContest(contestOA.id)) {
            throw RuntimeException("use_style==True but cvr=${cvr} does not contain contest ${contestOA.name} (${contestOA.id})")
        }
        val mvr_assort = if (mvr.phantom || (hasStyle && !mvr.hasContest(contestOA.id))) 0.0
                         else this.assorter.assort(mvr, usePhantoms = false)

        val cvr_assort = if (cvr.phantom) .5 else avgBatchAssortValue // TODO phantom probaly not used
        return cvr_assort - mvr_assort
    }

    override fun toString() = buildString {
        appendLine("OneAuditComparisonAssorter for contest ${contestOA.name} (${contestOA.id})")
        appendLine("  assorter=${assorter.desc()}")
        appendLine("  avgCvrAssortValue=$avgCvrAssortValue")
        appendLine("  batchAvgValues=${stratumInfos.map { (key, value) -> "$key = ${value.avgBatchAssortValue}" }}")
    }
}

data class StratumInfo(val avgBatchAssortValue: Double, val cassorter: ComparisonAssorter?)

