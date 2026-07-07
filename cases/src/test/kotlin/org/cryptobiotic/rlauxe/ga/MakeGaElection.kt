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
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.persist.AuditRecord
import kotlin.test.Test

class MakeGaElection {

    @Test
    fun makeGa2Election() {
        val inputdir = "/home/stormy/datadrive/github/nealmcb/rla-review-arlo/2026-05-19-primary/extracted"
        val topdir = "$cases/ga/ga2026-2"

        val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .05,)
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 11),
            ContestSampleControl(minRecountMargin = .005, minMargin = .005, minSize = 10, contestSampleCutoff = 10000,
                auditSampleCutoff = 200000, sampling = Sampling.consistent),
            ClcaConfig(),
        )

        val contestsFile =
            "/home/stormy/datadrive/github/nealmcb/rla-review-arlo/2026-05-19-primary/downloads/contest_results_comparison.csv"

        //     electionName: String,
        //    version: String,
        //    topdir: String,
        //    creation: AuditCreationConfig,
        //    roundConfig: AuditRoundConfig,
        //    startFirstRound: Boolean = true,
        createGa2Election(
            "Ga2026Primary from ballot image audit",
            contestsFile,
            inputdir=inputdir,
            topdir = topdir,
            creation,
            round,
        )
    }

    @Test
    fun makeGaElectionOa() {
        val inputdir = "/home/stormy/datadrive/github/nealmcb/rla-review-arlo/2026-05-19-primary/extracted"
        val topdir = "$cases/ga/ga2026"

        val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .05,)
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 10),
            ContestSampleControl(minRecountMargin = .005, minSize = 10, contestSampleCutoff = 10000,
                auditSampleCutoff = 200000, sampling = Sampling.consistent),
            ClcaConfig(),
        )

        //     electionName: String,
        //    version: String,
        //    topdir: String,
        //    creation: AuditCreationConfig,
        //    roundConfig: AuditRoundConfig,
        //    startFirstRound: Boolean = true,
        createGaElection(
            "Ga2026Primary from manifests and candidate_totals",
            inputdir,
            topdir = topdir,
            AuditType.ONEAUDIT,
            creation,
            round,
        )
    }

    @Test
    fun makeGaElectionPolling() {
        val inputdir = "/home/stormy/datadrive/github/nealmcb/rla-review-arlo/2026-05-19-primary/extracted"
        val topdir = "$cases/ga/ga2026poll"

        val creation = AuditCreationConfig(AuditType.POLLING, riskLimit = .05,)
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 10),
            ContestSampleControl(minRecountMargin = .005, minSize = 10, contestSampleCutoff = 10000,
                auditSampleCutoff = 200000, sampling = Sampling.consistent),
            clcaConfig = null,
            pollingConfig = PollingConfig(),
        )

        //     electionName: String,
        //    version: String,
        //    topdir: String,
        //    creation: AuditCreationConfig,
        //    roundConfig: AuditRoundConfig,
        //    startFirstRound: Boolean = true,
        createGaElection(
            "Ga2026Primary (polling) from manifests and candidate_totals",
            inputdir,
            topdir = topdir,
            AuditType.POLLING,
            creation,
            round,
            pollingMode = PollingMode.withPools,
        )
    }

    @Test
    fun testVerifyGaElection() {
        val topdir = "$cases/ga3/ga2026"
        val record = AuditRecord.read(topdir)
        println(record)
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = true)
        println()
        print(results)
    }
}