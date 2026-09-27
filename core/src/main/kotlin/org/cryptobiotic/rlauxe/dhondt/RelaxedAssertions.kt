package org.cryptobiotic.rlauxe.dhondt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.betting.estRiskStandardBet
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.math.max
import kotlin.math.min

private val logger = KotlinLogging.logger("RelaxedAssertions")
private val debug = false

class RelaxedAssertions(val contestRound: ContestRound, alpha: Double) {
    val useAlpha = contestRound.auditorWantRisk ?: alpha
    val dcontest = contestRound.contestUA.contest as DhondtContest

    // must get from contestRound.contestUA, not auditRecord.contests
    val orgAssorters = contestRound.contestUA.clcaAssertions.map { it.assorter }

    val orgInfo = dcontest.info
    val votes = dcontest.votes
    val Npop = contestRound.contestUA.Npop
    val nsamples = contestRound.haveSampleSize

    val failures: MutableList<DhondtFailure>
    val singleFailure: Boolean
    var nSeatsInPlay: Int
    val candidateRanges: CandidateRanges

    init {
        logger.debug { "Contest ${orgInfo.name} haveSampleSize=${contestRound.haveSampleSize}" }
        failures = makeFailures(dcontest, orgAssorters)

        // single DH failure
        singleFailure = (failures.size == 1) // We know that the last seat is either PS/4 or MR/5.
        if (singleFailure) {
            nSeatsInPlay = 1
            candidateRanges = CandidateRanges(dcontest, failures)
        } else {
            // count the seats in play
            val contestedWinningSeats = mutableSetOf<Int>()
            val contestedWinningCandidates = mutableListOf<DhondtScore>()
            failures.forEach { failure ->
                val added = contestedWinningSeats.add(failure.winnerScore.winningSeat!!)
                if (added) contestedWinningCandidates.add(failure.winnerScore)
            }
            nSeatsInPlay = contestedWinningSeats.size
            if (nSeatsInPlay > 1) {
                round2(contestedWinningSeats, contestedWinningCandidates)
                candidateRanges = CandidateRanges(dcontest, failures)

            } else {
                candidateRanges = CandidateRanges(dcontest, failures)
            }
        }

        println()
        println(candidateRanges.show())
    }

    fun showRelaxedAssertions() = buildString {
        appendLine("Failures")
        appendLine(DhondtFailure.header())
        failures.forEach { appendLine(it) }
        appendLine()
        append(showRelaxedAssertions2(dcontest, orgAssorters, nsamples, useAlpha))
    }

    fun makeFailures(fromContest: DhondtContest, fromAssorters: List<AssorterIF>): MutableList<DhondtFailure> {
        val failures = mutableListOf<DhondtFailure>()
        fromAssorters.filter { it is DhondtAssorter }.forEach { assorter ->
            val dassorter = assorter as DhondtAssorter
            val risk = estRiskStandardBet(Npop, dassorter.noerror(true), nsamples)
            if (risk > useAlpha) {
                val winnerId = dassorter.winner()
                val loserId = dassorter.loser()
                val winnerScore =
                    fromContest.sortedScores.find { it.divisor == dassorter.winnerDivisor && it.partyId == winnerId }!!
                val loserScore =
                    fromContest.sortedScores.find { it.divisor == dassorter.loserDivisor && it.partyId == loserId }!!

                failures.add(DhondtFailure(Npop, dassorter, winnerScore, loserScore, risk, nsamples, useAlpha))
            }
        }
        return failures
    }

    // So when multiple seats are contested, go to a "second round", adding DH assertions between the contested winning candidates.
    fun round2(contestedWinningSeats: Set<Int>, contestedWinningCandidates: List<DhondtScore>) {
        val candidateMap = dcontest.parties.associateBy { it.id }

        // add DH between contestedWinningCandidates
        // new assorters
        val assorters = mutableListOf<DhondtAssorter>()
        for (winidx in 0 until contestedWinningCandidates.size) {
            for (loseidx in winidx+1 until contestedWinningCandidates.size) {
                val winScore = contestedWinningCandidates[winidx]
                val loseScore = contestedWinningCandidates[loseidx]
                val winParty = candidateMap[winScore.partyId]!!
                val loseParty = candidateMap[loseScore.partyId]!!
                //         fun makeFrom(info: ContestInfo, winner: DhondtParty, loser: DhondtParty, Nc: Int, Npop: Int?=null): DhondtAssorter {
                val assorter = makeDhondtFrom(dcontest.info, winParty, winScore.divisor, loseParty, loseScore.divisor, dcontest.Nc)
                assorters.add(assorter)
            }
        }

        /* println("\nRound2 assorters added")
        // println(DhondtFailure.header())
        assorters.forEach { println("  $it ${estRiskStandardBet(Npop, it.noerror(true), nsamples)}") }
        println() */

        assorters.forEach { dassorter ->
            val risk = estRiskStandardBet(Npop, dassorter.noerror(true), nsamples)
            if (risk > useAlpha) {
                val winnerId = dassorter.winner()
                val loserId = dassorter.loser()
                val winnerScore =
                    dcontest.sortedScores.find { it.divisor == dassorter.winnerDivisor && it.partyId == winnerId }!!
                val loserScore =
                    dcontest.sortedScores.find { it.divisor == dassorter.loserDivisor && it.partyId == loserId }!!
                val failure = DhondtFailure(Npop, dassorter, winnerScore, loserScore, risk, nsamples, useAlpha, round2=true)
                failures.add(failure)
            }
        }
    }

    fun makeDhondtFrom(info: ContestInfo, winner: DhondtParty, winnerDivisor: Int, loser: DhondtParty, loserDivisor: Int, Nc: Int, Npop: Int?=null): DhondtAssorter {
        // Let f_e,s = Te/d(s) for entity e and seat s
        // f_A,WA > f_B,LB, so e = A and s = Wa

        val fw = winner.totalVotes / winnerDivisor.toDouble()
        val fl = loser.totalVotes / loserDivisor.toDouble()
        val voteDiff = (fw - fl)

        val lower = -1.0 / loserDivisor  // lower bound of g
        val upper = 1.0 / winnerDivisor  // upper bound of g
        val c = -1.0 / (2 * lower)  // affine transform h = c * g + 1/2
        val hmeanReported = c * voteDiff/Nc + 0.5
        val hmeanDiluted = c * voteDiff/(Npop ?: Nc) + 0.5

        return DhondtAssorter(
            info,
            winner.id,
            loser.id,
            winnerDivisor = winnerDivisor,
            loserDivisor = loserDivisor
        ).setMeans(hmeanReported, hmeanDiluted)
    }
}

fun maxRiskForWinnerSeat(winnerNameRound: String, assorters: List<AssorterIF>, haveMvrs: Int?, npop: Int): Pair<String, Double> {
    if (haveMvrs == null) return Pair("", 0.0)
    var maxRisk = 0.0
    var maxAssorter = ""
    assorters.filter { it is DhondtAssorter && it.winnerNameRound() == winnerNameRound }.forEach { dassorter ->
        val noerror: Double = 1.0 / (2.0 - dassorter.dilutedMargin() / dassorter.upperBound())
        val estRisk = estRiskStandardBet(npop, noerror, haveMvrs)
        if (estRisk > maxRisk) {
            maxRisk = estRisk
            maxAssorter = dassorter.shortName()
        }
        maxRisk = max(maxRisk, estRisk)
    }
    return Pair(maxAssorter, maxRisk)
}

fun maxRiskForLoserSeat(loserNameRound: String, assorters: List<AssorterIF>, haveMvrs: Int?, npop: Int): Pair<String, Double> {
    if (haveMvrs == null) return Pair("", 0.0)
    var maxRisk = 0.0
    var maxAssorter = ""
    assorters.filter { it is DhondtAssorter && it.loserNameRound() == loserNameRound }.forEach { dassorter ->
        val noerror: Double = 1.0 / (2.0 - dassorter.dilutedMargin() / dassorter.upperBound())
        val estRisk = estRiskStandardBet(npop, noerror, haveMvrs)
        if (estRisk > maxRisk) {
            maxRisk = estRisk
            maxAssorter = dassorter.shortName()
        }
        maxRisk = max(maxRisk, estRisk)
    }
    return Pair(maxAssorter, maxRisk)
}

// would be convenient if dcontest.assorters was correct2
// do we include added asssertions ??
fun showRelaxedAssertions2(dcontest: DhondtContest, assorters: List<AssorterIF>, haveMvrs: Int?, alpha: Double): String = buildString {
    val orgInfo = dcontest.info
    appendLine("winning seats")
    append(" seat ${sfn("winner-round", candNameWidth)}     ${sfn("nvotes", 6)}, ")
    append(" ${sfn(" score", 6)}, scoreDiff, maxRisk, maxAssertion")
    appendLine()

    // sorted scores
    var prevScore: DhondtScore? = null
    // the winners
    repeat(dcontest.nseats) { idx ->
        val score = dcontest.sortedScores[idx]
        // sortedRawScores.filter{ it.divisor <= maxRound }.forEachIndexed { idx, score ->
        val candId = score.partyId
        append(" (${nfn(idx + 1, 2)}) ")
        val nameRound = "${orgInfo.candidateIdToName[candId]!!}/${score.divisor}"
        val below = if (dcontest.partiesBelowThreshold.contains(candId)) "*" else " "
        append(" ${trunc(nameRound, candNameWidth)}$below, ")
        append(" ${nfn(dcontest.votes[candId]!!, 6)}, ${nfn(score.score.toInt(), 6)}, ")
        if (prevScore != null) append("    ${nfn(prevScore.score.toInt() - score.score.toInt(), 6)},")
        else append("          ,")
        val (maxName, maxRisk) = maxRiskForWinnerSeat(nameRound, assorters, haveMvrs, dcontest.Nc)
        append(" ${dfn(maxRisk, 3)}, ")
        if (maxRisk > alpha) append(maxName)
        prevScore = score
        appendLine()
    }
    val lastWinner = prevScore!!

    // the losers
    val losersLeft: Int = dcontest.sortedScores.size - dcontest.nseats
    val maxLosers = min(6, losersLeft)

    repeat(maxLosers) { idx ->
        val scoreRank = dcontest.nseats + idx
        val loser = dcontest.sortedScores[scoreRank]
        val candId = loser.partyId
        append("      ")
        val nameRound = "${orgInfo.candidateIdToName[candId]}/${loser.divisor}"
        val below = if (dcontest.partiesBelowThreshold.contains(candId)) "*" else " "
        append(" ${trunc(nameRound, candNameWidth)}$below, ")
        append(" ${nfn(dcontest.votes[candId]!!, 6)}, ${nfn(loser.score.toInt(), 6)}, ")

        if (prevScore != null) append("    ${nfn(prevScore.score.toInt() - loser.score.toInt(), 6)},")
        else append("          ,")
        val (maxName, maxRisk) = maxRiskForLoserSeat(nameRound, assorters, haveMvrs, dcontest.Nc)
        append(" ${dfn(maxRisk, 3)}, ")
        if (maxRisk > alpha) append(maxName)

        prevScore = loser
        appendLine()
    }
    appendLine()
}