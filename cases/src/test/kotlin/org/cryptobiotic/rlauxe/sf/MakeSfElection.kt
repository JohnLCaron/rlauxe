package org.cryptobiotic.rlauxe.sf

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.boulder.AuditResult
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
import kotlin.collections.forEach
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
            AuditType.ONEAUDIT,
            zipFilename,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = cvrExportCsv,
            contestSampleCutoff = 2500,
            auditSampleCutoff = 5000,
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
        val auditdir = "$testdataDir/cases/sf2024/clca/audit"

        createSfElection(
            auditdir=auditdir,
            AuditType.CLCA,
            zipFilename,
            "ContestManifest.json",
            "CandidateManifest.json",
            cvrExportCsv = cvrExportCsv,
            contestSampleCutoff = 1000,
            auditSampleCutoff = 2000,
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
                contestSampleCutoff = 2000,
                auditSampleCutoff = 5000,
            )

            val publisher = Publisher(auditdir)
            val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
            sortManifestInternal(publisher, config.seed)

            return runAllRoundsAndVerify(auditdir)
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////
    //// generates the CLCA for CaseStudiesRemoveNmax
    @Test
    fun createSfRemoveNclca() {

        val tasks = mutableListOf<ConcurrentTaskG<List<AuditResult>>>()
        repeat(11) { removeN ->
            val auditdir = "$testdataDir/cases/sf2024/clcan/audit$removeN"
            tasks.add(RunRemoveSFtask( removeN,1, auditdir, AuditType.CLCA))
        }

        val results: List<AuditResult> =
            ConcurrentTaskRunnerG<List<AuditResult>>().run(tasks, nthreads = 5).flatten()

        println("CLCA results")
        results.forEach { println(it) }
    }

    //// generates the OA for CaseStudiesRemoveNmax
    @Test
    fun createSfRemoveNoa() {

        val tasks = mutableListOf<ConcurrentTaskG<List<AuditResult>>>()
        repeat(11) { removeN ->
            val auditDir = "$testdataDir/cases/sf2024/oan/audit$removeN"
            tasks.add(RunRemoveSFtask(removeN, 20, auditDir, AuditType.ONEAUDIT))
        }

        val estResults: List<AuditResult> =
            ConcurrentTaskRunnerG<List<AuditResult>>().run(tasks, nthreads = 7).flatten()

        val results = mutableMapOf<Int, MutableList<AuditResult>>()
        println("OneAudit results")
        estResults.forEach { result ->
            println("$result")
            val list = results.getOrPut(result.removeN) { mutableListOf() }
            list.add(result)
        }

        results.forEach { (removeN, resultList) ->
            val nmvrs = resultList.map { it.nmvrs }
            val deciles = makeDeciles(nmvrs)
            val success = resultList.map { it.nsuccess }
            println("$removeN, ${nmvrs.average()}, ${success.average()}, $deciles")
        }
    }

    inner class RunRemoveSFtask(
        val removeN: Int,
        val nruns: Int,
        val auditDir: String,
        val auditType: AuditType,
    ) : ConcurrentTaskG<List<AuditResult>> {

        override fun name() = "removeN= $removeN"

        override fun run(): List<AuditResult> {

            createSfElection(
                auditdir=auditDir,
                auditType = auditType,
                zipFilename,
                "ContestManifest.json",
                "CandidateManifest.json",
                cvrExportCsv = cvrExportCsv,
                poolsHaveOneCardStyle=false,
                contestSampleCutoff = null,
                auditSampleCutoff = null,
                minRecountMargin = 0.0,
                minMargin = 0.0,
                removeMaxContests = removeN,
            )

            val publisher = Publisher(auditDir)
            val results = mutableListOf<AuditResult>()
            repeat(nruns) { run ->
                val config = readAuditConfigUnwrapped(publisher.auditConfigFile())!!
                val nconfig = config.copy(removeMaxContests = removeN, seed = secureRandom.nextLong())
                writeAuditConfigJsonFile(nconfig, publisher.auditConfigFile())

                println("${name()} removeN=$removeN run=$run")
                startFirstRound(auditDir)
                runAllRoundsAndVerify(auditDir, verify=false)

                val contestState = mutableMapOf<Int, TestH0Status>()
                val auditRecord = AuditRecord.readFrom(auditDir)!!
                auditRecord.rounds.forEach { auditRound ->
                    auditRound.contestRounds.forEach { contestRound ->
                        contestState[contestRound.id] = contestRound.status
                    }
                }
                val successes = contestState.values.count { it == TestH0Status.StatRejectNull }
                val result = AuditResult(removeN, (auditRecord as AuditRecord).previousMvrs.size, successes)
                println("${name()} removeN=$removeN result=$result")
                results.add(result)
            }
            return results
        }
    }

}



