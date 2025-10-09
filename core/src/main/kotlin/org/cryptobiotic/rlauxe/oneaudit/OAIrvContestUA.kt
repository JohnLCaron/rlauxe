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

    override fun recountMargin(): Double {
        try {
            val pctDefault = -1.0
            val rcontest = (contest as RaireContest)
            if (rcontest.roundsPaths.isEmpty()) return pctDefault
            val rounds = rcontest.roundsPaths.first().rounds // common case is only one
            if (rounds.isEmpty()) return pctDefault

            // find the latest round with two candidates
            var latestRound : IrvRound? = null
            rounds.forEach{ if (it.count.size == 2) latestRound = it}
            if (latestRound == null) return pctDefault

            val winner = latestRound.count.maxBy { it.value }
            val loser = latestRound.count.minBy { it.value }
            return (winner.value - loser.value) / (winner.value.toDouble())
        } catch (e : Throwable) {
            logger.error(e) { "recountMargin for contest ${contest.id}" }
            return -1.0
        }
    }

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
