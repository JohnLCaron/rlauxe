package org.cryptobiotic.rlauxe.sf

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvFile
import org.cryptobiotic.rlauxe.util.ConcurrentTask
import org.cryptobiotic.rlauxe.util.ConcurrentTaskRunner
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.audit.runAllRoundsAndVerify
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting
import org.cryptobiotic.rlauxe.util.Welford
import kotlin.collections.List
import kotlin.collections.forEach
import kotlin.test.Test

class MakeSfVarianceData {
    val sfDir = "$testdataDir/cases/sf2024"
    val castVoteRecordZip = "$sfDir/CVR_Export_20241202143051.zip"
    val cvrExportCsv = "$sfDir/$cvrExportCsvFile"

    //// generates the OneAudits for CaseStudiesVarianceScatter for regular OA
    @Test
    fun createSFOAvariance() {
        val topdir = "$testdataDir/cases/sf2024oa"

        val tasks = mutableListOf<ConcurrentTask<Boolean>>()
        repeat(1) { run ->
            tasks.add( RunOneAuditVarianceTask(run+1, topdir, nsimTrials = 1) )
        }

        val estResults = ConcurrentTaskRunner<Boolean>().run(tasks, nthreads=1) // OOM, reduce threads
        println(estResults)
        //$prefix avoid      other  .0001  .001   .01 .05
        // final      0    581,840   7901    42  2556   0 // nrun = 1, nsim = 1,  time= 7min 25sec, nthreads=1
        // final      0 11,610,773  61453     0  5739   0 // nrun = 1, nsim = 20, time= 9min 39sec, nthreads=1 (but the sims are threaded)
        // final      0 11,469,893  61703   149 13911   0 // nrun = 2, nsim = 10, time= 17min 49sec, nthreads=1
        // final      0 17,379,813  81536     0 9808    0 // nrun = 2, nsim = 10, time= 33min 20sec, nthreads=1

        // switch to comparing to prevBet, not maxBet
        //$prefix    avoid      .0001   .001   .01   .05    > .05
        //    final 592387     448956  92769 36000 11576     3086  // nrun = 1, nsim = 1,  time= 5min 53 sec, nthreads=1; <.001 91%
        // decimate 603552     502552  72771 18183  7813     2233; 7 min 19 sec = 439 secs
        // decimate 554191      13451  19250 13318  7361     1955; 2 min 12 sec = 132 secs
        GeneralAdaptiveBetting.showCounts("final")
    }

    inner class RunOneAuditVarianceTask(
        val runIndex: Int,
        val topdir: String,
        val nsimTrials: Int,
    ) : ConcurrentTask<Boolean> {
        val auditdir = "$topdir/audit$runIndex"

        override fun name() = "createSFElection $runIndex"

        override fun run(): Boolean {
            val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit=.05, )
            val round = AuditRoundConfig(
                SimulationControl(nsimTrials = nsimTrials),
                ContestSampleControl(minRecountMargin = .005, minMargin=0.0, contestSampleCutoff = 2500, auditSampleCutoff = 5000),
                ClcaConfig(), null)

            createSfElection(
                auditdir=auditdir,
                castVoteRecordZip,
                "ContestManifest.json",
                "CandidateManifest.json",
                cvrExportCsv = cvrExportCsv,
                creation,
                round,
            )
            GeneralAdaptiveBetting.showCounts("after create")

            return runAllRoundsAndVerify(auditdir)
        }
    }

    //// generates the OneAudits for CaseStudiesVarianceScatter for Precinct-Style OA
    @Test
    fun createSFOAvarianceSP() {
        val topdir = "$testdataDir/cases/sf2024oasp"

        val tasks = mutableListOf<ConcurrentTask<Boolean>>()
        repeat(20) { run ->
            tasks.add( RunOneAuditVarianceTaskSP(run+1, topdir, AuditType.ONEAUDIT) )
        }

        val estResults = ConcurrentTaskRunner<Boolean>().run(tasks, nthreads=10) // OOM, reduce threads
        println(estResults)
    }

    inner class RunOneAuditVarianceTaskSP(
        val runIndex: Int,
        val topdir: String,
        val auditType: AuditType,
    ) : ConcurrentTask<Boolean> {
        val auditdir = "$topdir/audit$runIndex"
        val contestManifestFilename = "ContestManifest.json"
        val candidateManifestFile = "CandidateManifest.json"
        val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit=.05,)
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 22),
            ContestSampleControl(minRecountMargin = .005, minMargin=0.0, contestSampleCutoff = 2500, auditSampleCutoff = 5000),
            ClcaConfig(fuzzMvrs=.001), null)

        val mvrSource: MvrSource = MvrSource.testPrivateMvrs

        override fun name() = "createSFElection $runIndex"

        override fun run(): Boolean {
            val election = CreatePrecinctAndStyle(
                castVoteRecordZip,
                contestManifestFilename,
                candidateManifestFile,
                cvrExportCsv,
                auditType = creation.auditType,
                poolsHaveOneCardStyle=true,
                mvrSource = mvrSource
            )

            createElectionRecord(election, auditDir = auditdir)

            val config = Config(election.electionInfo(), creation, round)
            createAuditRecord(config, election, auditDir = auditdir)

            startFirstRound(auditdir)
            return runAllRoundsAndVerify(auditdir)
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////
    //// generates the CLCA for CaseStudiesRemoveNmax
    @Test
    fun createSfRemoveNclca() {

        val tasks = mutableListOf<ConcurrentTask<List<AuditResult2>>>()
        repeat(8) { removeN ->
            val auditdir = "$testdataDir/cases/sf2024/clcan/audit$removeN"
            tasks.add(RunRemoveSFtask( removeN+2,20, auditdir, AuditType.CLCA, nsimTrials=20))
        }

        val results: List<AuditResult2> =
            ConcurrentTaskRunner<List<AuditResult2>>().run(tasks, nthreads = 10).flatten()

        println("CLCA results")
        results.forEach { println(it) }
    }

    //// generates the OA for CaseStudiesRemoveNmax
    @Test
    fun createSfRemoveNoa() {

        val tasks = mutableListOf<ConcurrentTask<List<AuditResult2>>>()
        repeat(8) { removeN ->
            val auditDir = "$testdataDir/cases/sf2024/oan/audit$removeN"
            tasks.add(RunRemoveSFtask(removeN+2, 20, auditDir, AuditType.ONEAUDIT, nsimTrials=20))
        }

        val estResults: List<AuditResult2> =
            ConcurrentTaskRunner<List<AuditResult2>>().run(tasks, nthreads = 11).flatten()

        val resultsByN = mutableMapOf<Int, MutableList<AuditResult2>>()
        println("OneAudit SessionScan results")
        estResults.forEach { result ->
            val list = resultsByN.getOrPut(result.removeN) { mutableListOf() }
            list.add(result)
        }

        resultsByN.toSortedMap().forEach { (removeN, resultList) ->
            val welford = Welford()
            val welfordNrounds = Welford()
            resultList.map { welford.update(it.nmvrs.toDouble()) }
            resultList.map { welfordNrounds.update(it.rounds.toDouble()) }
            println("$removeN, ${welford.count}, ${welford.mean()}, ${welford.stddev()}, ${welfordNrounds.mean()} , ${welfordNrounds.stddev()}")
        }
    }

    inner class RunRemoveSFtask(
        val removeN: Int,
        val nruns: Int,
        val auditDir: String,
        val auditType: AuditType,
        val nsimTrials: Int,
    ) : ConcurrentTask<List<AuditResult2>> {

        override fun name() = "removeN= $removeN"

        override fun run(): List<AuditResult2> {
            // run 20 times with different seed, write on top of each run
            val results = mutableListOf<AuditResult2>()
            repeat(nruns) { run ->
                val creation = AuditCreationConfig(auditType, riskLimit = .05) // seperate seed

                val sampleControl =
                    ContestSampleControl.NONE.copy(other = mapOf(ContestSampleControl.removeMaxContests to removeN.toString()))
                val round = AuditRoundConfig(
                    SimulationControl(nsimTrials = nsimTrials),
                    sampling = sampleControl,
                    ClcaConfig(),
                    null
                )

                createSfElection(
                    auditdir = auditDir,
                    castVoteRecordZip,
                    "ContestManifest.json",
                    "CandidateManifest.json",
                    cvrExportCsv = cvrExportCsv,
                    creation,
                    round,
                )

                println("${name()} removeN=$removeN run=$run")
                runAllRoundsAndVerify(auditDir, verify = false)

                val contestState = mutableMapOf<Int, TestH0Status>()
                val auditRecord = AuditRecord.readFrom(auditDir)!!
                auditRecord.rounds.forEach { auditRound ->
                    auditRound.contestRounds.forEach { contestRound ->
                        contestState[contestRound.id] = contestRound.status
                    }
                }
                val result = AuditResult2(removeN, (auditRecord as AuditRecord).nmvrs, auditRecord.rounds.size)
                println("${name()} removeN=$removeN result=$result")
                results.add(result)
            }
            return results
        }
    }

    //// generates the OA for CaseStudiesRemoveNmax
    @Test
    fun createSfRemoveNsp() {
        val tasks = mutableListOf<ConcurrentTask<List<AuditResult2>>>()
        repeat(8) { removeN ->
            val auditDir = "$testdataDir/cases/sf2024/oaspn/audit$removeN"
            tasks.add( RunRemoveSFtaskSP(removeN+2, 20, auditDir, nsimTrials=20))
        }

        val estResults: List<AuditResult2> =
            ConcurrentTaskRunner<List<AuditResult2>>().run(tasks, nthreads = 11).flatten()

        val resultsByN = mutableMapOf<Int, MutableList<AuditResult2>>()
        println("OneAudit PrecinctStyle results")
        estResults.forEach { result ->
            val list = resultsByN.getOrPut(result.removeN) { mutableListOf() }
            list.add(result)
            println(result)
        }

        resultsByN.toSortedMap().forEach { (removeN, resultList) ->
            val welford = Welford()
            val welfordNrounds = Welford()
            resultList.map { welford.update(it.nmvrs.toDouble()) }
            resultList.map { welfordNrounds.update(it.rounds.toDouble()) }
            println("$removeN, ${welford.count}, ${welford.mean()}, ${welford.stddev()}, ${welfordNrounds.mean()} , ${welfordNrounds.stddev()}")
        }
    }

    inner class RunRemoveSFtaskSP(
        val removeN: Int,
        val nruns: Int,
        val auditDir: String,
        val nsimTrials: Int,
    ) : ConcurrentTask<List<AuditResult2>> {

        val contestManifestFilename = "ContestManifest.json"
        val candidateManifestFile = "CandidateManifest.json"

        override fun name() = "RunRemoveSFtaskSP $auditDir"

        override fun run(): List<AuditResult2> {

            val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit=.05,) // different seed
            val sampleControl = ContestSampleControl.NONE.copy(other = mapOf(ContestSampleControl.removeMaxContests to removeN.toString()))
            val round = AuditRoundConfig( SimulationControl(nsimTrials = nsimTrials), sampling=sampleControl, ClcaConfig(), null)
            val mvrSource: MvrSource = MvrSource.testPrivateMvrs

            // run 20 times with different seed, write on top of each run
            val results = mutableListOf<AuditResult2>()
            repeat(nruns) { run ->
                val election = CreatePrecinctAndStyle(
                    castVoteRecordZip,
                    contestManifestFilename,
                    candidateManifestFile,
                    cvrExportCsv,
                    auditType = creation.auditType,
                    poolsHaveOneCardStyle=true,
                    mvrSource = mvrSource
                )
                createElectionRecord(election, auditDir = auditDir)

                val config = Config(election.electionInfo(), creation, round)
                createAuditRecord(config, election, auditDir = auditDir)

                val startResult = startFirstRound(auditDir)
                if (startResult.isErr) {
                    println( startResult.toString() )
                    return emptyList()
                }

                println("${name()} removeN=$removeN run=$run")
                runAllRoundsAndVerify(auditDir, verify=false)

                val contestState = mutableMapOf<Int, TestH0Status>()
                val auditRecord = AuditRecord.readFrom(auditDir)!!
                auditRecord.rounds.forEach { auditRound ->
                    auditRound.contestRounds.forEach { contestRound ->
                        contestState[contestRound.id] = contestRound.status
                    }
                }
                val result = AuditResult2(removeN, (auditRecord as AuditRecord).nmvrs, auditRecord.rounds.size)
                println("${name()} removeN=$removeN result=$result")
                results.add(result)
            }

            return results // one for each run
        }
    }
}

data class AuditResult2(val removeN: Int, val nmvrs: Int, val rounds: Int)




