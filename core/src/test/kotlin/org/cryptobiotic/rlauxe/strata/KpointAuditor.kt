package org.cryptobiotic.rlauxe.strata

import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.roundToClosest
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

private val showPoints = false
private val showMarts = false

// dont use  "transformed overstatement assorters", use real assorters
class KPointAuditor(
    val Nk: List<Int>,   // a length-K list of the size of each stratum
    val Ac: List<Double>, // a length-K np.array of floats the reported assorter mean bar{A}_c in each stratum
    val n_bands: Int = 10,
    val alpha:Double = 0.05, // the significance level of the test
    val reps: Int = 1, // the number of simulations of the audit to run
    val p_2: DoubleArray =  doubleArrayOf(0.0, 0.0),  // a length-K np.array of floats the true rate of 2 vote overstatements in each stratum, defaults to none
    val p_1: DoubleArray =  doubleArrayOf(0.0, 0.0), // a length-K np.array of floats the true rate of 1 vote overstatements in each stratum, defaults to none
    val show: Boolean = false,
) {
    val K = Nk.size
    val N = Nk.sumOf { it }
    val wk = Nk.map { it / N.toDouble() }
    val margin = Ac.map { 2 * it - 1.0 }
    val noerror = margin.map { 1.0 / (2.0 - it) }

    val reportedMean = numpy_dotDD(wk, Ac)
    val etaPoints: List<KPoint>
    val ptValues = mutableListOf<List<Double>>() // ptValues(K, N)

    init {
        if (show) {
            println("BandedAuditor2 Nk = $Nk wk = ${show(wk)} ")
            println(" Ac = ${show(Ac)} margin_k=${show(margin)} noerror_k=${show(noerror)}")
            val v = 2 * reportedMean - 1
            val noerror =
                1.0 / (2.0 - v) //  where the pointmass would be for a global (unstratified) CCA TODO not used!
            println("mu= ${dfn(reportedMean, 3)} margin=${dfn(v, 3)} noerror=${dfn(noerror, 3)}")
            println("----------------------")
        }
        require(Ac.max() <= 1.0 && Ac.min() >= 0.0) { "reported assorter margin is not in [0,1]" }
        require(Nk.min() >= 1) { "N (population size) must be no less than 1 in all strata" }

        etaPoints = constructEtaPoints()

        repeat(K) { k ->
            val margin = 2 * Ac[k] - 1.0
            val noerror = 1.0 / (2.0 - margin)
            if (show) println("stratum $k: Ac= ${df(Ac[k])} margin=${df(margin)} noerror=${df(noerror)} upper=${df(2 * noerror)}")

            val p2n = roundToClosest(Nk[k] * p_2[k])
            val p1n = roundToClosest(Nk[k] * p_1[k])
            val noerrorn = Nk[k] - p2n - p1n

            // TODO investigate as we add in errors
            val p2assorts = List(p2n) { 0.0 }// assort value 0
            val p1assorts = List(p1n) { noerror / 2 } // assort value noerror/2
            val noerrors = List(noerrorn) { noerror }

            ptValues.add(p2assorts + p1assorts + noerrors)
        }
    }

    fun runAudit(): Double {
        val lnAlpha = -ln(alpha)

        val Xshuffled: List<List<Double>> = List(K) { k -> ptValues[k].toMutableList().shuffled() } // Xshuffled(K, N)
        val log = true

        // intersection marts, (kpoints, N+1)
        val marts = kpointUits(Xshuffled, Nk, etaPoints, log = log)

        // for each time, find min across points and return its index in IntArray(time)
        val minPointIndex: IntArray = findMinBand(marts)  // should be (401), index of smallest mart over all the bands

        val optimalMarts = DoubleArray(N + 1) // (N+1)
        for (i in 0..N) {
            optimalMarts[i] = marts[minPointIndex[i]][i]
        }

        val firstIndex = if (log) {
            findFirst(optimalMarts) { it > lnAlpha } ?: N
        } else {
            findFirst(optimalMarts) { it > 1 / alpha } ?: N
        }

        if (showMarts) {
            val martsT = numpy_transpose(marts) // (N+1, kpoints)
            for (t in 0 until firstIndex) {
                val mart = martsT[t]
                println("$t ${showMin(mart, minPointIndex[t])} index=${minPointIndex[t]} mart=${optimalMarts[t]} ")
            }
        }

        val thresh = if (log) lnAlpha else 1 / alpha

        val minPoint = minPointIndex[firstIndex]
        val optimalPoint = etaPoints[minPoint]
        val optimalMart = optimalMarts[firstIndex]
        val mu = numpy_dotDD(wk, optimalPoint.point.toList())
        if (show) println("thresh=$thresh pointIndex=${minPoint} optimalPoint = ${optimalPoint} mu=$mu optimalMart=$optimalMart samplesNeeded=$firstIndex")

        if (showPoints) {
            println("kpoints")
            etaPoints.forEachIndexed { idx, kpoint ->
                val star = if (idx == minPoint) "**" else ""
                println("$idx $star$kpoint")
            }
            println()
        }

        return firstIndex.toDouble()
    }

    // Ac: the reported assorter mean in each stratum, size = K
    // Nk: the size of the population within each stratum, size = K
    // n_bands: the number of equal-width bands in the tesselation of the null boundary
    fun constructEtaPoints(): List<KPoint> {
        val eta_0 = 0.5 //  the global null in terms of the original assorters

        // TODO generalize to K > 2
        require(K == 2) { "only works for two strata" }
        require(numpy_dotDD(wk, Ac) > 1 / 2) { "reported assorter mean (Ac) implies the winner lost" }

        // original: eta_1_grid = numpy_linspace(start = max(0.0, eta_0 - wk[1]), end = min(u, eta_0/wk[0]), npts = n_bands + 1) // 1D List
        val diffMean = Ac[0] - Ac[1]  // -.3
        val reportedMean0 = Ac[0]
        val start0 = max(0.0, reportedMean0 + diffMean / 2)
        val end0 = min(noerror[0], reportedMean0 - diffMean / 2)
        val eta1_grid = numpy_linspace(start = start0, end = end0, npts = n_bands + 1)

        // w · θ = 1/2 = eta0
        // 1/2 = wk1 * theta1 + wk2*theta2
        // (1/2 - wk1*theta1) = wk2 * theta2
        // (1/2 - wk1*theta1)/wk2 = theta2
        val eta2_grid = eta1_grid.map { (0.5 - wk[0] * it) / wk[1] }

        eta2_grid.zip(eta1_grid).forEachIndexed { k, (t2, t1) ->
            val term = t1 * wk[0] + t2 * wk[1]
            require(doubleIsClose(0.5, term)) { "0.5 != ${term}" }
        }

        //     B̄_k > β_k := θ_k + uA_k − Āc_k             (eq 3.2)
        //
        // where
        //
        //    θ_k is null mean in stratum k
        //    uA_k is the uA_k be the upper bound on the original (clca) assorter for stratum k = noerror
        //    Āc_k is the reported assorter mean

        //  transformed null means in stratum k
        val beta1_grid = eta1_grid.map { (it + noerror[0] - Ac[0]) }
        val beta2_grid = eta2_grid.map { it + noerror[1] - Ac[1] }

        // TODO use KPoints instead of bands I think
        val kpoints = mutableListOf<KPoint>()
        repeat(n_bands + 1) { i ->  // 2D = (row, col), so loop over the rows of beta_grid
            val points = doubleArrayOf(beta1_grid[i], beta2_grid[i])
            kpoints.add(KPoint(points))
        }

        if (show) {
            println("eta1_grid = ${show(eta1_grid)}")
            println("eta2_grid = ${show(eta2_grid)}")
            println("beta1_grid = ${show(beta1_grid)}")
            println("beta2_grid = ${show(beta2_grid)}")
            println("points")
            kpoints.forEach { println(" ${it}") }
            println()
        }

        return kpoints
    }

    // compute a product UI-TS by minimizing product I-TSMs along a grid of etas (the "band" method)
    // x(K, N), Nk(K); return Pair(optimalMart(N+1), optimalEta(N+1, K))
    fun kpointUits(x: List<List<Double>>, Nk: List<Int>, etaPoints: List<KPoint>, log: Boolean = true):
            List<List<Double>> {  // (npoints, N+1)

        // precompute strata selection for each sample in the strata
        val Stk: List<IntArray> = selector(Nk)

        val marts = mutableListOf<List<Double>>()  // (npoints, N+1)

        etaPoints.forEach { kpoint ->
            // TODO dont have to do if the bet in eta oblivious
            // precompute bet for each sample in the strata
            // bets are determined with max_eta, which makes the bets conservative for both strata and both vertices of the band
            // val bets_i = [mart(x[k], max_eta[k], bet[k], None, ws_N[k], log, output = "bets") for k in np.arange(K)]
            val bets = mutableListOf<List<Double>>()
            repeat(K) { k ->
                bets.add(inverse_eta(x[k], kpoint.point[k], Kwargs.empty))
            }

            // minima are evaluated at the endpoints of the band
            // one of which is the minimum over the whole band due to concavity
            val etas = kpoint.point.toList() // list(K)
            marts.add(
                intersectionMart(
                    xk = x,
                    Nk = Nk,
                    etak = etas,
                    lamk = bets,
                    Stk = Stk,
                    log = log,
                )
            )
        }

        return marts
    }

    // intersection mart across strata
    fun intersectionMart(
        xk: List<List<Double>>, Nk: List<Int>, etak: List<Double>, lamk: List<List<Double>>, Stk: List<IntArray>,
        log: Boolean = true,
    ): List<Double> { // (N+1)

        // here we precalculate all values of the martingale over t and k
        val ws_marts = mutableListOf<List<Double>>() // (K, N+1)
        repeat(K) { k ->
            val martk = mart(xk[k], etak[k], lamk[k], Nk[k], log) // why take the log here ?
            ws_marts.add(martk)
        }

        val selectedMarts = mutableListOf<DoubleArray>()  // (N+1, K)
        repeat(Stk.size) { t ->
            val marts_t = DoubleArray(K)
            repeat(K) { k ->
                val choice = Stk[t][k]
                marts_t[k] = ws_marts[k][choice]  // marts for time t over k
            }
//                #make all marts infinite if one is, when product is taken this enforces rule:
//                #we reject intersection null if certainly False in one stratum
//                #TODO: rethink this logic? What if the null is certainly true in a stratum?
//                #there is some more subtlety to be considered when sampling WOR
//                marts[i,:] = marts_i if not any(np.isposinf(marts_i)) else np.inf * np.ones(K)
            selectedMarts.add(marts_t)
        }

        val prodMarts = if (log) { // (N+1)
            selectedMarts.map { it.sum() }
        } else {
            selectedMarts.map { it.reduce { acc, element -> acc * element } }
        }

        return prodMarts
    }
}