package org.cryptobiotic.rlauxe.core

enum class AuditType { POLLING, CARD_COMPARISON, ONEAUDIT }

data class AuditPolling(
    val auditType: AuditType,
    val riskLimit: Double,
    val contests: List<Contest>, // order must not change; this is the contest name -> index
    val assertions: Map<Int, List<Assertion>>, // contestId -> assertion list
) {
    override fun toString() = buildString {
        appendLine("AuditPolling: auditType=$auditType riskLimit=$riskLimit")
        contests.forEach {
            appendLine("  Contest=${it}")
            val cass = assertions[it.id]!!
            cass.forEach { a ->
                appendLine("     $a")
            }
        }
    }
}

fun makePollingAudit(contests: List<Contest>, cvrs: Iterable<CvrIF>, riskLimit: Double  = 0.05): AuditPolling {
    val assertions: Map<Int, List<Assertion>> = contests.associate { makePollingAssertions(it, cvrs) }
    return AuditPolling(AuditType.POLLING, riskLimit, contests, assertions)
}

fun makePollingAssertions(contest: Contest, cvrs: Iterable<CvrIF>): Pair<Int, List<Assertion>> =
    when (contest.choiceFunction) {
        SocialChoiceFunction.APPROVAL,
        SocialChoiceFunction.PLURALITY, -> Pair(contest.id, makePluralityAssertions(contest, cvrs))
        SocialChoiceFunction.SUPERMAJORITY -> Pair(contest.id, makeSuperMajorityAssertions(contest, cvrs))
        else -> throw RuntimeException(" choice function ${contest.choiceFunction} is not supported")
    }

fun makePluralityAssertions(contest: Contest, cvrs: Iterable<CvrIF>): List<Assertion> {
    // test that every winner beats every loser. SHANGRLA 2.1
    val assertions = mutableListOf<Assertion>()
    contest.winners.forEach { winner ->
        contest.losers.forEach { loser ->
            val assorter = PluralityAssorter(contest, winner, loser)
            val avgAssortValue = cvrs.map { assorter.assort(it) }.average()
            assertions.add(Assertion(contest, assorter, avgAssortValue))
        }
    }
    return assertions
}

fun makeSuperMajorityAssertions(contest: Contest, cvrs: Iterable<CvrIF>): List<Assertion> {
    // each winner generates 1 assertion. SHANGRLA 2.3
    val assertions = mutableListOf<Assertion>()
    contest.winners.forEach { winner ->
        val assorter = SuperMajorityAssorter(contest, winner, contest.minFraction!!)
        val avgAssortValue = cvrs.map { assorter.assort(it) }.average()
        assertions.add(Assertion(contest, assorter, avgAssortValue))
    }
    return assertions
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

data class AuditComparison(
    val auditType: AuditType,
    val riskLimit: Double,
    val contests: List<Contest>,
    val assertions: Map<Int, List<ComparisonAssertion>>, // contestId -> assertion list
) {
    override fun toString() = buildString {
        appendLine("AuditComparison: auditType=$auditType riskLimit=$riskLimit")
        contests.forEach { contest ->
            appendLine("  Contest=${contest}")
            val cass = assertions[contest.id]!!
            cass.forEach { a ->
                appendLine("     $a")
            }
        }
    }
}

fun makeComparisonAudit(contests: List<Contest>, cvrs : Iterable<Cvr>, riskLimit: Double=0.05): AuditComparison {
    val comparisonAssertions = mutableMapOf<Int, List<ComparisonAssertion>>()

    contests.forEach { contest ->
        val assertions = when (contest.choiceFunction) {
            SocialChoiceFunction.APPROVAL,
            SocialChoiceFunction.PLURALITY, -> makePluralityAssertions(contest, cvrs)
            SocialChoiceFunction.SUPERMAJORITY -> makeSuperMajorityAssertions(contest, cvrs)
            else -> throw RuntimeException(" choice function ${contest.choiceFunction} is not supported")
        }

        val clist = assertions.map { assertion ->
            val avgCvrAssortValue = cvrs.map { assertion.assorter.assort(it) }.average()
            val comparisonAssorter = ComparisonAssorter(contest, assertion.assorter, avgCvrAssortValue)
            ComparisonAssertion(contest, comparisonAssorter)
        }
        comparisonAssertions[contest.id] = clist
    }

    return AuditComparison(AuditType.CARD_COMPARISON, riskLimit, contests, comparisonAssertions)
}