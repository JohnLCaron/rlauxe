package org.cryptobiotic.rlauxe.corlacvr

import org.cryptobiotic.rlauxe.corlaInput.CheckCvrRedaction
import org.cryptobiotic.rlauxe.corlaInput.RedactionCount
import org.cryptobiotic.rlauxe.corlaInput.readCorlaCvrs
import org.cryptobiotic.rlauxe.persist.validateOutputDirOfFile
import kotlin.test.Test

class TestAnonScenarios {
    val PYTHON_INPUT_DIR = "/home/stormy/datadrive/github/nealmcb/anonymize_cvr/testCases/unredacted"
    val PYTHON_OUTPUT_DIR = "/home/stormy/datadrive/github/nealmcb/anonymize_cvr/testCases/redactedByPrecinct"
    val baseOut = "/home/stormy/dev/github/rla/rlauxe/cases/src/test/data/anon/"
    val baseOutPrecinct = "/home/stormy/dev/github/rla/rlauxe/cases/src/test/data/anon/redactedByPrecinct"

    val scenarios = listOf(
        "no_redaction",
        "needs_borrowing",
        "rare_unique_contest",
        "near_unanimous_fixable",
        "near_unanimous_unavoidable",
        "blocked_style",
        "ballot_type_present",
    )

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

    /*
        Rare style (President+JudgeRetention, 8 ballots all voting Alice/Yes) becomes
    near-unanimous in President after borrowing 2 Bob voters.  balance_unanimity()
    then borrows 1 more Bob to push other_votes above NEAR_UNANIMOUS_THRESHOLD.
    Judge Retention has only one choice (Yes) and must not be flagged.

    Styles differ: rare = {President, Judge Retention}, common = {President, Senate,
    Judge Retention}.  The extra Senate contest makes them distinct style strings.

    Expected: near-unanimous President detected then fixed; Judge Retention skipped.
    Command: python anonymize_cvr.py testCases/generated/near_unanimous_fixable.csv /tmp/out.csv

 Writing test CVR files to /home/stormy/datadrive/github/nealmcb/anonymize_cvr/testCases/redactedByPrecinct/near_unanimous_fixable.csv
*** Pass 1: Looking for rare ballot styles.


  Rare ballot style/precinct combinations (1 of 2 total):
    8 ballot(s)  [precinct "P1", style #101]

Redaction is needed.
*** Pass 2: Building the aggregate row.

ndonors = 20
  Rare ballots: 8.
  Borrowing from common styles to satisfy these requirements:
  - the ballot aggregation must contain at least 10 ballots.
  - every contest in the rare styles must appear on at least 10 ballots in the aggregation.

  Aggregate has 8 ballot(s); need 2 more to reach minimum of 10.
  Contests below minimum:
    'Judge Retention': needs 2 more ballot(s)
    'President': needs 2 more ballot(s)

  Selecting ballots to borrow from common styles (this can take a few minutes)...
  Ballots borrowed for minimum counts: 2

*** Balancing near-unanimous contests.

  Make sure that the following constraint is met:
  - No contest in the aggregate may be near-unanimous. 'Near-unanimous'
    means all but 2 votes go to a single choice.

    'President': 'Alice' has 8 out of 10 votes

  Borrowed 3 contrasting ballot(s).
  Total ballots borrowed for unanimity balancing: 3

*** Pass 2 complete.
  Ballots from rare styles/precincts: 8
  Ballots borrowed from common styles: 5
  Total ballots in aggregate: 13

*** Pass 3: Writing output.
  Tally verification passed.
  Output written to /home/stormy/datadrive/github/nealmcb/anonymize_cvr/testCases/redactedByPrecinct/near_unanimous_fixable.csv.

Done. near_unanimous_fixable.csv files written.

     */
    @Test
    fun testOneScenario() {
        val scenario = "near_unanimous_fixable"
        val cvrFile = "$PYTHON_INPUT_DIR/$scenario.csv"
        val output = "$baseOutPrecinct/$scenario.csv"
        validateOutputDirOfFile(output)

        Anonomicer(cvrFile).writeRedactedFile(output)
            .verify(readCountyCvrs(output))

        // println ("------------ CheckCvrRedaction")
        // CheckCvrRedaction(cvrFile, output)

        compareTwoRedactions("$PYTHON_OUTPUT_DIR/$scenario.csv", output)
    }

    @Test
    fun compareAllScenarios() {
        var countFail = 0
        scenarios.forEach { scenario ->
            println("===========================================================================================")
            println(scenario)
            val cvrs = "$PYTHON_INPUT_DIR/$scenario.csv"
            val output = "$baseOutPrecinct/$scenario.csv"
            Anonomicer(cvrs).writeRedactedFile(output)
                .verify(readCountyCvrs(output))

            CheckCvrRedaction(cvrs, output)

            if (compareTwoRedactions("$PYTHON_OUTPUT_DIR/$scenario.csv", output).fail) countFail++
        }
            /* var fail = false

            // gonna fail
            val combine = expect.cvrs().zip(actual.cvrs())
            combine.forEach {
                val err = compareRowEquivilentNoFail(it.first, it.second)
                if (err.isNotEmpty()) {
                    println(err)
                    fail = true
                }
            }
            if (fail) println("***** FAIL $scenario") else println("Success $scenario")
            if (fail) countFail++
        } */
        println("failures = $countFail out of ${scenarios.size}")
    }
}

fun compareTwoRedactions(expectFile: String?, actualFile: String, show: Boolean = false): RedactionCount {
    println("expect $expectFile actual $actualFile")
    val actual = readCountyCvrs(actualFile)
    var fail = false

    if (expectFile != null) {
        val expect = readCountyCvrs(expectFile)

        if (expect.nrows() != actual.nrows()) {
            fail = true
            println("*** nrows: expect ${expect.nrows()} != ${actual.nrows()} actual")
        }
        if (expect.redaction().nredactedCvrs() != actual.redaction().nredactedCvrs()) {
            fail = true
            println("*** nredacted: expect ${expect.redaction().nredactedCvrs()} != ${actual.redaction().nredactedCvrs()} actual")
        }

        if (show) {
            println("\nexpect\n${tabulate(expect)}")
            println("\nactual\n${tabulate(actual)}")
        }
        return RedactionCount(expect.redaction().nredactedCvrs(), actual.redaction().nredactedCvrs(), fail)
    }

    return RedactionCount(-1, actual.redaction().nredactedCvrs(), fail)
}
