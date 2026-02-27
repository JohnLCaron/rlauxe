package org.cryptobiotic.rlauxe.sf

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvFile
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskRunnerG
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigUnwrapped
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.util.makeDeciles
import org.cryptobiotic.rlauxe.util.secureRandom
import org.cryptobiotic.util.runAllRoundsAndVerify
import kotlin.collections.List
import kotlin.test.Test
import kotlin.test.fail

class MakeSfElection {
    val sfDir = "$testdataDir/cases/sf2024"
    val zipFilename = "$sfDir/CVR_Export_20241202143051.zip"
    val cvrExportCsv = "$sfDir/$cvrExportCsvFile"

    @Test
    fun makeSFElectionOA() {
        val auditdir = "$testdataDir/cases/sf2024/oa/audit2"

        createSfElection(
            auditdir=auditdir,
            AuditType.ONEAUDIT,
            zipFilename,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = cvrExportCsv,
            poolsHaveOneCardStyle=false,
        )

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
        val auditdir = "$testdataDir/cases/sf2024/clca/audit2"

        createSfElection(
            auditdir=auditdir,
            AuditType.CLCA,
            zipFilename,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = cvrExportCsv,
        )
    }

    //// generates the OneAudits for CaseStudiesVarianceScatter
    @Test
    fun createSFOAvariance() {
        val topdir = "$testdataDir/cases/sf2024oa"

        val tasks = mutableListOf<ConcurrentTaskG<Boolean>>()
        repeat(20) { run ->
            tasks.add( RunOneAuditVarianceTask(run+1, topdir, AuditType.ONEAUDIT) )
        }

        val estResults = ConcurrentTaskRunnerG<Boolean>().run(tasks, nthreads=10) // OOM, reduce threads
        println(estResults)
    }

    inner class RunOneAuditVarianceTask(
        val runIndex: Int,
        val topdir: String,
        val auditType: AuditType,
    ) : ConcurrentTaskG<Boolean> {
        val auditdir = "$topdir/audit$runIndex"

        override fun name() = "createSFElection $runIndex"

        override fun run(): Boolean {

            createSfElection(
                auditdir=auditdir,
                auditType=auditType,
                zipFilename,
                "ContestManifest.json",
                "CandidateManifest.json",
                cvrExportCsv = cvrExportCsv,
                poolsHaveOneCardStyle=false,
            )

            val publisher = Publisher(auditdir)
            val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
            writeSortedCardsInternalSort(publisher, config.seed)

            return runAllRoundsAndVerify(auditdir)
        }
    }

    //// generates the CLCA for different n of "remove top n min-margin contests"
    @Test
    fun createSFremoveNclca() {
        val topdir = "$testdataDir/cases/sf2024/clca/audit2"

        val tasks = mutableListOf<ConcurrentTaskG<Pair<Int, Int>>>()
        repeat(11) { run ->
            tasks.add( RunAuditTask(run+1, topdir, AuditType.CLCA) )
        }

        val estResults: List<Pair<Int,Int>> = ConcurrentTaskRunnerG<Pair<Int, Int>>().run(tasks, nthreads=1) // OOM, reduce threads
        println("CLCA results")
        estResults.forEach{ println(it) }
    }

    @Test
    fun createSFremoveNoa() {
        val topdir = "$testdataDir/cases/sf2024/oa/audit2"

        val results = mutableMapOf<Int, MutableList<Int>>()
        repeat(10) {
            val tasks = mutableListOf<ConcurrentTaskG<Pair<Int, Int>>>()
            repeat(11) { removeN ->
                tasks.add(RunAuditTask(removeN + 1, topdir, AuditType.ONEAUDIT))
            }
            val estResults: List<Pair<Int,Int>> = ConcurrentTaskRunnerG<Pair<Int, Int>>().run(tasks, nthreads = 1) // OOM, reduce threads
            println("OneAudit results")
            estResults.forEach { (removeN, nmvrs) ->
                println("$removeN, $nmvrs")
                val list = results.getOrPut(removeN) { mutableListOf() }
                list.add(nmvrs)
            }
        }

        results.forEach { removeN, nmvrs ->
            val deciles = makeDeciles(nmvrs)
            println("$removeN, ${nmvrs.average()}, $deciles")
        }
    }

    class RunAuditTask(
        val removeN: Int,
        val auditDir: String,
        val auditType: AuditType,
        ) : ConcurrentTaskG<Pair<Int, Int>> {

        override fun name() = "removeN $removeN"

        override fun run(): Pair<Int, Int> {
            /* first time
            createSfElection(
                auditdir=auditdir,
                zipFilename,
                "ContestManifest.json",
                "CandidateManifest.json",
                cvrExportCsv = cvrExportCsv,
                auditType = AuditType.CLCA,
                poolsHaveOneCardStyle=false,
                mvrFuzz = 0.0,
                removeMinContests = 2,
            ) */

            val publisher = Publisher(auditDir)
            val config = readAuditConfigUnwrapped(publisher.auditConfigFile())!!
            val nconfig = config.copy(removeMinContests=removeN, seed = secureRandom.nextLong())
            writeAuditConfigJsonFile(nconfig, publisher.auditConfigFile())

            startFirstRound(auditDir)
            runAllRoundsAndVerify(auditDir)

            val auditRecord = AuditRecord.readFrom(auditDir)!!
            return Pair(removeN, (auditRecord as AuditRecord).previousMvrs.size)
        }
    }

}



