package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.core.CvrIF
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.core.AboveThreshold
import org.cryptobiotic.rlauxe.core.BelowThreshold
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.collections.mutableListOf

private val showDetails = false
private val useBt = false // always use Bt

// Belgium does each contest separately, so Npop = Nc. TODO generalize that

data class DhondtCandidate(val name: String, val id: Int, val votes: Int) {
    var lastSeatWon: Int? = null // We
    var firstSeatLost: Int? = null // Le
    var belowMinPct = false

    constructor(id: Int, votes: Int) : this("party-$id", id, votes)

    fun setResults(results: ProtoContest) {
        if (showDetails) results.sortedScores.filter{ it.candidate == this.id }.forEach { println(" ${it}") }

        lastSeatWon = results.winners.filter { it.candidate == this.id }.maxOfOrNull { it.divisor }
        firstSeatLost = results.losers.filter { it.candidate == this.id }.minOfOrNull { it.divisor }
    }
}

fun makeProtoContest(name: String, id: Int, parties: List<DhondtCandidate>, nseats: Int, undervotes: Int, minFraction: Double): ProtoContest {
    // have to do this before winners are assigned
    val nvotes = parties.sumOf { it.votes }
    parties.forEach { if (it.votes/nvotes.toDouble() < minFraction) it.belowMinPct = true }

    if (showDetails) println("makeDhondtElection")
    val dHondtContest = assignWinners(name, id, parties, nseats = nseats, undervotes=undervotes, minFraction = minFraction)
    parties.forEach {
        it.setResults(dHondtContest)
        if (showDetails) println()
    }

    dHondtContest.makeProtoAssorters()
    return dHondtContest
}

fun assignWinners(name: String, id: Int, parties: List<DhondtCandidate>, nseats : Int, undervotes: Int, minFraction: Double): ProtoContest {
    val scores = mutableListOf<DhondtScore>()
    parties.filter{ !it.belowMinPct }.forEach { party->
        repeat(nseats) { idx ->
            val seatno = idx + 1
            val divisor = seatno.toDouble()
            scores.add(DhondtScore(party.id, party.votes / divisor, seatno))
        }
    }
    scores.sortByDescending { it.score }

    repeat(nseats) { idx ->
        val score = scores[idx]
        score.setWinningSeat(idx + 1)
        if (showDetails) println(" ${idx+1}: ${scores[idx]}")
    }
    if (showDetails) println()

    return ProtoContest(name, id, parties, scores, nseats, undervotes, minFraction)
}

// f_e,s = Te /d(s)
// e = partyId, s = seatno, score = Te /d(s)
data class DhondtScore(val candidate: Int, val score: Double, val divisor: Int) {
    var winningSeat: Int? = null
    fun setWinningSeat(ws: Int?): DhondtScore { this.winningSeat = ws; return this }
    override fun toString() = buildString {
        append("DhondtScore(candidate=$candidate, divisor=$divisor, score=${df(score)}")
        if (winningSeat != null) append(", winningSeat=$winningSeat")
        append (")")
    }
}

data class ProtoContest(val name: String, val id: Int, val parties: List<DhondtCandidate>, val sortedScores: List<DhondtScore>,
                        val nseats: Int, val undervotes: Int, val minFraction: Double) {

    val winners = sortedScores.subList(0, nseats)
    val losers = sortedScores.subList(nseats, sortedScores.size)
    private val assorters = mutableListOf<AssorterBuilder>()

    val validVotes: Int  = parties.sumOf { it.votes }
    val totalVotes = validVotes + undervotes

    init {
        // isnt this already done?
        parties.forEach { if (it.votes/validVotes.toDouble() < minFraction) it.belowMinPct = true }
    }

    fun makeProtoAssorters() {
        // Let f_e,s = Te /d(s) for entity e and seat s
        // f_A,WA > f_B,LB, so e = A and s = Wa

        parties.forEach { winner ->
            if (winner.lastSeatWon != null) {
                parties.filter { it.id != winner.id }.forEach { loser ->
                    if (loser.firstSeatLost != null) {
                        val passorter = AssorterBuilder(this, winner, loser)
                        assorters.add(passorter)
                    }
                }
            }
        }
    }

    fun makeAssorters(): List<DHondtAssorter> = assorters.map { it.makeAssorter() }

    fun createInfo() = ContestInfo(
        name,
        id,
        parties.associate { Pair(it.name, it.id) },
        SocialChoiceFunction.DHONDT,
        nwinners = nseats,
        voteForN = 1,
        minFraction = minFraction,
    )

    fun createContest(Nc: Int? = null, Ncast: Int? = null): DHondtContest {
        val info = createInfo()
        val votes = parties.associate { Pair(it.id, it.votes) }
        val useNc = Nc ?: this.validVotes
        val result = DHondtContest(
            info,
            votes,
            useNc,
            Ncast ?: this.validVotes,
            this.sortedScores,
        )
        result.assorters.addAll(assorters.map { it.makeAssorter() })
        val lastWinningScore = winners.last()
        val lastWinner = parties.find{ it.id == lastWinningScore.candidate }!!

        parties.forEach { party ->
            if (party.belowMinPct) {
                // decide which is cheaper
                val bt = BelowThreshold.makeFromVotes(info, partyId = party.id, votes, minFraction, useNc)

                val partyCopy = party.copy()
                partyCopy.firstSeatLost = 1
                val dh =  AssorterBuilder(this, lastWinner, partyCopy).makeAssorter()

                val useAssorter = if (useBt || (bt.noerror() > dh.noerror())) bt else dh
                result.assorters.add(useAssorter)
            } else {
                result.assorters.add(AboveThreshold.makeFromVotes(info, partyId = party.id, votes, minFraction, useNc))
                    // result.assorters.add(AboveThresholdB.makeFromVotes(info, partyId = party.id, votes, minFraction, useNc))
            }
        }

        return result
    }
}

class DHondtContest(
    info: ContestInfo,
    voteInput: Map<Int, Int>,   // candidateId -> nvotes;  sum is nvotes or V_c
    Nc: Int,                // trusted maximum ballots/cards that contain this contest
    Ncast: Int,             // number of cast ballots containing this Contest, including undervotes
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

    val belowMinPct: Set<Int>// contestIds under minFraction
    val winnerSeats : Map<Int, Int> // cand, nseats
    val assorters = mutableListOf<AssorterIF>()

    init {
        // "A winning candidate must have a minimum fraction f ∈ (0, 1) of the valid votes to win". assume that means nvotes, not Nc.
        require(info.minFraction != null)
        val useMin = info.minFraction
        val belowMinPctM = mutableListOf<Int>()
        votes.toList().filter{ it.second.toDouble()/nvotes < useMin }.forEach {
            belowMinPctM.add(it.first)
        }
        belowMinPctM.sort()
        belowMinPct = belowMinPctM.toSet()

        val winnerSeatsM= mutableMapOf<Int, Int>()
        sortedScores.filter { it.winningSeat != null }.forEach {
            val count = winnerSeatsM.getOrPut(it.candidate) { 0 }
            winnerSeatsM[it.candidate] = count + 1
        }
        winnerSeats = winnerSeatsM.toMap()
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
                assorter.difficulty(votes[assorter.winner()]!!, votes[assorter.loser()]!!)
            }
            is BelowThreshold -> {
                // val nvotes = votes.values.sum() does not include undervotes
                assorter.difficulty(votes[assorter.winner()]!!, nvotes)
            }
            is AboveThreshold -> {
                // val nvotes = votes.values.sum() does not include undervotes
                assorter.difficulty(votes[assorter.winner()]!!, nvotes)
            }
            else -> throw RuntimeException()
        }
    }

    override fun show() = buildString {
        appendLine(super.show())
        append("   nseats=${winnerSeats.values.sum()} winners=${winnerSeats} belowMin=${belowMinPct} threshold=${info.minFraction} minVotes=${roundUp(info.minFraction!! * nvotes)}")
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

        info.candidateIds.forEach { id ->
            val rounds = sortedScores.filter { it.candidate == id }.map { Dround(id, it.score, it.divisor, it.winningSeat) }
            val below = if (belowMinPct.contains(id)) "*" else " "
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

    fun createSimulatedCvrs(): List<Cvr> {
        val cvrs = mutableListOf<Cvr>()
        var count=0
        this.votes.forEach { (candid, nvotes) ->
            repeat(nvotes) {
                count++
                cvrs.add( Cvr("cvr$count", mapOf(id to intArrayOf(candid))))
            }
        }
        repeat(undervotes) {
            count++
            cvrs.add( Cvr("undervote$count", mapOf(id to IntArray(0))))
        }
        repeat(Nphantoms()) {
            count++
            cvrs.add( Cvr("phantom$count", mapOf(id to IntArray(0)), phantom=true))
        }
        cvrs.shuffle()
        return cvrs
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DHondtContest) return false
        if (!super.equals(other)) return false

        if (sortedScores != other.sortedScores) return false
        if (belowMinPct != other.belowMinPct) return false
        if (winnerSeats != other.winnerSeats) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + sortedScores.hashCode()
        result = 31 * result + belowMinPct.hashCode()
        result = 31 * result + winnerSeats.hashCode()
        return result
    }

    class Dround(val candId: Int, val score: Double, val round: Int, val winningSeat: Int?)
}

private data class AssorterBuilder(val contest: ProtoContest, val winner: DhondtCandidate, val loser: DhondtCandidate) {
    // Let f_e,s = Te/d(s) for entity e and seat s
    // f_A,WA > f_B,LB, so e = A and s = Wa

    val fw = winner.votes / winner.lastSeatWon!!.toDouble()
    val fl = loser.votes / loser.firstSeatLost!!.toDouble()
    val gmean = (fw - fl)/contest.totalVotes // TODO need phantoms also

    val lower = -1.0 / loser.firstSeatLost!!  // lower bound of g
    val upper = 1.0 / winner.lastSeatWon!!  // upper bound of g
    val c = -1.0 / (2 * lower)  // affine transform h = c * g + 1/2
    val hmean = h(gmean)

    fun h(g: Double): Double = c * g + 0.5

    fun makeAssorter() = DHondtAssorter(
        contest.createInfo(),
        winner.id,
        loser.id,
        lastSeatWon = winner.lastSeatWon!!,
        firstSeatLost = loser.firstSeatLost!!)
     .setDilutedMean(hmean)
}

// ### Section 5.1 highest averages
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

// winner,loser: candidate ids
// lastSeatWon: last seat won by winner
// firstSeatLost: last seat lost by loser
data class DHondtAssorter(val info: ContestInfo, val winner: Int, val loser: Int, val lastSeatWon: Int, val firstSeatLost: Int): AssorterIF  {
    val upperg = 1.0 / lastSeatWon  // upper bound of g = 1/d(WA)  = 1/lastSeatWon   (highest loser)
    val lowerg = -1.0 / firstSeatLost  // lower bound of g = -1/d(WB) = -1/firstSeatLost (lowest winner)
    val c = -1.0 / (2 * lowerg)  // first/2
    private var dilutedMean: Double = 0.0

    fun setDilutedMean(mean: Double): DHondtAssorter {
        this.dilutedMean = mean
        return this
    }

    fun g(partyVote: Int): Double {
        return if (partyVote == winner) upperg
            else if (partyVote == loser) lowerg
            else 0.0
    }

    // h(b) = c · g(b) + 1/2
    fun h(partyVote: Int): Double {
        return c * g(partyVote) + 0.5
    }

    // l = h(-1/first) = -1/first * first/2 + 1/2 = 0
    // u = h(1/last) = 1/last * first/2 + 1/2 = (firstSeatLost/lastSeatWon+1)/2
    fun h2(g: Double): Double {
        return c * g + 0.5
    }

    // (first/last+1)/2
    override fun upperBound() = h2(upperg)
    override fun winner() = winner
    override fun loser() = loser
    override fun dilutedMean() = dilutedMean
    override fun dilutedMargin() = mean2margin(dilutedMean)

    // [ 0, .5, u]
    override fun assort(cvr: CvrIF, usePhantoms: Boolean): Double {
        if (!cvr.hasContest(info.id)) return 0.5
        if (usePhantoms && cvr.isPhantom()) return 0.0 // worst case
        val cands = cvr.votes(info.id)
        return if (cands != null && cands.size == 1) h(cands.first()) else 0.5
    }

    override fun desc() = buildString {
        append("${shortName()}: winner firstSeatLost=$firstSeatLost loser lastSeatWon=$lastSeatWon upperBound=(first/last+1)/2=${df(upperBound())}")
    }
    override fun shortName() = "DHondt w/l='${info.candidateIdToName[winner()]}'/'${info.candidateIdToName[loser()]}'"
    override fun hashcodeDesc() = "${winLose()} ${info.name}" // must be unique for serialization

    fun showAssertionDifficulty(votesForWinner: Int, votesForLoser: Int): String {
        val winnerScore = votesForWinner / lastSeatWon.toDouble()
        val loserScore = votesForLoser / firstSeatLost.toDouble()
        return "fw=${dfn(winnerScore, 1)} fl=${dfn(loserScore, 1)} fw-fl=${dfn(winnerScore - loserScore, 0)}"
    }

    fun difficulty(votesForWinner: Int, votesForLoser: Int): Double {
        val winnerScore = votesForWinner / lastSeatWon.toDouble()
        val loserScore = votesForLoser / firstSeatLost.toDouble()
        return winnerScore - loserScore
    }

    override fun calcMarginFromRegVotes(useVotes: Map<Int, Int>?, N: Int): Double {
        if (useVotes == null || N <= 0) {
            return 0.0
        } // shouldnt happen

        val winnerVotes = useVotes[winner()] ?: 0
        val loserVotes = useVotes[loser()] ?: 0

        val fw = winnerVotes / lastSeatWon.toDouble()
        val fl = loserVotes / firstSeatLost.toDouble()

        val gmean = (fw - fl)/N
        val hmean = h2(gmean)
        val margin = mean2margin(hmean)

        return margin
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DHondtAssorter) return false

        if (winner != other.winner) return false
        if (loser != other.loser) return false
        if (lastSeatWon != other.lastSeatWon) return false
        if (firstSeatLost != other.firstSeatLost) return false
        if (dilutedMean != other.dilutedMean) return false
        if (lowerg != other.lowerg) return false
        if (upperg != other.upperg) return false
        if (c != other.c) return false
        if (info != other.info) return false

        return true
    }

    override fun hashCode(): Int {
        var result = winner
        result = 31 * result + loser
        result = 31 * result + lastSeatWon
        result = 31 * result + firstSeatLost
        result = 31 * result + dilutedMean.hashCode()
        result = 31 * result + lowerg.hashCode()
        result = 31 * result + upperg.hashCode()
        result = 31 * result + c.hashCode()
        result = 31 * result + info.hashCode()
        return result
    }

    override fun toString() = desc()
}