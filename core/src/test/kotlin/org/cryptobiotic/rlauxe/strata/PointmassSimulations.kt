package org.cryptobiotic.rlauxe.strata

import org.cryptobiotic.rlauxe.port.calcCumulativeSum
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.roundToClosest
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/*
In a stratified audit, the population of ballot cards is partitioned into K disjoint strata.
Stratum k contains N_k ballot cards, so N = Sum { N_k }.

N = Nk.sum()
Nk: List<K> // the size of each stratum (immutable)
    length-K list of ints

wk: List<Double>: The weight of stratum k is w_k := N_k /N.
    length-K list of double

x: List<MutableList<Double>> // the data sampled from each stratum
    length-K list of length-n_k mutableList with elements in [0,1]

As of time t, the _sampling depth_ in stratum k is the number of items in Y^t that came from stratum k, denoted T_k(t).

Tk: IntArray<K> // the number of samples in Y^t that came from stratum k (mutable)
    length-K array of ints
    x[i].size = Tk[i]

Consider a single stratum with true mean µ*_k and null mean eta_k . We construct a process (M_kt)t∈N that is a TSM with respect to the
samples (X_kt )t∈N under the stratum null µ*_k ≤ eta_k . The conditional stratumwise null mean eta_kt
is the mean of the values remaining in X_k at time t if the null is true. (n_kt is "populationMeanIfH0")

etak: List<K> // the intersection null for each strata
    length-K list of double

An _interleaving_ of samples across strata is a stochastic process (Y_t) indexed by discrete
time t; Y^t := (Y_i)i=1..t is the t-prefix of (Y_i)i∈N.

x: List<MutableList<Double>> // the data sampled from each stratum
Nk: List<K> // the size of each stratum (immutable)
Tk: IntArray<K> // the number of samples in Y^t that came from stratum k (mutable)
etak: List<K> // the null hypothesis mean for each strata
Y = stratum selection across all strata, Y(t) = List<N>

*/

//        x: length-K list of length-n_k np.arrays with elements in [0,1]
//            the data sampled from each stratum
//        N: length-K list of ints
//            the size of each stratum
//        etas: list of 2-tuples, each with etas to test and etas to construct bets / allocations
//            intersection nulls over which the minimum will be taken

val nonadaptive_allocations = listOf("round_robin", "proportional_round_robin", "neyman", "more_to_larger_means", "greedy_kelly")

class PointmassSimulations {
    //alt_grid = np.linspace(0.51, 0.75, 20)
    //#alt_grid = [0.505, 0.51, 0.52, 0.53, 0.55, 0.6, 0.65, 0.7, 0.75] // wtf?
    //delta_grid = [0, 0.5]

    // the different "alternate (not null)" etas
    val alt_grid = numpy_linspace(0.51, 0.75, 20)
    val delta_grid = listOf( 0.0, 0.5)

    //alpha = 0.05
    //eta_0 = 0.5
    //n_bands_grid = [1, 3, 10, 100, 500]
    val alpha = .05
    val eta_0 = .5

    // wtf?
    val n_bands_grid = listOf(1, 3, 10, 100, 500)

    //methods_list = ['uinnsm_product', 'lcb']
    val inference = "ui-ts" // = listOf("uinnsm_product", "lcb")
    val bet = "inverse" // listOf("fixed_predictable", "agrapa", "inverse")
    val selection = "round_robin" // , "predictable_kelly", "greedy_kelly")

    val points = 100
    val K = 2
    val Nk = listOf(200, 200)

    init {
        println(alt_grid)
        require (alt_grid.last() == 0.75)
    }

    fun run() {
//
//for alt, delta, method, bet, allocation, n_bands in itertools.product(alt_grid, delta_grid, methods_list, bets_list, allocations_list, n_bands_grid):
    alt_grid.forEach {  alt ->
        delta_grid.forEach {  delta ->
            n_bands_grid.forEach {  n_bands -> // needed ?

                val A_c = listOf(alt - 0.5*delta, alt + 0.5*delta)
                val stopwatch = Stopwatch()
                var sample_size = 0
                val sampleSize =
                    simulate_plurcomp(
                        Nk = Nk,
                        A_c = A_c,
                        bet = bet,
                        selection = selection,
                        inference = inference,
                        n_bands = n_bands,
                        alpha = alpha,
                        // WOR = false, // TODO
                        reps = 1
                    )
                }
            }
        }
    }

    // write result to csv file
    //results = pd.DataFrame(results)
    //results.to_csv("point_mass_results.csv", index = False)
}

// def simulate_plurcomp(N, A_c, p_1 = np.array([0.0, 0.0]), p_2 = np.array([0.0, 0.0]), lam_func = None, allocation_func = Allocations.proportional_round_robin, method = "ui-ts", n_bands = 100, alpha = 0.05, WOR = False, reps = 30):
//    '''
//    simulate (repateadly, if desired) a card-level comparison audit (CCA) of a plurality contest
//    given reported assorter means and overstatement rates in each stratum
//    uses the parametrization of stratified CCAs developed in https://arxiv.org/pdf/2207.03379
//    Parameters
//    ----------
//        N: a length-K list of ints
//            the size of each stratum
//        A_c: a length-K np.array of floats
//            the reported assorter mean bar{A}_c in each stratum
//        p_1: a length-K np.array of floats
//            the true rate of 1 vote overstatements in each stratum, defaults to none
//        p_2: a length-K np.array of floats
//            the true rate of 2 vote overstatements in each stratum, defaults to none
//        lam_func: callable, a function from class Bets
//            the function for setting the bets (lambda_{ki}) for each stratum / time
//        allocation_func: callable, a function from the Allocations class
//            function for allocation sample to strata for each eta
//        method: string, either "ui-ts" or "lcbs"
//            the method for testing the global null
//            either union-intersection testing or combining lower confidence bounds as in Wright's method
//        alpha: float in (0,1)
//            the significance level of the test
//        WOR: boolean
//            should the martingales be computed under sampling without replacement?
//        reps: int
//            the number of simulations of the audit to run
//    Returns
//    ----------
//        two scalars, the expected stopping time of the audit and the global sample size of the audit;
//        these are the same whenever the allocation rule is nonadaptive
//    '''
//    assert method in ["lcb", "ui-ts"], "method argument is invalid"
//    K = len(N)
//    w = N/np.sum(N)
//    A_c_global = np.dot(w, A_c)
//    betas = construct_eta_bands_plurcomp(A_c, N, n_bands)
//
//    x = []
//    v = 2 * A_c_global - 1 # global diluted margin
//    a = 1/(2-v) # where the pointmass would be for a global (unstratified) CCA
//
//    #construct assorter population within each stratum
//    for k in np.arange(K):
//        num_points = [int(n_err) for n_err in saferound([N[k]*p_2[k], N[k]*p_1[k], N[k]*(1-p_2[k]-p_1[k])], places = 0)]
//        x.append(1/2 * np.concatenate([np.zeros(num_points[0]), np.ones(num_points[1]) * 1/2, np.ones(num_points[2])]))
//
//    stopping_times = np.zeros(reps) #container for global stopping times
//    sample_sizes = np.zeros(reps) #container for global sample sizes
//    for r in np.arange(reps):
//        X = [np.random.choice(x[k],  len(x[k]), replace = (not WOR)) for k in np.arange(K)] #shuffle (WOR) or sample (WR) a length-N_k sequence from each stratum k
//        if method == "ui-ts":
//            uits, eta_min, global_ss = banded_uits(X, N, betas, lam_func, allocation_func, log = True, WOR = WOR)
//            stopping_times[r] = np.where(any(uits > -np.log(alpha)), np.argmax(uits > -np.log(alpha)), np.sum(N))
//            sample_sizes[r] = global_ss[int(stopping_times[r])]
//        elif method == "lcb":
//            eta_0 = (1/2 + 1 - A_c_global)/2
//            lcb = global_lower_bound(X, N, lam_func, allocation_func, alpha, WOR = WOR, breaks = 1000)
//            stopping_times[r] = np.where(any(lcb > eta_0), np.argmax(lcb > eta_0), np.sum(N))
//            sample_sizes[r] = stopping_times[r]
//    return np.mean(stopping_times), np.mean(sample_sizes)

//  simulate a card-level comparison audit (CLCA) of a plurality contest
fun simulate_plurcomp(
    Nk: List<Int>,   // a length-K list of the size of each stratum
    A_c: List<Double>, // a length-K np.array of floats the reported assorter mean bar{A}_c in each stratum
    p_1: DoubleArray =  doubleArrayOf(0.0, 0.0), // a length-K np.array of floats the true rate of 1 vote overstatements in each stratum, defaults to none
    p_2: DoubleArray =  doubleArrayOf(0.0, 0.0),  // a length-K np.array of floats the true rate of 2 vote overstatements in each stratum, defaults to none
    bet: String, // callable, a function from class Bets the function for setting the bets (lambda_{ki}) for each stratum / time
    selection: String, // how to select which strata to sample
    inference: String, //  either "ui-ts" or "lcbs" the inference method for testing the global null
    n_bands: Int = 100,
    alpha:Double = 0.05, // the significance level of the test
    // WOR: Boolean = false, // WR not supported
    reps: Int = 30, // the number of simulations of the audit to run
) : Double {

// def simulate_plurcomp(N, A_c, p_1 = np.array([0.0, 0.0]), p_2 = np.array([0.0, 0.0]), lam_func = None, allocation_func = Allocations.proportional_round_robin, method = "ui-ts", n_bands = 100, alpha = 0.05, WOR = False, reps = 30):
//    '''
//    simulate (repateadly, if desired) a card-level comparison audit (CCA) of a plurality contest
//    given reported assorter means and overstatement rates in each stratum
//    uses the parametrization of stratified CCAs developed in https://arxiv.org/pdf/2207.03379
//    Parameters
//    Parameters
//    ----------
//        N: a length-K list of ints
//            the size of each stratum
//        A_c: a length-K np.array of floats
//            the reported assorter mean bar{A}_c in each stratum
//        p_1: a length-K np.array of floats
//            the true rate of 1 vote overstatements in each stratum, defaults to none
//        p_2: a length-K np.array of floats
//            the true rate of 2 vote overstatements in each stratum, defaults to none
//        lam_func: callable, a function from class Bets
//            the function for setting the bets (lambda_{ki}) for each stratum / time
//        allocation_func: callable, a function from the Allocations class
//            function for allocation sample to strata for each eta
//        method: string, either "ui-ts" or "lcbs"
//            the method for testing the global null
//            either union-intersection testing or combining lower confidence bounds as in Wright's method
//        alpha: float in (0,1)
//            the significance level of the test
//        WOR: boolean
//            should the martingales be computed under sampling without replacement?
//        reps: int
//            the number of simulations of the audit to run
//    Returns
//    ----------
//        two scalars, the expected stopping time of the audit and the global sample size of the audit;
//        these are the same whenever the allocation rule is nonadaptive
//    '''
//    assert method in ["lcb", "ui-ts"], "method argument is invalid"
//    K = len(N)
//    w = N/np.sum(N)
//    A_c_global = np.dot(w, A_c)
//    betas = construct_eta_bands_plurcomp(A_c, N, n_bands)
    val K = Nk.size
    val N = Nk.sumOf { it }
    val wk = Nk.map { it / N.toDouble() }

    val A_c_global = numpy_dotDD(wk, A_c) // weihjted
    val etaBands = construct_eta_bands_plurcomp(A_c, Nk, n_bands)
//
//    x = []
//    v = 2 * A_c_global - 1 # global diluted margin
//    a = 1/(2-v) # where the pointmass would be for a global (unstratified) CCA
    val v = 2 * A_c_global - 1
    val a = 1.0/(2.0-v) //  where the pointmass would be for a global (unstratified) CCA TODO not used!

    // TODO these calculations assume primitive assorter upper bound = 1
    val ua = 1.0 // primitive asorter upper bound
    val noerror = 1.0/(2.0-v/ua)
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
//
//    #construct assorter population within each stratum
    val x = mutableListOf<List<Double>>() // shape(K, N)
    repeat(K) { k ->
//        num_points = [int(n_err) for n_err in saferound([N[k]*p_2[k], N[k]*p_1[k], N[k]*(1-p_2[k]-p_1[k])], places = 0)]

//        list [N[k]*p_2[k], N[k]*p_1[k], N[k]*(1-p_2[k]-p_1[k])]
//        call saferound(list, places = 0), rounds each element of the list, makes sure that theres no rounding errors
//        python needs int(n_err) because saferound return rounded floats
//          val list = listOf(Nk[k]*p_2[k], Nk[k]*p_1[k], Nk[k]*(1-p_2[k]-p_1[k]))
//          val num_points = saferound(list, places = 0)

          val p2n = roundToClosest(Nk[k] * p_2[k])
          val p1n = roundToClosest(Nk[k] * p_1[k])
          val noerrorn = Nk[k] - p2n - p1n

//        x.append(1/2 * np.concatenate([np.zeros(num_points[0]), np.ones(num_points[1]) * 1/2, np.ones(num_points[2])]))
// TODO this gives assort values p2o = 0, p1o = 1/4, noerror = 1/2
//     should be   assort values p2o = 0, p1o = noerror/2, noerror = noerror, where noerror is for the kth strata
//     perhaps these values are multiplied by 2 * noerror (ie upper) somewhere
          val p2assorts = List<Double>(p2n) { 0.0 }// assort value 0
          val p1assorts = List<Double>(p1n) { 0.25 } // assort value noerror/2
          val noerrors = List<Double>(noerrorn) { 0.5 }

          x.add(p2assorts + p1assorts + noerrors)
      }

//    stopping_times = np.zeros(reps) #container for global stopping times
//    sample_sizes = np.zeros(reps) #container for global sample sizes
    val stopping_times = mutableListOf<Int>()
    val sample_sizes = mutableListOf<Double>()
    val lnAlpha = -ln(alpha)

    repeat(reps) { r ->
        // #shuffle (WOR) or sample (WR) a length-N_k sequence from each stratum k
        // X = [np.random.choice(x[k],  len(x[k]), replace = (not WOR)) for k in np.arange(K)]
        // this probably doesnt work as is for WOR, where size = len(x[k]) ??
        // use newer numpy Generator function
        // just use shuffle
        val Xshuffled: List<List<Double>> = List(K) { k -> x[k].toMutableList().shuffled() }

        if (inference == "ui-ts") {
            // fun banded_uits(x : List<List<Double>>, N: List<Int>, etas: List<Pair<Double, Double>>, lam_func: String, allocation_func: String = "proportional_round_robin",
            //                log: Boolean = true, WOR: Boolean = false, verbose: Boolean = false) {
            val (optimalMart, optimalEta, global_ss) = banded_uits(Xshuffled, Nk, etaBands, bet, selection, log = true)

            val sampleSize = findFirst(optimalMart) { it > lnAlpha} ?: N
            // any(uits > -np.log(alpha)), np.argmax(uits > -np.log(alpha)), np.sum(N))
            // np.where(any(uits > -np.log(alpha)), np.argmax(uits > -np.log(alpha)), np.sum(N))

            // val wtf = np.where(any(optimalMart > -lnAlpha), np.argmax(uits > -np.log(alpha)), np.sum(Nk))
            stopping_times.add( sampleSize)

        } // else if (inference == "lcb") {
//            eta_0 = (1/2 + 1 - A_c_global)/2
//            lcb = global_lower_bound(X, N, lam_func, allocation_func, alpha, WOR = WOR, breaks = 1000)
//            stopping_times[r] = np.where(any(lcb > eta_0), np.argmax(lcb > eta_0), np.sum(N))
//            sample_sizes[r] = stopping_times[r]

//        if method == "ui-ts":
//            uits, eta_min, global_ss = banded_uits(X, N, betas, lam_func, allocation_func, log = True, WOR = WOR)
//            stopping_times[r] = np.where(any(uits > -np.log(alpha)), np.argmax(uits > -np.log(alpha)), np.sum(N))
//            sample_sizes[r] = global_ss[int(stopping_times[r])]
//        elif method == "lcb":
//            eta_0 = (1/2 + 1 - A_c_global)/2
//            lcb = global_lower_bound(X, N, lam_func, allocation_func, alpha, WOR = WOR, breaks = 1000)
//            stopping_times[r] = np.where(any(lcb > eta_0), np.argmax(lcb > eta_0), np.sum(N))
//            sample_sizes[r] = stopping_times[r]
    }
    return stopping_times.average()
}

fun findFirst( x: DoubleArray, pred: (Double) -> Boolean): Int? {
    for (i in 0 until x.size) {
        if (pred(x[i])) return i
    }
    return null
}

// def construct_eta_bands_plurcomp(A_c, N, n_bands = 100):
//    '''
//    construct a set of bands to run a card-level comparison audit of a plurality contest
//    using the parameterization from Sweeter than SUITE (STS): https://arxiv.org/pdf/2207.03379
//    TODO maybe section 4.1 "Tuning parameters were chosen as follows..."
//
//    Parameters
//    ----------
//        A_c: length-2 list of floats in [0,1]
//            the reported assorter mean in each stratum
//        N: length-2 list of ints
//            the size of the population within each stratum
//        n_bands: positive int
//            the number of equal-width bands in the tesselation of the null boundary
//    Returns
//    ----------
//        a list of tuples of length points,
//        each tuple represents a band over which the null mean will be tested,
//        it contains one eta that is used to construct bets and selections
//        and two etas representing the endpoints of the band, which are used to conservatively test the intersection nulls for that band
//    '''
//    assert (np.max(A_c) <= 1) and (np.min(A_c) >= 0), "reported assorter margin is not in [0,1]"
//    assert np.min(N) >= 1, "N (population size) must be no less than 1 in all strata"
//    K = len(N)
//    u = 2 # the upper bound on the overstatment assorters, per STS
//    eta_0 = 1/2 # the global null in terms of the original assorters
//    assert K == 2, "only works for two strata"
//    w = N / np.sum(N)
//    assert np.dot(w, A_c) > 1/2, "reported assorter mean (A_c) implies the winner lost"
//    eta_1_grid = np.linspace(max(0, eta_0 - w[1]), min(u, eta_0/w[0]), n_bands + 1)
//    eta_2_grid = (eta_0 - w[0] * eta_1_grid) / w[1]
//    # transformed overstatement assorters
//    # the transformation is per STS, except divided by 2
//    # the division by 2 allows a plurality CCA population to be defined on [0,1] instead of [0,2]
//    beta_1_grid = (eta_1_grid + 1 - A_c[0])/2 # transformed null means in stratum 1
//    beta_2_grid = (eta_2_grid + 1 - A_c[1])/2 # transformed null means in stratum 2
//    beta_grid = np.transpose(np.vstack((beta_1_grid, beta_2_grid)))
//    betas = []
//    for i in np.arange(beta_grid.shape[0]-1):
//        centroid = (beta_grid[i,:] + beta_grid[i+1,:]) / 2
//        betas.append([(beta_grid[i,:], beta_grid[i+1,:]), centroid])
//    return betas

// TODO what is length ?
// A_c: the reported assorter mean in each stratum, size = K
// N: the size of the population within each stratum, size = K
// n_bands: the number of equal-width bands in the tesselation of the null boundary, size =  ?
fun construct_eta_bands_plurcomp(A_c: List<Double>, Nk: List<Int>, n_bands: Int = 100): List<Band> {
    require (A_c.max() <= 1.0 && A_c.min() >= 0.0) { "reported assorter margin is not in [0,1]" }
    require (Nk.min() >= 1) {"N (population size) must be no less than 1 in all strata" }

    val uO = 2.0 // the upper bound on the overstatment assorters, per SWEETER
    // TODO should be  uO = 2.0 / (2.0 - assorterMargin / assorter.upperBound()); however if uA = 1, margin in [0,1], uO = [1,2]

    val eta_0 = 0.5 //  the global null in terms of the original assorters

    val K = Nk.size
    require (K == 2) { "only works for two strata" }
    // TODO generalize to K > 2

    val N = Nk.sumOf { it }
    val wk = Nk.map { it / N.toDouble() }
    val u = 2.0
    require (numpy_dotDD(wk, A_c) > 1/2) { "reported assorter mean (A_c) implies the winner lost" }

    // TODO wtf ? eta_grid<K> aka vertices; see Algorithm1 in STRATIFIED "Compute I-TSMs at vertices" line 8
    // THis probably just computes the verticies at each band endpoint
    // eta_1_grid = np.linspace(max(0, eta_0 - w[1]), min(u, eta_0/w[0]), n_bands + 1)
    val eta_1_grid = numpy_linspace(start = max(0.0, eta_0 - wk[1]), end = min(u, eta_0/wk[0]), npts = n_bands + 1) // 1D List
    // val eta_2_grid = (eta_0 - w[0] * eta_1_grid) / w[1]
    val eta_2_grid = eta_1_grid.map { (eta_0 - wk[0] * it) / wk[1]} // 1D List

    // TODO WTF ?
    // transformed overstatement assorters
    // the transformation is per STS, except divided by 2
    // the division by 2 allows a plurality CCA population to be defined on [0,1] instead of [0,2]
    // val beta_1_grid = (eta_1_grid + 1 - A_c[0])/2 //  transformed null means in stratum 1
    val beta_1_grid = eta_1_grid.map { (it + 1 - A_c[0])/2} //  transformed null means in stratum 1
    // val beta_2_grid = (eta_2_grid + 1 - A_c[1])/2  //  transformed null means in stratum 2
    val beta_2_grid = eta_2_grid.map { (it + 1 - A_c[1])/2 } //  transformed null means in stratum 2

    // beta_1_grid and beta_2_grid are 1D lists
    // vstack turns them into 2D list of list
    // transpose transposes them
    //     beta_grid = np.transpose(np.vstack((beta_1_grid, beta_2_grid)))
    val beta_grid: List<Pair<Double, Double>> = beta_1_grid.mapIndexed{ idx, beta1 -> Pair(beta1, beta_2_grid[idx]) }
    // so beta_grid = Pair(beta1[0], beta2[0]) .. Pair(beta1[n], beta2[n])

    val bands = mutableListOf<Band>()
    // for (i in numpy_arange(beta_grid.shape[0]-1)) {
    repeat(n_bands) { i ->  // 2D = (row, col), so loop over the rows of beta_grid
        // arr[0:2, :] — Selects the first two rows and all columns. [7, 10]
        // so beta_grid[i,:] must be the ith row;
        // so beta_grid[i+1,:] must be the (i+1)th row;
        // this is the list of centroid/distance between the values of row i and i+1
        // val centroid = (beta_grid[i,:]+beta_grid[i+1, :]) / 2
        val startpoint = beta_grid[i]
        val endpoint = beta_grid[i+1]
        val centroid = doubleArrayOf( (startpoint.first + endpoint.first)/2, (startpoint.second + endpoint.second)/2 )

        // betas.add([(beta_grid[i,:], beta_grid[i+1, :]), centroid])
        // [(beta_grid[i,:], beta_grid[i+1, :]), centroid] is the concat of rowi, row i + 1, and their centroid ?
        //    Returns
        //    ----------
        //        a list of tuples of length points,
        //        each tuple represents a band over which the null mean will be tested,
        //        it contains one eta that is used to construct bets and selections
        //        and two etas representing the endpoints of the band, which are used to conservatively test the intersection nulls for that band
        // ie just cram shit into an array with no semantics
        //  so "one eta that is used to construct bets and selections"
        //
        bands.add(Band(KPoint(startpoint), KPoint(endpoint), KPoint(centroid)))
    }
    bands.forEachIndexed { idx, it -> println("$idx $it") }
    return bands
}

// startpoint: starting point in K-space
// endpoint: ending point in K-space
// centroid: centroid<K> in kth dimension = strata
class Band(val startpoint: KPoint, val endpoint: KPoint, val  centroid: KPoint) {
    override fun toString(): String {
        return "Band( startpoint=$startpoint, endpoint=$endpoint, centroid=$centroid)"
    }
}
class KPoint(val point: DoubleArray) {
    constructor(pt: Pair<Double, Double>) : this(doubleArrayOf(pt.first, pt.second))
    constructor(pt: List<Double>) : this(DoubleArray(pt.size) { pt[it] })
    override fun toString() = buildString {
        append("(")
        point.forEach { append("${dfn(it, 5)}, ") }
        append(")")
    }
}

// compute a product UI-TS by minimizing product I-TSMs along a grid of etas (the "band" method)
// x: the data sampled from each stratum; length-K list of length-n_k np.arrays with elements in [0,1] (K, Nk)
// Nk: the size of each stratum; length-K list of ints
// etas: intersection nulls over which the minimum will be taken; list of Pairs of etas to test and etas to construct bets / allocations

fun banded_uits(x: List<List<Double>>, Nk: List<Int>, etaBands: List<Band>, bet: String, selection: String,
                log: Boolean = true, WOR: Boolean = false, verbose: Boolean = false): Triple<DoubleArray, List<KPoint>, Int> {

    val K = Nk.size
    val N = Nk.sumOf { it }
    val wk = Nk.map { it / N.toDouble() }
    val Tk = IntArray(K) { x[it].size }

    //    #record objective value and selections for each band
    //    obj = np.zeros((len(etas), np.sum(n) + 1))
    //    sel = np.zeros((len(etas), np.sum(n) + 1, K))
    //    min_etas = []
    /*    bets = []
    val obj = numpy_zeros(bands.size, N+1)
    val sel = numpy_zeros(bands.size, N+1, K)
    val min_etas = mutableListOf<Double>()
    val bets = mutableListOf<Double>()

    // skip greedy kelly
    // draw the selection sequence one time if nonadaptive
    val Y = round_robin(Nk) */

    //         #draw the selection sequence one time if nonadaptive
    //        if allocation_func in nonadaptive_allocations:
    //            first_centroid = etas[0][1]
    //            bets_i = [mart(x[k], first_centroid[k], lam_func[k], None, ws_N[k], log, output = "bets") for k in np.arange(K)]
    //            T_k_i = selector(x, N, allocation_func, first_centroid, bets_i)

    val bets = mutableListOf<List<Double>>()

    val first_centroid: KPoint = etaBands.first().centroid
    repeat(K) { k ->
        bets.add( inverse_eta(x[k], first_centroid.point[k], Kwargs.empty))
    }

    // this seems wrong, Nk are the strata sizes. where are the samples used ??
    val Tki: List<IntArray> = selector(x, Nk, first_centroid, bets)

    val marts = mutableListOf<List<Double>>()  // (nbands, ntimes)
    val sel = mutableListOf<List<IntArray>>()

    // loop over bands
    etaBands.forEach { band ->
        // val centroid_eta = etaBands[i].centroid

        // find largest eta in the band for each stratum
        // max_eta = np.max(np.vstack(etas[i][0]),0) #record largest eta in the band for each stratum
        val max_eta = mutableListOf<Double>()
        repeat (K) { k ->
            max_eta.add( max(band.startpoint.point[k], band.endpoint.point[k]))
        }

        // val max_eta = np.max(np.vstack( etaBands[i][0]), 0) // record largest eta in the band for each stratum

        // bets are determined for max_eta, which makes the bets conservative for both strata and both vertices of the band
        // val bets_i = [mart(x[k], max_eta[k], bet[k], None, ws_N[k], log, output = "bets") for k in np.arange(K)]
        bets.clear()
        repeat(K) { k ->
            bets.add( inverse_eta(x[k], max_eta[k], Kwargs.empty))
        }

        //            itsm_mat = np.zeros((np.sum(n)+1, 2))
        //            #minima are evaluated at the endpoints of the band//
        //            #one of which is the minimum over the whole band due to concavity
        //            for j in np.arange(2):
        //                itsm_mat[:,j] = intersection_mart(x = x, N = N, eta = etas[i][0][j], lam = bets_i, T_k = T_k_i,
        //                    combine = "product", log = log, WOR = WOR)
        //
        // fun intersection_mart(x: List<List<Double>>, Nk: List<Int>, eta: Double,
        //                      lam_func = None, lam = None, mixing_dist = None, allocation_func = None, T_k = None,
        //                      combine = "product", theta_func = None, log = True, WOR = False, return_selections = False, last = False, running_max = True) {

        // val itsm_mat = np.zeros((np.sum(n) + 1, 2))
        // minima are evaluated at the endpoints of the band//
        // one of which is the minimum over the whole band due to concavity
        val itsm_mat = mutableListOf<List<Double>>() // (K, N+1)

        repeat(2) { j ->
            val kpoint = if (j==0) band.startpoint else band.endpoint
            val etas = kpoint.point.toList()
            //   itsm_mat[:,j] = intersection_mart(x...
            itsm_mat.add( intersection_mart(x = x, Nk = Nk, eta = etas, lam = bets, T_k = Tki, log = log) )
        }

        //         obj[i,:] = np.min(itsm_mat, 1)
        val mat1 = itsm_mat[1]
        val itsiMin: List<Double> = itsm_mat[0].mapIndexed { idx, mat0 -> min(mat0, mat1[idx]) }
        marts.add(itsiMin)
        sel.add(Tki)
    }

    //     val obj = mutableListOf<List<Double>>() (ntrials, N+1)
    //    val sel = mutableListOf<List<IntArray>>() (N+1)
    //  Returns the indices of the minimum values along an axis
    //  opt_index = np.argmin(obj, 0)

    // for each time, find min across bands and return its index in IntArray(time)
    val optIndex: IntArray = findMinBand( marts )  // should be (401), index of smallest mart over all the bands
    val optimalEta = mutableListOf<KPoint>() // (N+1,Kpoint)
    val optimalMart = DoubleArray(N + 1) // (N+1)
    // this appears to just equal the time (1, 2, 3 ...)
    // global_sample_size = np.sum(np.max(sel, 0), 1) TODO what is the global_sample_size ??

    for (i in 0..N) {
        optimalEta.add(etaBands[optIndex[i]].centroid)
        optimalMart[i] = marts[optIndex[i]][i]
    }

    // DoubleArray, List<DoubleArray>, Double
    return Triple(optimalMart, optimalEta, 0)

    //    opt_index = np.argmin(obj, 0)
    //    eta_opt = np.zeros((np.sum(n) + 1, len(x))) (N, K)
    //    mart_opt = np.zeros(np.sum(n) + 1)
    //    global_sample_size = np.sum(np.max(sel, 0), 1)
    //    for i in np.arange(np.sum(n) + 1):
    //        eta_opt[i,:] = etas[opt_index[i]][1] #record the center eta of the band that minimizes (not exact minimizer)
    //        mart_opt[i] = obj[opt_index[i],i]
    //    if verbose:
    //        return mart_opt, eta_opt, global_sample_size, obj, sel, bets
    //    else:
    //        return mart_opt, eta_opt, global_sample_size
}

// input: (bands, time)
// for each time, find min across bands and return its index in IntArray(time)
fun findMinBand(input: List< List<Double> >): IntArray {
    val ntimes = input[0].size
    val minIndex = IntArray(ntimes)
    val minValue = DoubleArray(ntimes)  { Double.MAX_VALUE }
    input.forEachIndexed { bandIdx, inner ->
        inner.forEachIndexed { time, v ->
            if (v < minValue[time] ) {
                minValue[time] = v
                minIndex[time] = bandIdx
            }
        }
    }
    return minIndex
}

// T_k(t,k): (N + 1) x K (t,k);  the selections for stratum k at each time t
// return mart(N+1) for each t
fun intersection_mart(x: List<List<Double>>, Nk: List<Int>, eta: List<Double>, lam: List<List<Double>>, T_k: List<IntArray>,
                      log:Boolean = true, running_max:Boolean = true): List<Double> {
                      // mixing_dist = None, allocation_func = None, T_k = None,
                      // combine = "product", theta_func = None, log = True, WOR = False, return_selections = False, last = False, running_max = True) {
    val K = Nk.size
    val N = Nk.sum()

    // ws_N = N if WOR else np.inf*np.ones(K)
    // ws_log = False if combine == "sum" else log

//    if mixing_dist is None:
//        if lam is None:
//            lam = [mart(x[k], eta[k], lam_func[k], None, ws_N[k], ws_log, output = "bets") for k in np.arange(K)]

//        #within-stratum martingales
//        ws_marts = [mart(x[k], eta[k], None, lam[k], ws_N[k], ws_log) for k in np.arange(K)]
    val ws_marts = mutableListOf<List<Double>>() // (K, N+1)
    repeat(K) { k ->
        val martk = mart(x[k], eta[k], lam[k], Nk[k], log)
        ws_marts.add(martk)
    }

//        #construct the interleaving
//        if T_k is None:
//            T_k = selector(x, N, allocation_func, eta, lam)
//        if last:
//            marts = np.array([[ws_marts[k][T_k[-1, k]] for k in np.arange(K)]])
//            if np.any(np.isposinf(marts)):
//                #it's not exactly clear how to handle certainties when sampling without replacement
//                #i.e. what if the null is certainly false in one stratum but certainly true in another...
//                marts = np.inf * np.ones((1,K))
//        else:

    val marts = mutableListOf<DoubleArray>()  //(N+1, K)
//            marts = np.zeros((T_k.shape[0], K))
    repeat(T_k.size) { t ->
//            for i in np.arange(T_k.shape[0]):
        val marts_t = DoubleArray(K)
//                marts_i = np.array([ws_marts[k][T_k[i, k]] for k in np.arange(K)])
        repeat(K) { k ->
            val choice = T_k[t][k]
            marts_t[k] = ws_marts[k][choice]  // marts for time t over k
        }
//                #make all marts infinite if one is, when product is taken this enforces rule:
//                #we reject intersection null if certainly False in one stratum
//                #TODO: rethink this logic? What if the null is certainly true in a stratum?
//                #there is some more subtlety to be considered when sampling WOR
//                marts[i,:] = marts_i if not any(np.isposinf(marts_i)) else np.inf * np.ones(K)
        marts.add(marts_t)
    }

    // if combine == "product":
    //        if mixing_dist is None:
    //            int_mart = np.sum(marts, 1) if log else np.prod(marts, 1)
    var int_mart = if (log) { // (N+1)
        marts.map { it.sum() }
    }  else {
        marts.map{ it.reduce { acc, element -> acc * element } }
    }
    if (running_max)
        int_mart = runningMaximum(int_mart)
    return int_mart

    //        else:
    //            #this takes the product across strata and the mean across the mixing distribution
    //            int_mart = np.mean(np.prod(marts, 2), 0)
    //            int_mart = np.log(int_mart) if log else int_mart
    //        if running_max:
    //            int_mart = np.maximum.accumulate(int_mart)
    //    elif combine == "sum":
    //        assert theta_func is not None, "Need to specify a theta function from Weights if using sum"
    //        thetas = theta_func(eta)
    //        int_mart = np.log(np.sum(thetas * marts, 1)) if log else np.sum(thetas * marts, 1)
    //        if running_max:
    //            int_mart = np.maximum.accumulate(int_mart)
    //    elif combine == "fisher":
    //        pvals = np.exp(-np.maximum(0, marts)) if log else 1 / np.maximum(1, marts)
    //        fisher_stat = -2 * np.sum(np.log(pvals), 1)
    //        pval = 1 - chi2.cdf(fisher_stat, df = 2*K)
    //        pval = np.log(pval) if log else pval
    //    else:
    //        raise NotImplementedError("combine must be product, sum, or fisher")
    //    result = int_mart if combine != 'fisher' else pval
    //    if return_selections:
    //        return result, T_k
    //    else:
    //        return result
}

// numpy np.maximum.accumulate(int_mart)
fun runningMaximum(input: List<Double>): List<Double> {
    if (input.isEmpty()) return emptyList()

    val result = mutableListOf<Double>()
    var currentMax = input[0]

    for (value in input) {
        if (value > currentMax) {
            currentMax = value
        }
        result.add(currentMax)
    }
    return result
}

// def intersection_mart(x, N, eta, lam_func = None, lam = None, mixing_dist = None, allocation_func = None, T_k = None, combine = "product", theta_func = None, log = True, WOR = False, return_selections = False, last = False, running_max = True):
//    '''
//    an intersection test supermartingale (I-TSM) for a vector intersection null eta
//    TODO assumes sampling is with replacement: no population size is required
//
//    Parameters
//    ----------
//        x: length-K list of length-n_k np.arrays with elements in [0,1]
//            the data sampled from each stratum
//        N: length-K list of ints
//            population size for each stratum
//        eta: length-K np.array or list in [0,1]
//            the vector of null means
//        lam_func: callable, a function from class Bets; a list of functions, one for each stratum; or a list of lists, containing a mixture of bets for each stratum
//            a betting strategy, a function that sets bets possibly given past data and eta
//        lam: length-K list of length-n_k np.arrays corresponding to bets, or list of lists of np.arrays to be mixed over
//            bets for each stratum, real numbers between 0 and 1/eta_k
//        mixing_dist: a B by K np.array in [0,1], or nothing
//            only for mixing over the intersection martingales
//            lambdas to mix over, B is just any positive integer (the size of the mixing distribution)
//        allocation_func: callable, a function from class Allocations
//            the allocation function to be used
//        T_k: (sum(n) + 1) x K np.array of ints
//            the selections for each stratum (columns) at each time (rows), alternative to allocation_funct
//        combine: string, in ["product", "sum", "fisher"]
//            how to combine within-stratum martingales to test the intersection null
//        theta_func: callable, a function from class Weights
//            only relevant if combine == "sum", the weights to use when combining with weighted sum
//        log: boolean
//            return the log I-TSM if true, otherwise return on original scale
//        WOR: boolean
//            should martingales be computed under sampling with or without replacement?
//        return_selections: boolean
//            return matrix of stratum sample sizes (T_k) if True, otherwise just return combined martingale
//        last: Boolean
//            return only the last index of the martingale; helps speed things up when T_k is given
//        running_max: boolean
//            take the running maximum of the I-TSM
//    Returns
//    ----------
//        the value of an intersection martingale that uses all the data (not running max)
//    '''
//    K = len(eta)
//    assert lam_func is None or mixing_dist is None, "Must specify (exactly one of) mixing distribution or predictable lambda function"
//    assert bool(allocation_func is None) != bool(T_k is None), "Must specify (exactly one of) selector (allocation_func) or selections (T_k)"
//    assert (last and T_k is not None) or not last, "last only works when T_k is given"
//    # check that all types and arguments are aligned
//    assert ((combine == "product") and (mixing_dist is not None)) or (lam_func is not None and (callable(lam_func) or (len(lam_func) == K))) or (lam is not None and ((type(lam) is np.ndarray) or (len(lam) == K))), "lam or lam_func needs to be a single object or a list of length K (one for each stratum); mixing_dist only works with product combining"
//    if mixing_dist is not None:
//        assert allocation_func in nonadaptive_allocations, "for now, mixing only works with nonadaptive allocation"
//    ws_log = False if combine == "sum" else log
//    ws_N = N if WOR else np.inf*np.ones(K)
//
//    # expand bets into lists over strata by copying
//    if callable(lam_func):
//        lam_func = [lam_func] * K
//
//    if mixing_dist is None:
//        if lam is None:
//            lam = [mart(x[k], eta[k], lam_func[k], None, ws_N[k], ws_log, output = "bets") for k in np.arange(K)]
//        #within-stratum martingales
//        ws_marts = [mart(x[k], eta[k], None, lam[k], ws_N[k], ws_log) for k in np.arange(K)]
//        #construct the interleaving
//        if T_k is None:
//            T_k = selector(x, N, allocation_func, eta, lam)
//        if last:
//            marts = np.array([[ws_marts[k][T_k[-1, k]] for k in np.arange(K)]])
//            if np.any(np.isposinf(marts)):
//                #it's not exactly clear how to handle certainties when sampling without replacement
//                #i.e. what if the null is certainly false in one stratum but certainly true in another...
//                marts = np.inf * np.ones((1,K))
//        else:
//            marts = np.zeros((T_k.shape[0], K))
//            for i in np.arange(T_k.shape[0]):
//                marts_i = np.array([ws_marts[k][T_k[i, k]] for k in np.arange(K)])
//                #make all marts infinite if one is, when product is taken this enforces rule:
//                #we reject intersection null if certainly False in one stratum
//                #TODO: rethink this logic? What if the null is certainly true in a stratum?
//                #there is some more subtlety to be considered when sampling WOR
//                marts[i,:] = marts_i if not any(np.isposinf(marts_i)) else np.inf * np.ones(K)
//    else:
//        B = mixing_dist.shape[0]
//        if T_k is None:
//            T_k = selector(x, N, allocation_func, eta, lam = None)
//        marts = np.zeros((B, T_k.shape[0], K))
//        for b in range(B):
//            lam = [Bets.fixed(x[k], eta[k], c = mixing_dist[b,k]) for k in np.arange(K)]
//            ws_marts = [mart(x[k], eta[k], None, lam[k], ws_N[k], log = False) for k in np.arange(K)]
//            for i in np.arange(T_k.shape[0]):
//                marts_bi = np.array([ws_marts[k][T_k[i, k]] for k in np.arange(K)])
//                marts[b,i,:] = marts_bi if not any(np.isposinf(marts_bi)) else np.inf * np.ones(K)
//    if combine == "product":
//        if mixing_dist is None:
//            int_mart = np.sum(marts, 1) if log else np.prod(marts, 1)
//        else:
//            #this takes the product across strata and the mean across the mixing distribution
//            int_mart = np.mean(np.prod(marts, 2), 0)
//            int_mart = np.log(int_mart) if log else int_mart
//        if running_max:
//            int_mart = np.maximum.accumulate(int_mart)
//    elif combine == "sum":
//        assert theta_func is not None, "Need to specify a theta function from Weights if using sum"
//        thetas = theta_func(eta)
//        int_mart = np.log(np.sum(thetas * marts, 1)) if log else np.sum(thetas * marts, 1)
//        if running_max:
//            int_mart = np.maximum.accumulate(int_mart)
//    elif combine == "fisher":
//        pvals = np.exp(-np.maximum(0, marts)) if log else 1 / np.maximum(1, marts)
//        fisher_stat = -2 * np.sum(np.log(pvals), 1)
//        pval = 1 - chi2.cdf(fisher_stat, df = 2*K)
//        pval = np.log(pval) if log else pval
//    else:
//        raise NotImplementedError("combine must be product, sum, or fisher")
//    result = int_mart if combine != 'fisher' else pval
//    if return_selections:
//        return result, T_k
//    else:
//        return result

// for one strata
fun mart(x: List<Double>, eta: Double, lams: List<Double>, N: Int, log: Boolean): List<Double> {

    val cumulSum = calcCumulativeSum(N, eta, x.toDoubleArray(), withReplacement = false)
    val eta_t = cumulSum.S.mapIndexed { idx, s -> (N*eta - s)/(N - cumulSum.indices[idx]+1)}

        // TODO we had this in the orinal port, but it got thrown away....
    //val S = np.insert(np.cumsum(x), 0, 0)[0:-1]
    //val j = np.arange(1,len(x)+1)
    //val eta_t = (N*eta-S)/(N-j+1)

    //        mart_array = np.zeros((len(x)+1, len(lam))) //

//        for l in range(len(lam)):
//            mart = np.insert(np.cumprod(1 + lam[l] * (x - eta_t)), 0, 1)
//            mart[np.insert(eta_t < 0, 0, False)] = np.inf
//            mart[np.insert(eta_t > 1, 0, False)] = 0
//            mart_array[:,l] = mart

    val payoffs = x.mapIndexed { idx, xt -> 1 + lams[idx] * (xt - eta_t[idx]) }
    val cumprod = payoffs.runningReduce { acc, d -> acc * d }
    //      mart = np.insert(np.cumprod(1 + lam[l] * (x - eta_t)), 0, 1)
    val mart = mutableListOf(1.0).apply { addAll(cumprod) }

    //      mart[np.insert(eta_t < 0, 0, False)] = np.inf
    //      mart[np.insert(eta_t > 1, 0, False)] = 0
    for (i in eta_t.indices) {
        if (eta_t[i] < 0) mart[i + 1] = Double.POSITIVE_INFINITY
        if (eta_t[i] > 1) mart[i + 1] = 0.0
    }

//  theres only one list of lams, so we dont need this
//  h_mart = np.mean(mart_array, 1) # this is the "hedged martingale", the flat average over the bets

//        h_mart = np.log(h_mart) if log else h_mart
    return if (log) mart.map{ ln(it) } else mart
}


// def mart(x, eta, lam_func = None, lam = None, N = np.inf, log = True, output = "mart"):
//    '''
//    betting martingale
//
//    Parameters
//    ----------
//        x: length-n_k np.array with elements in [0,1]
//            data
//        eta: scalar in [0,1]
//            null mean
//        lam_func: callable, a function from the Bets class, or a list of callables
//            if callable, the TSM is computed for that betting function
//            if list, a TSM is computed for every betting function in the list and those TSMs are averaged into a new TSM
//        lam: length-n_k np.array or list of length-n_k np.arrays to be mixed over
//            pre-set bets
//        N: positive integer,
//            the size of the population from which x is drawn (x could be the entire population)
//        log: Boolean
//            indicates whether the martingale or terms should be returned on the log scale or not
//        output: str
//            indicates what to return
//            "mart": return the martingale sequence (found by multiplication or summing if log)
//            "bets": returns just the bets for the martingale
//    Returns
//    ----------
//        mart: scalar; the value of the (log) betting martingale at time n_k
//
//    '''
//    assert bool(lam is None) != bool(lam_func is None), "must specify exactly one of lam_func or lam"
//    if N < np.inf:
//        S = np.insert(np.cumsum(x), 0, 0)[0:-1]
//        j = np.arange(1,len(x)+1)
//        eta_t = (N*eta-S)/(N-j+1)
//    elif N == np.inf:
//        eta_t = eta * np.ones(len(x))
//    else:
//        raise ValueError("Input an integer value for N, possibly np.inf")
//    if callable(lam_func):
//        lam_func = [lam_func]
//    #note: per Waudby-Smith and Ramdas, the null mean for the bets does not update when sampling WOR
//    #note: eta < 0 or eta > 1 can create runtime warnings in log, but are replaced appropriately by inf
//    if lam_func is not None:
//        lam = [lf(x, eta) for lf in lam_func]
//    elif type(lam) is np.ndarray:
//        lam = [lam]
//
//    if output == "mart":
//        mart_array = np.zeros((len(x)+1, len(lam)))
//        for l in range(len(lam)):
//            mart = np.insert(np.cumprod(1 + lam[l] * (x - eta_t)), 0, 1)
//            mart[np.insert(eta_t < 0, 0, False)] = np.inf
//            mart[np.insert(eta_t > 1, 0, False)] = 0
//            mart_array[:,l] = mart
//        h_mart = np.mean(mart_array, 1) # this is the "hedged martingale", the flat average over the bets
//        h_mart = np.log(h_mart) if log else h_mart
//        out = h_mart
//    elif output == "bets":
//        out = lam
//    else:
//        out = "Input a valid argument to return, either 'marts', 'terms', or 'bets'"
//    return out



//    takes data and predictable tuning parameters and returns a sequence of stratum sample sizes
//    equivalent to [S_t for k in 1:K]
// // return a length (N+1) sequence of interleaved stratum selections
fun selector(x : List<List<Double>>, Nk: List<Int>, first_centroid: KPoint, bets: List<List<Double>> ): List<IntArray> {
    // return round_robin(Nk)

    val K = Nk.size
    val N = Nk.sumOf { it }
    //         n = [len(x_k) for x_k in x]

    // selections from 0 in each stratum; time 1 is first sample
    ////    T_k = np.zeros((np.sum(n) + 1, K), dtype = int)
    val Tk = mutableListOf<IntArray>()
    val running_Tk = IntArray(K)

    fun needsMore(running_Tk: IntArray, wantsN: List<Int>): Boolean {
        val all = wantsN.mapIndexed { k, n -> ( running_Tk[k] < n) }
        return all.fold(false) { acc, next -> acc || next }
    }

    Tk.add(IntArray(K))
    var t = 0
    while ( needsMore(running_Tk, Nk)) {
        t += 1
        // fun round_robin(running_T_k: List<Int>, Nk: List<Int>): List<Int> {
        val next_k = round_robin(
            running_Tk,
            Nk = Nk
        )
        running_Tk[next_k] += 1
        Tk.add(running_Tk.copyOf())
    }

    // Tk.forEach { println(it.contentToString()) }
    return Tk
}

// def selector(x, N, allocation_func, eta = None, lam = None, for_samples = False):
//    # if lam is None:
//    #    assert allocation_func in lcb_allocations, "bets not supplied (lam is None) but required"
//    w = N/np.sum(N)
//    K = len(N)
//    if for_samples:
//        n = [len(x_k)-1 for x_k in x]
//    else:
//        n = [len(x_k) for x_k in x]
//
//    terms = None
//    #selections from 0 in each stratum; time 1 is first sample
//    T_k = np.zeros((np.sum(n) + 1, K), dtype = int)
//    running_T_k = np.zeros(K, dtype = int)
//    t = 0
//    while np.any(running_T_k < n):
//        t += 1
//        next_k = round_robin(
//            x = x,
//            running_T_k = running_T_k,
//            n = n,
//            N = N,
//            eta = eta,
//            lam = lam,
//            terms = terms)
//        running_T_k[next_k] += 1
//        T_k[t,:] = running_T_k
//    return T_k