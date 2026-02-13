package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.betting.ClcaErrorTracker
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting2
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.roundUp
import kotlin.math.ln
import kotlin.test.Test

class TestOneAuditAssortValueRates {

    @Test
    fun showPayoffs() {
        val maxBet = 1.8
        show(1.0, .52, maxBet)
        show(.80, .52, maxBet)
        show(1.5, .52, maxBet)
    }

    fun show(upper: Double, poolAvg: Double, maxBet: Double) {
        val poolMargin = 2.0 * poolAvg - 1.0

        println("poolAvg=$poolAvg upper=${df(upper)}")
        val taus = TausOA(upper = upper, poolAvg = poolAvg)

        // bassort = [1-poolAvg/u,
        //            (u - poolAvg + .5)/u,
        //            (2u - poolAvg)/u] * noerror, for mvr loser, other and winner
        // bassort = [1-poolAvg, 1.5 - poolAvg, 2 - poolAvg] * noerror  when u == 1
        println("taus=[1-poolAvg/u, (u + .5 - poolAvg)/u, (2u - poolAvg)/u]")
        taus.tausOA.forEach{ println("  $it") }
        // (0.45, loser)
        // (0.95, other)
        // (1.45, winner) * noerror

        val noerror: Double = 1.0 / (2.0 - poolMargin / upper) // clca assort value when no error
        println("noerror=${df(noerror)} for margin=${df(poolMargin)} upper=${df(upper)}")

        println("bassort = taus * noerror")
        taus.tausOA.forEach{ println("  ${it.second} == ${df(it.first * noerror)}") }

        // payoff == 1.0 + maxBet * (bassort - mui)
        val mui = 0.5 /// approx
        println("payoff = 1.0 + maxBet * (bassort - mui) where maxBet = ${df(maxBet)}")
        taus.tausOA.forEach {
            val bassort = it.first * noerror
            val payoff = 1.0 + maxBet * (bassort - mui)
            println("  ${it.second} == ${df(payoff)}")
        }
        println("--------------------")
    }

    @Test
    fun showEst() {
        val upper = 1.0
        val poolAvg = .52  // diluted
        val poolPct = .20
        showEst(upper, .52, 1.8, poolPct, otherPoolVotes = 0, Npop=10_000) // -581
        showEst(upper, .52, 1.8, poolPct, otherPoolVotes = 0, Npop=100_000) // -581
        showEst(upper, .52, 1.8, poolPct, otherPoolVotes = 10_000, Npop=100_000) // 637
        showEst(upper, .54, 1.8, poolPct, otherPoolVotes = 0, Npop=100_000) // 235
        showEst(upper, .52, 1.6, poolPct, otherPoolVotes = 0, Npop=100_000) // -1663
        showEst(upper, .52, 1.2, poolPct, otherPoolVotes = 0, Npop=100_000) // 1235
        showEstOptimalBet(upper, .52, 1.8, poolPct, otherPoolVotes = 0, Npop=100_000) // 765, optimalBet = 0.756
    }

    fun showEst(upper: Double, poolAvg: Double, maxBet: Double, poolPct: Double, otherPoolVotes: Int = 100, Npop: Int = 10000) {
        println("poolAvg=$poolAvg upper=${df(upper)} maxBet=${df(maxBet)} poolPct=${df(poolPct)}")
        println("        otherPoolVotes=$otherPoolVotes Npop=$Npop\n")

        val poolMargin = 2.0 * poolAvg - 1.0

        val taus = TausOA(upper = upper, poolAvg = poolAvg)
        val noerror: Double = 1.0 / (2.0 - poolMargin / upper) // clca assort value when no error

        println("bassort = taus * noerror")
        taus.tausOA.forEach{ println("  ${it.second} == ${df(it.first * noerror)}") }

        // payoff == 1.0 + maxBet * (bassort - mui)
        val mui = 0.5 /// approx
        println("payoff = 1.0 + maxBet * (bassort - mui) where maxBet = ${df(maxBet)}")
        taus.tausOA.forEach {
            val bassort = it.first * noerror
            val payoff = 1.0 + maxBet * (bassort - mui)
            println("  ${it.second} == ${df(payoff)}")
        }

        val Npopd = Npop.toDouble()
        val ncvrs = Npopd * (1 - poolPct)
        val npool = Npopd * poolPct
        val npoolCast = npool - otherPoolVotes
        val winnerVotes = poolAvg * npoolCast
        val loserVotes = (1 - poolAvg) * npoolCast

        val ps = mutableListOf<Double>()
        ps.add(loserVotes / Npopd) // loser
        ps.add(otherPoolVotes / Npopd) // other
        ps.add(winnerVotes / Npopd) // winner

        println("rates")
        val p0 = 1.0 - ps.sum()
        println("  noerror = $p0")
        println("    loser = ${ps[0]} ($loserVotes)")
        println("    other = ${ps[1]} ($otherPoolVotes)")
        println("   winner = ${ps[2]} ($winnerVotes)")

        val noerrorTerm = ln(1.0 + maxBet * (noerror - 0.5)) * p0

        println("ln payoff  (bassort, payoff, lnpayoff)")
        println("   noerror = ${noerrorTerm}")

        var sumOneAuditTerm = 0.0
        taus.tausOA.forEachIndexed { idx, it ->
            val payoff = 1.0 + maxBet * (it.first * noerror - 0.5) // 1.0 + maxBet * (bassort - mui)
            val payoffln = ln(payoff)
            val payofflnp = payoffln * ps[idx]
            sumOneAuditTerm += payofflnp
            println("    ${it.second} =  ${payofflnp} (${it.first * noerror} ${payoff} ${payoffln})")
        }
        println("     sumOA = ${sumOneAuditTerm}")

        val lnPayoff = noerrorTerm + sumOneAuditTerm
        println("    sumAll = ${lnPayoff}")

        val alpha = .05
        val est =  roundUp((-ln(alpha) / lnPayoff))

        println("estimated number of samples = -ln(alpha) / lnPayoff = ${-ln(alpha)} / ${lnPayoff}")
        println("estimated number of samples = ${est}")
        println("--------------------")
    }

    fun showEstOptimalBet(upper: Double, poolAvg: Double, maxBet: Double, poolPct: Double, otherPoolVotes: Int = 100, Npop: Int = 10000) {
        println("poolAvg=$poolAvg upper=${df(upper)} maxBet=${df(maxBet)} poolPct=${df(poolPct)}")
        println("        otherPoolVotes=$otherPoolVotes Npop=$Npop\n")

        val poolMargin = 2.0 * poolAvg - 1.0

        val taus = TausOA(upper = upper, poolAvg = poolAvg)
        val noerror: Double = 1.0 / (2.0 - poolMargin / upper) // clca assort value when no error

        println("bassort = taus * noerror")
        taus.tausOA.forEach{ println("  ${it.second} == ${df(it.first * noerror)}") }

        // payoff == 1.0 + maxBet * (bassort - mui)
        val mui = 0.5 /// approx
        println("payoff = 1.0 + maxBet * (bassort - mui) where maxBet = ${df(maxBet)}")
        taus.tausOA.forEach {
            val bassort = it.first * noerror
            val payoff = 1.0 + maxBet * (bassort - mui)
            println("  ${it.second} == ${df(payoff)}")
        }

        val Npopd = Npop.toDouble()
        val ncvrs = Npopd * (1 - poolPct)
        val npool = Npopd * poolPct
        val npoolCast = npool - otherPoolVotes
        val winnerVotes = poolAvg * npoolCast
        val loserVotes = (1 - poolAvg) * npoolCast

        val ps = mutableListOf<Double>()
        ps.add(loserVotes / Npopd) // loser
        ps.add(otherPoolVotes / Npopd) // other
        ps.add(winnerVotes / Npopd) // winner

        println("rates")
        val p0 = 1.0 - ps.sum()
        println("  noerror = $p0")
        println("    loser = ${ps[0]} ($loserVotes)")
        println("    other = ${ps[1]} ($otherPoolVotes)")
        println("   winner = ${ps[2]} ($winnerVotes)")

        // assort value -> rate
        val rates = mapOf(
            taus.tausOA[0].first * noerror to ps[0],
            taus.tausOA[1].first * noerror to ps[1],
            taus.tausOA[2].first * noerror to ps[2],
            )
        println("rates = $rates")

        // data class OneAuditAssortValueRates(val rates: Map<Double, Double>, val totalInPools: Int) {
        val oaAssortRates = OneAuditAssortValueRates(rates, npool.toInt())

        // data class GeneralAdaptiveBetting2(
        //    val Npop: Int, // population size for this contest
        //    val aprioriCounts: ClcaErrorCounts, // apriori counts not counting phantoms, non-null so we have noerror and upper
        //    val nphantoms: Int, // number of phantoms in the population
        //    val maxLoss: Double, // between 0 and 1; this bounds how close lam can get to 2.0; maxBet = maxLoss / mui
        //
        //    val oaAssortRates: OneAuditAssortValueRates? = null, // non-null for OneAudit
        //    val d: Int = 100,  // trunc weight
        //    val debug: Boolean = false,
        val betFun = GeneralAdaptiveBetting2(
            Npop = Npop,
            aprioriCounts = ClcaErrorCounts.empty(noerror, upper),
            nphantoms = 0,
            maxLoss = maxBet/2,
            oaAssortRates = oaAssortRates,
            d = 100,
            debug=false,
        )

        val betFnOld = GeneralAdaptiveBetting(
            Npop,
            ClcaErrorCounts.empty(noerror, upper),
            0,
            oaAssortRates = oaAssortRates,
            maxLoss = maxBet/2,
            debug=false,
        )
        val optimalBet = betFun.bet(ClcaErrorTracker(noerror, upper))
        println("optimalBet = $optimalBet")

        val noerrorTerm = ln(1.0 + optimalBet * (noerror - 0.5)) * p0

        println("ln payoff  (bassort, payoff, lnpayoff)")
        println("   noerror = ${noerrorTerm}")

        var sumOneAuditTerm = 0.0
        taus.tausOA.forEachIndexed { idx, it ->
            val payoff = 1.0 + optimalBet * (it.first * noerror - 0.5) // 1.0 + maxBet * (bassort - mui)
            val payoffln = ln(payoff)
            val payofflnp = payoffln * ps[idx]
            sumOneAuditTerm += payofflnp
            println("    ${it.second} =  ${payofflnp} (${it.first * noerror} ${payoff} ${payoffln})")
        }
        println("     sumOA = ${sumOneAuditTerm}")

        val lnPayoff = noerrorTerm + sumOneAuditTerm
        println("    sumAll = ${lnPayoff}")

        val alpha = .05
        val est =  roundUp((-ln(alpha) / lnPayoff))

        println("estimated number of samples = -ln(alpha) / lnPayoff = ${-ln(alpha)} / ${lnPayoff}")
        println("estimated number of samples = ${est}")
        println("--------------------")
    }


}