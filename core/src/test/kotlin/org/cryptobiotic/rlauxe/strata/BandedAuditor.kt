package org.cryptobiotic.rlauxe.strata

import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.roundToClosest
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

private val showBands = true


class BandedAuditor(
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
    val etaBands: List<Band>
    val ptValues = mutableListOf<List<Double>>() // ptValues(K, N) for UI_TS "transformed overstatement assorters"

    init {
        if (show) {
            println("BandedAuditor Nk = $Nk wk = ${show(wk)} ")
            println(" Ac = ${show(Ac)} margin_k=${show(margin)} noerror_k=${show(noerror)}")
            val v = 2 * reportedMean - 1
            val noerror = 1.0 / (2.0 - v) //  where the pointmass would be for a global (unstratified) CCA TODO not used!
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

        etaBands = constructEtaBands(Ac, Nk, n_bands)

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

    fun runBandAudit() : Double {
        val lnAlpha = -ln(alpha)

        val Xshuffled: List<List<Double>> = List(K) { k -> ptValues[k].toMutableList().shuffled() } // Xshuffled(K, N)
        val log = true

        // Optimizer
        val (optimalMarts, minBandIndex) = bandedUits(Xshuffled, Nk, etaBands, log = log)

        val firstIndex = if (log) {
            findFirst(optimalMarts) { it > lnAlpha } ?: N
        } else {
            findFirst(optimalMarts) { it > 1/alpha } ?: N
        }

        val thresh = if (log) lnAlpha else 1/alpha

        val minBand=minBandIndex[firstIndex]
        val optimalBand = etaBands[minBand]
        val optimalMart = optimalMarts[firstIndex]
        val mu = numpy_dotDD(wk, optimalBand.centroid.point.toList())
        println("thresh=$thresh bandIndex=${minBand} optimalBand = ${optimalBand} mu=$mu optimalMart=$optimalMart samplesNeeded=$firstIndex")

        if (showBands) {
            println("bands")
            etaBands.forEachIndexed { idx, band ->
                val star = if (idx == minBand) "**" else ""
                println("$idx $star$band")
            }
            println()
        }

        return firstIndex.toDouble()
    }

    // Ac: the reported assorter mean in each stratum, size = K
    // Nk: the size of the population within each stratum, size = K
    // n_bands: the number of equal-width bands in the tesselation of the null boundary
    fun constructEtaBands(Ac: List<Double>, Nk: List<Int>, n_bands: Int = 100): List<Band> {
        require (Ac.max() <= 1.0 && Ac.min() >= 0.0) { "reported assorter margin is not in [0,1]" }
        require (Nk.min() >= 1) {"N (population size) must be no less than 1 in all strata" }

        val uOver = 2.0 // the upper bound on the overstatment assorters, per SWEETER
        // TODO should be  uOver = 2.0 / (2.0 - assorterMargin / assorter.upperBound())
        //   however if uA = 1, margin in [0,1], uO = [1,2]
        //   then 2 is the global bound, but for a specific assorter, 2.0 / (2.0 - v/u) <= 2 is tighter

        val eta_0 = 0.5 //  the global null in terms of the original assorters

        require (K == 2) { "only works for two strata" }
        // TODO generalize to K > 2

        val u = 2.0 // the upper bound on the overstatment assorters, per STS, real value is 2.0 / (2.0 - v/u)
        require (numpy_dotDD(wk, Ac) > 1/2) { "reported assorter mean (Ac) implies the winner lost" }

        // eta_grid<K> aka vertices; see Algorithm 1 in STRATIFIED "Compute I-TSMs at vertices" line 8
        // This computes the verticies at each band endpoint
        // eta_1_grid = np.linspace(max(0, eta_0 - w[1]), min(u, eta_0/w[0]), n_bands + 1)

        // these are the hypothesized null means
        val eta_1_grid = numpy_linspace(start = max(0.0, eta_0 - wk[1]), end = min(u, eta_0/wk[0]), npts = n_bands + 1) // 1D List

        // since w dot Ac = 1/2, then setting eta1 gives us eta2:
        // val eta_2_grid = (eta_0 - w[0] * eta_1_grid) / w[1]
        val eta_2_grid = eta_1_grid.map { (eta_0 - wk[0] * it) / wk[1]}

        eta_1_grid.zip(eta_2_grid).forEachIndexed { k, (t1, t2) ->
            val term = t1*wk[0] + t2*wk[1]
            require(doubleIsClose(0.5,term))  {"0.5 != ${term}"}
        }

        // 1D List
        if (show) {
            println("wk = ${show(wk)} Ac = ${show(Ac)} ")
            println("eta_1_grid = ${show(eta_1_grid)}")
            println("eta_2_grid = ${show(eta_2_grid)}")
        }

        // transformed overstatement assorters
        // the transformation is per STS, except divided by 2
        // "Before running EB, the population and null were transformed to [0,1] by dividing by uk",
        // where uk is the stratum upper value, probably 2*noerror (Sweeter p8)

        //     B̄_k > β_k := θ_k + uA_k − Āc_k             (eq 3.2)
        //
        // where
        //
        //    θ_k is null mean in stratum k
        //    uA_k is the uA_k be the upper bound on the original (clca) assorter for stratum k = noerror
        //    Āc_k is the reported assorter mean

        // the division by 2 allows a plurality CCA population to be defined on [0,1] instead of [0,2]
        // val beta_1_grid = (eta_1_grid + 1 - Ac[0])/2 //  transformed null means in stratum 1
        val beta_1_grid = eta_1_grid.map { (it + 1 - Ac[0])/2} //  transformed null means in stratum 1
        // val beta_2_grid = (eta_2_grid + 1 - Ac[1])/2  //  transformed null means in stratum 2
        val beta_2_grid = eta_2_grid.map { (it + 1 - Ac[1])/2 } //  transformed null means in stratum 2

        val beta_grid: List<Pair<Double, Double>> = beta_1_grid.mapIndexed{ idx, beta1 -> Pair(beta1, beta_2_grid[idx]) }
        if (show) {
            println("beta_1_grid = ${show(beta_1_grid)}")
            println("beta_2_grid = ${show(beta_2_grid)}")
            print("beta_grid =")
            beta_grid.forEach{ print("[${dfn(it.first, 3)}, ${dfn(it.second, 3)}], ") }
            println()
        }

        val bands = mutableListOf<Band>()
        repeat(n_bands) { i ->  // 2D = (row, col), so loop over the rows of beta_grid
            val startpoint = beta_grid[i]
            val endpoint = beta_grid[i+1]
            val centroid = doubleArrayOf( (startpoint.first + endpoint.first)/2, (startpoint.second + endpoint.second)/2 )
            bands.add(Band(KPoint(startpoint), KPoint(endpoint), KPoint(centroid)))
        }
        return bands
    }

    // compute a product UI-TS by minimizing product I-TSMs along a grid of etas (the "band" method)
    // x(K, N), Nk(K); return Pair(optimalMart(N+1), optimalEta(N+1, K))
    fun bandedUits(x: List<List<Double>>, Nk: List<Int>, etaBands: List<Band>, log: Boolean = true):
            Pair<DoubleArray, IntArray> { // Pair(optimalMart(N+1), optimalEta(N+1, K))

        /* precompute bet for each sample in the strata
        val bets = mutableListOf<List<Double>>()
        val first_centroid: KPoint = etaBands.first().centroid // TODO why use the first? why not the reported mean ??
        repeat(K) { k ->
            bets.add( inverse_eta(x[k], first_centroid.point[k], Kwargs.empty))
        } */

        // precompute strata selection for each sample in the strata
        val Stk: List<IntArray> = selector(Nk)

        val marts = mutableListOf<List<Double>>()  // (nbands, N+1)
        // val sel = mutableListOf<List<IntArray>>()

        // loop over bands
        etaBands.forEach { band ->
            // val centroid_eta = etaBands[i].centroid

            // find largest eta in the band for each stratum
            val max_eta = mutableListOf<Double>()
            repeat(K) { k ->
                max_eta.add(max(band.startpoint.point[k], band.endpoint.point[k]))
            }

            // precompute bet for each sample in the strata
            // bets are determined with max_eta, which makes the bets conservative for both strata and both vertices of the band
            // val bets_i = [mart(x[k], max_eta[k], bet[k], None, ws_N[k], log, output = "bets") for k in np.arange(K)]
            val bets = mutableListOf<List<Double>>()
            repeat(K) { k ->
                bets.add( inverse_eta(x[k], max_eta[k], Kwargs.empty))
            }

            // minima are evaluated at the endpoints of the band
            // one of which is the minimum over the whole band due to concavity
            val itsm_mat = mutableListOf<List<Double>>() // (K, N+1)
            repeat(2) { j ->
                val kpoint = if (j == 0) band.startpoint else band.endpoint
                val etas = kpoint.point.toList() // list(K)
                itsm_mat.add( intersectionMart(xk = x, Nk = Nk, etak = etas, lamk = bets, Stk = Stk, log = log, running_max = true) )
            }

            //  obj[i,:] = np.min(itsm_mat, 1)
            val mat1 = itsm_mat[1]
            val itsiMin: List<Double> = itsm_mat[0].mapIndexed { idx, mat0 -> min(mat0, mat1[idx]) }
            marts.add(itsiMin)
            // sel.add(Tki) // Tki doesnt change, so why?
        }

        // for each time, find min across bands and return its index in IntArray(time)
        val minBandIndex: IntArray = findMinBand( marts )  // should be (401), index of smallest mart over all the bands

        val optimalMart = DoubleArray(N + 1) // (N+1)
        for (i in 0..N) {
            optimalMart[i] = marts[minBandIndex[i]][i]
        }

        val martsT = numpy_transpose(marts)
        martsT.forEachIndexed { t, it ->
            println("$t ${showMin(it, minBandIndex[t])} index=${minBandIndex[t]} mart=${optimalMart[t]} ")
        }

        // DoubleArray, List<DoubleArray>, Double
        return Pair(optimalMart, minBandIndex)
    }

    // x(K, N), Nk(K), etak(K), Stk: (N + 1) x K; return mart(N+1) for all t
    // Stk(N + 1) x K ;  the selections for stratum k at each time t
    // etak(2): startpoint, endpoint eta values
    fun intersectionMart(
        xk: List<List<Double>>, Nk: List<Int>, etak: List<Double>, lamk: List<List<Double>>, Stk: List<IntArray>,
        log: Boolean = true, running_max: Boolean = true,
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

        var prodMarts = if (log) { // (N+1)
            selectedMarts.map { it.sum() }
        } else {
            selectedMarts.map { it.reduce { acc, element -> acc * element } }
        }
        if (running_max)
            prodMarts = runningMaximum(prodMarts)

        return prodMarts
    }

}
