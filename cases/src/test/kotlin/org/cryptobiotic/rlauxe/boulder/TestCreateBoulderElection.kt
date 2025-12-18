package org.cryptobiotic.rlauxe.boulder

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.writeSortedCardsInternalSort
import org.cryptobiotic.rlauxe.cli.RunRliRoundCli
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class TestCreateBoulderElection {

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

    // looks like the 2024-Boulder-County-General-Redacted-Cast-Vote-Record.xlsx got saved with incorrect character encoding (?).
    // hand corrected "Claudia De la Cruz / Karina García"

    /* @Test
    fun createBoulder24oa() {
        createBoulderElectionOA(
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            auditDir = "$testdataDir/cases/boulder24oa/audit",
            clear = true,
        )
    } */

    @Test
    fun createBoulder24oa() {
        val topdir = "$testdataDir/cases/boulder24/oa"
        createBoulderElectionP(
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            topdir = topdir,
            auditType = AuditType.ONEAUDIT,
            poolsHaveOneCardStyle=true,
        )

        val publisher = Publisher("$topdir/audit")
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)
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
        val topdir = "$testdataDir/cases/boulder24/clca"
        createBoulderElectionP(
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            topdir = topdir,
            auditType = AuditType.CLCA,
            poolsHaveOneCardStyle=true,
        )

        val publisher = Publisher("$topdir/audit")
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)
    }

    @Test
    fun testRunVerifyBoulder24clca() {
        val auditdir = "$testdataDir/cases/boulder24/clca/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = false)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun createBoulder25clca() { // simulate CVRs
        val datadir = "$testdataDir/cases/boulder2025"
        val topdir = "$testdataDir/cases/boulder2025/clca"
        createBoulderElectionP(
            "$datadir/Redacted-CVR-PUBLIC.utf8.csv",
            "$datadir/2025C-Boulder-County-Official-Statement-of-Votes.utf8.csv",
            topdir = topdir,
            auditType = AuditType.CLCA,
            poolsHaveOneCardStyle=true,
            )

        val publisher = Publisher("$topdir/audit")
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)
    }

    // @Test
    fun createBoulderOArepeat() {
        val topdir = "$testdataDir/cases/boulder24oa"

        repeat(10) { run ->
            val auditDir = "$topdir/audit$run"

            createBoulderElectionP(
                "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
                "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
                topdir = topdir,
                auditDir = auditDir,
                auditType = AuditType.ONEAUDIT,
                poolsHaveOneCardStyle=true,
                )
        }
        runBoulderOArepeat()
    }

    // @Test
    fun runBoulderOArepeat() {
        val topDir = "$testdataDir/cases/boulder24oa"

        repeat(10) { run ->
            val auditDir = "$topDir/audit$run"
            RunRliRoundCli.main(
                arrayOf(
                    "-in", auditDir,
                )
            )
            RunRliRoundCli.main(
                arrayOf(
                    "-in", auditDir,
                )
            )
        }
    }

    /*

       @Test
    fun createBoulder24() {
        createBoulderElection(
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            auditDir = "$testdataDir/cases/boulder24",
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

    pooled votes only, simulating a "batch level comparison audit"
    @Test
    fun createBoulder24blca() {
        createBoulderElectionOneAudit(
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            auditDir = "$testdataDir/cases/boulder24blca",
            includeCvrs = false,
        )
    }

     */
}