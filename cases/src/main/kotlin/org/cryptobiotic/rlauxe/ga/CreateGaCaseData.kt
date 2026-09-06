package org.cryptobiotic.rlauxe.ga

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.PollingConfig
import org.cryptobiotic.rlauxe.audit.PollingMode
import org.cryptobiotic.rlauxe.audit.Sampling
import org.cryptobiotic.rlauxe.audit.SimulationControl

fun makeGa2026(topdir: String, inputdir: String, auditTypeS: String?) {
    val auditType = if (auditTypeS == "poll") AuditType.POLLING else AuditType.ONEAUDIT
    val creation = AuditCreationConfig(auditType, riskLimit = .05,)
    val round = AuditRoundConfig(
        SimulationControl(nsimTrials = 100), // TODO 10 or 100 ?
        ContestSampleControl(minRecountMargin = .005, minSize = 10, contestSampleCutoff = null,
            auditSampleCutoff = null, sampling = Sampling.consistent),
        ClcaConfig(), PollingConfig()
    )

    createGaElection(
        "Ga2026Primary from manifests and candidate_totals",
        inputdir,
        topdir = topdir,
        auditType,
        creation,
        round,
        pollingMode = if (auditType.isPolling()) PollingMode.withPools else null,
    )
}

/*
$ java -classpath cases/build/libs/rlauxe-cases-0.10.2.0-uber.jar org.cryptobiotic.rlauxe.cli.CreateCaseData \
    -case ga26p --output "/home/datadrive/rla/cases/ga/ga2026Primary" \
    --input "/home/stormy/datadrive/github/nealmcb/rla-review-arlo/2026-05-19-primary/extracted"
 */