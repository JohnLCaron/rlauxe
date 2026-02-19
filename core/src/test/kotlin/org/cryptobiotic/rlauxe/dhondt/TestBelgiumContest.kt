package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.core.AboveThreshold
import org.cryptobiotic.rlauxe.core.BelowThreshold
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.doublePrecision
import org.cryptobiotic.rlauxe.workflow.CardManifest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestBelgiumContest {
    val config: AuditConfig
    val contests: List<ContestWithAssertions>
    val infos: Map<Int, ContestInfo>
    val cardManifest: CardManifest

    init {
        val auditdir = "$testdataDir/cases/belgium/2024/Anvers/audit"
        val auditRecord = AuditRecord.readFrom(auditdir) as AuditRecord
        cardManifest = auditRecord.readCardManifest()
        config = auditRecord.config
        contests = auditRecord.contests
        infos = contests.map{ it.contest.info() }.associateBy { it.id }
    }

    @Test
    fun testAssorters() {
        testAssorters(contests.first())
    }

    fun testAssorters(contestUA: ContestWithAssertions) {
        val contestd = contestUA.contest as DHondtContest
        // contestd.assorters is empty when deserialized

        contestUA.assertions().forEach { assertion ->
            val assorter = assertion.assorter
            println(assorter)

            if (assorter is DHondtAssorter) {
                println(" setDilutedMean = ${setDilutedMean(assorter, contestd)}")
                println(" dilutedMean= ${assorter.dilutedMean()}")
                assertEquals(assorter.dilutedMean(), setDilutedMean(assorter, contestd), doublePrecision)

                val diff = contestd.difficulty(assorter)
                println(" diff = $diff")
                val gmean = diff/contestd.Nc
                println(" diff/Nc = ${diff/contestd.Nc}")

                val hmean = assorter.h2(gmean)
                println(" hmean = ${assorter.h2(gmean)}")
                assertEquals(assorter.dilutedMean(), hmean, doublePrecision)

            } else if (assorter is BelowThreshold) {
                println(" dilutedMean= ${assorter.dilutedMean()}")

                val diff = contestd.difficulty(assorter)
                println(" diff = $diff")
                val gmean = diff/contestd.Nc
                println(" diff/Nc = ${diff/contestd.Nc}")
                assertEquals(diff/contestd.Nc, contestd.recountMargin(assorter), doublePrecision)

                val hmean = assorter.h2(gmean)
                println(" hmean = ${assorter.h2(gmean)}")
                assertEquals(assorter.dilutedMean(), hmean, doublePrecision)

            } else if (assorter is AboveThreshold) {
                println(" dilutedMean= ${assorter.dilutedMean()}")

                val diff = contestd.difficulty(assorter)
                println(" diff = $diff")
                val gmean = diff/contestd.Nc
                println(" diff/Nc = ${diff/contestd.Nc}")
                assertEquals(diff/contestd.Nc, contestd.recountMargin(assorter), doublePrecision)

                val hmean = assorter.h2(gmean)
                println(" hmean = ${assorter.h2(gmean)}")
                assertEquals(assorter.dilutedMean(), hmean, doublePrecision)
            }

            println(" dilutedMargin = ${assorter.dilutedMargin()}")
            println(" calcMarginFromRegVotes = ${assorter.calcMarginFromRegVotes(contestd.votes, contestd.Nc)}")
            assertEquals(assorter.dilutedMargin(), assorter.calcMarginFromRegVotes(contestd.votes, contestd.Nc), doublePrecision)

            println("recountMargin = ${contestd.recountMargin(assorter)}")
            println("showDifficulty = ${contestd.showAssertionDifficulty(assorter)}")
            println()
        }
    }
}
