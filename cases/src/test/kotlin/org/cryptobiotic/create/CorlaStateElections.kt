package org.cryptobiotic.create

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.audit.runAllRoundsAndVerify
import org.cryptobiotic.rlauxe.audit.startFirstRound
import org.cryptobiotic.rlauxe.auditcenter.Colorado2020General
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
import org.cryptobiotic.rlauxe.corlaCounty.createCorlaStateElection
import org.cryptobiotic.rlauxe.persist.AuditRecord
import kotlin.test.Test

class CorlaStateElections {

    @Test
    fun createOne() {
        val toptopdir = "$cases/corlaState/2020"
        val stateInput = Colorado2020General()

        createCorlaStateElection(
            topdir = "$toptopdir/sim",
            stateInput,
            creation = AuditCreationConfig(AuditType.CLCA, riskLimit = .04),
            roundConfig = boulderRoundSettings(),
            variant = ElectionVariantEnum.Sim,
        )
    }
}