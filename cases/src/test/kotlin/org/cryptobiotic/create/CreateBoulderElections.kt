package org.cryptobiotic.create

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.boulder.Boulder23Input
import org.cryptobiotic.rlauxe.boulder.Boulder24Input
import org.cryptobiotic.rlauxe.boulder.Boulder25Input
import org.cryptobiotic.rlauxe.boulder.Boulder26pInput
import org.cryptobiotic.rlauxe.boulder.BoulderVariantEnum
import org.cryptobiotic.rlauxe.boulder.boulderRoundSettings
import org.cryptobiotic.rlauxe.boulder.createBoulderElection
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.persist.AuditRecord
import kotlin.test.Test

class CreateBoulderElections {

    /*
    @Test
    fun createBoulder26p() {
        val topdir = "$cases/boulder/boulder2026p"

        createBoulderElection(
            input= Boulder26pInput(),
            topdir = topdir,
            creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .03),
            roundConfig = boulderRoundSettings(),
        )
    }

    @Test
    fun createBoulder25() {
        val topdir = "$cases/boulder/boulder2025"

        createBoulderElection(
            input= Boulder25Input(),
            topdir = topdir,
            creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .03),
            roundConfig = boulderRoundSettings(),
        )

    }

    // looks like the 2024-Boulder-County-General-Redacted-Cast-Vote-Record.xlsx got saved with incorrect character encoding (?).
    // hand corrected "Claudia De la Cruz / Karina García"


    @Test
    fun createBoulder24phantoms() {
        val topdir = "$cases/boulder/boulder2024/phantoms"

        // redacted ballots are turned into phantoms
        createBoulderElection(
            input= Boulder24Input(),
            topdir = topdir,
            creation = AuditCreationConfig(AuditType.CLCA, riskLimit = .03),
            roundConfig = boulderRoundSettings(),
        )
    }

    @Test
    fun createBoulder24onePool() {
        val topdir = "$cases/boulder/boulder2024/onePool"

        // the ballots for each redacted group are placed in a seperate physical bins, and the ballots
        // are referenced by an index number into the bin
        createBoulderElection(
            input= Boulder24Input(),
            topdir = topdir,
            creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .03),
            roundConfig = boulderRoundSettings(),
            onePool = true
        )
    }

    @Test
    fun createBoulder24oa() {
        val topdir = "$cases/boulder/boulder2024/oa"

        // the ballots for each redacted group are placed in a seperate physical bins, and the ballots
        // are referenced by an index number into the bin
        createBoulderElection(
            input= Boulder24Input(),
            topdir = topdir,
            creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .03),
            roundConfig = boulderRoundSettings(),
            variant = BoulderVariantEnum.OnePool,
        )
    } */

    @Test
    fun createBoulder23() {
        val topdir = "$cases/boulder/boulder2023"

        // redacted ballots are turned into phantoms
        createBoulderElection(
            input= Boulder23Input(),
            topdir = topdir,
            creation = AuditCreationConfig(AuditType.CLCA, riskLimit = .03),
            roundConfig = boulderRoundSettings(),
            variant = BoulderVariantEnum.Sim,
       )
    }

    @Test
    fun testOpenRecord() {
        val topdir = "$cases/boulder/boulder2026p"
        val record = AuditRecord.read(topdir)
    }

    @Test
    fun createBoulder24clca() {
        val toptopdir = "$cases/boulder/boulder2025r"
        val input= Boulder25Input()

        // redacted ballots are simulated
        createBoulderElection(
            input= input,
            topdir = "$toptopdir/sim",
            creation = AuditCreationConfig(AuditType.CLCA, riskLimit = .03),
            roundConfig = boulderRoundSettings(),
            variant = BoulderVariantEnum.Sim,
        )
    }

    @Test
    fun createBoulderVariants() {
        val toptopdir = "$cases/boulder/boulder2023r"
        val input= Boulder23Input()

        // redacted ballots are simulated
        createBoulderElection(
            input= input,
            topdir = "$toptopdir/sim",
            creation = AuditCreationConfig(AuditType.CLCA, riskLimit = .03),
            roundConfig = boulderRoundSettings(),
            variant = BoulderVariantEnum.Sim,
        )

        // redacted ballots are turned into phantoms
        createBoulderElection(
            input= input,
            topdir = "$toptopdir/phantoms",
            creation = AuditCreationConfig(AuditType.CLCA, riskLimit = .03),
            roundConfig = boulderRoundSettings(),
            variant = BoulderVariantEnum.Phantoms,
        )

        // the ballots for each redacted group are placed in one pool
        createBoulderElection(
            input= input,
            topdir = "$toptopdir/onePool",
            creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .03),
            roundConfig = boulderRoundSettings(),
            variant = BoulderVariantEnum.OnePool,
        )

        // the ballots for each redacted group are placed in seperate pools.
        // to use this the redacted ballots would have to be identified by ballot style and identifier.
        createBoulderElection(
            input= input,
            topdir = "$toptopdir/styles",
            creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .03),
            roundConfig = boulderRoundSettings(),
            variant = BoulderVariantEnum.Styles,
        )
    }
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
