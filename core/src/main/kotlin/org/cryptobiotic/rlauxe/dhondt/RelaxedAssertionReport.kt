package org.cryptobiotic.rlauxe.dhondt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.util.Indent
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.math.min

class RelaxedAssertionReport(val builder: CandSeatRangeBuilder) {
    val dcontest: DHondtContest = builder.dcontest
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

    private val show = false
    private val showLosers = 6

    data class Score(val winner: DhondtScore, val loser: DhondtScore, val startLoser: Int, val nlosers: Int) {
        var assorter: DHondtAssorter? = null
        var diff: Int = 0
        var minDiff: Int = 0
        var fails: Boolean = false

        fun name(): String {
            return if (assorter != null)
                "${assorter!!.winnerNameRound()}-${assorter!!.loserNameRound()}"
            else
                "${winner.candidate}/${winner.divisor}-${loser.candidate}/${loser.divisor}"
        }

        override fun toString() = "${if (fails) "**" else ""}(winner=${winner.candidate}/${winner.divisor}, loser=${loser.candidate}/${loser.divisor}, " +
                "startLoser=$startLoser)"
    }

    class KeepScore() {
        val rows = mutableMapOf<Int, RowScores>()
        fun addColumn(startRow: Int, cols: MutableList<Score>) {
            repeat(cols.size) { idx ->
                val row = rows.getOrPut(startRow + idx, { RowScores(startRow + idx) })
                row.scores.add(cols[idx])
            }
        }

        fun getRow(row: Int): List<Score> {
            if (rows[row] == null)
                print("")
            return rows[row]?.scores ?: emptyList()
        }

        class RowScores(val row: Int) {
            val scores = mutableListOf<Score>()
        }
    }

    fun showRelaxedAssertions(): String = buildString {
        //// reported winners
        appendLine("${dcontest}")
        appendLine("reported winners")
        append(" seat ${sfn("winner-round", candNameWidth)}     ${sfn("nvotes", 6)}, ")
        append(" ${sfn(" score", 6)}, scoreDiff, scoreDiffMin")
        appendLine()

        // sorted scores
        var prevScore: DhondtScore? = null
        // the winners
        repeat(dcontest.nseats) { idx ->
            val score = dcontest.sortedScores[idx]
            // sortedRawScores.filter{ it.divisor <= maxRound }.forEachIndexed { idx, score ->
            val candId = score.candidate
            append(" (${nfn(idx + 1, 2)}) ")
            val nameRound = "${builder.orgInfo.candidateIdToName[candId]!!}/${score.divisor}"
            val below = if (builder.belowMinPct.contains(candId)) "*" else " "
            append(" ${trunc(nameRound, candNameWidth)}$below, ")
            append(" ${nfn(builder.votes[candId]!!, 6)}, ${nfn(score.score.toInt(), 6)}, ")

            if (prevScore != null) append("    ${nfn(prevScore.score.toInt() - score.score.toInt(), 6)},")
            prevScore = score
            appendLine()
        }
        val lastWinner = prevScore!!

        // the losers
        val losersLeft: Int = dcontest.sortedScores.size - dcontest.nseats
        val maxLosers = min(showLosers, losersLeft)
        var keepScore = KeepScore()

        // build KeepScore
        repeat(maxLosers) { idx ->
            val scoreRank = dcontest.nseats + idx
            val loser = dcontest.sortedScores[scoreRank]
            val score = Score(lastWinner, loser, dcontest.nseats + idx + 1, dcontest.nseats + maxLosers)
            buildKeepScore(score, keepScore, Indent(0, nspaces=4))
        }

        repeat(maxLosers) { idx ->
            val scoreRank = dcontest.nseats + idx
            val loser = dcontest.sortedScores[scoreRank]
            // sortedRawScores.filter{ it.divisor <= maxRound }.forEachIndexed { idx, score ->
            val candId = loser.candidate
            append("      ")
            val nameRound = "${builder.orgInfo.candidateIdToName[candId]}/${loser.divisor}"
            val below = if (builder.belowMinPct.contains(candId)) "*" else " "
            append(" ${trunc(nameRound, candNameWidth)}$below, ")
            append(" ${nfn(builder.votes[candId]!!, 6)}, ${nfn(loser.score.toInt(), 6)}, ")

            val scoreTree = Score(lastWinner, loser, dcontest.nseats+idx+1, dcontest.nseats+maxLosers)
            val sb = StringBuffer()
            showRowScore(scoreTree, keepScore, sb)
            appendLine(sb)
            if (show) println()
        }

        appendLine()

        append( showDhondtRiskFailures() )
        appendLine()
    }

    fun showRowScore(score : Score, keepScore: KeepScore, sb: StringBuffer) {
        showScore(score, sb)
        keepScore.getRow(score.startLoser).forEach { sib ->
            showScoreWithName(sib, sb)
        }
    }

    fun showScore(score : Score, sb: StringBuffer) {
        testScore(score)
        sb.append("    ${nfn(score.diff, 6)}, ${nfn(score.minDiff,6)}${if (score.fails) "*" else " "}")
    }

    fun showScoreWithName(score : Score, sb: StringBuffer) {
        testScore(score)
        val s = " ${score.name()}: ${score.diff}, ${score.minDiff}${if (score.fails) "*" else " "}"
        sb.append(" ${trunc(s, 40)};")
    }

    fun buildKeepScore(score : Score, keepScore: KeepScore, indent: Indent) {
        testScore(score)
        if (show) println("${indent}$score")

        if (score.fails) {
            val col = mutableListOf<Score>()
            for (scoreRank in score.startLoser until score.nlosers) {
                val nextLoser = dcontest.sortedScores[scoreRank]
                val test = Score(score.loser, nextLoser, scoreRank+1, score.nlosers)
                col.add(test)
            }
            keepScore.addColumn(score.startLoser+1, col)
            col.forEach {
                buildKeepScore(it, keepScore, indent.incr())
            }
        }
    }

    fun testScore(score: Score) {
        score.diff = score.winner.score.toInt() - score.loser.score.toInt()
        val assorter = builder.findAssorter(score.winner, score.loser)
        score.minDiff = assorter.scoreRange(dcontest.Nc, builder.nsamples, alpha)
        score.fails = (score.diff < score.minDiff)
        score.assorter = assorter
    }

    // not crazy about these DhondtLoserGroup
    fun showDhondtRiskFailures() = buildString {

        appendLine("Contested       loser/round   nvotes,  score, scoreDiff,  noerror, estSamples, actSamples, estRisk, assertion")

        var idx = 0
        builder.failureNodes.forEach {  node ->
            val failure = node.failure
            val assorter = failure.assorter
            val candId = assorter.loser()
            val winningSeat = failure.winnerScore.winningSeat
            if (winningSeat == null)
                append("       ")
            else
                append(" (${nfn(winningSeat, 2)})  ")

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

    // a >? b
    // a > c
    // b >? c
    //

    ///////////////////////////////////////////////////////////////////////////////////////////////////
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
                    append(" (${nfn(winningSeat, 2)})  ")

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
            done.add(altContest.failure.assorter.reverseName())

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
        repeat(nseats + 3) { idx ->
            val score = sortedScores[idx]
            val candId = score.candidate
            if (idx < nseats) append(" (${nfn(idx + 1, 2)}) ") else append("      ")
            append(" ${trunc(info.candidateIdToName[candId]!!, candNameWidth)}")
            append("-${nfn(score.divisor, 2)}, ")
            append(" ${nfn(votes[candId]!!, 6)}, ${nfn(score.score.toInt(), 6)}, ")

            if (prev != null) append(" ${nfn(prev - score.score.toInt(), 6)},")
            prev = score.score.toInt()

            // find the assertion
            // append("${sfn("scoreRange", 10)}")
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