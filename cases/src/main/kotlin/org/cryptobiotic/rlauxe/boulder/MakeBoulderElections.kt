package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.Sampling
import org.cryptobiotic.rlauxe.audit.SimulationControl


fun boulderRoundSettings() = AuditRoundConfig(
    SimulationControl(nsimTrials = 10, estPercentile = listOf(50, 80)),
    ContestSampleControl(minRecountMargin = .005, minMargin = 0.005, minSize = 10,
        contestSampleCutoff = 5000, auditSampleCutoff = 200000, sampling = Sampling.consistent),
    ClcaConfig(), null)

fun makeBoulderElectionOA(toptopdir: String) {
    val topdir = "$toptopdir"

    createBoulderElection(
        input=Boulder24Input(),
        topdir = topdir,
        creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .03),
        roundConfig = boulderRoundSettings(),
        variant = BoulderVariantEnum.Styles
    )
}

fun makeBoulderElectionClca(toptopdir: String) {
    val topdir = "$toptopdir"

    createBoulderElection(
        input=Boulder24Input(),
        topdir = topdir,
        creation = AuditCreationConfig(AuditType.CLCA, riskLimit = .03),
        roundConfig = boulderRoundSettings(),
        variant = BoulderVariantEnum.Phantoms
    )
}

/*
$ java -classpath cases/build/libs/cases-0.10.0.0-uber.jar org.cryptobiotic.rlauxe.cli.CreateCaseData  \
   -case boulder2024 -toptopdir "/home/stormy/datadrive/rla/cases/boulder2024"

$ java -classpath cases/build/libs/cases-0.10.0.0-uber.jar org.cryptobiotic.rlauxe.cli.CreateCaseData  \
   -case boulder2024 -toptopdir "/home/stormy/datadrive/rla/cases/boulder2024" --auditType clca
 */