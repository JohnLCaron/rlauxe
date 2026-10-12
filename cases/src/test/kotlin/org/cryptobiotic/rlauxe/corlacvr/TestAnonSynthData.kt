package org.cryptobiotic.rlauxe.corlacvr

import org.cryptobiotic.rlauxe.corlaInput.Colorado2026PwithCvrs
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.io.File
import kotlin.test.Test

class TestAnonSynthData {
    val INPUT_DIR = "/home/stormy/datadrive/github/nealmcb/anonymize_cvr/testCases/unredacted"
    val OUTPUT_DIR = "/home/stormy/datadrive/github/nealmcb/anonymize_cvr/testCases/redactedByPrecinct"
    val baseOut = "/home/stormy/dev/github/rla/rlauxe/cases/src/test/data/anon/synth/precinct"

    /*
    @Test
    fun testRareStyles() {
        val output = "$baseOut/2026/$county.csv"

        println("read unredacted cvrs from ${INPUT_DIR}")
        val anon = Anonomicer(readCorlaCvrs(INPUT_DIR))
        val redactedFile = "$baseOut/2026p/$county.csv"
        anon.redact(redactedFile)

        // - Morgan County ballot 102-15-5 — a Libertarian ballot, confirmed correctly starred/redacted
        // (rare "03-LBR" ballot style). This is a real, already-verified "should be redacted, was redacted"
        // gold-standard case — use it as a golden-file regression fixture.


        var countNcards = 0
        val actual = File(redactedFile).readLines()
        actual.forEach { line ->
            if (line.contains("*,*")) {
                println(line)
                countNcards++
            }
            if (line.startsWith("AGGREGATED")) println(line)
        }

        // val redactedMorganCvrs = readCorlaCvrs(redactedFile, redaction = Redaction())
        // compareCvrEquivilent(countyInput.cvrsSource, redactedFile)
    } */
}