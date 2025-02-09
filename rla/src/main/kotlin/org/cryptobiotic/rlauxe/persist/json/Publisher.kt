package org.cryptobiotic.rlauxe.persist.json

import org.cryptobiotic.rlauxe.util.ErrorMessages
import java.nio.file.Files
import java.nio.file.Path

class Publisher(val topdir: String) {
    init {
        validateOutputDir(Path.of(topdir), ErrorMessages("Publisher"))
    }

    fun auditConfigFile() = "$topdir/auditConfig.json"
    fun cvrsFile() = "$topdir/cvrs.json"
    fun ballotManifestFile() = "$topdir/ballotManifest.json"

    fun sampleIndicesFile(round: Int): String {
        val dir = "$topdir/round$round"
        validateOutputDir(Path.of(dir), ErrorMessages("Publisher"))
        return "$dir/sampleIndices.json"
    }

    fun sampleMvrsFile(round: Int): String {
        val dir = "$topdir/round$round"
        validateOutputDir(Path.of(dir), ErrorMessages("Publisher"))
        return "$dir/sampleMvrs.json"
    }

    fun auditRoundFile(round: Int): String {
        val dir = "$topdir/round$round"
        validateOutputDir(Path.of(dir), ErrorMessages("Publisher"))
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