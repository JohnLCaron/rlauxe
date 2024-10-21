package org.cryptobiotic.rlauxe.core

data class AuditPolling(
    val auditType: AuditType,
    val riskLimit: Double,
    val contests: List<AuditContest>,
    val assertions: Map<AuditContest, List<Assertion>>,
) {
    override fun toString() = buildString {
        appendLine("AuditPolling: auditType=$auditType riskLimit=$riskLimit")
        contests.forEach {
            appendLine("  Contest=${it}")
            val cass = assertions[it]!!
            cass.forEach { a ->
                appendLine("     $a")
            }
        }
    }
}

fun makePollingAudit(contests: List<AuditContest>, riskLimit: Double  = 0.05): AuditPolling {
    val assertions: Map<AuditContest, List<Assertion>> = contests.associate { makePollingAssertions(it) }
    return AuditPolling(AuditType.POLLING, riskLimit, contests, assertions)
}

fun makePollingAssertions(contest: AuditContest): Pair<AuditContest, List<Assertion>> =
    when (contest.choiceFunction) {
        SocialChoiceFunction.APPROVAL,
        SocialChoiceFunction.PLURALITY, -> Pair(contest, makePluralityAssertions(contest))
        SocialChoiceFunction.SUPERMAJORITY -> Pair(contest, makeSuperMajorityAssertions(contest))
        else -> throw RuntimeException(" choice function ${contest.choiceFunction} is not supported")
    }

fun makePluralityAssertions(contest: AuditContest): List<Assertion> {
    // test that every winner beats every loser. SHANGRLA 2.1
    val assertions = mutableListOf<Assertion>()
    contest.winners.forEach { winner ->
        contest.losers.forEach { loser ->
            val assort = PluralityAssorter(contest, winner, loser)
            assertions.add(Assertion(contest, assort))
        }
    }
    return assertions
}

fun makeSuperMajorityAssertions(contest: AuditContest): List<Assertion> {
    // each winner generates 1 assertion. SHANGRLA 2.3
    val assertions = mutableListOf<Assertion>()
    contest.winners.forEach { winner ->
        val assort = SuperMajorityAssorter(contest, winner, contest.minFraction!!)
        assertions.add(Assertion(contest, assort))
    }
    return assertions
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

data class AuditComparison(
    val auditType: AuditType,
    val riskLimit: Double,
    val contests: List<AuditContest>,
    val assertions: Map<AuditContest, List<ComparisonAssertion>>,
) {
    override fun toString() = buildString {
        appendLine("AuditComparison: auditType=$auditType riskLimit=$riskLimit")
        contests.forEach {
            appendLine("  Contest=${it}")
            val cass = assertions[it]!!
            cass.forEach { a ->
                appendLine("     $a")
            }
        }
    }
}

fun makeComparisonAudit(contests: List<AuditContest>, riskLimit: Double  = 0.05, cvrs : Iterable<Cvr>): AuditComparison {
    val comparisonAssertions = mutableMapOf<AuditContest, List<ComparisonAssertion>>()

    contests.forEach { contest ->
        val assertions = when (contest.choiceFunction) {
            SocialChoiceFunction.APPROVAL,
            SocialChoiceFunction.PLURALITY, -> makePluralityAssertions(contest)
            SocialChoiceFunction.SUPERMAJORITY -> makeSuperMajorityAssertions(contest)
            else -> throw RuntimeException(" choice function ${contest.choiceFunction} is not supported")
        }

        val clist = assertions.map { assertion ->
            val avgCvrAssortValue = cvrs.map { assertion.assorter.assort(it) }.average()
            val comparisonAssorter = ComparisonAssorter(contest, assertion.assorter, avgCvrAssortValue)
            ComparisonAssertion(contest, comparisonAssorter)
        }
        comparisonAssertions[contest] = clist
    }

    return AuditComparison(AuditType.CARD_COMPARISON, riskLimit, contests, comparisonAssertions)
}