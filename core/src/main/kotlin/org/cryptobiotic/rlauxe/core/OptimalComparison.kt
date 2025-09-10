package org.cryptobiotic.rlauxe.core


import org.apache.commons.math3.analysis.UnivariateFunction
import org.apache.commons.math3.analysis.solvers.BisectionSolver
import org.apache.commons.math3.optim.MaxEval
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType
import org.apache.commons.math3.optim.univariate.BrentOptimizer
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction
import org.apache.commons.math3.optim.univariate.SearchInterval
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

// betting functions that use Kelly optimization of lambda parameter for the BettingFn

// https://github.com/spertus/comparison-RLA-betting/blob/main/comparison_audit_simulations.R
//   if(strategy == "adaptive"){
//    if(is.null(pars)){stop("Need to specify pars$prior_p_k, pars$d_k, pars$eps_k for k in 1,2")}
//    lag_p_hat_1 <- c(0, lag(cummean(x == a/2))[-1])
//    lag_p_hat_2 <- c(0, lag(cummean(x == 0))[-1])
//    tilde_p_1 <- pmax((pars$d_1 * pars$prior_p_1 + (1:N) * lag_p_hat_1) / (pars$d_1 + 1:N - 1), pars$eps_1)
//    tilde_p_2 <- pmax((pars$d_2 * pars$prior_p_2 + (1:N) * lag_p_hat_2) / (pars$d_2 + 1:N - 1), pars$eps_2)
//    tilde_lambda <- rep(NA, N)
//    for(i in 1:N){
//      tilde_lambda[i] <- optimal_lambda(a = a, p_1 = tilde_p_1[i], p_2 = tilde_p_2[i])
//    }
//    mart <- cumprod(1 + tilde_lambda * (x - m))
//
//   pars_adaptive <- list(
//     "prior_p_1" = sim_frame$prior_p_1[i],
//    "prior_p_2" = sim_frame$prior_p_2[i],
//    "d_1" = sim_frame$d_1[i],
//    "d_2" = sim_frame$d_2[i],
//    "eps_1" = sim_frame$eps_1[i],
//    "eps_2" = sim_frame$eps_2[i]
//    )
//   stopping_times_adaptive[i,] <- replicate(n_sims, simulate_audit(pop, a =  sim_frame$a[i], strategy = "adaptive", pars = pars_adaptive))
//
// log_expected_value <- function(lambda, a, p_1, p_2){
//  log(1 + lambda * (a - 1/2)) * (1 - p_1 - p_2) + log(1 + lambda * (a/2 - 1/2)) * p_1 + log(1 - lambda / 2) * p_2
// }
//
// optimal_lambda <- function(a, p_1, p_2){
//  temp_log_expected_value <- function(lambda){
//    log_expected_value(lambda, a = a, p_1 = p_1, p_2 = p_2)
//  }
//  derivative <- function(lambda){
//    (a - 1/2) * (1 - p_1 - p_2) / (1 + lambda * (a - 1/2)) + (a - 1) * p_1 / (2 - lambda * (1 - a)) + p_2 / (2 - lambda)
//  }
//  solution <- optimize(temp_log_expected_value, interval = c(0,2), maximum = TRUE)
//  solution$maximum
// }

// Cobra section 4.2 Adaptive betting
// In a BSM, the bets need not be fixed and λi can be a predictable function of the
// data X1, . . . , Xi−1 . This allows us to estimate the rates based on past samples as
// well as a priori considerations. We adapt the “shrink-trunc” estimator of Stark [11] to rate estimation.
//
// The rates are allowed to learn from past data in the current audit through
// p̂_k(i−1) , while being anchored to the a priori estimate p̃_k . The tuning parameter
// d_k reflects the degree of confidence in the a priori rate, with large d_k anchoring
// more strongly to p̃_k . Finally, eps_k should generally be set above 0. In particular,
// eps_k > 0 will prevent stalls.
//
// At each time i, the shrink-trunc estimated rate p̃_ki can be plugged into (2)
// and set equal to 0 to obtain the bet λi .
//
class AdaptiveBetting(
    val Nc: Int, // max number of cards for this contest
    val withoutReplacement: Boolean = true,
    val a: Double, // compareAssorter.noerror
    val d: Int,  // weight
    errorRates: ClcaErrorRates, // ? = null,  // a priori estimate of the error rates
    val eps: Double = .00001    // TODO I think we picked this number out of a hat.
): BettingFn {
    val p2o: Double = if (errorRates == null) -1.0 else errorRates.p2o // apriori rate of 2-vote overstatements; set < 0 to remove consideration
    val p1o: Double = if (errorRates == null) -1.0 else errorRates.p1o // apriori rate of 1-vote overstatements; set < 0 to remove consideration
    val p1u: Double = if (errorRates == null) -1.0 else errorRates.p1u // apriori rate of 1-vote understatements; set < 0 to remove consideration
    val p2u: Double = if (errorRates == null) -1.0 else errorRates.p2u // apriori rate of 2-vote understatements; set < 0 to remove consideration

    override fun bet(prevSamples: PrevSamplesWithRates): Double {
        val lastj = prevSamples.numberOfSamples()
        val p2oest = if (p2o < 0.0 || lastj == 0) 0.0 else estimateRate(d, p2o, prevSamples.countP2o().toDouble() / lastj, lastj, eps)
        val p1oest = if (p1o < 0.0 || lastj == 0) 0.0 else estimateRate(d, p1o, prevSamples.countP1o().toDouble() / lastj, lastj, eps)
        val p1uest = if (p1u < 0.0 || lastj == 0) 0.0 else estimateRate(d, p1u, prevSamples.countP1u().toDouble() / lastj, lastj, eps)
        val p2uest = if (p2u < 0.0 || lastj == 0) 0.0 else estimateRate(d, p2u, prevSamples.countP2u().toDouble() / lastj, lastj, eps)

        val mui = populationMeanIfH0(Nc, withoutReplacement, prevSamples)
        val kelly = OptimalLambda(a, ClcaErrorRates(p2oest, p1oest, p1uest, p2uest), mui)
        return kelly.solve()
    }

    // For k ∈ {1, 2} we set a value d_k ≥ 0, capturing the degree of shrinkage to the a priori estimate p̃_k ,
    // and a truncation factor eps_k ≥ 0, enforcing a lower bound on the estimated rate.
    // Let p̂_ki be the sample rates at time i, e.g., p̂_2i = Sum(1{Xj = 0})/i , j=1..i
    // Then the shrink-trunc estimate is:
    //   p_̃ki := (d_k * p̃_k + i * p̂_k(i−1)) / (d_k + i − 1) ∨ epsk  ; COBRA eq (4)
    fun estimateRate(d: Int, apriori: Double, sampleRate: Double, sampleNum: Int, eps: Double): Double {
        //   (d_k * p̃_k + i * p̂_k(i−1)) / (d_k + i − 1) ∨ epsk  ; COBRA eq (4)
        val est = (d * apriori + sampleNum * sampleRate) / (d + sampleNum - 1)
        val lower =  max(est, eps) // lower bound on the estimated rate
        return min(1.0, lower) // upper bound on the estimated rate
    }
}

fun sampleSize(risk: Double, payoff:Double) = -ln(risk) / ln(payoff)

// We know the true rate of all errors
class OracleComparison(
    val a: Double, // noerror
    val errorRates: ClcaErrorRates,
): BettingFn {
    val lam: Double
    init {
        val kelly = OptimalLambda(a, errorRates)
        lam = kelly.solve()
    }
    // note lam is a constant
    override fun bet(prevSamples: PrevSamplesWithRates): Double {
        return lam
    }
}

/**
 * This follows the code in https://github.com/spertus/comparison-RLA-betting/blob/main/comparison_audit_simulations.R
 * Not completely sure of the relationship to COBRA section 3.2.
 * Has been generalized to allow p3 and p4 errors and sampling without replacement (WoR) by setting mui.
 * Note if (lam < 1.0) "**** betting against"
 * a := compareAssorter.noerror
 * a := 1 / (2 − v/au)
 *    v := 2Āc − 1 is the diluted margin: the difference in votes for the reported winner and reported loser, divided by the total number of ballots cast.
 *   au := assort upper value, = 1 for plurality, 1/(2*minFraction) for supermajority
 * mui := mean value under H0, = 1/2 for with replacement
 * p0 := #{xi = a}/N is the rate of correct CVRs.
 * p2o := #{xi = 0}/N is the rate of 2-vote overstatements.
 * p1o := #{xi = a/2}/N is the rate of 1-vote overstatements.
 * p1u := #{xi = 3a/2}/N is the rate of 1-vote understatements.
 * p2u := #{xi = 2a}/N is the rate of 2-vote understatements.
 */
class OptimalLambda(val a: Double, val errorRates: ClcaErrorRates, val mui: Double = 0.5) {
    val p2o = errorRates.p2o
    val p1o = errorRates.p1o
    val p1u = errorRates.p1u
    val p2u = errorRates.p2u
    val p0 = 1.0 - p2o - p1o - p1u - p2u
    val debug = false

    fun solve(): Double {
        val function = UnivariateFunction { lam -> expectedValueLogt(lam) }  // The function to be optimized

        // BrentOptimizer: For a function defined on some interval (lo, hi),
        // this class finds an approximation x to the point at which the function attains its minimum.
        // It implements Richard Brent's algorithm (from his book "Algorithms for Minimization without Derivatives", p. 79)
        // for finding minima of real univariate functions.
        // This code is an adaptation, partly based on the Python code from SciPy (module "optimize.py" v0.5);
        // the original algorithm is also modified to use an initial guess provided by the user,
        // to ensure that the best point encountered is the one returned.
        // Also see https://en.wikipedia.org/wiki/Brent%27s_method
        val optimizer = BrentOptimizer(1e-6, 1e-6)

        // Optimize the function within the given range [start, end]
        val start = 0.0
        val end = 2.0
        val result: UnivariatePointValuePair = optimizer.optimize(
            UnivariateObjectiveFunction(function),
            SearchInterval(start, end),
            GoalType.MAXIMIZE,
            MaxEval(1000)
        )
        if (debug) println( "Kelly: p2o=${p2o}  p1o=${p1o}  p1u=${p1u}  p2u=${p2u} point=${result.point}")
        return result.point
    }

    // EF [Ti ] = p0 [1 + λ(a − mu_i)] + p1 [1 + λ(a/2 − mu_i)] + p2 [1 − λ*mu_i)]  + p3 [1 + λ(3*a/2 − mu_i)]  + p4 [[1 + λ(2*a − mu_i)]
    // EF [Ti ] = p0 * EF[1 + λ(a − mu_i)] + p1 * EF[1 + λ(a/2 − mu_i)] + p2 * EF[1 − λ*mu_i)] + p3 * EF[1 + λ(3*a/2 − mu_i)] + p4 * EF[1 + λ(2*a − mu_i)]

    // log_expected_value <- function(lambda, a, p_1, p_2){
    //  log(1 + lambda * (a - 1/2)) * (1 - p_1 - p_2) + log(1 + lambda * (a/2 - 1/2)) * p_1 + log(1 - lambda / 2) * p_2
    //}

    // not really sure of this; its not really the ln of Ti, some kind of approx?
    // EF [ln(Ti) ] = p0 * ln[1 + λ(a − mu_i)] + p1 * ln[1 + λ(a/2 − mu_i)] + p2 * ln[1 − λ*mu_i)]  + p3 * ln[1 + λ(3*a/2 − mu_i)] + p4 * ln[1 + λ(2*a − mu_i)]
    fun expectedValueLogt(lam: Double): Double {

        return ln(1.0 + lam * (a - mui)) * p0 +
                ln(1.0 - lam * mui) * p2o +
                ln(1.0 + lam * (a*0.5 - mui)) * p1o +
                ln(1.0 + lam * (a*1.5 - mui)) * p1u +
                ln(1.0 + lam * (a*2.0 - mui)) * p2u
    }

    /* why not just use
    fun lnExpectedT(lam: Double): Double {
        return ln(expectedT(lam))
    }

    fun expectedT(lam: Double): Double {
        return (1.0 + lam * (a - mui)) * p0 +       // term0
                (1.0 + lam * (a*0.5 - mui)) * p1 +  // term1
                (1.0 - lam * mui) * p2 +            // term2
                (1.0 + lam * (a*1.5 - mui)) * p3 +  // term3
                (1.0 + lam * (a*2.0 - mui)) * p4    // term4
    }


    // chain rule:   d/dx (ln(f(x)) = f'(x) / f(x) for each of the first 3 terms of expectedT separately
    //  derivative <- function(lambda){
    //    (a - 1/2) * (1 - p_1 - p_2) / (1 + lambda * (a - 1/2)) + (a - 1) * p_1 / (2 - lambda * (1 - a)) + p_2 / (2 - lambda)
    //  }
    // so this is p0 * d/dx (ln(term0)) + p1 * d/dx (ln(term1)) + p2 * d/dx (ln(term2))
    //  why arent we using the derivitive ? Apparently optimize() operates on the function, not its derivitive.
    // "The function optimize searches the interval from lower to upper for a minimum or maximum of the function f with respect to its first argument."
    fun derivativeFromRcode(lam: Double): Double {
        return  p0 * (a - mui) / (1.0 + lam * (a - mui)) +
                p1 * (a*0.5 - mui) / (1.0 + lam * (a*0.5 - mui)) +
                p2 * mui / (lam * mui - 1.0) +
                p3 * (a*1.5 - mui) / (1.0 + lam * (a*1.5 - mui)) +
                p4 * (a*2.0 - mui) / (1.0 + lam * (a*2.0 - mui))
    }

     */
}

// MoreStyle footnote 5
// The number of draws S4 needs to confirm results depends on the diluted margin and
// the number and nature of discrepancies the sample uncovers. The initial sample size can be
// written as a constant (denoted ρ) divided by the “diluted margin.”
// In general, ρ = − log(α)/[ 2γ + λ log(1 − 2γ)], where γ is an error inflation factor and λ is the anticipated rate of
// one-vote overstatements in the initial sample as a percentage of the diluted margin [17]. We define γ and λ as in
// https://www.stat.berkeley.edu/~stark/Vote/auditTools.htm.


/////////////////////////////////////////////////////////////////////////////////////////////////////

//        # called "kelly optimal" from UI-TS SF_oneaudit_example.ipynb
//       #     'test':             NonnegMean.betting_mart,
//       #     'bet':              NonnegMean.kelly_optimal
//       # 'test_kwargs': {'d': 100, 'f': 0}
//    def kelly_optimal(self, x: np.array, pop = None, **kwargs):
//        """
//        return the Kelly-optimal bet
//
//        Parameters
//        ----------
//        x: np.array
//            input data
//        pop: optional np.array
//            the population (order does not matter) that will be used to compute the optimal bet
//        Takes x to be the population unless pop is provided
//        """
//        t = self.t # the null mean
//        pop = getattr(self, "pop", None) # attempts to inherit pop from the class
//        if pop is None:
//            pop = x
//        min_slope = NonnegMean.deriv(0, pop, t)
//        max_slope = NonnegMean.deriv(1/t, pop, t)
//        # if the return is always growing, set lambda to the maximum allowed
//        if (min_slope > 0) & (max_slope > 0):
//            out = 1/t
//        # if the return is always shrinking, set lambda to 0
//        elif (min_slope < 0) & (max_slope < 0):
//            out = 0
//        # otherwise, optimize on the interval [0, 1/eta]
//        else:
//            lam_star = sp.optimize.root_scalar(lambda lam: NonnegMean.deriv(lam, pop, t), bracket = [0, 1/t], method = 'bisect')
//            assert lam_star.converged, "Could not find Kelly optimal bet, the optimization may be poorly conditioned"
//            out = lam_star['root']
//        return out * np.ones_like(x)

// called "kelly optimal" from UI-TS SF_oneaudit_example.ipynb
//     'test':             NonnegMean.betting_mart,
//     'bet':              NonnegMean.kelly_optimal /'test_kwargs': {'d': 100, 'f': 0}

class KellyOptimal(val x: DoubleArray, val t: Double)  {
    /*
        return the Kelly-optimal bet

        Parameters
        ----------
        x: np.array
            input data
        pop: optional np.array
            the population (order does not matter) that will be used to compute the optimal bet
        Takes x to be the population unless pop is provided
        */
    fun bet(prevSamples: PrevSamplesWithRates): Double {
        TODO("Not yet implemented")
    }

    init {
        // t = self.t # the null mean
        // pop = getattr(self, "pop", None) # attempts to inherit pop from the class
        // if pop is None:
        //    pop = x
        val pop = x
        val min_slope = deriv(0.0, pop, t)
        val max_slope = deriv(1.0 / t, pop, t)

        //  if the return is always growing, set lambda to the maximum allowed
        val result = if ((min_slope > 0) && (max_slope > 0))
            1.0 / t
        // if the return is always shrinking, set lambda to 0
        else if ((min_slope < 0) && (max_slope < 0))
            0.0
        // otherwise, optimize on the interval [0, 1/eta]
        else {
            /* val lam_star = sp.optimize.root_scalar(
                lambda lam : NonnegMean . deriv (lam,
                pop,
                t
            ), bracket = [0.0, 1.0/t], method = 'bisect')
            assert lam_star . converged, "Could not find Kelly optimal bet, the optimization may be poorly conditioned"
            out = lam_star['root'] */
            0.0
        }
    }
}

//     def deriv(lam, x, eta):
//        return np.sum((x - eta) / (1 + lam * (x - eta)))
//
// numpy sum
// def sum(a, axis=None, dtype=None, out=None, keepdims=np._NoValue,
//        initial=np._NoValue, where=np._NoValue):
//    """
//    Sum of array elements over a given axis.

// SliceDice section 3.1, p 6:
// For a generic population and sampling design, the Kelly bet can be found numerically.
// In particular, if we postulate a finite-population
//    {x̃i} i=1..N
// and cards are drawn IID (rather than without replacement), then the a priori Kelly bet solves
//    0 = d/dlam (E log[1 + λ(X̃i − η)] = Sum ( xi - η / (1 + lam * (xi - η))   (eq 2)
// and λ∗ can be found by bisection search, for example.
//

// this is eq 2
fun deriv(lam: Double, xarray: DoubleArray, eta: Double): Double {
    return xarray.sumOf { x ->
        (x - eta) / (1 + lam * (x - eta))
    }
}

// lo, hi are x values
fun solve(function: UnivariateFunction, lo: Double, hi: Double): Double {

    // BrentOptimizer: For a function defined on some interval (lo, hi),
    // this class finds an approximation x to the point at which the function attains its minimum.
    // It implements Richard Brent's algorithm (from his book "Algorithms for Minimization without Derivatives", p. 79)
    // for finding minima of real univariate functions.
    // This code is an adaptation, partly based on the Python code from SciPy (module "optimize.py" v0.5);
    // the original algorithm is also modified to use an initial guess provided by the user,
    // to ensure that the best point encountered is the one returned.
    // Also see https://en.wikipedia.org/wiki/Brent%27s_method

    val solver = BisectionSolver()

    //     public double solve(int maxEval, FUNC f, double min, double max) {
    //        return solve(maxEval, f, min, max, min + 0.5 * (max - min));
    //    }
    // Optimize the function within the given range [lo, hi, end]
    val result = solver.solve(1000, function, lo, hi)

    return result
}

// note SHANGRLA uses "bisect" here, not "brent"
// hmmm Brent might not want the derivitive, rather the function itself??
// from scipy:

// def root_scalar(f, args=(), method=None, bracket=None,
//                fprime=None, fprime2=None,
//                x0=None, x1=None,
//                xtol=None, rtol=None, maxiter=None,
//                options=None):
//    """
//    Find a root of a scalar function.
//
//    Parameters
//    ----------
//    f : callable
//        A function to find a root of.
//
//        Suppose the callable has signature ``f0(x, *my_args, **my_kwargs)``, where
//        ``my_args`` and ``my_kwargs`` are required positional and keyword arguments.
//        Rather than passing ``f0`` as the callable, wrap it to accept
//        only ``x``; e.g., pass ``fun=lambda x: f0(x, *my_args, **my_kwargs)`` as the
//        callable, where ``my_args`` (tuple) and ``my_kwargs`` (dict) have been
//        gathered before invoking this function.
//    args : tuple, optional
//        Extra arguments passed to the objective function and its derivative(s).
//    method : str, optional
//        Type of solver.  Should be one of
//
//        - 'bisect'    :ref:`(see here) <optimize.root_scalar-bisect>`
//        - 'brentq'    :ref:`(see here) <optimize.root_scalar-brentq>`
//        - 'brenth'    :ref:`(see here) <optimize.root_scalar-brenth>`
//        - 'ridder'    :ref:`(see here) <optimize.root_scalar-ridder>`
//        - 'toms748'    :ref:`(see here) <optimize.root_scalar-toms748>`
//        - 'newton'    :ref:`(see here) <optimize.root_scalar-newton>`
//        - 'secant'    :ref:`(see here) <optimize.root_scalar-secant>`
//        - 'halley'    :ref:`(see here) <optimize.root_scalar-halley>`
//
//    bracket: A sequence of 2 floats, optional
//        An interval bracketing a root.  ``f(x, *args)`` must have different
//        signs at the two endpoints.
//    x0 : float, optional
//        Initial guess.
//    x1 : float, optional
//        A second guess.
//    fprime : bool or callable, optional
//        If `fprime` is a boolean and is True, `f` is assumed to return the
//        value of the objective function and of the derivative.
//        `fprime` can also be a callable returning the derivative of `f`. In
//        this case, it must accept the same arguments as `f`.
//    fprime2 : bool or callable, optional
//        If `fprime2` is a boolean and is True, `f` is assumed to return the
//        value of the objective function and of the
//        first and second derivatives.
//        `fprime2` can also be a callable returning the second derivative of `f`.
//        In this case, it must accept the same arguments as `f`.
//    xtol : float, optional
//        Tolerance (absolute) for termination.
//    rtol : float, optional
//        Tolerance (relative) for termination.
//    maxiter : int, optional
//        Maximum number of iterations.
//    options : dict, optional
//        A dictionary of solver options. E.g., ``k``, see
//        :obj:`show_options()` for details.
//
//    Returns
//    -------
//    sol : RootResults
//        The solution represented as a ``RootResults`` object.
//        Important attributes are: ``root`` the solution , ``converged`` a
//        boolean flag indicating if the algorithm exited successfully and
//        ``flag`` which describes the cause of the termination. See
//        `RootResults` for a description of other attributes.
//
//    See also
//    --------
//    show_options : Additional options accepted by the solvers
//    root : Find a root of a vector function.
//
//    Notes
//    -----
//    This section describes the available solvers that can be selected by the
//    'method' parameter.
//
//    The default is to use the best method available for the situation
//    presented.
//    If a bracket is provided, it may use one of the bracketing methods.
//    If a derivative and an initial value are specified, it may
//    select one of the derivative-based methods.
//    If no method is judged applicable, it will raise an Exception.
//
//    Arguments for each method are as follows (x=required, o=optional).
//
//    +-----------------------------------------------+---+------+---------+----+----+--------+---------+------+------+---------+---------+
//    |                    method                     | f | args | bracket | x0 | x1 | fprime | fprime2 | xtol | rtol | maxiter | options |
//    +===============================================+===+======+=========+====+====+========+=========+======+======+=========+=========+
//    | :ref:`bisect <optimize.root_scalar-bisect>`   | x |  o   |    x    |    |    |        |         |  o   |  o   |    o    |   o     |
//    +-----------------------------------------------+---+------+---------+----+----+--------+---------+------+------+---------+---------+
//    | :ref:`brentq <optimize.root_scalar-brentq>`   | x |  o   |    x    |    |    |        |         |  o   |  o   |    o    |   o     |
//    +-----------------------------------------------+---+------+---------+----+----+--------+---------+------+------+---------+---------+
//    | :ref:`brenth <optimize.root_scalar-brenth>`   | x |  o   |    x    |    |    |        |         |  o   |  o   |    o    |   o     |
//    +-----------------------------------------------+---+------+---------+----+----+--------+---------+------+------+---------+---------+
//    | :ref:`ridder <optimize.root_scalar-ridder>`   | x |  o   |    x    |    |    |        |         |  o   |  o   |    o    |   o     |
//    +-----------------------------------------------+---+------+---------+----+----+--------+---------+------+------+---------+---------+
//    | :ref:`toms748 <optimize.root_scalar-toms748>` | x |  o   |    x    |    |    |        |         |  o   |  o   |    o    |   o     |
//    +-----------------------------------------------+---+------+---------+----+----+--------+---------+------+------+---------+---------+
//    | :ref:`secant <optimize.root_scalar-secant>`   | x |  o   |         | x  | o  |        |         |  o   |  o   |    o    |   o     |
//    +-----------------------------------------------+---+------+---------+----+----+--------+---------+------+------+---------+---------+
//    | :ref:`newton <optimize.root_scalar-newton>`   | x |  o   |         | x  |    |   o    |         |  o   |  o   |    o    |   o     |
//    +-----------------------------------------------+---+------+---------+----+----+--------+---------+------+------+---------+---------+
//    | :ref:`halley <optimize.root_scalar-halley>`   | x |  o   |         | x  |    |   x    |    x    |  o   |  o   |    o    |   o     |
//    +-----------------------------------------------+---+------+---------+----+----+--------+---------+------+------+---------+---------+
//
//    Examples
//    --------
//
//    Find the root of a simple cubic
//
//    >>> from scipy import optimize
//    >>> def f(x):
//    ...     return (x**3 - 1)  # only one real root at x = 1
//
//    >>> def fprime(x):
//    ...     return 3*x**2
//
//    The `brentq` method takes as input a bracket
//
//    >>> sol = optimize.root_scalar(f, bracket=[0, 3], method='brentq')
//    >>> sol.root, sol.iterations, sol.function_calls
//    (1.0, 10, 11)
//
//    The `newton` method takes as input a single point and uses the
//    derivative(s).
//
//    >>> sol = optimize.root_scalar(f, x0=0.2, fprime=fprime, method='newton')
//    >>> sol.root, sol.iterations, sol.function_calls
//    (1.0, 11, 22)
//
//    The function can provide the value and derivative(s) in a single call.
//
//    >>> def f_p_pp(x):
//    ...     return (x**3 - 1), 3*x**2, 6*x
//
//    >>> sol = optimize.root_scalar(
//    ...     f_p_pp, x0=0.2, fprime=True, method='newton'
//    ... )
//    >>> sol.root, sol.iterations, sol.function_calls
//    (1.0, 11, 11)
//
//    >>> sol = optimize.root_scalar(
//    ...     f_p_pp, x0=0.2, fprime=True, fprime2=True, method='halley'
//    ... )
//    >>> sol.root, sol.iterations, sol.function_calls
//    (1.0, 7, 8)
