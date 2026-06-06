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
import kotlin.collections.mapIndexed
import kotlin.math.max
import kotlin.math.min
import kotlin.text.appendLine

val candNameWidth = 20
val alpha = .05
val alphaFudge = .05

data class DhondtRiskFailure(
    val Npop: Int,
    val assorter: DHondtAssorter,
    val winnerScore: DhondtScore,
    val loserScore: DhondtScore,
    val risk: Double,
    val samplesUsed: Int,
    val alreadyExists: Boolean, // ??
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

class ThresholdRiskFailure(
    val dcontest: DHondtContest,
    val Npop: Int,
    val assorter: AssorterIF, // always BelowThreshold?
    val risk: Double, 
    val samplesUsed: Int?
) {
    val noerror = assorter.noerror(true)
    val nmvrs = samplesUsed ?: 0
    fun estMvrs(): Int {
        return estSampleSizeStandardBet(Npop, noerror, alpha)
    }

    override fun toString() = buildString {
        append("${assorter.shortName()}: ")
        append(" ${nfn(dcontest.marginInVotes(assorter), 7)}, ${dfn(noerror, 6)}, ")
        append(" ${nfn(estMvrs(), 8)}, ${nfn(nmvrs, 8)},    ${dfn(risk, 4)},")
    }
}

///////////////////////////////////////////////////////////////////
// this is for one contest

class CandSeatRangeBuilder(val contestRound: ContestRound) {
    val dcontest = contestRound.contestUA.contest as DHondtContest
    val assorters = contestRound.contestUA.clcaAssertions.map { it.assorter }
    val orgInfo = dcontest.info
    val belowMinPct = dcontest.partiesBelowThreshold
    val votes = dcontest.votes
    val Npop = contestRound.contestUA.Npop
    val nsamples = contestRound.haveSampleSize // if this changes, need to redo

    val failureNodes: List<AltNode>
    val dhondtRanges: ContestSeats // range with just dhondt failures

    //val thrashers: List<ThresholdRiskFailure>
    //val altThrashContests: List<AltContest>
    var mergedRanges = ContestSeats(0, emptyList())

    init {
        // dhondt assertion failures
        failureNodes = makeFailureNodes(assorters)

        // I think we want breadth first
        val assertionsDone = mutableSetOf<String>()
        var allDone = true
        failureNodes.forEach {
            val done = it.addChildren(assertionsDone)
            allDone = allDone && done
        }

        var allChildren: List<AltNode> = failureNodes
        while (!allDone) {
            allChildren = allChildren.map { it.children }.flatten()
            allDone = true
            allChildren.forEach {
                val done = it.addChildren(assertionsDone)
                allDone = allDone && done
            }
        }

        dhondtRanges = makeCandSeatRanges(dcontest, failureNodes)

        /* threshold assertion failures
        val wthrashers = mutableListOf<ThresholdRiskFailure>()
        contestRound.assertionRounds.forEach { ar ->
            val nsamples = contestRound.haveSampleSize
            val assorter = ar.assertion.assorter
            val risk = if (ar.auditResult != null) ar.auditResult!!.pmin else {
                estRiskStandardBet(Npop, assorter.noerror(true), nsamples)
            }
            if (risk > alphaFudge && assorter !is DHondtAssorter) {
                wthrashers.add(ThresholdRiskFailure(dcontest, Npop, assorter, risk, nsamples))
            }
        }
        thrashers = wthrashers.toList()

        // TODO: if there are n thrashers, there are 2^n possible alternative scores. Generate each and take the min and max over all
        // TODO this assumes only one thrasher for now
        if (wthrashers.size > 1) throw RuntimeException("currently can only have one failed threshold assorter per contest")
        altThrashContests = wthrashers.map { makeAltThrasherContest(it) }
*/
        mergedRanges = dhondtRanges
    }

    fun makeFailureNodes(assorters: List<AssorterIF>): List<AltNode> {
        val failures = mutableListOf<DhondtRiskFailure>()
        assorters.filter { it is DHondtAssorter }.forEach { assorter ->
            val dassorter = assorter as DHondtAssorter
            val risk = estRiskStandardBet(Npop, dassorter.noerror(true), nsamples)
            if (risk > alphaFudge) {
                val winnerId = dassorter.winner()
                val loserId = dassorter.loser()
                val winnerScore =
                    dcontest.sortedScores.find { it.divisor == dassorter.lastSeatWon && it.candidate == winnerId }!!
                val loserScore =
                    dcontest.sortedScores.find { it.divisor == dassorter.firstSeatLost && it.candidate == loserId }!!

                val alreadyExists = assorters.find { it.hashcodeDesc() == dassorter.hashcodeDesc() } != null // ??
                failures.add(DhondtRiskFailure(Npop, dassorter, winnerScore, loserScore, risk, nsamples, alreadyExists))
            }
        }
        return failures.mapIndexed { idx, it -> AltNode("root.node${idx+1}", dcontest, it) }
    }

    fun makeCandSeatRanges(dc: DHondtContest, failureNodes: List<AltNode>): ContestSeats {
        val candSeats = mutableMapOf<Int, CandidateSeats>() // one for each candidate
        dc.info.candidateIdToName.forEach { (candId, name) ->
            candSeats[candId] = CandidateSeats(candId, name)
        }
        dc.winnerSeats.forEach { (candId, nseats) ->
            candSeats[candId]!!.reportedSeats = nseats
            candSeats[candId]!!.minSeats = nseats
            candSeats[candId]!!.maxSeats = nseats
        }

        var workingNodes: List<AltNode> = failureNodes
        while (workingNodes.isNotEmpty()) {
            workingNodes.forEach {
                val failure = it.failure
                candSeats[failure.assorter.winner()]!!.failures.add(failure)
                candSeats[failure.assorter.loser()]!!.failures.add(failure)
            }
            workingNodes = workingNodes.map { it.children }.flatten()
        }

        // for each seat, can only win 1 or lose 1
        val winners = mutableSetOf<Int>()
        val losers = mutableSetOf<Int>()
        failureNodes.forEach { altNode ->
            winners.add(altNode.failure.winnerScore.candidate)
            losers.add(altNode.failure.loserScore.candidate)
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

    // TODO
    fun makeAltThrasherContest(thrasher: ThresholdRiskFailure): AltContest {
        val alt = DHondtContest.fromVotes(
            orgInfo,
            votes,
            dcontest.Nc,
            dcontest.Ncast,
            belowMinPct - setOf(thrasher.assorter.winner())
        )
        // recalc assorters TODO not going through DhondtBuilder, so parties arent complete WHAT THE FUCK ??
        alt.assorters.addAll(DHondtAssorter.makeDhondtAssorters(orgInfo, alt.Nc, alt.parties))
        return AltContest(alt, thrasher = thrasher)
    }

    inner class AltNode(val name: String, fromContest: DHondtContest, val failure: DhondtRiskFailure) {
        val altContest: AltContest
        val children = mutableListOf<AltNode>()

        init {
            // in order to flip the winner/loser assertion, youd have to change the reported votes / margin
            // and all the changed assertions would depend on what the score gap is.

            // lets just manipuate the lastSeatWon/firstSeatLost
            val winner = failure.assorter.winner()
            val loser = failure.assorter.loser()

            val parties = fromContest.parties.toList()
            val winnerParty = parties.find { it.id == winner }!!
            val loserParty = parties.find { it.id == loser }!!

            winnerParty.firstSeatLost = winnerParty.lastSeatWon
            winnerParty.lastSeatWon = if (winnerParty.lastSeatWon!! > 0) winnerParty.lastSeatWon!! - 1 else null

            loserParty.lastSeatWon = loserParty.firstSeatLost
            loserParty.firstSeatLost = loserParty.firstSeatLost!! + 1

            // then we have to manipulate the sortedScores (!)
            val nseats = fromContest.info.nwinners
            val sortedScoresCalc =
                assignWinners(parties, nseats, fromContest.Nc, fromContest.info.minFraction!!, null, flip = true)
            // sortedScoresCalc.forEachIndexed { idx, it -> println(" $idx $it") }

            val dalt = DHondtContest(
                fromContest.info, fromContest.votes, fromContest.Nc, fromContest.Ncast,
                parties,
                sortedScoresCalc
            )

            val assorters = DHondtAssorter.makeDhondtAssorters(fromContest.info, dalt.Nc, dalt.parties)
            dalt.assorters.addAll(assorters)

            altContest = AltContest(dalt, failure = failure)
            // do as seperate step  children = altContest.dhondtFailures.mapIndexed { idx, it -> AltNode("name-$idx", alt, it) }
        }

        // add all the failures from altContest
        fun addChildren(alreadyDone: MutableSet<String>): Boolean {
            alreadyDone.add(altContest.failure!!.assorter.shortName())
            alreadyDone.add(altContest.failure!!.assorter.reverseName())

            var idx = 1
            altContest.dhondtFailures.forEach { failure ->
                val skip = alreadyDone.contains(failure.assorter.shortName())

                if (!skip) {
                    val child = AltNode("${this.name}-$idx", altContest.alt, failure)
                    alreadyDone.add(failure.assorter.shortName())
                    alreadyDone.add(failure.assorter.reverseName())
                    children.add(child)
                    idx++
                }
            }
            return idx == 1 // true == done
        }
    }

    inner class AltContest(
        val alt: DHondtContest,
        val failure: DhondtRiskFailure? = null,
        val thrasher: ThresholdRiskFailure? = null
    ) {
        val dhondtFailures: List<DhondtRiskFailure>
        //val threshRanges: ContestSeats
        // val mergedRanges = ContestSeats(0, emptyList())

        init {
            dhondtFailures = makeAltRiskFailures()
            // threshRanges = makeCandSeatRanges(alt, dhondtFailures)
            // this.mergedRanges = mergeCandSeatRanges(dhondtRanges, threshRanges)
        }

        fun makeAltRiskFailures(): List<DhondtRiskFailure> {
            val altFailures = mutableListOf<DhondtRiskFailure>()
            alt.assorters.forEach { assorter ->
                require(assorter is DHondtAssorter)
                val risk = estRiskStandardBet(Npop, assorter.noerror(true), nsamples)
                if (risk > alphaFudge) {
                    val winnerId = assorter.winner()
                    val loserId = assorter.loser()
                    val winnerScore =
                        alt.sortedScores.find { it.divisor == assorter.lastSeatWon && it.candidate == winnerId }!!
                    val loserScore =
                        alt.sortedScores.find { it.divisor == assorter.firstSeatLost && it.candidate == loserId }

                    val alreadyExists = dcontest.assorters.find { it.hashcodeDesc() == assorter.hashcodeDesc() } != null
                    altFailures.add(
                        DhondtRiskFailure(Npop, assorter, winnerScore, loserScore!!, risk, nsamples, alreadyExists)
                    )
                }
            }
            return altFailures
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
    }

}

///////////////////////////////////////////////////////////////////
// this is for all contests

// one candidate min/max/reported for this contest
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

// all candidates min/max/reported for this contest
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

fun makeAllSeats(auditRound: AuditRoundIF, contestLimits: List<SampleLimit>): AllSeats {
    val contestLimitsMap = contestLimits.associateBy { it.id }
    val contestSeats = auditRound.contestRounds.map { contestRound ->
        val sampleLimit = contestLimitsMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        val builder = CandSeatRangeBuilder(contestRound)
        builder.dhondtRanges // TODO merge threshold
    }

    return AllSeats(contestSeats)
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