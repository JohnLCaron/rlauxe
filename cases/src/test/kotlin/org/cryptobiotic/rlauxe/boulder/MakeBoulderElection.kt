package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.startFirstRound
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskRunnerG
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigUnwrapped
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.util.makeDeciles
import org.cryptobiotic.rlauxe.util.secureRandom
import org.cryptobiotic.util.runAllRoundsAndVerify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class MakeBoulderElection {

    // looks like the 2024-Boulder-County-General-Redacted-Cast-Vote-Record.xlsx got saved with incorrect character encoding (?).
    // hand corrected "Claudia De la Cruz / Karina García"

    @Test
    fun createBoulder24oa() {
        val auditdir = "$testdataDir/cases/boulder24/oa/audit2"
        createBoulderElection(
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            auditdir = auditdir,
            auditType = AuditType.ONEAUDIT,
            minMargin = .011
        )
    }

    @Test
    fun testRunVerifyBoulder24oa() {
        val auditdir = "$testdataDir/cases/boulder24/oa/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = false)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun createBoulder24clca() { // simulate CVRs
        val auditdir = "$testdataDir/cases/boulder24/clca/audit2"
        createBoulderElection(
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            auditdir = auditdir,
            auditType = AuditType.CLCA,
        )
    }

    @Test
    fun testRunVerifyBoulder24clca() {
        val auditdir = "$testdataDir/cases/boulder24/clca/audit2"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = false)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun createBoulder25clca() { // simulate CVRs
        val datadir = "$testdataDir/cases/boulder2025"
        val auditdir = "$testdataDir/cases/boulder2025/clca/audit"
        createBoulderElection(
            "$datadir/Redacted-CVR-PUBLIC.utf8.csv",
            "$datadir/2025C-Boulder-County-Official-Statement-of-Votes.utf8.csv",
            auditdir = auditdir,
            auditType = AuditType.CLCA,
        )
    }

    /*

 @Test
 fun createBoulder24recount() {
     createBoulderElection(
         "src/test/data/Boulder2024/2024-Boulder-County-General-Recount-Redacted-Cast-Vote-Record.csv",
         "src/test/data/Boulder2024/2024G-Boulder-County-Amended-Statement-of-Votes.csv",
         auditDir = "$testdataDir/cases/boulder24recount",
         minRecountMargin = 0.0,
     )
 }

 @Test
 fun createBoulder23() {
     val sovo = readBoulderStatementOfVotes(
         "src/test/data/Boulder2023/2023C-Boulder-County-Official-Statement-of-Votes.csv", "Boulder2023")
     val sovoRcv = readBoulderStatementOfVotes(
         "src/test/data/Boulder2023/2023C-Boulder-County-Official-Statement-of-Votes-RCV.csv", "Boulder2023Rcv")
     val combined = BoulderStatementOfVotes.combine(listOf(sovoRcv, sovo))

     createBoulderElectionWithSov(
         "src/test/data/Boulder2023/Redacted-2023Coordinated-CVR.csv",
         "$testdataDir/cases/boulder23",
         combined,
     )
 }

 @Test
 fun createBoulder23recount() {
     val sovo = readBoulderStatementOfVotes(
         "src/test/data/Boulder2023/2023C-Boulder-County-Official-Statement-of-Votes-Recount.csv", "Boulder2023")
     createBoulderElectionWithSov(
         "src/test/data/Boulder2023/Redacted-2023Coordinated-CVR.csv",
         "$testdataDir/cases/boulder23recount",
         sovo,
         minRecountMargin = 0.0,
     )
 }

  */

    @Test
    fun testParseContestName() {
        assertEquals(Pair("Frankenfurter", 11), parseContestName("Frankenfurter (Vote For=11)"))
        assertEquals(Pair("Frankenfurter", 11), parseContestName("Frankenfurter(Vote For=11)"))
        assertEquals(Pair("Frankenfurter", 11), parseContestName("Frankenfurter(Vote For=11"))
        assertEquals(Pair("Frankenfurter", 11), parseContestName("Frankenfurter(Vote For=11) but wait theres more"))
        assertEquals(Pair("Heather (Bob) Morrisson", 11), parseContestName("Heather (Bob) Morrisson (Vote For=11)"))
        assertEquals(Pair("Stereoscopic", 1), parseContestName("Stereoscopic    "))
    }

    @Test
    fun testParseIrvContestName() {
        assertEquals(Pair("Frankenfurter (Vote For=11)", 1), parseIrvContestName("Frankenfurter (Vote For=11)"))
        assertEquals(Pair("Frankenfurter", 11), parseIrvContestName("Frankenfurter (Number of positions=11, Number of ranks=4)"))
        assertEquals(Pair("Frankenfurter", 11), parseIrvContestName("Frankenfurter(Number of positions=11, but wait theres more"))
        assertEquals(Pair("Heather (Bob) Morrisson", 11), parseIrvContestName("Heather (Bob) Morrisson (Number of positions=11,)"))
        assertEquals(Pair("Number of positions=", 1), parseIrvContestName("Number of positions=    "))
    }

    //// generates the OneAudits for CaseStudiesVarianceScatter
    @Test
    fun createBoulderOAvariance() {
        val topdir = "$testdataDir/cases/boulder24oa2"

        val tasks = mutableListOf<ConcurrentTaskG<Boolean>>()
        repeat(20) { run ->
            tasks.add(RunOneAuditVarianceTask(run + 1, topdir))
        }

        val estResults = ConcurrentTaskRunnerG<Boolean>().run(tasks, nthreads = 10) // OOM, reduce threads
        println(estResults)
    }

    class RunOneAuditVarianceTask(
        val runIndex: Int,
        val topdir: String,
    ) : ConcurrentTaskG<Boolean> {
        val auditdir = "$topdir/audit$runIndex"

        override fun name() = "createBoulderElection $runIndex"

        override fun run(): Boolean {
            createBoulderElection(
                "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
                "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
                auditdir = auditdir,
                auditType = AuditType.ONEAUDIT,
                // minMargin = .011
            )
            return runAllRoundsAndVerify(auditdir, verify = false)
        }
    }

    ///////////////////////////////////////////////
    //// generates the CLCA for CaseStudiesRemoveNmax
    @Test
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

    @Test
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

        createBoulderElection(
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            auditdir = auditDir,
            auditType = auditType,
            removeCutoffContests = false,
            minRecountMargin = 0.0,
            minMargin = 0.0,
            maxSamplePct = 0.0,
        )

        val publisher = Publisher(auditDir)
        val results = mutableListOf<AuditResult>()
        repeat(nruns) { run ->
            val config = readAuditConfigUnwrapped(publisher.auditConfigFile())!!
            val nconfig = config.copy(removeMaxContests = removeN, seed = secureRandom.nextLong())
            writeAuditConfigJsonFile(nconfig, publisher.auditConfigFile())
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
