package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.audit.AssertionRound
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.AuditRoundResult
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.estRisk
import org.cryptobiotic.rlauxe.util.estSamples
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.text.appendLine

class RelaxedAssertions(val dc: DHondtContest) {
    val info = dc.info
    val belowMinPct = dc.belowMinPct
    val sortedScores = dc.sortedScores
    val winnerSeats = dc.winnerSeats
    val votes = dc.votes

    fun showAssertions(rounds: List<AuditRoundIF>, recurse: Boolean = false): String = buildString {
        val lastAssertionRounds = mutableMapOf<String, AssertionRound>()
        rounds.filter{ it.auditWasDone }.map {
            val contestRound = it.contestRounds.find { cr -> cr.id == info.id }
            if (contestRound != null) {
                contestRound.assertionRounds.forEach { ar ->
                    lastAssertionRounds[ar.assertion.assorter.hashcodeDesc()] = ar
                }
            }
        }

        val candNameWidth = 20
        val width = 12
        val maxRound = sortedScores.filter { it.winningSeat != null }.maxOfOrNull { it.divisor }!! + 1
        val nseats = winnerSeats.values.sum()

        append(" seat ${sfn("winner", candNameWidth - 3)}/round     ${sfn("nvotes", 6)}, ")
        append("${sfn(" score", 6)},  ${sfn("voteDiff", 6)}, ")
        appendLine()

        // sorted scores
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
        // appendLine("winners=${winnerSeats}")
        appendLine()

        // failed dhondt assertions -> "contested seats"
        val armsMap = mutableMapOf<Int, AssertionRiskGroup>()
        lastAssertionRounds.forEach { (key, ar) ->
            val assorter = ar.assertion.assorter
            val risk = ar.auditResult?.pmin ?: Double.NaN
            if (risk > .05 && assorter is DHondtAssorter) {
                val winnerId = assorter.winner()
                val loserId = assorter.loser()
                val winnerScore = sortedScores.find { it.divisor == assorter.lastSeatWon && it.candidate == winnerId }!!
                val loserScore = sortedScores.find { it.divisor == assorter.firstSeatLost && it.candidate == loserId }!!
                val group = armsMap.getOrPut(loserId) { AssertionRiskGroup(loserId) }
                group.arms.add(AssertionRiskMargin(ar, ar.assertion.assorter, winnerScore, loserScore, ar.auditResult!!))
            }
        }
        if (armsMap.isNotEmpty()) {
            val sortedGroups = armsMap.values.toList().sortedByDescending { it -> it.highScore() }
            append(showAssertionsAtRisk(sortedGroups, candNameWidth))
        }
        appendLine()

        // failed threshold assertions -> alternate scores
        val thrashers = mutableListOf<AssertionThrasher>()
        lastAssertionRounds.forEach { (key, ar) ->
            val assorter = ar.assertion.assorter
            val risk = ar.auditResult?.pmin ?: Double.NaN
            if (risk > .05 && assorter !is DHondtAssorter) {
                thrashers.add(AssertionThrasher(ar, ar.assertion.assorter, ar.auditResult))
            }
        }
        if (!recurse && thrashers.isNotEmpty()) {
            append(showAlternate(thrashers, rounds))
        }
    }

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
        val winnerScore: DhondtScore,
        val loserScore: DhondtScore,
        val auditResult: AuditRoundResult?
    ) {
        val nomargin = 2.0 * assorter.noerror() - 1.0
        val risk = ar.auditResult?.pmin ?: Double.NaN
        val nmvrs = ar.auditResult?.samplesUsed ?: 0
        fun estMvrs(): Int  {
            // payoff_noerror = (1 + λ * (noerror − 1/2))  ;  (µ_i is approximately 1/2)
            // payoff_noerror^n > 1/alpha
            // n = 1/ln(alpha) / ln(λ * (noerror − 1/2)); noerror − 1/2 = nomargin/2
            // TODO
            val maxLoss: Double = 1.0 / 1.03905
            return roundUp(estSamples(2*maxLoss, nomargin, .05)) // =  -ln(alpha) / ln(1.0 + bet * nomargin/2)
        }
    }

    fun showAssertionsAtRisk(sortedGroups: List<AssertionRiskGroup>, candNameWidth: Int) = buildString {

        appendLine("Contested            loser/round   nvotes,  score, voteDiff,  noerror, nomargin, estSamples, actSamples,   risk, assertion")

        sortedGroups.forEach {  group ->
            group.sortedArms().forEachIndexed { idx, arm ->
                val assorter = arm.assorter
                val candId = assorter.loser()
                if (idx == 0) {
                    append(" (${nfn(arm.winnerScore.winningSeat!!, 2)})  ")
                    append(arm.loserScore.showLoser(trunc(info.candidateIdToName[candId]!!, candNameWidth), votes[candId]!!))
                } else {
                    append(" (${nfn(arm.winnerScore.winningSeat!!, 2)})  ")
                    append("                                           ")
                }
                append(" ${nfn(dc.marginInVotes(assorter), 7)}, ${dfn(assorter.noerror(), 6)},   ${dfn(arm.nomargin, 4)}, ")
                append(" ${nfn(arm.estMvrs(), 8)}, ${nfn(arm.nmvrs, 8)},    ${dfn(arm.risk, 4)},")
                append(" winner ${assorter.winnerNameRound()} loser ${assorter.loserNameRound()}")
                appendLine()
            }
        }
    }

    // thrashers are the thresholds (only Below?)  that dodnt make their risk limit
    fun showAlternate(thrashers: List<AssertionThrasher>, rounds: List<AuditRoundIF>) = buildString {
        appendLine("------------------------------------------------------------------------------")
        appendLine("Thresholds                        marginInVotes, nomargin, estSamples, actSamples,   risk")
        thrashers.forEach {  thrasher ->
            appendLine(thrasher)
        }
        appendLine()

        val thrasherIds = thrashers.map { it.assorter.winner() }.toSet()
        // increase nwinners
        val infoPlus = info.copy(nwinners = info.nwinners + thrashers.size)
        //     info: ContestInfo,
        //    voteInput: Map<Int, Int>,   // candidateId -> nvotes;  sum is nvotes or V_c
        //    Nc: Int,                // trusted maximum ballots/cards that contain this contest
        //    Ncast: Int,             // number of cast ballots containing this Contest, including undervotes
        //    belowMinPctIn: Set<Int>?  // candidateIds under minFraction

        // TODO not going through the builder, so parties arent complete
        val alt = DHondtContest(info, votes, dc.Nc, dc.Ncast, belowMinPct - thrasherIds)

        // recalc assorters
        val org = DHondtAssorter.makeDhondtAssorters(dc.info, dc.Nc, dc.parties)
        //appendLine("org has ${org.size} Dhondt assertions")
        alt.assorters.addAll(DHondtAssorter.makeDhondtAssorters(infoPlus, alt.Nc, alt.parties))
        //appendLine("alt has ${alt.assorters.size} ")
        /* alt.assorters.sortedBy { it.noerror() }. forEach {
            appendLine("   ${it.shortName()} ${it.upperBound()} ${it.noerror()}")
        } */

        // use new assorters as proxy for audit
        append( showFromAssorters(alt) )
    }

    inner class AssertionThrasher(val ar: AssertionRound, val assorter: AssorterIF, val auditResult: AuditRoundResult?) {
        val nomargin = 2.0 * assorter.noerror() - 1.0
        val risk = ar.auditResult?.pmin ?: Double.NaN
        val nmvrs = ar.auditResult?.samplesUsed ?: 0
        fun estMvrs(): Int  {
            // payoff_noerror = (1 + λ * (noerror − 1/2))  ;  (µ_i is approximately 1/2)
            // payoff_noerror^n > 1/alpha
            // n = 1/ln(alpha) / ln(λ * (noerror − 1/2)); noerror − 1/2 = nomargin/2
            // TODO
            val maxLoss: Double = 1.0 / 1.03905
            return roundUp(estSamples(2*maxLoss, nomargin, .05)) // =  -ln(alpha) / ln(1.0 + bet * nomargin/2)
        }

        override fun toString() = buildString {
            append("${assorter.shortName()}: ")
            append(" ${nfn(dc.marginInVotes(assorter), 7)}, ${dfn(nomargin, 6)}, ")
            append(" ${nfn(estMvrs(), 8)}, ${nfn(nmvrs, 8)},    ${dfn(risk, 4)},")
        }

    }

    fun showFromAssorters(alt: DHondtContest): String = buildString {
        val candNameWidth = 20
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
        val armsMap = mutableMapOf<Int, AssorterRiskGroup>()
        alt.assorters.forEach { assorter ->
            require(assorter is DHondtAssorter)
            val winnerId = assorter.winner()
            val loserId = assorter.loser()
            val winnerScore = sortedScores.find { it.divisor == assorter.lastSeatWon && it.candidate == winnerId }!!
            val loserScore = sortedScores.find { it.divisor == assorter.firstSeatLost && it.candidate == loserId }!!
            val arm = AssorterRiskMargin(assorter, winnerScore, loserScore)
            if (arm.risk > .05) {
                val armGroup = armsMap.getOrPut(loserId) { AssorterRiskGroup(loserId) }
                armGroup.arms.add(AssorterRiskMargin(assorter, winnerScore, loserScore))
            }
        }

        if (armsMap.isNotEmpty()) {
            val sortedGroups = armsMap.values.toList().sortedByDescending { it -> it.highScore() }
            append(showAssortersAtRisk(sortedGroups, candNameWidth))
        }
        appendLine()
    }

    data class AssorterRiskGroup(val loserId: Int) {
        val arms = mutableListOf<AssorterRiskMargin>()
        fun highScore(): Double {
            val wtf = arms.maxOfOrNull { it.loserScore.score }
            return wtf ?: 0.0
        }
        fun sortedArms() = arms.sortedBy { it.nomargin }
    }

    data class AssorterRiskMargin(
        // val ar: AssertionRound,
        val assorter: DHondtAssorter,
        val winnerScore: DhondtScore,
        val loserScore: DhondtScore,
        // val auditResult: AuditRoundResult?
    ) {
        val nomargin = 2.0 * assorter.noerror() - 1.0

        val maxLoss: Double = 1.0 / 1.03905
        val nmvrs = 0 // roundUp(estSamples(2*maxLoss, nomargin, .05) )
        val risk =  estRisk(2*maxLoss, nomargin, 1000) // TODO get actual nsamples

        fun estMvrs(): Int  {
            // payoff_noerror = (1 + λ * (noerror − 1/2))  ;  (µ_i is approximately 1/2)
            // payoff_noerror^n > 1/alpha
            // n = 1/ln(alpha) / ln(λ * (noerror − 1/2)); noerror − 1/2 = nomargin/2
            // TODO
            val maxLoss: Double = 1.0 / 1.03905
            return roundUp(estSamples(2*maxLoss, nomargin, .05)) // =  -ln(alpha) / ln(1.0 + bet * nomargin/2)
        }
    }

    fun showAssortersAtRisk(sortedGroups: List<AssorterRiskGroup>, candNameWidth: Int) = buildString {

        appendLine("Contested          loser/round  nvotes,  score, voteDiff,  noerror, nomargin, estSamples, actSamples, estRisk, assertion")

        sortedGroups.forEach {  group ->
            group.sortedArms().forEachIndexed { idx, arm ->
                val assorter = arm.assorter
                val candId = assorter.loser()
                if (idx == 0) {
                    append(" (${nfn(arm.winnerScore.winningSeat!!, 2)})")
                    append(arm.loserScore.showLoser(trunc(info.candidateIdToName[candId]!!, candNameWidth), votes[candId]!!))
                } else {
                    append(" (${nfn(arm.winnerScore.winningSeat!!, 2)})")
                    append("                                           ")
                }
                append(" ${nfn(dc.marginInVotes(assorter), 7)}, ${dfn(assorter.noerror(), 6)},   ${dfn(arm.nomargin, 4)}, ")
                append(" ${nfn(arm.estMvrs(), 8)}, ${nfn(arm.nmvrs, 8)},    ${dfn(arm.risk, 4)},")
                append("    winner ${assorter.winnerNameRound()} loser ${assorter.loserNameRound()}")
                appendLine()
            }
        }
    }

}