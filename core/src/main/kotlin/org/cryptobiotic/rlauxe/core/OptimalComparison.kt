package org.cryptobiotic.rlauxe.core


import org.apache.commons.math3.analysis.UnivariateFunction
import org.apache.commons.math3.optim.MaxEval
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType
import org.apache.commons.math3.optim.univariate.BrentOptimizer
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction
import org.apache.commons.math3.optim.univariate.SearchInterval
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.math.ln
import kotlin.math.max

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
    val eps: Double = .00001
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
        return max(est, eps) // lower bound on the estimated rate
    }
}

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
        val stopwatch = Stopwatch()
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
        if (debug) println( "Kelly: p2o=${p2o}  p1o=${p1o}  p1u=${p1u}  p2u=${p2u} point=${result.point} took=$stopwatch")
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

