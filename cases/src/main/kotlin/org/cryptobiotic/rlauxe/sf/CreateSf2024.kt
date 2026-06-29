package org.cryptobiotic.rlauxe.sf

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.SimulationControl
import org.cryptobiotic.rlauxe.dominion.CvrExportCsvHeader
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportJsonSummary
import org.cryptobiotic.rlauxe.dominion.convertCvrExportJsonToCsv
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvFile
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.ZipReaderTour
import java.io.FileOutputStream

fun makeSFElectionOA(toptopdir: String) {
    val castVoteRecordZip = "$toptopdir/CVR_Export_20241202143051.zip"
    val cvrExportCsv = "$toptopdir/$cvrExportCsvFile"
    val topdir = "$toptopdir/oa"

    val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit=.05,)
    val round = AuditRoundConfig(
        SimulationControl(nsimTrials = 22),
        ContestSampleControl(minRecountMargin = .005, minMargin=0.0, contestSampleCutoff = 2500, auditSampleCutoff = 5000),
        ClcaConfig(), null)

    createSfElection(
        topdir=topdir,
        castVoteRecordZip,
        "ContestManifest.json",
        "CandidateManifest.json",
        cvrExportCsv = cvrExportCsv,
        creation,
        round,
    )
}

fun makeSFElectionClca(toptopdir: String) {
    val castVoteRecordZip = "$toptopdir/CVR_Export_20241202143051.zip"
    val cvrExportCsv = "$toptopdir/$cvrExportCsvFile"
    val topdir = "$toptopdir/clca"

    val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.05, )
    val round = AuditRoundConfig(
        SimulationControl(nsimTrials = 22),
        ContestSampleControl(contestSampleCutoff = 1000, auditSampleCutoff = 2000),
        ClcaConfig(fuzzMvrs=.001), null)

    createSfElection(
        topdir=topdir,
        castVoteRecordZip,
        "ContestManifest.json",
        "CandidateManifest.json",
        cvrExportCsv = cvrExportCsv,
        creation,
        round,
    )
}

// extract the cvrExport from castVoteRecordZip zipped json files, write to cvrExport.csv
// only need to do this once.
fun createCvrExportCsvFile(toptopdir: String): DominionCvrExportJsonSummary {
    val castVoteRecordZip = "$toptopdir/CVR_Export_20241202143051.zip"
    val manifestFile = "ContestManifest.json"
    val stopwatch = Stopwatch()
    val outputFilename = "$toptopdir/$cvrExportCsvFile"
    val cvrExportCsvStream = FileOutputStream(outputFilename)
    cvrExportCsvStream.write(CvrExportCsvHeader.toByteArray())

    val contestManifest = readContestManifestFromZip(castVoteRecordZip, manifestFile)
    println("IRV contests = ${contestManifest.irvContests}")

    var countFiles = 0
    val summaryTotal = DominionCvrExportJsonSummary()
    val zipReader = ZipReaderTour(
        castVoteRecordZip, silent = true, sortPaths = true,
        filter = { path -> path.toString().contains("CvrExport_") },
        visitor = { inputStream ->
            val summary = convertCvrExportJsonToCsv(inputStream, cvrExportCsvStream, contestManifest)
            summaryTotal.add(summary)
            countFiles++
        },
    )
    zipReader.tourFiles()
    cvrExportCsvStream.close()

    println("read ${summaryTotal.ncvrs} cvrs in $countFiles files")
    println("took = $stopwatch")
    return summaryTotal
}

/*
$ java -classpath cases/build/libs/cases-0.10.0.0-uber.jar org.cryptobiotic.rlauxe.cli.CreateCaseData  \
   -case sf2024 -toptopdir "/home/stormy/datadrive/rla/cases/sf2024" --cvrExport

$ java -classpath cases/build/libs/cases-0.10.0.0-uber.jar org.cryptobiotic.rlauxe.cli.CreateCaseData  \
   -case sf2024 -toptopdir "/home/stormy/datadrive/rla/cases/sf2024"

$ java -classpath cases/build/libs/cases-0.10.0.0-uber.jar org.cryptobiotic.rlauxe.cli.CreateCaseData  \
   -case sf2024 -toptopdir "/home/stormy/datadrive/rla/cases/sf2024" --auditType clca
 */