package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.Sampling
import org.cryptobiotic.rlauxe.audit.SimulationControl
import org.cryptobiotic.rlauxe.corlaCounty.ElectionVariantEnum
import org.cryptobiotic.rlauxe.corlaInput.Colorado2020General
import org.cryptobiotic.rlauxe.corlaInput.Colorado2022Primary
import org.cryptobiotic.rlauxe.corlaInput.Colorado2024General
import org.cryptobiotic.rlauxe.corlaInput.Colorado2026PMerged
import org.cryptobiotic.rlauxe.corlaInput.Colorado2026Primary
import org.cryptobiotic.rlauxe.corlaInput.Colorado2026PwithCvrs
import org.cryptobiotic.rlauxe.corlaInput.votedatabase2020Counties
import kotlin.collections.forEach
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries

fun corlaCreationSettings(year: Int) =
    AuditCreationConfig(AuditType.CLCA,
        riskLimit= if (year == 2020) .04 else .03
    )

fun corlaRoundSettings(sampling: Sampling) = AuditRoundConfig(
    SimulationControl(nsimTrials = 10, estPercentile = listOf(50, 80)),
    ContestSampleControl(minRecountMargin = .005, minMargin = .005, minSize = 10,
        contestSampleCutoff = 5000, auditSampleCutoff = 200000, sampling = sampling),
    ClcaConfig(), null)

fun makeCorla2020ClcaWithCvrs(toptopdir: String, auditcenter: String, votedatabase: String?) {
    val topdir = "$toptopdir/clca"

    // uses votedatabase; redacted ballots are simulated; perhaps should be onepool ?
    createCorlaStateElection(
        topdir = topdir,
        Colorado2020General(auditcenter),
        creation = corlaCreationSettings(2020),
        roundConfig = corlaRoundSettings(sampling = Sampling.consistent),
        variant = ElectionVariantEnum.Sim,
        votedatabase = if (votedatabase != null) votedatabase2020Counties(votedatabase) else null,
    )

    /* createCountyElectionSimCvrs(
        topdir,
        Colorado2020General(auditcenter),
        corlaCreationSettings(2020),
        corlaRoundSettings(sampling = Sampling.consistent),
        name = "Colorado 2020 clca",
        startFirstRound = true
    ) */
}

fun makeCorla2020Uniform(toptopdir: String, auditcenter: String)  {
    val topdir = "$toptopdir/uniform"

    createCountyElectionSimCvrs(
        topdir,
        Colorado2020General(auditcenter),
        corlaCreationSettings(2020),
        corlaRoundSettings(sampling = Sampling.uniform),
        name = "Colorado 2020 uniform",
        startFirstRound = true
    )
}

fun makeCorla2020Clca(toptopdir: String, auditcenter: String) {
    val topdir = "$toptopdir/clca"

    createCountyElectionSimCvrs(
        topdir,
        Colorado2020General(auditcenter),
        corlaCreationSettings(2020),
        corlaRoundSettings(sampling = Sampling.consistent),
        name = "Colorado2020 Clca with Cvrs",
        startFirstRound = true
    )

    /* countyElectionWithCvrs(
        votedatabase2020Counties(votedatabase),
        Colorado2020General(auditcenter),
        topdir,
        corlaCreationSettings(2020),
        corlaRoundSettings(sampling = Sampling.consistent),
        name = "Colorado2020 Clca with Cvrs",
        startFirstRound = true,
        isUniform = false,
    ) */
}

fun makeCorla2020UniformSimCvrs(toptopdir: String, auditcenter: String, votedatabase: String) {
    val topdir = "$toptopdir/uniform"

    createCountyElectionSimCvrs(
        topdir, Colorado2020General(auditcenter),
        corlaCreationSettings(2020),
        corlaRoundSettings(sampling = Sampling.uniform),
        name = "Colorado2020 Clca with Cvrs",
        startFirstRound = true
    )

    /* countyElectionWithCvrs(
        votedatabase2020Counties(votedatabase),
        Colorado2020General(auditcenter),
        topdir,
        corlaCreationSettings(2020),
        corlaRoundSettings(sampling = Sampling.uniform),
        name = "Colorado2020 Uniform with Cvrs",
        startFirstRound = true,
        isUniform = true,
    ) */
}

fun makeCorla2022Primary(toptopdir: String, auditcenter: String) {
    val topdir = toptopdir

    createCountyElectionSimCvrs(
        topdir, Colorado2022Primary(auditcenter),
        corlaCreationSettings(2022),
        corlaRoundSettings(sampling = Sampling.consistent),
        name = "Colorado2022Primary", startFirstRound = true
    )
}

fun makeCorla2024(toptopdir: String, auditcenter: String) {
    val topdir = toptopdir

    createCountyElectionSimCvrs(
        topdir, Colorado2024General(auditcenter),
        corlaCreationSettings(2024),
        corlaRoundSettings(sampling = Sampling.consistent),
        name = "County2024General", startFirstRound = true
    )
}

fun makeCorla2026p(toptopdir: String, auditcenter: String) {
    val topdir = toptopdir

    createCountyElectionSimCvrs(
        topdir, Colorado2026Primary(auditcenter),
        corlaCreationSettings(2026),
        corlaRoundSettings(sampling = Sampling.consistent),
        name = "Colorado2026PrimaryMerged", startFirstRound = true
    )
}

fun makeCorla2026pm(toptopdir: String, auditcenter: String) {
    val topdir = toptopdir

    createCountyElectionSimCvrs(
        topdir, Colorado2026PMerged(auditcenter),
        corlaCreationSettings(2026),
        corlaRoundSettings(sampling = Sampling.consistent),
        name = "Colorado2026Primary", startFirstRound = true
    )
}

fun makeCorla2026Pcvrs(toptopdir: String, auditcenter: String) {
    val topdir = toptopdir

    // uses auditcenter 4 counties; redacted ballots are simulated
    createCorlaStateElection(
        topdir = topdir,
        Colorado2026PwithCvrs(auditcenter),
        creation = corlaCreationSettings(2026),
        roundConfig = corlaRoundSettings(sampling = Sampling.consistent),
        variant = ElectionVariantEnum.Sim,
    )

    /* countyElectionWithCvrs(
        auditcenter2026Counties("$auditcenter/2026/primary/observerfiles"),
        Colorado2026PwithCvrs(),
        topdir,
        corlaCreationSettings(2026),
        corlaRoundSettings(sampling = Sampling.consistent),
        name = "Colorado2026Pcvrs",
        startFirstRound = true,
        isUniform = false,
    ) */
}

fun auditcenter2026Counties(topdir: String): Map<String, String> {
    val path = Path(topdir) // or does votedatabase include

    val cvrdata = mutableListOf<Pair<String, String>>()
    path.listDirectoryEntries().sorted().filter { it.fileName.toString().contains("CVR_Export")}.forEach { file ->
        var county = file.fileName.toString().split("_")[0]
        if (county == "LaPlata") county = "La Plata"
        cvrdata.add(Pair(county, file.toString()))
    }
    return cvrdata.toMap()
}

/*
$ java -classpath cases/build/libs/rlauxe-cases-0.10.4.2-uber.jar org.cryptobiotic.rlauxe.cli.CreateCaseData  \
    -case corla2020 -toptopdir "/home/stormy/datadrive/rla/cases/corla2020" \
    -auditcenter "//home/stormy/datadrive/github/nealmcb/auditcenter"

$ java -classpath cases/build/libs/rlauxe-cases-0.10.4.2-uber.jar org.cryptobiotic.rlauxe.cli.CreateCaseData  \
    -case corla2020withCvrs -toptopdir "/home/stormy/datadrive/rla/cases/corla2020withCvrs" \
    -auditcenter "//home/stormy/datadrive/github/nealmcb/auditcenter"

$ java -classpath cases/build/libs/rlauxe-cases-0.10.4.2-uber.jar org.cryptobiotic.rlauxe.cli.CreateCaseData  \
    -case corla2022p -toptopdir "/home/stormy/datadrive/rla/cases/corla2022p" \
    -auditcenter "//home/stormy/datadrive/github/nealmcb/auditcenter"

$ java -classpath cases/build/libs/rlauxe-cases-0.10.4.2-uber.jar org.cryptobiotic.rlauxe.cli.CreateCaseData  \
    -case corla2024 -toptopdir "/home/stormy/datadrive/rla/cases/corla2024" \
    -auditcenter "//home/stormy/datadrive/github/nealmcb/auditcenter"

$ java -classpath cases/build/libs/rlauxe-cases-0.10.4.2-uber.jar org.cryptobiotic.rlauxe.cli.CreateCaseData  \
    -case corla2026pm -toptopdir "/home/stormy/datadrive/rla/cases/corla2026pm" \
    -auditcenter "//home/stormy/datadrive/github/nealmcb/auditcenter"

$ java -classpath cases/build/libs/rlauxe-cases-0.10.4.2-uber.jar org.cryptobiotic.rlauxe.cli.CreateCaseData  \
    -case corla2026Pcvrs -toptopdir "/home/stormy/datadrive/rla/cases/corla2026Pcvrs" \
    -auditcenter "//home/stormy/datadrive/github/nealmcb/auditcenter"
 */