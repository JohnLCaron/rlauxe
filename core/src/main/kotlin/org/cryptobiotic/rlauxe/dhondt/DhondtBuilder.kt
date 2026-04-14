package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.audit.AssertionRound
import org.cryptobiotic.rlauxe.audit.AuditRoundResult
import org.cryptobiotic.rlauxe.core.AboveThreshold
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.BelowThreshold
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.estSamples
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.math.max


private val showDetails = false
private val useBt = true // always use Bt

fun makeDhondtBuilder(
    name: String,
    id: Int,
    parties: List<DhondtCandidate>,
    nseats: Int,
    undervotes: Int,
    minFraction: Double,
): DhondtBuilder {
    // have to do this before winners are assigned
    val nvotes = parties.sumOf { it.votes }
    parties.forEach { if (it.votes / nvotes.toDouble() < minFraction) it.belowMinPct = true }

    if (showDetails) println("makeDhondtElection")
    val dHondtContest = assignWinners(name, id, parties, nseats = nseats, undervotes = undervotes, minFraction = minFraction)
    parties.forEach {
        it.setResults(dHondtContest)
        if (showDetails) println()
    }

    dHondtContest.makeProtoAssorters()
    return dHondtContest
}

private fun assignWinners(
    name: String,
    id: Int,
    parties: List<DhondtCandidate>,
    nseats: Int,
    undervotes: Int,
    minFraction: Double,
): DhondtBuilder {
    val scores = mutableListOf<DhondtScore>()
    parties.filter { !it.belowMinPct }.forEach { party ->
        repeat(nseats) { idx ->
            val seatno = idx + 1
            val divisor = seatno.toDouble()
            scores.add(DhondtScore(party.id, party.votes / divisor, seatno))
        }
    }
    scores.sortByDescending { it.score }

    var maxRound = 0
    repeat(nseats) { idx ->
        val score = scores[idx]
        score.setWinningSeat(idx + 1)
        maxRound = max(maxRound, idx + 1)
        if (showDetails) println(" ${idx + 1}: ${scores[idx]}")
    }
    if (showDetails) println()

    val rawscores = mutableListOf<DhondtScore>()
    parties.forEach { party ->
        repeat(maxRound) { idx ->
            val seatno = idx + 1
            val divisor = seatno.toDouble()
            rawscores.add(DhondtScore(party.id, party.votes / divisor, seatno))
        }
    }
    rawscores.sortByDescending { it.score }

    return DhondtBuilder(name, id, parties, scores, nseats, undervotes, minFraction, rawscores)
}

data class DhondtBuilder(
    val name: String,
    val id: Int,
    val parties: List<DhondtCandidate>,
    val sortedScores: List<DhondtScore>,
    val nseats: Int,
    val undervotes: Int,
    val minFraction: Double,
    val sortedRawScores: List<DhondtScore>,
) {
    val winners = sortedScores.subList(0, nseats)
    val losers = sortedScores.subList(nseats, sortedScores.size)
    private val assorters = mutableListOf<AssorterBuilder>()

    val validVotes: Int = parties.sumOf { it.votes }
    val totalVotes = validVotes + undervotes

    init {
        // isnt this already done?
        parties.forEach { if (it.votes / validVotes.toDouble() < minFraction) it.belowMinPct = true }
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
        )
        result.assorters.addAll(assorters.map { it.makeAssorter() })
        val lastWinningScore = winners.last()
        val lastWinner = parties.find { it.id == lastWinningScore.candidate }!!

        parties.forEach { party ->
            if (party.belowMinPct) {
                // decide which is cheaper
                val bt = BelowThreshold.makeFromVotes(info, partyId = party.id, votes, minFraction, useNc)

                val partyCopy = party.copy()
                partyCopy.firstSeatLost = 1
                val dh = AssorterBuilder(this, lastWinner, partyCopy).makeAssorter()

                val useAssorter = if (useBt || (bt.noerror() > dh.noerror())) bt else dh
                result.assorters.add(useAssorter)
            } else {
                result.assorters.add(AboveThreshold.makeFromVotes(info, partyId = party.id, votes, minFraction, useNc))
            }
        }

        return result
    }
}

data class AssorterBuilder(
    val contest: DhondtBuilder,
    val winner: DhondtCandidate,
    val loser: DhondtCandidate,
) {
    // Let f_e,s = Te/d(s) for entity e and seat s
    // f_A,WA > f_B,LB, so e = A and s = Wa

    val fw = winner.votes / winner.lastSeatWon!!.toDouble()
    val fl = loser.votes / loser.firstSeatLost!!.toDouble()
    val gmean = (fw - fl) / contest.totalVotes // TODO need phantoms also

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
        firstSeatLost = loser.firstSeatLost!!
    )
        .setDilutedMean(hmean)
}

//////////////////////////////////////

// this could be the base, and DhondtContest subclasses it.
// or just turn this into DhondtContest
class AltDhondt(
    val info: ContestInfo,
    val votes: Map<Int, Int>,   // candidateId -> nvotes;  sum is nvotes or V_c
    val belowMinPct: Set<Int>// candidateIds under minFraction
) {
    val nvotes = votes.values.sum()

    val winnerSeats: Map<Int, Int> // cand, nseats

    // dhondts and threshold assorters; these are set at creation, but not serialized, so cant assume they exist
    val assorters = mutableListOf<AssorterIF>()

    val parties: List<DhondtCandidate>
    val sortedScores = mutableListOf<DhondtScore>()
    val sortedRawScores = mutableListOf<DhondtScore>()

    init {
        //// duplicate in DHondtContest
        // recreate the parties
        parties = info.candidateIds.map { id ->
            DhondtCandidate(info.candidateIdToName[id]!!, id, votes[id]!!)
        }
        // could use belowMinPct; these dont have first/last yet
        parties.forEach { it.belowMinPct = belowMinPct.contains( it.id)  }

        // recreate the winners and losers
        val nseats = info.nwinners
        parties.filter { !it.belowMinPct }.forEach { party ->
            repeat(nseats) { idx ->
                val seatno = idx + 1
                val divisor = seatno.toDouble()
                sortedScores.add(DhondtScore(party.id, party.votes / divisor, seatno))
            }
        }
        sortedScores.sortByDescending { it.score }

        var maxRound = 0
        repeat(nseats) { idx ->
            val score = sortedScores[idx]
            score.setWinningSeat(idx + 1)
            maxRound = max(maxRound, idx + 1)
        }
        val winnerScores = sortedScores.subList(0, nseats)
        val loserScores = sortedScores.subList(nseats, sortedScores.size)

        // recreate the raw scores
        parties.forEach { party ->
            repeat(maxRound) { idx ->
                val seatno = idx + 1
                val divisor = seatno.toDouble()
                sortedRawScores.add(DhondtScore(party.id, party.votes / divisor, seatno))
            }
        }
        sortedRawScores.sortByDescending { it.score }

        val winnerSeatsM = mutableMapOf<Int, Int>()
        sortedScores.filter { it.winningSeat != null }.forEach {
            val count = winnerSeatsM.getOrPut(it.candidate) { 0 }
            winnerSeatsM[it.candidate] = count + 1
        }
        winnerSeats = winnerSeatsM.toMap()

        val winners = winnerSeats.keys.toList()
        val losers = info.candidateIds.filter { !winners.contains(it) }
        val winnerNames = winners.map { info.candidateIdToName[it]!! }
    }

    // duplicate with DhondtContest
    fun marginInVotes(assorter: AssorterIF): Int {
        return when (assorter) {
            is DHondtAssorter -> {
                roundToClosest(assorter.difficulty(votes[assorter.winner()]!!, votes[assorter.loser()]!!))
            }

            is BelowThreshold -> {
                roundToClosest(assorter.t * nvotes - votes[assorter.winner()]!!)
            }

            is AboveThreshold -> {
                roundToClosest(votes[assorter.winner()]!! - assorter.t * nvotes)
            }

            else -> throw RuntimeException("unknown assorter type= ${assorter.javaClass.simpleName}")
        }
    }

    // duplicate with DhondtContest
    fun showAssertions(lastAssertionRounds: Map<String, AssertionRound>) = buildString {
        val candNameWidth = 20
        val width = 12
        val maxRound = sortedScores.filter { it.winningSeat != null }.maxOfOrNull { it.divisor }!! + 1
        val nseats = winnerSeats.values.sum()

        append(" seat ${sfn("winner", candNameWidth - 3)}/round     ${sfn("nvotes", 6)}, ")
        append("${sfn(" score", 6)},  ${sfn("voteDiff", 6)}, ")
        appendLine()

        var prev: Int? = null
        repeat(nseats) { idx ->
            val score = sortedScores[idx]
            // sortedRawScores.filter{ it.divisor <= maxRound }.forEachIndexed { idx, score ->
            val candId = score.candidate
            if (idx < nseats) append(" (${nfn(idx + 1, 2)}) ") else append("      ")
            val below = if (belowMinPct.contains(candId)) "*" else " "
            append(" ${trunc(info.candidateIdToName[candId]!!, candNameWidth)}$below")
            append("/${nfn(score.divisor, 2)}, ")
            append(" ${nfn(votes[candId]!!, 6)}, ${nfn(score.score.toInt(), 6)}, ")

            if (prev != null) append(" ${nfn(prev - score.score.toInt(), 6)},")
            prev = score.score.toInt()
            appendLine()
        }
        appendLine("winners=${winnerSeats}")
        appendLine()

        val armsMap = mutableMapOf<Int, AssertionRiskGroup>()
        lastAssertionRounds.forEach { (key, ar) ->
            val assorter = ar.assertion.assorter
            val risk = ar.auditResult?.pmin ?: Double.NaN
            if (risk > .05 && assorter is DHondtAssorter) {
                val loserId = assorter.loser()
                val loserScore = sortedScores.find { it.divisor == assorter.firstSeatLost && it.candidate == loserId }!!
                val group = armsMap.getOrPut(loserId) { AssertionRiskGroup(loserId) }
                group.arms.add(AssertionRiskMargin(ar, ar.assertion.assorter, loserScore, ar.auditResult!!))
            }
        }
        if (armsMap.isNotEmpty()) {
            val sortedGroups = armsMap.values.toList().sortedByDescending { it -> it.highScore() }
            append(showAssertionsAtRisk(sortedGroups, candNameWidth))
        }

        /*
        val thrashers = mutableListOf<AssertionThrasher>()
        lastAssertionRounds.forEach { (key, ar) ->
            val assorter = ar.assertion.assorter
            val risk = ar.auditResult?.pmin ?: Double.NaN
            if (risk > .05 && assorter !is DHondtAssorter) {
                thrashers.add(AssertionThrasher(ar, ar.assertion.assorter, ar.auditResult))
            }
        }
        if (thrashers.isNotEmpty()) {
            append(showAssertionThrashers(thrashers, candNameWidth))
        } */
    }

    // duplicate with DhondtContest

    data class AssertionRiskGroup(val loserId: Int) {
        val arms = mutableListOf<AssertionRiskMargin>()
        fun highScore(): Double {
            val wtf = arms.maxOfOrNull { it.loserScore.score }
            return wtf ?: 0.0
        }
        fun sortedArms() = arms.sortedBy { it.nomargin }
    }

    data class AssertionRiskMargin(
        val ar: AssertionRound,
        val assorter: DHondtAssorter,
        val loserScore: DhondtScore,
        val auditResult: AuditRoundResult?,
    ) {
        val nomargin = 2.0 * assorter.noerror() - 1.0
        val risk = ar.auditResult?.pmin ?: Double.NaN
        val nmvrs = ar.auditResult?.samplesUsed ?: 0
        fun estMvrs(): Int {
            // payoff_noerror = (1 + λ * (noerror − 1/2))  ;  (µ_i is approximately 1/2)
            // payoff_noerror^n > 1/alpha
            // n = 1/ln(alpha) / ln(λ * (noerror − 1/2)); noerror − 1/2 = nomargin/2
            // TODO
            val maxLoss: Double = 1.0 / 1.03905
            return roundUp(estSamples(2 * maxLoss, nomargin, .05)) // =  -ln(alpha) / ln(1.0 + bet * nomargin/2)
        }
    }

    fun showAssertionsAtRisk(sortedGroups: List<AssertionRiskGroup>, candNameWidth: Int) = buildString {

        appendLine("Contested          loser/round    nvotes,  score, voteDiff,  noerror, nomargin, estSamples, actSamples,   risk, assertion")

        sortedGroups.forEach { group ->
            group.sortedArms().forEachIndexed { idx, arm ->
                val assorter = arm.assorter
                val candId = assorter.loser()
                append("       ")
                if (idx == 0) {
                    append(arm.loserScore.showLoser(trunc(info.candidateIdToName[candId]!!, candNameWidth), votes[candId]!!))
                } else {
                    append("                                           ")
                }
                append(" ${nfn(marginInVotes(assorter), 7)}, ${dfn(assorter.noerror(), 6)},   ${dfn(arm.nomargin, 4)}, ")
                append(" ${nfn(arm.estMvrs(), 8)}, ${nfn(arm.nmvrs, 8)},    ${dfn(arm.risk, 4)},")
                append(" winner ${assorter.winnerNameRound()} loser ${assorter.loserNameRound()}")
                appendLine()
            }
        }
    }

}