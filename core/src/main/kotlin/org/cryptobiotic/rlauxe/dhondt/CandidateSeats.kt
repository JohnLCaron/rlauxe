package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.persist.SampleLimit
import org.cryptobiotic.rlauxe.util.*
import kotlin.Int
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.max
import kotlin.text.appendLine

val candNameWidth = 20

// assorters that dont satisfy risk because nsamples <= needed
data class DhondtFailure(
    val Npop: Int,
    val assorter: DhondtAssorter,
    val winnerScore: DhondtScore,
    val loserScore: DhondtScore,
    val risk: Double,
    val samplesUsed: Int,
    val alpha: Double,
    val round2: Boolean = false
) {
    val noerror = assorter.noerror(true)

    init {
        if (noerror < 0.5)
            print("")
    }

    fun estMvrs(): Int  {
        return estSampleSizeStandardBet(Npop, noerror, alpha)
    }

    override fun toString() = buildString {
        val assorter = assorter
        append("${sfn(assorter.shortName(), 25)}," )
        append(" ${nfn(winnerScore.winningSeat!!, 11)},")
        append("  ${dfn(noerror, 4)},")
        append(" ${nfn(estMvrs(), 8)}, ${nfn(samplesUsed, 11)}, ${dfn(risk, 4)},")
        if (round2) append(" round2")
    }

    companion object {
        fun header() = "${sfn("name", 25)}, winningSeat, noerror,  estMvrs, samplesUsed,   risk"
    }
}

///////////////////////////////////////////////////////////////////
// figure out candidate seat ranges
// this is for one contest

class CandidateRanges(val dcontest: DhondtContest, val failures: List<DhondtFailure>) {

    val partyRanges: ContestSeats // contest/party seat ranges from all failed assertions
    val partyMap = dcontest.parties.associateBy { it.id }

    init {
        partyRanges = makePartySeatRanges(dcontest, failures)
    }

    fun makePartySeatRanges(dc: DhondtContest, failures: List<DhondtFailure>): ContestSeats {
        val partySeats = mutableMapOf<Int, CandidateSeats>() // one for each candidate
        dc.info.candidateIdToName.forEach { (candId, name) ->
            partySeats[candId] = CandidateSeats(candId, name)
        }
        dc.winnerSeats.forEach { (candId, nseats) ->
            partySeats[candId]!!.reportedSeats = nseats
            partySeats[candId]!!.minSeats = nseats
            partySeats[candId]!!.maxSeats = nseats
        }

        failures.forEach { failure ->
            partySeats[failure.assorter.winner()]!!.failures.add(failure)
            partySeats[failure.assorter.loser()]!!.failures.add(failure)
        }

        // for each seat, can only win 1 or lose 1
        val winners = mutableSetOf<Int>()
        val losers = mutableSetOf<Int>()
        failures.forEach { failure ->
            winners.add(failure.winnerScore.partyId)
            losers.add(failure.loserScore.partyId)
        }
        winners.forEach {
            val win = partySeats[it]!!
            win.minSeats = max(0, win.minSeats - 1) // TODO ??
        }
        losers.forEach {
            val lose = partySeats[it]!!
            lose.maxSeats++
        }

        val failedAssorters = failures.map { it.assorter }
        return ContestSeats(dc.id, partySeats.values.toList(), failedAssorters)
    }

    fun show() = buildString {
        appendLine("CandidateRanges for ${dcontest.name}")
        appendLine(partyRanges.showSeatRanges())
    }
}

// one candidate min/max/reported for this contest
data class CandidateSeats(val candId: Int, val candName: String) {
    var minSeats = 0
    var reportedSeats = 0
    var maxSeats = 0
    val failures = mutableSetOf<DhondtFailure>()

    override fun toString() = buildString {
        appendLine("CandidateSeats(candId=$candId, candName='$candName', minSeats=$minSeats, reportedSeats=$reportedSeats, maxSeats=$maxSeats, failures=${failures.size})")
        failures.forEach { appendLine( "  ${it.assorter.hashcodeDesc()}") }
    }
}

// all candidates min/max/reported for this contest
data class ContestSeats(val contestId:Int, val candidates: List<CandidateSeats>, val failedAssertions: List<AssorterIF>) {

    fun showSeatRanges() = buildString {
        appendLine("ContestId=$contestId")
        appendLine("|                  party     | min | reported | max | nfailures |")
        appendLine("|----------------------------|-----|----------|-----|-----------|")
        candidates.sortedByDescending { it.maxSeats }.forEach {
            append("|  ${trunc("${it.candName}", candNameWidth)} (${nfn(it.candId, 2)}) | ${nfn(it.minSeats, 2)}")
            append("  |    ${nfn(it.reportedSeats, 2)}    | ${nfn(it.maxSeats, 2)}  |")
            appendLine("  ${nfn(it.failures.size, 6)}   |")
        }
    }

    fun nfailures(): Int {
        return candidates.map { it.failures.size }.sum()
    }
}


///////////////////////////////////////////////////////////////////
// this is for all contests in the audit round

fun makeAllSeats(auditRound: AuditRoundIF, contestLimits: List<SampleLimit>, alpha: Double): AllSeats {
    val contestLimitsMap = contestLimits.associateBy { it.id }
    val contestSeats = auditRound.contestRounds.map { contestRound ->
        val sampleLimit = contestLimitsMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        val relax2 = RelaxedAssertions(contestRound, alpha)
        relax2.candidateRanges
    }

    return AllSeats(contestSeats.map { it.partyRanges })
}

// all candidates min/max/reported for all contests
data class AllSeats(val contestSeats: List<ContestSeats>)  {
    val candidateSums: List<CandidateSeats>

    init {
        val sum = mutableMapOf<Int, CandidateSeats>()
        contestSeats.forEach { candRange ->
            candRange.candidates.forEach { range ->
                val sumCandidate = sum.getOrPut(range.candId) { CandidateSeats(range.candId, range.candName) }
                sumCandidate.minSeats += range.minSeats
                sumCandidate.reportedSeats += range.reportedSeats
                sumCandidate.maxSeats += range.maxSeats
                sumCandidate.failures.addAll(range.failures)
            }
        }
        candidateSums = sum.values.toList()
    }

    fun calcCoalition(candidates: Set<Int>, candNames: Map<Int, String>): Coalition {
        val coalition = Coalition(candidates, candNames)
        contestSeats.forEach {
            coalition.addContestSeats(it)
        }
        return coalition
    }

    fun showAllPartySeats() = buildString {
        appendLine("|                party      | min | reported | max |")
        appendLine("|---------------------------|-----|----------|-----|")
        candidateSums.sortedByDescending { it.maxSeats }.forEach {
            append("|  ${trunc("${it.candName} (${nfn(it.candId, 2)})", candNameWidth+4)} | ${nfn(it.minSeats, 2)}")
            appendLine("  |    ${nfn(it.reportedSeats, 2)}    | ${nfn(it.maxSeats, 2)}  |")
        }
        val nseats = candidateSums.sumOf { it.reportedSeats }
        appendLine("\nnseats=$nseats ncands=${candidateSums.size} ")
    }
}

data class Coalition(val candidates: Set<Int>, val candNames: Map<Int, String>) {
    var reportedSeats = 0
    var seatsLost = 0
    var seatsGained = 0
    val losers = mutableListOf<DhondtFailure>() // may want to see where each loss came from
    val winners = mutableListOf<DhondtFailure>() // may want to see where each loss came from
    val nuetral = mutableListOf<DhondtFailure>() // may want to see where each loss came from
    var nfailures = 0

    fun reportedSeats() = reportedSeats
    fun minSeats() = reportedSeats - seatsLost
    fun maxSeats() = reportedSeats + seatsGained
    fun all() = losers + winners + nuetral

    fun addContestSeats(contest: ContestSeats) {
        contest.candidates.forEach { candSeats ->
            if (this.candidates.contains(candSeats.candId)) {
                reportedSeats += candSeats.reportedSeats
                candSeats.failures.forEach { addLoserResult(it) }
            }
        }
    }

    fun addLoserResult(failure: DhondtFailure) {
        val winnerCand = failure.assorter.winner()
        val loserCand = failure.assorter.loser()

        // if the switch stays in the coalition, ignore.
        if (candidates.contains(winnerCand) && !candidates.contains(loserCand)) {
            seatsLost++
            winners.add(failure)
        }
        if (!candidates.contains(winnerCand) && candidates.contains(loserCand)) {
            seatsGained++
            losers.add(failure)
        } else {
            nuetral.add(failure)
        }

        nfailures++
    }

    override fun toString() = buildString {
        val names = candidates.map { candNames[it] }
        appendLine("Coalition Parties: $names ($candidates)")
        appendLine("   minSeats=${minSeats()}, reportedSeats=$reportedSeats, maxSeats=${maxSeats()}")
        appendLine("   Contested Assertions")
        appendLine("        contest,      winner,      loser,    seatChange")
        losers.forEach {
            append("${trunc(it.assorter.info.name, 15)}, ")
            append("${trunc(it.assorter.winnerNameRound(), 11)}, ")
            append("${trunc(it.assorter.loserNameRound(), 11)}")
            appendLine(",     1")
        }
        winners.forEach {
            append("${trunc(it.assorter.info.name, 15)}, ")
            append("${trunc(it.assorter.winnerNameRound(), 11)}, ")
            append("${trunc(it.assorter.loserNameRound(), 11)}")
            appendLine(",    -1")
        }
        nuetral.forEach {
            append("${trunc(it.assorter.info.name, 15)}, ")
            append("${trunc(it.assorter.winnerNameRound(), 11)}, ")
            append("${trunc(it.assorter.loserNameRound(), 11)}")
            appendLine(",     0")
        }
    }
}
