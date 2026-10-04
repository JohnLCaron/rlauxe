package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.betting.estRiskStandardBet
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.math.max
import kotlin.math.min

interface RelaxedAssertionsIF {
    val orgContest: DhondtContest
    val Npop: Int
    val nsamples: Int
    val alpha: Double

    fun altContests(): List<AltContest>
    fun assortersForProof(): List<AssorterIF>
    fun totalContestRange(): ContestRange // TODO just return PartyRanges ??
    fun failures(): List<DhondtFailure>
    fun tfailures(): List<ThresholdFailure>
    fun show(): String
}

data class AltContest(
    val name: String,
    val altContest: DhondtContest,
    val altAssorters: List<AssorterIF>,
    val contestRange: ContestRange,
    val dhFail: Int,
    val tFail: Int,
    )

class NoFailures(override val orgContest: DhondtContest) : RelaxedAssertionsIF {
    override val Npop = 0
    override val nsamples = 0
    override val alpha = .05

    override fun altContests() = emptyList<AltContest>()
    override fun assortersForProof() = emptyList<AssorterIF>()
    override fun totalContestRange() = ContestRange(orgContest).computeRangesFromFailures()
    override fun failures() = emptyList<DhondtFailure>()
    override fun tfailures() = emptyList<ThresholdFailure>()
    override fun show() = "No Failures"
}

// assorters that dont satisfy risk because nsamples <= needed
data class DhondtFailure(
    val Npop: Int,
    val assorter: DhondtAssorter,
    val winnerScore: DhondtCandidateScore,
    val loserScore: DhondtCandidateScore,
    val risk: Double,
    val samplesUsed: Int,
    val alpha: Double,
    val round2: Boolean = false
) {
    val noerror = assorter.noerror(true)

    val estSamples = estSampleSizeStandardBet(Npop, noerror, 0.05)

    fun estMvrs(): Int  {
        return estSampleSizeStandardBet(Npop, noerror, alpha)
    }

    override fun toString() = buildString {
        val assorter = assorter
        append("${sfn(assorter.shortName(), 25)}," )
        append(" ${nfn(winnerScore.winningSeat!!, 11)},")
        append("  ${dfn(noerror, 4)},")
        append(" ${nfn(estMvrs(), 8)}, ${nfn(samplesUsed, 11)}, ${dfn(risk, 4)}, ${nfn(estSamples, 4)},")
        if (round2) append(" round2")
    }

    companion object {
        fun header() = "${sfn("name", 25)}, winningSeat, noerror,  estMvrs, samplesUsed,   risk"
    }
}

// threshold assorters that dont satisfy risk because nsamples <= needed

class ThresholdFailure(
    val tcontest: DhondtContest,
    val Npop: Int,
    val assorter: AssorterIF, // BelowThreshold or AboveThreshold
    val risk: Double,
    val samplesUsed: Int,
    val alpha: Double
) {
    val noerror = assorter.noerror(true)
    val nmvrs = samplesUsed

    fun estMvrs(): Int {
        return estSampleSizeStandardBet(Npop, noerror, alpha)
    }

    override fun toString() = buildString {
        val assorter = assorter
        append("${sfn(assorter.shortName(), 25)}," )
        append(" ${nfn(tcontest.marginInVotes(assorter), 11)},")
        append("  ${dfn(noerror, 4)},")
        append(" ${nfn(estMvrs(), 8)}, ${nfn(samplesUsed, 11)}, ${dfn(risk, 4)}") // , ${nfn(estSamples, 4)},")
    }

    fun toString2() = buildString {
        append("${assorter.shortName()}: ")
        append(" ${nfn(tcontest.marginInVotes(assorter), 7)}, ${dfn(noerror, 6)}, ")
        append(" ${nfn(estMvrs(), 8)}, ${nfn(nmvrs, 8)},    ${dfn(risk, 4)},")
    }
}

class FailureFinder(val Npop: Int, val nsamples: Int, val alpha: Double) {

    fun findDhondtFailures(fromContest: DhondtContest, fromAssorters: List<AssorterIF>): MutableList<DhondtFailure> {
        val failures = mutableListOf<DhondtFailure>()
        fromAssorters.filter { it is DhondtAssorter }.forEach { assorter ->
            val dassorter = assorter as DhondtAssorter
            val risk = estRiskStandardBet(Npop, dassorter.noerror(true), nsamples)
            if (risk > alpha) {
                val winnerId = dassorter.winner()
                val loserId = dassorter.loser()
                val winnerScore =
                    fromContest.sortedScores.find { it.divisor == dassorter.winnerDivisor && it.partyId == winnerId }!!
                val loserScore =
                    fromContest.sortedScores.find { it.divisor == dassorter.loserDivisor && it.partyId == loserId }

                if (loserScore == null)
                    print("")
                failures.add(DhondtFailure(Npop, dassorter, winnerScore, loserScore!!, risk, nsamples, alpha))
            }
        }
        return failures
    }

    fun findThresholdFailures(dcontest: DhondtContest, fromAssorters: List<AssorterIF>): MutableList<ThresholdFailure> {
        val failures = mutableListOf<ThresholdFailure>()
        fromAssorters.filter { it !is DhondtAssorter }.forEach { assorter ->
            val risk = estRiskStandardBet(Npop, assorter.noerror(true), nsamples)
            if (risk > alpha) {
                failures.add(ThresholdFailure(dcontest, dcontest.Nc, assorter, risk, nsamples, alpha))
            }
        }
        return failures
    }
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////

fun makeRelaxedAssertions(contestRound: ContestRound, alpha: Double, mvrLimit: Int? = null, version: String? = null): RelaxedAssertionsIF {
    val useAlpha = contestRound.auditorWantRisk ?: alpha
    val orgContest = contestRound.contestUA.contest as DhondtContest
    // must get from contestRound.contestUA, not auditRecord.contests
    val orgAssorters = contestRound.contestUA.clcaAssertions.map { it.assorter }
    val Npop = contestRound.contestUA.Npop
    val nsamples = mvrLimit ?: contestRound.haveSampleSize

    val failureFinder = FailureFinder(Npop, nsamples, useAlpha)
    val tfailures = failureFinder.findThresholdFailures(orgContest, orgAssorters)
    val failures = failureFinder.findDhondtFailures(orgContest, orgAssorters)

    return when {
        (tfailures.isEmpty() && failures.isEmpty()) -> NoFailures(orgContest)
        // (tfailures.isEmpty() && version == "useV") -> RelaxedAssertionsV(orgContest, Npop, nsamples, useAlpha, failures)
        else -> MakeRelaxedAssertions(orgContest, orgAssorters, Npop, nsamples, useAlpha, failureFinder, version)
        //  else -> ThresholdAssertionsV(orgContest, Npop, nsamples, useAlpha, failures, tfailures)
    }
}
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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

fun showCandidateSeatOrder(dcontest: DhondtContest, assorters: List<AssorterIF>, haveMvrs: Int?, alpha: Double): String = buildString {
    val orgInfo = dcontest.info
    // appendLine("candidate/seat order")
    append(" seat ${sfn("winner-round", candNameWidth)}     ${sfn("nvotes", 6)}, ")
    append(" ${sfn(" score", 6)}, scoreDiff, maxRisk, maxAssertion")
    appendLine()

    // sorted scores
    var prevScore: DhondtCandidateScore? = null
    // the winners
    repeat(dcontest.nseats) { idx ->
        val score = dcontest.sortedScores[idx]
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

// make table 5 from Vaness'a paper
fun showTable5(dcontest: DhondtContest, failures: List<DhondtFailure>): String = buildString {
    appendLine("Table 5")

    val yellowWinners = failures.mapIndexed { idx, df -> Pair(df.assorter.winnerNameRound(), idx+1) }
    val yellowLosers = failures.mapIndexed { idx, df -> Pair(df.assorter.loserNameRound(), idx+1) }

    appendLine("yellowWinners: ${yellowWinners}")
    appendLine("yellowLosers: ${yellowLosers}")

    val names = dcontest.info.candidateIdToName
    val nameWidth = 8
    val winningSeats = dcontest.winnerSeatCount.toList().sortedByDescending{ it.second }.toMap()
    appendLine("${dcontest.name}: ${winningSeats}")
    appendLine()
    append("|seat|")

    winningSeats.forEach { (id, _) ->
        val name = names[id]!!
        append(" ${trunc(name, nameWidth)} |")
    }
    appendLine()

    append("|----|")
    winningSeats.forEach { append("-${"-".repeat(nameWidth)}-|") }
    appendLine()

    var more = true
    var seatIdx = 0
    while (more) {
        append("| ${nfn(seatIdx+1, 2)} |")
        winningSeats.forEach { (id, nseats) ->
            val candidate = "${names[id]}/${seatIdx+1}"
            val yellow: Set<Int> = yellowWinners.filter { it.first == candidate }.map{ it.second }.toSet()

            val color = when {
                (yellow.isNotEmpty()) -> "Y${yellow}"
                (seatIdx < nseats) -> "G"
                else -> " "
            }
            append(" ${trunc(color, nameWidth)} |")
        }
        appendLine()
        seatIdx++
        more = winningSeats.any { it.value >= seatIdx }
    }
    val maxseats = seatIdx

    more = true
    seatIdx = 0
    while (more) {
        append("| ${nfn(seatIdx+1, 2)} |")
        winningSeats.forEach { (id, nseats) ->
            val candidate = "${names[id]}/${seatIdx+1}"
            val yellow: Set<Int> = yellowLosers.filter { it.first == candidate }.map{ it.second }.toSet()

            val color = when {
                (yellow.isNotEmpty()) -> "Y${yellow}"
                (seatIdx > nseats-1) -> "R"
                else -> " "
            }
            append(" ${trunc(color, nameWidth)} |")
        }
        appendLine()
        seatIdx++
        more = seatIdx < maxseats + 1
    }
}
