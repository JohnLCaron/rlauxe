package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.corlaInput.removeParen
import org.cryptobiotic.rlauxe.persist.AuditRecord
import kotlin.test.Test

class TestMisc {

    @Test
    fun testRemoveParen() {
        val s = "sdkajhsdkjh999 (last one)"
        println("'$s' -> '${removeParen(s)}'")
        val s2 = "trailing blank (last one) "
        println("'$s2' -> '${removeParen(s2)}'")
        val s3 = "Thompson R2-J School District Board of Education Director District A (4 Year Term)"
        println("'$s3' -> '${removeParen(s3)}'")
    }

    @Test
    fun testNoerror() {
        val auditRecord = AuditRecord.read("/home/joncifer/rla/cases/corla/2020")!!
        val lastAuditRound = auditRecord.rounds.last()

        val contestRoundMap = mutableMapOf<Int, ContestRound>()
        for (contestRound in lastAuditRound!!.contestRounds) {
            contestRoundMap.put(contestRound.id, contestRound)
        }
        val cwa = auditRecord.contests.find { it.id == 285 }!!

        val minClcaAssertion = cwa.minClcaAssertion()
        val noerrorClca = minClcaAssertion!!.noerror
        println("noerrorClca = $noerrorClca")

        val minAssertion = cwa.minAssertion()!!
        val noerror =  minAssertion.assorter.noerror()
        println("    noerror = $noerror")
        println("diff = ${noerror - noerrorClca}")

        val Npop = 4817
        val alpha = .04
        println()
        println("1estSampleSizeStandardBet=${estSampleSizeStandardBet(Npop, noerrorClca, alpha)}")
        println("2estSampleSizeStandardBet=${estSampleSizeStandardBet(Npop, noerror, alpha)}")
    }

}