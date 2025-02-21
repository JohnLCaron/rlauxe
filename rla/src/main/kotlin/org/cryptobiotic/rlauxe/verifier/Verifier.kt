package org.cryptobiotic.rlauxe.verifier

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.persist.json.Publisher
import org.cryptobiotic.rlauxe.workflow.AuditConfig
import org.cryptobiotic.rlauxe.workflow.AuditState
import org.cryptobiotic.rlauxe.workflow.AuditType

class Verifier(val publish: Publisher, val show: Boolean = false) {
    var auditConfig : AuditConfig = readAuditConfigJsonFile(publish.auditConfigFile()).unwrap()

    init {
        println()
        println(auditConfig)
    }

    fun verify(): Boolean {
        var allOk = true

        if (auditConfig.auditType == AuditType.CLCA) {
            allOk = allOk && verifyCvrSampleNumbers()
        } else {
            allOk = allOk && verifyBallotManifest()
        }

        val contests = mutableMapOf<Int, ContestUnderAudit>()
        var state: AuditState? = null
        for (roundIdx in 1..publish.rounds()) {
            //println("Round $roundIdx ------------------------------------")
            state = verifyRound(roundIdx)
            state.contests.forEach { contests[it.id] = it }
        }
        println()
        verifyContests(contests.toSortedMap().values.toList(), null)

        println("\nverify = $allOk")
        return allOk
    }

    fun verifyCvrSampleNumbers(): Boolean {
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
        return ok
    }

    fun verifyBallotManifest(): Boolean {
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
        return ok
    }

    fun verifyRound(roundIdx: Int): AuditState {
        var result = true
        val state = readAuditStateJsonFile(publish.auditRoundFile(roundIdx)).unwrap()
        println("${state.show()} ")

        if (roundIdx != state.roundIdx) {
            println("  *** roundIdx = ${state.roundIdx} should be = $roundIdx")
            result = false
        }

        val indices = readSampleIndicesJsonFile(publish.sampleIndicesFile(roundIdx)).unwrap()
        if (indices.size != state.nmvrs) {
            println("  *** nmvrs = ${state.nmvrs} should be = ${indices.size} ")
            result = false
        }

        if (state.auditWasDone) {
            val mvrs = readCvrsJsonFile(publish.sampleMvrsFile(roundIdx)).unwrap()
            if (indices.size != mvrs.size) {
                println("*** indices = ${indices.size} NOT EQUAL mvrs = ${mvrs.size}")
                result = false
            }
        }
        return state
    }

    fun verifyContests(contests: List<ContestUnderAudit>, roundIdx: Int?): Boolean {
        contests.forEach { contest ->
            if (contest.assertions().filter { roundIdx == null || it.round == roundIdx}.count() > 0) {
                println(contest.show(roundIdx))
            }
        }
        return true
    }
}