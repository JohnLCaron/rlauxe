package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CompositeAuditRecord
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.math.min
import kotlin.test.Test

class TestRelaxedAssertions {
    val topdir = "$cases/belgium/belgium2024/"
    val auditRecord = AuditRecord.read(topdir)!! as CompositeAuditRecord
    val contests = auditRecord.contests
    val partyNames = auditRecord.readPartyNames()
    val lastRound = auditRecord.rounds.last()
    val config = auditRecord.config
    val sampleLimits = auditRecord.readSampleLimits()
    val sampleLimitMap = auditRecord.readSampleLimits().associateBy { it.id }

    @Test
    fun testOneFailure() {
        // Anvers has 4 DH failures. Do we need to try all combinations ?? = 2^4 = 16 ??
        val contestRound = lastRound.contestRounds.find { it.id == 1 }!!
        val sampleLimit = sampleLimitMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        RelaxedAssertions(contestRound, .05)
    }

    @Test
    fun testShowRelaxedAssertions() {
        // Anvers has 4 DH failures. Do we need to try all combinations ?? = 2^4 = 16 ??
        val contestRound = lastRound.contestRounds.find { it.id == 1 }!!
        val sampleLimit = sampleLimitMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }

        val relax2 = RelaxedAssertions(contestRound, .05)
        print(relax2.showRelaxedAssertions())
    }
}

// would be convenient if dcontest.assorters was correct2
fun showRelaxedAssertions2(dcontest: DhondtContest, assorters: List<AssorterIF>, haveMvrs: Int?, alpha: Double): String = buildString {
    /* appendLine("parties")
    dcontest.parties.forEach { appendLine("  $it")}
    appendLine() */

    val orgInfo = dcontest.info
    appendLine("winning seats")
    append(" seat ${sfn("winner-round", candNameWidth)}     ${sfn("nvotes", 6)}, ")
    append(" ${sfn(" score", 6)}, scoreDiff, maxRisk, maxAssertion")
    appendLine()

    // sorted scores
    var prevScore: DhondtScore? = null
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