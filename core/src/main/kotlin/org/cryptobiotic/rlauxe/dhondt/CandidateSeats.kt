package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.persist.SampleLimit
import org.cryptobiotic.rlauxe.util.*
import kotlin.Int
import kotlin.math.max
import kotlin.math.min
import kotlin.text.appendLine

val candNameWidth = 20

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
// this is for one contest

class ContestRanges(val dcontest: DhondtContest, val failures: List<DhondtFailure>) {
    // all party's min/max/reported
    val partyRanges = mutableMapOf<Int, PartyRange>() // one for each candidate

    init {
        dcontest.parties.forEach { party ->
            partyRanges[party.id] = PartyRange(party.id)
        }
        dcontest.winnerSeats.forEach { partyId, nseats ->
            partyRanges[partyId]!!.setReportedSeats2(nseats)
        }
        addFailingAssertion(failures)
    }

    fun mergeAltContest(alt: ContestRanges) {
        alt.partyRanges.values.forEach { altRange ->
            val myRange = this.partyRanges[altRange.partyId]!!
            myRange.expandRange(altRange)
        }
    }

    /* Failed DH assertions divide the seats into 3 groups.
    **Yellow seats**: Seats that have a failing assertion.
    **Green seats**: Seats above the yellow are definite winners. Each is either blocked by a seat of the same party below it,
    or if it is the lowest winning candidate, has a non-failing assertion for every other candidate, which confirms its winning status.
    **Red seats**: Seats below the yellow are definite losers. Each is either blocked by a seat of the same party above it, or if it
    is the highest losing candidate, has a non-failing assertion for every other candidate, which confirms its losing status.

    When some assertions fail, we can calculate for each party the possible range of seats that might be awarded.

    For the seats that are in play, form the "reported yellow set" as the set of winning candidates in the yellow seats.
    This set has n unique candidates when there are n yellow seats.

    The number of seats that a party might lose is bounded by the number of candidates it has in the reported yellow set.

    For each failed assertion, add the losing candidate to the "failed assertion candidate" set. This set has a maximum of
    k unique candidates when there are k failed assertions. It may have less.

    The number of seats that a party might gain is bounded by the number of candidates it has in the failed assertion candidate set.

    These are conservative estimates, further work may be able to tighten these bounds. For example, it may be possible to prove that a winning candidate in the yellow seats must still win a seat at a lower rank.
    */
    fun addFailingAssertion(failures: List<DhondtFailure>) {
        val reportedYellowSet = mutableSetOf<DhondtCandidateScore>()
        val hopefulCandidates = mutableSetOf<DhondtCandidateScore>()

        // For the seats that are in play, form the "reported yellow set" as the set of winning candidates in the yellow seats.
        // For each failed assertion, add the losing candidate to the "failed assertion candidate" set.
        failures.forEach { failure ->
            reportedYellowSet.add(failure.winnerScore)
            if (!failure.round2) hopefulCandidates.add(failure.loserScore) // TODO round2
        }

        // The number of seats that a party might lose is bounded by the number of candidates it has in the reported yellow set.
        reportedYellowSet.forEach { candidate ->
            val partyRange: PartyRange = partyRanges[candidate.partyId]!!
            partyRange.minSeats--
            if (partyRange.minSeats < 0) throw RuntimeException("partyRange.minSeats < 0")
        }

        // The number of seats that a party might gain is bounded by the number of candidates it has in the failed assertion candidate set.
        hopefulCandidates.forEach { candidate ->
            val partyRange: PartyRange = partyRanges[candidate.partyId]!!
            partyRange.maxSeats++
        }
    }

    /*
    fun buildContestSeats(): ContestSeats {
        return ContestSeats(dc.id, partySeats.values.toList(), failedAssorters)
    } */

    fun showSeatRanges() = buildString {
        appendLine("ContestId=${dcontest.id}")
        appendLine("| party | min | reported | max | nfailures |")
        appendLine("|-------|-----|----------|-----|-----------|")
        partyRanges.values.sortedByDescending { it.maxSeats }.forEach {
            append("|    ${nfn(it.partyId, 2)} | ${nfn(it.minSeats, 2)}")
            appendLine("  |    ${nfn(it.reportedSeats, 2)}    | ${nfn(it.maxSeats, 2)}  |")
            // appendLine("  ${nfn(it.failures.size, 6)}   |")
        }
    }
}

// one party's min/max/reported
data class PartyRange(val partyId: Int) {
    var minSeats = 0
    var reportedSeats = 0
    var maxSeats = 0

    fun setReportedSeats2(nseats: Int) {
        reportedSeats = nseats
        minSeats = nseats
        maxSeats = nseats
    }

    fun expandRange(altSeats: PartyRange) {
        minSeats = min(minSeats, altSeats.minSeats)
        maxSeats = max(maxSeats, altSeats.maxSeats)
    }

    override fun toString() = buildString {
        appendLine("PartyRange(candId=$partyId, partyId='$partyId', minSeats=$minSeats, reportedSeats=$reportedSeats, maxSeats=$maxSeats")
        // failures.forEach { appendLine( "  ${it.assorter.hashcodeDesc()}") }
    }
}

// seems to be the same as ContestRanges, but used across contests ??
/* all candidates min/max/reported for this contest
data class ContestSeats(val contestId:Int, val candidates: List<PartyRange>, val failedAssertions: List<AssorterIF>) {

    fun showSeatRanges() = buildString {
        appendLine("ContestId=$contestId")
        appendLine("|                  party     | min | reported | max | nfailures |")
        appendLine("|----------------------------|-----|----------|-----|-----------|")
        candidates.sortedByDescending { it.maxSeats }.forEach {
            append("|  ${trunc("${it.partyId}", candNameWidth)} (${nfn(it.partyId, 2)}) | ${nfn(it.minSeats, 2)}")
            appendLine("  |    ${nfn(it.reportedSeats, 2)}    | ${nfn(it.maxSeats, 2)}  |")
            // appendLine("  ${nfn(it.failures.size, 6)}   |")
        }
    }

    /* fun nfailures(): Int {
        return candidates.map { it.failures.size }.sum()
    } */
} */


///////////////////////////////////////////////////////////////////
// this is for all contests in the audit round

fun makeAllSeats(auditRound: AuditRoundIF, contestLimits: List<SampleLimit>, alpha: Double): AllSeats {
    val contestLimitsMap = contestLimits.associateBy { it.id }
    val contestRanges = auditRound.contestRounds.map { contestRound ->
        val sampleLimit = contestLimitsMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        makeRelaxedAssertions(contestRound, alpha).contestRanges()
    }

    return AllSeats(contestRanges)
}

// all candidates min/max/reported for all contests
data class AllSeats(val contestSeats: List<ContestRanges>)  {
    val candidateSums: List<PartyRange>

    init {
        val sum = mutableMapOf<Int, PartyRange>()
        contestSeats.forEach { candRange ->
            candRange.partyRanges.values.forEach { range ->
                val sumCandidate = sum.getOrPut(range.partyId) { PartyRange(range.partyId) }
                sumCandidate.minSeats += range.minSeats
                sumCandidate.reportedSeats += range.reportedSeats
                sumCandidate.maxSeats += range.maxSeats
                // sumCandidate.failures.addAll(range.failures)
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
            append("|  ${trunc("${it.partyId} (${nfn(it.partyId, 2)})", candNameWidth+4)} | ${nfn(it.minSeats, 2)}")
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

    fun addContestSeats(contest: ContestRanges) {
        contest.partyRanges.values.forEach { candSeats ->
            if (this.candidates.contains(candSeats.partyId)) {
                reportedSeats += candSeats.reportedSeats
                // HEY candSeats.failures.forEach { addLoserResult(it) }
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
