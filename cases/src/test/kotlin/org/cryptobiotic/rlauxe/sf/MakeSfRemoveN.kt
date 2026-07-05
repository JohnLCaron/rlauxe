package org.cryptobiotic.rlauxe.sf

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvFile
import org.cryptobiotic.rlauxe.util.ConcurrentTask
import org.cryptobiotic.rlauxe.util.ConcurrentTaskRunner
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.audit.runAllRoundsAndVerify
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.util.Welford
import kotlin.collections.List
import kotlin.collections.forEach
import kotlin.test.Test

class MakeSfRemoveN {
    val sfDir = "$cases/sf/sf2024"
    val castVoteRecordZip = "$sfDir/CVR_Export_20241202143051.zip"
    val cvrExportCsv = "$sfDir/$cvrExportCsvFile"
    //// generates the CLCA for CaseStudiesRemoveNmax
    @Test
    fun createSfRemoveNclca() {

        val tasks = mutableListOf<ConcurrentTask<List<AuditResult2>>>()
        repeat(8) { removeN ->
            val topdirn = "$testdataDir/cases/sf2024/clcan$removeN"
            tasks.add(RunRemoveSFtask( removeN+2,20, topdirn, AuditType.CLCA, nsimTrials=20))
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
            val topdirn = "$testdataDir/cases/sf2024/oan$removeN"
            tasks.add(RunRemoveSFtask(removeN+2, 20, topdirn, AuditType.ONEAUDIT, nsimTrials=20))
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
        val topdir: String,
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
                    topdir = topdir,
                    castVoteRecordZip,
                    "ContestManifest.json",
                    "CandidateManifest.json",
                    cvrExportCsv = cvrExportCsv,
                    creation,
                    round,
                )

                println("${name()} removeN=$removeN run=$run")
                runAllRoundsAndVerify(topdir, verify = false)

                val contestState = mutableMapOf<Int, TestH0Status>()
                val auditRecord = AuditRecord.read(topdir)!!
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
            val topdirn = "$testdataDir/cases/sf2024/oaspn$removeN"
            tasks.add( RunRemoveSFtaskSP(removeN+2, 20, topdirn, nsimTrials=20))
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
        val topdir: String,
        val nsimTrials: Int,
    ) : ConcurrentTask<List<AuditResult2>> {

        val contestManifestFilename = "ContestManifest.json"
        val candidateManifestFile = "CandidateManifest.json"

        override fun name() = "RunRemoveSFtaskSP $topdir"

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
                createElectionRecord(election, topdir = topdir)

                val config = Config(election.electionInfo(), creation, round)
                createAuditRecord(config, election, topdir = topdir)

                val startResult = startFirstRound(topdir)
                if (startResult.isErr) {
                    println( startResult.toString() )
                    return emptyList()
                }

                println("${name()} removeN=$removeN run=$run")
                runAllRoundsAndVerify(topdir, verify=false)

                val contestState = mutableMapOf<Int, TestH0Status>()
                val auditRecord = AuditRecord.read(topdir)!!
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




