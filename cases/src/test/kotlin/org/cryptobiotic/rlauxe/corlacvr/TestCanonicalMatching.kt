package org.cryptobiotic.rlauxe.corlacvr

import org.cryptobiotic.rlauxe.corlaInput.Statewide2020General
import org.cryptobiotic.rlauxe.corlaInput.isWriteIn
import kotlin.test.Test

class TestCanonicalMatching {

    @Test
    fun testCanonicalMatching() {
        val input = Statewide2020General()
        val county = "Jefferson"
        val countyInput = input.corlaCountyInput("Jefferson")!!
        val corlaCvrs = countyInput.readCorlaCvrs()
        val schema = corlaCvrs.schema
        schema.contests.forEach { scontest: SchemaContestInfo ->
            val canon = input.matchCanonicalContest(county, scontest.contestName)
            if (canon == null) println("no match for ${scontest.contestName}") else {
                val start = scontest.startCol
                repeat(scontest.ncols) { idx ->
                    val colInfo = schema.columns[start + idx]
                    if (!isWriteIn(colInfo.choiceName)) {
                        val cchoice = input.matchCanonicalCandidate(county, canon, colInfo.choiceName)
                        if (cchoice == null) println("    no match for ${canon.contestName} ${colInfo.choiceName}")
                    }
                }
            }
        }
        val failing = "Lookout Mounatin Water District Ballot Issue 6B"
        val canon = input.matchCanonicalContest(county, failing)
        println("$failing -> $canon")
    }

}