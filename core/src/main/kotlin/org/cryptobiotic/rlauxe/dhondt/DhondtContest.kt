package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*


// ### Section 5.1 highest averages from Proportional paper
//
//A highest averages method is parameterized by a set of divisors d(1), d(2), . . . d(S) where S is the number of seats.
//The divisors for D’Hondt are d(i) = i. Sainte-Laguë has divisors d(i) = 2i − 1.
//
//Define
//
//    fe,s = Te/d(s) for entity e and seat s.
//
// ### Section 5.2 Simple D’Hondt: Party-only voting
//
//In the simplest form of highest averages methods, seats are allocated to each
//entity (party) based on individual entity tallies. Let We be the number of seats
//won and Le the number of the first seat lost by entity e. That is:
//
//    We = max{s : (e, s) ∈ W}; ⊥ if e has no winners. this is e's lowest winner.
//    Le = min{s : (e, s) !∈ W}; ⊥ if e won all the seats. this is e's highest loser.
//
//The inequalities that define the winners are, for all parties A with at least
//one winner, for all parties B (different from A) with at least one loser, as follows:
//
//    fA,WA > fB,LB    A’s lowest winner beat party B’s highest loser
//    TA/d(WA) > TB/d(LB)
//    TA/d(WA) - TB/d(LB) > 0
//
//From this, we define the proto-assorter for any ballot b as
//
//    g_AB(b) = 1/d(WA) if b is a vote for A
//            = -1/d(WB) if b is a vote for B
//            = 0 otherwisa
//
//    or equivilantly, g_AB(b) = bA/d(WA) - bB/d(WB)
//
//g lower bound is -1/d(WB) = -1/first (lowest winner)
//g upper bound is 1/d(WA)  = 1/last   (highest loser)
//c = -1.0 / (2 * lower) = first/2
//h upper bound is h(g upper) = h(1/last) * c + 1/2 = (1/last) * first/2 + 1/2 = (first/last+1)/2
//
//first and last both range from 1 to nseats, so
//    min upper is (1/nseats + 1)/2 which is between 1/2 and 1
//    max upper is (nseats + 1)/2 which is >= 1


/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
class DHondtContest(
    info: ContestInfo,
    voteInput: Map<Int, Int>,   // candidateId -> nvotes;  sum is nvotes or V_c
    Nc: Int,                    // trusted maximum ballots/cards that contain this contest
    Ncast: Int,                 // number of cast ballots containing this Contest, including undervotes
    val parties: List<DhondtCandidate>,     // the candidate parties
    val sortedScores: List<DhondtScore>,
): Contest(info, voteInput, Nc, Ncast) {
    val nvotes = votes.values.sum()

    override fun Nc() = Nc
    override fun Nphantoms() = Nc - Ncast
    override fun Nundervotes() = undervotes
    override fun info() = info
    override fun winnerNames() = winnerNames
    override fun winners() = winners
    override fun losers() = losers

    val nseats: Int
    val partiesBelowThreshold: Set<Int> // candidateIds under minFraction
    val winnerSeats: Map<Int, Int>
    // dhondts and threshold assorters; these are set at creation, but not serialized, so cant assume they exist
    // can we put the generation of these inside? problem is ContestUA is serialized seperately, would have to rejigger that
    val assorters = mutableListOf<AssorterIF>()

    init {
        require(info.minFraction != null)
        nseats = info.nwinners
        val nvotes = votes.values.sum()

        // "A winning candidate must have a minimum fraction f ∈ (0, 1) of the valid votes to win". assume that means nvotes, not Nc.
        partiesBelowThreshold = parties.filter { it.votes / nvotes.toDouble() < info.minFraction }.map { it.id }.toSet()

        // last / first
        val winnerScores = sortedScores.subList(0, nseats)
        val loserScores = sortedScores.subList(nseats, sortedScores.size)
        parties.forEach { party ->
            party.lastSeatWon = winnerScores.filter { it.candidate == party.id }.maxOfOrNull { it.divisor }
            party.firstSeatLost = loserScores.filter { it.candidate == party.id }.minOfOrNull { it.divisor }
        }

        val winnerSeatsM= mutableMapOf<Int, Int>()
        sortedScores.filter { it.winningSeat != null }.forEach {
            val count = winnerSeatsM.getOrPut(it.candidate) { 0 }
            winnerSeatsM[it.candidate] = count + 1
        }
        winnerSeats = winnerSeatsM.toMap()
        // fields in superclass
        winners = winnerSeats.keys.toList()
        losers = info.candidateIds.filter { !winners.contains(it) }
        winnerNames = winners.map { info.candidateIdToName[it]!! }
    }

    override fun recountMargin(assorter: AssorterIF): Double {
        return when (assorter) {
            is DHondtAssorter -> {
                val winnerScore = votes[assorter.winner()]!! / assorter.lastSeatWon.toDouble()
                val loserScore = votes[assorter.loser()]!! / assorter.firstSeatLost.toDouble()
                (winnerScore - loserScore) / winnerScore
            }
            is BelowThreshold -> {
                // val nvotes = votes.values.sum() does not include undervotes ??
                assorter.t - votes[assorter.winner()]!! / nvotes.toDouble()
            }

            is AboveThreshold -> {
                // val nvotes = votes.values.sum() does not include undervotes
                votes[assorter.winner()]!! / nvotes.toDouble() - assorter.t
            }
            else -> throw RuntimeException()
        }
    }

    override fun showAssertionDifficulty(assorter: AssorterIF): String {
        return when (assorter) {
            is DHondtAssorter -> {
                assorter.showAssertionDifficulty(votes[assorter.winner()]!!, votes[assorter.loser()]!!)
            }
            is BelowThreshold -> {
                // val nvotes = votes.values.sum() does not include undervotes
                assorter.showAssertionDifficulty(votes[assorter.winner()]!!, nvotes)
            }
            is AboveThreshold -> {
                // val nvotes = votes.values.sum() does not include undervotes
                assorter.showAssertionDifficulty(votes[assorter.winner()]!!, nvotes)
            }
            else -> throw RuntimeException()
        }
    }

    fun difficulty(assorter: AssorterIF): Double {
        return when (assorter) {
            is DHondtAssorter -> {
                assorter.voteDiff(votes[assorter.winner()]!!, votes[assorter.loser()]!!)
            }
            is BelowThreshold -> {
                // val nvotes = votes.values.sum() does not include undervotes
                assorter.difficulty(votes[assorter.winner()]!!, nvotes)
            }
            is AboveThreshold -> {
                // val nvotes = votes.values.sum() does not include undervotes
                assorter.difficulty(votes[assorter.winner()]!!, nvotes)
            }
            else -> throw RuntimeException("unknown assorter type= ${assorter.javaClass.simpleName}")
        }
    }

    override fun marginInVotes(assorter: AssorterIF): Int {
        return when (assorter) {
            is DHondtAssorter -> {
                roundToClosest(assorter.voteDiff(votes[assorter.winner()]!!, votes[assorter.loser()]!!))
            }
            is BelowThreshold -> {
                roundToClosest(assorter.t * nvotes- votes[assorter.winner()]!!)
            }
            is AboveThreshold -> {
                roundToClosest(votes[assorter.winner()]!! - assorter.t * nvotes)
            }
            else -> throw RuntimeException("unknown assorter type= ${assorter.javaClass.simpleName}")
        }
    }

    override fun show() = buildString {
        appendLine(super.show())
        append("   nseats=$nseats winnerSeats=${winnerSeats} belowMin=${partiesBelowThreshold} threshold=${info.minFraction} minVotes=${roundUp(info.minFraction!! * nvotes)}")
    }

    override fun showCandidates() = buildString {
        val width0 = 20
        val width = 12
        val maxRound = sortedScores.filter{ it.winningSeat != null }.maxOfOrNull { it.divisor }!! + 1

        appendLine()
        append("candidate ${trunc("Round", width0 - "candidate".length + 3)}:")
        for (round in 1 .. maxRound) {
            append("${nfn(round, width)} |")
        }
        appendLine()

        info.candidateNames.toSortedMap().forEach { (name, id) ->
            val rounds = sortedScores.filter { it.candidate == id }.map { Dround(id, it.score, it.divisor, it.winningSeat) }
            val below = if (partiesBelowThreshold.contains(id)) "*" else " "
            val candName = "${nfn(id, 2)} ${trunc(info.candidateIdToName[id]!!, width0)}$below"
            append(showCandidate(candName, votes[id]!!, maxRound, rounds, width))
        }
        appendLine("\n* failed threshold")
    }

    fun showCandidate(candName: String, candTotal: Int, maxRound: Int, rounds: List<Dround>, width:Int) = buildString {
        append("${candName}:")
        for (round in 1 .. maxRound) {
            val candRound = rounds.find { it.round == round }
            if (candRound == null) {
                val score = candTotal / round
                append("${nfn(score, width)} |")
                // append("${trunc(" ", width)}|")
            } else {
                val winner = if (candRound.winningSeat != null) " (${candRound.winningSeat})" else ""
                val entry = "${candRound.score.toInt()}$winner"
                append("${sfn(entry, width)} |")
            }
        }
        appendLine()
    }

    data class Dround(val candId: Int, val score: Double, val round: Int, val winningSeat: Int?)

    // this needs to be recalculated as the mvrLimit might change
/*     private var candSeats: CandSeatRangeBuilder2? = null
    private fun getCandSeats(contestRound: ContestRound): CandSeatRangeBuilder2 {
        if (candSeats != null) return candSeats!!
        candSeats = CandSeatRangeBuilder2(contestRound)
        return candSeats!!
    } */

    fun showRelaxedAssertions(contestRound: ContestRound): String {
        val cands = CandSeatRangeBuilder(contestRound)
        val relax = RelaxedAssertionReport(cands)
        return relax.showRelaxedAssertions()
    }

    // show altContests tree with this assertion as the root
    fun showRelaxedAssertion(contestRound: ContestRound, cassertion: ClcaAssertion): String {
        return ""
        /* val cands = CandSeatRangeBuilder(contestRound)
        val relax = RelaxedAssertionReport(cands)
        val failure = cands.failureNodes.find { it.assorter == cassertion.assorter }!!
        val altContest = cands.makeAltContest(this, failure)

        val done = mutableSetOf<String>()
        val result = relax.showAltFailureContestRecurse(altContest, done)
        println("assertions done:")
        done.forEach{ println("   $it") }
        return result */
    }

    fun countContestedSeats(contestRound: ContestRound): Int {
        val cands = CandSeatRangeBuilder(contestRound)
        return 0 // cands.failureNodes.size + cands.thrashers.size
    }

    fun showContestedSeats(contestRound: ContestRound): Pair<Int, String> {
        val relax = RelaxedAssertionsOld(this, contestRound)
        return relax.contestedSeatReport()
    }

    //// create a cvr for each vote
    fun createSimulatedCvrs(): List<Cvr> {
        val cvrs = mutableListOf<Cvr>()
        var count=0
        this.votes.forEach { (candid, nvotes) ->
            repeat(nvotes) {
                count++
                cvrs.add( Cvr("cvr$count", mapOf(id to intArrayOf(candid)), poolId=info.id))
            }
        }
        repeat(undervotes) {
            count++
            cvrs.add( Cvr("undervote$count", mapOf(id to IntArray(0)), poolId=info.id))
        }
        repeat(Nphantoms()) {
            count++
            cvrs.add( Cvr("phantom$count", mapOf(id to IntArray(0)), poolId=info.id, phantom=true))
        }
        cvrs.shuffle()
        return cvrs
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DHondtContest) return false
        if (!super.equals(other)) return false

        if (sortedScores != other.sortedScores) return false
        if (partiesBelowThreshold != other.partiesBelowThreshold) return false
        if (winnerSeats != other.winnerSeats) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + sortedScores.hashCode()
        result = 31 * result + partiesBelowThreshold.hashCode()
        result = 31 * result + winnerSeats.hashCode()
        return result
    }

    companion object {

        fun fromVotes(info: ContestInfo, votes: Map<Int, Int>, Nc: Int, Ncast: Int, belowMinPctIn: Set<Int>?): DHondtContest {
            // recreate the parties from the votes
            val parties = info.candidateIds.map { id ->
                DhondtCandidate(info.candidateIdToName[id]!!, id, votes[id]!!)
            }
            val sortedScoresCalc = assignWinners(parties, info.nwinners, Nc, info.minFraction!!, belowMinPctIn)

            return DHondtContest(info, votes, Nc, Ncast, parties, sortedScoresCalc)
        }

    }
}