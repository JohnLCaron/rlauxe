package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.Sampling
import org.cryptobiotic.rlauxe.audit.SimulationControl

fun makeCorlaElectionClca(toptopdir: String, auditcenter: String) {
    val topdir = "$toptopdir/clca"

    val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.04, ) // 2020 only
    val round = AuditRoundConfig(
        SimulationControl(nsimTrials = 10, estPercentile = listOf(42, 55, 67)),
        ContestSampleControl(minRecountMargin = .005, minMargin = .01, minSize = 10, contestSampleCutoff = 10000,
            auditSampleCutoff = 200000, sampling = Sampling.consistent),
        ClcaConfig(), null)

    createCountyElectionSansCvrs(topdir, Colorado2020General(auditcenter),
        creation, round, name = "Colorado2020", startFirstRound = true)
}

fun makeCorlaElectionUniform(toptopdir: String, auditcenter: String)  {
    val topdir = "$toptopdir/uniform"
    val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.04, ) // TODO LOOK
    val round = AuditRoundConfig(
        SimulationControl(nsimTrials = 10, estPercentile = listOf(42, 55, 67)),
        ContestSampleControl(
            minRecountMargin = .005,
            minMargin = .01,
            minSize = 10,
            contestSampleCutoff = 10000,
            auditSampleCutoff = 200000,
            sampling = Sampling.uniform),
        ClcaConfig(), null)

    createCountyElectionSansCvrs(topdir, Colorado2020General(auditcenter),
        creation, round, name = "Colorado2020uniform", startFirstRound = true)
}

/*
$ java -classpath cases/build/libs/cases-0.10.0.0-uber.jar org.cryptobiotic.rlauxe.cli.CreateCaseData  \
   -case boulder2024 -toptopdir "/home/stormy/datadrive/rla/cases/boulder2024"

$ java -classpath cases/build/libs/cases-0.10.0.0-uber.jar org.cryptobiotic.rlauxe.cli.CreateCaseData  \
   -case boulder2024 -toptopdir "/home/stormy/datadrive/rla/cases/boulder2024" --auditType clca
 */