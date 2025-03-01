package org.cryptobiotic.rlauxe.audit

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.workflow.AuditConfig
import org.cryptobiotic.rlauxe.workflow.AuditState
import org.cryptobiotic.rlauxe.workflow.BallotOrCvr

import kotlin.math.max

class AuditRecord(
    val location: String,
    val auditConfig: AuditConfig,
    val rounds: List<AuditRound>,
    val cvrs: List<CvrUnderAudit>,
    val mvrs: Set<CvrUnderAudit>,
) {
    val nrounds = rounds.size

    companion object {

        fun readFrom(location: String): AuditRecord {
            val publisher = Publisher(location)
            val auditConfigResult = readAuditConfigJsonFile(publisher.auditConfigFile())
            val auditConfig = auditConfigResult.unwrap()
            val cvrs = readCvrsJsonFile(publisher.cvrsFile()).unwrap() // TODO must maintain order

            val mvrs = mutableSetOf<CvrUnderAudit>()
            val previousSamples = mutableSetOf<Int>()
            val rounds = mutableListOf<AuditRound>()
            for (roundIdx in 1..publisher.rounds()) {
                val state = readAuditStateJsonFile(publisher.auditRoundFile(roundIdx)).unwrap()
                val contests = state.contests.map { contest -> ContestRound(contest) }

                val sampledIndices = readSampleIndicesJsonFile(publisher.sampleIndicesFile(roundIdx)).unwrap()
                // may not exist yet
                val sampledMvrsResult = readCvrsJsonFile(publisher.sampleMvrsFile(roundIdx))
                val sampledMvrs = if (sampledMvrsResult is Ok) sampledMvrsResult.unwrap() else emptyList()
                mvrs.addAll(sampledMvrs) // cumulative

                val round = AuditRound(state, contests, sampledIndices)
                round.calcNewSamples(previousSamples)
                round.calcContestMvrs(cvrs)
                previousSamples.addAll(sampledIndices) // cumulative

                rounds.add(round)
            }
            return AuditRecord(location, auditConfig, rounds, cvrs, mvrs)
        }
    }
}

class AuditRound(
    val state: AuditState,
    val contests: List<ContestRound>,
    var sampledIndices: List<Int>,
) {
    var newSamples: Set<Int> = emptySet()
    var previousSetCopy: Set<Int> = emptySet()

    fun calcNewSamples(previousSet: Set<Int>? = null) {
        if (previousSet != null) this.previousSetCopy = setOf(*previousSet.toTypedArray())
        newSamples = sampledIndices.filter { it !in previousSetCopy }.toSet()
    }

    fun resetSamples(sampledIndices: List<Int>, cvrs: List<BallotOrCvr>) {
        this.sampledIndices = sampledIndices
        calcNewSamples()
        calcContestMvrs(cvrs)
    }

    fun calcContestMvrs(cvrs: List<BallotOrCvr>) {
        val actualMvrsCount = mutableMapOf<ContestRound, Int>() // contestId -> mvrs in sample
        val newMvrsCount = mutableMapOf<ContestRound, Int>() // contestId -> new mvrs in sample
        sampledIndices.forEach { sidx ->
            val boc = cvrs[sidx] // TODO this requires the cvrs be in order!!
            contests.forEach { contest ->
                if (boc.hasContest(contest.id)) {
                    actualMvrsCount[contest] = actualMvrsCount[contest]?.plus(1) ?: 1
                    if (sidx in newSamples) {
                        newMvrsCount[contest] = newMvrsCount[contest]?.plus(1) ?: 1
                    }
                }
            }
        }
        actualMvrsCount.forEach { contest, count ->  contest.actualMvrs = count }
        newMvrsCount.forEach { contest, count ->  contest.actualNewMvrs = count }
    }

    // used in viewer
    fun maxBallotsUsed(): Int {
        var result = 0
        contests.forEach { contest ->
            contest.contestUA.assertions().forEach { assertion ->
                assertion.roundResults.filter { it.roundIdx == state.roundIdx }.forEach { rr ->
                    result = max(result, rr.maxBallotIndexUsed)
                }
            }
        }
        return result
    }
}

class ContestRound(val contestUA: ContestUnderAudit) {
    val id = contestUA.id
    var actualMvrs = 0 // Actual number of new ballots with this contest contained in this round's sample.
    var actualNewMvrs = 0 // Estimate of the new samples required to confirm the contest
}