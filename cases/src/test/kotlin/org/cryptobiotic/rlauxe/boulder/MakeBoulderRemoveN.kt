package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.SimulationControl
import org.cryptobiotic.rlauxe.audit.startFirstRound
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskRunnerG
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.estimateOld.makeDeciles
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode
import org.cryptobiotic.util.runAllRoundsAndVerify

class MakeBoulderRemoveN {
    ///////////////////////////////////////////////
    //// generates the CLCA for CaseStudiesRemoveNmax
    //@Test
    fun createBoulderRemoveNclca() {
        val results = mutableListOf<AuditResult>()

        repeat(11) { removeN ->
            val auditdir = "$testdataDir/cases/boulder24/clcan/audit$removeN"
            val task = RunRemoveBoulderTask(removeN, 1, auditdir, AuditType.CLCA)
            val estResults: List<AuditResult> = task.run()
            results.addAll(estResults)
        }
        println("CLCA results")
        results.forEach { println(it) }
    }

    //@Test
    fun createBoulderRemoveNoa() {

        val tasks = mutableListOf<ConcurrentTaskG<List<AuditResult>>>()
        repeat(11) { removeN ->
            val auditDir = "$testdataDir/cases/boulder24/oan/audit$removeN"
            tasks.add(RunRemoveBoulderTask(removeN, 1, auditDir, AuditType.ONEAUDIT))
        }

        val estResults: List<AuditResult> =
            ConcurrentTaskRunnerG<List<AuditResult>>().run(tasks, nthreads = 5).flatten()

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
}

class RunRemoveBoulderTask(
    val removeN: Int,
    val nruns: Int,
    val auditDir: String,
    val auditType: AuditType,
) : ConcurrentTaskG<List<AuditResult>> {

    override fun name() = "removeN=$removeN"

    override fun run(): List<AuditResult> {
        val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit=.05, PersistedWorkflowMode.testPrivateMvrs)
        val round = AuditRoundConfig(SimulationControl(nsimEst = 22), ContestSampleControl.NONE, ClcaConfig(), null)

        createBoulderElection(
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            auditdir = auditDir,
            creation,
            round,
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
            runAllRoundsAndVerify(auditDir, verify = false)

            val contestState = mutableMapOf<Int, TestH0Status>()
            val auditRecord = AuditRecord.readFrom(auditDir)!!
            auditRecord.rounds.forEach { auditRound ->
                auditRound.contestRounds.forEach { contestRound ->
                    contestState[contestRound.id] = contestRound.status
                }
            }
            val successes = contestState.values.count { it == TestH0Status.StatRejectNull }
            val result = AuditResult(removeN, (auditRecord as AuditRecord).previousMvrs.size, successes)
            println("${name()} removeN=$removeN resilt=$result")
            results.add(result)
        }
        return results
    }
}

data class AuditResult(val removeN: Int, val nmvrs: Int, val nsuccess: Int)
