package org.cryptobiotic.create

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.Sampling
import org.cryptobiotic.rlauxe.audit.SimulationControl
import org.cryptobiotic.rlauxe.corlaInput.Statewide2020General
import org.cryptobiotic.rlauxe.util.Locations.cases
import org.cryptobiotic.rlauxe.corlaCounty.ElectionVariantEnum
import org.cryptobiotic.rlauxe.corla.createCorlaStateElection2
import org.cryptobiotic.rlauxe.corlaInput.Statewide2020Redacted
import kotlin.test.Test

class CorlaStateElections {
    val top2020dir2 = "$cases/corla/2020r"
    val top2020dir = "$cases/corla/2020"
    val top2026pdir = "$cases/corlaState/2026p"

    @Test
    fun create2020r() {
        val stateInput = Statewide2020Redacted()

        createCorlaStateElection2(
            topdir = top2020dir2,
            stateInput=stateInput,
            creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .03),
            roundConfig = corlaRoundSettings(),
            variant = ElectionVariantEnum.OnePool,
            alreadyMadeCounties = false,
        )
    }

    @Test
    fun create2020() {
        val stateInput = Statewide2020General()

        createCorlaStateElection2(
            topdir = top2020dir,
            stateInput=stateInput,
            creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .03),
            roundConfig = corlaRoundSettings(),
            variant = ElectionVariantEnum.OnePool,
            alreadyMadeCounties = true,
        )
    }

    /*
@Test
fun createAll2020Variants() {
    val stateInput = Statewide2020General()

    createCorlaStateVariants(
        toptopdir = top2020dir,
        stateInput=stateInput,
        riskLimit=.04,
    )
}

@Test
fun createAll2026pVariants() {
    val stateInput = Statewide2026PwithCvrs()

    createCorlaStateVariants(
        toptopdir = top2026pdir,
        stateInput=stateInput,
        riskLimit=.03,
    )
}


fun createCorlaStateVariants(toptopdir: String, stateInput: StatewideInputWithCvrs, riskLimit: Double) {
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
} */
}

fun corlaRoundSettings() = AuditRoundConfig(
    SimulationControl(nsimTrials = 10, estPercentile = listOf(50, 80)),
    ContestSampleControl(minRecountMargin = .005, minMargin = 0.005, minSize = 10,
        contestSampleCutoff = 5000, auditSampleCutoff = 200000, sampling = Sampling.consistent),
    ClcaConfig(), null)