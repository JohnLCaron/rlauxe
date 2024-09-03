package org.cryptobiotic.rlauxe.core

data class Assertion(
    val contest: AuditContest,
    val winner: Int,
    val loser: Int,
) {
    val assorter: AssorterFunction = makeAssorter()

    fun makeAssorter() : AssorterFunction {
        return when (contest.choiceFunction) {
            SocialChoiceFunction.SUPERMAJORITY -> SupermajorityAssorter(contest, winner, loser)
            SocialChoiceFunction.IRV -> throw RuntimeException("IRV Not supported")
            SocialChoiceFunction.PLURALITY,
            SocialChoiceFunction.APPROVAL -> PluralityAssorter(contest, winner, loser)
        }
    }

    override fun toString() = buildString {
        appendLine("Assertion")
        appendLine("   $contest)")
        appendLine("   assorter=$assorter)")
    }
}

data class PollingAudit(
    val auditType: AuditType,
    val riskLimit: Double = 0.05,
    val contests: List<AuditContest>,
) {
    // TODO put in Contest?
    // keep in Audit, Assertions structure ?
    val assertions: Map<AuditContest, List<Assertion>> = contests.associate { makeAssertions(it) } // map of AuditContest -> List<Assertion>

    fun makeAssertions(contest: AuditContest): Pair<AuditContest, List<Assertion>> {
        val assertions = mutableListOf<Assertion>()
        contest.winners.forEach { winner ->
            contest.losers.forEach { loser ->
                assertions.add(Assertion(contest, winner, loser))
            }
        }
        return Pair(contest, assertions)
    }
}

//////////////////////////////////////////////////////////////////////////////////////////

class ComparisonAssertion(
    val contest: AuditContest,
    val winner: Int,
    val loser: Int,
    cvrs: Iterable<Cvr>,
) {
    val assorter: ComparisonAssorter = makeAssorter(cvrs)

    fun makeAssorter(cvrs: Iterable<Cvr>) : ComparisonAssorter {
        val assorter = when (contest.choiceFunction) {
            SocialChoiceFunction.SUPERMAJORITY -> SupermajorityAssorter(contest, winner, loser)
            SocialChoiceFunction.IRV -> throw RuntimeException("IRV Not supported")
            SocialChoiceFunction.PLURALITY,
            SocialChoiceFunction.APPROVAL -> PluralityAssorter(contest, winner, loser)
        }
        val avgCvrAssortValue = cvrs.map { assorter.assort(it) }.average()
        return ComparisonAssorter(assorter, avgCvrAssortValue)
    }

    override fun toString() = buildString {
        appendLine("ComparisonAssertion")
        appendLine("   $contest)")
        appendLine("   assorter=$assorter)")
    }
}

data class ComparisonAudit(
    val auditType: AuditType,
    val riskLimit: Double = 0.05,
    val contests: List<AuditContest>,
    val cvrs: Iterable<Cvr>,
) {
    // TODO put in Contest?
    val assertions: Map<AuditContest, List<ComparisonAssertion>> = contests.associate { makeAssertions(it) } // map of AuditContest -> List<Assertion>

    fun makeAssertions(contest: AuditContest): Pair<AuditContest, List<ComparisonAssertion>> {
        val assertions = mutableListOf<ComparisonAssertion>()
        contest.winners.forEach { winner ->
            contest.losers.forEach { loser ->
                assertions.add(ComparisonAssertion(contest, winner, loser, cvrs))
            }
        }
        return Pair(contest, assertions)
    }
}