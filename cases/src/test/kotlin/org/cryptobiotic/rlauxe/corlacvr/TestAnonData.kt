package org.cryptobiotic.rlauxe.corlacvr

import org.cryptobiotic.rlauxe.corlaInput.Colorado2020General
import org.cryptobiotic.rlauxe.corlaInput.Colorado2026PwithCvrs
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

class TestAnonData {
    val python = "/home/stormy/datadrive/github/nealmcb/anonymize_cvr/testData/2020"
    val base2026Precinct = "/home/stormy/dev/github/rla/rlauxe/cases/src/test/data/anon/2026/precinct"
    val base2020Precinct = "/home/stormy/dev/github/rla/rlauxe/cases/src/test/data/anon/2020/precinct"

    /*
    *** Pass 1: Looking for rare ballot styles.

    WARNING: Leakage: ballot types [1, 2] all share the same contest pattern

      Rare ballot style/precinct combinations (4 of 21 total):
        2 ballot(s)  [precinct "3356255004 (3356255004)", ballot type: "1", style #111111111111111111111111110]
        3 ballot(s)  [precinct "3356255006 (3356255006)", ballot type: "1", style #111111111111111111111111110]
        4 ballot(s)  [precinct "3356255002 (3356255002)", ballot type: "2", style #111111111111111111111111111]
        4 ballot(s)  [precinct "3356255003 (3356255003)", ballot type: "1", style #111111111111111111111111110]

    Redaction is needed.
    *** Pass 2: Building the aggregate row.

    ndonors = 294
      Rare ballots: 13.
      Borrowing from common styles to satisfy these requirements:
      - the ballot aggregation must contain at least 10 ballots.
      - every contest in the rare styles must appear on at least 10 ballots in the aggregation.

      Contests below minimum:
        'Colorado River Water Conservation District Ballot Issue 7A: (Vote For=1)': needs 6 more ballot(s)

      Selecting ballots to borrow from common styles (this can take a few minutes)...
      Ballots borrowed for minimum counts: 6

    *** Balancing near-unanimous contests.

      Make sure that the following constraint is met:
      - No contest in the aggregate may be near-unanimous. 'Near-unanimous'
        means all but 2 votes go to a single choice.

        'Presidential Electors (Vote For=1)': 'Donald J. Trump / Michael R. Pence' has 4 out of 5 votes
        'United States Senator (Vote For=1)': 'Cory Gardner' has 4 out of 5 votes
        'Representative to the 117th United States Congress-District 3 (Vote For=1)': 'Lauren Boebert' has 2 out of 3 votes
        'State Board of Education Member - Congressional District 3 (Vote For=1)': 'Joyce Rankin' has 2 out of 3 votes
        'State Senator - District 35 (Vote For=1)': 'Cleave Simpson' has 1 out of 2 votes
        'State Representative - District 62 (Vote For=1)': 'Logan Taggart' has 1 out of 2 votes
        'Saguache County Commissioner District 1 (Vote For=1)': 'Richard Pascoe' has 2 out of 3 votes
        'Colorado Supreme Court Justice - Hart (Vote For=1)': 'YES' has 1 out of 1 votes
        'Colorado Supreme Court Justice - Samour (Vote For=1)': 'YES' has 1 out of 1 votes
        'Colorado Court of Appeals Judge - Tow (Vote For=1)': 'YES' has 1 out of 1 votes
        'Colorado Court of Appeals Judge - Welling (Vote For=1)': 'YES' has 1 out of 1 votes
        'District Court Judge - 12th Judicial District - Gonzales (Vote For=1)': 'NO' has 1 out of 2 votes
        'District Court Judge - 12th Judicial District - Hopkins (Vote For=1)': 'YES' has 1 out of 1 votes
        'Amendment C (Constitutional) (Vote For=1)': 'NO / AGAINST' has 12 out of 13 votes
        'Proposition 114 (Statutory) (Vote For=1)': 'NO / AGAINST' has 12 out of 14 votes

      Borrowed 9 contrasting ballot(s).
        'Representative to the 117th United States Congress-District 3 (Vote For=1)': 'Diane E. Mitsch Bush' has 6 out of 8 votes
        'State Board of Education Member - Congressional District 3 (Vote For=1)': 'Mayling Simpson' has 7 out of 9 votes
        'State Senator - District 35 (Vote For=1)': 'Carlos R. Lopez' has 7 out of 8 votes
        'State Representative - District 62 (Vote For=1)': 'Donald E. Valdez' has 7 out of 8 votes
        'Saguache County Commissioner District 1 (Vote For=1)': 'Thomas D. McCracken' has 7 out of 9 votes
        'Colorado Court of Appeals Judge - Tow (Vote For=1)': 'NO' has 5 out of 7 votes
        'Colorado Court of Appeals Judge - Welling (Vote For=1)': 'NO' has 6 out of 7 votes

      Borrowed 3 contrasting ballot(s).
      Total ballots borrowed for unanimity balancing: 12

    *** Pass 2 complete.
      Ballots from rare styles/precincts: 13
      Ballots borrowed from common styles: 18
      Total ballots in aggregate: 31

    *** Pass 3: Writing output.
      Tally verification passed.
      Output written to /home/stormy/datadrive/github/nealmcb/anonymize_cvr/testData/2020/Saguache.csv.
    Elapsed time: 0.8630 seconds
     */

    @Test
    fun testAnonymizeOne() {
        val stopwatch = Stopwatch()

        val input = Colorado2020General()
        val county = "Phillips"
        val countyInput = input.corlaCountyInput(county)!!
        println("County $county from ${countyInput.cvrsSource}")

        val output = "$base2026Precinct/$county.csv"

        val cvrs = countyInput.readCorlaCvrs()
        Anonomicer(cvrs, styleOnly=false).redact(output)
        println("that took $stopwatch")

        //println("---------------------------------")
        //CheckCvrRedaction(countyInput.cvrsSource, output)
        println("---------------------------------")
        println("python vs rlauxe")
        compareTwoRedactions("$python/$county.csv", output, show=true)
    }

    // needs redaction: Arapahoe (301 s), Denver (517 s), La Plata(640 ms), Larimer (2 sec), Mesa (32 s), Routt (6), Saguache (2), Sedgwick(214 ms)
    @Test
    fun testAnonymize2020Cvrs() {
        val stopwatch = Stopwatch()
        val input = Colorado2020General()
        val countNredacted = mutableMapOf<String, CompareWithPython>()
        input.counties().filter{ it != "Garfield" && it != "El Paso" }.forEach { county ->
            try {
                val countyInput = input.corlaCountyInput(county)!!
                println("===========================================================================================")
                println("County $county from ${countyInput.cvrsSource}")

                val styleOnly = county == "Arapahoe"
                val output = "$base2020Precinct/$county.csv"
                val anon = Anonomicer(countyInput.readCorlaCvrs(), styleOnly=styleOnly)
                anon.redact(output)

                println("---------------------------------")
                println("python vs raire")
                countNredacted[county] = compareTwoRedactions("$python/$county.csv", output, show = true)
            } catch (e: Throwable) {
                println("failed on $county: ${e.message}")
            }
        }
        writeRedactionCount("$base2020Precinct/redactedCountByPrecinct.csv", countNredacted)
        println("that took $stopwatch")
    }

    @Test
    fun testAnonymize2026pCvrs() {
        val stopwatch = Stopwatch()
        val input = Colorado2026PwithCvrs()
        val countNredacted = mutableMapOf<String, CompareWithPython>()
        input.counties().forEach { county ->
            val countyInput = input.corlaCountyInput(county)!!
            println("===========================================================================================")
            println("County $county from ${countyInput.cvrsSource}")

            val output = "$base2026Precinct/$county.csv"
            val anon = Anonomicer(countyInput.readCorlaCvrs())
            anon.redact(output)

            println("---------------------------------")
            println("python vs raire")
            countNredacted[county] = compareTwoRedactions(null, output, show = true)

        }
        writeRedactionCount("$base2026Precinct/redactedCountByPrecinct.csv", countNredacted)
        println("that took $stopwatch")
    }
}

fun compareCvrEquivilent(cvrFile1: String, cvrFile2: String) {
    println("compare $cvrFile1")
    println("     to $cvrFile2")

    val corlaCvr1 = readCorlaCvrs(cvrFile1, redaction = Redaction())
    val corlaCvr2 = readCorlaCvrs(cvrFile2, redaction = Redaction())

    val cvrs1 = corlaCvr1.cvrs()
    val cvrs2 = corlaCvr2.cvrs()
    assertEquals(corlaCvr1.nrows(), corlaCvr2.nrows())
    cvrs1.zip(cvrs2).forEach { (cvr1, cvr2) ->
        compareRowEquivilent(cvr1, cvr2)
    }

    //     fun groups(): List<RedactedGroup>  // Aggregated redactions: make into pools
    //    fun redactedRows(): List<CvrRow>  // row redactions given in the CVR file
    //    fun nredactedCvrs()
    assertEquals(corlaCvr1.redaction().groups(), corlaCvr2.redaction().groups())
    assertEquals(corlaCvr1.redaction().nredactedCvrs(), corlaCvr2.redaction().nredactedCvrs())

    corlaCvr1.redaction().redactedRows().zip(corlaCvr2.redaction().redactedRows()).forEach { (g1, g2) ->
        compareRowEquivilent(g1, g2)
    }
}

fun compareRowEquivilent(row1: CvrRow, row2: CvrRow) {
    assertEquals(row1.ballotType, row2.ballotType)
    assertEquals(row1.imprintedId, row2.imprintedId)
    assertEquals(row1.contestVotes, row2.contestVotes)
}

fun compareRowEquivilentNoFail(row1: CvrRow, row2: CvrRow) = buildString {
    if (row1.ballotType != row2.ballotType) append("ballotType: ${row1.ballotType} != ${row2.ballotType}")
    if (row1.imprintedId != row2.imprintedId) append("imprintedId: ${row1.imprintedId} != ${row2.imprintedId}")
    if (row1.contestVotes != row2.contestVotes) append("contestVotes: ${row1.contestVotes} != ${row2.contestVotes}")
}

// feed WriteCountyInputData
fun writeRedactionCount(outputFilename: String, countNredacted: Map<String, CompareWithPython>) {
    // misc data by county
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
    writer.write("    county, pythonRedactions, rlauxeRedactions\n")
    countNredacted.toSortedMap().forEach {
        writer.write("${sfn(it.key, 10)}, ${nfn(it.value.pythonNredact, 7)}, ${nfn(it.value.rlauxeNredact, 7)}\n")
    }
    writer.close()
    println("wrote ${countNredacted.size} redactionCount to $outputFilename")
}

fun readRedactionCount(filename: String): Map<String, CompareWithPython> {
    // misc data by county
    val countNredacted = mutableMapOf<String, CompareWithPython>()
    if (!Path(filename).exists()) return countNredacted

    val lines = File(filename).readLines()
    lines.forEachIndexed { idx, line ->
        if (idx > 0) {
            val tokens = line.split(",")
            val county = tokens[0].trim()
            val predact = tokens[1].trim().toInt()
            val rredact = tokens[2].trim().toInt()
            countNredacted[county] = CompareWithPython(predact, rredact, false)
        }
    }
    return countNredacted
}