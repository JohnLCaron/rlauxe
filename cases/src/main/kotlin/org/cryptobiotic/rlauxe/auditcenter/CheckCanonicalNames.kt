package org.cryptobiotic.rlauxe.auditcenter

// check name consistency in ColoradoInput
class CheckCanonicalNames( val input: ColoradoInput) {
    val canonical = input.canonicalContests()
    val canonicalContestNames = canonical.map { it.key }

    init {
        checkCanonicalHasContestTabulate()
        checkCanonicalHasCountyTabulate()
        checkContestTabulateHasCanonical()
        checkCountyTabulateAndCanonicalContests()
        checkContestRoundAndCanonicalContests()
        checkCanonicalHasContestComparison()
        checkCorrectedCanonicalContests()
        reportCorrectedCanonicalContests()
    }

    fun checkCanonicalHasContestTabulate() {
        println("\n--------------------- checkCanonicalHasContestTabulate")

        val extras = mutableListOf<CanonicalContest>()

        // TODO read raw input ??
        val contestTabs: Map<String, ContestTabAllCounties> = input.contestTabsAllCounties()
        contestTabs.values.forEach { contest ->
            if (!canonical.contains(contest.contestName)) {
                println("missing contest '${contest.contestName}'")
                val addIt = CanonicalContest(contest.contestName, contest.choices.keys.toList())
                addIt.counties.addAll(contest.counties)
                extras.add(addIt)
            } else {
                val canonicalChoices: Set<String> = canonical[contest.contestName]!!.choices.toSet()
                val missingChoices = mutableListOf<String>()
                var hasMissing = false

                contest.choices.forEach {
                    if (!canonicalChoices.contains(it.key)) {
                        println("missing choice  '${it.key}' in contest '${contest.contestName}'")
                        missingChoices.add(it.key)
                        hasMissing = true
                    }
                }
                if (hasMissing) println()

                if (missingChoices.isNotEmpty()) {
                    println("\nadd the following to contestNameCleanup for contest ${contest.contestName}")
                    missingChoices.forEach { println("  $it -> $it") }
                }
            }
        }

        if (extras.isNotEmpty()) {
            println("add the following to canonicalContests:")
            extras.forEach { println("  $it") }
            println()
        }
    }

    fun checkCanonicalHasCountyTabulate(): Boolean {
        println("\n-------------------------------- checkCanonicalHasCountyTabulate")
        val missing = mutableListOf<String>()
        var allOk = true

        // same as raw input; no mods
        input.countyTabsAllContests().filter{!input.skipCounties(it.key) }.forEach { (countyName, ct: CountyTabAllContests) ->
            ct.contests.forEach { (contestName, countyTabAllContests) ->
                val canonicalContest = input.canonicalContests()[contestName]
                if (canonicalContest == null) {
                    println("  canonical missing countyTabulate contest '${contestName}' county '$countyName'")
                    missing.add(contestName)
                    allOk = false

                } else {
                    val missingChoices = mutableListOf<String>()
                    // these will fail CountyContestVotes.canonicalChoices()
                    countyTabAllContests.choices.filter { !isWriteIn(it.key) }.keys.forEach { choiceName ->
                        if (canonicalContest.matchCandidateName(choiceName) == null) {
                            println("  canonical missing countyTabulate choice '${choiceName}' in contest '${contestName}' in county $countyName")
                            missingChoices.add(choiceName)
                            allOk = false
                        }
                    }
                    if (missingChoices.isNotEmpty()) {
                        println("\nadd the following to contestNameCleanup for contest ${contestName}")
                        val missingCandList = missingChoices.joinToString(prefix = "\"", postfix = "\"")
                        println("    addCandidates(result, \"$contestName\", listOf($missingCandList))")

                    }
                }
            }
        }
        println()
        return allOk
    }

    fun checkContestTabulateHasCanonical() {
        println("\n------------------------- checkContestTabulateHasCanonical")
        val missing = mutableListOf<String>()

        // raw inout
        val contestTabs: Map<String, ContestTabAllCounties> = input.contestTabsAllCounties()
        canonical.values.forEach { cc ->
            if (!contestTabs.contains(cc.contestName)) {
                println("contestTabulate missing canonical contest '${cc.contestName}'")
                missing.add(cc.contestName)
            } else {
                val contestTab : ContestTabAllCounties = contestTabs[cc.contestName]!!
                val tabChoices: Set<String> = contestTab.choices.keys.toList().toSet()
                cc.choices.forEach {
                    if (!tabChoices.contains(it))
                        println("    missing choice  '${it}' in contestTab '${cc.contestName}'")
                }
            }
        }
        println()
        missing.forEach { println("result.remove(\"$it\")") }
    }

    fun checkCountyTabulateAndCanonicalContests() {
        println("\n-------------------------- checkCountyTabulateAndCanonicalContests")
        val countyTabs: Map<String, CountyTabAllContests> = readCountyTabulateCsv(input.tabulateCountyFile)
        val countiesFromTab = countyTabs.keys.toSet().toList()
        compareLists(input.counties(), countiesFromTab, "canonical", "countyTabulateCsv")
    }

    fun checkContestRoundAndCanonicalContests() {
        println("\n-------------------------------- compare canonical contests and ContestRoundCsv")
        val contestRounds = input.roundContests().values.map { it.contestName }

        compareLists(canonicalContestNames, contestRounds, "canonical", "ContestRoundCsv")
    }

    fun checkCanonicalHasContestComparison() {
        println("\n--------------------------------- checkCanonicalHasContestComparison")
        val contestMvrs = input.contestsFromMvrs.map { it.contestName }

        contestMvrs.forEach {
            if (!canonical.contains(it)) println("  canonical missing '${it}' in contestsFromMvrs")
        }
    }

    // now correct ColoradoInput and then run this:
    fun checkCorrectedCanonicalContests() {
        println("\n================================ checkCorrectedCanonicalContests")
        input.canonicalContests().forEach { cc ->
            if (!input.contestTabsAllCounties().contains(cc.key)) println( "contestTabsByCounty '${cc.key}' from canonical")
            if (!input.roundContests().contains(cc.key)) println( "roundContests '${cc.key}' from canonical")
        }
    }

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
            if (!input.contestTabsAllCounties().contains(cc.key)) println("contestTabsByCounty '${cc.key}' from canonical")
            if (!input.roundContests().contains(cc.key)) println("roundContests '${cc.key}' from canonical")
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