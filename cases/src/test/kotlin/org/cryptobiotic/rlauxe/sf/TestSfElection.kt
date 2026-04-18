package org.cryptobiotic.rlauxe.sf

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.betting.ClcaErrorRates
import org.cryptobiotic.rlauxe.betting.ClcaErrorTracker
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFileUnwrapped
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.use

class TestSfElection {

    @Test
    fun testRunVerifySFoa() {
        val auditdir = "$testdataDir/cases/sf2024/oa/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = false)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testSFoaPopulations() {
        val auditdir = "$testdataDir/cases/sf2024/oa/audit"

        val auditRecord = AuditRecord.read(auditdir) as AuditRecord
        val mvrManager = PersistedMvrManager(auditRecord, false)
        val cardManifest = mvrManager.sortedManifest()
        val pools = mvrManager.pools()!!
        val populationNcards = pools.sumOf { (it as CardPoolIF).ncards() }
        println("manifestSumPools = $populationNcards")

        var countCards = 0
        var count49 = 0
        var count49pools = 0
        cardManifest.cards.iterator().use { iter ->
            while (iter.hasNext()) {
                val card = iter.next()
                if (card.hasContest(49)) {
                    count49++
                    if (card.poolId() != null) count49pools++
                }
                countCards++
            }
        }
        println("countCards = $countCards")
        println("pool/cards = ${populationNcards/countCards.toDouble()}")

        println("count cards with contest49 = $count49  and in pools =  $count49pools")
        println("contest49 pool/cards = ${count49pools/count49.toDouble()}")
        println()

        val contests = auditRecord.contests
        val contest49 = contests.find { it.id == 49 }!!
        println("contest49 = $contest49")
        contest49.clcaAssertions.forEach {
            println(" ${it.cassorter}")
            val oaass = it.cassorter as OneAuditClcaAssorter
            println("     ${oaass.oaAssortRates} npools=${oaass.poolAverages.assortAverage.size}")
            assertEquals(count49pools, oaass.oaAssortRates.ncardsInPools)
            val nzavg = oaass.poolAverages.assortAverage.filter { it.value != 0.0 }.count()
            println(" non zero pools = ${nzavg}")
        }
        assertEquals(count49, contest49.Npop)

        val publisher = Publisher(auditdir)
        val sortedMvrs = mvrManager.readCardsAndMerge(publisher.sortedMvrsFile())
        countCards = 0
        count49 = 0
        count49pools = 0
        sortedMvrs.use { iter ->
            while (iter.hasNext()) {
                val card = iter.next()
                if (card.hasContest(49)) {
                    count49++
                    if (card.poolId() != null) count49pools++
                }
                countCards++
            }
        }
        println()
        println("countMvrs = $countCards")
        println("count cards with contest49 = $count49  and in pools =  $count49pools")
        println("contest49 pool/cards = ${count49pools/count49.toDouble()}")

        val pw = PersistedWorkflow(auditRecord, false)

        val sumPools = pools.sumOf { (it as CardPool).ncards() }
        println("sumPools = $sumPools")
    }

    @Test
    fun testSFmaxBet() {
        val auditdir = "$testdataDir/cases/sf2024/oa/audit"
        val publisher = Publisher(auditdir)

        val contests = readContestsJsonFileUnwrapped(publisher.contestsFile())
        val scontests = contests.sortedBy { it.id  }

        scontests.filter { !it.isIrv && it.minDilutedMargin()!! < .07 }.forEach { contest ->
            val minAssertion = contest.minClcaAssertion()!!
            val oaass = minAssertion.cassorter as OneAuditClcaAssorter

            // data class GeneralAdaptiveBetting2(
            //    val Npop: Int, // population size for this contest
            //    val aprioriCounts: ClcaErrorCounts, // apriori counts not counting phantoms, non-null so we have noerror and upper
            //    val nphantoms: Int, // number of phantoms in the population
            //    val maxLoss: Double, // between 0 and 1; this bounds how close lam can get to 2.0; maxBet = maxLoss / mui
            //
            //    val oaAssortRates: OneAuditAssortValueRates? = null, // non-null for OneAudit
            //    val d: Int = 100,  // trunc weight
            //    val debug: Boolean = false,
            val betFn = GeneralAdaptiveBetting(
                Npop = contest.Npop,
                aprioriErrorRates = ClcaErrorRates.empty(oaass.noerror(), 1.0),
                contest.Nphantoms,
                maxLoss = .9,
                oaAssortRates=oaass.oaAssortRates,
                d = 100,
            )
            val bet = betFn.bet(ClcaErrorTracker(oaass.noerror(), 1.0))

            println("${contest.id}-${minAssertion.assorter.winLose()} bet = $bet")
        }
    }
}



