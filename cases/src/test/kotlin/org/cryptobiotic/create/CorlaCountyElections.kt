package org.cryptobiotic.create

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.audit.runAllRoundsAndVerify
import org.cryptobiotic.rlauxe.audit.startFirstRound
import org.cryptobiotic.rlauxe.auditcenter.Colorado2024General
import org.cryptobiotic.rlauxe.auditcenter.Colorado2026PMerged
import org.cryptobiotic.rlauxe.auditcenter.Colorado2026PwithCvrs
import org.cryptobiotic.rlauxe.auditcenter.ColoradoInput
import org.cryptobiotic.rlauxe.boulder.Boulder23Input
import org.cryptobiotic.rlauxe.boulder.Boulder24Input
import org.cryptobiotic.rlauxe.boulder.Boulder25Input
import org.cryptobiotic.rlauxe.boulder.Boulder26pInput
import org.cryptobiotic.rlauxe.boulder.boulderRoundSettings
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.corlaCounty.CorlaCountyInput
import org.cryptobiotic.rlauxe.corlaCounty.ElectionVariantEnum
import org.cryptobiotic.rlauxe.corlaCounty.LaPlata26pInput
import org.cryptobiotic.rlauxe.corlaCounty.Morgan26pInput
import org.cryptobiotic.rlauxe.corlaCounty.Weld26pInput
import org.cryptobiotic.rlauxe.corlaCounty.createCorlaCountyElection
import org.cryptobiotic.rlauxe.persist.AuditRecord
import kotlin.test.Test

class CorlaCountyElections {

    @Test
    fun createOne() {
        val toptopdir = "$cases/corlaCounty/boulder26p"
        val input = Boulder26pInput()
        val stateInput = Colorado2026PwithCvrs()

        // fun createCorlaCountyElection(
        //    countyInput: CorlaCountyInput,
        //    stateInput: ColoradoInput,
        //    topdir: String,
        //    creation: AuditCreationConfig,
        //    roundConfig: AuditRoundConfig,
        //    mvrSource: MvrSource = MvrSource.testPrivateMvrs,
        //    hasStyle: Boolean = true, // TODO wtf ??
        //    variant: ElectionVariantEnum,
        createCorlaCountyElection(
            input,
            stateInput,
            topdir = "$toptopdir/styles",
            creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .03),
            roundConfig = boulderRoundSettings(),
            variant = ElectionVariantEnum.Styles,
        )
    }

    @Test
    fun createBoulder26p() {
        createCorlaCountyVariants("$cases/corlaCounty/boulder26p",
            Boulder26pInput(),
        Colorado2026PwithCvrs()
        )
    }

    @Test
    fun createBoulder24() {
        createCorlaCountyVariants("$cases/corlaCounty/boulder24",
            Boulder24Input(),
            Colorado2024General()
        )
    }

    @Test
    fun createLaPlata26p() {
        createCorlaCountyVariants("$cases/corlaCounty/laplata26p",
            LaPlata26pInput(),
            Colorado2026PwithCvrs()
        )
    }

    @Test
    fun createMorgan26p() {
        createCorlaCountyVariants("$cases/corlaCounty/morgan26p",
            Morgan26pInput(),
            Colorado2026PwithCvrs()
        )
    }

    @Test
    fun createWeld26p() {
        createCorlaCountyVariants("$cases/corlaCounty/weld26p",
            Weld26pInput(),
            Colorado2026PwithCvrs()
        )
    }

    fun createCorlaCountyVariants(toptopdir: String, countyInput: CorlaCountyInput, stateInput: ColoradoInput) {
        // redacted ballots are simulated
        createCorlaCountyElection(
            countyInput,
            stateInput,
            topdir = "$toptopdir/sim",
            creation = AuditCreationConfig(AuditType.CLCA, riskLimit = .03),
            roundConfig = boulderRoundSettings(),
            variant = ElectionVariantEnum.Sim,
        )

        // redacted ballots are turned into phantoms
        createCorlaCountyElection(
            countyInput,
            stateInput,
            topdir = "$toptopdir/phantoms",
            creation = AuditCreationConfig(AuditType.CLCA, riskLimit = .03),
            roundConfig = boulderRoundSettings(),
            variant = ElectionVariantEnum.Phantoms,
        )

        // the ballots for each redacted group are placed in one pool
        createCorlaCountyElection(
            countyInput,
            stateInput,
            topdir = "$toptopdir/onepool",
            creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .03),
            roundConfig = boulderRoundSettings(),
            variant = ElectionVariantEnum.OnePool,
        )

        // the ballots for each redacted group are placed in seperate pools.
        // to use this the redacted ballots would have to be identified by ballot style and identifier.
        createCorlaCountyElection(
            countyInput,
            stateInput,
            topdir = "$toptopdir/styles",
            creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .03),
            roundConfig = boulderRoundSettings(),
            variant = ElectionVariantEnum.Styles,
        )
    }

    @Test
    fun compareToPhantoms() {
        val toptopdir = "$cases/corlaCounty/boulder24"
        createBoulder24()
        compareVariants(toptopdir, "phantoms", listOf("sim", "onepool", "styles"))
    }

    @Test
    fun compareToSim() {
        val toptopdir = "$cases/corlaCounty/boulder24"
        createBoulder24()
        compareVariants(toptopdir, "sim", listOf("phantoms", "onepool", "styles"))
    }

    // we want to get the audited contests from one variant, and run that with the other variants
    fun compareVariants(toptopdir: String, from: String, compareOthers: List<String>) {
        val fromtopdir = "$toptopdir/$from"
        startFirstRound(fromtopdir)
        runAllRoundsAndVerify(fromtopdir, 5, false)

        val fromAuditRecord = AuditRecord.read(fromtopdir)!! as AuditRecord
        val (successfulContests, nmvrs) = fromAuditRecord.contestRounds(successOnly = true)
        println(">>>>>>>>>>>>>>>>>>>>>>>> $from has ${successfulContests.size} contests, nmvrs = $nmvrs")
        val onlyContests = successfulContests.keys.toList()
        compareOthers.forEach { other ->
            val othertopdir = "$toptopdir/$other"

            startFirstRound(othertopdir, onlyContests = onlyContests)
            runAllRoundsAndVerify(othertopdir, 5, false)

            val fromAuditRecord = AuditRecord.read(othertopdir)!! as AuditRecord
            val (otherContests, nmvrs)  = fromAuditRecord.contestRounds(successOnly = true)
            println(">>>>>>>>>>>>>>>>>>>>>>> $other has ${otherContests.size} contests, nmvrs=$nmvrs")
        }

    }
}