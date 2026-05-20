package org.cryptobiotic.rlauxe.persist

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.util.ErrorMessages
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

private val logger = KotlinLogging.logger("Publisher")

/* also see docs/AuditRecord.md

$topdir/
    countyData.csv (county contests only)
    countyContestData.csv (county contests only)

    $auditdir/
        // election record - output of CreateElectionRecord
        cardManifest.csv      // AuditableCardCsv, may be zipped
        cardPools.csv         // CardPoolCsv:    CardPoolIF -> CardPool (optional)
        cardStyles.json       // CardStylesJson: CardStyleIF -> CardStyle (optional)
        contests.json         // ContestsUnderAuditJson
        electionInfo.json     // ElectionInfoJson

        // auditRecord - output of CreateAuditRecord
        auditCreationConfig.json  // AuditCreationConfigJson
        auditRoundPrototype.json // AuditRoundConfigJson
        sortedCards.csv       // AuditableCardCsv, sorted by prn, may be zipped
        sortedCards.proto     // ProtoCard: same as sortedCards.csv in protobuf (4x faster than csv), optional
        fastSampling.bin      // just prn, styleId in binary (30-240x faster than proto), optional

        roundX/
            auditEstX.json       // AuditRoundJson,  an audit state with estimation, ready for auditing
            auditRoundConfigX.json  // AuditRoundConfigJson, configuration for round
            auditStateX.json     // AuditRoundJson,  the results of the audit for this round
            sampleCardsX.csv     // AuditableCardCsv, complete sorted cards used for this round
            sampleMvrsX.csv      // AuditableCardCsv, complete sorted mvrs used for this round
            samplePrnsX.json     // SamplePrnsJson, complete sorted sample prns for this round

        private/
            sortedMvrs.csv       // AuditableCardCsv, sorted by prn, matches sortedCards.csv, may be zipped
            unsortedMvrs.csv     // AuditableCardCsv (optional)
 */

class Publisher(val auditDir: String) {
    init {
        validateOutputDir(Path.of(auditDir), ErrorMessages("Publisher"))
    }

    // fun auditConfigFile() = "$auditDir/auditConfig.json"
    fun auditCreationConfigFile() = "$auditDir/auditCreationConfig.json"
    fun auditRoundProtoFile() = "$auditDir/auditRoundConfig.json"
    // fun auditSeedFile() = "$auditDir/auditSeed.json"
    fun cardManifestFile() = "$auditDir/cardManifest.csv" // cardManifest
    fun cardPoolsFile() = "$auditDir/cardPools.csv"
    fun cardStylesFile() = "$auditDir/cardStyles.json"
    fun contestsFile() = "$auditDir/contests.json"
    fun electionInfoFile() = "$auditDir/electionInfo.json"
    fun fastSamplingFile() = "$auditDir/fastSampling.bin" //make sampling fast
    fun sortedCardsFile() = "$auditDir/sortedCards.csv" // sorted cardManifest
    fun sortedCardsProtoFile() = "$auditDir/sortedCards.proto" // cardManifest

    // private
    fun sortedMvrsFile() = "$auditDir/private/sortedMvrs.csv"   // TODO make proto ??
    fun privateOneshotFile() = "$auditDir/private/oneshot.txt"
    fun unsortedMvrsFile() = "$auditDir/private/unsortedMvrs.csv"

    fun auditRoundConfigFile(round: Int): String {
        val dir = "$auditDir/round$round"
        validateOutputDir(Path.of(dir), ErrorMessages("auditStateFile"))
        return "$dir/auditRoundConfig$round.json"
    }

    fun auditEstFile(round: Int): String {
        val dir = "$auditDir/round$round"
        validateOutputDir(Path.of(dir), ErrorMessages("auditStateFile"))
        return "$dir/auditEst$round.json"
    }

    fun auditFile(round: Int): String {
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

    // debugging see "keepSimMvrs" flag
    fun estMvrsFile(round: Int, trial: Int): String {
        val dir = "$auditDir/round$round"
        validateOutputDir(Path.of(dir), ErrorMessages("sampleMvrsFile"))
        return "$dir/estMvrs$trial.csv"
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
fun clearDirectory(path: Path) {
    if (!Files.exists(path)) {
        Files.createDirectories(path)
    } else {
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .map { obj: Path -> obj.toFile() }
            .forEach { obj: File -> obj.delete() }
    }
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

fun exists(filename: String) : Boolean {
    return Files.exists(kotlin.io.path.Path(filename))
}
