package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.MvrSource
import org.cryptobiotic.rlauxe.audit.Sampling
import org.cryptobiotic.rlauxe.audit.SimulationControl
import org.cryptobiotic.rlauxe.audit.createAuditRecord
import org.cryptobiotic.rlauxe.audit.createElectionRecord
import org.cryptobiotic.rlauxe.audit.startFirstRound
import org.cryptobiotic.rlauxe.boulder.createBoulderElection
import org.cryptobiotic.rlauxe.dominion.CvrExportCsvHeader
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportJsonSummary
import org.cryptobiotic.rlauxe.dominion.convertCvrExportJsonToCsv
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvFile
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.sf.createSfElection
import org.cryptobiotic.rlauxe.sf.readContestManifestFromZip
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.ZipReaderTour
import java.io.FileOutputStream
import kotlin.io.path.Path

fun makeCorlaElectionClca(toptopdir: String, auditcenter: String) {
    val topdir = "$toptopdir/clca"

    val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.04, ) // 2020 only
    val round = AuditRoundConfig(
        SimulationControl(nsimTrials = 10, estPercentile = listOf(42, 55, 67)),
        ContestSampleControl(minRecountMargin = .005, minSize = 10, contestSampleCutoff = 10000,
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
            minMargin = .005,
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