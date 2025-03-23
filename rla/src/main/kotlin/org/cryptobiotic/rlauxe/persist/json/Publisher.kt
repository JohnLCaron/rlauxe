package org.cryptobiotic.rlauxe.persist.json

import org.cryptobiotic.rlauxe.util.ErrorMessages
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/*
    topdir/
      auditConfig.json      // AuditConfigJson
      contests.json         // ContestsUnderAuditJson
      cvrs.csv              // CvrsCsv (or)
      ballotManifest.json   // BallotManifestJson

      roundX/
        auditState.json     // AuditRoundJson
        sampleNumbers.json  // SampleNumbersJson // the sample numbers to be audited
        sampleMvrs.csv      // CvrsCsv  // the mvrs used for the audit; matches sampleNumbers.json

 */

class Publisher(val topdir: String) {
    val errs =  ErrorMessages("Publisher");
    init {
        validateOutputDir(Path.of(topdir), errs)
    }

    fun auditConfigFile() = "$topdir/auditConfig.json"
    fun contestsFile() = "$topdir/contests.json"
    fun cvrsCsvFile() = "$topdir/cvrs.csv"
    fun ballotManifestFile() = "$topdir/ballotManifest.json"

    fun sampleNumbersFile(round: Int): String {
        val dir = "$topdir/round$round"
        validateOutputDir(Path.of(dir), errs)
        return "$dir/sampleNumbers.json"
    }

    fun sampleMvrsFile(round: Int): String {
        val dir = "$topdir/round$round"
        validateOutputDir(Path.of(dir), errs)
        return "$dir/sampleMvrs.csv"
    }

    fun auditRoundFile(round: Int): String {
        val dir = "$topdir/round$round"
        validateOutputDir(Path.of(dir), errs)
        return "$dir/auditState.json"
    }

    // what round are we on?
    fun rounds(): Int {
        var roundIdx = 1
        while (Files.exists(Path.of("$topdir/round$roundIdx"))) {
            roundIdx++
        }
        return roundIdx - 1
    }

    fun validateOutputDirOfFile(filename: String) {
        val parentDir = Path.of(filename).parent
        if (!validateOutputDir(parentDir, errs)) {
            println(errs)
        }
    }
}

/** Make sure output directories exists and are writeable.  */
fun clearDirectory(path: Path): Boolean {
    if (!Files.exists(path)) {
        Files.createDirectories(path)
    } else {
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .map { obj: Path -> obj.toFile() }
            .forEach { obj: File -> obj.delete() }
    }
    return true
}

/** Make sure output directories exists and are writeable.  */
fun validateOutputDir(path: Path, errs: ErrorMessages): Boolean {
    if (!Files.exists(path)) {
        Files.createDirectories(path)
    }
    if (!Files.isDirectory(path)) {
        errs.add(" Output directory '$path' is not a directory")
    }
    if (!Files.isWritable(path)) {
        errs.add(" Output directory '$path' is not writeable")
    }
    if (!Files.isExecutable(path)) {
        errs.add(" Output directory '$path' is not executable")
    }
    return !errs.hasErrors()
}