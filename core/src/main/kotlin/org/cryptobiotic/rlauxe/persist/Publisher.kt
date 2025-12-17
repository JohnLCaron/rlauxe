package org.cryptobiotic.rlauxe.persist

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.util.ErrorMessages
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
// import kotlin.io.path.Path

private val logger = KotlinLogging.logger("Publisher")

/* see docs/Overview.md

    $auditdir/
        auditConfig.json      // AuditConfigJson
        auditSeed.json        // PrnJson
        cardManifest.csv      // AuditableCardCsv, may be zipped
        cardPools.json        // CardPoolJson (OneAudit only)
        contests.json         // ContestsUnderAuditJson
        sortedCards.csv       // AuditableCardCsv, sorted by prn, may be zipped

        roundX/
            auditStateX.json     // AuditRoundJson,  the state of the audit for this round
            sampleCardsX.csv     // AuditableCardCsv, complete cards used for this round; matches samplePrnsX.csv
            sampleMvrsX.csv      // AuditableCardCsv, complete mvrs used for this round; matches samplePrnsX.csv
            samplePrnsX.json     // SamplePrnsJson, complete sample prns for this round, in order

        private/
            sortedMvrs.csv       // AuditableCardCsv, sorted by prn, matches sortedCards.csv, may be zipped
 */

class Publisher(val auditDir: String) {
    init {
        validateOutputDir(Path.of(auditDir), ErrorMessages("Publisher"))
    }

    fun auditConfigFile() = "$auditDir/auditConfig.json"
    fun auditSeedFile() = "$auditDir/auditSeed.json"
    fun cardManifestFile() = "$auditDir/cardManifest.csv" // cardManifest
    fun cardPoolsFile() = "$auditDir/cardPools.json"
    fun populationsFile() = "$auditDir/populations.json"
    fun contestsFile() = "$auditDir/contests.json"
    fun sortedCardsFile() = "$auditDir/sortedCards.csv" // sorted cardManifest
    fun privateMvrsFile() = "$auditDir/private/sortedMvrs.csv"

    fun auditStateFile(round: Int): String {
        val dir = "$auditDir/round$round"
        validateOutputDir(Path.of(dir), ErrorMessages("auditStateFile"))
        return "$dir/auditState$round.json"
    }

    fun samplePrnsFile(round: Int): String {
        val dir = "$auditDir/round$round"
        validateOutputDir(Path.of(dir), ErrorMessages("samplePrnsFile"))
        return "$dir/samplePrns$round.json"
    }

    fun sampleCardsFile(round: Int): String {
        val dir = "$auditDir/round$round"
        validateOutputDir(Path.of(dir), ErrorMessages("sampleCards"))
        return "$dir/sampleCards$round.csv"
    }

    fun sampleMvrsFile(round: Int): String {
        val dir = "$auditDir/round$round"
        validateOutputDir(Path.of(dir), ErrorMessages("sampleMvrsFile"))
        return "$dir/sampleMvrs$round.csv"
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

fun existsOrZip(filename: String) : Boolean {
    return (Files.exists(kotlin.io.path.Path("$filename.zip"))) || (Files.exists(kotlin.io.path.Path(filename)))
}