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

// immutable
data class DhondtParty(val partyName: String, val id: Int, val totalVotes: Int,
     val lastSeatWon: Int, val firstSeatLost: Int, val isBelowMin: Boolean, val nCandidates: Int) {

    override fun toString() = "DhondtParty('$partyName' ($id) votes=$totalVotes below=$isBelowMin last/first=$lastSeatWon/$firstSeatLost)"
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// TODO make constructor private, always use Builder
// TODO note we are not assuming a finite number of party candidates. should we ??

// immutable
class DhondtContest(
    info: ContestInfo,
    voteInput: Map<Int, Int>,   // partyId -> nvotes;  sum is nvotes or V_c
    Nc: Int,                    // trusted maximum ballots/cards that contain this contest
    Ncast: Int,                 // number of cast ballots containing this Contest, including undervotes
    val parties: List<DhondtParty>, // the parties
    val sortedScores: List<DhondtCandidateScore>,
    val assorters: List<AssorterIF>,
    thresholdOverride: Set<Int>? = null, // // set when non standard: partyIds below threshold

): Contest(info, voteInput, Nc, Ncast) {
    val nvotes = votes.values.sum()

    override fun winnerNames() = winnerNames
    override fun winners() = winners
    override fun losers() = losers

    val nseats: Int
    val partiesBelowThreshold: Set<Int> // partyId under minFraction

    // val winnerSeatCount: Map<Int, Int> // party id -> nseats won
    // this is s(P) -> N+ in vanessa's paper section 2.3
    val winnerSeatCount: Map<Int, Int> // party id -> nseats won, including 0?

    // dhondts and threshold assorters; these are set at creation, but not serialized, so cant assume they exist
    // can we put the generation of these inside? problem is ContestUA is serialized seperately, would have to rejigger that

    init {
        require(info.minFraction != null)
        nseats = info.nwinners
        val nvotes = votes.values.sum()

        // "A winning party must have a minimum fraction f ∈ (0, 1) of the valid votes to win". assume that means nvotes, not Nc.
        partiesBelowThreshold = thresholdOverride ?: parties.filter { it.totalVotes / nvotes.toDouble() < info.minFraction }.map { it.id }.toSet()

        winnerSeatCount = winnerSeatCount(sortedScores, parties.map { it.id })

        // fields in superclass
        winners = winnerSeatCount.filter{ it.value > 0 }.map{ it.key }.toList()
        losers = winnerSeatCount.filter{ it.value == 0 }.map{ it.key }.toList()
        winnerNames = winners.map { info.candidateIdToName[it]!! }
    }

    override fun hasNoLosers() = false

    fun candidateName(partyId: Int, divisor: Int) = "${info.candidateIdToName[partyId]}/$divisor"

    override fun recountMargin(assorter: AssorterIF): Double {
        return when (assorter) {
            is DhondtAssorter -> {
                val winnerScore = votes[assorter.winner()]!! / assorter.winnerDivisor.toDouble()
                val loserScore = votes[assorter.loser()]!! / assorter.loserDivisor.toDouble()
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
            is DhondtAssorter -> {
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

    // TODO should be the factor from KISS paper
    fun difficulty(assorter: AssorterIF): Double {
        return 1.0 / assorter.reportedMargin()
    }

    override fun marginInVotes(assorter: AssorterIF): Int {
        return when (assorter) {
            is DhondtAssorter -> {
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
        append("   nseats=$nseats winnerSeats=${winnerSeatCount} belowMin=${partiesBelowThreshold} threshold=${info.minFraction} minVotes=${roundUp(info.minFraction!! * nvotes)}")
    }

    override fun showCandidates() = buildString {
        val width0 = 20
        val width = 12
        val maxRound = sortedScores.filter{ it.winningSeat != null }.maxOfOrNull { it.divisor }!! + 1

        appendLine()
        append("party${trunc("Round", width0 - "party".length + 3)}:")
        for (round in 1 .. maxRound) {
            append("${nfn(round, width)} |")
        }
        appendLine()

        info.candidateNames.toSortedMap().forEach { (name, id) ->
            val rounds = sortedScores.filter { it.partyId == id }.map { Dround(id, it.score, it.divisor, it.winningSeat) }
            val below = if (partiesBelowThreshold.contains(id)) "*" else " "
            val candName = "${nfn(id, 2)} ${trunc(info.candidateIdToName[id]!!, width0)}$below"
            append(showParty(candName, votes[id]!!, maxRound, rounds, width))
        }
        appendLine("\n* failed threshold")
    }

    fun showParty(candName: String, candTotal: Int, maxRound: Int, rounds: List<Dround>, width:Int) = buildString {
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

    // for viewer
    fun showRelaxedAssertion(contestRound: ContestRound, maxRisk: Double) = buildString {
        val relax = makeRelaxedAssertions(contestRound, maxRisk)
        append(relax.show())
    }

    // for viewer
    fun getRelaxedAssertion(contestRound: ContestRound, maxRisk: Double, version: String? = null): RelaxedAssertionsIF {
        return makeRelaxedAssertions(contestRound, maxRisk, version = version)
    }

    /* fun countContestedSeats(contestRound: ContestRound): Int {
        val relax = makeRelaxedAssertions(contestRound, .05)
        return relax.failures().size
    } */

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
        if (other !is DhondtContest) return false
        if (!super.equals(other)) return false

        if (sortedScores != other.sortedScores) return false
        if (parties != other.parties) return false
        if (partiesBelowThreshold != other.partiesBelowThreshold) return false
        if (winnerSeatCount != other.winnerSeatCount) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + parties.hashCode()
        result = 31 * result + sortedScores.hashCode()
        result = 31 * result + partiesBelowThreshold.hashCode()
        result = 31 * result + winnerSeatCount.hashCode()
        return result
    }
}