package org.cryptobiotic.rlauxe.strata

import org.apache.commons.math3.analysis.MultivariateFunction
import org.apache.commons.math3.optim.InitialGuess
import org.apache.commons.math3.optim.MaxEval
import org.apache.commons.math3.optim.PointValuePair
import org.apache.commons.math3.optim.SimpleBounds
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.BOBYQAOptimizer
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.roundToClosest
import kotlin.collections.contentToString
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

class BOBYQAAuditor(
    val Nk: List<Int>,   // a length-K list of the size of each stratum
    val Ac: List<Double>, // a length-K np.array of floats the reported assorter mean bar{A}_c in each stratum
    val alpha:Double = 0.05, // the significance level of the test
    val reps: Int = 1, // the number of simulations of the audit to run
    val p_2: DoubleArray =  doubleArrayOf(0.0, 0.0),  // a length-K np.array of floats the true rate of 2 vote overstatements in each stratum, defaults to none
    val p_1: DoubleArray =  doubleArrayOf(0.0, 0.0), // a length-K np.array of floats the true rate of 1 vote overstatements in each stratum, defaults to none
    val show: Boolean = false,
) {
    val K = Nk.size
    val N = Nk.sumOf { it }
    val wk = Nk.map { it / N.toDouble() }

    val reportedMean = numpy_dotDD(wk, Ac)
    val ptValues = mutableListOf<List<Double>>() // ptValues(K, N) for UI_TS "transformed overstatement assorters"

    init {
        if (show) {
            val v_k = Ac.map { 2 * it - 1 }
            val noerror_k = v_k.map { 1.0 / (2.0 - it) }
            println("Ac = ${show(Ac)} margin_k=${show(v_k)} noerror_k=${show(noerror_k)}")
            val v = 2 * reportedMean - 1
            val noerror =
                1.0 / (2.0 - v) //  where the pointmass would be for a global (unstratified) CCA TODO not used!
            println("mu= ${dfn(reportedMean, 3)} margin=${dfn(v, 3)} noerror=${dfn(noerror, 3)}")
            println("----------------------")
        }
        // TODO these calculations assume primitive assorter upper bound = 1
        // val ua = 1.0 // primitive asorter upper bound
        // val noerroru = 1.0 / (2.0 - v / ua)
        // 1/(2-v) = 1/2
        // 1 = 2-v/2
        // v/2 = 1
        // v = 2 ??

        // TODO and that the clca assorter in (0..1) instead of (0..2*noerror)
        // ["p2o", "p1o", "noerror", "p1u", "p2u"] = [0, .5, 1, 1.5, 2]*noerror
        // to normalize to (0..1), divide by 2*noerror
        // ["p2o", "p1o", "noerror", "p1u", "p2u"] = [0, .25, .5, .75, 1]

        // TODO but here we have
        // ["p2o", "p1o", "noerror"] = [0, .5, 1]
        // should be
        // ["p2o", "p1o", "noerror"]] = [0, .25, .5]
        // TODO but maybe thats ok ?? Basically they have normalized to (0..2) instead of (0..1) or (0..2*noerror)
        // probably the main place there might be a problem is if other = 1/2, 1, or noerror/2
        // also the calculation of nsamples should be -ln(alpha) / ln(1.0 + bet * (x - mu))
        //         x: length-n_k np.array with elements in [0,1]

        repeat(K) { k ->
            val p2n = roundToClosest(Nk[k] * p_2[k])
            val p1n = roundToClosest(Nk[k] * p_1[k])
            val noerrorn = Nk[k] - p2n - p1n

            // TODO this gives assort values p2o = 0, p1o = 1/4, noerror = 1/2
            //     should be   assort values p2o = 0, p1o = noerror/2, noerror = noerror, where noerror is for the kth strata
            //     perhaps these values are multiplied by 2 * noerror (ie upper) somewhere
            val p2assorts = List<Double>(p2n) { 0.0 }// assort value 0
            val p1assorts = List<Double>(p1n) { 0.25 } // assort value noerror/2
            val noerrors = List<Double>(noerrorn) { 0.5 }

            ptValues.add(p2assorts + p1assorts + noerrors)
        }
    }

    fun runObjectiveOptimizer() : Double {
        val Xshuffled: List<List<Double>> = List(K) { k -> ptValues[k].toMutableList().shuffled() } // Xshuffled(K, N)
        val Stk: List<IntArray> = selector(Nk)

        val bets = mutableListOf<List<Double>>()
        repeat(K) { k ->
            bets.add( inverse_eta(Xshuffled[k], Ac[k], Kwargs.empty))
        }

        // class Optimizer(
        //    val xk: List<List<Double>>,
        //    val Nk: List<Int>,
        //    val lamk: List<List<Double>>, // precalculated bets
        //    val Stk: List<IntArray>, // strataSelectors
        //    val log: Boolean,
        //    val alpha: Double,
        //)
        val optimizer = Optimizer(Xshuffled, Nk, lamk=bets, Stk, log=true, alpha)

        val (optimalEta, samplesNeeded) = optimizer.solve(Ac.toDoubleArray())
        println("optimalEta = ${optimalEta.contentToString()} samplesNeeded=$samplesNeeded")

        return samplesNeeded
    }
}

// Alpha 5.2 "Supermartingale-based tests of intersection hypotheses"
// To audit a given assertion, we need to check whether there is _any_ µ = (µ1..µS ) ∈ [0, u]^S
// with µ̃ ≤ 1/2 for which maxj Tj (µ) < 1/α. If there is, sampling needs to continue.
// We thus need to find
//  (30)    PS_j := max { µ∈[0,u]^S : µ̃ ≤ 1/2 { max Tj (µ) over j}−1 }
// the solution to a finite-dimensional optimization problem.

// Sweeter 3.3 Union-intersection tests
// Consider a fixed vector θ of within-stratum nulls. Let P (θ) be a valid
// P-value for the intersection null µ ≤ θ. We can reject the
// union-intersection null (1) if we can reject the intersection null for all feasible θ
// in the half-space w dot θ ≤ 1/2.
// Equivalently, P(θ) maximized over feasible θ is a P-value for eq (1):
//    P∗ := max {P(θ): 0 ≤ θ ≤ u and w dot θ ≤ 1/2}. (max over θ)

// Sweeter 3.5 Intersection supermartingales
//
// ALPHA derives a simple form for the P-value for an intersection null when
// supermartingales are used as test statistics within strata. Let M_k (θ_k) be a
// supermartingale constructed from n_k samples drawn from stratum k when the
// null µ_k ≤ θ_k is true. Then the product of these supermartingales is also a
// supermartingale under the intersection null, so its reciprocal (truncated above at 1) is a valid P-value:
//    P_M(θ) := 1 ∧ Prod { M_k (θ_k) }^(-1)
// Maximizing PM(θ) (equivalently, minimizing the intersection supermartingale) yields PM*, a valid P-value for (1).

// keep everything the same except eta0, to guarentee convexity
class Optimizer(
    val xk: List<List<Double>>,
    val Nk: List<Int>,
    val lamk: List<List<Double>>, // precalculated bets
    val Stk: List<IntArray>, // strataSelectors
    val log: Boolean,
    val alpha: Double,
) {
    val N = Nk.sum()
    val K = Nk.size
    val ialpha = 1.0 / alpha
    val lnAlpha = -ln(alpha)
    val wk = Nk.map { it / N.toDouble() }

    fun solve(startPoint: DoubleArray): Pair<DoubleArray, Double> {

        val objFunction = MultivariateFunction { lam -> samplesNeeded(lam) }  // The function to be optimized

        // i think you need to constrain to theta dot weight = 1/2
        val lowerBounds = DoubleArray( K ) { k -> 0.75*startPoint[k] }
        val upperBounds = DoubleArray( K ) { k -> 1.25*startPoint[k] }

        val n = 2
        val optimizer = BOBYQAOptimizer(2 * n + 1)

        val optimum: PointValuePair = optimizer.optimize(
            MaxEval(1000),
            ObjectiveFunction(objFunction),
            GoalType.MINIMIZE,
            InitialGuess(startPoint),
            SimpleBounds(lowerBounds, upperBounds)
        )
        val optimalEta = optimum.getPoint()

        return Pair(optimalEta, samplesNeeded(optimalEta))
    }

    // eta0(k)
    fun samplesNeeded(theta: DoubleArray): Double {

        val mu = numpy_dotDD(wk, theta.toList())
        if (mu < 0.5) {
            println("** eta0 = ${theta.contentToString()} mu=$mu")
            return N.toDouble()
        }

        val ws_marts = mutableListOf<List<Double>>() // (K, N+1)
        repeat(K) { k ->
            val martk = mart(xk[k], theta[k], lamk[k], Nk[k], log) // why take the log here ?
            ws_marts.add(martk)
        }

        val selectedMarts = mutableListOf<DoubleArray>()  // (N+1, K)
        repeat(Stk.size) { t ->
            val marts_t = DoubleArray(K)
            repeat(K) { k ->
                val choice = Stk[t][k]
                marts_t[k] = ws_marts[k][choice]  // marts for time t over k
            }
            selectedMarts.add(marts_t)
        }

        val firstIndex = if (log) { // (N+1)
            val prodMarts = selectedMarts.map { it.sum() }
            findFirst(prodMarts.toDoubleArray()) { it > lnAlpha } ?: N
        } else {
            val prodMarts = selectedMarts.map { it.reduce { acc, element -> acc * element } }
            findFirst(prodMarts.toDoubleArray()) { it > ialpha } ?: N
        }

        println("eta0 = ${theta.contentToString()} mu=$mu sampleSize=$firstIndex")
        return firstIndex.toDouble()
    }
}