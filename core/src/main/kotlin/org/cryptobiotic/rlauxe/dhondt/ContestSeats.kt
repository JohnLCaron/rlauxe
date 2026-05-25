package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.betting.estRiskStandardBet
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.persist.SampleLimit
import org.cryptobiotic.rlauxe.util.*
import kotlin.Int
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.max
import kotlin.math.min
import kotlin.text.appendLine

// TODO better to look at the AssertionRounds and use the ones not proven, rather than sampleLimit (??)

private val candNameWidth = 20
private val alpha = .05
private val alphaFudge = .05

// TODO same as DhondtRiskFailure
// each failed assorter, thus contest specific
data class DhondtRiskFailure(
    val Npop: Int,
    val assorter: DHondtAssorter,
    val winnerScore: DhondtScore,
    val loserScore: DhondtScore,
    val risk: Double,
    val samplesUsed: Int,
    val alreadyExists: Boolean,
) {
    val noerror = assorter.noerror(true)

    fun estMvrs(): Int  {
        return estSampleSizeStandardBet(Npop, noerror, alpha)
    }

    override fun toString() = buildString {
        val assorter = assorter
        append(" (${nfn(winnerScore.winningSeat!!, 2)})  ")
        append("                                           ")
        append(" ${dfn(noerror, 6)}, ")
        append(" ${nfn(estMvrs(), 8)}, ${nfn(samplesUsed, 8)},    ${dfn(risk, 4)},")
        append(" winner ${assorter.winnerNameRound()} loser ${assorter.loserNameRound()}, $alreadyExists" )
        appendLine()
    }
}

///////////////////////////////////////////////////////////////////

// this is for a single contest, doh!
class CandSeatRangeBuilder2(val contestRound: ContestRound) {
    val dcontest = contestRound.contestUA.contest as DHondtContest
    val assorters = contestRound.contestUA.clcaAssertions.map { it.assorter }
    val orgInfo = dcontest.info
    val belowMinPct = dcontest.belowMinPct
    val votes = dcontest.votes
    val Npop = contestRound.contestUA.Npop
    val nsamples = contestRound.haveSampleSize

    val altContest: DHondtContest?
    val altFailures: List<DhondtRiskFailure>?

    val threshRanges: ContestSeats?
    val mergedRanges: ContestSeats

    init {
        val dhondtFailures = makeRiskFailures(assorters)
        val dhondtRanges = makeCandSeatRanges2(dcontest, dhondtFailures)

        //// threshold assertion failures -> alternate scores
        val wthrashers = mutableListOf<ThresholdRiskFailure2>()
        contestRound.assertionRounds.forEach { ar ->
            val nsamples = contestRound.haveSampleSize
            val assorter = ar.assertion.assorter
            val risk = if (ar.auditResult != null) ar.auditResult!!.pmin else {
                estRiskStandardBet(Npop, assorter.noerror(true), nsamples)
            }
            if (risk > alphaFudge && assorter !is DHondtAssorter) {
                wthrashers.add(ThresholdRiskFailure2(assorter, risk, nsamples))
            }
        }

        // if any thresholds fail, then generate an alternate contest
        // TODO this assumes only one thrasher I think
        if (wthrashers.size > 1) throw RuntimeException("can only have one tfailed hreshold assorter per contest")

        // TODO: if there are n thrashers, there are 2^n possible alternative scores. Generate each and take the min and max over all
        if (wthrashers.isNotEmpty()) {
            altContest = makeAltContest(wthrashers)
            altFailures = makeAltRiskFailures(altContest)
            threshRanges = makeCandSeatRanges2(altContest, altFailures)
            this.mergedRanges = mergeCandSeatRanges(dhondtRanges, threshRanges)

        } else {
            altContest = null
            altFailures = null
            threshRanges = null
            mergedRanges = dhondtRanges
        }
    }

    // do we have to do this for each or all ?
    // thrashers are the thresholds that didnt make their risk limit
    fun makeAltContest(thrashers: List<ThresholdRiskFailure2>): DHondtContest {
        val thrasherIds = thrashers.map { it.assorter.winner() }.toSet()

        // TODO not going through DhondtBuilder, so parties arent complete WHAT THE FUCK ??
        val alt = DHondtContest(orgInfo, votes, dcontest.Nc, dcontest.Ncast, belowMinPct - thrasherIds)
        // recalc assorters
        alt.assorters.addAll(DHondtAssorter.makeDhondtAssorters(orgInfo, alt.Nc, alt.parties))
        return alt
    }

    fun makeRiskFailures(assorters:List<AssorterIF>): List<DhondtRiskFailure> {
        val failures = mutableListOf<DhondtRiskFailure>()
        assorters.filter { it is DHondtAssorter } .forEach { assorter ->
            val dassorter = assorter as DHondtAssorter
            val risk = estRiskStandardBet(Npop, dassorter.noerror(true), nsamples)
            if (risk > alphaFudge) {
                val winnerId = dassorter.winner()
                val loserId = dassorter.loser()
                val winnerScore = dcontest.sortedScores.find { it.divisor == dassorter.lastSeatWon && it.candidate == winnerId }!!
                val loserScore = dcontest.sortedScores.find { it.divisor == dassorter.firstSeatLost && it.candidate == loserId }

                val alreadyExists = assorters.find { it.hashcodeDesc() == dassorter.hashcodeDesc() } != null // ??
                failures.add( DhondtRiskFailure(Npop, dassorter, winnerScore, loserScore!!, risk, nsamples, alreadyExists) )
            }
        }
        return failures
    }

    // why always dhondt ??
    fun makeAltRiskFailures(alt: DHondtContest): List<DhondtRiskFailure> {
        val altFailures = mutableListOf<DhondtRiskFailure>()
        alt.assorters.forEach { assorter ->
            require(assorter is DHondtAssorter)
            val risk = estRiskStandardBet(Npop, assorter.noerror(true), nsamples)
            if (risk > alphaFudge) {
                val winnerId = assorter.winner()
                val loserId = assorter.loser()
                val winnerScore = alt.sortedScores.find { it.divisor == assorter.lastSeatWon && it.candidate == winnerId }!!
                val loserScore = alt.sortedScores.find { it.divisor == assorter.firstSeatLost && it.candidate == loserId }

                val alreadyExists = dcontest.assorters.find { it.hashcodeDesc() == assorter.hashcodeDesc() } != null
                altFailures.add(DhondtRiskFailure(Npop, assorter, winnerScore, loserScore!!, risk, nsamples, alreadyExists))
            }
        }

        return altFailures
    }

    fun makeCandSeatRanges2(dc: DHondtContest, failures: List<DhondtRiskFailure>): ContestSeats {
        val candSeats = mutableMapOf<Int, CandidateSeats>() // one for each candidate
        dc.info.candidateIdToName.forEach { (candId, name) ->
            candSeats[candId] = CandidateSeats(candId, name)
        }
        dc.winnerSeats.forEach { (candId, nseats) ->
            candSeats[candId]!!.reportedSeats = nseats
            candSeats[candId]!!.minSeats = nseats
            candSeats[candId]!!.maxSeats = nseats
        }

        failures.forEach { failure ->
            candSeats[failure.assorter.winner()]!!.failures.add(failure)
            candSeats[failure.assorter.loser()]!!.failures.add(failure)
        }

        // for each seat, can only win 1 or lose 1
        val winners = mutableSetOf<Int>()
        val losers = mutableSetOf<Int>()
        failures.forEach { failure ->
            winners.add(failure.winnerScore.candidate)
            losers.add(failure.loserScore.candidate)
        }
        winners.forEach {
            val win = candSeats[it]!!
            win.minSeats--
        }
        losers.forEach {
            val lose = candSeats[it]!!
            lose.maxSeats++
        }

        return ContestSeats(dc.id, candSeats.values.toList())
    }

    // union of threshRanges into orgRanges
    fun mergeCandSeatRanges(orgRanges: ContestSeats, threshRanges: ContestSeats): ContestSeats {
        if (threshRanges.candidates.isEmpty()) return orgRanges
        orgRanges.candidates.forEach { mergeRange -> // do we know that this has all candidates ??
            val threshRange = threshRanges.candidates.find { it.candId == mergeRange.candId }!! // ??
            mergeRange.minSeats = min(mergeRange.minSeats, threshRange.minSeats)
            mergeRange.maxSeats = max(mergeRange.maxSeats, threshRange.maxSeats)
            mergeRange.failures.addAll(threshRange.failures)
        }

        return orgRanges
    }

    // thrashers are the threshold assorters that didnt make their risk limit
    inner class ThresholdRiskFailure2(val assorter: AssorterIF, val risk: Double, val samplesUsed: Int?) {
        val noerror = assorter.noerror(true)
        val nmvrs = samplesUsed ?: 0
        fun estMvrs(): Int {
            // payoff_noerror = (1 + λ * (noerror − 1/2))  ;  (µ_i is approximately 1/2)
            // payoff_noerror^n > 1/alpha
            // n = 1/ln(alpha) / ln(λ * (noerror − 1/2)); noerror − 1/2 = nomargin/2
            return estSampleSizeStandardBet(Npop, noerror, alpha)
        }

        override fun toString() = buildString {
            append("${assorter.shortName()}: ")
            append(" ${nfn(dcontest.marginInVotes(assorter), 7)}, ${dfn(noerror, 6)}, ")
            append(" ${nfn(estMvrs(), 8)}, ${nfn(nmvrs, 8)},    ${dfn(risk, 4)},")
        }
    }
}

data class CandidateSeats(val candId: Int, val candName: String) {
    var minSeats = 0
    var reportedSeats = 0
    var maxSeats = 0
    val failures = mutableSetOf<DhondtRiskFailure>()

    override fun toString() = buildString {
        appendLine("CandidateSeats(candId=$candId, candName='$candName', minSeats=$minSeats, reportedSeats=$reportedSeats, maxSeats=$maxSeats, failures=${failures.size})")
        failures.forEach { appendLine( "  ${it.assorter.hashcodeDesc()}") }
    }
}

data class ContestSeats(val contestId:Int, val candidates: List<CandidateSeats>) {

    fun showSeatRanges() = buildString {
        appendLine("ContestId=$contestId")
        appendLine("|                party     | min | reported | max | nfailures |")
        appendLine("|--------------------------|-----|----------|-----|-----------|")
        candidates.sortedByDescending { it.maxSeats }.forEach {
            append("|  ${trunc(it.candName, candNameWidth)} ${nfn(it.candId, 2)} | ${nfn(it.minSeats, 2)}")
            append("  |    ${nfn(it.reportedSeats, 2)}    | ${nfn(it.maxSeats, 2)}  |")
            appendLine("  ${nfn(it.failures.size, 6)}   |")
        }
    }
}

///////////////////////////////////////////////////////////////////
// this is for all contests

fun makeContestAndCandidateSeats(auditRound: AuditRoundIF, contestLimits: List<SampleLimit>): AllSeats {
    val contestLimitsMap = contestLimits.associateBy { it.id }
    val contestSeats = auditRound.contestRounds.map { contestRound ->
        val sampleLimit = contestLimitsMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        val builder = CandSeatRangeBuilder2(contestRound)
        builder.mergedRanges
    }

    return AllSeats(contestSeats)
}

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

    fun calcCoalition(candidates: Set<Int>, candNames: Map<Int, String>): Coalition2 {
        val coalition = Coalition2(candidates, candNames)
        contestSeats.forEach {
            coalition.addContestSeats(it)
        }
        return coalition
    }
}

data class Coalition2(val candidates: Set<Int>, val candNames: Map<Int, String>) {
    var reportedSeats = 0
    var seatsLost = 0
    var seatsGained = 0
    val failures = mutableListOf<DhondtRiskFailure>() // may want to see where each loss came from
    var nfailures = 0

    fun reportedSeats() = reportedSeats
    fun minSeats() = reportedSeats - seatsLost
    fun maxSeats() = reportedSeats + seatsGained

    fun addContestSeats(contest: ContestSeats) {
        contest.candidates.forEach { candSeats ->
            if (this.candidates.contains(candSeats.candId)) {
                reportedSeats += candSeats.reportedSeats
                candSeats.failures.forEach { addLoserResult(it) }
            }
        }
    }

    fun addLoserResult(loser: DhondtRiskFailure) {
        val winnerCand = loser.assorter.winner()
        val loserCand = loser.assorter.loser()

        // if the switch stays in the coalition, ignore.
        if (candidates.contains(winnerCand) && !candidates.contains(loserCand)) {
            seatsLost++
            failures.add(loser)
        }
        if (!candidates.contains(winnerCand) && candidates.contains(loserCand)) {
            seatsGained++
            failures.add(loser)
        }
        nfailures++
    }

    override fun toString() = buildString {
        val names = candidates.map { candNames[it] }
        appendLine("Coalition Parties: $names ($candidates)")
        appendLine("   minSeats=${minSeats()}, reportedSeats=$reportedSeats, maxSeats=${maxSeats()}")
        appendLine("   Contested Assertions")
        appendLine("        contest,      winner,      loser")
        failures.forEach { appendLine("${trunc(it.assorter.info.name, 15)}, ${trunc(it.assorter.winnerNameRound(), 11)}, ${trunc(it.assorter.loserNameRound(), 11)}") }
    }
}