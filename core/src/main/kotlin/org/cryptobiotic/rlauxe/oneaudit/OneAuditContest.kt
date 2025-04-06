package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.math.min

class OneAuditContest (
    override val info: ContestInfo,
    val cvrVotes: Map<Int, Int>,   // candidateId -> nvotes;  sum is nvotes or V_c
    val cvrNc: Int,
    val pools: Map<Int, BallotPool>, // pool id -> pool
) : ContestIF {
    override val id = info.id
    val name = info.name
    override val choiceFunction = info.choiceFunction
    override val ncandidates = info.candidateIds.size

    val votes: Map<Int, Int>  // cand -> vote
    override val winnerNames: List<String>
    override val winners: List<Int>
    override val losers: List<Int>

    override val Nc: Int  // upper limit on number of ballots for all strata for this contest
    override val Np: Int  // number of phantom ballots for all strata for this contest

    override val undervotes: Int
    val minMargin: Double

    init {
        val poolNc = pools.values.sumOf { it.ncards }
        Nc = poolNc + cvrNc
        Np = 0 // TODO

        //// construct total votes, adding 0 votes if needed
        val voteBuilder = mutableMapOf<Int, Int>()  // cand -> vote
        cvrVotes.forEach { cand, votes ->
            val tvote = voteBuilder[cand] ?: 0
            voteBuilder[cand] = tvote + votes
        }
        pools.values.forEach { pool ->
            require(pool.contest == info.id)
            pool.votes.forEach { cand, votes ->
                val tvote = voteBuilder[cand] ?: 0
                voteBuilder[cand] = tvote + votes
            }
        }
        info.candidateIds.forEach {
            if (!voteBuilder.contains(it)) {
                voteBuilder[it] = 0
            }
        }
        votes = voteBuilder.toMap()

        //// find winners, check that the minimum value is satisfied
        // This works for PLURALITY, APPROVAL, SUPERMAJORITY.  IRV not supported I think
        val useMin = info.minFraction ?: 0.0
        val nvotes = votes.values.sum()
        val overTheMin = votes.toList().filter{ it.second.toDouble()/nvotes >= useMin }.sortedBy{ it.second }.reversed()
        val useNwinners = min(overTheMin.size, info.nwinners)
        winners = overTheMin.subList(0, useNwinners).map { it.first }
        val mapIdToName: Map<Int, String> = info.candidateNames.toList().associate { Pair(it.second, it.first) } // invert the map
        winnerNames = winners.map { mapIdToName[it]!! }

        // find losers
        val mlosers = mutableListOf<Int>()
        info.candidateNames.forEach { (_, id) ->
            if (!winners.contains(id)) mlosers.add(id)
        }
        losers = mlosers.toList()
        undervotes = Nc * info.nwinners - nvotes - Np

        val sortedVotes = votes.toList().sortedBy{ it.second }.reversed()
        minMargin = (sortedVotes[0].second - sortedVotes[1].second) / Nc.toDouble()
    }

    fun makeContestUnderAudit() : OAContestUnderAudit {
        val contestUA = OAContestUnderAudit(this)
        contestUA.makeClcaAssertions()
        return contestUA
    }

    fun makeContest() = Contest(info, votes, Nc, Np)

    fun reportedMargin(winner: Int, loser:Int): Double {
        val winnerVotes = votes[winner] ?: 0
        val loserVotes = votes[loser] ?: 0
        return (winnerVotes - loserVotes) / Nc.toDouble()
    }

    override fun toString() = buildString {
        appendLine("$name ($id) Nc=$Nc Np=$Np votes=${votes} minMargin=${df(minMargin)}")
    }
}

/////////////////////////////////////////////////////////////////////////////////////////////

class OAContestUnderAudit(
    val contestOA: OneAuditContest,
    isComparison: Boolean = true,
    hasStyle: Boolean = true
): ContestUnderAudit(contestOA.makeContest(), isComparison=isComparison, hasStyle=hasStyle) {

    override fun makeClcaAssertions(): ContestUnderAudit {
        // TODO assume its plurality for now
        val assertions = mutableListOf<Assertion>()
        contest.winners.forEach { winner ->
            contest.losers.forEach { loser ->
                val baseAssorter = PluralityAssorter.makeWithVotes(this.contest, winner, loser, contestOA.votes)
                assertions.add( Assertion( this.contest.info, baseAssorter))
            }
        }
        // turn into comparison assertions
        this.clcaAssertions = assertions.map { assertion ->
            val margin = assertion.assorter.reportedMargin()
            ClcaAssertion(contest.info, OAClcaAssorter(this.contestOA, assertion.assorter, margin2mean(margin)))
        }
        return this
    }

    // not used
    fun makeClcaAssertionsFromCvrs(cvrs: Iterator<Cvr>): ContestUnderAudit {
        // TODO assume its plurality for now
        val assertions = mutableListOf<Assertion>()
        contest.winners.forEach { winner ->
            contest.losers.forEach { loser ->
                val baseAssorter = PluralityAssorter.makeWithVotes(this.contest, winner, loser, contestOA.votes)
                assertions.add( Assertion( this.contest.info, baseAssorter))
            }
        }

        // calculate the assorter means all at once
        val asnWelford = assertions.map { Pair(it, Welford())}.toMap()
        while (cvrs.hasNext()) {
            val cvr = cvrs.next()
            asnWelford.forEach { (asn, welford) ->
                welford.update(asn.assorter.assort(cvr))
            }
        }

        // turn into comparison assertions
        this.clcaAssertions = asnWelford.map { (asn, welford) ->
            val cvrMean = if (welford.count == 0) mean2margin(asn.assorter.reportedMargin()) else welford.mean
            println("  margin for ${asn.assorter.desc()} reportedMargin ${asn.assorter.reportedMargin()}, cvrMargin = ${ mean2margin(cvrMean) }")
            ClcaAssertion(contest.info, OAClcaAssorter(this.contestOA, asn.assorter, cvrMean))
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

data class OAClcaAssorter(
    val contestOA: OneAuditContest,
    val assorter: AssorterIF,   // A(mvr)
    val avgCvrAssortValue: Double,    // Ā(c) = average CVR assorter value
) : ClcaAssorterIF {
    val cassorter = ClcaAssorter(contestOA.info, assorter, avgCvrAssortValue) // TODO subclass and override bassort ??

    override fun noerror() = cassorter.noerror
    override fun upperBound() = cassorter.upperBound
    override fun meanAssort() = cassorter.meanAssort()
    override fun assorter() = assorter
    override fun id() = contestOA.id

    // B(bi, ci)
    override fun bassort(mvr: Cvr, cvr: Cvr): Double {
        if (cvr.poolId == null) {
            return cassorter.bassort(mvr, cvr)
        }
        val pool = contestOA.pools[cvr.poolId]
        if (pool == null) { throw IllegalStateException("Dont have pool ${cvr.poolId} in contest ${contestOA.id}") }

        val avgBatchAssortValue = margin2mean(pool.calcReportedMargin(assorter.winner(), assorter.loser()))
        val overstatement = overstatementPoolError(mvr, cvr, avgBatchAssortValue) // ωi
        val tau = (1.0 - overstatement / this.assorter.upperBound())
        // had error using the pool margin instead of the assorter margin
        //val margin = 2.0 * avgBatchAssortValue - 1.0 // reported assorter margin
        //val noerror = 1.0 / (2.0 - margin / assorter.upperBound())  // assort value when there's no error
        val result =  tau * cassorter.noerror
        return result
    }

    fun overstatementPoolError(mvr: Cvr, cvr: Cvr, avgBatchAssortValue: Double, hasStyle: Boolean = true): Double {
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
    }
}

