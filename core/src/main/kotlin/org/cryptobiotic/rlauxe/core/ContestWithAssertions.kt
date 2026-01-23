package org.cryptobiotic.rlauxe.core

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.dhondt.DHondtContest
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.pfn
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards

// note mutability
open class ContestWithAssertions(
    val contest: ContestIF,
    val isClca: Boolean = true,
    NpopIn: Int? = null,
) {
    val id = contest.id
    val name = contest.name
    val choiceFunction = contest.choiceFunction
    val ncandidates = contest.ncandidates
    val Nc = contest.Nc()
    val Nphantoms = contest.Nphantoms()
    val Npop: Int = NpopIn ?: Nc // "sample population size" for this contest, used to make diluted margins
    val isIrv = contest.info().isIrv

    var preAuditStatus = TestH0Status.InProgress // pre-auditing status: NoLosers, NoWinners, ContestMisformed, MinMargin, TooManyPhantoms
    var assertions: List<Assertion> = emptyList() // mutable needed for Raire override and serialization
    var clcaAssertions: List<ClcaAssertion> = emptyList() // mutable needed for serialization

    init {
        if (contest.losers().size == 0) {
            preAuditStatus = TestH0Status.NoLosers
        } else if (contest.winners().size == 0) {
            preAuditStatus = TestH0Status.NoWinners
        }
    }

    // dhondt
    fun addAssertionsFromAssorters(assorters: List<AssorterIF>): ContestWithAssertions {
        val assertions = mutableListOf<Assertion>()
        assorters.forEach { assorter ->
            assertions.add(Assertion(contest.info(), assorter))
        }
        this@ContestWithAssertions.assertions = assertions

        if (isClca) {
            addClcaAssertionsFromDilutedMargin()
        }

        return this
    }

    fun addStandardAssertions(): ContestWithAssertions {
        if (contest.votes() == null) {
            throw RuntimeException("contest type ${contest.javaClass.simpleName} is not supported for addStandardAssertions")
        }

        this.assertions = when (choiceFunction) {
            SocialChoiceFunction.APPROVAL,
            SocialChoiceFunction.PLURALITY -> makePluralityAssertions()
            SocialChoiceFunction.THRESHOLD -> makeThresholdAssertions()
            else -> throw RuntimeException("choice function ${choiceFunction} is not supported")
        }

        if (isClca) {
            addClcaAssertionsFromDilutedMargin()
        }

        return this
    }

    private fun makePluralityAssertions(): List<Assertion> {
        // test that every winner beats every loser. SHANGRLA 2.1
        val assertions = mutableListOf<Assertion>()
        contest.winners().forEach { winner ->
            contest.losers().forEach { loser ->
                val assorter = PluralityAssorter.makeWithVotes(contest, winner, loser, Npop)
                assertions.add(Assertion(contest.info(), assorter))
            }
        }
        return assertions
    }

    private fun makeThresholdAssertions(): List<Assertion> {
        require(contest.info().minFraction != null)
        // each winner generates 1 assertion. SHANGRLA 2.3
        val assertions = mutableListOf<Assertion>()
        contest.winners().forEach { candId ->
            val assorter = AboveThreshold.makeFromVotes(contest as Contest, candId, Npop)
            assertions.add(Assertion(contest.info(), assorter))
        }
        return assertions
    }

    private fun addClcaAssertionsFromDilutedMargin(): ContestWithAssertions {
        require(isClca) { "makeComparisonAssertions() can be called only on comparison contest"}

        this.clcaAssertions = assertions.map { assertion ->
            ClcaAssertion(contest.info(), makeClcaAssorter(assertion))
        }
        return this
    }

    fun makeClcaAssorter(assertion: Assertion): ClcaAssorter {
        return ClcaAssorter(contest.info(), assertion.assorter, dilutedMargin=assertion.assorter.dilutedMargin())
    }

    fun assertions(): List<Assertion> {
        return if (isClca) clcaAssertions else assertions
    }

    // assertion with the minimum noerror
    fun minClcaAssertion(): ClcaAssertion? {
        if (clcaAssertions.isEmpty()) return null
        val margins = clcaAssertions.map { Pair(it, it.cassorter.noerror())  }
        val minMargin = margins.sortedBy { it.second }
        return minMargin.first().first
    }

    // assertion with the minimum dilutedMargin
    fun minPollingAssertion(): Assertion? {
        if (assertions.isEmpty()) return null
        val margins = assertions.map { Pair(it, it.assorter.dilutedMargin())  }
        val minMargin = margins.sortedBy { it.second }
        return minMargin.first().first
    }

    fun minAssertion(): Assertion? {
        return if (isClca) minClcaAssertion() else minPollingAssertion()
    }

    fun minDilutedMargin(): Double? {
        val minAssertion = minAssertion()
        return if (minAssertion != null) minAssertion.assorter.dilutedMargin() else null
    }

    fun minRecountMargin(): Double? {
        val minAssertion = minAssertion()
        return if (minAssertion != null)  contest.recountMargin(minAssertion.assorter) else null
    }

    fun minAssertionDifficulty(): String {
        val minAssertion = minAssertion()
        return if (minAssertion != null)  contest.showAssertionDifficulty(minAssertion.assorter) else "N/A"
    }

    fun phantomRate() = contest.Nphantoms() / Npop.toDouble()

    override fun toString() = showShort()

    open fun show() = buildString {
        appendLine("${contest::class.simpleName} ${contest.show()}")
        val minAssertion = minAssertion()
        if (minAssertion != null) {
            val minAssorter = minAssertion.assorter
            append("   ${contest.showAssertionDifficulty(minAssertion.assorter)}")
            append(" Npop=$Npop dilutedMargin=${pfn(minAssorter.dilutedMargin())}")
            appendLine(" reportedMargin=${pfn(minAssorter.dilutedMargin())} recountMargin=${pfn(contest.recountMargin(minAssorter))} ")
            appendLine()
        }
        append(contest.showCandidates())
    }

    open fun showShort() = buildString {
        val votes = contest.votes() ?: "N/A"
        append("$name ($id) votes=${votes} Nc=$Nc Npop=$Npop minDilutedMargin=${df(minDilutedMargin())}")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ContestWithAssertions

        if (isClca != other.isClca) return false
        // if (hasCompleteCvrs != other.hasCompleteCvrs) return false
        if (!contest.equals(other.contest)) return false
        if (preAuditStatus != other.preAuditStatus) return false
        if (assertions != other.assertions) return false
        if (clcaAssertions != other.clcaAssertions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isClca.hashCode()
        // result = 31 * result + hasCompleteCvrs.hashCode()
        result = 31 * result + contest.hashCode()
        result = 31 * result + preAuditStatus.hashCode()
        result = 31 * result + assertions.hashCode()
        result = 31 * result + clcaAssertions.hashCode()
        return result
    }

    companion object {
        private val logger = KotlinLogging.logger("ContestUnderAudit")

        // make contestUA from contests, generate Npop by reading cards
        fun make(contests: List<ContestIF>, cards: CloseableIterator<AuditableCard>, isClca: Boolean): List<ContestWithAssertions> {
            val infos = contests.map { it.info() }.associateBy { it.id }
            val manifestTabs = tabulateAuditableCards(cards, infos)
            val npopMap = manifestTabs.mapValues { it.value.ncards }
            return make(contests, npopMap, isClca)
        }

        // make contestUA from contests and Nbs.
        // this does not make OneAudit: use makeOneAuditContests
        fun make(contests: List<ContestIF>, npopMap: Map<Int,Int>, isClca: Boolean): List<ContestWithAssertions> {
            return contests.map {
                val cua = ContestWithAssertions(it, isClca, NpopIn=npopMap[it.id]).addStandardAssertions()
                if (it is DHondtContest) {
                    cua.addAssertionsFromAssorters(it.assorters)
                } else {
                    cua.addStandardAssertions()
                }
            }
        }
    }
}
