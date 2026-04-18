package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.audit.AssertionRound
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.estRisk
import org.cryptobiotic.rlauxe.util.estSamplesFromNomargin
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.max
import kotlin.math.min
import kotlin.text.appendLine

private val candNameWidth = 20

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
            if (topdirLimited.contains("2024limited")) { // kludge for viewer
                val compositeDir = topdirLimited
                val compositeRecord = AuditRecord.read(compositeDir)!!
                val candRanges = mutableListOf<CandSeatRanges>()
                compositeRecord.contests.forEach { contestUA ->
                    val dcontest = contestUA.contest as DHondtContest
                    candRanges.add(dcontest.makeSeatRanges(compositeRecord.rounds))
                }
                println("Sum of candidate ranges across all contests")
                val sum = CandSeatRanges.sumRanges(candRanges)
                appendLine()
                append(sum.showSeatRanges())
            }
        }
    }
}

class RelaxedAssertions(val org: DHondtContest) {
    val orgInfo = org.info
    val belowMinPct = org.belowMinPct
    val votes = org.votes
    val nseats = org.winnerSeats.values.sum()

    fun makeSeatRanges(rounds: List<AuditRoundIF>): CandSeatRanges  {
        val lastAssertionRounds = mutableMapOf<String, AssertionRound>()
        rounds.map {
            val contestRound = it.contestRounds.find { cr -> cr.id == orgInfo.id }
            if (contestRound != null) {
                contestRound.assertionRounds.forEach { ar ->
                    lastAssertionRounds[ar.assertion.assorter.hashcodeDesc()] = ar
                }
            }
        }

        //// dhondt failures = contested seats
        val dhondtFailures = mutableMapOf<Int, DhondtLoserGroup>()
        lastAssertionRounds.forEach { (key, ar) ->
            val assorter = ar.assertion.assorter
            val risk = if (ar.auditResult != null) ar.auditResult!!.pmin else {
                estRisk(2/1.03905, mean2margin(assorter.noerror()), 1000) // TODO how many samples ??
            }
            if (risk > .05 && assorter is DHondtAssorter) {
                val winnerId = assorter.winner()
                val loserId = assorter.loser()
                val winnerScore = org.sortedScores.find { it.divisor == assorter.lastSeatWon && it.candidate == winnerId }!!
                val loserScore = org.sortedScores.find { it.divisor == assorter.firstSeatLost && it.candidate == loserId }!!
                val group = dhondtFailures.getOrPut(loserId) { DhondtLoserGroup(loserId) }
                group.arms.add(DhondtRiskFailure(assorter, winnerScore, loserScore, risk, ar.auditResult?.nmvrs))
            }
        }
        val orgRanges = makeCandSeatRanges(org, dhondtFailures.values.flatMap { it.arms })

        //// threshold assertion failures -> alternate scores
        val thrashers = mutableListOf<ThresholdRiskFailure>()
        lastAssertionRounds.forEach { (key, ar) ->
            val assorter = ar.assertion.assorter
            val risk = if (ar.auditResult != null) ar.auditResult!!.pmin else {
                estRisk(2/1.03905, mean2margin(assorter.noerror()), 1000) // TODO how many samples ??
            }
            if (risk > .05 && assorter !is DHondtAssorter) {
                thrashers.add(ThresholdRiskFailure(assorter, risk, ar.auditResult?.nmvrs))
            }
        }

        // TODO: if there are n thrashers, there are 2^n possible alternative scores. Generate each and take the min and max over all
        val threshRanges = mutableListOf<CandSeatRanges>()
        if (thrashers.isNotEmpty()) {
            val alt = makeAltContest(thrashers)
            val altFailures = makeAltRiskFailures(alt)
            threshRanges.add(makeCandSeatRanges(alt, altFailures.values.flatMap { it.arms }))
        }

        return mergeCandSeatRanges(orgRanges, threshRanges)
    }

    fun showAssertions(rounds: List<AuditRoundIF>): String = buildString {
        val lastAssertionRounds = mutableMapOf<String, AssertionRound>()
        rounds.map {
            val contestRound = it.contestRounds.find { cr -> cr.id == orgInfo.id }
            if (contestRound != null) {
                contestRound.assertionRounds.forEach { ar ->
                    lastAssertionRounds[ar.assertion.assorter.hashcodeDesc()] = ar
                }
            }
        }

        //// reported winners
        appendLine("reported winners")
        append(" seat ${sfn("winner", candNameWidth - 3)}/round     ${sfn("nvotes", 6)}, ")
        append("${sfn(" score", 6)},  ${sfn("voteDiff", 6)}, ")
        appendLine()
        // sorted scores
        var prev: Int? = null
        repeat(nseats) { idx ->
            val score = org.sortedScores[idx]
            // sortedRawScores.filter{ it.divisor <= maxRound }.forEachIndexed { idx, score ->
            val candId = score.candidate
            if (idx < nseats) append(" (${nfn(idx + 1, 2)}) ") else append("      ")
            val below = if (belowMinPct.contains(candId)) "*" else " "
            append(" ${trunc(orgInfo.candidateIdToName[candId]!!, candNameWidth)}$below")
            append("/${nfn(score.divisor, 2)}, ")
            append(" ${nfn(votes[candId]!!, 6)}, ${nfn(score.score.toInt(), 6)}, ")

            if (prev != null) append(" ${nfn(prev - score.score.toInt(), 6)},")
            prev = score.score.toInt()
            appendLine()
        }
        appendLine()

        //// dhondt failures = contested seats
        val dhondtFailures = mutableMapOf<Int, DhondtLoserGroup>()
        lastAssertionRounds.forEach { (key, ar) ->
            val assorter = ar.assertion.assorter
            val risk = if (ar.auditResult != null) ar.auditResult!!.pmin else {
                estRisk(2/1.03905, mean2margin(assorter.noerror()), 1000) // TODO how many samples ??
            }
            if (risk > .05 && assorter is DHondtAssorter) {
                val winnerId = assorter.winner()
                val loserId = assorter.loser()
                val winnerScore = org.sortedScores.find { it.divisor == assorter.lastSeatWon && it.candidate == winnerId }!!
                val loserScore = org.sortedScores.find { it.divisor == assorter.firstSeatLost && it.candidate == loserId }!!
                val group = dhondtFailures.getOrPut(loserId) { DhondtLoserGroup(loserId) }
                group.arms.add(DhondtRiskFailure(assorter, winnerScore, loserScore, risk, ar.auditResult?.nmvrs))
            }
        }
        if (dhondtFailures.isNotEmpty()) {
            val sortedGroups = dhondtFailures.values.toList().sortedByDescending { it -> it.highScore() }
            append(showDhondtRiskFailures(sortedGroups))
        }
        appendLine()
        val orgRanges = makeCandSeatRanges(org, dhondtFailures.values.flatMap { it.arms })

        //// threshold assertion failures -> alternate scores
        val thrashers = mutableListOf<ThresholdRiskFailure>()
        lastAssertionRounds.forEach { (key, ar) ->
            val assorter = ar.assertion.assorter
            val risk = if (ar.auditResult != null) ar.auditResult!!.pmin else {
                estRisk(2/1.03905, mean2margin(assorter.noerror()), 1000) // TODO how many samples ??
            }
            if (risk > .05 && assorter !is DHondtAssorter) {
                thrashers.add(ThresholdRiskFailure(assorter, risk, ar.auditResult?.nmvrs))
            }
        }

        // TODO: if there are n thrashers, there are 2^n possible alternative scores. Generate each and take the min and max over all
        val threshRanges = mutableListOf<CandSeatRanges>()
        if (thrashers.isNotEmpty()) {
            val alt = makeAltContest(thrashers)
            val altFailures = makeAltRiskFailures(alt)
            append(showAltFailures(thrashers, alt, altFailures))
            threshRanges.add(makeCandSeatRanges(alt, altFailures.values.flatMap { it.arms }))
        }
        appendLine()

        val merged = mergeCandSeatRanges(orgRanges, threshRanges)
        append(merged.showSeatRanges())
    }

    /////////////////////////////////////////////////////////////////////////////////////////////

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
            winners.add( arm.winnerScore.candidate)
            losers.add( arm.loserScore.candidate)
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
                val trange = threshRange.ranges.find{ it.candName == mergeRange.candName }!!
                mergeRange.minSeats = min(mergeRange.minSeats, trange.minSeats)
                mergeRange.maxSeats = max(mergeRange.maxSeats, trange.maxSeats)
            }
        }

        return orgRanges
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
    data class DhondtLoserGroup(val loserId: Int) {
        val arms = mutableListOf<DhondtRiskFailure>()
        fun highScore(): Double {
            val wtf = arms.maxOfOrNull { it.loserScore.score }
            return wtf ?: 0.0
        }
        fun sortedArms() = arms.sortedBy { it.nomargin }
    }

    data class DhondtRiskFailure(
        val assorter: DHondtAssorter,
        val winnerScore: DhondtScore,
        val loserScore: DhondtScore,
        val risk: Double,
        val samplesUsed: Int?
    ) {
        val nomargin = 2.0 * assorter.noerror() - 1.0
        val nmvrs = samplesUsed ?: 0
        fun estMvrs(): Int  {
            // payoff_noerror = (1 + λ * (noerror − 1/2))  ;  (µ_i is approximately 1/2)
            // payoff_noerror^n > 1/alpha
            // n = 1/ln(alpha) / ln(λ * (noerror − 1/2)); noerror − 1/2 = nomargin/2
            // TODO
            val maxLoss: Double = 1.0 / 1.03905
            return roundUp(estSamplesFromNomargin(2*maxLoss, nomargin, .05)) // =  -ln(alpha) / ln(1.0 + bet * nomargin/2)
        }
    }

    fun showDhondtRiskFailures(sortedGroups: List<DhondtLoserGroup>) = buildString {

        appendLine("Contested           loser/round   nvotes,  score, voteDiff,  noerror, nomargin, estSamples, actSamples, estRisk, assertion")

        sortedGroups.forEach {  group ->
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
                append(" ${nfn(org.marginInVotes(assorter), 7)}, ${dfn(assorter.noerror(), 6)},   ${dfn(arm.nomargin, 4)}, ")
                append(" ${nfn(arm.estMvrs(), 8)}, ${nfn(arm.nmvrs, 8)},    ${dfn(arm.risk, 4)},")
                append(" winner ${assorter.winnerNameRound()} loser ${assorter.loserNameRound()}")
                appendLine()
            }
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // threshold

    inner class ThresholdRiskFailure(val assorter: AssorterIF, val risk: Double, val samplesUsed: Int?) {
        val nomargin = 2.0 * assorter.noerror() - 1.0
        val nmvrs = samplesUsed ?: 0
        fun estMvrs(): Int  {
            // payoff_noerror = (1 + λ * (noerror − 1/2))  ;  (µ_i is approximately 1/2)
            // payoff_noerror^n > 1/alpha
            // n = 1/ln(alpha) / ln(λ * (noerror − 1/2)); noerror − 1/2 = nomargin/2
            // TODO
            val maxLoss: Double = 1.0 / 1.03905
            return roundUp(estSamplesFromNomargin(2*maxLoss, nomargin, .05)) // =  -ln(alpha) / ln(1.0 + bet * nomargin/2)
        }

        override fun toString() = buildString {
            append("${assorter.shortName()}: ")
            append(" ${nfn(org.marginInVotes(assorter), 7)}, ${dfn(nomargin, 6)}, ")
            append(" ${nfn(estMvrs(), 8)}, ${nfn(nmvrs, 8)},    ${dfn(risk, 4)},")
        }
    }

    // do we have to do this for each or all ?
    // thrashers are the thresholds that dodnt make their risk limit
    fun makeAltContest(thrashers: List<ThresholdRiskFailure>): DHondtContest  {
        val thrasherIds = thrashers.map { it.assorter.winner() }.toSet()
        // increase nwinners
        // val infoPlus = orgInfo.copy(nwinners = nseats + thrashers.size) // TODO do we need this?
        //     info: ContestInfo,
        //    voteInput: Map<Int, Int>,   // candidateId -> nvotes;  sum is nvotes or V_c
        //    Nc: Int,                // trusted maximum ballots/cards that contain this contest
        //    Ncast: Int,             // number of cast ballots containing this Contest, including undervotes
        //    belowMinPctIn: Set<Int>?  // candidateIds under minFraction

        // TODO not going through the builder, so parties arent complete
        val alt = DHondtContest(orgInfo, votes, org.Nc, org.Ncast, belowMinPct - thrasherIds)

        // recalc assorters
        alt.assorters.addAll(DHondtAssorter.makeDhondtAssorters(orgInfo, alt.Nc, alt.parties))

        return alt // Pair(alt, makeAltRiskFailures(alt))
    }

    // these are not dhondts ??
    fun makeAltRiskFailures(alt: DHondtContest): Map<Int, DhondtLoserGroup> {
        val altFailures = mutableMapOf<Int, DhondtLoserGroup>()
        alt.assorters.forEach { assorter ->
            require(assorter is DHondtAssorter)
            val winnerId = assorter.winner()
            val loserId = assorter.loser()
            val winnerScore = alt.sortedScores.find { it.divisor == assorter.lastSeatWon && it.candidate == winnerId }!!
            val loserScore = alt.sortedScores.find { it.divisor == assorter.firstSeatLost && it.candidate == loserId }
            if (loserScore == null)
                print("")
            val nomargin = 2.0 * assorter.noerror() - 1.0
            val risk = estRisk(2 / 1.03905, nomargin, 1000) // TODO get actual nsamples
            val arm = DhondtRiskFailure(assorter, winnerScore, loserScore!!, risk, null)
            if (arm.risk > .05) {
                val armGroup = altFailures.getOrPut(loserId) { DhondtLoserGroup(loserId) }
                armGroup.arms.add(arm)
            }
        }
        return altFailures
    }


    fun showAltFailures(thrashers: List<ThresholdRiskFailure>, alt: DHondtContest, altFailures: Map<Int, DhondtLoserGroup>) = buildString {
        appendLine("------------------------------------------------------------------------------")
        appendLine("Thresholds             marginInVotes, nomargin, estSamples, actSamples,   risk")
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

        appendLine("Contested         loser/round     nvotes,  score, voteDiff,  noerror, nomargin, estSamples, estRisk, assertion")

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
                append(" ${nfn(org.marginInVotes(assorter), 7)}, ${dfn(assorter.noerror(), 6)},   ${dfn(arm.nomargin, 4)}, ")
                append(" ${nfn(arm.estMvrs(), 8)},   ${dfn(arm.risk, 4)},")
                append("    winner ${assorter.winnerNameRound()} loser ${assorter.loserNameRound()}")
                appendLine()
            }
        }
    }

}