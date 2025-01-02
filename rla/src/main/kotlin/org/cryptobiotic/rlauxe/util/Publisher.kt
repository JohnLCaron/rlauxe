package org.cryptobiotic.rlauxe.util

import java.nio.file.Files
import java.nio.file.Path

class Publisher(val topdir: String) {
    init {
        validateOutputDir(Path.of(topdir), ErrorMessages("Publisher"))
    }

    fun auditConfigFile() = "$topdir/audirConfig.json"
    fun electionInitFile() = "$topdir/electionInit.json"
    fun cvrsFile() = "$topdir/cvrs.json"

    fun sampleIndicesFile(round: Int): String {
        val dir = "$topdir/round$round"
        validateOutputDir(Path.of(dir), ErrorMessages("Publisher"))
        return "$dir/sampleIndices.json"
    }

    fun auditRoundFile(round: Int): String {
        val dir = "$topdir/round$round"
        validateOutputDir(Path.of(dir), ErrorMessages("Publisher"))
        return "$dir/auditRound.json"
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