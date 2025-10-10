package org.cryptobiotic.rlauxe.oneaudit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.Assertion
import org.cryptobiotic.rlauxe.raire.IrvCountResult
import org.cryptobiotic.rlauxe.raire.IrvRound
import org.cryptobiotic.rlauxe.raire.RaireAssertion
import org.cryptobiotic.rlauxe.raire.RaireAssorter
import org.cryptobiotic.rlauxe.raire.RaireContest
import org.cryptobiotic.rlauxe.raire.showIrvCountResult

private val logger = KotlinLogging.logger("OAIrvContestUA")

class OAIrvContestUA(
    contest: RaireContest,
    hasStyle: Boolean = true,
    val rassertions: List<RaireAssertion>,
): OAContestUnderAudit(contest, hasStyle=hasStyle) {

    init {
        this.pollingAssertions = makeRairePollingAssertions()
    }

    fun makeRairePollingAssertions(): List<Assertion> {
        return rassertions.map { rassertion ->
            val assorter = RaireAssorter(contest.info(), rassertion, (rassertion.marginInVotes.toDouble() / contest.Nc()))
            Assertion(contest.info(), assorter)
        }
    }

    override fun recountMargin() = (contest as RaireContest).recountMargin()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as OAIrvContestUA
        if (rassertions != other.rassertions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + rassertions.hashCode()
        return result
    }

    override fun showCandidates() = buildString {
        val roundsPaths = (contest as RaireContest).roundsPaths
        append(showIrvCountResult(IrvCountResult(roundsPaths), contest.info))
    }
}
