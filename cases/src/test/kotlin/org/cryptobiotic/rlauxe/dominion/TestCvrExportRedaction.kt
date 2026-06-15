package org.cryptobiotic.rlauxe.dominion

import org.cryptobiotic.rlauxe.auditcenter.Colorado2020General
import org.cryptobiotic.rlauxe.votedatabase.colorado2020
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCvrExportRedaction {
    val show = false

    @Test
    fun testBoulder20() {
        val filename = "/home/stormy/datadrive/votedatabase/cvr/Colorado/Boulder/Boulder CO.csv"
        val export: DominionCvrExport = DominionCvrExportReader(filename).read()
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
        val export: DominionCvrExport = DominionCvrExportReader(filename).read()
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
        val export: DominionCvrExport = DominionCvrExportReader(filename).read()
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
        val export: DominionCvrExport = DominionCvrExportReader(filename).read()
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
        val export: DominionCvrExport = DominionCvrExportReader(filename).read()
        export.exportCardStyles.forEach { type ->
            println("  $type")
        }
        export.redacted.forEach { group ->
            println("  $group")
        }
    }

    @Test
    fun testElPaso() { // redaction
        var filename = "$colorado2020/El Paso/cvr.csv"
        println(filename)
        val export: DominionCvrExport = DominionCvrExportReader(filename).read()
        export.exportCardStyles.forEach { type ->
            println("  $type")
        }
        export.redacted.forEach { group ->
            println("  $group")
        }
    }

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
                            val export: DominionCvrExport = DominionCvrExportReader(filename).read()
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