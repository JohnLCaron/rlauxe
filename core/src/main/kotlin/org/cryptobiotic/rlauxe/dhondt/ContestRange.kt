package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.persist.SampleLimit
import org.cryptobiotic.rlauxe.util.*
import kotlin.Int
import kotlin.math.max
import kotlin.math.min
import kotlin.text.appendLine

val candNameWidth = 20

///////////////////////////////////////////////////////////////////
// all party's min/max/reported for one contest
class ContestRange(val dcontest: DhondtContest) {
    private val partyMap = mutableMapOf<Int, PartyRange>() // one for each candidate
    private val dhFailures = mutableListOf<DhondtFailure>()

    init {
        // all parties have an entry
        dcontest.parties.forEach { party ->
            partyMap[party.id] = PartyRange(party.id, party.partyName)
        }
        dcontest.winnerSeatCount.forEach { partyId, nseats ->
            partyMap[partyId]!!.setReportedWinners(nseats)
        }
    }

    fun dhFail() = dhFailures.size

    // these come from failed BelowThreshold assertions
    fun mergeAltContestRange(altRange: ContestRange) {
        altRange.partyMap.values.forEach { altRange ->
            val myRange = this.partyMap[altRange.partyId]!!
            myRange.expandRange(altRange)
        }
    }

    fun addFailure(failure: DhondtFailure) {
        dhFailures.add(failure)
    }

    fun addFailures(failures: List<DhondtFailure>) {
        dhFailures.addAll(failures)
    }

    fun partyRanges() = partyMap.values.toList()

    // dont compute until all failures are added
    fun computeRangesFromFailures(): ContestRange {
        val uncertainWinnerSet = mutableSetOf<DhondtCandidateScore>()
        val uncertainLoserSet = mutableSetOf<DhondtCandidateScore>()

        // For the seats that are in play, form the "reported yellow set" as the set of winning candidates in the yellow seats.
        // For each failed assertion, add the losing candidate to the "failed assertion candidate" set.
        dhFailures.forEach { failure ->
            uncertainWinnerSet.add(failure.winnerScore)
            uncertainLoserSet.add(failure.loserScore)
        }

        // The number of seats that a party might lose is bounded by the number of unique candidates it has in the failed winner candidates.
        uncertainWinnerSet.forEach { candidate ->
            val partyRange: PartyRange = partyMap[candidate.partyId]!!
            partyRange.minSeats--
            if (partyRange.minSeats < 0) throw RuntimeException("partyRange.minSeats < 0") // do not silently fail
        }

        // The number of seats that a party might gain is bounded by the number of unique candidates it has in the failed loser candidates.
        uncertainLoserSet.forEach { candidate ->
            val partyRange: PartyRange = partyMap[candidate.partyId]!!
            partyRange.maxSeats++
        }

        return this
    }

    fun showSeatRanges() = buildString {
        val nameMap = dcontest.info.candidateIdToName
        appendLine("ContestId=${dcontest.id}")
        appendLine("| ${trunc("party", 25)} | min | reported | max |")
        appendLine("|-${"-".repeat(25)}-|-----|----------|-----|")
        partyRanges().sortedByDescending { it.maxSeats }.forEach {
            val name = nameMap[it.partyId] ?: "unknown"
            append("| ${trunc(name, 20)} (${nfn(it.partyId, 2)}) | ${nfn(it.minSeats, 2)}")
            appendLine("  |    ${nfn(it.reportedSeats, 2)}    | ${nfn(it.maxSeats, 2)}  |")
            // appendLine("  ${nfn(it.failures.size, 6)}   |")
        }
    }

    companion object {
        fun fromFailures(dcontest: DhondtContest, failures: List<DhondtFailure>): ContestRange {
            val cr = ContestRange(dcontest)
            cr.addFailures(failures)
            cr.computeRangesFromFailures()
            return cr
        }
    }
}


// one party's min/max/reported
class PartyRange(val partyId: Int, val partyName: String) {
    var minSeats = 0
    var reportedSeats = 0
    var maxSeats = 0

    fun setReportedWinners(nseats: Int) {
        reportedSeats = nseats
        minSeats = nseats
        maxSeats = nseats
    }

    fun expandRange(altSeats: PartyRange) {
        minSeats = min(minSeats, altSeats.minSeats)
        maxSeats = max(maxSeats, altSeats.maxSeats)
    }

    override fun toString() = buildString {
        appendLine("PartyRange(name=$partyName, partyId='$partyId', minSeats=$minSeats, reportedSeats=$reportedSeats, maxSeats=$maxSeats")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PartyRange

        if (partyId != other.partyId) return false
        if (minSeats != other.minSeats) return false
        if (reportedSeats != other.reportedSeats) return false
        if (maxSeats != other.maxSeats) return false
        if (partyName != other.partyName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = partyId
        result = 31 * result + minSeats
        result = 31 * result + reportedSeats
        result = 31 * result + maxSeats
        result = 31 * result + partyName.hashCode()
        return result
    }
}

///////////////////////////////////////////////////////////////////
// this is for all contests in the audit round

fun makeAllSeatsFromRound(auditRound: AuditRoundIF, contestLimits: List<SampleLimit>, alpha: Double): AllSeats {
    val contestLimitsMap = contestLimits.associateBy { it.id }
    val contestRanges = auditRound.contestRounds.map { contestRound ->
        val sampleLimit = contestLimitsMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        val relax = makeRelaxedAssertions(contestRound, alpha)
        relax.totalContestRange()
    }

    return AllSeats(contestRanges)
}

// all party's min/max/reported for all contests
// assume contestRanges have been computed
data class AllSeats(val contestRanges: List<ContestRange>)  {
    val partySums: List<PartyRange>

    init {
        val sum = mutableMapOf<Int, PartyRange>()
        contestRanges.forEach { candRange ->
            candRange.partyRanges().forEach { range ->
                val sumCandidate = sum.getOrPut(range.partyId) { PartyRange(range.partyId, range.partyName) }
                sumCandidate.minSeats += range.minSeats
                sumCandidate.reportedSeats += range.reportedSeats
                sumCandidate.maxSeats += range.maxSeats
            }
        }
        partySums = sum.values.toList()
    }

    fun calcCoalition(candidates: Set<Int>, candNames: Map<Int, String>): Coalition {
        val coalition = Coalition(candidates, candNames)
        contestRanges.forEach {
            coalition.addContestSeats(it)
        }
        return coalition
    }

    fun showAllPartySeats(partyName: Map<Int,String>) = buildString {
        appendLine("|                party      | min | reported | max |")
        appendLine("|---------------------------|-----|----------|-----|")
        partySums.sortedByDescending { it.maxSeats }.forEach {
            append("|  ${trunc("${partyName[it.partyId]} (${nfn(it.partyId, 2)})", candNameWidth+4)} | ${nfn(it.minSeats, 2)}")
            appendLine("  |    ${nfn(it.reportedSeats, 2)}    | ${nfn(it.maxSeats, 2)}  |")
        }
        val nseats = partySums.sumOf { it.reportedSeats }
        appendLine("\nnseats=$nseats ncands=${partySums.size} ")
    }
}

// compute the party sums for some subset of all the parties
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

    fun addContestSeats(contest: ContestRange) {
        contest.partyRanges().forEach { candSeats ->
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
