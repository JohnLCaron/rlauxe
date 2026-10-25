package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.corlaInput.isWriteIn
// import org.cryptobiotic.rlauxe.corlaInput.matchCandidateName

// check name consistency in ColoradoInput
class CheckCanonicalNames( val input: StatewideInput) {
    val canonical = input.canonicalContests()
    val canonicalContestNames = canonical.map { it.key }

    init {
        checkCanonicalHasContestTabulate()
        // checkCanonicalHasCountyTabulate()
        checkContestTabulateHasCanonical()
        // checkCountyTabulateAndCanonicalContests()
        checkContestRoundHasCanonical()
        checkCanonicalHasMvrComparison()

        reportCorrectedCanonicalContests()
    }


    ///////////////////////////////////////////////
    // ContestTabAllCounties derived from countyTabAllContests
    fun checkCanonicalHasContestTabulate() {
        println("\n--------------------- checkCanonicalHasContestTabulate")

        val extras = mutableListOf<CanonicalContest>()
        val missingChoices = mutableMapOf<String, MutableList<String>>()

        // TODO read raw input ??
        val contestTabs: Map<String, ContestTabAllCounties> = input.contestTabsAllCounties()
        contestTabs.values.forEach { contestTab ->
            val cc = input.matchCanonicalContest(contestTab.contestName)
            if (cc == null) {
                println("missing contest '${contestTab.contestName}'")
                val addIt = CanonicalContest(input, contestTab.contestName, contestTab.choices.keys.toList())
                addIt.counties.addAll(contestTab.counties)
                extras.add(addIt)
            } else {
                contestTab.choices.forEach {
                    if (input.matchCanonicalCandidate(contestTab.counties.first(), cc, it.key) == null) {
                        // println("missing choice  '${it.key}' in contest '${contest.contestName}'")
                        val missingChoiceList = missingChoices.getOrPut(cc.contestName) { mutableListOf() }
                        missingChoiceList.add(it.key)
                    }
                }

                /* if (missingChoices.isNotEmpty()) {
                    println("add the following to canonicalContests (or contestNameCleanup for contest ${contest.contestName})")
                    // missingChoices.forEach { println("  $it -> $it") }
                    val qchoices = buildString{ missingChoices.forEach { append("\"$it\",") } }
                    println("  addCandidates(result, \"${cc.contestName }\", ${listOf(qchoices)})")
                } */
            }
        }

        if (extras.isNotEmpty() || missingChoices.isNotEmpty()) {
            println("add the following to statInput.canonicalContests:")
        }
        if (extras.isNotEmpty()) {
            println("   val extras = listOf(")
            extras.forEach { println("      $it") }
            println("   )")
            println("   extras.forEach { result[it.contestName] = it }\n")
        }
        if (missingChoices.isNotEmpty()) {
            missingChoices.forEach {  (contest, choiceList) ->
                val qchoices = buildString{ choiceList.forEach { append("\"$it\",") } }
                println("   addCandidates(result, \"$contest\", listOf($qchoices))")
            }
            println()
        }
    }

    fun checkContestTabulateHasCanonical() {
        println("\n------------------------- checkContestTabulateHasCanonical")
        val missing = mutableListOf<String>()

        // raw inout
        val contestTabs: Map<String, ContestTabAllCounties> = input.contestTabsAllCounties()
        canonical.values.forEach { cc ->
            if (!contestTabs.contains(cc.contestName)) {
                if (cc.contestName.startsWith("Springfield")) {
                    val countyTabs: Map<String, CountyTabAllContests> = input.countyTabsAllContests()
                    val baca: CountyTabAllContests = countyTabs["Baca"]!!
                    val bacaContest = baca.contests[cc.contestName]
                    print("")
                }
                println("contestTabulate missing canonical contest '${cc.contestName}'")
                missing.add(cc.contestName)
            } else {
                val contestTab : ContestTabAllCounties = contestTabs[cc.contestName]!!
                contestTab.choices.forEach { choice ->
                    if (input.matchCanonicalCandidate(contestTab.counties.first(), cc, choice.key) == null)
                        println("    missing choice  '${choice.key}' in contestTab '${cc.contestName}'")
                }
            }
        }
        println()
        missing.forEach { println("result.remove(\"$it\")") } // TODO should we remove them ?? could calc from cvrs
    }


    fun checkCanonicalHasCountyTabulate(): Boolean {
        println("\n---------------------------- checkCanonicalHasCountyTabulate")
        val missing = mutableListOf<String>()
        var allOk = true

        // same as raw input; no mods
        input.countyTabsAllContests().filter{!input.skipCounties(it.key) }.forEach { (countyName, ct: CountyTabAllContests) ->
            // for each contest in each county; counties have the same contest
            ct.contests.forEach { (contestName, countyTabAllContests) ->
                val cc = input.canonicalContests()[contestName]
                // println(canonicalContest)

                if (cc == null) {
                    println("  canonical missing countyTabulate contest '${contestName}' county '$countyName'")
                    missing.add(contestName)
                    allOk = false

                } else {
                    val missingChoices = mutableListOf<String>()
                    // these will fail CountyContestVotes.canonicalChoices()
                    countyTabAllContests.choices.filter { !isWriteIn(it.key) }.keys.forEach { choiceName ->
                        if (input.matchCanonicalCandidate(countyName, cc, choiceName) == null) {
                            // println("  canonical missing countyTabulate choice '${choiceName}' in contest '${contestName}' in county $countyName")
                            missingChoices.add(choiceName)
                            allOk = false
                        }
                    }
                    if (missingChoices.isNotEmpty()) {
                        println("\nadd the following to contestNameCleanup for contest '${contestName}'")
                        val missingCandList = missingChoices.joinToString(prefix = "\"", postfix = "\"")
                        println("    addCandidates(result, \"$contestName\", listOf($missingCandList))")
                    }
                }
            }
        }
        println()
        return allOk
    }

    fun checkCountyTabulateAndCanonicalContests() {
        println("\n-------------------------- checkCountyTabulateAndCanonicalContests")
        val countyTabs: Map<String, CountyTabAllContests> = input.countyTabsAllContests()
        val countiesFromTab = countyTabs.keys.toSet().toList()
        compareLists(input.counties(), countiesFromTab, "canonical", "countyTabulateCsv")
    }

    fun checkContestRoundHasCanonical() {
        println("\n-------------------------------- checkContestRoundHasCanonical")
        val contestRounds = input.roundContests().values.map { it.contestName }

        compareLists(canonicalContestNames, contestRounds, "canonical", "ContestRoundCsv")
    }

    fun checkCanonicalHasMvrComparison() {
        println("\n--------------------------------- checkCanonicalHasMvrComparison")
        val contestMvrs = input.contestsFromMvrs.map { it.contestName }

        contestMvrs.forEach {
            if (!canonical.contains(it)) println("  canonical missing '${it}' in contestsFromMvrs")
        }
    }

    // now correct ColoradoInput and then run this:

    fun reportCorrectedCanonicalContests() {
        println("\n--------------------------------- reportCorrectedCanonicalContests")

        val inputCanonical = input.canonicalContests()

        input.contestTabsAllCounties().forEach {
            if (!inputCanonical.contains(it.key)) println("canonical missing '${it.key}' from contestTabsByCounty")
        }

        input.roundContests().forEach {
            if (!inputCanonical.contains(it.key)) println( "canonical missing '${it.key}' from roundContests")
        }

        input.contestsFromMvrs.forEach {
            if (!inputCanonical.contains(it.contestName)) println("canonical missing '${it.contestName}' from contestMvrs")
        }

        inputCanonical.forEach { cc ->
            if (!input.contestTabsAllCounties().contains(cc.key)) println("contestTabsByCounty missing '${cc.key}' from canonical")
            if (!input.roundContests().contains(cc.key))          println("      roundContests missing '${cc.key}' from canonical")
        }
    }

}

fun compareLists(list1: List<String>, list2: List<String>, name1: String = "canonical1", name2: String = "canonical2") {
    list1.forEach {
        if (!list2.contains(it)) {
            println("  $name2 doesnt have '${it}' from $name1")
        }
    }
    list2.forEach {
        if (!list1.contains(it)) {
            println("  $name1 doesnt have '${it}' from $name2")
        }
    }
}