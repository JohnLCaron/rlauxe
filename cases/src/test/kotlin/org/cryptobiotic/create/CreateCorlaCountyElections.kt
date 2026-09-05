package org.cryptobiotic.create

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.auditcenter.Colorado2024General
import org.cryptobiotic.rlauxe.auditcenter.Colorado2026PMerged
import org.cryptobiotic.rlauxe.auditcenter.Colorado2026PwithCvrs
import org.cryptobiotic.rlauxe.boulder.Boulder24Input
import org.cryptobiotic.rlauxe.boulder.Boulder25Input
import org.cryptobiotic.rlauxe.boulder.Boulder26pInput
import org.cryptobiotic.rlauxe.boulder.boulderRoundSettings
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.corlaCounty.ElectionVariantEnum
import org.cryptobiotic.rlauxe.corlaCounty.LaPlata26pInput
import org.cryptobiotic.rlauxe.corlaCounty.Morgan26pInput
import org.cryptobiotic.rlauxe.corlaCounty.Weld26pInput
import org.cryptobiotic.rlauxe.corlaCounty.createCorlaCountyElection
import kotlin.test.Test

class CreateCorlaCountyElections {

    @Test
    fun createCorlaCountyElections() {
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
            topdir = "$toptopdir/phantoms",
            creation = AuditCreationConfig(AuditType.CLCA, riskLimit = .03),
            roundConfig = boulderRoundSettings(),
            variant = ElectionVariantEnum.Phantoms,
        )
    }

    @Test
    fun createCorlaCountyVariants() {
        val toptopdir = "$cases/corlaCounty/boulder24"
        val input = Boulder24Input()
        val stateInput = Colorado2024General()

        // redacted ballots are simulated
        createCorlaCountyElection(
            input,
            stateInput,
            topdir = "$toptopdir/sim",
            creation = AuditCreationConfig(AuditType.CLCA, riskLimit = .03),
            roundConfig = boulderRoundSettings(),
            variant = ElectionVariantEnum.Sim,
        )

        // redacted ballots are turned into phantoms
        createCorlaCountyElection(
            input,
            stateInput,
            topdir = "$toptopdir/phantoms",
            creation = AuditCreationConfig(AuditType.CLCA, riskLimit = .03),
            roundConfig = boulderRoundSettings(),
            variant = ElectionVariantEnum.Phantoms,
        )

        // the ballots for each redacted group are placed in one pool
        createCorlaCountyElection(
            input,
            stateInput,
            topdir = "$toptopdir/onepool",
            creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .03),
            roundConfig = boulderRoundSettings(),
            variant = ElectionVariantEnum.OnePool,
        )

        // the ballots for each redacted group are placed in seperate pools.
        // to use this the redacted ballots would have to be identified by ballot style and identifier.
        createCorlaCountyElection(
            input,
            stateInput,
            topdir = "$toptopdir/styles",
            creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .03),
            roundConfig = boulderRoundSettings(),
            variant = ElectionVariantEnum.Styles,
        )
    }
}