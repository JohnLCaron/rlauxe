package org.cryptobiotic.create

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.SimulationControl
import org.cryptobiotic.rlauxe.boulder.BoulderStatementOfVotes
import org.cryptobiotic.rlauxe.boulder.createBoulderElection
import org.cryptobiotic.rlauxe.boulder.createBoulderElectionWithSovo
import org.cryptobiotic.rlauxe.boulder.parseContestNameAndVoteFor
import org.cryptobiotic.rlauxe.boulder.parseIrvContestName
import org.cryptobiotic.rlauxe.boulder.readBoulderStatementOfVotes
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class MakeBoulderElection {

    // looks like the 2024-Boulder-County-General-Redacted-Cast-Vote-Record.xlsx got saved with incorrect character encoding (?).
    // hand corrected "Claudia De la Cruz / Karina García"

    @Test
    fun createBoulder24oa() {
        val topdir = "$cases/boulder/boulder2024/oa"

        val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 22),
            ContestSampleControl(
                minRecountMargin = .005,
                minMargin = 0.0,
                contestSampleCutoff = 5000,
                auditSampleCutoff = 20000
            ),
            ClcaConfig(), null
        )

        createBoulderElection(
            "2024",
            "/resources/data/cases/boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "/resources/data/cases/boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            topdir = topdir,
            creation,
            round,
            distributeOvervotes = listOf(0, 63),
            hasStyle = true,
            )
    }

    @Test
    fun createBoulder24clca() { // simulate CVRs
        val topdir = "$cases/boulder/boulder2024/clca"

        val creation = AuditCreationConfig(AuditType.CLCA, riskLimit = .03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 20, estPercentile = listOf(42, 55, 67)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 5000, auditSampleCutoff = 20000),
            ClcaConfig(fuzzMvrs = .001), null // TOFO is fuzz implemented ??
        )

        createBoulderElection(
            "2024",
            "/resources/data/cases/boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "/resources/data/cases/boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            topdir = topdir,
            creation,
            round,
            distributeOvervotes = listOf(0, 63),
            hasStyle = true,
        )
    }

    // @Test
    fun createBoulder25oa() { // simulate CVRs
        val topdir = "$testdataDir/cases/boulder2025/oa"

        val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 22),
            ContestSampleControl(
                minRecountMargin = .005,
                minMargin = 0.0,
                contestSampleCutoff = 5000,
                auditSampleCutoff = 10000
            ),
            ClcaConfig(), null
        )

        createBoulderElection(
            "2025",
            "src/test/data/Boulder2025/Redacted-CVR-PUBLIC.csv",
            "src/test/data/Boulder2025/2025C-Boulder-County-Official-Statement-of-Votes.csv",
            topdir = topdir,
            creation,
            round,
            distributeOvervotes = listOf(),
            startFirstRound = false
        )
    }

    // @Test
    fun createBoulder23oa() {
        val sovo = readBoulderStatementOfVotes(
            "src/test/data/Boulder2023/2023C-Boulder-County-Official-Statement-of-Votes.csv", "Boulder2023"
        )
        val sovoRcv = readBoulderStatementOfVotes(
            "src/test/data/Boulder2023/2023C-Boulder-County-Official-Statement-of-Votes-RCV.csv", "Boulder2023Rcv"
        )
        val combined = BoulderStatementOfVotes.combine(listOf(sovoRcv, sovo))

        println(combined.show())

        val topdir = "$testdataDir/cases/boulder23/oa"

        val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 22),
            ContestSampleControl(
                minRecountMargin = .005,
                minMargin = 0.0,
                contestSampleCutoff = 5000,
                auditSampleCutoff = 10000
            ),
            ClcaConfig(), null
        )

        // fun createBoulderElectionWithSovo(
        //    cvrExportFile: String,
        //    sovo: BoulderStatementOfVotes,
        //    topdir: String,
        //    creation: AuditCreationConfig,
        //    round: AuditRoundConfig,
        //    mvrSource: MvrSource = MvrSource.testPrivateMvrs,
        //)
        createBoulderElectionWithSovo(
            "2023",
            cvrExportFile = "src/test/data/Boulder2023/Redacted-2023Coordinated-CVR.csv",
            sovo = combined,
            topdir = topdir,
            creation,
            round,
            distributeOvervotes = listOf(2),
        )

       /*
        createBoulderElectionWithSov(
            "src/test/data/Boulder2023/Redacted-2023Coordinated-CVR.csv",
            "$testdataDir/cases/boulder23",
            combined,
        ) */
    }

    /*

 @Test
 fun createBoulder24recount() {
     createBoulderElection(
         "src/test/data/Boulder2024/2024-Boulder-County-General-Recount-Redacted-Cast-Vote-Record.zip",
         "src/test/data/Boulder2024/2024G-Boulder-County-Amended-Statement-of-Votes.csv",
         topdir = "$testdataDir/cases/boulder24recount",
         minRecountMargin = 0.0,
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
}
