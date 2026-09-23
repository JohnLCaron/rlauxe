package org.cryptobiotic.rlauxe.corlacvr

import org.cryptobiotic.rlauxe.corlaInput.Colorado2020General
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals

class TestAnonymizeCvrs {
    val base = "/home/stormy/datadrive/github/nealmcb/anonymize_cvr/testCases/generated"
    val baseOut = "/home/stormy/dev/github/rla/rlauxe/cases/src/test/data/anon"

    val scenarios = listOf(
        "no_redaction",
        "needs_borrowing",
        "rare_unique_contest",
        "near_unanimous_fixable",
        "near_unanimous_unavoidable",
        "blocked_style",
        "ballot_type_present",
    )

    @Test
    fun testCheckAnonymizeCvrs() {
        val cvrs = "$base/near_unanimous_fixable.csv"
        AnonymizeCvrsCli.main(
            arrayOf(
                "--mode", "check",
                "-input", cvrs,
            )
        )
    }

// scenario_ballot_type_present
// has two ballot styles with same contest set [0,1], these generate a single CardStyle
// and ballot style [1] has rows with different set [0], [0,1]
// row 19 could create a second CardStyle with name "1+1" meaning  balot type 1, variant1, with different contest set
//                    type votes
// 9,1,1,9,1-1-9,P1,    1, 1,0,1,0
// 12,1,1,12,1-1-12,P1, 2, 0,1,0,1
// 19,1,1,19,1-1-19,P1, 1, 1,0,,
// could ignore BallotType field for the purpose of redaction?
// TODO fix CardStyles with same name.

    @Test
    fun testBallotType() {
        val cvrs = "$base/ballot_type_present.csv"
        val output = "$baseOut/ballot_type_present.csv"
        AnonymizeCvrsCli.main(
            arrayOf(
                "-input", cvrs,
                "-output", output,
                "--mode", "redact"
            )
        )
    }

    @Test
    fun testBlockedStyle() {
        val cvrs = "$base/blocked_style.csv"
        val output = "$baseOut/blocked_style.csv"
        AnonymizeCvrsCli.main(
            arrayOf(
                "-input", cvrs,
                "-output", output,
                "--mode", "redact"
            )
        )

    }

    @Test
    fun compareAllScenarios() {
        scenarios.forEach { scenario ->
            println("===========================================================================================")
            AnonymizeCvrsCli.main(
                arrayOf(
                    "-input", "$base/$scenario.csv",
                    "-output", "$baseOut/$scenario.csv",
                    "--mode", "redact"
                )
            )

            val actual = File("$baseOut/$scenario.csv").readLines()
            val expect = File("/home/stormy/datadrive/github/nealmcb/anonymize_cvr/testCases/converted/$scenario.csv").readLines()
            assertEquals(expect.size, actual.size)
            val combine = expect.zip(actual)
            combine.forEach {
                assertEquals(it.first, it.second)
            }
            println("success")
        }
    }

    @Test
    fun testAnonymizeOne() {
        val input = Colorado2020General()
        val county = "Routt"
        val countyInput = input.corlaCountyInput(county)!!
        val output = "$baseOut/2020/$county.csv"

        val anon = Anonymize(countyInput.readCorlaCvrs(), 10, output)
        anon.execute_redact()

        val actual = File(output).readLines()
        actual.forEach { line ->
            if (line.startsWith("AGGREGATED")) println(line)
        }

        val expect = File("/home/stormy/datadrive/github/nealmcb/anonymize_cvr/testCases/converted/$county.csv").readLines()
        expect.forEachIndexed { idx, line ->
            if (idx > 4) assertEquals(line, actual[idx])
        }
        println("success")
    }

    // needs redaction: Arapahoe (301 s), Denver (517 s), La Plata(640 ms), Larimer (2 sec), Mesa (32 s), Routt (6), Saguache (2), Sedgwick(214 ms)
    @Test
    fun testAnonymize2020Cvrs() {
        val stopwatch = Stopwatch()
        val input = Colorado2020General()
        val countNredacted = mutableMapOf<String, Int>()
        input.counties().forEach { county ->
            val countyInput = input.corlaCountyInput(county)!!
            println("===========================================================================================")
            println("County $county from ${countyInput.cvrsSource}")
            AnonymizeCvrsCli.main(
                arrayOf(
                    "-input", countyInput.cvrsSource,
                    "-output", "$baseOut/2020/$county.csv",
                    "--mode", "redact"
                )
            )

            var countNcards = 0
            val actual = File("$baseOut/2020/$county.csv").readLines()
            actual.forEach { line ->
                if (line.contains("*,*")) countNcards++
                if (line.startsWith("AGGREGATED")) println(line)
            }
            if (countNcards > 0) print("countNcards = $countNcards ")
            println("success")
            countNredacted[county] = countNcards
        }
        writeRedactionCount("$baseOut/2020/redacted.csv", countNredacted)
        println("that took $stopwatch")
    }
}

fun writeRedactionCount(outputFilename: String, countNredacted: Map<String, Int>) {
    // misc data by county
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
    writer.write("    county, addRedactedCards\n")
    countNredacted.toSortedMap().forEach {
        writer.write("${sfn(it.key, 10)}, ${nfn(it.value, 7)}\n")
    }
    writer.close()
    println("wrote ${countNredacted.size} redactionCount to $outputFilename")
}

fun readRedactionCount(filename: String): Map<String, Int> {
    // misc data by county
    val countNredacted = mutableMapOf<String, Int>()
    if (!Path(filename).exists()) return countNredacted

    val lines = File(filename).readLines()
    lines.forEachIndexed { idx, line ->
        if (idx > 0) {
            val tokens = line.split(",")
            val county = tokens[0].trim()
            val nredact = tokens[1].trim().toInt()
            countNredacted[county] = nredact
        }
    }
    return countNredacted
}