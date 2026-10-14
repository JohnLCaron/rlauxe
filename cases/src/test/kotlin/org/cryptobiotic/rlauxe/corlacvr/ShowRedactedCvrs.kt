package org.cryptobiotic.rlauxe.corlacvr

import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test

class ShowRedactedCvrs {

    @Test
    fun allColorado2020Counties() {
        val path = Path("/home/stormy/dev/github/rla/rlauxe/cases/src/test/data/anon/2020")
        path.listDirectoryEntries().sorted().filter{ !it.toString().contains("redacted.csv") }.forEach { path ->
            lookForRedactions(path.toString(), showRedaction = true)
        }
    }

    @Test
    fun oneColorado2020Counties() {
        // lookForRedactions("/home/stormy/datadrive/votedatabase/cvr/Colorado/Phillips/cvr.csv", showHeaders = true)
        lookForRedactions("/home/stormy/dev/github/rla/rlauxe/cases/src/test/data/anon/2020/Phillips.csv", showHeaders = true)
    }

    @Test
    fun testGarfield() {
        val corlaCvrs = Garfield2020CountyCvrs("/home/stormy/dev/github/rla/rlauxe/cases/src/test/data/anon/2020/Garfield.csv")
        println("  nrows = ${corlaCvrs.nrows()} redacted cvrs = ${corlaCvrs.redaction().redactedRows().size} redacted groups = ${corlaCvrs.redaction().groups().size} ")
    }
}


fun lookForRedactions(filename: String, showRedaction: Boolean = false, showHeaders: Boolean = false) {
    println("## Reading cvrs from $filename")

    val redaction = if (filename.lowercase().contains("boulder")) RedactionBoulder(show=showRedaction) else Redaction(show=showRedaction)

    try {
        val corlaRawCvrs = readCountyCvrs(filename, redaction = redaction, showHeaders = showHeaders)

        println("  nrows = ${corlaRawCvrs.nrows()} redacted cvrs = ${corlaRawCvrs.redaction().redactedRows().size} redacted groups = ${corlaRawCvrs.redaction().groups().size} ")

        if (corlaRawCvrs.redaction().groups().size > 0) {
            var sumInGroups = 0
            corlaRawCvrs.redaction().groups().forEach { group ->
                println("  $group")
                sumInGroups += group.ncards()
            }
            println("sumInGroups = $sumInGroups")
        }

    } catch (e: Throwable) {
        println("$filename got error ${e.message}")
    }
}