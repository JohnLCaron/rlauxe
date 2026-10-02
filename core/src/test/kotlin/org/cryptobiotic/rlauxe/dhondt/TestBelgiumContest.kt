package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.core.AboveThreshold
import org.cryptobiotic.rlauxe.core.BelowThreshold
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.util.doublePrecision
import org.cryptobiotic.rlauxe.persist.SortedManifest
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
        val topdir = "$cases/belgium/belgium2024/Bruxelles"
        val auditRecord = AuditRecord.read(topdir) as AuditRecord
        val mvrManager = PersistedMvrManager(auditRecord)
        sortedManifest = mvrManager.sortedManifest()
        config = auditRecord.config
        contests = auditRecord.contests
        rounds = auditRecord.rounds
        infos = contests.map { it.contest.info() }.associateBy { it.id }
    }

    @Test
    fun testAssorters() {
        testAssorters(contests.first())
    }

    fun testAssorters(contestUA: ContestWithAssertions) {
        val contestd = contestUA.contest as DhondtContest
        // contestd.assorters is empty when deserialized TODO still true ??

        contestUA.assertions().forEach { assertion ->
            val assorter = assertion.assorter
            println(assorter)


            contestd.assorters.forEach {
                println(it)
                assertEquals(it, it)
                assertEquals(it.hashCode(), it.hashCode())

                if (it is DhondtAssorter) {
                    println(" setDilutedMean = ${setDilutedMean(it, contestd)}")
                    println(" dilutedMean= ${it.dilutedMean()}")
                    println(" reportedMean= ${it.reportedMean()}")
                    assertEquals(it.dilutedMean(), setDilutedMean(it, contestd), doublePrecision)

                    assertEquals(contestd.difficulty(it), 1.0 / it.reportedMargin(), doublePrecision)

                    val gmean = contestd.marginInVotes(it) / contestd.Nc.toDouble()
                    println(" gmean = ${gmean}")

                    val hmean = it.h2(gmean)
                    println(" hmean = ${it.h2(gmean)}")
                    assertEquals(it.dilutedMean(), hmean, 1.0e-5) // why not perfect ??

                } else if (it is BelowThreshold) {
                    println(" dilutedMean= ${it.dilutedMean()}")

                    assertEquals(contestd.difficulty(it), 1.0 / it.reportedMargin(), doublePrecision)

                    val gmean = contestd.marginInVotes(it) / contestd.Nc.toDouble()
                    val hmean = it.h2(gmean)
                    println(" hmean = ${it.h2(gmean)}")
                    assertEquals(it.dilutedMean(), hmean, 1.0e-5) // why not perfect ??

                } else if (it is AboveThreshold) {
                    println(" dilutedMean= ${it.dilutedMean()}")

                    assertEquals(contestd.difficulty(it), 1.0 / it.reportedMargin(), doublePrecision)

                    val gmean = contestd.marginInVotes(it) / contestd.Nc.toDouble()
                    println(" gmean = ${gmean}")
                    val hmean = it.h2(gmean)
                    println(" hmean = ${it.h2(gmean)}")
                    assertEquals(it.dilutedMean(), hmean, 1.0e-5) // why not perfect ??
                }

                println(" margin = ${it.margin(true)}")
                println(" calcMarginFromRegVotes = ${it.calcMarginFromRegVotes(contestd.votes, contestd.Nc)}")
                assertEquals(it.margin(true), it.calcMarginFromRegVotes(contestd.votes, contestd.Nc), doublePrecision)

                println("recountMargin = ${contestd.recountMargin(it)}")
                println("showDifficulty = ${contestd.showAssertionDifficulty(it)}")
                println()
            }
        }

        fun testAssortersOld(contestUA: ContestWithAssertions) {
            val contestd = contestUA.contest as DhondtContest
            // contestd.assorters is empty when deserialized

            contestUA.assertions().forEach { assertion ->
                val assorter = assertion.assorter
                println(assorter)

                if (assorter is DhondtAssorter) {
                    println(" setDilutedMean = ${setDilutedMean(assorter, contestd)}")
                    println(" dilutedMean= ${assorter.dilutedMean()}")
                    assertEquals(assorter.dilutedMean(), setDilutedMean(assorter, contestd), doublePrecision)

                    val diff = contestd.difficulty(assorter)
                    println(" diff = $diff")
                    val gmean = diff / contestd.Nc
                    println(" diff/Nc = ${diff / contestd.Nc}")

                    val hmean = assorter.h2(gmean)
                    println(" hmean = ${assorter.h2(gmean)}")
                    assertEquals(assorter.dilutedMean(), hmean, doublePrecision)

                } else if (assorter is BelowThreshold) { // TODO
                    println(" dilutedMean= ${assorter.dilutedMean()}")

                    val diff = contestd.difficulty(assorter)
                    println(" diff = $diff")
                    val gmean = diff / contestd.Nc
                    println(" diff/Nc = ${diff / contestd.Nc}")
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
                    println(" diff/Nc = ${diff / contestd.Nc}")
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
}
