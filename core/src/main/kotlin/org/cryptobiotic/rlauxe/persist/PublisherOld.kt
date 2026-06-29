package org.cryptobiotic.rlauxe.persist

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.util.ErrorMessages
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path

private val logger = KotlinLogging.logger("PublisherOld")

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

class PublisherOld(val topdir: String) {
    val auditdir = "$topdir/audit"
    fun auditCreationConfigFile() = "$auditdir/auditCreationConfig.json"
    fun auditRoundProtoFile() = "$auditdir/auditRoundConfig.json"
    fun cardManifestFile() = "$auditdir/cardManifest.csv" // cardManifest
    fun cardPoolsFile() = "$auditdir/cardPools.csv"
    fun countyCardPoolsFile() = "$auditdir/countyCardPools.csv"
    fun countyCvrPoolsFile() = "$auditdir/countyCvrPools.csv"
    fun cardStylesFile() = "$auditdir/cardStyles.json"
    fun contestsFile() = "$auditdir/contests.json"
    fun electionInfoFile() = "$auditdir/electionInfo.json"
    fun fastSamplingFile() = "$auditdir/fastSampling.bin" //make sampling fast
    fun sortedCardsFile() = "$auditdir/sortedCards.csv" // sorted cardManifest
    fun sortedCardsProtoFile() = "$auditdir/sortedCards.proto" // cardManifest

    // private
    fun sortedMvrsFile() = "$auditdir/private/sortedMvrs.csv"   // TODO make proto ??
    fun privateOneshotFile() = "$auditdir/private/oneshot.txt"
    fun unsortedMvrsFile() = "$auditdir/private/unsortedMvrs.csv"
    fun unsortedMvrsDirectory() = "$auditdir/private"

    fun auditRoundConfigFile(round: Int): String {
        val dir = "$auditdir/round$round"
        validateOutputDir(Path.of(dir), ErrorMessages("auditStateFile"))
        return "$dir/auditRoundConfig$round.json"
    }

    fun auditEstFile(round: Int): String {
        val dir = "$auditdir/round$round"
        validateOutputDir(Path.of(dir), ErrorMessages("auditStateFile"))
        return "$dir/auditEst$round.json"
    }

    fun auditFile(round: Int): String {
        val dir = "$auditdir/round$round"
        validateOutputDir(Path.of(dir), ErrorMessages("auditStateFile"))
        return "$dir/auditState$round.json"
    }

    fun samplePrnsFile(round: Int): String {
        val dir = "$auditdir/round$round"
        validateOutputDir(Path.of(dir), ErrorMessages("samplePrnsFile"))
        return "$dir/samplePrns$round.json"
    }

    fun sampleCardsFile(round: Int): String {
        val dir = "$auditdir/round$round"
        validateOutputDir(Path.of(dir), ErrorMessages("sampleCards"))
        return "$dir/sampleCards$round.csv"
    }

    fun sampleMvrsFile(round: Int): String {
        val dir = "$auditdir/round$round"
        validateOutputDir(Path.of(dir), ErrorMessages("sampleMvrsFile")) // TODO bad idea ?
        return "$dir/sampleMvrs$round.csv"
    }

    // debugging see "keepSimMvrs" flag
    fun estMvrsFile(round: Int, trial: Int): String {
        val dir = "$auditdir/round$round"
        validateOutputDir(Path.of(dir), ErrorMessages("sampleMvrsFile"))
        return "$dir/estMvrs$trial.csv"
    }

    // what round are we on?
    fun currentRound(): Int {
        var roundIdx = 1
        while (Files.exists(Path.of("$auditdir/round$roundIdx"))) {
            roundIdx++
        }
        return roundIdx - 1
    }
}