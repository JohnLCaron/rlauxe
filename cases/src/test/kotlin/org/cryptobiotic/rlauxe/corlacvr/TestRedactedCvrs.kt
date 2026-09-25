package org.cryptobiotic.rlauxe.corlacvr

import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test

class TestRedactedCvrs {

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
        val corlaCvrs = Garfield2020RawCvrs("/home/stormy/dev/github/rla/rlauxe/cases/src/test/data/anon/2020/Garfield.csv")
        println("  nrows = ${corlaCvrs.nrows()} redacted cvrs = ${corlaCvrs.redaction().redactedRows().size} redacted groups = ${corlaCvrs.redaction().groups().size} ")
    }

}