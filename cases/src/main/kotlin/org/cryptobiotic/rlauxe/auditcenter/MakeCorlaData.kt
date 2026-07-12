package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.Sampling
import org.cryptobiotic.rlauxe.audit.SimulationControl
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

fun corlaCreationSettings(year: Int) =
    AuditCreationConfig(AuditType.CLCA,
        riskLimit= if (year == 2020) .04 else .03
    )

fun corlaRoundSettings(sampling: Sampling) = AuditRoundConfig(
    SimulationControl(nsimTrials = 10, estPercentile = listOf(50, 80)),
    ContestSampleControl(minRecountMargin = .005, minMargin = .01, minSize = 10,
        contestSampleCutoff = 10000, auditSampleCutoff = 200000, sampling = sampling),
    ClcaConfig(), null)


fun makeCorla2020Clca(toptopdir: String, auditcenter: String) {
    val topdir = "$toptopdir/clca"

    createCountyElectionSansCvrs(
        topdir,
        Colorado2020General(auditcenter),
        corlaCreationSettings(2020),
        corlaRoundSettings(sampling = Sampling.consistent),
        name = "Colorado 2020 clca",
        startFirstRound = true)
}

fun makeCorla2020Uniform(toptopdir: String, auditcenter: String)  {
    val topdir = "$toptopdir/uniform"

    createCountyElectionSansCvrs(
        topdir,
        Colorado2020General(auditcenter),
        corlaCreationSettings(2020),
        corlaRoundSettings(sampling = Sampling.uniform),
        name = "Colorado 2020 uniform",
        startFirstRound = true)
}

fun makeCorla2020ClcaWithCvrs(toptopdir: String, auditcenter: String, votedatabase: String) {
    val topdir = "$toptopdir/clca"

    countyElectionWithCvrs(
        allVotedatabaseCounties(votedatabase),
        Colorado2020General(auditcenter),
        topdir,
        corlaCreationSettings(2020),
        corlaRoundSettings(sampling = Sampling.consistent),
        name = "Colorado2020 Clca with Cvrs",
        startFirstRound = true,
        isUniform = false,
    )
}

fun makeCorla2020UniformWithCvrs(toptopdir: String, auditcenter: String, votedatabase: String) {
    val topdir = "$toptopdir/uniform"

    countyElectionWithCvrs(
        allVotedatabaseCounties(votedatabase),
        Colorado2020General(auditcenter),
        topdir,
        corlaCreationSettings(2020),
        corlaRoundSettings(sampling = Sampling.uniform),
        name = "Colorado2020 Uniform with Cvrs",
        startFirstRound = true,
        isUniform = true,
    )
}

fun makeCorla2022Primary(toptopdir: String, auditcenter: String) {
    val topdir = toptopdir

    createCountyElectionSansCvrs(
        topdir, Colorado2022Primary(auditcenter),
        corlaCreationSettings(2022),
        corlaRoundSettings(sampling = Sampling.consistent),
        name = "Colorado2022Primary", startFirstRound = true
    )
}

fun makeCorla2024(toptopdir: String, auditcenter: String) {
    val topdir = toptopdir

    createCountyElectionSansCvrs(
        topdir, Colorado2024General(auditcenter),
        corlaCreationSettings(2024),
        corlaRoundSettings(sampling = Sampling.consistent),
        name = "County2024General", startFirstRound = true
    )
}

fun allVotedatabaseCounties(votedatabase: String): Map<String, String> {
    val path = Path(votedatabase) // or does votedatabase include

    val cvrdata = mutableListOf<Pair<String, String>>()
    path.listDirectoryEntries().sorted().filter { it.isDirectory() && !it.fileName.toString().startsWith("202")}.forEach { subdir ->
        val county = subdir.fileName.toString()
        // Baca duplicates Huerfano
        // Gunnison is missing contest tabulation
        // Las Animas has only 120 of 8000 cvrs
        // San Juan is missing
        // Monroe, Rooselvelt: no such county in Colorado
        if (county !in listOf("Baca", "Gunnison", "Las Animas", "San Juan", "Monroe", "Roosevelt")) {
            try {
                val filename = "${subdir}/cvr.csv" // entry.toString()
                cvrdata.add(Pair(county, filename))
            } catch (e: Exception) {
                println(e.message)
                throw e
            }
        }
    }
    return cvrdata.toMap()
}

/*
$ java -classpath cases/build/libs/cases-0.10.0.0-uber.jar org.cryptobiotic.rlauxe.cli.CreateCaseData  \
   -case boulder2024 -toptopdir "/home/stormy/datadrive/rla/cases/boulder2024"

$ java -classpath cases/build/libs/cases-0.10.0.0-uber.jar org.cryptobiotic.rlauxe.cli.CreateCaseData  \
   -case boulder2024 -toptopdir "/home/stormy/datadrive/rla/cases/boulder2024" --auditType clca
 */