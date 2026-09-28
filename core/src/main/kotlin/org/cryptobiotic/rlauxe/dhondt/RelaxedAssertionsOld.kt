package org.cryptobiotic.rlauxe.dhondt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.betting.estRiskStandardBet
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.util.DepthNodeIterator
import org.cryptobiotic.rlauxe.util.Indent
import org.cryptobiotic.rlauxe.util.TreeNode
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.math.min

/*
private val logger = KotlinLogging.logger("RelaxedAssertions")
private val debug = false

class RelaxedAssertionsOld(val contestRound: ContestRound, alpha: Double) {
    val useAlpha = contestRound.auditorWantRisk ?: alpha
    val orgContest = contestRound.contestUA.contest as DhondtContest
    // must get from contestRound.contestUA, not auditRecord.contests
    val orgAssorters = contestRound.contestUA.clcaAssertions.map { it.assorter }

    val orgInfo = orgContest.info
    val votes = orgContest.votes
    val Npop = contestRound.contestUA.Npop
    val nsamples = contestRound.haveSampleSize
    var rootNode = TreeNode(AltContest(null).fromContest(orgContest))

    init {
        logger.debug{"Contest ${orgInfo.name} haveSampleSize=${contestRound.haveSampleSize}"}
        val failures = makeFailures(orgContest, orgAssorters)
        recurseForFailures(rootNode, failures, Indent(0, nspaces = 4))
    }

    fun showRelaxedAssertions() = showRelaxedAssertionsOld(orgContest, orgAssorters, nsamples, useAlpha)

    fun show() = buildString {
        appendLine("Contest ${orgInfo.name} DH failure branches")
        var count = 0
        DepthNodeIterator(rootNode).forEach {
            appendLine("(${it.name()}) : ${it.value.show(cumulRisk(it))}")
            count++
        }
        appendLine("DH failure branches ($count)")
    }

    fun cumulRisk(node: TreeNode<AltContest>? ): Double {
        var cumul = 1.0
        var cursor = node
        while (cursor != null) {
            cumul *= cursor.value.risk()
            cursor = cursor.parent
        }
        return cumul
    }

    fun recurseForFailures(parentNode: TreeNode<AltContest>, failures: List<DhondtRiskFailure>, indent : Indent) {
        failures.forEach { failure ->
            if (debug)  println("$indent ${failure.show()}")
            val childNode = makeChildNode(parentNode, failure)
            makeAltContestFromFlippedAssertion(childNode, failure)
            recurseForFailures(childNode, childNode.value.newFailures, indent.incr())
        }
    }

    fun makeFailures(fromContest: DhondtContest, fromAssorters: List<AssorterIF>): List<DhondtRiskFailure> {
        val failures = mutableListOf<DhondtRiskFailure>()
        // when there are multipled falres you have to do each combination
        fromAssorters.filter { it is DhondtAssorter }.forEach { assorter ->
            val dassorter = assorter as DhondtAssorter
            val risk = estRiskStandardBet(Npop, dassorter.noerror(true), nsamples)
            if (risk > useAlpha) {
                val winnerId = dassorter.winner()
                val loserId = dassorter.loser()
                val winnerScore =
                    fromContest.sortedScores.find { it.divisor == dassorter.winnerDivisor && it.partyId == winnerId }!!
                val loserScore =
                    fromContest.sortedScores.find { it.divisor == dassorter.loserDivisor && it.partyId == loserId }!!

                val alreadyExists = fromAssorters.find { it.hashcodeDesc() == dassorter.hashcodeDesc() } != null // wtf ??
                failures.add(DhondtRiskFailure(Npop, dassorter, winnerScore, loserScore, risk, nsamples, alreadyExists))
            }
        }
        return failures
    }

    fun makeChildNode(parentNode: TreeNode<AltContest>, failure: DhondtRiskFailure): TreeNode<AltContest> {
        val altContest = AltContest(failure).fromContest(parentNode.value.fromContest!!)
        altContest.skipAssertions.addAll(parentNode.value.skipAssertions)

        val childNode = parentNode.add(altContest)
        return childNode
    }

    fun makeAltContestFromFlippedAssertion(childNode: TreeNode<AltContest>, failure: DhondtRiskFailure) {
        val altContest = childNode.value
        val fromContest = altContest.fromContest!! // maybe you should pass this in ??

        // in order to flip the winner/loser assertion, youd have to change the reported votes / margin
        // and all the changed assertions would depend on what the score gap is.
        // println("*** make makeAltContestFromFlippedAssertion for ${failure.assorter}")

        // lets just manipuate the lastSeatWon/firstSeatLost
        val winner = failure.assorter.winner()
        val loser = failure.assorter.loser()

        if (childNode.name() == "B" && failure.assorter.shortName() == "CD&V/3-VLAAMS BELANG/6")
            print("bad")

        // change to builder so we can modify
        val parties = fromContest.parties.map { DhondtPartyBuilder(it) }
        val winnerParty = parties.find { it.id == winner }!!
        val loserParty = parties.find { it.id == loser }!!
        if (winnerParty.lastSeatWon == null || loserParty.firstSeatLost == null) {
            logger.warn { "${childNode.name()} ${failure.assorter.shortName()}: $winnerParty = ${winnerParty.lastSeatWon} and $loserParty = ${loserParty.firstSeatLost}" }
            val debugInfo = buildString {
                appendLine(fromContest.show())
                fromContest.parties.forEach { appendLine("  $it")}
            }
            logger.warn { debugInfo }
            val debugInfo2 = buildString {
                appendLine("orgContest = ${orgContest.show()}")
                orgContest.parties.forEach { appendLine("  $it")}
            }
            logger.warn { debugInfo2 }
            return
        }

        // is this where we get into trouble ??
        winnerParty.firstSeatLost = winnerParty.lastSeatWon
        winnerParty.lastSeatWon = if (winnerParty.lastSeatWon!! > 0) winnerParty.lastSeatWon!! - 1 else null

        loserParty.lastSeatWon = loserParty.firstSeatLost
        loserParty.firstSeatLost = loserParty.firstSeatLost!! + 1

        // why did we think we couldnt do this ??
        // because if you use the normal assignWinners(), you'd get the same contest
        val builder = DhondtBuilder(
            name = fromContest.name,
            id = fromContest.id,
            partyBs = parties,
            nseats = fromContest.info.nwinners,
            Nc = fromContest.Nc,
            undervotes = fromContest.undervotes,
            minFraction = fromContest.info.minFraction!!,
            fromContest.partiesBelowThreshold, // only one that changes
            flip = true
        )

        val dalt = builder.build()
        if (debug) println("parent.skipAssertions = ${altContest.skipAssertions}")
        dalt.assorters.removeAll { altContest.skipAssertions.contains(it.shortName()) }
        altContest.fromContest(dalt) // now switch to
        altContest.setFailures(makeFailures(dalt, dalt.assorters))
    }

    fun makeAltContestFromWinnerList(childNode: TreeNode<AltContest>, failure: DhondtRiskFailure) {
        val altContest = childNode.value
        val fromContest = altContest.fromContest!! // maybe you should pass this in ??

        // in order to flip the winner/loser assertion, youd have to change the reported votes / margin
        // and all the changed assertions would depend on what the score gap is.
        // println("*** make makeAltContestFromFlippedAssertion for ${failure.assorter}")

        // lets just manipuate the lastSeatWon/firstSeatLost
        val winner = failure.assorter.winner()
        val loser = failure.assorter.loser()

        // heres where we need fromContest
        val parties = fromContest.parties.map { DhondtPartyBuilder(it) }
        val winnerParty = parties.find { it.id == winner }!!
        val loserParty = parties.find { it.id == loser }!!
        if (winnerParty.lastSeatWon == null || loserParty.firstSeatLost == null)
            logger.warn{ "winnerParty.lastSeatWon == null || loserParty.firstSeatLost" }

        winnerParty.firstSeatLost = winnerParty.lastSeatWon
        winnerParty.lastSeatWon = if (winnerParty.lastSeatWon!! > 0) winnerParty.lastSeatWon!! - 1 else null

        loserParty.lastSeatWon = loserParty.firstSeatLost
        loserParty.firstSeatLost = loserParty.firstSeatLost!! + 1

        // why did we think we couldnt do this ??
        // because if you use the normal assignWinners(), you'd get the same contest
        // heres where we need fromContest
        val builder = DhondtBuilder(
            name = fromContest.name,
            id = fromContest.id,
            partyBs = parties,
            nseats = fromContest.info.nwinners,
            Nc = fromContest.Nc,
            undervotes = fromContest.undervotes,
            minFraction = fromContest.info.minFraction!!,
            fromContest.partiesBelowThreshold, // only one that changes
            flip = true
        )

        val dalt = builder.build()
        if (debug) println("parent.skipAssertions = ${altContest.skipAssertions}")
        dalt.assorters.removeAll { altContest.skipAssertions.contains(it.shortName()) }
        altContest.fromContest(dalt) // now switch to
        altContest.setFailures(makeFailures(dalt, dalt.assorters))
    }

    // TODO do we really skip both the assertions and its flip ??
    class AltContest(
        val fromFailure: DhondtRiskFailure?,
    ) {
        val skipAssertions = mutableSetOf<String>()
        var newFailures = emptyList<DhondtRiskFailure>()
        var fromContest: DhondtContest? = null // source of contest constant info; always orgContest ??

        init {
            if (fromFailure != null) {
                skipAssertions.add(fromFailure.assorter.shortName())
                skipAssertions.add(fromFailure.assorter.reverseName())
            }
        }

        fun fromContest(fromContest: DhondtContest): AltContest {
            this.fromContest = fromContest
            return this
        }

        fun setFailures(failures: List<DhondtRiskFailure>) {
            newFailures = failures
            failures.forEach {
                skipAssertions.add(it.assorter.shortName())
                skipAssertions.add(it.assorter.reverseName())
            }
        }

        fun risk() = fromFailure?.risk ?: 1.0

        fun show(cumul: Double)= buildString {
            appendLine("AltContest fromFailure=${fromFailure?.show() ?: "none"} cumul=${dfn(cumul, 4)}")
            appendLine("     skipAssertions=$skipAssertions")
            if (newFailures.isEmpty())
                appendLine("     no new failures")
            else
                newFailures.forEach { appendLine("        ${it.show()}")}
        }

    }
}

fun DhondtRiskFailure.show() = buildString {
    val assorter = assorter
    append("failed '${assorter.desc()}' has $samplesUsed/${estMvrs()} samples : risk = ${dfn(risk, 4)}")
}

// would be convenient if dcontest.assorters was correct2
fun showRelaxedAssertionsOld(dcontest: DhondtContest, assorters: List<AssorterIF>, haveMvrs: Int?, alpha: Double): String = buildString {
    /* appendLine("parties")
    dcontest.parties.forEach { appendLine("  $it")}
    appendLine() */

    val orgInfo = dcontest.info
    appendLine("winning seats")
    append(" seat ${sfn("winner-round", candNameWidth)}     ${sfn("nvotes", 6)}, ")
    append(" ${sfn(" score", 6)}, scoreDiff, maxRisk, maxAssertion")
    appendLine()

    // sorted scores
    var prevScore: DhondtCandidateScore? = null
    // the winners
    repeat(dcontest.nseats) { idx ->
        val score = dcontest.sortedScores[idx]
        // sortedRawScores.filter{ it.divisor <= maxRound }.forEachIndexed { idx, score ->
        val candId = score.partyId
        append(" (${nfn(idx + 1, 2)}) ")
        val nameRound = "${orgInfo.candidateIdToName[candId]!!}/${score.divisor}"
        val below = if (dcontest.partiesBelowThreshold.contains(candId)) "*" else " "
        append(" ${trunc(nameRound, candNameWidth)}$below, ")
        append(" ${nfn(dcontest.votes[candId]!!, 6)}, ${nfn(score.score.toInt(), 6)}, ")
        if (prevScore != null) append("    ${nfn(prevScore.score.toInt() - score.score.toInt(), 6)},")
        else append("          ,")
        val (maxName, maxRisk) = maxRiskForWinnerSeat(nameRound, assorters, haveMvrs, dcontest.Nc)
        append(" ${dfn(maxRisk, 3)}, ")
        if (maxRisk > alpha) append(maxName)
        prevScore = score
        appendLine()
    }
    val lastWinner = prevScore!!

    // the losers
    val losersLeft: Int = dcontest.sortedScores.size - dcontest.nseats
    val maxLosers = min(6, losersLeft)

    repeat(maxLosers) { idx ->
        val scoreRank = dcontest.nseats + idx
        val loser = dcontest.sortedScores[scoreRank]
        val candId = loser.partyId
        append("      ")
        val nameRound = "${orgInfo.candidateIdToName[candId]}/${loser.divisor}"
        val below = if (dcontest.partiesBelowThreshold.contains(candId)) "*" else " "
        append(" ${trunc(nameRound, candNameWidth)}$below, ")
        append(" ${nfn(dcontest.votes[candId]!!, 6)}, ${nfn(loser.score.toInt(), 6)}, ")

        if (prevScore != null) append("    ${nfn(prevScore.score.toInt() - loser.score.toInt(), 6)},")
        else append("          ,")
        val (maxName, maxRisk) = maxRiskForLoserSeat(nameRound, assorters, haveMvrs, dcontest.Nc)
        append(" ${dfn(maxRisk, 3)}, ")
        if (maxRisk > alpha) append(maxName)

        prevScore = loser
        appendLine()
    }
    appendLine()
}

 */