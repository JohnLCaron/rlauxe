package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.core.AboveThreshold
import org.cryptobiotic.rlauxe.core.BelowThreshold
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.doublePrecision
import org.cryptobiotic.rlauxe.persist.SortedManifest
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import kotlin.test.Test
import kotlin.test.assertEquals

class TestBelgiumContest {
    val config: Config
    val contests: List<ContestWithAssertions>
    val rounds: List<AuditRound>
    val infos: Map<Int, ContestInfo>
    val sortedManifest: SortedManifest

    init {
        val topdir = "$testdataDir/cases/belgium/belgium2024/Bruxelles"
        val auditRecord = AuditRecord.read(topdir) as AuditRecord
        val mvrManager = PersistedMvrManager(auditRecord)
        sortedManifest = mvrManager.sortedManifest()
        config = auditRecord.config
        contests = auditRecord.contests
        rounds = auditRecord.rounds
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

            } else if (assorter is BelowThreshold) { // TODO
                println(" dilutedMean= ${assorter.dilutedMean()}")

                val diff = contestd.difficulty(assorter)
                println(" diff = $diff")
                val gmean = diff/contestd.Nc
                println(" diff/Nc = ${diff/contestd.Nc}")
                //assertEquals(diff/contestd.Nc, contestd.recountMargin(assorter), doublePrecision)

                //val hmean = assorter.h2(gmean)
                //println(" hmean = ${assorter.h2(gmean)}")
                //assertEquals(assorter.dilutedMean(), hmean, doublePrecision)

            } else if (assorter is AboveThreshold) { // TODO
                println("  dilutedMean= ${assorter.dilutedMean()}")
                println(" reportedMean= ${assorter.reportedMean()}")
                println("  dilutedMargin= ${assorter.dilutedMargin()}")
                println(" reportedMargin= ${assorter.reportedMargin()}")
                println(" recountMargin = ${contestd.recountMargin(assorter)}")

                val diff = contestd.difficulty(assorter)
                println(" difficulty = $diff")
                println(" diff/Nc = ${diff/contestd.Nc}")
                //if (!doubleIsClose(diff/contestd.Nc, contestd.recountMargin(assorter), doublePrecision))
               //     print("")
                //assertEquals(diff/contestd.Nc, contestd.recountMargin(assorter), doublePrecision)

                // wtf
                //val gmean = diff/contestd.Nc
                //val hmean = assorter.h2(gmean)
                //println(" hmean = ${assorter.h2(gmean)}")
                //assertEquals(assorter.dilutedMean(), hmean, doublePrecision)
            }

            println(" margin = ${assorter.margin(contestUA.hasStyle)}")
            println(" calcMarginFromRegVotes = ${assorter.calcMarginFromRegVotes(contestd.votes, contestd.Nc)}")
            assertEquals(assorter.margin(contestUA.hasStyle), assorter.calcMarginFromRegVotes(contestd.votes, contestd.Nc), doublePrecision)

            println("recountMargin = ${contestd.recountMargin(assorter)}")
            println("showDifficulty = ${contestd.showAssertionDifficulty(assorter)}")
            println()
        }
    }
}
