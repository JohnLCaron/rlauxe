package org.cryptobiotic.rlauxe.verifier

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.persist.json.Publisher
import org.cryptobiotic.rlauxe.workflow.AuditConfig
import org.cryptobiotic.rlauxe.workflow.AuditState
import org.cryptobiotic.rlauxe.workflow.AuditType

class Verifier(val publish: Publisher) {
    var auditConfig : AuditConfig = readAuditConfigJsonFile(publish.auditConfigFile()).unwrap()

    init {
        println("auditConfig = $auditConfig")
    }

    fun verify(): Boolean {
        var allOk = true

        if (auditConfig.auditType == AuditType.CARD_COMPARISON) {
            allOk = allOk && verifyCvrSampleNumbers()
        } else {
            allOk = allOk && verifyBallotManifest()
        }

        var state: AuditState? = null
        for (roundIdx in 1..publish.rounds()) {
            state = verifyRound(roundIdx)
            verifyContests(state)
            println("------------------------------------")
        }
        // allOk = allOk && verifyContests(state)

        println("verify = $allOk")
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
        println("  verifyCvrs $ok size=${cvrs.size} bad=${countBad} ")
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
        println("  verifyBallots $ok size=${ballotManifest.ballots.size} bad=${countBad} ")
        return ok
    }

    fun verifyRound(roundIdx: Int): AuditState {
        var result = true
        val state = readAuditStateJsonFile(publish.auditRoundFile(roundIdx)).unwrap()
        println(" ${state.show()}")

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

    fun verifyContests(state: AuditState?): Boolean {
        if (state == null) return false
        println()
        state.contests.forEach { contest ->
            println(contest.show())
        }

        return true
    }
}