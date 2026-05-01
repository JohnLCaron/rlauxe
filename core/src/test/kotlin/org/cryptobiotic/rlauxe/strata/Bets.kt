package org.cryptobiotic.rlauxe.strata

import org.cryptobiotic.rlauxe.util.Welford
import kotlin.math.sqrt

//    Methods to set bets that are eta-adaptive, data-adaptive, both, or neither.
//    Currently, bets for stratum k can only depend on eta and data within stratum k
//    TODO this is with replacement, so eta_ki = eta_k
//    TODO these are the betting strategies, array at a time
//
//    Parameters
//        ----------
//        eta: float in [0,1]
//            the null mean within stratum k
//        x: 1-dimensional np.array of length n_k := T_k(t) with elements in [0,1]
//            the data sampled from stratum k
//        kwargs: additional arguments specific to each strategy
//        Returns
//        ----------
//        lam: a length-n_k np.array corresponding to lambda_{ki} in the I-TSM:
//            prod_{i=1}^{T_k(t)} [1 + lambda_{ki (X_{ki} - eta_k)] (org - note eta_k is constant, ie with replacement)
//            prod_{i=1}^{T_k(t)} [1 + λ_ki (X_ki - eta_k)]         (mod)
//            M_kt(η_k) := Prod { (1 + λ_ki (X_ki − eta_ki )), i=1..T_k(t)) }  (eq 2.3.1) (switch to eta with replacement notation)

data class Kwargs(val args: Map<String, Double>) {
    fun get(key:String, def: Double?) = args[key] ?: def
    companion object {
        val empty = Kwargs(emptyMap())
    }
}

//     def inverse_eta(x, eta, **kwargs):
//        '''
//        eta_adaptive; c/eta for c in [0,1]
//        '''
//        c = kwargs.get('c', None)
//        l = kwargs.get('l', 0.1)
//        u = kwargs.get('u', 0.9)
//        if c is None:
//            lag_mean, lag_sd = Bets.lag_welford(x)
//            c_untrunc = lag_mean - lag_sd
//            c = np.minimum(np.maximum(l, c_untrunc), u)
//        lam = np.ones(len(x)) * c/eta
//        return lam

// eta_adaptive; c / eta for c in [0, 1]

// The second strategy is λInverse_kt(eta) := c_kt / eta_k , where c_kt is a predictable tuning parameter.
// An inverse bet is valid as long as 0 ≤ c_kt ≤ 1 and eta_k > 0.
//
// To set c_kt, note that for any given null eta_k, it is sensible to bet more when
// the true mean µ_k is larger and the standard deviation σ_k is smaller. This suggests setting
// c_kt := l_k ∨ (µ̂_k(t−1) − σ̂_k(t−1) ) ∧ u_k , where µ_̂k(t−1) and σ̂_k(t−1) are predictable estimates of
// the true mean and standard deviation.
//
// The limits lk , uk ∈ [0, 1) are user-chosen truncation parameters ensuring the bets are valid. As
// defaults, we recommend setting lk = 0.1 and uk = 0.9 so that some amount is always wagered
// but the I-TSMs cannot go broke.

fun inverse_eta(x: List<Double>, eta: Double, kwargs: Kwargs): List<Double> {

    var carg = kwargs.get("c", null) // assume scalar TODO ignore for now
    val l = kwargs.get("l", 0.1)!!
    val u = kwargs.get("u", 0.9)!!

    // c_kt := l_k ∨ (µ̂_k(t−1) − σ̂_k(t−1) ) ∧ u_k , where µ_̂k(t−1) and σ̂_k(t−1) are predictable estimates of
    // the true mean and standard deviation.

    val (lag_mean, lag_sd) = lag_welford(x, kwargs)
    val c_untrunc = lag_mean.zip(lag_sd).map { (a, b) -> a - b }
    val c = numpy_minimum(numpy_maximum(c_untrunc, l), u)

    val lam = c.map { it/eta }
    return lam
}

//     def lag_welford(x, **kwargs):
//        '''
//        computes the lagged mean and standard deviation using Welford's algorithm (not a bet)
//        inserts the default values 1/2 for the mean and 1/4 for the SD
//        ------------
//        kwargs:
//            mu_0: float in [0,1], the first value of the lagged running mean
//            sd_0: float in [0,1/2], the first 2 values of the lagged running SD
//        '''
//        mu_0 = kwargs.get("mu_0", 1/2)
//        sd_0 = kwargs.get("sd_0", 1/4)
//        w = Welford()
//        mu_hat = []
//        sd_hat = []
//        for x_i in x:
//            w.add(x_i)
//            mu_hat.append(float(w.mean))
//            sd_hat.append(np.sqrt(w.var_s))
//        if len(sd_hat) > 0:
//            sd_hat[0] = sd_0
//        lag_mu_hat = np.insert(np.array(mu_hat), 0, mu_0)[0:-1]
//        lag_sd_hat = np.insert(np.array(sd_hat), 0, sd_0)[0:-1]
//        return lag_mu_hat, lag_sd_hat

fun lag_welford(x: List<Double>, kwargs: Kwargs) : Pair<List<Double>, List<Double>> {

    val mu_0 = kwargs.get("mu_0", 0.5)!!
    val sd_0 = kwargs.get("sd_0", 0.25)!!

    val w = Welford()
    val mu_hat = mutableListOf<Double>()
    val sd_hat = mutableListOf<Double>()
    for (x_i in x) {
        w.update(x_i)
        mu_hat.add(w.mean)
        sd_hat.add(sqrt(w.variance()))
    }
    if (sd_hat.size > 0) {
        sd_hat[0] = sd_0
    }

    // lag_mu_hat = np.insert(np.array(mu_hat), 0, mu_0)[0:-1]
    //    arr = np.array(mu_hat) turns into array from list ??
    //    ins = np.insert(arr, 0, mu_0) // prepends mu_0
    //    ins[0:-1] // removes the last element
    val lag_mu_hat: List<Double> = listOf(mu_0) + mu_hat.subList(0, mu_hat.size-1)
    val lag_sd_hat: List<Double> = listOf(sd_0) + sd_hat.subList(0, sd_hat.size-1)

    return Pair(lag_mu_hat, lag_sd_hat)
}

/*
//     def agrapa(x, eta, **kwargs):
//        '''
//        AGRAPA (approximate-GRAPA) from Section B.3 of Waudby-Smith and Ramdas, 2022
//        lambda is set to approximately maximize a Kelly-like objective (expectation of log martingale)
//        eta-adaptive
//        -------------
//        kwargs:
//            sd_min: scalar, the minimum value allowed for the estimated standard deviation
//            c: scalar, the maximum value allowed for bets
//        '''
//        #bet the farm if you can't possibly lose (eta is 0)
//        sd_min = kwargs.get('sd_min', 0.01) #floor for sd (prevents divide by zero error)
//        c = kwargs.get('c', 0.75) #threshold for bets from W-S and R
//        eps = kwargs.get('eps', 1e-5) #floor for eta (prevents divide by zero error)
//
//        # using welford
//        lag_mu_hat, lag_sd_hat = Bets.lag_welford(x)
//
//        lag_sd_hat = np.maximum(lag_sd_hat, sd_min)
//        lam_untrunc = (lag_mu_hat - eta) / (lag_sd_hat**2 + (lag_mu_hat - eta)**2)
//        #this rule says to bet the farm when eta is 0 (can't possibly lose)
//        lam_trunc = np.maximum(0, np.where(eta > 0, np.minimum(lam_untrunc, c/(eta+eps)), np.inf))
//        return lam_trunc

// Compare with AgrapaBet in ShangrlaBettingFns
fun agrapa(x: List<Double>, eta: Double, kwargs: Kwargs): List<Double> {
    // bet the farm if you can"t possibly lose (eta is 0)
    val sd_min = kwargs.get("sd_min", 0.01)!!   // floor for sd (prevents divide by zero error)
    val c = kwargs.get("c", 0.75)!!             // threshold for bets from W-S and R
    val eps = kwargs.get("eps", 1e-5)!!         // floor for eta (prevents divide by zero error)

    // using welford
    val (lag_mu_hat, lag_sd_hatw) = lag_welford(x, kwargs) // TODO python doesnt pass kwargs

    val lag_sd_hat = numpy_maximum(lag_sd_hatw, sd_min)
    val lam_untrunc = (lag_mu_hat - eta) / (lag_sd_hat**2 + (lag_mu_hat - eta)**2)

    // this rule says to bet the farm when eta is 0 (can't possibly lose)
    val lam_trunc = numpy_maximum(0, np.where(eta > 0, numpy_minimum(lam_untrunc, c/(eta+eps)), np.inf))
    return lam_trunc
}

//     def predictable_plugin(x, eta, **kwargs):
//        '''
//        predictable plug in estimator of Waudby-Smith and Ramdas 2024
//        eta-nonadaptive
//        -----
//        kwargs:
//            c: the truncation parameter
//        '''
//        c = kwargs.get('c', 0.9)
//        alpha = kwargs.get('alpha', 0.05)
//        sd_min = kwargs.get('sd_min', 0.01)
//        #compute running mean and SD
//        lag_mu_hat, lag_sd_hat = Bets.lag_welford(x)
//        lag_sd_hat = np.maximum(sd_min, lag_sd_hat)
//        t = np.arange(1, len(x) + 1)
//
//        lam_untrunc = np.sqrt((2 * np.log(2/alpha)) / (lag_sd_hat * t * np.log(t + 1)))
//        lam = np.minimum(lam_untrunc, c)
//        return lam

fun predictable_plugin(x: List<Double>, eta: Double, kwargs: Kwargs): List<Double> {
    val c = kwargs.get("c", 0.9)
    val alpha = kwargs.get("alpha", 0.05)
    val sd_min = kwargs.get("sd_min", 0.01)

    // compute running mean and SD
    val (lag_mu_hat, lag_sd_hat) = lag_welford(x, kwargs)
    val lag_sd_hat = np.maximum(sd_min, lag_sd_hat)
    val t = np.arange(1, x.size + 1)

    val lam_untrunc = np.sqrt((2 * np.log(2/alpha)) / (lag_sd_hat * t * np.log(t + 1)))
    val lam = np.minimum(lam_untrunc, c)
    return lam
} */
