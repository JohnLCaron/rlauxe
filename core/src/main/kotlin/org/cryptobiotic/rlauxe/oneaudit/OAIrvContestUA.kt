package org.cryptobiotic.rlauxe.oneaudit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.Assertion
import org.cryptobiotic.rlauxe.raire.RaireAssertion
import org.cryptobiotic.rlauxe.raire.RaireAssorter
import org.cryptobiotic.rlauxe.raire.RaireContest

private val logger = KotlinLogging.logger("OAIrvContestUA")

class OAIrvContestUA(
    contest: RaireContest,
    hasStyle: Boolean = true,
    val rassertions: List<RaireAssertion>,
): OAContestUnderAudit(contest, hasStyle=hasStyle, addAssertions=false) {

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

        other as OAIrvContestUA
        return rassertions == other.rassertions
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + rassertions.hashCode()
        return result
    }

}
