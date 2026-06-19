package org.cryptobiotic.rlauxe.votedatabase

import org.cryptobiotic.rlauxe.auditcenter.Colorado2020General
import org.cryptobiotic.rlauxe.corla.ColoradoInput
import org.cryptobiotic.rlauxe.dominion.DominionCvrExport
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportReader
import org.cryptobiotic.rlauxe.dominion.makeContestInfo
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.text.split

/*
votedatabase missing San Juan;  cant read Garfield (yet); has Monroe and Roosevelt from some other state
  Baca has Huerfano, remove
  Las Animas has 106 cards out of ~8000
Garfield has an older format, not yet read

auditcenter tabulate_county.csv missing Gunnison, San Juan

overall we are missing Baca, Garfield, Gunnison, San Juan counties

redactions: Boulder, Doloros, Pitkin, possibly Jefferson


*/

val votedatabase = "/home/stormy/datadrive/votedatabase"
val colorado2020 = "$votedatabase/cvr/Colorado"

class CompareVoteDatabaseAndAuditCenter {

    @Test
    fun testRedactionProblem() {
        val errs = matchNames("Larimer", "$colorado2020/Larimer/cvr.csv", Colorado2020General())
        assertEquals(0, errs)
    }

    @Test
    fun testGarfield() { // also Roosevelt
        val errs = matchNames("Garfield", "$colorado2020/Garfield/cvr.csv", Colorado2020General())
        assertEquals(0, errs)
    }

    @Test
    fun problem() { // redaction
        // val filename = "$colorado2020/Lincoln/cvr.csv"  had a misquoted quote that commons-csv was barfing on
        /* val filename = "$colorado2020/Roosevelt/cvr.csv" // might be older version of export file ??
        val reader: BufferedReader = File(filename).bufferedReader()
        var idx = 0
        while (idx < 10) {
            var line = reader.readLine()
            if (line == null) break
            println("$idx ${line.length}")

            val tokens = line.split(",")
            tokens.forEach {
                // if (it.contains("\""))
                    println(it)
            }
            idx++
        }
        reader.close() */

        matchNames("Roosevelt", "$colorado2020/Roosevelt/cvr.csv", Colorado2020General())
        // assertEquals(0, errs)
    }

    @Test
    fun testSuggest() {
        val contest = "Candidates for Town Council"
        val suggest = suggest("Eagle", contest, Colorado2020General())
        println("$contest -> $suggest")
    }

    @Test
    fun allColorado2020Counties() {
        val path = Path(colorado2020)
        path.listDirectoryEntries().sorted().filter { it.isDirectory() && !it.fileName.toString().startsWith("202")}.forEach { subdir ->
            val county = subdir.fileName.toString()
            /* subdir.listDirectoryEntries().filter { !it.isDirectory() && it.fileName.toString().endsWith(".csv")
                    && it.fileName.toString() != "summary.csv"
                    && !it.fileName.toString().contains("Manifest")
            }.forEach { entry -> */
            if (county !in listOf("Monroe", "Roosevelt", "Garfield")) { // no such county in Colorado && earlier format TODO
                try {
                    val filename = "${subdir.toString()}/cvr.csv" // entry.toString()
                    val errs = matchNames(county, filename, Colorado2020General())
                    assertEquals(0, errs)
                } catch (e: Exception) {
                    println(e.message)
                    throw e
                }
            }
            //}
        }
    }

    @Test
    fun showAllColorado2020Counties() {
        val path = Path(colorado2020)
        path.listDirectoryEntries().sorted().filter { it.isDirectory() && !it.fileName.toString().startsWith("202") }
            .forEach { subdir ->
                val county = subdir.fileName.toString()
                println("$county")
                subdir.listDirectoryEntries().filter {
                    !it.isDirectory() && it.fileName.toString().endsWith(".csv")
                            && it.fileName.toString() != "summary.csv"
                            && !it.fileName.toString().contains("Manifest")
                }.forEach { entry ->
                    if (county != "Monroe" && county != "Garfield") { // no such county in Colorado && earlier format && Baca has copy of Heurfano
                        try {
                            val filename = entry.toString()
                            val reader = DominionCvrExportReader(filename)
                            val star = if (reader.electionName.contains(county)) "" else "**"
                            println("  $star ${reader.electionName} : ${filename}")
                        } catch (e: Exception) {
                            println(e.message)
                            throw e
                        }
                    }
                }
            }
    }

    // could allow contained contests to be defined by the county

    fun matchNames(county: String, exportFile: String, input: ColoradoInput): Int {
        println("\n-----------------------------------")
        println("county=$county csvfile = $exportFile")

        val export: DominionCvrExport = DominionCvrExportReader(exportFile).read()
        val sinfoList = export.makeContestInfo()
        var errs = 0

        // test every export contest has a match in canon, along with each choice
        sinfoList.map { sinfo ->
            val contest = input.matchCanonicalContest(county, sinfo.name)
            if (contest == null) {
                println("   \"${sinfo.name}\" -> \"${suggest(county, sinfo.name, input)}\"")
                errs++
            } else {
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

        /* test every canon contest has a match in export TODO
        val countyContestTabs = input.countyContestTabs.find { it.countyName == county }!!
        countyContestTabs.contests.keys.forEach { canonContest ->
            val exportContest = input.matchExportContest(county, canonContest)
            if (infos[exportContest] == null) {
                println("*** didnt find canonicalContest named '${canonContest}' in export file")
                errs++
            }
        } */

        return errs
    }
}

fun suggest(county: String, contest: String, input: ColoradoInput): String {
    val countyContestTabs = input.countyContestTabs.find { it.countyName == county }
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