package org.cryptobiotic.rlauxe.dhondt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.betting.estRiskStandardBet
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.text.appendLine

// TODO better to look at the AssertionRounds and use the ones not proven, rather than sampleLimit (??)

private val candNameWidth = 20
private val alpha = .05
private val alphaFudge = .05

class RelaxedAssertions(val dcontest: DHondtContest, val contestRound: ContestRound) {
    val orgInfo = dcontest.info
    val belowMinPct = dcontest.belowMinPct
    val votes = dcontest.votes
    val nseats = dcontest.winnerSeats.values.sum()
    val Npop = contestRound.contestUA.Npop
    val builder = CandSeatRangeBuilder(dcontest, contestRound)

    fun makeSeatRanges(): CandSeatRanges {
        return builder.mergedRanges
    }

    fun showAssertions(): String = buildString {
        //// reported winners
        appendLine("${dcontest}")
        appendLine("reported winners")
        append(" seat ${sfn("winner-round", candNameWidth)}     ${sfn("nvotes", 6)}, ")
        append("${sfn(" score", 6)},  ${sfn("voteDiff", 6)}, ")
        appendLine()
        // sorted scores
        var prev: Int? = null
        repeat(nseats) { idx ->
            val score = dcontest.sortedScores[idx]
            // sortedRawScores.filter{ it.divisor <= maxRound }.forEachIndexed { idx, score ->
            val candId = score.candidate
            if (idx < nseats) append(" (${nfn(idx + 1, 2)}) ") else append("      ")
            val nameRound = "${orgInfo.candidateIdToName[candId]!!}-${score.divisor}"
            val below = if (belowMinPct.contains(candId)) "*" else " "
            append(" ${trunc(nameRound, candNameWidth)}$below, ")
            append(" ${nfn(votes[candId]!!, 6)}, ${nfn(score.score.toInt(), 6)}, ")

            if (prev != null) append("   ${nfn(prev - score.score.toInt(), 6)},")
            prev = score.score.toInt()
            appendLine()
        }
        appendLine()

        //// dhondt failures = contested seats
        val dhondtFailures = mutableMapOf<Int, DhondtLoserGroup>()
        contestRound.assertionRounds.forEach { ar ->
            val nsamples = contestRound.haveSampleSize
            val assorter = ar.assertion.assorter
            val risk = if (ar.auditResult != null) ar.auditResult!!.pmin else {
                estRiskStandardBet(Npop, assorter.noerror(true), nsamples)
            }
            if (risk > alphaFudge && assorter is DHondtAssorter) {
                val winnerId = assorter.winner()
                val loserId = assorter.loser()
                val winnerScore =
                    dcontest.sortedScores.find { it.divisor == assorter.lastSeatWon && it.candidate == winnerId }!!
                val loserScore =
                    dcontest.sortedScores.find { it.divisor == assorter.firstSeatLost && it.candidate == loserId }!!
                val group = dhondtFailures.getOrPut(loserId) { DhondtLoserGroup(loserId) }
                group.arms.add(DhondtRiskFailure(Npop, assorter, winnerScore, loserScore, risk, nsamples))
            }
        }
        if (dhondtFailures.isNotEmpty()) {
            val sortedGroups = dhondtFailures.values.toList().sortedByDescending { it -> it.highScore() }
            append(showDhondtRiskFailures(sortedGroups))
        }
        appendLine()

        //// threshold assertion failures -> alternate scores
        if (builder.thrashers.isNotEmpty()) {
            append( showAltFailures(builder.thrashers, builder.altContest!!, builder.altFailures!!) )
        }
        appendLine()
    }

    fun contestedSeatReport(): Pair<Int, String> {
        var countDhondtFailures = 0
        var countThresholdFailures = 0
        val report = buildString {
            logger.debug { "DHondtContest '${dcontest.name}' using ${contestRound.haveSampleSize} samples" }
            appendLine("DHondtContest '${dcontest.name}'")
            val dhondtFailures = mutableMapOf<Int, DhondtLoserGroup>()
            contestRound.assertionRounds.forEach { ar ->
                val nsamples = contestRound.haveSampleSize
                val assorter = ar.assertion.assorter
                val risk = if (ar.auditResult != null) ar.auditResult!!.pmin else {
                    estRiskStandardBet(Npop, assorter.noerror(true), nsamples)
                }
                if (risk > alphaFudge && assorter is DHondtAssorter) {
                    val winnerId = assorter.winner()
                    val loserId = assorter.loser()
                    val winnerScore =
                        dcontest.sortedScores.find { it.divisor == assorter.lastSeatWon && it.candidate == winnerId }!!
                    val loserScore =
                        dcontest.sortedScores.find { it.divisor == assorter.firstSeatLost && it.candidate == loserId }!!
                    val group = dhondtFailures.getOrPut(loserId) { DhondtLoserGroup(loserId) }
                    group.arms.add(DhondtRiskFailure(Npop, assorter, winnerScore, loserScore, risk, nsamples))
                }
            }
            countDhondtFailures = dhondtFailures.values.sumOf { it.arms.size }
            if (dhondtFailures.isNotEmpty()) {
                val sortedGroups = dhondtFailures.values.toList().sortedByDescending { it -> it.highScore() }
                append(showDhondtRiskFailures(sortedGroups))
            }

            if (builder.altFailures != null) {
                builder.altFailures.values.forEach { it.arms.forEach { append(it.toString()) } }
                countThresholdFailures = builder.altFailures.values.sumOf { it.arms.size }
            }
        }
        return Pair(countDhondtFailures + countThresholdFailures, report)
    }

    fun showDhondtRiskFailures(sortedGroups: List<DhondtLoserGroup>) = buildString {

        appendLine("Contested       loser-round   nvotes,  score, voteDiff,  noerror, estSamples, actSamples, estRisk, assertion")

        sortedGroups.forEach {  group ->
            group.sortedArms().forEachIndexed { idx, arm ->
                val assorter = arm.assorter
                val candId = assorter.loser()
                if (idx == 0) {
                    append(" (${nfn(arm.winnerScore.winningSeat!!, 2)})  ")
                    append(arm.loserScore.showLoser(trunc(orgInfo.candidateIdToName[candId]!!, candNameWidth-4), votes[candId]!!))
                } else {
                    append(" (${nfn(arm.winnerScore.winningSeat!!, 2)})  ")
                    append("                                       ")
                }
                append(" ${nfn(dcontest.marginInVotes(assorter), 7)}, ${dfn(arm.noerror, 6)}, ")
                append(" ${nfn(arm.estMvrs(), 8)}, ${nfn(arm.nmvrs, 8)},     ${dfn(arm.risk, 4)},")
                append(" winner ${assorter.winnerNameRound()} loser ${assorter.loserNameRound()}")
                appendLine()
            }
        }
    }

    fun showAltFailures(thrashers: List<CandSeatRangeBuilder.ThresholdRiskFailure>, alt: DHondtContest, altFailures: Map<Int, DhondtLoserGroup>) = buildString {
        appendLine("------------------------------------------------------------------------------")
        appendLine("Thresholds             marginInVotes, noerror, estSamples, actSamples,   risk")
        thrashers.forEach {  thrasher ->
            appendLine(thrasher)
        }
        appendLine()

        // use new assorters as proxy for audit
        val nseats = alt.winnerSeats.values.sum()
        val sortedScores = alt.sortedScores
        val belowMinPct = alt.belowMinPct
        val info = alt.info
        val votes = alt.votes

        append(" seat ${sfn("winner", candNameWidth - 3)}/round     ${sfn("nvotes", 6)}, ")
        append("${sfn(" score", 6)},  ${sfn("voteDiff", 6)}, ")
        appendLine()

        // sorted scores
        var prev: Int? = null
        repeat(nseats) { idx ->
            val score = sortedScores[idx]
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
        // appendLine("winners=${alt.winnerSeats}")
        appendLine()

        // failed dhondt assertions -> "contested seats"
        if (altFailures.isNotEmpty()) {
            val sortedGroups = altFailures.values.toList().sortedByDescending { it -> it.highScore() }
            append(showAltDhondtRiskFailures(sortedGroups))
        }
        appendLine()
    }

    fun showAltDhondtRiskFailures(altFailures: List<DhondtLoserGroup>) = buildString {
        appendLine("Contested  ${sfn("loser-round", candNameWidth)}    nvotes,  score, voteDiff,  noerror, estSamples, actSamples, estRisk, assertion")

        altFailures.forEach {  group ->
            group.sortedArms().forEachIndexed { idx, arm ->
                val assorter = arm.assorter
                val candId = assorter.loser()
                if (idx == 0) {
                    append(" (${nfn(arm.winnerScore.winningSeat!!, 2)})  ")
                    append(arm.loserScore.showLoser(trunc(orgInfo.candidateIdToName[candId]!!, candNameWidth), votes[candId]!!))
                } else {
                    append(" (${nfn(arm.winnerScore.winningSeat!!, 2)})  ")
                    append("                                           ")
                }
                append(" ${nfn(dcontest.marginInVotes(assorter), 7)}, ${dfn(arm.noerror, 6)}, ")
                append(" ${nfn(arm.estMvrs(), 8)},    ${nfn(arm.nmvrs, 8)},     ${dfn(arm.risk, 4)},")
                append("    winner ${assorter.winnerNameRound()} loser ${assorter.loserNameRound()}")
                appendLine()
            }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger("RelaxedAssertions")
    }

}

///////////////////////////////////////////////////////////////////

data class DhondtLoserGroup(val loserId: Int) {
    val arms = mutableListOf<DhondtRiskFailure>()
    fun highScore(): Double {
        val wtf = arms.maxOfOrNull { it.loserScore.score }
        return wtf ?: 0.0
    }
    fun sortedArms() = arms.sortedBy { it.noerror }
}

data class DhondtRiskFailure(
    val Npop: Int,
    val assorter: DHondtAssorter,
    val winnerScore: DhondtScore,
    val loserScore: DhondtScore,
    val risk: Double,
    val samplesUsed: Int?,
) {
    val noerror = assorter.noerror(true)
    val nmvrs = samplesUsed ?: 0

    fun estMvrs(): Int  {
        return estSampleSizeStandardBet(Npop, noerror, alpha)
    }

    override fun toString() = buildString {
        val assorter = assorter
        val candId = assorter.loser()
        append(" (${nfn(winnerScore.winningSeat!!, 2)})  ")
        append("                                           ")
        append(" ${dfn(noerror, 6)}, ")
        append(" ${nfn(estMvrs(), 8)}, ${nfn(nmvrs, 8)},    ${dfn(risk, 4)},")
        append(" winner ${assorter.winnerNameRound()} loser ${assorter.loserNameRound()}")
        appendLine()
    }
}

///////////////////////////////////////////////////////////////////

class CandSeatRangeBuilder(val dcontest: DHondtContest, val contestRound: ContestRound) {
    val orgInfo = dcontest.info
    val belowMinPct = dcontest.belowMinPct
    val votes = dcontest.votes
    val Npop = contestRound.contestUA.Npop

    val orgRanges: CandSeatRanges
    val threshRanges: List<CandSeatRanges> // ??
    val mergedRanges: CandSeatRanges
    val thrashers: List<ThresholdRiskFailure>
    val altContest: DHondtContest?
    val altFailures: Map<Int, DhondtLoserGroup>?

    init {
        //// dhondt failures = contested seats
        val dhondtFailures = mutableMapOf<Int, DhondtLoserGroup>()
        contestRound.assertionRounds.forEach { ar ->
            val nsamples = contestRound.haveSampleSize

            val assorter = ar.assertion.assorter
            val risk = if (ar.auditResult != null) ar.auditResult!!.pmin else {
                estRiskStandardBet(Npop, assorter.noerror(true), nsamples)
            }
            if (risk > alphaFudge && assorter is DHondtAssorter) {
                val winnerId = assorter.winner()
                val loserId = assorter.loser()
                val winnerScore =
                    dcontest.sortedScores.find { it.divisor == assorter.lastSeatWon && it.candidate == winnerId }!!
                val loserScore =
                    dcontest.sortedScores.find { it.divisor == assorter.firstSeatLost && it.candidate == loserId }!!
                val group = dhondtFailures.getOrPut(loserId) { DhondtLoserGroup(loserId) }
                group.arms.add(DhondtRiskFailure(Npop, assorter, winnerScore, loserScore, risk, nsamples))
            }
        }
        orgRanges = makeCandSeatRanges(dcontest, dhondtFailures.values.flatMap { it.arms })

        //// threshold assertion failures -> alternate scores
        val wthrashers = mutableListOf<ThresholdRiskFailure>()
        contestRound.assertionRounds.forEach { ar ->
            val nsamples = contestRound.haveSampleSize
            val assorter = ar.assertion.assorter
            val risk = if (ar.auditResult != null) ar.auditResult!!.pmin else {
                estRiskStandardBet(Npop, assorter.noerror(true), nsamples)
            }
            if (risk > alphaFudge && assorter !is DHondtAssorter) {
                wthrashers.add(ThresholdRiskFailure(assorter, risk, nsamples))
            }
        }

        // TODO: if there are n thrashers, there are 2^n possible alternative scores. Generate each and take the min and max over all
        val wthreshRanges = mutableListOf<CandSeatRanges>()
        if (wthrashers.isNotEmpty()) {
            altContest = makeAltContest(wthrashers)
            altFailures = makeAltRiskFailures(altContest)
            wthreshRanges.add( makeCandSeatRanges(altContest, altFailures.values.flatMap { it.arms }) )
        } else {
            altContest = null
            altFailures = null
        }

        this.thrashers = wthrashers
        this.threshRanges = wthreshRanges.toList()
        this.mergedRanges = mergeCandSeatRanges(orgRanges, threshRanges)
    }

    // do we have to do this for each or all ?
    // thrashers are the thresholds that didnt make their risk limit
    fun makeAltContest(thrashers: List<ThresholdRiskFailure>): DHondtContest {
        val thrasherIds = thrashers.map { it.assorter.winner() }.toSet()
        // increase nwinners
        // val infoPlus = orgInfo.copy(nwinners = nseats + thrashers.size) // TODO do we need this?
        //     info: ContestInfo,
        //    voteInput: Map<Int, Int>,   // candidateId -> nvotes;  sum is nvotes or V_c
        //    Nc: Int,                // trusted maximum ballots/cards that contain this contest
        //    Ncast: Int,             // number of cast ballots containing this Contest, including undervotes
        //    belowMinPctIn: Set<Int>?  // candidateIds under minFraction

        // TODO not going through the builder, so parties arent complete
        val alt = DHondtContest(orgInfo, votes, dcontest.Nc, dcontest.Ncast, belowMinPct - thrasherIds)

        // recalc assorters
        alt.assorters.addAll(DHondtAssorter.makeDhondtAssorters(orgInfo, alt.Nc, alt.parties))

        return alt // Pair(alt, makeAltRiskFailures(alt))
    }

    fun makeAltRiskFailures(alt: DHondtContest): Map<Int, DhondtLoserGroup> {
        val altFailures = mutableMapOf<Int, DhondtLoserGroup>()
        alt.assorters.forEach { assorter ->
            require(assorter is DHondtAssorter)
            val winnerId = assorter.winner()
            val loserId = assorter.loser()
            val winnerScore = alt.sortedScores.find { it.divisor == assorter.lastSeatWon && it.candidate == winnerId }!!
            val loserScore = alt.sortedScores.find { it.divisor == assorter.firstSeatLost && it.candidate == loserId }

            val risk = estRiskStandardBet(Npop, assorter.noerror(true), 1000)
            val arm = DhondtRiskFailure(Npop, assorter, winnerScore, loserScore!!, risk, null)
            if (arm.risk > alphaFudge) {
                val armGroup = altFailures.getOrPut(loserId) { DhondtLoserGroup(loserId) }
                armGroup.arms.add(arm)
            }
        }
        return altFailures
    }

    fun makeCandSeatRanges(dc: DHondtContest, failures: List<DhondtRiskFailure>): CandSeatRanges {
        val candSeats = mutableMapOf<Int, CandSeatRange>()
        dc.info.candidateIdToName.forEach { (candId, name) ->
            candSeats[candId] = CandSeatRange(name)
        }
        dc.winnerSeats.forEach { (candId, nseats) ->
            candSeats[candId]!!.reportedSeats = nseats
            candSeats[candId]!!.minSeats = nseats
            candSeats[candId]!!.maxSeats = nseats
        }

        // can only win 1 or lose 1
        val winners = mutableSetOf<Int>()
        val losers = mutableSetOf<Int>()
        failures.forEach { arm ->
            winners.add(arm.winnerScore.candidate)
            losers.add(arm.loserScore.candidate)
        }
        winners.forEach {
            val win = candSeats[it]!!
            win.minSeats--
        }
        losers.forEach {
            val lose = candSeats[it]!!
            lose.maxSeats++
        }
        return CandSeatRanges(candSeats.values.toList())
    }

    // union of threshRanges into orgRanges
    fun mergeCandSeatRanges(orgRanges: CandSeatRanges, threshRanges: List<CandSeatRanges>): CandSeatRanges {
        if (threshRanges.isEmpty()) return orgRanges

        orgRanges.ranges.forEach { mergeRange ->
            threshRanges.forEach { threshRange ->
                val trange = threshRange.ranges.find { it.candName == mergeRange.candName }!!
                mergeRange.minSeats = min(mergeRange.minSeats, trange.minSeats)
                mergeRange.maxSeats = max(mergeRange.maxSeats, trange.maxSeats)
            }
        }

        return orgRanges
    }

    // thrashers are the threshold assorters that didnt make their risk limit
    inner class ThresholdRiskFailure(val assorter: AssorterIF, val risk: Double, val samplesUsed: Int?) {
        val noerror = assorter.noerror(true)
        val nmvrs = samplesUsed ?: 0
        fun estMvrs(): Int {
            // payoff_noerror = (1 + λ * (noerror − 1/2))  ;  (µ_i is approximately 1/2)
            // payoff_noerror^n > 1/alpha
            // n = 1/ln(alpha) / ln(λ * (noerror − 1/2)); noerror − 1/2 = nomargin/2
            val maxLoss: Double = 1.0 / 1.03905
            return estSampleSizeStandardBet(Npop, noerror, alpha)
        }

        override fun toString() = buildString {
            append("${assorter.shortName()}: ")
            append(" ${nfn(dcontest.marginInVotes(assorter), 7)}, ${dfn(noerror, 6)}, ")
            append(" ${nfn(estMvrs(), 8)}, ${nfn(nmvrs, 8)},    ${dfn(risk, 4)},")
        }
    }
}

data class CandSeatRange(val candName: String) {
    var minSeats = 0
    var reportedSeats = 0
    var maxSeats = 0
}

data class CandSeatRanges(val ranges: List<CandSeatRange>) {

    fun showSeatRanges() = buildString {
        appendLine("|                party   | min | reported | max |")
        appendLine("|------------------------|-----|----------|-----|")
        ranges.sortedByDescending { it.maxSeats }.forEach {
            appendLine("|  ${trunc(it.candName, candNameWidth) }  | ${nfn(it.minSeats, 2)}  |    ${nfn(it.reportedSeats, 2)}    | ${nfn(it.maxSeats, 2)}  |")
        }
    }

    companion object {
        fun sumRanges(candRanges: List<CandSeatRanges>): CandSeatRanges {
            val sum = mutableMapOf<String, CandSeatRange>()
            candRanges.forEach { candRange ->
                candRange.ranges.forEach { range ->
                    val sumRange = sum.getOrPut(range.candName) { CandSeatRange(range.candName) }
                    sumRange.minSeats += range.minSeats
                    sumRange.reportedSeats += range.reportedSeats
                    sumRange.maxSeats += range.maxSeats
                }
            }
            return CandSeatRanges(sum.values.toList())
        }

        fun showSeatRanges(topdirLimited: String) = buildString {
            val compositeDir = topdirLimited
            val auditRecord = AuditRecord.read(compositeDir)!!
            val contests = auditRecord.contests
            val lastRound = auditRecord.rounds.last()

            val candRanges = mutableListOf<CandSeatRanges>()
            auditRecord.contests.forEach { contestUA ->
                val dcontest = contestUA.contest as DHondtContest
                val contestRound = lastRound.contestRounds.find { it.contestUA.id == contestUA.id }
                if (contestRound != null) {
                    val relax = RelaxedAssertions(dcontest, contestRound)
                    candRanges.add(relax.makeSeatRanges())
                }
            }
            val sum = sumRanges(candRanges)
            appendLine()
            append(sum.showSeatRanges())

            val nseats = sum.ranges.sumOf { it.reportedSeats }
            appendLine("\nnseats=$nseats ncands=${sum.ranges.size} ")
        }
    }
}
