package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.core.Assertion
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.collections.mutableListOf

private val showDetails = false

fun makeProtoContest(name: String, id: Int, parties: List<DhondtCandidate>, nseats: Int, undervotes: Int, minPct: Double): ProtoContest {
    // have to do this before winners are assigned
    val nvotes = parties.sumOf { it.votes }
    parties.forEach { if (it.votes/nvotes.toDouble() < minPct) it.belowMinPct = true }

    if (showDetails) println("makeDhondtElection")
    val dHondtContest = assignWinners(name, id, parties, nseats = nseats, undervotes=undervotes, minPct = minPct)
    parties.forEach {
        it.setResults(dHondtContest)
        if (showDetails) println()
    }

    dHondtContest.makeProtoAssorters()
    return dHondtContest
}

fun assignWinners(name: String, id: Int, parties: List<DhondtCandidate>, nseats : Int, undervotes: Int, minPct: Double): ProtoContest {
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

    return ProtoContest(name, id, parties, scores, nseats, undervotes, minPct)
}

data class DhondtCandidate(val name: String, val id: Int, val votes: Int) {
    var lastSeatWon: Int? = null // We
    var firstSeatLost: Int? = null // Le
    var belowMinPct = false

    constructor(id: Int, votes: Int) : this("party-$id", id, votes)

    fun setResults(results: ProtoContest) {
        if (showDetails) results.sortedScores.filter{ it.candidate == this.id }.forEach { println(" ${it}") }

        lastSeatWon = results.winners.filter { it.candidate == this.id }.maxOfOrNull { it.divisor }
        firstSeatLost = results.losers.filter { it.candidate == this.id }.minOfOrNull { it.divisor }
        if (showDetails) println(this)
    }

    override fun toString(): String {
        val fw = if (this.lastSeatWon == null) 0.0 else this.votes / this.lastSeatWon!!.toDouble()
        val fl = if (this.firstSeatLost == null) 0.0 else this.votes / this.firstSeatLost!!.toDouble()
        return "Party(name='$name' id=$id, votes=$votes, lastSeatWon=$lastSeatWon, firstSeatLost=$firstSeatLost, belowMinPct=$belowMinPct"
    }
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
                        val nseats: Int, val undervotes: Int, val minPct: Double) {
    val winners = sortedScores.subList(0, nseats)
    val losers = sortedScores.subList(nseats, sortedScores.size)
    val assorters = mutableListOf<ProtoAssorter>()

    val validVotes: Int  = parties.sumOf { it.votes }
    val totalVotes = validVotes + undervotes

    init {
        parties.forEach { if (it.votes/validVotes.toDouble() < minPct) it.belowMinPct = true }
    }

    fun makeProtoAssorters() {
        // Let f_e,s = Te /d(s) for entity e and seat s
        // f_A,WA > f_B,LB, so e = A and s = Wa

        parties.forEach { winner ->
            if (winner.lastSeatWon != null) {
                parties.filter { it.id != winner.id }.forEach { loser ->
                    if (loser.firstSeatLost != null) {
                        val passorter = ProtoAssorter(this, winner, loser)
                        assorters.add(passorter)
                        if (showDetails) println(passorter.show())
                    }
                }
            }
        }
    }

    fun makeAssorters(): List<DHondtAssorterIF> = assorters.map { it.makeAssorter() }

    fun show() = buildString {
        appendLine("'$name' ($id) Nc=$validVotes nseats=$nseats minPct=$minPct")
        appendLine(showWinners())
    }

    fun showWinners() = buildString {
        appendLine("Calculated Winners")
        winners.sortedBy { it.winningSeat }.forEach {
            appendLine("  ${it}")
        }
    }

    fun createInfo() = ContestInfo(
        name,
        id,
        parties.associate { Pair(it.name, it.id) },
        SocialChoiceFunction.DHONDT,
        nwinners = nseats,
        voteForN = 1,
        minFraction = minPct,
    )

    fun createContest(Nc: Int? = null, Ncast: Int? = null): ContestDHondt {
        val result = ContestDHondt(
            createInfo(),
            parties.associate { Pair(it.id, it.votes) },
            Nc ?: this.validVotes,
            Ncast ?: this.validVotes,
            this.sortedScores,
        )
        result.assorters.addAll(assorters.map { it.makeAssorter() })
        return result
    }
}

class ContestDHondt(
    info: ContestInfo,
    voteInput: Map<Int, Int>,   // candidateId -> nvotes;  sum is nvotes or V_c
    Nc: Int,                // trusted maximum ballots/cards that contain this contest
    Ncast: Int,             // number of cast ballots containing this Contest, including undervotes
    val sortedScores: List<DhondtScore>,
): Contest(info, voteInput, Nc, Ncast) {

    override fun Nc() = Nc
    override fun Np() = Nc - Ncast
    override fun Nundervotes() = undervotes
    override fun info() = info
    override fun winnerNames() = winnerNames
    override fun winners() = winners
    override fun losers() = losers

    val belowMinPct: Set<Int>// contestIds under minPct
    val winnerSeats : Map<Int, Int> // cand, nseats
    val assorters = mutableListOf<DHondtAssorterIF>()

    init {
        val nvotes = votes.values.sum()

        // "A winning candidate must have a minimum fraction f ∈ (0, 1) of the valid votes to win". assume that means nvotes, not Nc.
        val useMin = info.minFraction ?: 0.0
        val belowMinPctM= mutableListOf<Int>()
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

    override fun recountMargin(assertion: Assertion): Double {
        val dassorter = assertion.assorter as DHondtAssorterIF
        val winnerScore = votes[assertion.assorter.winner()]!! / dassorter.lastSeatWon.toDouble()
        val loserScore = votes[assertion.assorter.loser()]!! / dassorter.firstSeatLost.toDouble()

        val pct = (winnerScore - loserScore) / winnerScore
        return pct
    }

    override fun showAssertionDiff(assertion: Assertion?): String {
        if (assertion == null) return ""
        val dassorter = assertion.assorter as DHondtAssorterIF
        val winner = votes[assertion.assorter.winner()]!! / dassorter.lastSeatWon.toDouble()
        val loser = votes[assertion.assorter.loser()]!! / dassorter.firstSeatLost.toDouble()
        val recountMargin = (winner - loser) / (winner.toDouble())
        return "winner=${dfn(winner, 1)} loser=${dfn(loser, 1)} diff=${dfn(winner-loser, 1)} recountMargin=${df(recountMargin)}"
    }

    override fun show() = buildString {
        append(super.show())
        appendLine("   nseats=${winnerSeats.values.sum()} winners=${winnerSeats} belowMin=${belowMinPct}")
    }

    override fun showCandidates() = buildString {
        val width0 = 20
        val width = 12
        val maxRound = sortedScores.filter{ it.winningSeat != null }.maxOfOrNull { it.divisor }!! + 1
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
        repeat(Np()) {
            count++
            cvrs.add( Cvr("phantom$count", mapOf(id to IntArray(0)), phantom=true))
        }
        cvrs.shuffle()
        return cvrs
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContestDHondt) return false
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

data class ProtoAssorter(val contest: ProtoContest, val winner: DhondtCandidate, val loser: DhondtCandidate) {
    // Let f_e,s = Te/d(s) for entity e and seat s
    // f_A,WA > f_B,LB, so e = A and s = Wa

    val fw = winner.votes / winner.lastSeatWon!!.toDouble()
    val fl = loser.votes / loser.firstSeatLost!!.toDouble()
    val gmean = (fw - fl)/contest.totalVotes // TODO need phantoms also

    val lower = -1.0 / loser.firstSeatLost!!  // lower bound of g
    val upper = 1.0 / winner.lastSeatWon!!  // upper bound of g
    val c = -1.0 / (2 * lower)  // affine transform h = c * g + 1/2
    val hmean = h(gmean)

    fun g(partyVote: Int): Double {
        return if (partyVote == winner.id) 1.0 / winner.lastSeatWon!!
        else if (partyVote == loser.id) -1.0 / loser.firstSeatLost!!
        else 0.0
    }

    // h(b) = c · g(b) + 1/2
    fun h(partyVote: Int): Double {
        return c * g(partyVote) + 0.5
    }

    fun h(g: Double): Double {
        return c * g + 0.5
    }

    fun show() = buildString {
        append("(${winner.id}/${loser.id}): fw/fl=${dfn(fw,1)}/${dfn(fl, 1)} ")
        appendLine("gmean=${df(gmean)} hmean=${df(h(gmean))} contest.totalVotes=${contest.totalVotes}")
    }

    fun getAssortAvg(undervotes: Int): Pair<Welford, Welford> {
        val gavg = Welford()
        val havg = Welford()
        contest.parties.forEach { party ->
            repeat(party.votes) {
                // println("  (${winner.id}/${loser.id}) voteFor= ${party.id} g=${g(party.id)} h=${h(party.id)}")
                gavg.update(g(party.id))
                havg.update(h(party.id))
            }
        }
        repeat(undervotes) {
            // println("  (${winner.id}/${loser.id}) voteFor= ${party.id} g=${g(party.id)} h=${h(party.id)}")
            gavg.update(g(-99))
            havg.update(h(-99))
        }
        return Pair(gavg, havg)
    }

    fun assort(mvr: Cvr, usePhantoms: Boolean): Double {
        if (!mvr.hasContest(contest.id)) return 0.5
        if (usePhantoms && mvr.phantom) return 0.0 // worst case
        val cands = mvr.votes[contest.id]!!
        return if (cands.size == 1) h(cands.first()) else 0.5
    }

    fun reportedMean() = hmean
    fun reportedMargin() = mean2margin(hmean) // TODO maybe wrong?

    fun makeAssorter() = DHondtAssorterIF(
        contest.createInfo(),
        winner.id,
        loser.id,
        lastSeatWon = winner.lastSeatWon!!,
        firstSeatLost = loser.firstSeatLost!!,
        hmean
    )
}

data class DHondtAssorterIF(val info: ContestInfo, val winner: Int, val loser: Int, val lastSeatWon: Int, val firstSeatLost: Int, val reportedMean: Double): AssorterIF  {
    val upper = 1.0 / lastSeatWon  // upper bound of g
    val lower = -1.0 / firstSeatLost  // lower bound of g
    val c = -1.0 / (2 * lower)  // affine transform h = c * g + 1/2

    fun g(partyVote: Int): Double {
        return if (partyVote == winner) upper
            else if (partyVote == loser) lower
            else 0.0
    }

    // h(b) = c · g(b) + 1/2
    fun h(partyVote: Int): Double {
        return c * g(partyVote) + 0.5
    }

    fun h2(g: Double): Double {
        return c * g + 0.5
    }

    /* TODO
    fun assort2(mvr: Cvr, usePhantoms: Boolean): Double {
        if (!mvr.hasContest(contest.id)) return 0.5
        if (usePhantoms && mvr.phantom) return 0.0 // worst case
        val w = mvr.hasMarkFor(contest.id, winner.id)
        val l = mvr.hasMarkFor(contest.id, loser.id)
        return (w - l + 1) * 0.5
    } */

    override fun assort(mvr: Cvr, usePhantoms: Boolean): Double {
        if (!mvr.hasContest(info.id)) return 0.5
        if (usePhantoms && mvr.phantom) return 0.0 // worst case
        val cands = mvr.votes[info.id]!!
        return if (cands.size == 1) h(cands.first()) else 0.5
    }

    override fun upperBound() = h2(upper)

    override fun desc() = buildString {
        append("(${winner}/${loser}): reportedMean=${df(reportedMean)} reportedMargin=${df(reportedMargin() )}")
    }

    override fun hashcodeDesc() = "${winLose()} ${info.name}" // must be unique for serialization

    override fun winner() = winner

    override fun loser() = loser

    override fun reportedMean() = reportedMean
    override fun reportedMargin() = mean2margin(reportedMean) // TODO

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DHondtAssorterIF) return false

        if (winner != other.winner) return false
        if (loser != other.loser) return false
        if (lastSeatWon != other.lastSeatWon) return false
        if (firstSeatLost != other.firstSeatLost) return false
        if (reportedMean != other.reportedMean) return false
        if (lower != other.lower) return false
        if (upper != other.upper) return false
        if (c != other.c) return false
        if (info != other.info) return false

        return true
    }

    override fun hashCode(): Int {
        var result = winner
        result = 31 * result + loser
        result = 31 * result + lastSeatWon
        result = 31 * result + firstSeatLost
        result = 31 * result + reportedMean.hashCode()
        result = 31 * result + lower.hashCode()
        result = 31 * result + upper.hashCode()
        result = 31 * result + c.hashCode()
        result = 31 * result + info.hashCode()
        return result
    }

    override fun toString() = desc()
}
