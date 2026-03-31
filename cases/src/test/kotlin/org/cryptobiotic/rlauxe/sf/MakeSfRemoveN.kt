package org.cryptobiotic.rlauxe.sf

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.boulder.AuditResult
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvFile
import org.cryptobiotic.rlauxe.util.ConcurrentTask
import org.cryptobiotic.rlauxe.util.ConcurrentTaskRunner
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.estimateOld.makeDeciles
import org.cryptobiotic.rlauxe.audit.runAllRoundsAndVerify
import kotlin.collections.List
import kotlin.collections.forEach

class MakeSfRemoveN {
    val sfDir = "$testdataDir/cases/sf2024"
    val zipFilename = "$sfDir/CVR_Export_20241202143051.zip"
    val cvrExportCsv = "$sfDir/$cvrExportCsvFile"

    //// generates the OneAudits for CaseStudiesVarianceScatter
    // @Test
    fun createSFOAvariance() {
        val topdir = "$testdataDir/cases/sf2024oa"

        val tasks = mutableListOf<ConcurrentTask<Boolean>>()
        repeat(20) { run ->
            tasks.add( RunOneAuditVarianceTask(run+1, topdir, AuditType.ONEAUDIT) )
        }

        val estResults = ConcurrentTaskRunner<Boolean>().run(tasks, nthreads=10) // OOM, reduce threads
        println(estResults)
    }

    inner class RunOneAuditVarianceTask(
        val runIndex: Int,
        val topdir: String,
        val auditType: AuditType,
    ) : ConcurrentTask<Boolean> {
        val auditdir = "$topdir/audit$runIndex"

        override fun name() = "createSFElection $runIndex"

        override fun run(): Boolean {
            val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit=.05, )
            val round = AuditRoundConfig(
                SimulationControl(nsimTrials = 22),
                ContestSampleControl(minRecountMargin = .005, minMargin=0.0, contestSampleCutoff = 2500, auditSampleCutoff = 5000),
                ClcaConfig(fuzzMvrs=.001), null)

            createSfElection(
                auditdir=auditdir,
                zipFilename,
                "ContestManifest.json",
                "CandidateManifest.json",
                cvrExportCsv = cvrExportCsv,
                creation,
                round,
            )

            // TODO
            val publisher = Publisher(auditdir)
            // val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
            // sortManifestInternal(publisher, config.seed)

            return runAllRoundsAndVerify(auditdir)
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////
    //// generates the CLCA for CaseStudiesRemoveNmax
    // @Test
    fun createSfRemoveNclca() {

        val tasks = mutableListOf<ConcurrentTask<List<AuditResult>>>()
        repeat(11) { removeN ->
            val auditdir = "$testdataDir/cases/sf2024/clcan/audit$removeN"
            tasks.add(RunRemoveSFtask( removeN,1, auditdir, AuditType.CLCA))
        }

        val results: List<AuditResult> =
            ConcurrentTaskRunner<List<AuditResult>>().run(tasks, nthreads = 5).flatten()

        println("CLCA results")
        results.forEach { println(it) }
    }

    //// generates the OA for CaseStudiesRemoveNmax
    // @Test
    fun createSfRemoveNoa() {

        val tasks = mutableListOf<ConcurrentTask<List<AuditResult>>>()
        repeat(11) { removeN ->
            val auditDir = "$testdataDir/cases/sf2024/oan/audit$removeN"
            tasks.add(RunRemoveSFtask(removeN, 20, auditDir, AuditType.ONEAUDIT))
        }

        val estResults: List<AuditResult> =
            ConcurrentTaskRunner<List<AuditResult>>().run(tasks, nthreads = 7).flatten()

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
    ) : ConcurrentTask<List<AuditResult>> {

        override fun name() = "removeN= $removeN"

        override fun run(): List<AuditResult> {
            val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit=.05, )
            val round = AuditRoundConfig(SimulationControl(nsimTrials = 22), ContestSampleControl.NONE, ClcaConfig(), null)

            createSfElection(
                auditdir=auditDir,
                zipFilename,
                "ContestManifest.json",
                "CandidateManifest.json",
                cvrExportCsv = cvrExportCsv,
                creation,
                round,
                // removeMaxContests = removeN, TODO
            )

            val publisher = Publisher(auditDir)
            val results = mutableListOf<AuditResult>()
            repeat(nruns) { run ->
                // TODO
                //val config = readAuditConfigUnwrapped(publisher.auditConfigFile())!!
                //val nconfig = config.copy(removeMaxContests = removeN, seed = secureRandom.nextLong())
                //writeAuditConfigJsonFile(nconfig, publisher.auditConfigFile())

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
                val result = AuditResult(removeN, (auditRecord as AuditRecord).nmvrs, successes)
                println("${name()} removeN=$removeN result=$result")
                results.add(result)
            }
            return results
        }
    }

}



