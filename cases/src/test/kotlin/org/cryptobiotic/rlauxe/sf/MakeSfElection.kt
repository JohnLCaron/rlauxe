package org.cryptobiotic.rlauxe.sf

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.boulder.createBoulderElection
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvFile
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskRunnerG
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import org.cryptobiotic.util.runAllRoundsAndVerify
import kotlin.test.Test
import kotlin.test.fail

class MakeSfElection {
    val sfDir = "$testdataDir/cases/sf2024"
    val zipFilename = "$sfDir/CVR_Export_20241202143051.zip"
    val cvrExportCsv = "$sfDir/$cvrExportCsvFile"

    @Test
    fun makeSFElectionOA() {
        val auditdir = "$testdataDir/cases/sf2024/oa/audit"

        createSfElection(
            auditdir=auditdir,
            zipFilename,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = cvrExportCsv,
            auditType = AuditType.ONEAUDIT,
            poolsHaveOneCardStyle=false,
        )

        val publisher = Publisher(auditdir)
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
    fun makeSFElectionClca() {
        val auditdir = "$testdataDir/cases/sf2024/clca/audit"

        createSfElection(
            auditdir=auditdir,
            zipFilename,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = cvrExportCsv,
            auditType = AuditType.CLCA,
            poolsHaveOneCardStyle=false,
            mvrFuzz = 0.0,
        )

        val publisher = Publisher(auditdir)
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)
    }

    @Test
    fun createSFOArepeat() {
        val topdir = "$testdataDir/cases/sf2024oa"

        val tasks = mutableListOf<ConcurrentTaskG<Boolean>>()
        repeat(20) { run ->
            tasks.add( RunAuditTask(run+1, topdir) )
        }

        val estResults = ConcurrentTaskRunnerG<Boolean>().run(tasks, nthreads=10) // OOM, reduce threads
        println(estResults)
    }

    inner class RunAuditTask(
        val runIndex: Int,
        val topdir: String,
    ) : ConcurrentTaskG<Boolean> {
        val auditdir = "$topdir/audit$runIndex"

        override fun name() = "createSFElection $runIndex"

        override fun run(): Boolean {

            createSfElection(
                auditdir=auditdir,
                zipFilename,
                "ContestManifest.json",
                "CandidateManifest.json",
                cvrExportCsv = cvrExportCsv,
                auditType = AuditType.ONEAUDIT,
                poolsHaveOneCardStyle=false,
            )

            val publisher = Publisher(auditdir)
            val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
            writeSortedCardsInternalSort(publisher, config.seed)

            return runAllRoundsAndVerify(auditdir)
        }
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
                oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = false),
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



