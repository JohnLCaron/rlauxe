package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.SimulationControl
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.util.ConcurrentTask
import org.cryptobiotic.rlauxe.util.ConcurrentTaskRunner
import org.cryptobiotic.util.runAllRoundsAndVerify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class MakeBoulderElection {

    // looks like the 2024-Boulder-County-General-Redacted-Cast-Vote-Record.xlsx got saved with incorrect character encoding (?).
    // hand corrected "Claudia De la Cruz / Karina García"

    @Test
    fun createBoulder24oa() {
        val auditdir = "$testdataDir/cases/boulder24/oa/audit"

        val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .05, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 22),
            ContestSampleControl(
                minRecountMargin = .005,
                minMargin = 0.0,
                contestSampleCutoff = 2500,
                auditSampleCutoff = 5000
            ),
            ClcaConfig(fuzzMvrs = .001), null
        )

        createBoulderElection(
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            auditdir = auditdir,
            creation,
            round
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
        val auditdir = "$testdataDir/cases/boulder24/clca/audit"

        val creation = AuditCreationConfig(AuditType.CLCA, riskLimit = .05, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 20, estPercentile = listOf(42, 55, 67)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 1000, auditSampleCutoff = 2000),
            ClcaConfig(fuzzMvrs = .001), null
        )

        createBoulderElection(
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            auditdir = auditdir,
            creation,
            round,
        )
    }

    @Test
    fun testRunVerifyBoulder24clca() {
        val auditdir = "$testdataDir/cases/boulder24/clca/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = false)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    /*
    @Test
    fun createBoulder25clca() { // simulate CVRs
        val datadir = "$testdataDir/cases/boulder2025"
        val auditdir = "$testdataDir/cases/boulder2025/clca/audit"
        createBoulderElection(
            "$datadir/Redacted-CVR-PUBLIC.utf8.csv",
            "$datadir/2025C-Boulder-County-Official-Statement-of-Votes.utf8.csv",
            auditdir = auditdir,
            auditType = AuditType.CLCA,
            contestSampleCutoff = 1000,
            auditSampleCutoff = 2000,
        )
    }
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
        assertEquals(
            Pair("Frankenfurter", 11),
            parseIrvContestName("Frankenfurter (Number of positions=11, Number of ranks=4)")
        )
        assertEquals(
            Pair("Frankenfurter", 11),
            parseIrvContestName("Frankenfurter(Number of positions=11, but wait theres more")
        )
        assertEquals(
            Pair("Heather (Bob) Morrisson", 11),
            parseIrvContestName("Heather (Bob) Morrisson (Number of positions=11,)")
        )
        assertEquals(Pair("Number of positions=", 1), parseIrvContestName("Number of positions=    "))
    }

    //// generates the OneAudits for CaseStudiesVarianceScatter
    @Test
    fun createBoulderOAvariance() {
        val topdir = "$testdataDir/cases/boulder24oa2"

        val tasks = mutableListOf<ConcurrentTask<Boolean>>()
        repeat(20) { run ->
            tasks.add(RunOneAuditVarianceTask(run + 1, topdir))
        }

        val estResults = ConcurrentTaskRunner<Boolean>().run(tasks, nthreads = 10) // OOM, reduce threads
        println(estResults)
    }

    class RunOneAuditVarianceTask(
        val runIndex: Int,
        val topdir: String,
    ) : ConcurrentTask<Boolean> {
        val auditdir = "$topdir/audit$runIndex"

        override fun name() = "createBoulderElection $runIndex"

        override fun run(): Boolean {
            val creation =
                AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .05, )
            val round = AuditRoundConfig(
                SimulationControl(nsimTrials = 22),
                ContestSampleControl(
                    minRecountMargin = .005,
                    minMargin = 0.0,
                    contestSampleCutoff = 2500,
                    auditSampleCutoff = 5000
                ),
                ClcaConfig(fuzzMvrs = .001), null
            )

            createBoulderElection(
                "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
                "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
                auditdir = auditdir,
                creation,
                round
            )
            return runAllRoundsAndVerify(auditdir, verify = false)
        }
    }
}
