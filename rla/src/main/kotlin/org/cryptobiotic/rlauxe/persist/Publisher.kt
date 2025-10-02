package org.cryptobiotic.rlauxe.persist

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.util.ErrorMessages
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

private val logger = KotlinLogging.logger("Publisher")

/*
    topdir/
      auditConfig.json      // AuditConfigJson
      contests.json         // ContestsUnderAuditJson
      sortedCards.csv       // AuditableCardCsv (or)
      sortedCards.zip       // AuditableCardCsv
      ballotPool.csv        // BallotPoolCsv (OneAudit only)

      roundX/
        auditState.json     // AuditRoundJson
        samplePrns.json     // SamplePrnsJson // the sample prns to be audited
        sampleMvrs.csv      // AuditableCardCsv  // the mvrs used for the audit; matches sampleNumbers.json
 */

class Publisher(val auditDir: String) {
    init {
        validateOutputDir(Path.of(auditDir), ErrorMessages("Publisher"))
    }

    fun auditConfigFile() = "$auditDir/auditConfig.json"
    fun contestsFile() = "$auditDir/contests.json"
    fun cardsCsvFile() = "$auditDir/sortedCards.csv"
    fun cardsCsvZipFile() = "$auditDir/sortedCards.csv.zip"
    fun ballotPoolsFile() = "$auditDir/ballotPools.csv"

    fun samplePrnsFile(round: Int): String {
        val dir = "$auditDir/round$round"
        validateOutputDir(Path.of(dir), ErrorMessages("samplePrnsFile"))
        return "$dir/samplePrns.json"
    }

    fun sampleMvrsFile(round: Int): String {
        val dir = "$auditDir/round$round"
        validateOutputDir(Path.of(dir), ErrorMessages("sampleMvrsFile"))
        return "$dir/sampleMvrs.csv"
    }

    fun auditRoundFile(round: Int): String {
        val dir = "$auditDir/round$round"
        validateOutputDir(Path.of(dir), ErrorMessages("auditRoundFile"))
        return "$dir/auditState.json"
    }

    // what round are we on?
    fun currentRound(): Int {
        var roundIdx = 1
        while (Files.exists(Path.of("$auditDir/round$roundIdx"))) {
            roundIdx++
        }
        return roundIdx - 1
    }
}

/** Make sure output directories exists; delete existing files in them.  */
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

/** Make sure output directories exist and are writeable.  */
fun validateOutputDir(dirPath: Path, errs: ErrorMessages? = null): ErrorMessages {
    val useErrors = errs ?: ErrorMessages("anon")
    if (!Files.exists(dirPath)) {
        Files.createDirectories(dirPath)
    }
    if (!Files.isDirectory(dirPath)) {
        useErrors.add(" Output directory '$dirPath' is not a directory")
    }
    if (!Files.isWritable(dirPath)) {
        useErrors.add(" Output directory '$dirPath' is not writeable")
    }
    if (!Files.isExecutable(dirPath)) {
        useErrors.add(" Output directory '$dirPath' is not executable")
    }
    if (useErrors.hasErrors()) logger.error{ useErrors.toString() }
    return useErrors
}

fun validateOutputDirOfFile(filename: String) {
    val parentDir = Path.of(filename).parent
    validateOutputDir(parentDir, ErrorMessages("validateOutputDirOfFile"))
}