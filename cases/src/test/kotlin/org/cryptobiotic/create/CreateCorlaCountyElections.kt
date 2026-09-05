package org.cryptobiotic.create

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.auditcenter.Colorado2026PMerged
import org.cryptobiotic.rlauxe.auditcenter.Colorado2026Primary
import org.cryptobiotic.rlauxe.boulder.Boulder23Input
import org.cryptobiotic.rlauxe.boulder.Boulder24Input
import org.cryptobiotic.rlauxe.boulder.Boulder25Input
import org.cryptobiotic.rlauxe.boulder.Boulder26pInput
import org.cryptobiotic.rlauxe.boulder.BoulderVariantEnum
import org.cryptobiotic.rlauxe.boulder.boulderRoundSettings
import org.cryptobiotic.rlauxe.boulder.createBoulderElection
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.corlaCounty.ElectionVariantEnum
import org.cryptobiotic.rlauxe.corlaCounty.Morgan26Input
import org.cryptobiotic.rlauxe.corlaCounty.createCorlaCountyElection
import org.cryptobiotic.rlauxe.persist.AuditRecord
import kotlin.test.Test

class CreateCorlaCountyElections {

    @Test
    fun createCorlaCountyElections() {
        val toptopdir = "$cases/corlaCounty/morgan2026"
        val input = Morgan26Input()
        val stateInput = Colorado2026PMerged()

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

    /* @Test
    fun createBoulderVariants() {
        val toptopdir = "$cases/boulder/boulder2026r"
        val input= Boulder26pInput()

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
    } */
}