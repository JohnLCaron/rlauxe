package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.persist.CardManifest
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.abs
import kotlin.math.ln
import kotlin.test.Test

class TestCorlaEstimate {
    @Test
    fun showEstimateGentle() {
        showEstimateSimple(0.05)
        showEstimateSimple(0.01)
        showEstimateSimple(0.005)
    }

    @Test
    fun testCorlaCalc() {
        val auditdir = "$testdataDir/cases/corla/clca/audit"
        val auditRecord = AuditRecord.read(auditdir)!!
        val sorted = auditRecord.contests.sortedBy { it.Nphantoms }.reversed()
        auditRecord.contests.forEach { contestUA ->
            val corlaEst = contestUA.contest.info().metadata.get("CORLAsample")
            println("contest ${contestUA.id}  corlaEst= $corlaEst nphantoms=${contestUA.Nphantoms}")
            auditRecord.rounds.forEach { round ->
                round.contestRounds.filter { it.contestUA.id == contestUA.id }.forEach { contestRound ->
                    val corlaCalc = corlaCalc(auditRecord.config, contestRound)
                    println("  round ${round.roundIdx} corlaCalc= $corlaCalc")
                }
            }
        }
    }

    @Test
    fun testCountPhantoms() {
        val auditdir = "$testdataDir/cases/corla/clca/audit"
        val auditRecord = AuditRecord.read(auditdir)!!
        val mvrManager = PersistedMvrManager(auditRecord as AuditRecord)
        countPhantoms(mvrManager.sortedManifest(), 116)
    }
}

fun countPhantoms(cardManifest: CardManifest, contestId: Int) {
    var count = 0
    var countPhantoms = 0
    var lastPhantoms = 0
    cardManifest.cards.iterator().use { cardIter ->
        while (cardIter.hasNext()) {
            val card = cardIter.next()
            if (card.isPhantom() && card.possibleContests().contains(contestId)) {
                countPhantoms++
            }
            count++
            if (count % 10000 == 0) {
                // println("$count ${countPhantoms - lastPhantoms}")
                lastPhantoms = countPhantoms
            }
        }
    }
    println("\nnphantoms = $countPhantoms for contest $contestId")
}

fun corlaCalc(config: Config, contestRound: ContestRound): SampleEst? {
    val minAssertion = contestRound.minAssertion()
    if (minAssertion == null) return null
    val lastResult = minAssertion.auditResult
    if (lastResult == null) return null

    val errorCounts = lastResult.clcaErrorTracker!!.measuredClcaErrorCounts()

    // fun estimateSampleSizeSimple(
    //    riskLimit: Double,
    //    dilutedMargin: Double,
    //    gamma: Double = 1.03905,
    //    twoOver: Int = 0,
    //    oneOver: Int = 0,
    //    oneUnder: Int = 0,
    //    twoUnder: Int = 0,
    //)

    // these 3 are oracles - we know the actual errors
    val est1 = estimateCorla(
        config.riskLimit,
        dilutedMargin = minAssertion.assertion.assorter.dilutedMargin(),
        twoOver = errorCounts.getNamedCount("p2o") ?: 0,
        oneOver = errorCounts.getNamedCount("p1o") ?: 0,
        oneUnder = errorCounts.getNamedCount("p1u") ?: 0,
        twoUnder = errorCounts.getNamedCount("p2u") ?: 0,
    )

    val est2 = estimateSampleSizePayloads(
        config.riskLimit,
        errors = errorCounts
    )

    if (contestRound.contestUA.id == 116)
        print("")

    val maxLoss = 1.0 / 1.03905
    val cassertion = minAssertion.assertion as ClcaAssertion
    val  est3 = cassertion.cassorter.sampleSizeWithErrors(2 * maxLoss, config.riskLimit,
        clcaErrorRates = errorCounts.clcaErrorRates())

    // not oracle, needs phantoms added to apriori
    val est4= minAssertion.calcNewMvrsNeeded(contestRound.contestUA, config)
    if (abs(est4 - est3) > 2)
        println("  *** ${contestRound.id} $est1 $est2 $est3 $est4 ")

    return SampleEst(est1, est2, est3, est4)
}

data class SampleEst(val simple: Int, val payoffs: Int, val optimal: Int, val assertionRound: Int)

fun showEstimateSimple(dilutedMargin: Double) {
    println("margin=$dilutedMargin")

    runEstimateSimple(dilutedMargin, 0, 0, 0, 0)
    runEstimateSimple(dilutedMargin,1, 0, 0, 0)
    runEstimateSimple(dilutedMargin,10, 0, 0, 0)
    runEstimateSimple(dilutedMargin,100, 0, 0, 0)

    runEstimateSimple(dilutedMargin,0, 1, 0, 0)
    runEstimateSimple(dilutedMargin,0, 10, 0, 0)
    runEstimateSimple(dilutedMargin,0, 100, 0, 0)

    runEstimateSimple(dilutedMargin, 0, 0, 1, 0)
    runEstimateSimple(dilutedMargin, 0, 0, 10, 0)
    runEstimateSimple(dilutedMargin, 0, 0, 100, 0)

    runEstimateSimple(dilutedMargin, 0, 0, 0, 1)
    runEstimateSimple(dilutedMargin, 0, 0, 0, 10)
    runEstimateSimple(dilutedMargin, 0, 0, 0, 100)

    runEstimateSimple(dilutedMargin, 1, 1, 1, 1)
    runEstimateSimple(dilutedMargin, 5, 5, 5, 5)
    runEstimateSimple(dilutedMargin, 10, 10, 10, 10)
    runEstimateSimple(dilutedMargin, 50, 50, 50, 50)
    runEstimateSimple(dilutedMargin, 100, 100, 100, 100)

    runEstimateSimple(dilutedMargin, 1, 10, 11, 12)
    runEstimateSimple(dilutedMargin, 10, 11, 12, 13)
    runEstimateSimple(dilutedMargin, 100, 110, 111, 99)
    println()
}

// see rla-kotlin compareCorlaToRlauxe testing that our function agrees with Corla's
fun runEstimateSimple(dilutedMargin: Double, twoUnder: Int, oneUnder: Int, oneOver: Int, twoOver: Int) {
    val riskLimit = 0.05
    val gamma = 1.2

    val est2 = estimateCorla(
        riskLimit,
        dilutedMargin,
        gamma,
        twoUnder = twoUnder,
        oneUnder = oneUnder,
        oneOver = oneOver,
        twoOver = twoOver
    )

    println("   [$twoOver, $oneOver, $oneUnder, $twoUnder] -> $est2")
}

// TODO not including the phantoms ....
fun estimateSampleSizePayloads(
    alpha: Double,
    errors: ClcaErrorCounts, // (val errorCounts: Map<Double, Int>, val totalSamples: Int, val noerror: Double, val upper: Double) {
): Int {
    // noerror = 1/(2-v/u) = 1/(2-v) when u = 1
    // probably λ = 2 / gamma
    val maxRisk = 1.0 / 1.03905
    val lam = 2 * maxRisk
    val noerror = errors.noerror

    // payoff_noerror = (1 + λ * (noerror − 1/2))
    val payoffNoerror = 1 + lam * (noerror - 0.5)
    val lnPayoffNoerror = ln(payoffNoerror)

    // payoff the risk
    // payoff_noerror^n_risk > (1 / alpha)
    // n_risk = -ln(alpha) / ln(payoff_noerror)
    val n_risk = -ln(alpha) / lnPayoffNoerror

    // payoff the errors
    // payoff_tau = (1 + λ * (tau * noerror − 1/2))
    // payoff_noerror^n_tau * payoff_tau = 1.0                             (eq 1)
    // n_tau = -ln(payoff_tau) / ln(payoff_noerror)

    // estimated samples =
    // n = n_risk + Sum_taus { count * nTaus }

    val sumNTaus = errors.errorCounts.map { (bassort, count) ->
        val payoff = 1 + lam * (bassort - 0.5)
        val n_tau = -ln(payoff) / lnPayoffNoerror
        count * n_tau
    }.sum()

    return roundUp(n_risk + sumNTaus )
}
