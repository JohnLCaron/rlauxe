package org.cryptobiotic.rlauxe.strata

import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.betting.ClcaErrorTracker
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.betting.populationMeanIfH0eta
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.roundToClosest
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

private val showBands = true
private val showMarts = true

// dont use  "transformed overstatement assorters", use real assorters
class BandedAuditor2(
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
    val ptValues = mutableListOf<List<Double>>() // ptValues(K, N)

    init {
        if (show) {
            println("BandedAuditor2 Nk = $Nk wk = ${show(wk)} ")
            println(" Ac = ${show(Ac)} margin_k=${show(margin)} noerror_k=${show(noerror)}")
            val v = 2 * reportedMean - 1
            val noerror = 1.0 / (2.0 - v) //  where the pointmass would be for a global (unstratified) CCA TODO not used!
            println("mu= ${dfn(reportedMean, 3)} margin=${dfn(v, 3)} noerror=${dfn(noerror, 3)}")
            println("----------------------")
        }
        require (Ac.max() <= 1.0 && Ac.min() >= 0.0) { "reported assorter margin is not in [0,1]" }
        require (Nk.min() >= 1) {"N (population size) must be no less than 1 in all strata" }

        etaBands = constructEtaBands()

        repeat(K) { k ->
            val margin = 2 * Ac[k] - 1.0
            val noerror = 1.0 / (2.0 - margin)
            if (show) println("stratum $k: Ac= ${df(Ac[k])} margin=${df(margin)} noerror=${df(noerror)} upper=${df(2*noerror)}")

            val p2n = roundToClosest(Nk[k] * p_2[k])
            val p1n = roundToClosest(Nk[k] * p_1[k])
            val noerrorn = Nk[k] - p2n - p1n

            // TODO investigate as we add in errors
            val p2assorts = List(p2n) { 0.0 }// assort value 0
            val p1assorts = List(p1n) { noerror/2 } // assort value noerror/2
            val noerrors = List(noerrorn) { noerror }

            ptValues.add(p2assorts + p1assorts + noerrors)
        }
    }

    fun runBandedAudit() : Double {
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
        if (show) println("thresh=$thresh bandIndex=${minBand} optimalBand = ${optimalBand} mu=$mu optimalMart=$optimalMart samplesNeeded=$firstIndex")

        if (show && showBands) {
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
    fun constructEtaBands(): List<Band> {
        val eta_0 = 0.5 //  the global null in terms of the original assorters

        // TODO generalize to K > 2
        require (K == 2) { "only works for two strata" }
        require (numpy_dotDD(wk, Ac) > 1/2) { "reported assorter mean (Ac) implies the winner lost" }

        // original: eta_1_grid = numpy_linspace(start = max(0.0, eta_0 - wk[1]), end = min(u, eta_0/wk[0]), npts = n_bands + 1) // 1D List
        val diffMean = Ac[0] - Ac[1]  // -.3
        val reportedMean0 = Ac[0]
        val start0 = max(0.0, reportedMean0 + diffMean/2)
        val end0 =  min(noerror[0], reportedMean0 - diffMean/2)
        val eta1_grid = numpy_linspace(start = start0, end = end0, npts = n_bands + 1)

        // w · θ = 1/2 = eta0
        // 1/2 = wk1 * theta1 + wk2*theta2
        // (1/2 - wk1*theta1) = wk2 * theta2
        // (1/2 - wk1*theta1)/wk2 = theta2
        val eta2_grid = eta1_grid.map { (0.5 - wk[0]*it)/wk[1] }

        eta2_grid.zip(eta1_grid).forEachIndexed { k, (t2, t1) ->
            val term = t1*wk[0] + t2*wk[1]
            require(doubleIsClose(0.5,term))  {"0.5 != ${term}"}
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

        val beta_grid: List<Pair<Double, Double>> = beta1_grid.mapIndexed{ idx, t1 -> Pair(t1, beta2_grid[idx]) }
        if (show) {
            println("eta1_grid = ${show(eta1_grid)}")
            println("eta2_grid = ${show(eta2_grid)}")
            println("beta1_grid = ${show(beta1_grid)}")
            println("beta2_grid = ${show(beta2_grid)}")
            print("beta_grid =")
            beta_grid.forEach{ print("[${dfn(it.first, 3)}, ${dfn(it.second, 3)}], ") }
            println()
        }

        // TODO use KPoints instead of bands I think
        val bands = mutableListOf<Band>()
        repeat(n_bands) { i ->  // 2D = (row, col), so loop over the rows of beta_grid
            val startpoint = beta_grid[i]
            val endpoint = beta_grid[i+1]
            val centroid = doubleArrayOf( (startpoint.first + endpoint.first)/2, (startpoint.second + endpoint.second)/2 )
            bands.add(Band(KPoint(startpoint), KPoint(endpoint), KPoint(centroid)))
        }
        if (show && showBands) {
            println("bands")
            bands.forEachIndexed { idx, band ->
                println(" $idx $band")
            }
            println()
        }
        return bands
    }

    // compute a product UI-TS by minimizing product I-TSMs along a grid of etas (the "band" method)
    // x(K, N), Nk(K); return Pair(optimalMart(N+1), optimalEta(N+1, K))
    fun bandedUits(x: List<List<Double>>, Nk: List<Int>, etaBands: List<Band>, log: Boolean = true):
            Pair<DoubleArray, IntArray> { // Pair(optimalMart(N+1), optimalEta(N+1, K))

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

            // TODO dont have to do if the bet in eta oblivious
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

        if (show && showMarts) {
            val martsT = numpy_transpose(marts)
            martsT.forEachIndexed { t, it ->
                println("$t ${showMin(it, minBandIndex[t])} index=${minBandIndex[t]} mart=${optimalMart[t]} ")
            }
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

    // compute a product UI-TS by minimizing product I-TSMs along a grid of etas (the "band" method)
    // x(K, N), Nk(K); return Pair(optimalMart(N+1), optimalEta(N+1, K))
    // one time at a time
    fun bandedUitsTime(x: List<List<Double>>, Nk: List<Int>, etaBands: List<Band>, log: Boolean = true):
            Pair<DoubleArray, IntArray> { // Pair(optimalMart(N+1), optimalEta(N+1, K))

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
                itsm_mat.add( intersectionMartTime(0, xk = x, Nk = Nk, etak = etas, lamk = bets, Stk = Stk, log = log, running_max = true) )
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
    fun intersectionMartTime(
        time: Int,
        xk: List<List<Double>>,
        Nk: List<Int>,
        etak: List<Double>, // exactly 2 ?
        lamk: List<List<Double>>,
        Stk: List<IntArray>,
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

class SeqAudit(val band: Band) {

}

// this has samples pushed to it rather than it pulling samples until done
class PushAudit(val eta: Double, // null mean
                val noerror: Double,
                val upperBound: Double = 1.0,
                val riskLimit: Double = .05,
                val Npop: Int,
                val Nphantoms: Int,
                val clcaConfig: ClcaConfig,
) {
    val endingTestStatistic = 1 / riskLimit

    val bettingFun : GeneralAdaptiveBetting
    val startingTestStatistic: Double = 1.0
    val errorTracker: ClcaErrorTracker

    init {
        val aprioriErrorRates = clcaConfig.apriori.makeErrorRates(noerror, upperBound)
        errorTracker = ClcaErrorTracker(noerror, upperBound)

        // use the same betting function as the real audit
        bettingFun = GeneralAdaptiveBetting(
            Npop, // population size for this contest
            aprioriErrorRates = aprioriErrorRates, // apriori rates not counting phantoms; non-null so we always have noerror and upper
            Nphantoms,
            clcaConfig.maxLoss,
            oaAssortRates=null,
        )
    }
    var status = TestH0Status.InProgress
    var testStatistic = startingTestStatistic // aka T
    var countUsed = 0

    fun pushAssortValue(assortValue: Double): Boolean {
        countUsed++

        val mui = populationMeanIfH0eta(Npop, eta, errorTracker)
        if (mui > upperBound) { // 1  # true mean is certainly less than 1/2
            status = TestH0Status.AcceptNull
            return true
        }
        if (mui < 0.0) { // 5 # true mean certainly greater than 1/2
            status = TestH0Status.SampleSumRejectNull
            return true
        }

        // we may have to make this deterministic to prove monotonic
        val bet = bettingFun.bet(errorTracker)

        val payoff = (1 + bet * (assortValue - mui))
        testStatistic *= payoff
        if (testStatistic > endingTestStatistic) {
            status = TestH0Status.StatRejectNull
            return true
        }

        errorTracker.addSample(assortValue, true)
        return false
    }

}

