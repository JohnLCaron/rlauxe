package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.Assertion
import org.cryptobiotic.rlauxe.raire.RaireAssertion
import org.cryptobiotic.rlauxe.raire.RaireAssorter

class OneAuditIrvContest(
    contestOA: OneAuditContest,
    hasStyle: Boolean = true,
    val rassertions: List<RaireAssertion>,
): OAContestUnderAudit(contestOA, hasStyle=hasStyle) {

    init {
        this.pollingAssertions = makeRairePollingAssertions()
    }

    fun makeRairePollingAssertions(): List<Assertion> {
        return rassertions.map { rassertion ->
            val assorter = RaireAssorter(contest.info(), rassertion, (rassertion.marginInVotes.toDouble() / contest.Nc()))
            Assertion(contest.info(), assorter)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as OneAuditIrvContest
        if (rassertions != other.rassertions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + rassertions.hashCode()
        return result
    }
}
