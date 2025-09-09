package org.cryptobiotic.rlauxe.sf

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.PersistentAudit
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.AuditableCardCsvReader
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.workflow.MvrManagerCardsSingleRound
import org.cryptobiotic.rlauxe.workflow.OneAuditAssertionAuditor
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

// This is to match https://github.com/spertus/UI-TS/blob/main/Code/SF_oneaudit_example.ipynb
// can use the cvsExport file from sf2024. need to redo the sorted cards.
class TestSfElectionOAnoStyles {
    val sfDir = "/home/stormy/rla/cases/sf2024"
    val zipFilename = "$sfDir/CVR_Export_20241202143051.zip"
    val topDir = "/home/stormy/rla/cases/sf2024oaNS"

    // create the audit contests using the cvrExport files
    @Test
    fun createSF2024OANS() {
        val auditDir = "$topDir/audit"
        clearDirectory(Path.of(auditDir))

        createSfElectionFromCsvExportOANS(
            topDir,
            auditDir,
            zipFilename,
            "ContestManifest.json",
            "CandidateManifest.json",
            "$sfDir/$cvrExportCsvFile",
            show = true,
        )
    }

    @Test
    fun countPoolCvrs() {
        val cvrCsv = "$sfDir/cvrExport.csv"
        countPools(cvrCsv)
        //  unpoolCount = 1387622 poolCount = 216286
    }

    @Test
    fun testCardContests() {
        val sortedCards = "$topDir/audit/sortedCards.csv"

        val countingContestsFromSortedCards = mutableMapOf<Int, ContestCount>()
        val scardIter = CvrIteratorAdapter(readCardsCsvIterator(sortedCards))
        while (scardIter.hasNext()) {
            val cvr = scardIter.next()
            cvr.votes.keys.forEach { contestId ->
                val contestCount = countingContestsFromSortedCards.getOrPut(contestId) { ContestCount() }
                contestCount.ncards++
                val isPooled = if (cvr.poolId == null) 0 else 1
                val groupCount = contestCount.counts.getOrPut(isPooled) { 0 }
                contestCount.counts[isPooled] = groupCount + 1
            }
        }
        println(" countingContestsFromSortedCards")
        countingContestsFromSortedCards.toSortedMap().forEach { (key, value) -> println("   $key $value") }
    }

    private val show = true

    @Test
    fun auditSf2024Poa() {
        val auditDir = "$topDir/audit"

        val rlauxAudit = PersistentAudit(auditDir, true)
        val contestRounds = rlauxAudit.contestsUA().map { ContestRound(it, 1) }

        val mvrManager = MvrManagerCardsSingleRound(AuditableCardCsvReader("$auditDir/sortedCards.csv"))
        val cvrPairs = mvrManager.makeCvrPairsForRound() // TODO use iterator, not List
        val runner = OneAuditAssertionAuditor()

        contestRounds.forEach { contestRound ->
            if (show) println("run contest ${contestRound.contestUA.contest}")
            contestRound.assertionRounds.forEach { assertionRound ->
                val cassorter = (assertionRound.assertion as ClcaAssertion).cassorter
                val sampler =
                    ClcaWithoutReplacement(contestRound.contestUA.id, true, cvrPairs, cassorter, allowReset = false)
                if (show) println("  run assertion ${assertionRound.assertion} reported Margin= ${mean2margin(cassorter.assorter.reportedMargin())}")

                val result: TestH0Result = runner.run(
                    rlauxAudit.auditConfig(),
                    contestRound.contestUA.contest,
                    assertionRound,
                    sampler,
                    1,
                )
                // assertEquals(TestH0Status.StatRejectNull, result.status)
                if (show) println("    sampleCount = ${result.sampleCount} poolCount = ${sampler.poolCount()} maxIdx=${sampler.maxSampleIndexUsed()} status = ${result.status}\n")
            }
        }
    }

    @Test
    fun auditSf2024oa18() {
        val auditDir = "$topDir/audit"

        val rlauxAudit = PersistentAudit(auditDir, true)
        val contest18 = rlauxAudit.contestsUA().find { it.contest.id == 18 }!!
        val minAssertion = contest18.minClcaAssertion()!!
        val assertionRound = AssertionRound(minAssertion, 1, null)

        val mvrManager = MvrManagerCardsSingleRound(AuditableCardCsvReader("$auditDir/sortedCards.csv"))
        val sampler =
            ClcaNoErrorIterator(
                contest18.id,
                contest18.Nc,
                CvrIteratorAdapter(mvrManager.sortedCards()),
                minAssertion.cassorter)

        if (show) println("  run assertion ${assertionRound.assertion} reported Margin= ${mean2margin(minAssertion.cassorter.assorter.reportedMargin())}")

        val runner = OneAuditAssertionAuditor()
        val result: TestH0Result = runner.run(
            rlauxAudit.auditConfig(),
            contest18.contest,
            assertionRound,
            sampler,
            1,
        )
        // assertEquals(TestH0Status.StatRejectNull, result.status)
        if (show) println("    sampleCount = ${result.sampleCount} maxIdx=${sampler.maxSampleIndexUsed()} status = ${result.status}\n")
    }
}


