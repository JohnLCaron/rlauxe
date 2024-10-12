package org.cryptobiotic.rlauxe.core


import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.optim.MaxEval
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction;
import org.apache.commons.math3.optim.univariate.SearchInterval;
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair;
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.math.ln
import kotlin.math.max

// betting functions that use Kelly optimization of lambda parameter for the BettingFn

// We know the true rate of p1 and p2 errors
class OracleComparison(
    val N: Int, // not used
    val withoutReplacement: Boolean = true,  // not used
    val upperBound: Double,  // not used
    val a: Double, // noerror
    val p1: Double = 1.0e-2, // the rate of 1-vote overstatements
    val p2: Double = 1.0e-4, // the rate of 2-vote overstatements
): BettingFn {
    val lam: Double
    init {
        require(upperBound > 1.0)
        val kelly = OptimalLambda(a, p1, p2)
        lam = kelly.solve()
    }
    // note lam is a constant
    override fun bet(prevSamples: PrevSamplesWithRates): Double {
        return lam
    }
}

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
// data X1 , . . . , Xi−1 . This allows us to estimate the rates based on past samples as
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
class AdaptiveComparison(
    val N: Int,
    val withoutReplacement: Boolean = true,
    val upperBound: Double,
    val a: Double, // noerror
    val d1: Int,  // weight p1, p3 // TODO derive from p1-p4 ??
    val d2: Int, // weight p2, p4
    val p1: Double = 1.0e-2, // apriori rate of 1-vote overstatements; set to 0 to remove consideration
    val p2: Double = 1.0e-4, // apriori rate of 2-vote overstatements; set to 0 to remove consideration
    val p3: Double = 1.0e-2, // apriori rate of 1-vote understatements; set to 0 to remove consideration
    val p4: Double = 1.0e-4, // apriori rate of 2-vote understatements; set to 0 to remove consideration
    val eps: Double = .00001
): BettingFn {
    init {
        require(upperBound > 1.0)
    }

    override fun bet(prevSamples: PrevSamplesWithRates): Double {
        val lastj = prevSamples.numberOfSamples()
        val p1est = if (p1 == 0.0) 0.0 else estimateRate(d1, p1, prevSamples.sampleP1count().toDouble() / lastj, lastj, eps)
        val p2est = if (p2 == 0.0) 0.0 else estimateRate(d2, p2, prevSamples.sampleP2count().toDouble() / lastj, lastj, eps)
        val p3est = if (p3 == 0.0) 0.0 else estimateRate(d1, p3, prevSamples.sampleP3count().toDouble() / lastj, lastj, eps)
        val p4est = if (p4 == 0.0) 0.0 else estimateRate(d2, p4, prevSamples.sampleP4count().toDouble() / lastj, lastj, eps)
        if (p3est > 0 || p4est > 0) {
            println("wtf")
        }
        val mui = populationMeanIfH0(N, withoutReplacement, prevSamples)
        val kelly = OptimalLambda(a, p1est, p2est, p3est, p4est, mui)
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

/**
 * This follows the code in https://github.com/spertus/comparison-RLA-betting/blob/main/comparison_audit_simulations.R
 * Not completely sure of the relationship to COBRA section 3.2.
 * Has been generalized to allow p3 and p4 errors and sampling without replacement (WoR) by setting mui.
 * Note if (lam < 1.0) "**** betting against"
 *
 * a := 1 / (2 − v/au)
 *    v := 2Āc − 1 is the diluted margin: the difference in votes for the reported winner and reported loser, divided by the total number of ballots cast.
 *   au := assort upper value, = 1 for plurality, 1/(2*minFraction) for supermajority
 * mui := mean value under H0, = 1/2 for with replacement
 * p0 := #{xi = a}/N is the rate of correct CVRs.
 * p1 := #{xi = a/2}/N is the rate of 1-vote overstatements.
 * p2 := #{xi = 0}/N is the rate of 2-vote overstatements.
 * p3 := #{xi = 3a/2}/N is the rate of 1-vote understatements.
 * p4 := #{xi = 2a}/N is the rate of 2-vote understatements.
 */
class OptimalLambda(val a: Double, val p1: Double, val p2: Double, val p3: Double = 0.0, val p4: Double = 0.0, val mui: Double = 0.5) {
    val p0 = 1.0 - p1 - p2 - p3 - p4
    val debug = false

    fun solve(): Double {
        val stopwatch = Stopwatch()
        // TODO why arent we giving it the derivitive ?
        val function = UnivariateFunction { lam -> expected_value_logT(lam) }  // The function to be optimized

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
        if (debug) println( "Kelly: p1=${p1}  p2=${p2}  p3=${p3}  p4=${p4} point=${result.point} took=$stopwatch")
        return result.point
    }

    // EF [Ti ] = p0 [1 + λ(a − mu_i)] + p1 [1 + λ(a/2 − mu_i)] + p2 [1 − λ*mu_i)]  + p3 [1 + λ(3*a/2 − mu_i)]  + p4 [[1 + λ(2*a − mu_i)]
    // EF [Ti ] = p0 * EF[1 + λ(a − mu_i)] + p1 * EF[1 + λ(a/2 − mu_i)] + p2 * EF[1 − λ*mu_i)] + p3 * EF[1 + λ(3*a/2 − mu_i)] + p4 * EF[1 + λ(2*a − mu_i)]

    // log_expected_value <- function(lambda, a, p_1, p_2){
    //  log(1 + lambda * (a - 1/2)) * (1 - p_1 - p_2) + log(1 + lambda * (a/2 - 1/2)) * p_1 + log(1 - lambda / 2) * p_2
    //}

    // not really sure of this; its not really the ln of Ti
    // EF [ln(Ti) ] = p0 * ln[1 + λ(a − mu_i)] + p1 * ln[1 + λ(a/2 − mu_i)] + p2 * ln[1 − λ*mu_i)]  + p3 * ln[1 + λ(3*a/2 − mu_i)] + p4 * ln[1 + λ(2*a − mu_i)]
    fun expected_value_logT(lam: Double): Double {
        return ln(1.0 + lam * (a - mui)) * p0 +
                ln(1.0 + lam * (a*0.5 - mui)) * p1 +
                ln(1.0 - lam * mui) * p2 +
                ln(1.0 + lam * (a*1.5 - mui)) * p3 +
                ln(1.0 + lam * (a*2.0 - mui)) * p4
    }

    // why not just use
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
    // TODO why arent we using the derivitive ? Apparently optimize() operates on the function, not its derivitive.
    // "The function optimize searches the interval from lower to upper for a minimum or maximum of the function f with respect to its first argument."
    fun derivativeFromRcode(lam: Double): Double {
        return  p0 * (a - mui) / (1.0 + lam * (a - mui)) +
                p1 * (a*0.5 - mui) / (1.0 + lam * (a*0.5 - mui)) +
                p2 * mui / (lam * mui - 1.0) +  // LOOK the R code has the sign wrong
                p3 * (a*1.5 - mui) / (1.0 + lam * (a*1.5 - mui)) +
                p4 * (a*2.0 - mui) / (1.0 + lam * (a*2.0 - mui))
    }
}
