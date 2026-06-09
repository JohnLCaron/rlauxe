package org.cryptobiotic.rlauxe.dhondt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc

class RelaxedAssertionReport(val builder: CandSeatRangeBuilder) {
    val dcontest = builder.dcontest
    val sortedLoserGroups: List<DhondtLoserGroup>

    init {
        val dhondtLoserGroups = mutableMapOf<Int, DhondtLoserGroup>() // loser candidate -> DhondtLoserGroup
        /* builder.failureNodes.forEach { altNode ->
            val assorter = altNode.failure.assorter
            val winner = assorter.winner()
            val loser = assorter.loser()
            val winnerScore = dcontest.sortedScores.find { it.divisor == assorter.lastSeatWon && it.candidate == winner }
            val loserScore = dcontest.sortedScores.find { it.divisor == assorter.firstSeatLost && it.candidate == loser }
            val group = dhondtLoserGroups.getOrPut(loser) { DhondtLoserGroup(loser) }
            group.failures.add(DhondtRiskFailure(builder.Npop, assorter, winnerScore, loserScore, altNode.failure.risk, builder.nsamples, true))
        } */
        sortedLoserGroups = dhondtLoserGroups.values.toList().sortedByDescending { it.highScore() }
    }

    fun showRelaxedAssertion(cassertion: ClcaAssertion) = buildString {
        // build an alternative contest with the named assertion absent
        // just
    }

    fun showRelaxedAssertions(): String = buildString {
        //// reported winners
        appendLine("${dcontest}")
        appendLine("reported winners")
        append(" seat ${sfn("winner-round", candNameWidth)}     ${sfn("nvotes", 6)}, ")
        append("${sfn(" score", 6)},  ${sfn("scoreDiff", 6)}, ")
        appendLine()
        // sorted scores
        var prev: Int? = null
        repeat(dcontest.nseats + 3) { idx ->
            val score = dcontest.sortedScores[idx]
            // sortedRawScores.filter{ it.divisor <= maxRound }.forEachIndexed { idx, score ->
            val candId = score.candidate
            if (idx < dcontest.nseats) append(" (${nfn(idx + 1, 2)}) ") else append("      ")
            val nameRound = "${builder.orgInfo.candidateIdToName[candId]!!}-${score.divisor}"
            val below = if (builder.belowMinPct.contains(candId)) "*" else " "
            append(" ${trunc(nameRound, candNameWidth)}$below, ")
            append(" ${nfn(builder.votes[candId]!!, 6)}, ${nfn(score.score.toInt(), 6)}, ")

            if (prev != null) append("   ${nfn(prev - score.score.toInt(), 6)},")
            prev = score.score.toInt()
            appendLine()
        }
        appendLine()

        append( showDhondtRiskFailures() )
        appendLine()

        // threshold assertion failures
        //if (builder.altThrashContests.isNotEmpty()) {
        //    append( showAltThrashContest(builder.altThrashContests.first()) )
        //}
        //appendLine()
    }

    // not crazy about these DhondtLoserGroup
    fun showDhondtRiskFailures() = buildString {

        appendLine("Contested       loser-round   nvotes,  score, scoreDiff,  noerror, estSamples, actSamples, estRisk, assertion")

        var idx = 0
        builder.failureNodes.forEach {  node ->
            val failure = node.failure
            val assorter = failure.assorter
            val candId = assorter.loser()
            val winningSeat = failure.winnerScore.winningSeat
            if (winningSeat == null)
                append("       ")
            else
                append(" (${nfn(winningSeat!!, 2)})  ")

            if (idx == 0) {
                append(failure.loserScore.showLoser(
                    trunc(
                        builder.orgInfo.candidateIdToName[candId]!!,
                        candNameWidth - 4
                    ), builder.votes[candId]!!))
            } else {
                append("                                       ")
            }

            append(" ${nfn(dcontest.marginInVotes(assorter), 7)}, ${dfn(failure.noerror, 6)}, ")
            append(" ${nfn(failure.estMvrs(), 8)}, ${nfn(failure.samplesUsed, 8)},     ${dfn(failure.risk, 4)},")
            append(" winner ${assorter.winnerNameRound()} loser ${assorter.loserNameRound()}")
            appendLine()
            idx++
        }
    }

    // not crazy about these DhondtLoserGroup
    fun showDhondtRiskFailures(sortedGroups: List<DhondtLoserGroup>) = buildString {

        appendLine("Contested       loser-round   nvotes,  score, scoreDiff,  noerror, estSamples, actSamples, estRisk, assertion")

        sortedGroups.forEach {  group ->
            group.sortedArms().forEachIndexed { idx, arm ->
                val assorter = arm.assorter
                val candId = assorter.loser()
                val winningSeat = arm.winnerScore.winningSeat
                if (winningSeat == null)
                    append("       ")
                else
                    append(" (${nfn(winningSeat!!, 2)})  ")

                if (idx == 0) {
                    append(arm.loserScore.showLoser(
                        trunc(
                            builder.orgInfo.candidateIdToName[candId]!!,
                            candNameWidth - 4
                        ), builder.votes[candId]!!))
                } else {
                    append("                                       ")
                }

                append(" ${nfn(dcontest.marginInVotes(assorter), 7)}, ${dfn(arm.noerror, 6)}, ")
                append(" ${nfn(arm.estMvrs(), 8)}, ${nfn(arm.samplesUsed, 8)},     ${dfn(arm.risk, 4)},")
                append(" winner ${assorter.winnerNameRound()} loser ${assorter.loserNameRound()}")
                appendLine()
            }
        }
    }

    fun showAltThrasherAssertions(altThrashContest: CandSeatRangeBuilder.AltContest) = buildString {
        appendLine("------------------------------------------------------------------------------")
        appendLine(altThrashContest.thrasher)
        appendLine()
        appendLine(showAltContest(altThrashContest))
    }

    fun showAltFailureContestRecurse(altContest: CandSeatRangeBuilder.AltContest, done: MutableSet<String>): String {
        return buildString {
            appendLine(showAltFailureContest(altContest))
            done.add(altContest.failure!!.assorter.shortName())
            done.add(altContest.failure!!.assorter.reverseName())

            // all the failures from altContest
            altContest.dhondtFailures.forEach {
                val skip = done.contains(it.assorter.shortName())
                logger.debug { "  here is ${it.assorter.shortName()} from ${altContest.alt} skip=$skip" }

                /* if (!skip) {
                    val subContest = builder.makeAltContest(dcontest, it)
                    append( showAltFailureContestRecurse(subContest, done) )
                    done.add(it.assorter.shortName())
                    done.add(it.assorter.reverseName())
                } */
            }
        }
    }

    fun showAltFailureContest(altContest: CandSeatRangeBuilder.AltContest) = buildString {
        appendLine()
        appendLine("Assertion Failure")
        appendLine(altContest.failure)
        append(showAltContest(altContest))
        logger.debug{"  did ${altContest.failure!!.assorter.shortName()} from ${altContest.alt} "}
    }

    fun showAltContest(altContest: CandSeatRangeBuilder.AltContest) = buildString {
        appendLine("Alternate Contest from ${altContest.alt} ")

        val dhondt = altContest.alt
        val nseats = dhondt.winnerSeats.values.sum()
        val sortedScores = dhondt.sortedScores
        val belowMinPct = dhondt.partiesBelowThreshold
        val info = dhondt.info
        val votes = dhondt.votes

        append(" seat ${sfn("winner", candNameWidth - 3)}-round     ${sfn("nvotes", 6)}, ")
        append("${sfn(" score", 6)},  ${sfn("scoreDiff", 6)}, ")
        appendLine()

        // sorted scores
        var prev: Int? = null
        repeat(nseats+ 3) { idx ->
            val score = sortedScores[idx]
            val candId = score.candidate
            if (idx < nseats) append(" (${nfn(idx + 1, 2)}) ") else append("      ")
            append(" ${trunc(info.candidateIdToName[candId]!!, candNameWidth)}")
            append("-${nfn(score.divisor, 2)}, ")
            append(" ${nfn(votes[candId]!!, 6)}, ${nfn(score.score.toInt(), 6)}, ")

            if (prev != null) append(" ${nfn(prev - score.score.toInt(), 6)},")
            prev = score.score.toInt()
            appendLine()
        }
        // appendLine("winners=${alt.winnerSeats}")
        appendLine()

        //// dhondt failures = contested seats
        val dhondtLoserGroups = mutableMapOf<Int, DhondtLoserGroup>() // loser candidate -> DhondtLoserGroup
        altContest.dhondtFailures.forEach { failure ->
            val assorter = failure.assorter
            val winner = assorter.winner()
            val loser = assorter.loser()
            val winnerScore = dhondt.sortedScores.find { it.divisor == assorter.lastSeatWon && it.candidate == winner }!!
            val loserScore = dhondt.sortedScores.find { it.divisor == assorter.firstSeatLost && it.candidate == loser }!!
            val group = dhondtLoserGroups.getOrPut(loser) { DhondtLoserGroup(loser) }
            group.failures.add(DhondtRiskFailure(builder.Npop, assorter, winnerScore, loserScore, failure.risk, builder.nsamples, true))
        }

        // failed dhondt assertions -> "contested seats"
        if (dhondtLoserGroups.isNotEmpty()) {
            val sortedGroups = dhondtLoserGroups.values.toList().sortedByDescending { it.highScore() }
            append(showAltDhondtRiskFailures(sortedGroups))
        }
        appendLine()
    }

    fun showAltDhondtRiskFailures(altFailures: List<DhondtLoserGroup>) = buildString {
        appendLine("Alternate Contest Assertion Failures")
        appendLine("Contested ${sfn("loser-round", candNameWidth)}    nvotes,  score, scoreDiff,  noerror, estSamples, actSamples,    estRisk, alreadyExists, assertion")

        altFailures.forEach {  group ->
            group.sortedArms().forEachIndexed { idx, arm ->
                val assorter = arm.assorter
                val candId = assorter.loser()
                if (idx == 0) {
                    append(" (${nfn(arm.winnerScore.winningSeat!!, 2)})  ")
                    append(arm.loserScore.showLoser(trunc(builder.orgInfo.candidateIdToName[candId]!!, candNameWidth), builder.votes[candId]!!))
                } else {
                    append(" (${nfn(arm.winnerScore.winningSeat!!, 2)})  ")
                    append("                                           ")
                }
                append(" ${nfn(dcontest.marginInVotes(assorter), 7)}, ${dfn(arm.noerror, 6)}, ")
                append(" ${nfn(arm.estMvrs(), 8)},    ${nfn(arm.samplesUsed, 8)},     ${dfn(arm.risk, 4)},")
                append(" ${arm.alreadyExists},   winner ${assorter.winnerNameRound()} loser ${assorter.loserNameRound()}")
                appendLine()
            }
        }
    }

    // group failures by loser candidate
    class DhondtLoserGroup(val loserId: Int) {
        val failures = mutableListOf<DhondtRiskFailure>()
        fun highScore(): Double {
            val wtf = failures.maxOfOrNull { it.loserScore.score }
            return wtf ?: 0.0
        }
        fun sortedArms() = failures.sortedBy { it.noerror }
    }

    companion object {
        private val logger = KotlinLogging.logger("RelaxedAssertion2")
    }
}