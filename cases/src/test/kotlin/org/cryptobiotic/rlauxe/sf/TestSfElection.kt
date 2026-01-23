package org.cryptobiotic.rlauxe.sf

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.persist.csv.*

import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.betting.ClcaErrorTracker
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvFile
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFileUnwrapped
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.workflow.readCardManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.use

class TestSfElection {
    val sfDir = "$testdataDir/cases/sf2024"
    val zipFilename = "$sfDir/CVR_Export_20241202143051.zip"
    val cvrExportCsv = "$sfDir/$cvrExportCsvFile"

    @Test
    fun testRunVerifySFoa() {
        val auditdir = "$testdataDir/cases/sf2024/oa/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = false)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testSFoaPools() {
        val auditdir = "$testdataDir/cases/sf2024/oa/audit"

        val publisher = Publisher(auditdir)
        val cardManifest = readCardManifest(publisher)
        val manifestSumPools = cardManifest.populations.sumOf { it.ncards() }
        println("manifestSumPools = $manifestSumPools")

        var countCards = 0
        var count49 = 0
        var count49pools = 0
        cardManifest.cards.iterator().use { iter ->
            while (iter.hasNext()) {
                val card = iter.next()
                if (card.hasContest(49)) {
                    count49++
                    if (card.poolId != null) count49pools++
                }
                countCards++
            }
        }
        println("countCards = $countCards")
        println("pool/cards = ${manifestSumPools/countCards.toDouble()}")

        println("count cards with contest49 = $count49  and in pools =  $count49pools")
        println("contest49 pool/cards = ${count49pools/count49.toDouble()}")
        println()

        val contests = readContestsJsonFileUnwrapped(publisher.contestsFile())
        val contest49 = contests.find { it.id == 49 }!!
        println("contest49 = $contest49")
        contest49.clcaAssertions.forEach {
            println(" ${it.cassorter}")
            val oaass = it.cassorter as ClcaAssorterOneAudit
            println("     ${oaass.oaAssortRates} npools=${oaass.poolAverages.assortAverage.size}")
            assertEquals(count49pools, oaass.oaAssortRates.totalInPools)
            val nzavg = oaass.poolAverages.assortAverage.filter { it.value != 0.0 }.count()
            println(" non zero pools = ${nzavg}")
        }
        assertEquals(count49, contest49.Npop)


        val sortedMvrs: CloseableIterator<AuditableCard> = readCardsCsvIterator(publisher.privateMvrsFile())
        countCards = 0
        count49 = 0
        count49pools = 0
        sortedMvrs.use { iter ->
            while (iter.hasNext()) {
                val card = iter.next()
                if (card.hasContest(49)) {
                    count49++
                    if (card.poolId != null) count49pools++
                }
                countCards++
            }
        }
        println()
        println("countMvrs = $countCards")
        println("count cards with contest49 = $count49  and in pools =  $count49pools")
        println("contest49 pool/cards = ${count49pools/count49.toDouble()}")

        /*

        val auditRecord = AuditRecord.readFrom(auditdir)
            ?: throw RuntimeException("directory '$auditdir' does not contain an audit record")
        val pw = PersistedWorkflow(auditRecord, false)
        val man: MvrManager = pw.mvrManager()
        val populations = man.populations()!!

        val sumPools = populations.sumOf { it.ncards() }
        println("sumPools = $sumPools") */
    }

    @Test
    fun testSFmaxBet() {
        val auditdir = "$testdataDir/cases/sf2024/oa/audit"
        val publisher = Publisher(auditdir)

        val contests = readContestsJsonFileUnwrapped(publisher.contestsFile())
        val scontests = contests.sortedBy { it.id  }

        scontests.filter { !it.isIrv && it.minDilutedMargin()!! < .07 }.forEach { contest ->
            val minAssertion = contest.minClcaAssertion()!!
            val oaass = minAssertion.cassorter as ClcaAssorterOneAudit
            val betFn = GeneralAdaptiveBetting(
                contest.Npop,
                ClcaErrorCounts.empty(oaass.noerror(), 1.0),
                contest.Nphantoms,
                oaass.oaAssortRates,
                maxRisk = 0.9,
                debug=true,
            )
            val bet = betFn.bet(ClcaErrorTracker(oaass.noerror(), 1.0))

            println("${contest.id}-${minAssertion.assorter.winLose()} bet = $bet")
        }
    }
}



