package org.cryptobiotic.rlauxe.verifier

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.persist.json.Publisher
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.workflow.AuditConfig
import org.cryptobiotic.rlauxe.workflow.AuditRound
import org.cryptobiotic.rlauxe.workflow.AuditType
import org.cryptobiotic.rlauxe.workflow.ContestRound

// TODO
class ShowPersistantStateResults(val publish: Publisher, val show: Boolean = false) {
    var auditConfig : AuditConfig = readAuditConfigJsonFile(publish.auditConfigFile()).unwrap()

    init {
        println()
        println(auditConfig)
    }

    fun verify() {
        val ncards = if (auditConfig.auditType == AuditType.CLCA) {
            verifyCvrSampleNumbers()
        } else {
            verifyBallotManifest()
        }

        var totalMvrs = 0
        val contests = mutableMapOf<Int, ContestRound>()
        var state: AuditRound? = null
        for (roundIdx in 1..publish.rounds()) {
            //println("Round $roundIdx ------------------------------------")
            state = verifyRound(roundIdx)
            state.contests.forEach { contests[it.id] = it }
            totalMvrs += state.nmvrs // TODO Wrong
        }
        println("  totalMvrs = $totalMvrs = ${df(100.0 * totalMvrs / ncards)} %")
        println()
        showContests(contests.toSortedMap().values.toList())
        println()
        verifyContests(contests.toSortedMap().values.toList(), null)
    }

    fun verifyCvrSampleNumbers(): Int {
        val cvrs: List<CvrUnderAudit> = readCvrsJsonFile(publish.cvrsFile()).unwrap()
        val prng = Prng(auditConfig.seed)
        var countBad = 0
        cvrs.forEach{
            if (it.sampleNum != prng.next()) {
                countBad++
                if (countBad > 10) throw RuntimeException()
            }
        }
        val ok = (countBad == 0)
        if (show) println("  verifyCvrs $ok size=${cvrs.size} bad=${countBad} ")
        return cvrs.size
    }

    fun verifyBallotManifest(): Int {
        val ballotManifest = readBallotManifestJsonFile(publish.ballotManifestFile()).unwrap()
        val prng = Prng(auditConfig.seed)
        var countBad = 0
        ballotManifest.ballots.forEach{
            if (it.sampleNum != prng.next()) {
                countBad++
                if (countBad > 10) throw RuntimeException()
            }
        }
        val ok = (countBad == 0)
        if (show) println("  verifyBallots $ok size=${ballotManifest.ballots.size} bad=${countBad} ")
        return ballotManifest.ballots.size
    }

    fun verifyRound(roundIdx: Int): AuditRound {
        var result = true
        val state = readAuditRoundJsonFile(publish.auditRoundFile(roundIdx)).unwrap()
        println("${state.show()} ")

        if (roundIdx != state.roundIdx) {
            println("  *** roundIdx = ${state.roundIdx} should be = $roundIdx")
            result = false
        }

        if (state.auditWasDone) {
            val indices = readSampleIndicesJsonFile(publish.sampleIndicesFile(roundIdx)).unwrap()
            if (indices.size != state.nmvrs) {
                println("  *** nmvrs = ${state.nmvrs} should be = ${indices.size} ")
                result = false
            }
            val mvrs = readCvrsJsonFile(publish.sampleMvrsFile(roundIdx)).unwrap()
            if (indices.size != mvrs.size) {
                println("*** indices = ${indices.size} NOT EQUAL mvrs = ${mvrs.size}")
                result = false
            }
        }
        return state
    }

    fun showContests(contests: List<ContestRound>): Boolean {
        contests.forEach { contest ->
             println(contest.toString())
        }
        return true
    }

    fun verifyContests(contests: List<ContestRound>, roundIdx: Int?): Boolean {
        contests.forEach { contest ->
            if (contest.assertions.filter { roundIdx == null || it.round == roundIdx}.count() > 0) {
                println(contest.contestUA.show(roundIdx))
            }
        }
        return true
    }
}