package org.cryptobiotic.create

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.corlaInput.Colorado2020General
import org.cryptobiotic.rlauxe.boulder.boulderRoundSettings
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.corlaCounty.ElectionVariantEnum
import org.cryptobiotic.rlauxe.corlaCounty.createCorlaStateElection
import kotlin.test.Test

class CorlaStateElections {

    @Test
    fun createOne() {
        val toptopdir = "$cases/corlaState/2020"
        val stateInput = Colorado2020General()

        createCorlaStateElection(
            topdir = "$toptopdir/oneaudit",
            stateInput,
            creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .04),
            roundConfig = boulderRoundSettings(),
            variant = ElectionVariantEnum.OnePool,
        )
    }
}