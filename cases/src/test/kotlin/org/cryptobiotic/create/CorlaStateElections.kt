package org.cryptobiotic.create

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.Sampling
import org.cryptobiotic.rlauxe.audit.SimulationControl
import org.cryptobiotic.rlauxe.audit.startFirstRound
import org.cryptobiotic.rlauxe.corlaInput.Colorado2020General
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.corlaCounty.ElectionVariantEnum
import org.cryptobiotic.rlauxe.corla.createCorlaStateElection
import org.cryptobiotic.rlauxe.corlaInput.Colorado2026PwithCvrs
import org.cryptobiotic.rlauxe.corlaInput.ColoradoInputWithCvrs
import kotlin.test.Test

class CorlaStateElections {
    val top2020dir = "$cases/corlaState/2020"
    val top2026pdir = "$cases/corlaState/2026p"

    @Test
    fun createOne() {
        val stateInput = Colorado2020General()

        createCorlaStateElection(
            topdir = "$top2020dir/onepool",
            stateInput=stateInput,
            creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .04),
            roundConfig = corlaRoundSettings(),
            variant = ElectionVariantEnum.OnePool,
        )
    }

    @Test
    fun createAll2020Variants() {
        val stateInput = Colorado2020General()

        createCorlaStateVariants(
            toptopdir = top2020dir,
            stateInput=stateInput,
            riskLimit=.04,
        )
    }

    @Test
    fun createAll2026pVariants() {
        val stateInput = Colorado2026PwithCvrs()

        createCorlaStateVariants(
            toptopdir = top2026pdir,
            stateInput=stateInput,
            riskLimit=.03,
        )
    }

    fun createCorlaStateVariants(toptopdir: String, stateInput: ColoradoInputWithCvrs, riskLimit: Double) {
        // redacted ballots are simulated
        createCorlaStateElection(
            topdir = "$toptopdir/sim",
            stateInput,
            creation = AuditCreationConfig(AuditType.CLCA, riskLimit = riskLimit),
            roundConfig = corlaRoundSettings(),
            variant = ElectionVariantEnum.Sim,
        )

        // redacted ballots are turned into phantoms
        createCorlaStateElection(
            topdir = "$toptopdir/phantoms",
            stateInput,
            creation = AuditCreationConfig(AuditType.CLCA, riskLimit = riskLimit),
            roundConfig = corlaRoundSettings(),
            variant = ElectionVariantEnum.Phantoms,
        )

        // the ballots for each redacted group are placed in one pool
        createCorlaStateElection(
            topdir = "$toptopdir/onepool",
            stateInput,
            creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = riskLimit),
            roundConfig = corlaRoundSettings(),
            variant = ElectionVariantEnum.OnePool,
        )

        // the ballots for each redacted group are placed in seperate pools.
        // to use this the redacted ballots would have to be identified by ballot style and identifier.
        /* createCorlaStateElection(
            topdir = "$toptopdir/styles",
            stateInput,
            creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .04),
            roundConfig = corlaRoundSettings(),
            variant = ElectionVariantEnum.Styles,
        ) */
    }

    @Test
    fun compareToPhantoms() {
        compareVariants(top2020dir, "phantoms", listOf("sim", "onepool"))
    }

    @Test
    fun compareToSim() {
        compareVariants(top2020dir, "sim", listOf("phantoms", "onepool"))
    }

    @Test
    fun testStartNewRound() {
        val fromtopdir = "$top2020dir/onepool"
        startFirstRound(fromtopdir)
    }
}

fun corlaRoundSettings() = AuditRoundConfig(
    SimulationControl(nsimTrials = 10, estPercentile = listOf(50, 80)),
    ContestSampleControl(minRecountMargin = .005, minMargin = 0.005, minSize = 10,
        contestSampleCutoff = 5000, auditSampleCutoff = 200000, sampling = Sampling.consistent),
    ClcaConfig(), null)