package org.cryptobiotic.rlauxe.util


import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.optim.MaxEval
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction;
import org.apache.commons.math3.optim.univariate.SearchInterval;
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair;
import org.cryptobiotic.rlauxe.core.BettingFn
import org.cryptobiotic.rlauxe.core.Samples
import kotlin.math.ln

class OptimalComparison(
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
        val kelly = Kelly(a, p1, p2)
        lam = kelly.solve()
    }
    // note lam is a constant
    override fun bet(prevSamples: Samples): Double {
        return lam
    }
}

//
// Results in Table 2
//
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
//}
//

//optimal_lambda <- function(a, p_1, p_2){
//  temp_log_expected_value <- function(lambda){
//    log_expected_value(lambda, a = a, p_1 = p_1, p_2 = p_2)
//  }
//  derivative <- function(lambda){
//    (a - 1/2) * (1 - p_1 - p_2) / (1 + lambda * (a - 1/2)) + (a - 1) * p_1 / (2 - lambda * (1 - a)) + p_2 / (2 - lambda)
//  }
//  solution <- optimize(temp_log_expected_value, interval = c(0,2), maximum = TRUE)
//  solution$maximum
//}

// TODO cache these results, esp useful for repeated trials
class Kelly(val a: Double, val p1: Double, val p2: Double) {
    val p0 = 1.0 - p1 - p2

    fun solve(): Double {
        val stopwatch = Stopwatch()
        // Define the function to be optimized
        val function = UnivariateFunction { lam -> log_expected_value(lam) }

        // Create an optimizer
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
        println( "Kelly: p1=${p1}  p2=${p2} point=${result.point} took=$stopwatch")
        return result.point
    }

    // EF [Ti ] = p0 [1 + λ(a − 1/2)] + p1 [1 − λ(1 − a)/2] + p2 [1 − λ/2]
    // EF [Ti ] = p0 * EF [1 + λ(a − 1/2)] + p1 * EF[1 − λ(1 − a)/2] + p2 * [1 − λ/2]
    // EF [Ti] = p0 * EF [1 + λ(a − 1/2)] + p1 * EF[1 − λ(1 − a)/2] + p2 * [1 − λ/2]
    // d/dλ (EF[log Ti])

    // log_expected_value <- function(lambda, a, p_1, p_2){
//  log(1 + lambda * (a - 1/2)) * (1 - p_1 - p_2) + log(1 + lambda * (a/2 - 1/2)) * p_1 + log(1 - lambda / 2) * p_2
//}
    fun log_expected_value(lam: Double): Double {
        return ln(1.0 + lam * (a - 0.5)) * p0 + ln(1.0 + lam * (a/2 - 0.5)) * p1 + ln(1.0 - lam / 2) * p2
    }

//  derivative <- function(lambda){
//    (a - 1/2) * (1 - p_1 - p_2) / (1 + lambda * (a - 1/2)) + (a - 1) * p_1 / (2 - lambda * (1 - a)) + p_2 / (2 - lambda)
//  }
    fun derivative(lam: Double): Double {
        return (a - 0.5) * (1.0 - p1 - p2) / (1.0 + lam * (a - 0.5)) + (a - 1.0) * p1 / (2.0 - lam * (1.0 - a)) + p2 / (2.0 - lam)
    }
}

fun mainExample(args: Array<String>) {
    // Define the function to be optimized
    val function = UnivariateFunction { x -> (x - 2) * (x - 2) + 1 }

    // Create an optimizer
    val optimizer = BrentOptimizer(1e-6, 1e-6)

    // Optimize the function within the given range [start, end]
    val start = -10.0
    val end = 10.0
    val result: UnivariatePointValuePair = optimizer.optimize(
        UnivariateObjectiveFunction(function),
        SearchInterval(start, end),
        MaxEval(1000)
    )

    // Get the optimum point and value
    val optimum = result.point
    val minValue = result.value

    // Print the result
    println("Optimum point: $optimum")
    println("Minimum value: $minValue")
}