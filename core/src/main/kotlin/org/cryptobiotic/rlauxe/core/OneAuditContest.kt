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

        Nc = strata.sumOf { it.Ng }
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
        val assertions = mutableListOf<Pair<Assertion, Map<String, Double>>>()
        contest.winners.forEach { winner ->
            contest.losers.forEach { loser ->
                val baseAssorter = PluralityAssorter.makeWithVotes(this.contest, winner, loser, contestOA.votes)
                val batchAvgValues = contestOA.strata.map { stratum -> Pair(stratum.strataName, stratum.reportedMargin(winner, loser)) }
                assertions.add( Pair(Assertion( this.contest, baseAssorter), batchAvgValues.toMap()))
            }
        }

        // turn into comparison assertions
        this.comparisonAssertions = null
        assertions.map { (assertion: Assertion, batchAvgValues: Map<String, Double>) ->
            val margin = assertion.assorter.calcAssorterMargin(id, cvrs)
            ComparisonAssertion(contest, OneAuditComparisonAssorter(contest, assertion.assorter, margin2mean(margin), batchAvgValues))
        }

        return this
    }

    fun makePluralityAssertions(): List<Assertion> {
        // test that every winner beats every loser. SHANGRLA 2.1
        val assertions = mutableListOf<Assertion>()
        contest.winners.forEach { winner ->
            contest.losers.forEach { loser ->
                val baseAssorter = PluralityAssorter.makeWithVotes(this.contest, winner, loser, contestOA.votes)
                val batchAvgValues = contestOA.strata.map { stratum -> Pair(stratum.strataName, stratum.reportedMargin(winner, loser)) }
                assertions.add( Assertion( this.contest,
                    OneAuditComparisonAssorter(this.contest, baseAssorter, 1.0, batchAvgValues.toMap())
                ))
            }
        }
        return assertions
    }
}

data class CompositeAssorter(val info: ContestInfo, val winner: Int, val loser: Int, val upperBound: Double,
        val reportedMargin: Double,
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
        val useStratum = assorters[cvr.id]?: assorterCvr // find the correct stratum
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
        appendLine("  winner=$winner, loser=$loser, upperBound=$upperBound, reportedMargin=${df(reportedMargin)}")
        assorters.forEach {
            appendLine("  $it")
        }
    }
}

data class BatchAssorter(val contest: ContestIF, val winner: Int, val loser: Int, val reportedMargin: Double): AssorterFunction {

    override fun assort(mvr: Cvr, usePhantoms: Boolean): Double {
        if (!mvr.hasContest(contest.info.id)) return 0.5
        if (usePhantoms && mvr.phantom) return 0.0 // valid vote for every loser
        val w = mvr.hasMarkFor(contest.info.id, winner)
        val l = mvr.hasMarkFor(contest.info.id, loser)
        return (w - l + 1) * 0.5
    }

    override fun upperBound() = 1.0
    override fun desc() = " winner=$winner loser=$loser"
    override fun winner() = winner
    override fun loser() = loser
    override fun reportedMargin() = reportedMargin
}

// assorter_mean_all = (whitmer-schuette)/N
// v = 2*assorter_mean_all-1
// u_b = 2*u/(2*u-v)  # upper bound on the overstatement assorter
// noerror = u/(2*u-v)

// Let
//     Ā(c) ≡ Sum(A(ci))/N be the average CVR assort value
//     margin ≡ 2Ā(c) − 1, the _reported assorter margin_, (for 2 candidate plurality, aka the _diluted margin_).
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
    val contest: ContestIF,
    val assorter: AssorterFunction,   // A(mvr)
    val avgCvrAssortValue: Double,    // Ā(c) = average CVR assort value
    val batchAvgValues: Map<String, Double>,   // strataName -> assorter_mean_poll
) : ComparisonAssorterIF {
    val margin = 2.0 * avgCvrAssortValue - 1.0 // reported assorter margin
    val noerror = 1.0 / (2.0 - margin / assorter.upperBound())  // assort value when there's no error
    val upperBound = 2.0 * noerror  // maximum assort value

    val cvrComparisonAssorter = ComparisonAssorter(contest, assorter, avgCvrAssortValue)

    init {
        require(avgCvrAssortValue > 0.5) { "$contest: ($avgCvrAssortValue) avgCvrAssortValue must be > .5" }// the math requires this; otherwise divide by negative number flips the inequality
        require(noerror > 0.5) { "$contest: ($noerror) noerror must be > .5" }
   }

    override fun margin() = margin
    override fun noerror() = noerror
    override fun upperBound() = upperBound
    override fun assorter() = assorter

    fun calcAssorterMargin(cvrPairs: Iterable<Pair<Cvr, Cvr>>): Double {
        val mean = cvrPairs.filter{ it.first.hasContest(contest.id) }
            .map { bassort(it.first, it.second) }.average()
        return mean2margin(mean)
    }

    // B(bi, ci)
    override fun bassort(mvr: Cvr, cvr:Cvr): Double {
        val batchAvgValue = batchAvgValues[cvr.id]
        if (batchAvgValue == null) {
            return cvrComparisonAssorter.bassort(mvr, cvr)
        }

        // Let
        //     Ā(c) ≡ Sum(A(ci))/N be the average CVR assort value
        //     margin ≡ 2Ā(c) − 1, the _reported assorter margin_, (for 2 candidate plurality, aka the _diluted margin_).
        //
        //     ωi ≡ A(ci) − A(bi)   overstatementError
        //     τi ≡ (1 − ωi /upper) ≥ 0, since ωi <= upper
        //     B(bi, ci) ≡ τi / (2 − margin/upper) = (1 − ωi /upper) / (2 − margin/upper)
        //
        //     B assigns nonnegative numbers to ballots, and the outcome is correct iff Bavg > 1/2
        //     So, B is an assorter.

        val overstatement = overstatementError(mvr, cvr, batchAvgValue) // ωi
        val tau = (1.0 - overstatement / this.assorter.upperBound())
        return tau * noerror
    }

    //    overstatement error for a CVR compared to the human reading of the ballot.
    //    the overstatement error ωi for CVR i is at most the value the assorter assigned to CVR i.
    //
    //     ωi ≡ A(ci) − A(bi) ≤ A(ci) ≤ upper              ≡   overstatement error (SHANGRLA eq 2, p 9)
    //      bi is the manual voting record (MVR) for the ith ballot
    //      ci is the cast-vote record for the ith ballot
    //      A() is the assorter function
    //
    //        Phantom CVRs and MVRs are treated specially:
    //            A phantom CVR is considered a non-vote in every contest (assort()=1/2).
    //            A phantom MVR is considered a vote for the loser (i.e., assort()=0) in every contest.
    fun overstatementError(mvr: Cvr, cvr: Cvr, batchAvgValue: Double, hasStyle: Boolean = true): Double {


        //        # sanity check
        //        if use_style and not cvr.has_contest(self.contest.id):
        //            raise ValueError(
        //                f"use_style==True but {cvr=} does not contain contest {self.contest.id}"
        //            )
        if (hasStyle and !cvr.hasContest(contest.info.id)) {
            throw RuntimeException("use_style==True but cvr=${cvr} does not contain contest ${contest.info.name} (${contest.info.id})")
        }

        //        If use_style, then if the CVR contains the contest but the MVR does
        //        not, treat the MVR as having a vote for the loser (assort()=0)
        //
        //        mvr_assort = (
        //            0
        //            if mvr.phantom or (use_style and not mvr.has_contest(self.contest.id))
        //            else self.assort(mvr)
        val mvr_assort = if (mvr.phantom || (hasStyle && !mvr.hasContest(contest.info.id))) 0.0
        else this.assorter.assort(mvr, usePhantoms = false)

        //        If not use_style, then if the CVR contains the contest but the MVR does not,
        //        the MVR is considered to be a non-vote in the contest (assort()=1/2).
        //
        //        # assort the CVR
        //        cvr_assort = (
        //            self.tally_pool_means[cvr.tally_pool] // TODO cvr.tally_pool used for ONEAUDIT
        //            if cvr.pool and self.tally_pool_means is not None
        //            else int(cvr.phantom) / 2 + (1 - int(cvr.phantom)) * self.assort(cvr)
        //        )

        val cvr_assort = if (cvr.phantom) .5 else batchAvgValue
        return cvr_assort - mvr_assort
    }
}

