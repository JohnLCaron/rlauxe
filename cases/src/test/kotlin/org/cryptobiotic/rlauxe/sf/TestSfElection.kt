package org.cryptobiotic.rlauxe.sf

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.AuditableCardCsvReader
import org.cryptobiotic.rlauxe.persist.cvrExportCsvFile
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.workflow.MvrManagerClcaSingleRound
import org.cryptobiotic.rlauxe.workflow.OneAuditAssertionAuditor
import kotlin.test.Test

class TestSfElection {
    val sfDir = "/home/stormy/rla/cases/sf2024"
    val zipFilename = "$sfDir/CVR_Export_20241202143051.zip"
    val cvrExportCsv = "$sfDir/$cvrExportCsvFile"
    val topDir = "/home/stormy/rla/cases/sf2024oa"

    @Test
    fun createSFElectionOA() {
        val topDir = "/home/stormy/rla/cases/sf2024/oa"

        createSfElection(
            topDir,
            zipFilename,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = cvrExportCsv,
            isClca = false,
        )
    }

    @Test
    fun createSFElectionClca() {
        val topDir = "/home/stormy/rla/cases/sf2024/clca"

        createSfElection(
            topDir,
            zipFilename,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = cvrExportCsv,
            isClca = true,
        )
    }

    @Test
    fun createSFElectionOAnostyles() {
        val topDir = "/home/stormy/rla/cases/sf2024/oans"

        createSfElectionNoStyles(
            topDir,
            zipFilename,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = cvrExportCsv,
        )
    }

    /*
    fun createSF2024OArepeat() {

        repeat(10) { run ->

            val auditConfig = AuditConfig(
                AuditType.ONEAUDIT, hasStyles = true, sampleLimit = 50000, riskLimit = .05, nsimEst = 10,
                minRecountMargin = 0.0,
                oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true),
                skipContests = listOf(14, 28)
            )

            val auditDir = "$topDir/audit$run"
            clearDirectory(Path.of(auditDir))

            createSfElectionFromCvrExportOA(
                auditDir,
                castVoteRecordZip = zipFilename,
                contestManifestFilename = "ContestManifest.json",
                candidateManifestFile = "CandidateManifest.json",
                cvrExportCsv = cvrExportCsv,
                auditConfigIn = auditConfig,
                show = false,
            )

            val workingDir = "$topDir/sortChunks$run"
            val ballotPoolFile = "$auditDir/$ballotPoolsFile"
            createSortedCards(
                topDir,
                auditDir,
                cvrExportCsv = cvrExportCsv,
                zip = true,
                workingDir = workingDir,
                ballotPoolFile = ballotPoolFile
            ) // write to "$auditDir/sortedCards.csv"
        }

        runSF2024OArepeat()
    }

    fun runSF2024OArepeat() {
        repeat(10) { run ->
            val auditDir = "$topDir/audit$run"
            RunRliRoundCli.main(
                arrayOf(
                    "-in", auditDir,
                    "-test",
                )
            )
            RunRliRoundCli.main(
                arrayOf(
                    "-in", auditDir,
                    "-test",
                )
            )
        }
    } */

    private val show = true

    // @Test
    fun auditSf2024oa() {
        val auditDir = "$topDir/audit"

        val rlauxAudit = PersistedWorkflow(auditDir, true)
        val contestRounds = rlauxAudit.contestsUA().map { ContestRound(it, 1) }

        val mvrManager = MvrManagerClcaSingleRound(AuditableCardCsvReader(Publisher(auditDir).cardsCsvFile()))
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
}



