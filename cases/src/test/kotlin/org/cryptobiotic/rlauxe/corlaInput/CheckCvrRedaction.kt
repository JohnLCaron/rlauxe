package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.corlaCounty.makeContestInfo
import org.cryptobiotic.rlauxe.corlacvr.Anonomicer
import org.cryptobiotic.rlauxe.corlacvr.Anonomicer.Companion.NEAR_UNANIMOUS_THRESHOLD
import org.cryptobiotic.rlauxe.corlacvr.checkEquivilentVotes
import org.cryptobiotic.rlauxe.corlacvr.findPrecinctStyles
import org.cryptobiotic.rlauxe.corlacvr.readCountyCvrsFromFile
import org.cryptobiotic.rlauxe.corlacvr.tabulate
import org.cryptobiotic.rlauxe.votedatabase.votedatabase
import kotlin.test.Test
import kotlin.test.assertTrue

class TestCvrRedaction {
    @Test
    fun testExcelMacro() {
        CheckCvrRedaction(
            "$votedatabase/cvr/Colorado/Sedgwick/sedgewickTestCvr.csv",
            "$votedatabase/cvr/Colorado/Sedgwick/sedgewickTestCvr_Redacted.csv",
            )
    }

    @Test
    fun testAnonymizePython() {
        CheckCvrRedaction(
            "$votedatabase/cvr/Colorado/Denver/cvr.csv",
            "/home/stormy/datadrive/github/nealmcb/anonymize_cvr/testCases/precinct/Denver.csv",
        )
    }

    @Test
    fun testAnonymizeKotlin() {
        CheckCvrRedaction(
            "$votedatabase/cvr/Colorado/Denver/cvr.csv",
            "/home/stormy/dev/github/rla/rlauxe/cases/src/test/data/anon/2020/Denver.csv",
        )
    }
}

class CheckCvrRedaction(
    val unredactedfile: String,
    val redactedFile: String,
    val show: Boolean = false,
) {
    init {
        println(unredactedfile)
        println(redactedFile)
        val unredactedCvrs = readCountyCvrsFromFile(unredactedfile, showHeaders = false, showSchema = false)

        val infos = unredactedCvrs.makeContestInfo()
        val unredactedGroups = findPrecinctStyles(unredactedCvrs, infos)
        if (show) {
            println("unredacted precinctStyles")
            unredactedGroups.sortedBy { it.ncards }.forEach {
                if (it.ncards < 10) println("  $it")
            }
        }
        val unredactedMinGroupSuze = unredactedGroups.minOf { it.ncards }
        println("  unredacted precinctStyles min cards = $unredactedMinGroupSuze")
        println("  unredacted cardStyle min cards = ${unredactedCvrs.cardStyles().minOf { it.ncards }}")

        val redactedCvrs = readCountyCvrsFromFile(redactedFile, showHeaders = false, showSchema = false)
        println("\nredactedCvrs")
        println("  countBlankPrecincts = ${redactedCvrs.countBlankPrecincts()}")
        println("  ballotStyleUnique = ${redactedCvrs.ballotStyleUnique()}")
        println("  ballotStyleMin = ${redactedCvrs.cardStyles().minOf { it.ncards }}")

        val redaction = redactedCvrs.redaction()
        println("  nredacted Cvrs (${redaction.redactedRows().size})")
        if (show) redaction.redactedRows().forEach { println("    headerValues=${it.headerValues}") }
        println("  aggregations")
        redaction.groups().forEach { println("       ${it.firstCsv}") }
        println()

        val precinctStyles = findPrecinctStyles(redactedCvrs, infos) // TODO styleOnly ??
        if (show) {
            println("redacted precinctStyles")
            precinctStyles.sortedBy { it.ncards }.forEach {
                // it.convert(redactedCvrs.cardStyleMap())
                if (it.ncards < 10) println("  $it")
            }
        }
        val redactedMinGroupSize = precinctStyles.minOf { it.ncards }
        println("  redacted precinctStyles min cards = $redactedMinGroupSize")
        println("  redacted cardStyle min cards = ${redactedCvrs.cardStyles().minOf { it.ncards }}")
        if (redactedMinGroupSize >= Anonomicer.MIN_BALLOTS_DEFAULT)
            println("1) redactedMinGroupSize >= ${Anonomicer.MIN_BALLOTS_DEFAULT}")
        else
            println("1) FAIL redactedMinGroupSize ($redactedMinGroupSize) < ${Anonomicer.MIN_BALLOTS_DEFAULT}")


        ///////////////////////////////////////////////////////
        val unredactedTabs = tabulate(unredactedCvrs, infos)
        val redactedTabs = tabulate(redactedCvrs, infos)
        val usum = unredactedTabs.sum()
        val rsum = redactedTabs.sum()
        var fail = false
        usum.forEach { (id, utab) ->
            val rtab = rsum[id]!!
            if (!checkEquivilentVotes(utab.votes, rtab.votes)) {
                println(utab)
                println(rtab)
                println()
                fail = true
            }
        }
        assertTrue(!fail)
        println("2) contest tabulations are the same before and after redaction")

        /* this can be impossible to tell; but could do it when redacting
        // how do we know what are the rare contests ??
        redactedTabs.redactedTabs.values.forEach { tab ->
            if (tab.ncards() < MIN_BALLOTS_DEFAULT)
                println("contest ${tab.contestId}: has ncards=${tab.ncards()} < ${MIN_BALLOTS_DEFAULT} min ballots")
            // assertTrue(tab.ncards() >= MIN_BALLOTS_DEFAULT, "contest ${tab.contestId}: has ncards=${tab.ncards()} < ${MIN_BALLOTS_DEFAULT} min ballots")
        }
        // println("all rare contests have ncards >= $MIN_BALLOTS_DEFAULT") */

        redactedTabs.redactedTabs.values.forEach { tab ->
            if (infos[tab.contestId]!!.candidateNames.size > 1) {
                val leadingVotes: Map.Entry<Int, Int> = tab.votes.maxBy { it.value }
                val nonleadingVotes = tab.nvotes() - leadingVotes.value
                if (nonleadingVotes < NEAR_UNANIMOUS_THRESHOLD)
                    println("    contest ${tab.contestId}: has nonleadingVotes=${nonleadingVotes} < ${NEAR_UNANIMOUS_THRESHOLD} NEAR_UNANIMOUS_THRESHOLD")
            }
        }
        println("3) all rare contests are not within $NEAR_UNANIMOUS_THRESHOLD votes of unanimity")
    }

}

/* unique precinct/ballotType. But we cant rely on ballotType to have unique contestSet
// So better might be
class PrecinctStyles(val ballotType: String, val precinctPortion: String) {
    val contests = mutableMapOf<Set<Int>, Int>() // count cards for unique contests within the precinct
    var ncards = 0
    var styleMap = emptyMap<String, Int>()

    fun convert(cardStyleMap: Map<CardStyleId, CvrCardStyle>) {
        styleMap = contests.mapKeys { cardStyleMap[CardStyleId(ballotType, it.key)]?.ballotType ?: "unknown" }
    }

    override fun toString(): String {
        return "PrecinctStyles(ballotType='$ballotType', precinctPortion='$precinctPortion', nunique=${contests.size}, ncards=$ncards, styleMap=$styleMap)"
    }
}

fun findPrecinctStyles(corlaCvrs: CorlaCountyCvrsIF): List<PrecinctStyles> {
    val styleCounters = mutableMapOf<Pair<String, String>, PrecinctStyles>()
    corlaCvrs.cvrs().forEach { cvr ->
        val id = Pair(cvr.ballotType, cvr.precinctPortion ?: "none")
        val unique = styleCounters.getOrPut(id) { PrecinctStyles(id.first, id.second) }
        val count = unique.contests.getOrDefault(cvr.contests(), 0)
        unique.contests[cvr.contests()] = count + 1
        unique.ncards++
    }
    styleCounters.values.forEach { it.convert(corlaCvrs.cardStyleMap()) }

    return styleCounters.values.toList()
} */

/////////////////////////////////