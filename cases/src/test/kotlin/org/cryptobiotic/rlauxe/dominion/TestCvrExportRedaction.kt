package org.cryptobiotic.rlauxe.dominion

import org.cryptobiotic.rlauxe.votedatabase.colorado2020
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test

class TestCvrExportRedaction {
    val show = false

    @Test
    fun testBoulder20() {
        val filename = "/home/stormy/datadrive/votedatabase/cvr/Colorado/Boulder/Boulder CO.csv"
        val export: DominionCvrCsvSummary = DominionCvrExportCsvReader(filename).read()
        export.exportCardStyles.forEach { type ->
            println("  $type")
        }
        // doesnt seem to have redactions
        export.redacted.forEach { group ->
            println("  $group")
        }
    }

    @Test
    fun testBoulder22Primary() {
        val filename = "/home/stormy/datadrive/votedatabase/cvr/2022Primaries/Colorado/Boulder CO '22 Primary.csv"
        val export: DominionCvrCsvSummary = DominionCvrExportCsvReader(filename).read()
        export.exportCardStyles.forEach { type ->
            println("  $type")
        }
        // doesnt seem to have redactions
        export.redacted.forEach { group ->
            println("  $group")
        }
    }

    @Test
    fun testBoulder24() {
        val filename = "src/test/data/Boulder2024/2024-Boulder-County-General-Recount-Redacted-Cast-Vote-Record.csv"
        val export: DominionCvrCsvSummary = DominionCvrExportCsvReader(filename).read()
        export.exportCardStyles.forEach { type ->
            println("  $type")
        }
        export.redacted.forEach { group ->
            println("  $group")
        }
    }

    @Test
    fun testBoulder25() {
        val filename = "src/test/data/Boulder2025/Redacted-CVR-PUBLIC.csv"
        val export: DominionCvrCsvSummary = DominionCvrExportCsvReader(filename).read()
        export.exportCardStyles.forEach { type ->
            println("  $type")
        }
        export.redacted.forEach { group ->
            println("  $group")
        }
    }

    @Test
    fun testEagle() { // redaction
        var filename = "$colorado2020/Eagle/cvr.csv"
        println(filename)
        val export: DominionCvrCsvSummary = DominionCvrExportCsvReader(filename).read()
        export.exportCardStyles.forEach { type ->
            println("  $type")
        }
        export.redacted.forEach { group ->
            println("  $group")
        }
    }

    @Test
    fun testElPaso() { // redaction
        var filename = "/home/stormy/datadrive/votedatabase/cvr/Colorado/Jefferson/JeffCO_2020_CVR_Redacted.csv"
        println(filename)
        val export: DominionCvrCsvSummary = DominionCvrExportCsvReader(filename).read()
        export.exportCardStyles.forEach { type ->
            println("  $type")
        }
        export.redacted.forEach { group ->
            println("  $group")
        }
    }

    // Boulder, Doloros, Pitkin, possibly Jefferson
    // /home/stormy/datadrive/votedatabase/cvr/Colorado/Jefferson/JeffCO_2020_CVR_Redacted.csv has columns shifted by 1
    // /home/stormy/datadrive/votedatabase/cvr/Colorado/Phillips/cvr.csv
    ///home/stormy/datadrive/votedatabase/cvr/Colorado/Phillips/Phillips 2020  cvr phillips county.csv
    //  RedactedGroup('r', ncards=1, contests=[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29] totalVotes=62726)
    @Test
    fun allColorado2020Counties() {
        val path = Path(colorado2020)
        path.listDirectoryEntries().sorted().filter { it.isDirectory() && !it.fileName.toString().startsWith("202") }
            .forEach { subdir ->
                val county = subdir.fileName.toString()
                if (county !in listOf("Monroe", "Roosevelt", "Garfield")) {
                    subdir.listDirectoryEntries().filter {
                        !it.isDirectory() && it.fileName.toString().endsWith(".csv")
                                && it.fileName.toString() != "summary.csv"
                                && !it.fileName.toString().contains("Manifest")
                    }.forEach { entry ->
                        try {
                            val filename = entry.toString()
                            println(filename)
                            val export: DominionCvrCsvSummary = DominionCvrExportCsvReader(filename).read()
                            export.redacted.forEach { group -> println("  $group") }

                        } catch (e: Exception) {
                            println(e.message)
                            throw e
                        }
                    }
                }
            }
    }
}