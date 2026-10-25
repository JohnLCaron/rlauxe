package org.cryptobiotic.rlauxe.corlacvr

import org.cryptobiotic.rlauxe.corlaInput.Statewide2020General
import org.cryptobiotic.rlauxe.corlaInput.Statewide2020Redacted
import org.cryptobiotic.rlauxe.corlaInput.Statewide2026PwithCvrs
import org.cryptobiotic.rlauxe.corlaInput.isWriteIn
import kotlin.test.Test

class TestCanonicalMatching {

    @Test
    fun testCanonicalMatching2020() {
        val input = Statewide2020General()
        val county = "Garfield"
        val countyInput = input.corlaCountyInput(county)!!
        val corlaCvrs = countyInput.readCorlaCvrs()
        val schema = corlaCvrs.schema
        schema.contests.forEach { scontest: SchemaContestInfo ->
            val canon = input.matchCanonicalContest(county, scontest.contestName)
            if (canon == null)
                println("no match for ${scontest.contestName}")
            else {
                schema.columnsFor(scontest).forEach { colInfo ->
                    if (!isWriteIn(colInfo.choiceName)) {
                        val cchoice = input.matchCanonicalCandidate(county, canon, colInfo.choiceName)
                        if (cchoice == null) {
                            println("    no match for contest='${canon.contestName}' choice='${colInfo.choiceName}'")
                            input.matchCanonicalCandidate(county, canon, colInfo.choiceName)
                        }
                    }
                }
            }
        }
        val failing = "Lookout Mounatin Water District Ballot Issue 6B"
        val canon = input.matchCanonicalContest(county, failing)
        println("$failing -> $canon")
    }

    @Test
    fun testCanonicalMatching2026() {
        val input = Statewide2026PwithCvrs()
        val county = "La Plata"
        val countyInput = input.corlaCountyInput(county)!!
        val corlaCvrs = countyInput.readCorlaCvrs()
        val schema = corlaCvrs.schema
        schema.contests.forEach { scontest: SchemaContestInfo ->
            val canon = input.matchCanonicalContest(county, scontest.contestName)
            if (canon == null) println("no match for ${scontest.contestName}") else {
                schema.columnsFor(scontest).forEach { colInfo ->
                    if (!isWriteIn(colInfo.choiceName)) {
                        val cchoice = input.matchCanonicalCandidate(county, canon, colInfo.choiceName)
                        if (cchoice == null)
                            println("    no match for contest='${canon.contestName}' choice='${colInfo.choiceName}'")
                    }
                }
            }
        }
        val failing = "Secretary of State"
        val canon = input.matchCanonicalContest(county, failing)
        println("$failing -> $canon")
    }

}