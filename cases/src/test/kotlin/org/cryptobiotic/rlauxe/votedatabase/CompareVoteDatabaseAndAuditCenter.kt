package org.cryptobiotic.rlauxe.votedatabase

import org.cryptobiotic.rlauxe.auditcenter.CanonicalContest
import org.cryptobiotic.rlauxe.corlaInput.Statewide2020General
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.corlacvr.CorlaCountyCvrsIF
import org.cryptobiotic.rlauxe.corlacvr.Garfield2020CountyCvrs
import org.cryptobiotic.rlauxe.corlaCounty.makeContestInfo
import org.cryptobiotic.rlauxe.corlacvr.readCountyCvrsFromFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.text.split

// see Corla2020notes

class CompareVoteDatabaseAndAuditCenter {

    @Test
    fun testRedactionProblem() {
        val errs = matchNames("Larimer", Statewide2020General())
        assertEquals(0, errs)
    }

    @Test
    fun testGarfield() { // also Roosevelt
        val errs = matchNames("Garfield", Statewide2020General())
        assertEquals(0, errs)
    }

    @Test
    fun problem() { // redaction
        matchNames("Douglas", Statewide2020General())
    }

    @Test
    fun testSuggest() {
        val contest = "Candidates for Town Council"
        val suggest = suggest("Eagle", contest, Statewide2020General())
        println("$contest -> $suggest")
    }

    @Test
    fun allColorado2020Counties() {
        val input = Statewide2020General()
        val errCount = emptyMap<String, Int>()
        input.counties().forEach { county ->
            matchNames(county, input)
        }
    }

    fun matchNames(county: String, input: StatewideInput): Int {
        val countyInput = input.corlaCountyInput(county)
        if (countyInput == null) {
            println("\n-----------------------------------")
            println("$county countyInput is null")
            return -1
        }
        if (!countyInput.hasCvrs()) {
            println("\n-----------------------------------")
            println("$county has no cvrs")
            return -1
        }
        return matchNames(county, countyInput.readCorlaCvrs(), input)
    }


    fun matchNames(county: String, export: CorlaCountyCvrsIF, input: StatewideInput): Int {
        println("\n-----------------------------------")
        println("county=$county csvfile = ${export.inputSource}")

        val sinfoList = export.makeContestInfo().values.toList()
        var errs = 0

        // test every export contest has a match in canon, along with each choice
        val match = mutableMapOf<String, String>()
        sinfoList.map { sinfo ->
            val contest: CanonicalContest? = input.matchCanonicalContest(county, sinfo.name)
            if (contest == null) {
                println("   \"${sinfo.name}\" -> \"${suggest(county, sinfo.name, input)}\"")
                errs++
            } else {
                match[contest.contestName] = sinfo.name
                // show the differences
                if (contest.contestName.startsWith("Pres") || contest.contestName.startsWith("United")) {
                    val candidateSet = contest.choices.toSet()
                    sinfo.candidateNames.keys.filter { it != "Write-In" }.forEach { cand ->
                        if (!candidateSet.contains(cand))
                            println("   no matches for original candidate '$cand' for contest '${contest.contestName}' county '$county'")
                    }
                }

                sinfo.candidateNames.keys.filter { it != "Write-In"}.forEach { cand ->
                    val match = input.matchCanonicalCandidate(county, contest, cand)
                    if (match == null) {
                        println("*** didnt match candidate '$cand' for contest '${contest.contestName}'")
                        contest.choices.forEach { println(" $it")}
                        errs++
                    }
                }
            }
        }

        // test every canon contest has a match in export
        val countyContestTabs = input.countyTabsAllContests()[county]!!
        countyContestTabs.contests.keys.forEach { canonContest ->
            if (match[canonContest] == null) {
                val match2 = sinfoList.find { it.name == canonContest } // probably not nneeded
                if (match2 == null) {
                    println("*** didnt find canonicalContest named '${canonContest}' in export file")
                    errs++
                }
            }
        }

        return errs
    }
}

fun readCvrExportsFromFile(county: String, cvrFile: String): CorlaCountyCvrsIF {
    return if (county == "Garfield") Garfield2020CountyCvrs(cvrFile) else
        readCountyCvrsFromFile(cvrFile)
}

fun suggest(county: String, contest: String, input: StatewideInput): String {
    val countyContestTabs = input.countyTabsAllContests()[county]
    if (countyContestTabs == null) return "unknown"

    for (canonContest in countyContestTabs.contests.keys) {
        if (canonContest.contains(contest)) return canonContest
        if (canonContest.contains(extractIssueName(contest))) return canonContest
        if (canonContest.contains(mutatis(contest))) return canonContest
        if (canonContest.contains(judge(contest))) return canonContest
    }
    return "unknown"
}

fun extractIssueName(name: String): String {
    val toks = name.split(" ", ":").filter{ it != "No."}
    for (idx in 0 until toks.size-2) {
        if (toks[idx] == "Ballot" && (toks[idx+1] == "Issue" || toks[idx+1] == "Question"))
            return "${toks[idx]} ${toks[idx+1]} ${toks[idx+2]}"
    }
    for (idx in 0 until toks.size-1) {
        if (toks[idx] == "Town" && toks[idx+1] == "Council")
            return "${toks[idx]} ${toks[idx+1]}"
    }
    return "gobbleygook"
}

fun mutatis(name: String): String {
    val tok = name.split("-").map{ it.trim() }
    if (tok[0] == "Colorado Supreme Court Justice") return "Justice of the Colorado Supreme Court - ${tok[1]}"
    return "gobbleygook"
}

fun judge(name: String): String {
    if (name.contains("County Court -"))
        return name.replace("County Court -", "County Court Judge -")

    return "gobbleygook"
}