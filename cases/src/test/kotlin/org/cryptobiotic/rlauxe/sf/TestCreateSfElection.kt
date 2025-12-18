package org.cryptobiotic.rlauxe.sf

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.audit.runRound
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvFile
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import kotlin.test.Test
import kotlin.test.fail

class TestCreateSfElection {
    val sfDir = "$testdataDir/cases/sf2024"
    val zipFilename = "$sfDir/CVR_Export_20241202143051.zip"
    val cvrExportCsv = "$sfDir/$cvrExportCsvFile"

    @Test
    fun createSFElectionOA() {
        val topdir = "$testdataDir/cases/sf2024/oa"

        createSfElectionP(
            topdir,
            zipFilename,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = cvrExportCsv,
            auditType = AuditType.ONEAUDIT,
            poolsHaveOneCardStyle=false,
        )

        val publisher = Publisher("$topdir/audit")
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)
    }

    @Test
    fun testRunVerifySFoa() {
        val auditdir = "$testdataDir/cases/sf2024/oa/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = false)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun createSFElectionClca() {
        val topdir = "$testdataDir/cases/sf2024/clca"

        createSfElectionP(
            topdir,
            zipFilename,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = cvrExportCsv,
            auditType = AuditType.CLCA,
            poolsHaveOneCardStyle=false,
        )

        val publisher = Publisher("$topdir/audit")
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)
    }

    /* @Test
    fun createSFElectionOneAuditNostyles() {
        val topdir = "$testdataDir/cases/sf2024/oans"

        createSfElectionPoolStyle(
            topdir,
            zipFilename,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = cvrExportCsv,
            isPolling = false
        )

        val publisher = Publisher("$topdir/audit")
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)
    }

   // @Test
    fun createSFElectionPollingNostyles() {
        val topdir = "$testdataDir/cases/sf2024/polling"

        createSfElectionPoolStyle(
            topdir,
            zipFilename,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = cvrExportCsv,
            isPolling = true
        )

        val publisher = Publisher("$topdir/audit")
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)
    }

    @Test
    fun runSFElectionPollingNostyles() {
        val topdir = "$testdataDir/cases/sf2024/polling"
        runRound(inputDir = "$topdir/audit")
    }
    */
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

}



