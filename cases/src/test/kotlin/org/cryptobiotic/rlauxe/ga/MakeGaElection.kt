package org.cryptobiotic.rlauxe.ga

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.SimulationControl
import org.cryptobiotic.rlauxe.cases
import kotlin.test.Test

class MakeGaElection {

    @Test
    fun testCreateGaElection() {
        val inputdir = "/home/stormy/datadrive/github/nealmcb/rla-review-arlo/2026-05-19-primary/extracted"
        val topdir = "$cases/ga/ga2026"

        val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .05,)
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 22),
            ContestSampleControl.NONE,
            ClcaConfig(), null
        )

        //     electionName: String,
        //    version: String,
        //    topdir: String,
        //    creation: AuditCreationConfig,
        //    roundConfig: AuditRoundConfig,
        //    startFirstRound: Boolean = true,
        createGaElection(
            "Ga2026Primary",
            inputdir,
            topdir = topdir,
            creation,
            round,
        )
    }
}