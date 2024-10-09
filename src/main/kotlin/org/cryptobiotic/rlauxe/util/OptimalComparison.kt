package org.cryptobiotic.rlauxe.util


import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.optim.MaxEval
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction;
import org.apache.commons.math3.optim.univariate.SearchInterval;
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair;
import org.cryptobiotic.rlauxe.core.BettingFn
import org.cryptobiotic.rlauxe.core.PrevSamplesWithRates
import kotlin.math.ln
import kotlin.math.max

// betting functions that use Kelly optimization

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
        val kelly = Kelly(a, p1, p2)
        lam = kelly.solve()
    }
    // note lam is a constant
    override fun bet(prevSamples: PrevSamplesWithRates): Double {
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
// and set equal to 0 to obtain the bet λi . Fixing p̃_1i := 0 allows us to use (3), in
// which case λi = (2 − 4a(1 − p̃2i ))/(1 − 2a).
//
//
// For k ∈ {1, 2} we set a value d_k ≥ 0, capturing the degree of shrinkage to the a priori estimate p̃_k ,
// and a truncation factor eps_k ≥ 0, enforcing a lower bound on the estimated rate.
// Let p̂_ki be the sample rates at time i, e.g., p̂_2i = Sum(1{Xj = 0})/i , j=1..i
// Then the shrink-trunc estimate is:
//   p_̃ki := (d_k * p̃_k + i * p̂_k(i−1)) / (d_k + i − 1) ∨ epsk  ; COBRA eq (4)

class AdaptiveComparison(
    val N: Int,
    val withoutReplacement: Boolean = true,
    val upperBound: Double,
    val a: Double, // noerror
    val d1: Int,  // weight p1
    val d2: Int, // weight p2
    val p1: Double = 1.0e-2, // apriori rate of 1-vote overstatements
    val p2: Double = 1.0e-4, // apriori rate of 2-vote overstatements
    val eps: Double = .00001
): BettingFn {
    init {
        require(upperBound > 1.0)
    }

    override fun bet(prevSamples: PrevSamplesWithRates): Double {
        val lastj = prevSamples.numberOfSamples()
        val p1est = estimateRate(d1, p1, prevSamples.sampleP1count().toDouble() / lastj, lastj, eps)
        val p2est = estimateRate(d2, p2, prevSamples.sampleP2count().toDouble()  / lastj, lastj, eps)
        val kelly = Kelly(a, p1est, p2est)
        if (lastj == 100)
            print("")
        return kelly.solve()
    }

    fun estimateRate(d: Int, apriori: Double, sampleRate: Double, sampleNum: Int, eps: Double): Double {
        //   (d_k * p̃_k + i * p̂_k(i−1)) / (d_k + i − 1) ∨ epsk  ; COBRA eq (4)
        val est = (d * apriori + sampleNum * sampleRate) / (d + sampleNum - 1)
        return max(est, eps) // lower bound on the estimated rate
    }
}

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

class Kelly(val a: Double, val p1: Double, val p2: Double) {
    val p0 = 1.0 - p1 - p2
    val debug = false

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
        if (debug) println( "Kelly: p1=${p1}  p2=${p2} point=${result.point} took=$stopwatch")
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

    // not used
    //  derivative <- function(lambda){
    //    (a - 1/2) * (1 - p_1 - p_2) / (1 + lambda * (a - 1/2)) + (a - 1) * p_1 / (2 - lambda * (1 - a)) + p_2 / (2 - lambda)
    //  }
    fun derivative(lam: Double): Double {
        return (a - 0.5) * (1.0 - p1 - p2) / (1.0 + lam * (a - 0.5)) + (a - 1.0) * p1 / (2.0 - lam * (1.0 - a)) + p2 / (2.0 - lam)
    }
}
