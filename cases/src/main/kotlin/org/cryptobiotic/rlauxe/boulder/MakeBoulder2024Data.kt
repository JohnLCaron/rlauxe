package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.SimulationControl

fun makeBoulderElectionOA(toptopdir: String) {
    val topdir = "$toptopdir"

    val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .03, )
    val round = AuditRoundConfig(
        SimulationControl(nsimTrials = 22),
        ContestSampleControl(
            minRecountMargin = .005,
            minMargin = 0.0,
            contestSampleCutoff = 5000,
            auditSampleCutoff = 10000
        ),
        ClcaConfig(), null
    )

    createBoulderElection(
        "2024",
        "/resources/data/cases/boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
        "/resources/data/cases/boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
        topdir = topdir,
        creation,
        round,
        distributeOvervotes = listOf(0, 63)
    )
}

fun makeBoulderElectionClca(toptopdir: String) {
    val topdir = "$toptopdir"

    val creation = AuditCreationConfig(AuditType.CLCA, riskLimit = .03, )
    val round = AuditRoundConfig(
        SimulationControl(nsimTrials = 20, estPercentile = listOf(42, 55, 67)),
        ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 1000, auditSampleCutoff = 2000),
        ClcaConfig(fuzzMvrs = .001), null // TOFO is fuzz implemented ??
    )

    createBoulderElection(
        "2024",
        "/resources/data/cases/boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
        "/resources/data/cases/boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
        topdir = topdir,
        creation,
        round,
        distributeOvervotes = listOf(0, 63)
    )
}

/*
$ java -classpath cases/build/libs/cases-0.10.0.0-uber.jar org.cryptobiotic.rlauxe.cli.CreateCaseData  \
   -case boulder2024 -toptopdir "/home/stormy/datadrive/rla/cases/boulder2024"

$ java -classpath cases/build/libs/cases-0.10.0.0-uber.jar org.cryptobiotic.rlauxe.cli.CreateCaseData  \
   -case boulder2024 -toptopdir "/home/stormy/datadrive/rla/cases/boulder2024" --auditType clca
 */