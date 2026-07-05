package org.cryptobiotic.rlauxe.ga

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.Sampling
import org.cryptobiotic.rlauxe.audit.SimulationControl

fun makeGa2026(topdir: String, inputdir: String) {
    val inputdir = "/home/stormy/datadrive/github/nealmcb/rla-review-arlo/2026-05-19-primary/extracted"

    val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .05,)
    val round = AuditRoundConfig(
        SimulationControl(nsimTrials = 100),
        ContestSampleControl(minRecountMargin = .005, minSize = 10, contestSampleCutoff = 10000,
            auditSampleCutoff = 200000, sampling = Sampling.consistent),
        ClcaConfig(),
    )
    createGaElection(
        "Ga2026Primary from manifests and candidate_totals",
        inputdir,
        topdir = topdir,
        creation,
        round,
    )
}

/*
$ java -classpath cases/build/libs/rlauxe-cases-0.10.2.0-uber.jar org.cryptobiotic.rlauxe.cli.CreateCaseData \
    -case ga26p --output "/home/datadrive/rla/cases/ga/ga2026Primary" \
    --input "/home/stormy/datadrive/github/nealmcb/rla-review-arlo/2026-05-19-primary/extracted"
 */