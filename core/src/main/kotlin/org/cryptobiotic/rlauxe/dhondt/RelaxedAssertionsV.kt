package org.cryptobiotic.rlauxe.dhondt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.core.AboveThreshold
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.dhondt.DhondtBuilder.Companion.makeDhAssorterFromDivisors
import org.cryptobiotic.rlauxe.util.nfn
import kotlin.collections.forEach
import kotlin.collections.setOf
import kotlin.io.println

class RelaxedAssertionsV(val orgContest: DhondtContest,
                         val fromAssorters: List<AssorterIF>,
                         override val Npop: Int,
                         override val nsamples: Int,
                         override val alpha: Double,
                         failuresIn: List<DhondtFailure>,
): RelaxedAssertionsIF {
    val orgInfo = orgContest.info
    val votes = orgContest.votes

    val failures: MutableList<DhondtFailure>

    val singleFailure: Boolean
    val altContest: DhondtContest
    val candidateRanges: ContestRanges
    val assortersForProof: List<AssorterIF>

    init {
        logger.debug { "Contest ${orgInfo.name} haveSampleSize=${nsamples}" }
        failures = failuresIn.toMutableList()

        // single DH failure
        singleFailure = (failures.size == 1)
        if (singleFailure) {
            altContest = DhondtBuilderV(orgContest, failures.first()).build()
            candidateRanges = ContestRanges(altContest, failures)
            assortersForProof = altContest.assorters
        } else {
            // multiple DH failure
            val algo  = multipleFailures()
            altContest = orgContest // TODO
            candidateRanges = algo.contestRanges(orgContest)
            assortersForProof = algo.assortersForProof + orgContest.assorters.filter { it !is DhondtAssorter } // add the threshold assorters
        }
    }

    override fun altContest() = altContest
    override fun assortersForProof() = assortersForProof
    override fun contestRanges() = candidateRanges
    override fun failures() = failures

    override fun show() = buildString {
        appendLine("Failures")
        appendLine(DhondtFailure.header())
        failures.forEach { appendLine(it) }
        appendLine()
        // "winning seats"
        append(showCandidateSeatOrder(altContest, assortersForProof, nsamples, alpha))
        appendLine()
        append(showTable5(altContest, failures))
        appendLine()
        append(candidateRanges.showSeatRanges())
    }

    class DHrelaxingAlgorithm() {
        val partyCounts = mutableMapOf<Int, PartyCount>()
        val uncertainWinners = mutableListOf<String>()
        val uncertainLosers = mutableListOf<String>()
        var assortersForProof = emptyList<AssorterIF>()

        var round = 0

        fun show() = buildString {
            partyCounts.values.forEach { appendLine("   ${it}")}
            appendLine("uncertainWinners = $uncertainWinners")
            appendLine("uncertainLosers = $uncertainLosers")
        }

        // not sure of this
        fun contestRanges(dcontest: DhondtContest) : ContestRanges {
            val cr = ContestRanges(dcontest, emptyList())
            cr.partyRanges.values.forEach { partyRange ->
                val uw = uncertainWinners.filter{ it.startsWith(partyRange.partyName) }.count()
                val ul = uncertainLosers.filter{ it.startsWith(partyRange.partyName) }.count()
                partyRange.minSeats -= uw
                partyRange.maxSeats += ul
            }
            return cr
        }
    }

    class PartyCount(val party: DhondtParty, val nseats: Int) {
        val id = party.id
        var dhw: Int = nseats
        var dhu: Int = 0
        var dhuw: Int = 0
        var dhul: Int = 0
        var dhl: Int = 999

        override fun toString() = buildString {
            append("PartyCount(party=${nfn(party.id, 2)}, nseats=${nfn(nseats, 2)} id=${nfn(id, 2)}, dhw=$dhw, dhu=$dhu, dhl=$dhl)")
        }
    }

    data class PartyDh(val step: Int, val winnerId: Int, val loserId: Int, val dh: DhondtAssorter)

    // 4.3.1
    fun multipleFailures(): DHrelaxingAlgorithm {
        val algo = DHrelaxingAlgorithm()
        val parties = orgContest.parties
        parties.forEach {
            algo.partyCounts[it.id] = PartyCount(it, orgContest.winnerSeatCount[it.id]!!)
        }
        println("${algo.show()}")

        var accept = false
        while(!accept) {
            algo.round++
            accept = step3(algo)
            println("${algo.show()}")
            println()
        }
        println("done")
        return algo
    }

    fun step3(algo: DHrelaxingAlgorithm): Boolean {
        val partyCounts = algo.partyCounts

        // "Calculate sample sizes" -> calculate noerror
        val partyDhs = mutableListOf<PartyDh>()

        if (algo.round == 4)
            print("")

        // a) For all A ∈ P with DHW (A) > 0, for all parties B ̸= A with DHU (B) > 0
        //    DHA,B (DHW (A), DHW (B) + 1)
        // (Every clear winner defeats every uncertain outcome.)
        partyCounts.values.forEach { partyA ->
            if (partyA.dhw > 0) {
                partyCounts.values.filter { it.id != partyA.id && it.dhu > 0 }.forEach { partyB ->
                    partyDhs.add(PartyDh(1, partyA.id, partyB.id,
                        makeDhAssorterFromDivisors(orgContest.info, partyA.party, partyA.dhw, partyB.party, partyB.dhw + 1, orgContest.Nc)))
                    if (partyDhs.last().dh.shortName() == "PVDA/3-N-VA/9")
                        print("")
                }
            }
        }

        // b) For all A ∈ P with DHU (A) > 0, for all parties B ̸= A with DHL (B) > 0,
        //     DHA,B (DHW (A) + DHU (A), DHW (B) + DHU (B) + 1).
        // (Every uncertain outcome defeats every clear loser.)
        partyCounts.values.forEach { partyA ->
            if (partyA.dhu > 0) {
                partyCounts.values.filter { it.id != partyA.id && it.dhl > 0 }.forEach { partyB ->
                    partyDhs.add(PartyDh(2, partyA.id, partyB.id,
                        makeDhAssorterFromDivisors(orgContest.info, partyA.party, partyA.dhw + partyA.dhu, partyB.party, partyB.dhw + partyB.dhu + 1, orgContest.Nc)))
                    if (partyDhs.last().dh.shortName() == "PVDA/3-N-VA/9")
                        print("")
                }
            }
        }

        // c) For all A ∈ P with DHW (A) > 0, and DHU (A) = 0 for all parties B ̸= A with DHL (B) > 0 and DHU (B) = 0,
        //      DHA,B (DHW (A), DHW (B) + DHU (B) + 1).
        // (Every clear winner defeats every clear loser for parties with no uncertain candidates.)
        partyCounts.values.forEach { partyA ->
            if (partyA.dhw > 0 && partyA.dhu == 0) {
                partyCounts.values.filter { it.id != partyA.id && it.dhl > 0 && it.dhu == 0 }.forEach { partyB ->
                    partyDhs.add(PartyDh(3, partyA.id, partyB.id,
                        makeDhAssorterFromDivisors(orgContest.info, partyA.party, partyA.dhw, partyB.party, partyB.dhw + partyB.dhu + 1, orgContest.Nc)))
                    if (partyDhs.last().dh.shortName() == "PVDA/3-N-VA/9")
                        print("")
                }
            }
        }

        val minPartyCount = partyDhs.minByOrNull { it.dh.noerror(true) }!!
        val estMvrs = estSampleSizeStandardBet(Npop, minPartyCount.dh.noerror(true), alpha)
        println("round ${algo.round} nassert=${partyDhs.size} estMvrs=$estMvrs assert='${minPartyCount.dh}'")

        if (estMvrs < nsamples) {
            algo.assortersForProof = partyDhs.map { it.dh }
            return true
        }

        val partyA = partyCounts[minPartyCount.dh.winnerId]!!
        val partyB = partyCounts[minPartyCount.dh.loserId]!!
        when (minPartyCount.step) {
            1 -> { partyA.dhw--; partyA.dhu++; algo.uncertainWinners.add(minPartyCount.dh.winnerNameRound()) } // move A’s lowest winner into “uncertain”
            2 -> { partyB.dhl--; partyB.dhu++; algo.uncertainLosers.add(minPartyCount.dh.loserNameRound()) } // move B’s highest loser into “uncertain”
            3 -> { partyA.dhw--; partyB.dhl++;  partyA.dhu++; partyB.dhu++
                algo.uncertainWinners.add(minPartyCount.dh.winnerNameRound()) // move A’s lowest winner and B’s highest loser into “uncertain”
                algo.uncertainLosers.add(minPartyCount.dh.loserNameRound())
            }
            else -> throw RuntimeException()
        }

        return false
    }

    companion object {
        private val logger = KotlinLogging.logger("RelaxedAssertionsV")
    }
}

open class DhondtBuilderV(
    from: DhondtContest,
    val failAssertion: DhondtFailure,
): DhondtBuilder(from) {

// When there is a single infeasible assertion that is a DH assertion DHA,B (w, l), we can
// omit it and generate the assertions that allow the seat to be assigned to either side of the
// infeasible DH comparison. The assertions are given in Proposition 3. This means adding
// an assertion that the second-lowest winner of Party A still defeated B’s highest loser,
// and that the lowest winner of Party A defeated B’s second-highest loser.
// We also need to add assertions to say that the winning side of the relaxed assertion (that is, Party A’s w-th candidate) is a
// DH loser compared to every other DH winner and, similarly, that the losing side (Party
// B’s l-th candidate) is a DH winner compared to every other DH loser. In other words,
// that these two are the most fragile in their respective categories.

    override fun build(): DhondtContest {
        val votes = partyBs.associate { Pair(it.id, it.totalVotes) }
        val sortedScores = createCandidateScores(partyBs, info.nwinners, Nc, info.minFraction!!, thresholdOverride)
        val parties = partyBs.map { it.build() }

        // define W the set of parties that have at least one reported winner
        // define L the set of parties that have at least one reported loser
        val winningParties : Set<DhondtParty> = parties.filter { it.lastSeatWon!! > 0 }.toSet()
        val losingParties : Set<DhondtParty> = parties.toSet()
        val assorters = mutableListOf<AssorterIF>()

        // Suppose the lowest-margin DH assertion is DH_R,Q (s(R), s(Q) + 1).
        val R : DhondtParty = parties.find { it.id == failAssertion.assorter.winnerId } !!
        val Q : DhondtParty = parties.find { it.id == failAssertion.assorter.loserId }!!

        // Proposition 3

        // (14) AT (A) for all A ∈ W ∪ {Q}
        val winnersUnionQ: Set<DhondtParty> = winningParties + setOf(Q)
        winnersUnionQ.forEach { winner ->
            assorters.add(AboveThreshold.makeFromVotes(info, partyId = winner.id, votes, minFraction, this.Nc))
        }

        // (15) BT(B) OR ( AND(DH_AB) for all A in Winners) for all B ∈ Losers \ Q
        val losersMinusQ : Set<DhondtParty> = losingParties - setOf(Q)
        losersMinusQ.forEach { loser ->
            assorters.addAll(btOrBhs(winningParties, loser, votes))
        }

        // (16) BT(Q) OR ( AND(DH_AQ) for all A in Winners\R )
        val winnersNotR: Set<DhondtParty> = winningParties - setOf(R)
        assorters.addAll(btOrBhs(winnersNotR, Q, votes))

        // (17) BT(B) OR DH_QB(s(Q)+1, s(B)+1) for all A ∈ W\{Q, R}
        val losersMinusQR = losingParties - setOf(Q, R)
        losersMinusQR.forEach { loser ->
            assorters.add(btOrBh(Q, winnerSeatCount[Q.id]!! + 1, loser, winnerSeatCount[loser.id]!! + 1, votes))
        }

        // (18) BT(B) OR DH_AR(s(A), s(R)) for all B ∈ W\{Q, R}
        val winnersMinusQR = winningParties - setOf(Q, R)
        assorters.addAll(btOrBhs(winnersMinusQR, R, votes))

        // (19) DH_R,Q (s(R), s(Q) + 2)
        makeDhAssorterFromDivisors(info, R, winnerSeatCount[R.id]!!, Q, winnerSeatCount[Q.id]!! + 2, Nc)

        // (20) DH_R,Q (s(R)-1, s(Q)+1)
        makeDhAssorterFromDivisors(info, R, winnerSeatCount[R.id]!! - 1, Q, winnerSeatCount[Q.id]!! + 1, Nc)

        return DhondtContest(
            info,
            votes,
            this.Nc,
            Ncast = this.validVotes + this.undervotes,
            parties,
            sortedScores,
            assorters,
            thresholdOverride,
        )
    }
}