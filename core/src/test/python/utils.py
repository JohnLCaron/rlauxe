import numpy as np
import scipy as sp
import cvxopt
import matplotlib.pyplot as plt
import math
import itertools
import pypoman
import warnings
from iteround import saferound
from scipy.stats import bernoulli, multinomial, chi2, t, beta
from scipy.stats.mstats import gmean
from welford import Welford
from functools import lru_cache




class Bets:
    '''
    Methods to set bets that are eta-adaptive, data-adaptive, both, or neither.
    Currently, bets for stratum k can only depend on eta and data within stratum k

    Parameters
        ----------
        eta: float in [0,1]
            the null mean within stratum k
        x: 1-dimensional np.array of length n_k := T_k(t) with elements in [0,1]
            the data sampled from stratum k
        kwargs: additional arguments specific to each strategy
        Returns
        ----------
        lam: a length-n_k np.array corresponding to lambda_{ki} in the I-TSM:
            prod_{i=1}^{T_k(t)} [1 + lambda_{ki (X_{ki} - eta_k)]

    '''
    # def __init__(self, x: np.array=None, eta: float=None, **kwargs):
    #    self.x = x
    #    self.eta = eta
    #    self.kwargs = kwargs

    def lag_welford(x, **kwargs):
        '''
        computes the lagged mean and standard deviation using Welford's algorithm (not a bet)
        inserts the default values 1/2 for the mean and 1/4 for the SD
        ------------
        kwargs:
            mu_0: float in [0,1], the first value of the lagged running mean
            sd_0: float in [0,1/2], the first 2 values of the lagged running SD
        '''
        mu_0 = kwargs.get("mu_0", 1/2)
        sd_0 = kwargs.get("sd_0", 1/4)
        w = Welford()
        mu_hat = []
        sd_hat = []
        for x_i in x:
            w.add(x_i)
            mu_hat.append(float(w.mean))
            sd_hat.append(np.sqrt(w.var_s))
        if len(sd_hat) > 0:
            sd_hat[0] = sd_0
        lag_mu_hat = np.insert(np.array(mu_hat), 0, mu_0)[0:-1]
        lag_sd_hat = np.insert(np.array(sd_hat), 0, sd_0)[0:-1]
        return lag_mu_hat, lag_sd_hat

    # alternative to Welford method above
    def lag_mean_var(x, **kwargs):
        '''
        Computes the lagged running mean and variance of a sequence of data.
        inserts default values of 1/2 for the mean and 1/4 for the SD at the beginning of the sequence
        ---------------
        Returns:
            A tuple containing two numpy arrays:
                - running_mean: The running mean of the data.
                - running_var: The running variance of the data.`
        '''
        n = len(x)
        mu_0 = kwargs.get("mu_0", 1/2)
        sd_0 = kwargs.get("sd_0", 1/4)

        mu_hat = np.zeros(n)
        var_hat = np.zeros(n)

        for i in range(n):
            current_x = x[:i+1]
            mu_hat[i] = np.mean(current_x)
            var_hat[i] = np.var(current_x)
        lag_mean = np.insert(mu_hat, 0, mu_0)[0:-1]
        lag_var = np.insert(var_hat, 0, sd_0**2)[0:-1]
        lag_sd = np.sqrt(lag_var)
        return lag_mean, lag_sd

    #work in progress: rewrite classes to define and inherit arguments from init
    #use kwargs for additional arguments specific to each method
    def fixed(x, eta, **kwargs):
        '''
        lambda fixed to c
        eta-nonadaptive
        ---------------
        kwargs:
            c: the fixed bet size
        '''
        c = kwargs.get('c', 0.75)
        lam = c * np.ones(x.size)
        return lam

    def cobra(x, eta, A_c, **kwargs):
        '''
        comparison optimal betting, per Spertus (2023) https://arxiv.org/pdf/2304.01010
        set up for a standard ballot-level comparison audit, defined on [0,1], ignoring understatements (i.e., interpreting them as correct CVRs), with null mean 1/2
        eta-adaptive
        -------------
        A_c: float in [0,1]
            the reported margin in the
        kwargs:
            p_1: the rate of 1-vote overstatements of the reported margin, defaults to 0
            p_2: the rate of 2-vote overstatements of the reported margin, defaults to 0.001
        '''
        p_1 = kwargs.get('p_1', 0.0)
        p_2 = kwargs.get('p_2', 0.001)
        p_0 = 1 - p_1 - p_2
        v = 2 * A_c - 1
        a = 1/(2-v)
        vals = np.array([0, a/2, a])
        probs = np.array([p_2, p_1, p_0])
        if (p_1 == 0) and (p_2 == 0):
            out = (1/eta) * np.ones(len(x))
        else:
            deriv = lambda lam: np.sum(probs * (vals - eta) / (1 + lam * (vals - eta)))
            lam_star = sp.optimize.root_scalar(deriv, bracket = [0, 1/eta], method = 'bisect')
            out = lam_star['root'] * np.ones(len(x))
        return out


    def universal_portfolio(x, eta, **kwargs):
        '''
        universal portfolio bets; per Waudby-Smith et al (2025) https://arxiv.org/pdf/2504.02818
        see also Ricardo Sandoval's code on Github: https://github.com/RicardoJSandoval/log-optimality/blob/main/utils/betting_strategies.py
        from which this function is copied w minor alterations
        computation can be unstable because a high-order polynomial (the raw wealth) is being integrated
        -----------------------------------
        kwargs:
            step: int, defaults to 1
                this is a step size in n = len(x), at which the up bet will be recomputed
                e.g., if step = 10, the bet is only recomputed every 10 samples
        '''
        #beta_distr = sp.stats.beta(0.5, 0.5) <- this is for the beta(1/2, 1/2)-weighted UP, we can also just use the uniform weighted UP
        step = kwargs.get("step", 1)
        n = len(x)
        z = x/eta - 1
        out = np.zeros(len(x))
        for i in range(1,n+1):
            if (i == 1) or (i % step == 0):
                num = lambda l : (l * np.prod(1 + l * z[:i], dtype=np.float128))
                denom = lambda l : (np.prod(1 + l * z[:i], dtype=np.float128))

                num_val = sp.integrate.quad(num, 0, 1)
                denom_val = sp.integrate.quad(denom, 0, 1)
                weight = num_val[0] / denom_val[0]
                if weight > 1 or weight < 0:
                    raise ValueError("UP is numerically unstable and returning invalid weights")
            bet = weight / eta # transformation to a bet per 1.2 of https://arxiv.org/pdf/2504.02818
            out[i-1] = bet
        return out


    def predictable_plugin(x, eta, **kwargs):
        '''
        predictable plug in estimator of Waudby-Smith and Ramdas 2024
        eta-nonadaptive
        -----
        kwargs:
            c: the truncation parameter
        '''
        c = kwargs.get('c', 0.9)
        alpha = kwargs.get('alpha', 0.05)
        sd_min = kwargs.get('sd_min', 0.01)
        #compute running mean and SD
        lag_mu_hat, lag_sd_hat = Bets.lag_welford(x)
        lag_sd_hat = np.maximum(sd_min, lag_sd_hat)
        t = np.arange(1, len(x) + 1)

        lam_untrunc = np.sqrt((2 * np.log(2/alpha)) / (lag_sd_hat * t * np.log(t + 1)))
        lam = np.minimum(lam_untrunc, c)
        return lam

    def apriori_bernoulli(x, eta, **kwargs):
        '''
        uses the optimal bets for the Bernoulli (following the SPRT), plugging in an estimate of the alternative mean
        lambda is eta-adaptive
        ----------------------
        kwargs:
            mu_0: float in (eta, 1]
                alternative hypothesized value for the population mean
            trunc_eta: float in [0,1]
                the eta at which truncation should be done.
                useful if bet is to be used over a band of etas.
                defaults to eta
            c: float in [0,1],
                truncation factor to keep bets below 1/max_eta
        '''
        mu_0 = kwargs.get("mu_0", (eta + 1)/2)
        c = kwargs.get("c", .99)
        min_lam = kwargs.get("min_lam", 0)
        if eta == 0:
            lam = np.inf
        elif eta == 1:
            lam = 0
        elif mu_0 == 1:
            lam = c/eta
        else:
            lam = np.minimum(np.maximum(min_lam, ((mu_0 / eta) - 1) / (1 - eta)), c/eta)
        lam = np.ones(len(x)) * lam
        return lam

    def predictable_bernoulli(x, eta, **kwargs):
        '''
        uses the optimal bets for the Bernoulli (following the SPRT), plugging in shrunk/truncated empirical mean estimate
        see https://projecteuclid.org/journals/annals-of-applied-statistics/volume-17/issue-1/ALPHA-Audit-that-learns-from-previously-hand-audited-ballots/10.1214/22-AOAS1646.short

        some of the code and documentation comes from shrink_trunc in Philip Stark's SHANGRLA code: https://github.com/pbstark/SHANGRLA/blob/main/shangrla/shangrla/NonnegMean.py


        lambda is eta-adaptive
        -------------
        kwargs:
            mu_0: float in (eta, u) (default u*(1-eps))
                initial alternative hypothesized value for the population mean
            c: float in [0,1]
                bet is truncated to c/eta
            eps_0: positive float
                scale factor in the sequence eps allowing the estimated mean to approach eta from above
            d: positive float
                relative weight of eta compared to an observation, in updating the alternative for each term
            sd_min: positive float
                lower threshold for the standard deviation of the sample, to avoid divide-by-zero errors and
                to limit the weight of u

        '''
        # set the parameters
        mu_0 = kwargs.get('mu_0', (eta + 1)/2) #defaults to midpoint between null and upper bound
        eps_0 = kwargs.get('eps', 0.5)
        eta_tol = kwargs.get('eps', 1e-5) #floor for eta (prevents divide by zero error)
        d = kwargs.get('d', 20)
        c = kwargs.get('c', 0.95)
        minsd = kwargs.get('sd_min', 0.01)

        S = np.insert(np.cumsum(x), 0, 0)[0:-1]  # 0, x_1, x_1+x_2, ...,
        j = np.arange(1,len(x)+1)              # 1, 2, 3, ..., len(x)
        trunc_below = np.maximum((d * mu_0 + S)/(d+j-1), eta + eps_0/np.sqrt(d+j-1))
        lam = np.minimum((trunc_below / eta - 1)/(1 - eta), c/(eta+eta_tol))
        return lam



    def agrapa(x, eta, **kwargs):
        '''
        AGRAPA (approximate-GRAPA) from Section B.3 of Waudby-Smith and Ramdas, 2022
        lambda is set to approximately maximize a Kelly-like objective (expectation of log martingale)
        eta-adaptive
        -------------
        kwargs:
            sd_min: scalar, the minimum value allowed for the estimated standard deviation
            c: scalar, the maximum value allowed for bets
        '''
        #bet the farm if you can't possibly lose (eta is 0)
        sd_min = kwargs.get('sd_min', 0.01) #floor for sd (prevents divide by zero error)
        c = kwargs.get('c', 0.75) #threshold for bets from W-S and R
        eps = kwargs.get('eps', 1e-5) #floor for eta (prevents divide by zero error)

        # using welford
        lag_mu_hat, lag_sd_hat = Bets.lag_welford(x)

        lag_sd_hat = np.maximum(lag_sd_hat, sd_min)
        lam_untrunc = (lag_mu_hat - eta) / (lag_sd_hat**2 + (lag_mu_hat - eta)**2)
        #this rule says to bet the farm when eta is 0 (can't possibly lose)
        lam_trunc = np.maximum(0, np.where(eta > 0, np.minimum(lam_untrunc, c/(eta+eps)), np.inf))
        return lam_trunc

    def trunc(x, eta, **kwargs):
        '''
        doesn't bet if running mean is less than null, otherwise bets inversely proportional to null
        eta-adaptive
        '''
        S = np.insert(np.cumsum(x),0,0)[0:-1]  # 0, x_1, x_1+x_2, ...,
        j = np.arange(1,len(x)+1)  # 1, 2, 3, ..., len(x)
        mu_hat = S/j
        eps = 1e-5
        lam_trunc = np.where(eta <= mu_hat, .75 / (eta + eps), 0)
        return lam_trunc

    def inverse_eta(x, eta, **kwargs):
        '''
        eta_adaptive; c/eta for c in [0,1]
        '''
        c = kwargs.get('c', None)
        l = kwargs.get('l', 0.1)
        u = kwargs.get('u', 0.9)
        if c is None:
            lag_mean, lag_sd = Bets.lag_welford(x)
            c_untrunc = lag_mean - lag_sd
            c = np.minimum(np.maximum(l, c_untrunc), u)
        lam = np.ones(len(x)) * c/eta
        return lam


    def negative_exponential(x, eta, **kwargs):
        '''
        eta-adaptive; smooth, bets more the higher the empirical mean is above the null mean
        The bet makes the UI-TS convex
        ----
        kwargs:
            eps: float in (0,1]: epsilon, the size of the bet at the empirical mean
            c: float in (0.5, 1] if eps is not given, it is set as (c - lag_sd_hat)\
                note that the largest SD for a [0,1]-bounded RV is 0.5
        '''
        if len(x) == 0: #negexp_uits sometimes calls this function with no samples
            lam = None #in that case, no bets are returned
        else:
            a = kwargs.get("a", None) # explicit value for log-intercept
            b = kwargs.get("b", None) # explicit value for log-coefficient on eta
            c = kwargs.get("c", 0.75)
            eps = kwargs.get("eps", None)
            if eps is not None:
                assert 0 < eps <= 1, "eps is OOB, must be in (0,1]"
                lag_mu_hat = np.insert(np.cumsum(x),0,1/2)[0:-1] / np.arange(1,len(x)+1)
                b = (1 - np.log(eps)) / lag_mu_hat
                lam = np.exp(1 - b * eta)
            elif (a is not None) and (b is not None):
                lam = np.exp(a - b * eta)
            else:
                assert 0.5 < c <= 1, "c is OOB, must be in (0.5,1]"
                lag_mu_hat, lag_sd_hat = Bets.lag_welford(x)
                eps = c - lag_sd_hat
                b = (1 - np.log(eps)) / lag_mu_hat
                lam = np.exp(1 - b * eta)
        return lam


    # python supports some maximal number of recursions (by default 1000)
    # sys.setrecursionlimit allows this to be increased (say 10000)
    # if there are too many samples in x use different behavior
    # this will be an issue in particular if it hits a new value of lambda
    # see how quickly it runs the way we wrote it before and use if its slow

    #recursive derivative w caching
    # @lru_cache(maxsize=None)
    # def deriv(lam, x, eta):
    #     if len(x) == 1:
    #         return (x[0] - eta) / (1 + lam * (x[0] - eta))
    #     else:
    #         return deriv(lam, x[:-1], eta) + (x[-1] - eta) / (1 + lam * (x[-1] - eta))

    #simple derivative
    def deriv(lam, x, eta):
        return np.sum((x - eta) / (1 + lam * (x - eta)))

    def kelly_optimal(x, eta, **kwargs):
        '''
        finds a kelly optimal bet by numerically optimizing for x; i
        if x is a lagged sample, this produces GRAPA as described in Section B.2 of https://arxiv.org/pdf/2010.09686
        if x is the actual population, this produces the Kely-optimal bet
        if pop is specified as a kwarg, it overrides x and uses pop to produce the bet, but the sequence is still len(x) long
        '''
        # cache the sample or the derivatives at the previous step
        # LRU cache function tells python to save function evaluations
        # at step n+1 evaluate the derivatives by calling the function
        # can't be a lambda function though, needs to be defined externally

        # warm start by bracketing based on the last optimum
        # x_0 in kwargs as a warm start (e.g., previous optimum)

        # compute the slope at the endpoints
        pop = kwargs.get("pop", x)
        min_slope = Bets.deriv(0, pop, eta)
        max_slope = Bets.deriv(1/eta, pop, eta)
        # if the return is always growing, set lambda to the maximum allowed
        if (min_slope > 0) & (max_slope > 0):
            out = 1/eta
        # if the return is always shrinking, set lambda to 0
        elif (min_slope < 0) & (max_slope < 0):
            out = 0
        # otherwise, optimize on the interval [0, 1/eta]
        else:
            lam_star = sp.optimize.root_scalar(lambda lam: Bets.deriv(lam, pop, eta), bracket = [0, 1/eta], method = 'bisect')
            out = lam_star['root']
        return out * np.ones(len(x))

    def grapa(x, eta, **kwargs):
        '''
        returns the (computationally expensive) GRAPA bet of https://arxiv.org/pdf/2010.09686
        by finding the kelly-optimal bet using only the sample history (to make the bet predictable)
        NB: this is not very efficient currently
        -------------------
        kwargs:
            c: float, a threshold on the bet
                useful to prevent "betting the farm" when the population contains 0s, defaults to 0.99
            past: len(x)-1 length np.array, the history of bets to time t
                if included, only the most recent bet is computed
        '''
        c = kwargs.get("c", 0.99)
        past = kwargs.get("past", None)
        if past:
            lam = past
            lam.append(Bets.kelly_optimal(x[:-1], eta))
        else:
            lam = np.zeros(len(x))
            # the first bet is 0, the subsequent ones are optimal for the lagged sample
            for i in range(1,len(x)):
                lam[i] = Bets.kelly_optimal(x[0:i-1], eta)
        return lam



    def convex_combination(x, eta, bet_list, bet_weights = None, **kwargs):
        '''
        WIP, NOT FUNCTIONING
        computes a new bet as a convex combination of other bets
        NOTE: could allow the weights to vary over time as well; e.g. proper dims are (len(bet_list), len(x))
        --------
        inputs:
            bet_list: list of callables with args (x, eta) from class Bets
                the bets to be combined, use lambda functions to set additional kwargs
            bet_weights: length len(bet_list) np.array of floats in [0,1], summing to 1
        '''
        if bet_weights is None:
            warnings.warn("bet_weights is none, setting to unweighted average.")
            bet_weights = np.ones(n_bets) * (1/n_bets) # use flat average as default
        elif np.any(bet_weights < 0):
            raise ValueError("some bet_weights are negative")
        elif np.sum(bet_weights) != 1:
            warnings.warn("bet_weights do not sum to 1, normalizing.")
            bet_weights = np.array(bet_weights)
            bet_weights = bet_weights / np.sum(bet_weights)
        else:
            bet_weights = np.array(bet_weights)
        n_bets = len(bet_list)
        lams = np.array((n_bets, len(x)))
        for i in range(n_bets):
            lams[i,:] = bet_list[i](x, eta)
        lam = np.dot(bet_weights, lams)
        return lam


class Allocations:
    '''
    fixed, predictable, and/or eta-adaptive stratum allocation rules, given bets
    Parameters
    ----------
        x: length-K list of length-n_k np.arrays with elements in [0,1]
            the data sampled from each stratum
        n: length-K list or np.array of ints
            the total sample size in each stratum, i.e. the length of each x
        N: length-K list or np.array of ints
            the (population) size of each stratum
        eta: length-K np.array in [0,1]^K
            the vector of null means across strata
        lam: a length-K np.arry each of len(x[k])

    Returns
    ----------
        allocation: a length sum(n_k) sequence of interleaved stratum selections in each round
    '''
    #is there a way to bifurcate allocations and betting into eta-oblivious/aware using class structure
    def round_robin(x, running_T_k, n, N, eta, lam, **kwargs):
        #eta-nonadaptive round robin strategy
        exhausted = np.ones(len(n))
        exhausted[running_T_k == n] = np.inf
        next = np.argmin(exhausted * running_T_k)
        return next

    def proportional_round_robin(x, running_T_k, n, N, eta, lam, **kwargs):
        #eta-nonadaptive round robin strategy, proportional to total sample size
        exhausted = np.ones(len(n))
        exhausted[running_T_k == n] = np.inf
        next = np.argmin(exhausted * running_T_k / n)
        return next

    def more_to_larger_means(x, running_T_k, n, N, eta, lam, **kwargs):
        #eta-nonadaptive
        #samples more from strata with larger values of x on average
        #does round robin until every stratum has been sampled once
        if any(running_T_k == 0):
            next = Allocations.round_robin(x, running_T_k, n, N, eta, lam)
        else:
            K = len(x)
            eps = kwargs.get("eps", 0.01)
            sd_min = kwargs.get("sd_min", 0.05)
            #UCB-like algorithm targeting the largest stratum mean
            past_x = [x[k][0:running_T_k[k]] for k in range(K)]
            means = np.array([np.mean(px) for px in past_x])
            std_errors = np.array([np.maximum(np.std(px), sd_min) for px in past_x]) / np.sqrt(running_T_k)
            ucbs = means + 2 * std_errors
            scores = np.where(running_T_k == n, -np.inf, ucbs)
            next = np.argmax(scores)
        return next

    def neyman(x, running_T_k, n, N, eta, lam, **kwargs):
        #eta-adaptive
        #uses a predictable Neyman allocation to set allocation probabilities
        #see Neyman (1934)
        if any(running_T_k <= 2):
            #use round robin until we have at least 2 samples from each stratum
            next = Allocations.round_robin(x, running_T_k, n, N, eta, lam)
        else:
            K = len(x)
            eps = kwargs.get("eps", 0.01) #lower bound on sd
            sds = np.array([np.std(x[k][0:running_T_k[k]]) for k in range(K)]) + eps
            sds = np.where(running_T_k == n, 0, sds)
            neyman_weights = N * sds
            probs = neyman_weights / np.sum(neyman_weights)
            next = np.random.choice(np.arange(K), size = 1, p = probs)
        return next

    def proportional_to_mart(x, running_T_k, n, N, eta, lam, **kwargs):
        #eta-adaptive strategy, based on size of martingale for given intersection null
        #this function involves alot of overhead, may want to restructure
        if any(running_T_k <= 1):
            next = Allocations.round_robin(x, running_T_k, n, N, eta, lam)
        K = len(x)
        marts = np.array([mart(x[k], eta[k], None, lam[k], N[k], log = False)[running_T_k[k]] for k in range(K)])
        scores = np.minimum(np.maximum(marts, 1), 1e3)
        scores = np.where(running_T_k == n, 0, scores) #if the stratum is exhausted, its score is 0
        probs = scores / np.sum(scores)
        next = np.random.choice(np.arange(K), size = 1, p = probs)
        return next

    def predictable_kelly(x, running_T_k, n, N, eta, lam, terms, **kwargs):
        '''
        for this allocation function and greedy kelly, need to pass in an array of the past log-growths (terms)
        terms is a list of the log-growths in each stratum
        '''
        #this estimates the expected log-growth of each martingale
        #and then draws with probability proportional to this growth
        #currently, can't use randomized betting rules (would need to pass in terms directly)
        if any(running_T_k <= 2):
            next = Allocations.round_robin(x, running_T_k, n, N, eta, lam)
        else:
            K = len(x)
            eps = kwargs.get("eps", 0.01)
            sd_min = kwargs.get("sd_min", 0.05)
            #return past terms for each stratum on log scale
            #compute martingale as if sampling were with replacement (N = np.inf)
            past_terms = [terms[k][0:running_T_k[k]] for k in range(K)]

            #use a UCB-like approach to select next stratum
            est_log_growth = np.array([np.mean(pt) for pt in past_terms])
            se_log_growth = np.array([np.maximum(np.std(pt), sd_min) for pt in past_terms]) / np.sqrt(running_T_k)
            ucbs_log_growth = est_log_growth + 2 * se_log_growth
            scores = np.where(running_T_k == n, -np.inf, ucbs_log_growth)
            next = np.argmax(scores)
        return next
    #essentially just a renaming of predictable_kelly, but is handled differently
    def greedy_kelly(x, running_T_k, n, N, eta, lam, terms, **kwargs):
        '''
        terms is a list of the log-growths in each stratum
        '''
        if any(running_T_k <= 2):
            next = Allocations.round_robin(x, running_T_k, n, N, eta, lam)
        else:
            K = len(x)
            eps = kwargs.get("eps", 0.01)
            sd_min = kwargs.get("sd_min", 0.05)
            #return past terms for each stratum on log scale
            #compute martingale as if sampling were with replacement (N = np.inf)
            past_terms = [terms[k][0:running_T_k[k]] for k in range(K)]

            #use a UCB-like approach to select next stratum
            est_log_growth = np.array([np.mean(t) for t in past_terms])
            se_log_growth = np.array([np.maximum(np.std(pt), sd_min) for pt in past_terms]) / np.sqrt(running_T_k)
            ucbs_log_growth = est_log_growth + 2 * se_log_growth
            scores = np.where(running_T_k == n, -np.inf, ucbs_log_growth)
            next = np.argmax(scores)
        return next

#record which allocation rules are nonadaptive, useful in some functions/assertions below
nonadaptive_allocations = [Allocations.round_robin, Allocations.proportional_round_robin, Allocations.neyman, Allocations.more_to_larger_means, Allocations.greedy_kelly]
#allocation functions supported for LCB method
lcb_allocations = [Allocations.round_robin, Allocations.proportional_round_robin, Allocations.neyman, Allocations.more_to_larger_means]

class Weights:
    '''
    Methods to set weights for combining across strata by summing
    Generally will be less powerful than taking products
    (see Vovk and Wang 2020 on E-value combining)

    Parameters
    ----------
        x: length-K list of length-n_k np.arrays with elements in [0,1]
            the data sampled from each stratum
        eta: length-K np.array in [0,1]^K
            the vector of null means across strata
        func: callable, a function from the Bets class

    Returns
    ----------
        theta: a length-K list of convex weights
            the weights for combining the martingales as a sum I-TSM E-value at time t

    '''
    def fixed(eta):
        '''
        balanced, fixed weights (usual average)
        '''
        theta = np.ones(len(eta))/len(eta)
        return theta



def mart(x, eta, lam_func = None, lam = None, N = np.inf, log = True, output = "mart"):
    '''
    betting martingale

    Parameters
    ----------
        x: length-n_k np.array with elements in [0,1]
            data
        eta: scalar in [0,1]
            null mean
        lam_func: callable, a function from the Bets class, or a list of callables
            if callable, the TSM is computed for that betting function
            if list, a TSM is computed for every betting function in the list and those TSMs are averaged into a new TSM
        lam: length-n_k np.array or list of length-n_k np.arrays to be mixed over
            pre-set bets
        N: positive integer,
            the size of the population from which x is drawn (x could be the entire population)
        log: Boolean
            indicates whether the martingale or terms should be returned on the log scale or not
        output: str
            indicates what to return
            "mart": return the martingale sequence (found by multiplication or summing if log)
            "bets": returns just the bets for the martingale
    Returns
    ----------
        mart: scalar; the value of the (log) betting martingale at time n_k

    '''
    assert bool(lam is None) != bool(lam_func is None), "must specify exactly one of lam_func or lam"
    if N < np.inf:
        S = np.insert(np.cumsum(x), 0, 0)[0:-1]
        j = np.arange(1,len(x)+1)
        eta_t = (N*eta-S)/(N-j+1)
    elif N == np.inf:
        eta_t = eta * np.ones(len(x))
    else:
        raise ValueError("Input an integer value for N, possibly np.inf")
    if callable(lam_func):
        lam_func = [lam_func]
    #note: per Waudby-Smith and Ramdas, the null mean for the bets does not update when sampling WOR
    #note: eta < 0 or eta > 1 can create runtime warnings in log, but are replaced appropriately by inf
    if lam_func is not None:
        lam = [lf(x, eta) for lf in lam_func]
    elif type(lam) is np.ndarray:
        lam = [lam]

    if output == "mart":
        mart_array = np.zeros((len(x)+1, len(lam)))
        for l in range(len(lam)):
            mart = np.insert(np.cumprod(1 + lam[l] * (x - eta_t)), 0, 1)
            mart[np.insert(eta_t < 0, 0, False)] = np.inf
            mart[np.insert(eta_t > 1, 0, False)] = 0
            mart_array[:,l] = mart
        h_mart = np.mean(mart_array, 1) # this is the "hedged martingale", the flat average over the bets
        h_mart = np.log(h_mart) if log else h_mart
        out = h_mart
    elif output == "bets":
        out = lam
    else:
        out = "Input a valid argument to return, either 'marts', 'terms', or 'bets'"
    return out


def selector(x, N, allocation_func, eta = None, lam = None, for_samples = False):
    '''
    takes data and predictable tuning parameters and returns a sequence of stratum sample sizes
    equivalent to [S_t for k in 1:K]

    Parameters
    ----------
        x: length-K list of length-n_k np.arrays with elements in [0,1]
            data, may be sample from population or an entire population
            in which case N[k] = len(x[k])
        N: length-K list or np.array of ints
            the population size of each stratum
        eta: length-K np.array with elements in [0,1]
            the intersection null for H_0: mu <= eta, can be None
        lam: length-K list of np.arrays each of len(x[k])
            the bets to use for each sample at each time
        allocation_func: callable, a function from Allocations class
        for_samples: boolean
            this is only True when used in negexp_ui_mart,
            which needs an interleaving of _samples_ not martingales,
            and needs a slightly different indexing (one shorter)
    Returns
    ----------
        an np.array of length np.sum(N) by
    '''
    if lam is None:
        assert allocation_func in lcb_allocations, "bets not supplied (lam is None) but required"
    w = N/np.sum(N)
    K = len(N)
    if for_samples:
        n = [len(x_k)-1 for x_k in x]
    else:
        n = [len(x_k) for x_k in x]
    # special handling for predictable kelly and greedy kelly
    if allocation_func in [Allocations.greedy_kelly, Allocations.predictable_kelly]:
        terms = []
        for k in range(K):
            #NB: terms are always computed as-if sampling without replacement
            m = mart(x[k], eta[k], lam_func = None, lam = lam[k], N = np.inf, log = True)
            log_growth = np.diff(m)
            terms.append(log_growth)
    else:
        terms = None
    #selections from 0 in each stratum; time 1 is first sample
    T_k = np.zeros((np.sum(n) + 1, K), dtype = int)
    running_T_k = np.zeros(K, dtype = int)
    t = 0
    while np.any(running_T_k < n):
        t += 1
        next_k = allocation_func(
            x = x,
            running_T_k = running_T_k,
            n = n,
            N = N,
            eta = eta,
            lam = lam,
            terms = terms)
        running_T_k[next_k] += 1
        T_k[t,:] = running_T_k
    return T_k

def lower_confidence_bound(x, lam_func, alpha, N = np.inf, breaks = 1000):
    '''
    return a lower confidence bound for a betting martingale\
    given a function to sets bets (lam_func) and a level (1-alpha)

    Parameters
    ----------
        x: length-n_k np.array with elements in [0,1]
            data
        lam_func: callable, a function from the Bets class
        alpha: double in (0,1]
        N: integer
            the size of the stratum when sampling WOR or np.inf when sampling WR
        breaks: int > 0
            the number of equally-spaced breaks in [0,1] on which to compute P-value
            the more there are, the less conservative the bound is (resolution is 1/breaks)
    Returns
    ----------
        level (1-alpha) lower confidence bound on the mean
    '''
    grid = np.arange(0, 1 + 1/breaks, step = 1/breaks)
    confset = np.zeros((len(grid), len(x) + 1))
    for i in np.arange(len(grid)):
        m = grid[i]
        confset[i,:] = mart(x, eta = m, lam_func = lam_func, lam = None, N = N, log = True) < np.log(1/alpha)
    lb = np.zeros(len(x)+1)
    for j in np.arange(len(x)+1):
        lb[j] = grid[np.argmax(confset[:,j])]
    return lb

def global_lower_bound(x, N, lam_func, allocation_func, alpha, WOR = False, breaks = 1000):
    '''
    return a level (1-alpha) lower confidence bound on a global mean\
    computed by using Wright's method, summing lower confidence bounds\
    across strata

    Parameters
    ----------
        x: length-K list of length-n_k np.arrays with elements in [0,1]
            data from each stratum
        N: length-K list or length-K np.arrays
            the size of each stratum
        lam_func: callable (TODO: allow to differ across strata)
            function from the Bets class
        allocation_func: callable
            function from Allocations class
        alpha: double in (0,1]
            1 minus the confidence level for the lower bound
        WOR: Boolean
            compute the confidence interval for sampling with or without replacement?
        breaks: int > 0
            the number of equally-spaced breaks in [0,1] on which to get each separate confidence bound
    Returns
    ----------
        level (1-alpha) lower confidence bound on the global mean of a stratified population
    '''
    assert allocation_func in lcb_allocations, "allocation_func is not supported for LCB method"
    w = N/np.sum(N) #stratum weights
    K = len(N)
    lcbs = []
    if callable(lam_func):
        lam_func = [lam_func] * K
    for k in np.arange(K):
        N_k = N[k] if WOR else np.inf
        lcbs.append(lower_confidence_bound(
            x = x[k],
            lam_func = lam_func[k],
            alpha = 1-(1-alpha)**(1/K), # sidak correction to each bound
            N = N_k,
            breaks = breaks))
    T_k = selector(x, N, allocation_func, eta = None, lam = None)
    running_lcbs = np.zeros((T_k.shape[0], K))
    for i in np.arange(T_k.shape[0]):
        running_lcbs[i,:] = np.array([lcbs[k][T_k[i, k]] for k in np.arange(K)])
    global_lcb = np.matmul(running_lcbs, w)
    return global_lcb


def intersection_mart(x, N, eta, lam_func = None, lam = None, mixing_dist = None, allocation_func = None, T_k = None, combine = "product", theta_func = None, log = True, WOR = False, return_selections = False, last = False, running_max = True):
    '''
    an intersection test supermartingale (I-TSM) for a vector intersection null eta
    assumes sampling is with replacement: no population size is required

    Parameters
    ----------
        x: length-K list of length-n_k np.arrays with elements in [0,1]
            the data sampled from each stratum
        N: length-K list of ints
            population size for each stratum
        eta: length-K np.array or list in [0,1]
            the vector of null means
        lam_func: callable, a function from class Bets; a list of functions, one for each stratum; or a list of lists, containing a mixture of bets for each stratum
            a betting strategy, a function that sets bets possibly given past data and eta
        lam: length-K list of length-n_k np.arrays corresponding to bets, or list of lists of np.arrays to be mixed over
            bets for each stratum, real numbers between 0 and 1/eta_k
        mixing_dist: a B by K np.array in [0,1], or nothing
            only for mixing over the intersection martingales
            lambdas to mix over, B is just any positive integer (the size of the mixing distribution)
        allocation_func: callable, a function from class Allocations
            the allocation function to be used
        T_k: (sum(n) + 1) x K np.array of ints
            the selections for each stratum (columns) at each time (rows), alternative to allocation_funct
        combine: string, in ["product", "sum", "fisher"]
            how to combine within-stratum martingales to test the intersection null
        theta_func: callable, a function from class Weights
            only relevant if combine == "sum", the weights to use when combining with weighted sum
        log: boolean
            return the log I-TSM if true, otherwise return on original scale
        WOR: boolean
            should martingales be computed under sampling with or without replacement?
        return_selections: boolean
            return matrix of stratum sample sizes (T_k) if True, otherwise just return combined martingale
        last: Boolean
            return only the last index of the martingale; helps speed things up when T_k is given
        running_max: boolean
            take the running maximum of the I-TSM
    Returns
    ----------
        the value of an intersection martingale that uses all the data (not running max)
    '''
    K = len(eta)
    assert lam_func is None or mixing_dist is None, "Must specify (exactly one of) mixing distribution or predictable lambda function"
    assert bool(allocation_func is None) != bool(T_k is None), "Must specify (exactly one of) selector (allocation_func) or selections (T_k)"
    assert (last and T_k is not None) or not last, "last only works when T_k is given"
    # check that all types and arguments are aligned
    assert ((combine == "product") and (mixing_dist is not None)) or (lam_func is not None and (callable(lam_func) or (len(lam_func) == K))) or (lam is not None and ((type(lam) is np.ndarray) or (len(lam) == K))), "lam or lam_func needs to be a single object or a list of length K (one for each stratum); mixing_dist only works with product combining"
    if mixing_dist is not None:
        assert allocation_func in nonadaptive_allocations, "for now, mixing only works with nonadaptive allocation"
    ws_log = False if combine == "sum" else log
    ws_N = N if WOR else np.inf*np.ones(K)

    # expand bets into lists over strata by copying
    if callable(lam_func):
        lam_func = [lam_func] * K

    if mixing_dist is None:
        if lam is None:
            lam = [mart(x[k], eta[k], lam_func[k], None, ws_N[k], ws_log, output = "bets") for k in np.arange(K)]
        #within-stratum martingales
        ws_marts = [mart(x[k], eta[k], None, lam[k], ws_N[k], ws_log) for k in np.arange(K)]
        #construct the interleaving
        if T_k is None:
            T_k = selector(x, N, allocation_func, eta, lam)
        if last:
            marts = np.array([[ws_marts[k][T_k[-1, k]] for k in np.arange(K)]])
            if np.any(np.isposinf(marts)):
                #it's not exactly clear how to handle certainties when sampling without replacement
                #i.e. what if the null is certainly false in one stratum but certainly true in another...
                marts = np.inf * np.ones((1,K))
        else:
            marts = np.zeros((T_k.shape[0], K))
            for i in np.arange(T_k.shape[0]):
                marts_i = np.array([ws_marts[k][T_k[i, k]] for k in np.arange(K)])
                #make all marts infinite if one is, when product is taken this enforces rule:
                #we reject intersection null if certainly False in one stratum
                #TODO: rethink this logic? What if the null is certainly true in a stratum?
                #there is some more subtlety to be considered when sampling WOR
                marts[i,:] = marts_i if not any(np.isposinf(marts_i)) else np.inf * np.ones(K)
    else:
        B = mixing_dist.shape[0]
        if T_k is None:
            T_k = selector(x, N, allocation_func, eta, lam = None)
        marts = np.zeros((B, T_k.shape[0], K))
        for b in range(B):
            lam = [Bets.fixed(x[k], eta[k], c = mixing_dist[b,k]) for k in np.arange(K)]
            ws_marts = [mart(x[k], eta[k], None, lam[k], ws_N[k], log = False) for k in np.arange(K)]
            for i in np.arange(T_k.shape[0]):
                marts_bi = np.array([ws_marts[k][T_k[i, k]] for k in np.arange(K)])
                marts[b,i,:] = marts_bi if not any(np.isposinf(marts_bi)) else np.inf * np.ones(K)
    if combine == "product":
        if mixing_dist is None:
            int_mart = np.sum(marts, 1) if log else np.prod(marts, 1)
        else:
            #this takes the product across strata and the mean across the mixing distribution
            int_mart = np.mean(np.prod(marts, 2), 0)
            int_mart = np.log(int_mart) if log else int_mart
        if running_max:
            int_mart = np.maximum.accumulate(int_mart)
    elif combine == "sum":
        assert theta_func is not None, "Need to specify a theta function from Weights if using sum"
        thetas = theta_func(eta)
        int_mart = np.log(np.sum(thetas * marts, 1)) if log else np.sum(thetas * marts, 1)
        if running_max:
            int_mart = np.maximum.accumulate(int_mart)
    elif combine == "fisher":
        pvals = np.exp(-np.maximum(0, marts)) if log else 1 / np.maximum(1, marts)
        fisher_stat = -2 * np.sum(np.log(pvals), 1)
        pval = 1 - chi2.cdf(fisher_stat, df = 2*K)
        pval = np.log(pval) if log else pval
    else:
        raise NotImplementedError("combine must be product, sum, or fisher")
    result = int_mart if combine != 'fisher' else pval
    if return_selections:
        return result, T_k
    else:
        return result


def plot_marts_eta(x, N, lam_func = None, mixture = None, allocation_func = Allocations.proportional_round_robin, combine = "product", theta_func = None, log = True, res = 1e-2, range = [0,1], running_max = False):
    '''
    generate a 2-D or 3-D plot of an intersection martingale over possible values of bs{eta}
    the global null is always eta <= 1/2; future update: general global nulls

    Parameters
    ----------
        x: length-K list of length-n_k np.arrays with elements in [0,1]
            the data sampled from each stratum
        N: length-K np.array of positive ints,
            the vector of stratum sizes
        lam_func: callable, a function from class Bets
        mixture: either "vertex" or "uniform"; cannot specify if lam_func is not None
        combine: string, either "product" or "sum"
            how to combine within-stratum martingales to test the intersection null
        allocation_func: callable, a function from class Allocations
        theta_func: callable, a function from class Weights
            only relevant if combine == "sum", the weights to use when combining with weighted sum
        log: Boolean
            return the log I-TSM if true, otherwise return on original scale
        res: float > 0,
            the resolution of equally-spaced grid to compute and plot the I-TSM over
        running_max: boolean
            take the running max of each intersection mart? defaults to False
    Returns
    ----------
        generates and shows a plot of the last value of an I-TSM over different values of the null mean
    '''
    K = len(x)
    if mixture == "vertex":
        vertices = construct_vertex_etas(1/2, N)
        mixing_dist = 1 - np.array(vertices)
    elif mixture == "uniform":
        lam_grids = K * [np.linspace(0.01,0.99,5)]
        mixing_dist = np.array(list(itertools.product(*lam_grids)))
    elif mixture is None:
        mixing_dist = None
    else:
        stop("Specify a valid mixture method; either uniform, vertex, or None")
    eta_grid = np.arange(range[0] + res, range[1]-res, step=res)
    eta_xs, eta_ys, eta_zs, objs = [], [], [], []
    w = N / np.sum(N)
    if K == 2:
        for eta_x in eta_grid:
            eta_y = (1/2 - w[0] * eta_x) / w[1]
            if eta_y > 1 or eta_x < 0: continue
            obj = intersection_mart(x = x, N = N, eta = np.array([eta_x,eta_y]), lam_func = lam_func,
             lam = None, mixing_dist = mixing_dist, allocation_func = allocation_func,
             combine = combine, theta_func = theta_func, log = log, running_max = running_max)[-1]
            eta_xs.append(eta_x)
            eta_ys.append(eta_y)
            objs.append(obj)
        min_ix = np.argmin(objs)
        min_eta = np.round([eta_xs[min_ix], eta_ys[min_ix]], 2)
        plt.plot(eta_xs, objs, linewidth = 1)
        plt.show()
        print("minimum eta = " + str(min_eta))
        print("minimum = " + str(objs[min_ix]))
    elif K == 3:
        fig = plt.figure()
        ax = fig.add_subplot(projection='3d')
        for eta_x in eta_grid:
            for eta_y in eta_grid:
                eta_z = (1/2 - w[0]*eta_x-w[1]*eta_y)/w[2]
                if eta_z > 1 or eta_z < 0: continue
                obj = intersection_mart(x = x, N = N, eta = np.array([eta_x,eta_y,eta_z]), lam_func = lam_func,
                 lam = None, mixing_dist = mixing_dist, allocation_func = allocation_func,
                 combine = combine, theta_func = theta_func, log = log, running_max = running_max)[-1]
                eta_xs.append(eta_x)
                eta_ys.append(eta_y)
                eta_zs.append(eta_z)
                objs.append(obj)
        ax.scatter(eta_xs, eta_ys, objs, c = objs)
        min_ix = np.argmin(objs)
        min_eta = np.round([eta_xs[min_ix], eta_ys[min_ix], eta_zs[min_ix]], 2)
        ax.view_init(20, 120)
        plt.show()
        print("minimum eta = " + str(min_eta))
        print("minimum = " + str(objs[min_ix]))
    else:
        raise NotImplementedError("Can only plot I-TSM over eta for 2 or 3 strata.")


def construct_exhaustive_eta_grid(eta_0, calX, N):
    '''
    construct a grid of null means for a stratified population\
    representing the null parameter space under a particular global null eta_0.
    Used to compute a UI-TS using a brute force strategy\
    that evaluates an I-TSM at every feasible element of a (discrete) null parameter space.

    Parameters
    ----------
        eta_0: scalar in [0,1]
            the global null
        calX: np.array or length-K list of np.arrays
            the possible population values, overall or within each stratumwise
        N: length-K list of ints
            the size of the population within each stratum
    Returns
    ----------
        a grid of within stratum null means, to be passed into brute_force_uits
    '''
    K = len(N)
    w = N / np.sum(N)
    u_k = np.array([np.max(x) for x in calX])
    l_k = np.array([np.min(x[np.nonzero(x)]) for x in calX])
    #upper bound on how many null means there are
    ub_calC = 1
    for k in np.arange(K):
        ub_calC *= sp.special.comb(N[k] + len(calX[k]) - 1, len(calX[k]) - 1)
    #build stratum-wise means
    means = [[] for _ in range(K)]
    for k in np.arange(K):
        for lst in itertools.combinations_with_replacement(calX[k], r = N[k]):
            if np.mean(lst) not in means[k]:
                means[k].append(np.mean(lst))
    etas_temp = []
    etas = []
    #should there be a factor of K in here
    eps_k = w*(l_k / np.array(N))
    eps = np.max(eps_k)
    for crt_prd in itertools.product(*means):
        #this condition checks if the candidate intersection null is near the boundary
        if eta_0 - eps <= np.dot(w, crt_prd) <= eta_0:
            etas.append(crt_prd)
    #for e_1, e_2 in itertools.combinations(etas_temp):
        #this condition ensures the candidate is not everywhere less than another member of eta
    #    if not any([all(np.array(candidate_eta) <= np.array(e)) for e in etas_temp]):
    #        etas.append(candidate_eta)
    calC = len(etas)
    return etas, calC, ub_calC


def construct_eta_grid_plurcomp(N, A_c, assorter_method):
    '''
    construct all the intersection nulls possible in a comparison audit of a plurality contest

    Parameters
    ----------
        N: a length-K list of ints
            the size of each stratum
        A_c: a length-K np.array of floats
            the reported assorter mean bar{A}_c in each stratum
        assorter_method: str, either "sts" or "global"
            method for constructing the audit, effects values of means produced within strata
            "sts": as described in Sweeter than Suite Section 3.2
            "global": canonical means summing to 1/2 (no adjustment for stratum-wise reported assorter means)/
                      the population itself is rescaled to reflect a comparison audit (see simulate_comparison_audit)
    Returns
    ----------
        every eta that is possible in a comparison risk-limiting audit\
        given the input diluted margins and stratum sizes
    '''
    assert assorter_method in ["sts", "global"]
    w = N/np.sum(N)
    #assert np.dot(w, A_c) > 0.5, "global reported margin <= 1/2"
    K = len(N)
    means = []
    for k in np.arange(K):
        means.append(np.arange(0, 1 + 0.5/N[k], step  = 0.5/N[k]))
    etas = []
    #if we can't hit w^T mu = 1/2, what is the largest gap possible?
    #should there be a factor of K
    eps_k = w*(0.5/np.array(N))
    eps = np.max(eps_k)
    for crt_prd in itertools.product(*means):
        if 1/2 - eps <= np.dot(w, crt_prd) <= 1/2:
            if assorter_method == "sts":
                #null means as defined in Sweeter than SUITE https://arxiv.org/pdf/2207.03379.pdf
                #but divided by two, to map population from [0,2] to [0,1]
                etas.append(tuple((np.array(crt_prd) + 1 - A_c) / 2))
            else:
                etas.append(crt_prd)
    calC = len(etas)
    return etas, calC




def construct_vertex_etas(eta_0, N):
    '''
    find all the null means that make up vertices of calC.
    Can be used to (relatively) efficiently compute the union-intersection martingale
    IF the betting and allocation strategies are eta-nonadaptive


    Parameters
    ----------
        eta_0: scalar in [0,1]
            the global null
        N: length-K list of ints
            the size of the population within each stratum
    Returns
    ----------
        a list of intersection nulls, to be passed into brute_force_uits
    '''
    assert len(N) < 16, "Too many strata to compute vertices."
    w = N / np.sum(N)
    K = len(N)
    #define constraint set for pypoman projection
    A = np.concatenate((
        np.expand_dims(w, axis = 0),
        np.expand_dims(-w, axis = 0),
        -np.identity(K),
        np.identity(K)))
    b = np.concatenate((eta_0 * np.ones(2), np.zeros(K), np.ones(K)))
    vertices = np.stack(pypoman.compute_polytope_vertices(A, b), axis = 0)
    etas = vertices[np.matmul(vertices, w) == eta_0,]
    etas = list(map(tuple, etas))
    return etas


def construct_eta_bands(eta_0, N, n_bands = 100):
    '''
    partition the null space into a set of n_bands equi-spaced bands, represented by their vertices and a centroid
    allows the null eta_0 to be tested by the banding method
    Parameters
    ----------
        eta_0: scalar in [0,1]
            the global null
        N: length-2 list of ints
            the size of the population within each stratum
        n_bands: positive int
            the number of equal-width bands in the tesselation of the null boundary
    Returns
    ----------
        a list of tuples of length points,
        each tuple represents a band over which the null mean will be tested,
        it contains one eta that is used to construct bets and selections
        and two etas representing the endpoints of the band, which are used to conservatively test the intersection nulls for that band
    '''
    K = len(N)
    assert K == 2, "only works for two strata"
    w = N / np.sum(N)
    eta_1_grid = np.linspace(max(0, eta_0 - w[1]), min(1, eta_0/w[0]), n_bands + 1)
    eta_2_grid = (eta_0 - w[0] * eta_1_grid) / w[1]
    eta_grid = np.transpose(np.vstack((eta_1_grid, eta_2_grid)))
    etas = []
    for i in np.arange(eta_grid.shape[0]-1):
        centroid = (eta_grid[i,:] + eta_grid[i+1,:]) / 2
        etas.append([(eta_grid[i,:], eta_grid[i+1,:]), centroid])
    return etas


def banded_uits(x, N, etas, lam_func, allocation_func = Allocations.proportional_round_robin, log = True, WOR = False, verbose = False):
    '''
    compute a product UI-TS by minimizing product I-TSMs along a grid of etas (the "band" method)

    Parameters
    ----------
        x: length-K list of length-n_k np.arrays with elements in [0,1]
            the data sampled from each stratum
        N: length-K list of ints
            the size of each stratum
        etas: list of 2-tuples, each with etas to test and etas to construct bets / allocations
            intersection nulls over which the minimum will be taken
        lam_func: callable, a function from class Bets OR a list of functions, one for each stratum
            the function for setting the bets (lambda_{ki}) for each stratum / time
        allocation_func: callable, a function from the Allocations class
            function for allocation sample to strata for each eta
        log: Boolean
            return the log UI-TS if true, otherwise return on original scale
        WOR: Boolean
            should the intersection martingales be computed under sampling without replacement
    Returns
    ----------
        the value of a union-intersection martingale using all data x
    '''
    K = len(x)
    w = N / np.sum(N)
    n = [x_k.shape[0] for x_k in x] #get the sample size
    ws_N = N if WOR else np.inf*np.ones(K) # this is only used to set bets for greedy kelly
    if callable(lam_func):
        lam_func = [lam_func] * K

    #record objective value and selections for each band
    obj = np.zeros((len(etas), np.sum(n) + 1))
    sel = np.zeros((len(etas), np.sum(n) + 1, K))
    min_etas = []
    bets = []
    if allocation_func == Allocations.greedy_kelly:
        if not log:
            obj[:,0] = 1
        #selections from 0 in each stratum; time 1 is first sample
        T_k = np.zeros((np.sum(n) + 1, K), dtype = int)
        running_T_k = np.zeros(K, dtype = int)
        t = 0
        eta_star = np.zeros(K) #intialize eta_star (the minimizer, to be tracked)
        eta_star_index = 0 #initialize index of the minimizer
        terms = [] # storage for the TSM terms to be called at each stage of greedy_kelly
        # loop once over etas to set things up
        for i in np.arange(len(etas)):
            # compute bets
            max_eta = np.max(np.vstack(etas[i][0]),0) #record largest eta in the band for each stratum
            centroid_eta = etas[i][1]
            bets_i = [mart(x[k], max_eta[k], lam_func[k], None, ws_N[k], log, output = "bets") for k in np.arange(K)]
            bets.append(bets_i)
            # compute terms
            terms_i = []
            for k in range(K):
                #NB: terms are always computed as-if sampling is with replacement (IID)
                m = mart(x[k], centroid_eta[k], lam = bets_i, N = np.inf, log = True)
                log_growth = np.diff(m)
                terms_i.append(log_growth)
            terms.append(terms_i)
        while np.any(running_T_k < n):
            t += 1
            next_k = Allocations.greedy_kelly(x, running_T_k, n, N, eta_star, bets[eta_star_index], terms = terms[eta_star_index])
            running_T_k[next_k] += 1
            T_k[t,:] = running_T_k
            for i in np.arange(len(etas)):
                itsm_1 = intersection_mart(x = x, N = N, eta = etas[i][0][0], lam = bets[i], T_k = T_k[0:(t+1),:],
                        combine = "product", log = log, WOR = WOR, last = True)
                itsm_2 = intersection_mart(x = x, N = N, eta = etas[i][0][1], lam = bets[i], T_k = T_k[0:(t+1),:],
                        combine = "product", log = log, WOR = WOR, last = True)
                obj[i,t] = min(itsm_1, itsm_2)
            eta_star_index = np.argmin(obj[:,t])
            eta_star = etas[eta_star_index][1] #centroid
        for i in np.arange(len(etas)):
            sel[i,:,:] = T_k
    else:
        #draw the selection sequence one time if nonadaptive
        if allocation_func in nonadaptive_allocations:
            first_centroid = etas[0][1]
            bets_i = [mart(x[k], first_centroid[k], lam_func[k], None, ws_N[k], log, output = "bets") for k in np.arange(K)]
            T_k_i = selector(x, N, allocation_func, first_centroid, bets_i)
        for i in np.arange(len(etas)):
            centroid_eta = etas[i][1]
            max_eta = np.max(np.vstack(etas[i][0]),0) #record largest eta in the band for each stratum
            #bets are determined for max_eta, which makes the bets conservative for both strata and both vertices of the band
            bets_i = [mart(x[k], max_eta[k], lam_func[k], None, ws_N[k], log, output = "bets") for k in np.arange(K)]
            bets.append(bets_i)
            #adaptive selections are determined by the centroid of each band
            if allocation_func not in nonadaptive_allocations:
                T_k_i = selector(x, N, allocation_func, centroid_eta, bets_i)
            itsm_mat = np.zeros((np.sum(n)+1, 2))
            #minima are evaluated at the endpoints of the band//
            #one of which is the minimum over the whole band due to concavity
            for j in np.arange(2):
                itsm_mat[:,j] = intersection_mart(x = x, N = N, eta = etas[i][0][j], lam = bets_i, T_k = T_k_i,
                    combine = "product", log = log, WOR = WOR)
            obj[i,:] = np.min(itsm_mat, 1)
            sel[i,:,:] = T_k_i

    opt_index = np.argmin(obj, 0)
    eta_opt = np.zeros((np.sum(n) + 1, len(x)))
    mart_opt = np.zeros(np.sum(n) + 1)
    global_sample_size = np.sum(np.max(sel, 0), 1)
    for i in np.arange(np.sum(n) + 1):
        eta_opt[i,:] = etas[opt_index[i]][1] #record the center eta of the band that minimizes (not exact minimizer)
        mart_opt[i] = obj[opt_index[i],i]
    if verbose:
        return mart_opt, eta_opt, global_sample_size, obj, sel, bets
    else:
        return mart_opt, eta_opt, global_sample_size


def brute_force_uits(x, N, etas, lam_func = None, allocation_func = Allocations.proportional_round_robin, mixture = None, combine = "product", theta_func = None, log = True, WOR = False, eta_0_mixture = 1/2):
    '''
    compute a UI-TS by minimizing I-TSMs by brute force search over feasible eta, passed as etas

    Parameters
    ----------
        x: length-K list of length-n_k np.arrays with elements in [0,1]
            the data sampled from each stratum
        N: length-K list of ints
            the size of each stratum
        etas: list of length-K tuples
            intersection nulls over which the minimum will be taken
        lam_func: callable, a function from class Bets
            the function for setting the bets (lambda_{ki}) for each stratum / time
        allocation_func: callable, a function from the Allocations class
            function for allocation sample to strata for each eta
        mixture: string or None, either "vertex" or "uniform"
            if present, defines one of two mixing strategies for each I-TSM and builds mixing_dist
                "vertex": mixes over a discrete uniform distribution on lambda = 1 - etas
                "uniform": mixes over a uniform distribution on [0,1]^K, gridded into 10 equally-spaced points
        combine: string, either "product" or "sum"
            how to combine within-stratum martingales to test the intersection null
        theta_func: callable, a function from class Weights
            only relevant if combine == "sum", the weights to use when combining with weighted sum
        log: Boolean
            return the log UI-TS if true, otherwise return on original scale
        WOR: Boolean
            should the intersection martingales be computed under sampling without replacement
        eta_0_mixture: float in [0,1]
            the global null mean, only used to construct the vertices of calC for mixture == "vertex";
            in other cases can be ignored. Defaults to 1/2
    Returns
    ----------
        the value of a union-intersection martingale using all data x
    '''
    assert (lam_func is None) or (mixture is None), "cannot specify both a mixture strategy and predictable lambda function"
    K = len(x)
    w = N / np.sum(N)
    n = [x_k.shape[0] for x_k in x] #get the sample size
    ws_N = N if WOR else np.inf*np.ones(K) # this is only used to set bets for greedy kelly

    if mixture == "vertex":
        vertices = construct_vertex_etas(eta_0_mixture, N)
        mixing_dist = 1 - np.array(vertices)
    elif mixture == "uniform":
        lam_grids = K * [np.linspace(0.01,0.99,10)]
        mixing_dist = np.array(list(itertools.product(*lam_grids)))
    elif mixture is None:
        mixing_dist = None
    else:
        stop("Specify a valid mixture method; either uniform or vertex")

    if callable(lam_func):
        lam_func = [lam_func] * K

    #evaluate intersection mart on every eta
    obj = np.zeros((len(etas), np.sum(n) + 1))
    sel = np.zeros((len(etas), np.sum(n) + 1, K))
    #different method for greedy_kelly, since it needs to sequentially update the eta
    #is there a way to make this cleaner and faster?
    if allocation_func == Allocations.greedy_kelly:
        assert mixture is None, "cannot use greedy_kelly with a mixing distribution"
        #selections from 0 in each stratum; time 1 is first sample
        T_k = np.zeros((np.sum(n) + 1, K), dtype = int)
        running_T_k = np.zeros(K, dtype = int)
        t = 0
        eta_star = np.zeros(K) #intialize eta_star (the minimizer, to be tracked)
        eta_star_index = 0 #initialize index of the minimizer
        #set all bets
        bets = []
        terms = []
        for e in etas:
            bets_i = [mart(x[k], e[k], lam_func[k], None, ws_N[k], log, output = "bets") for k in np.arange(K)]
            bets.append(bets_i)
            terms_i = []
            for k in range(K):
                #NB: terms for greedy-kelly are always computed as-if sampling without replacement
                m = mart(x[k], e[k], lam = bets_i[k], N = np.inf, log = True)
                log_growth = np.diff(m)
                terms_i.append(log_growth)
            terms.append(terms_i)
        while np.any(running_T_k < n):
            t += 1
            next_k = Allocations.greedy_kelly(x, running_T_k, n, N, eta_star, bets[eta_star_index], terms = terms[eta_star_index])
            running_T_k[next_k] += 1
            T_k[t,:] = running_T_k
            for i in np.arange(len(etas)):
                obj[i,t] = intersection_mart(x = x, N = N, eta = etas[i], T_k = T_k[0:(t+1),:],
                    lam_func = None, lam = bets[i], combine = combine,
                    theta_func = theta_func, log = log, WOR = WOR, last = True)
            eta_star_index = np.argmin(obj[:,t]) if combine != "fisher" else np.argmax(obj[:,t], 0)
            eta_star = etas[eta_star_index]
        for i in np.arange(len(etas)):
            sel[i,:,:] = T_k #this is just to count sample size and redefine T_k below
    else:
        for i in np.arange(len(etas)):
                obj[i,:], sel[i,:,:] = intersection_mart(
                    x = x,
                    N = N,
                    eta = etas[i],
                    lam_func = lam_func,
                    mixing_dist = mixing_dist,
                    allocation_func = allocation_func,
                    combine = combine,
                    theta_func = theta_func,
                    log = log,
                    WOR = WOR,
                    return_selections = True)
        #return the global selections if nonadaptive; otherwise return None
    if allocation_func in nonadaptive_allocations:
        T_k = sel[0,:,:]
    else:
        T_k = None
    opt_index = np.argmin(obj, 0) if combine != "fisher" else np.argmax(obj, 0)
    eta_opt = np.zeros((np.sum(n) + 1, len(x)))
    mart_opt = np.zeros(np.sum(n) + 1)
    global_sample_size = np.sum(np.max(sel, 0), 1)
    for i in np.arange(np.sum(n) + 1):
        eta_opt[i,:] = etas[opt_index[i]]
        mart_opt[i] = obj[opt_index[i],i]
    return mart_opt, eta_opt, global_sample_size, T_k

####### stratified comparison audit functions #######

def construct_eta_grid_plurcomp(N, A_c, assorter_method):
    '''
    construct all the intersection nulls possible in a comparison audit of a plurality contest

    Parameters
    ----------
        N: a length-K list of ints
            the size of each stratum
        A_c: a length-K np.array of floats
            the reported assorter mean bar{A}_c in each stratum
        assorter_method: str, either "sts" or "global"
            method for constructing the audit, effects values of means produced within strata
            "sts": as described in Sweeter than Suite Section 3.2
            "global": canonical means summing to 1/2 (no adjustment for stratum-wise reported assorter means)/
                      the population itself is rescaled to reflect a comparison audit (see simulate_comparison_audit)
    Returns
    ----------
        every eta that is possible in a comparison risk-limiting audit\
        given the input diluted margins and stratum sizes
    '''
    assert assorter_method in ["sts", "global"]
    w = N/np.sum(N)
    #assert np.dot(w, A_c) > 0.5, "global reported margin <= 1/2"
    K = len(N)
    means = []
    for k in np.arange(K):
        means.append(np.arange(0, 1 + 0.5/N[k], step  = 0.5/N[k]))
    etas = []
    #if we can't hit w^T mu = 1/2, what is the largest gap possible?
    #should there be a factor of K
    eps_k = w*(0.5/np.array(N))
    eps = np.max(eps_k)
    for crt_prd in itertools.product(*means):
        if 1/2 - eps <= np.dot(w, crt_prd) <= 1/2:
            if assorter_method == "sts":
                #null means as defined in Sweeter than SUITE https://arxiv.org/pdf/2207.03379.pdf
                #but divided by two, to map population from [0,2] to [0,1]
                etas.append(tuple((np.array(crt_prd) + 1 - A_c) / 2))
            else:
                etas.append(crt_prd)
    calC = len(etas)
    return etas, calC

def construct_eta_bands_plurcomp(A_c, N, n_bands = 100):
    '''
    construct a set of bands to run a card-level comparison audit of a plurality contest
    using the parameterization from Sweeter than SUITE (STS): https://arxiv.org/pdf/2207.03379


    Parameters
    ----------
        A_c: length-2 list of floats in [0,1]
            the reported assorter mean in each stratum
        N: length-2 list of ints
            the size of the population within each stratum
        n_bands: positive int
            the number of equal-width bands in the tesselation of the null boundary
    Returns
    ----------
        a list of tuples of length points,
        each tuple represents a band over which the null mean will be tested,
        it contains one eta that is used to construct bets and selections
        and two etas representing the endpoints of the band, which are used to conservatively test the intersection nulls for that band
    '''
    assert (np.max(A_c) <= 1) and (np.min(A_c) >= 0), "reported assorter margin is not in [0,1]"
    assert np.min(N) >= 1, "N (population size) must be no less than 1 in all strata"
    K = len(N)
    u = 2 # the upper bound on the overstatment assorters, per STS
    eta_0 = 1/2 # the global null in terms of the original assorters
    assert K == 2, "only works for two strata"
    w = N / np.sum(N)
    assert np.dot(w, A_c) > 1/2, "reported assorter mean (A_c) implies the winner lost"
    eta_1_grid = np.linspace(max(0, eta_0 - w[1]), min(u, eta_0/w[0]), n_bands + 1)
    eta_2_grid = (eta_0 - w[0] * eta_1_grid) / w[1]
    # transformed overstatement assorters
    # the transformation is per STS, except divided by 2
    # the division by 2 allows a plurality CCA population to be defined on [0,1] instead of [0,2]
    beta_1_grid = (eta_1_grid + 1 - A_c[0])/2 # transformed null means in stratum 1
    beta_2_grid = (eta_2_grid + 1 - A_c[1])/2 # transformed null means in stratum 2
    beta_grid = np.transpose(np.vstack((beta_1_grid, beta_2_grid)))
    betas = []
    for i in np.arange(beta_grid.shape[0]-1):
        centroid = (beta_grid[i,:] + beta_grid[i+1,:]) / 2
        betas.append([(beta_grid[i,:], beta_grid[i+1,:]), centroid])
    return betas



def simulate_plurcomp(N, A_c, p_1 = np.array([0.0, 0.0]), p_2 = np.array([0.0, 0.0]), lam_func = None, allocation_func = Allocations.proportional_round_robin, method = "ui-ts", n_bands = 100, alpha = 0.05, WOR = False, reps = 30):
    '''
    simulate (repateadly, if desired) a card-level comparison audit (CCA) of a plurality contest
    given reported assorter means and overstatement rates in each stratum
    uses the parametrization of stratified CCAs developed in https://arxiv.org/pdf/2207.03379
    Parameters
    ----------
        N: a length-K list of ints
            the size of each stratum
        A_c: a length-K np.array of floats
            the reported assorter mean bar{A}_c in each stratum
        p_1: a length-K np.array of floats
            the true rate of 1 vote overstatements in each stratum, defaults to none
        p_2: a length-K np.array of floats
            the true rate of 2 vote overstatements in each stratum, defaults to none
        lam_func: callable, a function from class Bets
            the function for setting the bets (lambda_{ki}) for each stratum / time
        allocation_func: callable, a function from the Allocations class
            function for allocation sample to strata for each eta
        method: string, either "ui-ts" or "lcbs"
            the method for testing the global null
            either union-intersection testing or combining lower confidence bounds as in Wright's method
        alpha: float in (0,1)
            the significance level of the test
        WOR: boolean
            should the martingales be computed under sampling without replacement?
        reps: int
            the number of simulations of the audit to run
    Returns
    ----------
        two scalars, the expected stopping time of the audit and the global sample size of the audit;
        these are the same whenever the allocation rule is nonadaptive
    '''
    assert method in ["lcb", "ui-ts"], "method argument is invalid"
    K = len(N)
    w = N/np.sum(N)
    A_c_global = np.dot(w, A_c)
    betas = construct_eta_bands_plurcomp(A_c, N, n_bands)

    x = []
    v = 2 * A_c_global - 1 # global diluted margin
    a = 1/(2-v) # where the pointmass would be for a global (unstratified) CCA

    #construct assorter population within each stratum
    for k in np.arange(K):
        num_points = [int(n_err) for n_err in saferound([N[k]*p_2[k], N[k]*p_1[k], N[k]*(1-p_2[k]-p_1[k])], places = 0)]
        x.append(1/2 * np.concatenate([np.zeros(num_points[0]), np.ones(num_points[1]) * 1/2, np.ones(num_points[2])]))

    stopping_times = np.zeros(reps) #container for global stopping times
    sample_sizes = np.zeros(reps) #container for global sample sizes
    for r in np.arange(reps):
        X = [np.random.choice(x[k],  len(x[k]), replace = (not WOR)) for k in np.arange(K)] #shuffle (WOR) or sample (WR) a length-N_k sequence from each stratum k
        if method == "ui-ts":
            uits, eta_min, global_ss = banded_uits(X, N, betas, lam_func, allocation_func, log = True, WOR = WOR)
            stopping_times[r] = np.where(any(uits > -np.log(alpha)), np.argmax(uits > -np.log(alpha)), np.sum(N))
            sample_sizes[r] = global_ss[int(stopping_times[r])]
        elif method == "lcb":
            eta_0 = (1/2 + 1 - A_c_global)/2
            lcb = global_lower_bound(X, N, lam_func, allocation_func, alpha, WOR = WOR, breaks = 1000)
            stopping_times[r] = np.where(any(lcb > eta_0), np.argmax(lcb > eta_0), np.sum(N))
            sample_sizes[r] = stopping_times[r]
    return np.mean(stopping_times), np.mean(sample_sizes)

########### Hybrid audit ##########
def generate_hybrid_audit_population(N, A_c, invalid = None, assort_method = "STS"):
    '''
    a function to generate a population of assorters for a hybrid audit of a plurality contest
    the first stratum is a card-polling stratum (without CVRs)
    the second stratum is the card-level comparision stratum (with CVRs)

    Parameters
    --------------
    N: a length-2 np.array or list of positive ints
        the sizes of each stratum
    A_c: a length-2 np.array of floats in [0,1]
        the reported (and true) assorter mean in each batch
        expressed in terms of the proportion of valid votes for the winner: A_c[i] > 0.5 means the winner won stratum i
    invalid: a length-B np.array of floats in [0,1]
        the proportion of invalid votes in each batch; defaults to 0
    assort_method: str in ["STS","ONE"]
        "STS" uses the method to construct assorters and intersection nulls in Sweeter than SUITE (https://arxiv.org/pdf/2207.03379)
        "ONE" uses the method to construct assorters in ONEAudit (https://arxiv.org/pdf/2303.03335)
    Returns
    ------------
    a list of ints; the assorters representing the hybrid audit population
    '''
    w = N / np.sum(N) # stratum weights
    A_c_global = np.dot(w, A_c) # global reported assorter mean
    assert (len(A_c) == 2) and (len(N) == 2), "a hybrid audit consists of exactly two strata"
    assert A_c_global > 1/2, "contradiction: batch-level assorter means imply reported winner lost"
    K = 2 # number of strata
    u = 1 # upper bound for plurality assorters
    if invalid is None:
        invalid = np.zeros(2)
    v = 2 * A_c_global - 1
    if assort_method == "ONE":
        batch_sizes = np.append(N[0], np.ones(N[1]))
        # CVR stratum needs to be expanded to batches of size 1
        N_2_i = N[1] * invalid[1] # the number of CVRs showing invalid votes
        N_2_w = N[1] * A_c[1] * (1 - invalid[1]) # the number of CVRs showing votes for the winner
        N_2_l = N[1] * (1 - A_c[1]) * (1 - invalid[1]) # the number of CVRs showing votes for the winner
        N_2_iwl = [int(c) for c in saferound([N_2_i, N_2_w, N_2_l], places = 0)] # rounding to integers
        A_c_cvrs = np.repeat([1/2, 1, 0], N_2_iwl)
        A_c_batches = np.append(A_c[0], A_c_cvrs)
        invalid_batches = np.append(invalid[0], np.append(np.ones(N_2_iwl[0]), np.zeros(N_2_iwl[1] + N_2_iwl[2])))
        pop = generate_oneaudit_population(batch_sizes = batch_sizes, A_c = A_c_batches, invalid = invalid_batches)
        strata = np.repeat(np.where(batch_sizes > 1, 0, 1), repeats = batch_sizes.astype("int"))
        strat_pop = [pop[strata == 0], pop[strata == 1]] # split population into polling and clca strata
    elif assort_method == "STS":
        # number of cards of each kind in card-polling stratum
        N_1_i = N[0] * invalid[0] # the number of CVRs showing invalid votes
        N_1_w = N[0] * A_c[0] * (1 - invalid[0]) # the number of CVRs showing votes for the winner
        N_1_l = N[0] * (1 - A_c[0]) * (1 - invalid[0])
        N_1_iwl = [int(c) for c in saferound([N_1_i, N_1_w, N_1_l], places = 0)]
        # population values for the polling stratum, as overstatements from pseudo-CVRs
        # B_nocvrs_i = 1 + (np.ones(N_1_iwl[0])/2 - A_c[0])
        # B_nocvrs_w = 1 + (np.ones(N_1_iwl[1]) - A_c[0])
        # B_nocvrs_l = 1 + (np.zeros(N_1_iwl[2]) - A_c[0])
        # population values as raw assorters
        B_nocvrs_i = np.ones(N_1_iwl[0])/2 # invalid votes
        B_nocvrs_w = np.ones(N_1_iwl[1]) # votes for winner
        B_nocvrs_l = np.zeros(N_1_iwl[2]) # votes for loser
        B_nocvrs = np.concatenate([B_nocvrs_i, B_nocvrs_w, B_nocvrs_l]) # the assorter values of cards without CVRs
        B_cvrs = np.ones(N[1]) / (2 * u) # the assorter values of cards correct CVRs (divided by 2)
        strat_pop = [B_nocvrs, B_cvrs]
    else:
        raise ValueError("Input assort_method in [\"STS\", \"ONE\"]")
    return strat_pop

def construct_eta_bands_hybrid(A_c, N, n_bands = 100, assort_method = "STS"):
    '''
    construct a set of bands to run a hybrid audit of a plurality contest
    using the parameterization from Sweeter than SUITE (STS): https://arxiv.org/pdf/2207.03379

    Parameters
    ----------
        A_c: length-2 list of floats in [0,1]
            the reported assorter mean in each stratum
            the first entry is the card-polling stratum, the second is the CLCA stratum
        N: length-2 list of ints
            the size of the population within each stratum
            the first entry is the card-polling stratum, the second is the CLCA stratum
        n_bands: positive int
            the number of equal-width bands in the tesselation of the null boundary
        assort_method: str in ["STS","ONE"]
            the method used to parameterize the population and construct assorters
            MUST MATCH THE STRATEGY USED TO CONSTRUCT ASSORTERS
    Returns
    ----------
        a list of tuples of length points,
        each tuple represents a band over which the null mean will be tested,
        it contains one eta that is used to construct bets and selections
        and two etas representing the endpoints of the band, which are used to conservatively test the intersection nulls for that band
    '''
    assert (np.max(A_c) <= 1) and (np.min(A_c) >= 0), "reported assorter margin is not in [0,1]"
    assert np.min(N) >= 1, "N (population size) must be no less than 1 in all strata"
    K = len(N)
    u = 1 # the upper bound on the original assorters
    eta_0 = 1/2 # the global null on the scale of the original assorters
    assert K == 2, "only works for two strata"
    w = N / np.sum(N)
    assert np.dot(w, A_c) > 1/2, "reported assorter mean (A_c) implies the winner lost"
    eta_1_grid = np.linspace(max(0, eta_0 - w[1]), min(u, eta_0/w[0]), n_bands + 1)
    eta_2_grid = (eta_0 - w[0] * eta_1_grid) / w[1]
    if assort_method == "STS":
        # the transformation is per STS, except divided by 2 in the CLCA stratum
        # the division of CLCA means (and overstatement assorters) by u is so the stratum is bounded on [0,1] (ow the CLCA stratum is bounded on [0,2])
        #beta_1_grid = (eta_1_grid + 1 - A_c[0]) / (2*u) # null means in card-polling stratum
        beta_1_grid = eta_1_grid
        beta_2_grid = (eta_2_grid + 1 - A_c[1]) / (2*u) # null means in CLCA stratum
    elif assort_method == "ONE":
        # ONEAudit does not require transforming the nulls within the CVR stratum
        beta_1_grid = eta_1_grid
        beta_2_grid = eta_2_grid
    else:
        raise ValueError("assort_method must be in [\"STS\", \"ONE\"]")
    beta_grid = np.transpose(np.vstack((beta_1_grid, beta_2_grid)))
    betas = []
    for i in np.arange(beta_grid.shape[0] - 1):
        centroid = (beta_grid[i,:] + beta_grid[i+1,:]) / 2
        betas.append([(beta_grid[i,:], beta_grid[i+1,:]), centroid])
    return betas

############ ONEAudit  ##############
# functions to generate ONEAudit populations per Stark, 2023 (https://arxiv.org/abs/2303.03335) and simulate sampling and inference
def generate_oneaudit_population(batch_sizes, A_c, invalid = None, A_m = None):
    '''
    a function to generate a population of assorters using ONE CVRs based on batch subtotals
    from a two-candidate plurality contest, potentially with invalid votes

    Parameters
    --------------
    batch_sizes: a length-B np.array of positive ints
        the sizes of each of B batches; NB: the population size is sum(batch_sizes)
    A_c: a length-B np.array of floats in [0,1]
        the reported assorter mean in each batch
        expressed in terms of the proportion of valid votes for the winner: A_c[i] > 0.5 means the winner won batch i
    invalid: a length-B np.array of floats in [0,1]
        the proportion of invalid votes in each batch; defaults to 0
    A_m: a length-B np.array of floats in [0,1]
        the true assorter mean in each batch
        defaults to A_c, implying the reported means (A_c) are exactly accurate
    Returns
    ------------
    a list of ints; the ONE assorters representing the audit population
    '''
    B = len(batch_sizes) # the number of batches
    if A_m is None:
        A_m = A_c
    if invalid is None:
        invalid = np.zeros(B)
    A_c_global = np.dot(batch_sizes / np.sum(batch_sizes), A_c)
    A_m_global = np.dot(batch_sizes / np.sum(batch_sizes), A_c)
    if A_c_global < 1/2:
        warnings.warn("reported assorter means imply reported winner lost")
    if A_m_global < 1/2:
        warnings.warn("true assorter means imply reported winner lost")


    u = 1 # the upper bound on the original assorters for plurality contests
    batches = []

    v = 2 * A_c_global - 1 # global reported assorter margin
    for i in range(B):
        # these votes are possibly fractional and need to be rounded
        invalid_votes    = batch_sizes[i] * invalid[i] # the number of invalid votes
        votes_for_winner = batch_sizes[i] * A_m[i] * (1 - invalid[i]) # the number of votes for the winner
        votes_for_loser  = batch_sizes[i] * (1 - A_m[i]) * (1 - invalid[i]) # the number of votes for the loser
        # rounding while preserving the sum
        votes = saferound([invalid_votes, votes_for_winner, votes_for_loser], places = 0)
        votes = [int(vote) for vote in votes] # conversion to integers

        # ONE assorter values
        B_i = (u + 1/2 - A_c[i]) / (2 * u - v) # assorter for invalid votes
        B_w = (u + 1   - A_c[i]) / (2 * u - v) # assorter for winner votes
        B_l = (u + 0   - A_c[i]) / (2 * u - v) # assorter for loser votes

        # assorter populations as an array
        batches.append(np.concatenate([B_i * np.ones(votes[0]), B_w * np.ones(votes[1]), B_l * np.ones(votes[2])]))
    pop = np.concatenate(batches)
    return pop





############ functions to compute the convex (inverse) UI-TS ############
class PGD:
    '''
    class of helper functions to compute UI-TS for inverse bets by projected gradient descent
    currently everything is computed on assumption of sampling with replacement
    '''


    def log_mart(eta, samples):
        '''
        return the log value of within-stratum martingale evaluated at eta_k
        bets are exponential in negative eta, offset by lagged sample mean
        '''
        #lag_mean = np.mean(past_samples) if past_samples.size > 0 else 1/2
        #for the bet on the first sample, just guesses a mean of 1/2; after that, uses the sample mean
        x = samples
        if x.size == 0:
            return 0
        else:
            return np.sum(np.log(1 + Bets.inverse_eta(x, eta) * (x - eta)))

    def global_log_mart(eta, samples):
        '''
        return the log value of the product-combined I-TSM evaluated at eta
        '''
        eta = np.array(eta)
        return cvxopt.matrix(
            np.sum([PGD.log_mart(eta[k], samples[k]) for k in np.arange(len(eta))])
        )


    def partial(eta, samples):
        '''
        return the partial derivative (WRT eta) of the log I-TSM evaluated at eta_k
        '''
        x = samples
        eta = np.array(eta)
        lag_mean, lag_sd = Bets.lag_welford(x)
        c_untrunc = lag_mean - lag_sd
        c = np.minimum(np.maximum(0.1, c_untrunc), 0.9)
        if x.size == 0: #if a stratum hasn't been sampled, the UI-TS is 1 for all eta
            return 0
        else:
            return -np.sum((c * x * (eta**(-2))) / (1 - c + c * x * (eta**(-1))))

    def grad(eta, samples):
        '''
        return the gradient (WRT eta) of the log I-TSM evaluated at eta
        '''
        eta = np.array(eta)
        return cvxopt.matrix(
            np.array([PGD.partial(eta[k], samples[k]) for k in np.arange(len(eta))])
        )

    def second_partial(eta, samples):
        '''
        computes the second partial derivative (WRT eta^2) of the log I-TSM evaluated at eta_k
        Mixed partials are zero.
        '''
        x = samples
        eta = np.array(eta)
        lag_mean, lag_sd = Bets.lag_welford(x)
        c_untrunc = lag_mean - lag_sd
        c = np.minimum(np.maximum(0.1, c_untrunc), 0.9)
        if x.size == 0:
            return 0
        else:
            g = 1 - c + c * x / eta
            g_prime = -c * x * eta**(-2)
            f = c * x * eta**(-2)
            f_prime = -2 * c * x * eta**(-3)
            numerator = g * f_prime - g_prime * f
            denominator = g**2
            return -np.sum(numerator/denominator)

    def hessian(eta, samples):
        '''
        return the Hessian matrix of the log I-TSM evaluated at eta
        the off-diagonals (mixed-partials) are all zero
        '''
        eta = np.array(eta)
        K = len(eta)
        hess = np.zeros((K, K))
        for k in range(K):
            hess[k][k] = PGD.second_partial(eta[k], samples[k])
        return cvxopt.matrix(hess)




def convex_uits(x, N, allocation_func, eta_0 = 1/2, log = True):
    '''
    compute the UI-TS when bets are inverse:
        lambda = c / eta
    currently only works for sampling with replacement

    Parameters
    ----------
    x: length-K list of np.arrays
        samples from each stratum in random order
    N: np.array of length K
        the number of elements in each stratum in the population
    allocation_func: callable, a function from class Allocations
        the desired allocation strategy.
    eta_0: double in [0,1]
        the global null mean
    log: Boolean
        return UI-TS on log-scale
    Returns
    --------
    the value of the union-intersection supermartingale
    '''
    assert allocation_func in nonadaptive_allocations, "Cannot use eta-adaptive allocation"
    w = N / np.sum(N) #stratum weights
    K = len(N) #number of strata
    n = [x[k].shape[0] for k in range(K)]

    #this is a nested list of arrays
    #it stores the samples available in each stratum at time i = 0,1,2,...,n
    samples_t = [[[] for _ in range(K)] for _ in range(np.sum(n)+1)]
    uits = [0 if log else 1] #uits starts at 1 at time 0
    samples_t[0] = [np.array([]) for _ in range(K)] #initialize with no samples
    T_k = np.zeros((np.sum(n)+1, K), dtype = int)
    eta_stars = np.zeros((np.sum(n)+1, K)) #stores minimizing etas
    bets_t = [np.zeros(1)]*K # stores bets within each stratum at each time; first bet is 0
    #constraint set for cvxopt
    G = np.concatenate((
        np.expand_dims(w, axis = 0),
        np.expand_dims(-w, axis = 0),
        -np.identity(K),
        np.identity(K))
    )
    h = np.concatenate((
        eta_0 * np.ones(1),
        -eta_0 * np.ones(1),
        np.zeros(K),
        np.ones(K))
    )
    eta_stars = np.zeros((np.sum(n)+1, K))
    eta_star_start = pypoman.projection.project_point_to_polytope(point = np.ones(K), ineq = (G, h), qpsolver = 'cvxopt')
    eta_stars[0,:] = eta_star_start



    for i in np.arange(1, np.sum(n)):
        #select next stratum
        if allocation_func in [Allocations.greedy_kelly]:
            terms = []
            for k in range(K):
                m = mart(x[k][0:T_k[i,k]], eta_stars[i-1,k], lam = bets_t[k], N = np.inf, log = True)
                log_growth = np.diff(m)
                terms.append(log_growth)
        else:
            terms = None
        S_i = allocation_func(x, T_k[i-1,:], n, N, eta = eta_stars[i-1,:], lam = bets_t, terms = terms)
        T_k[i,:] = T_k[i-1,:]
        T_k[i,S_i] += 1
        #bets for the stratum selection
        bets_t = [mart(x[k][0:T_k[i,k]], eta_stars[i-1,k], lam_func = Bets.inverse_eta, output = "bets") for k in range(K)]
        for k in np.arange(K):
            samples_t[i][k] = x[k][np.arange(T_k[i,k])] #update available samples
        #don't compute the minimum if there are unsampled strata
        if any(T_k[i,:] == 0):
            log_ts = 0
            eta_stars[i,:] = eta_star_start
        else:
            SAMPLES = samples_t[i]
            #define function for cvxopt
            def F(x=None, z=None):
                x0 = cvxopt.matrix(eta_stars[i-1,:])
                if x is None and z is None:
                    return 0, x0
                if z is None:
                    return PGD.global_log_mart(x, SAMPLES), PGD.grad(x, SAMPLES).T
                return PGD.global_log_mart(x, SAMPLES), PGD.grad(x, SAMPLES).T, z*PGD.hessian(x, SAMPLES)
            soln = cvxopt.solvers.cp(F, cvxopt.matrix(G), cvxopt.matrix(h))
            if soln['status'] == 'optimal':
                eta_stars[i,:] = np.array(soln['x']).T
            else:
                raise RuntimeError("Optimization did not converge")
            #store current value of UI-TS
            log_ts = float(PGD.global_log_mart(eta_stars[i], SAMPLES)[0])
        if log:
            uits.append(log_ts)
        else:
            uits.append(np.exp(log_ts))
    return np.array(uits), eta_stars, T_k


######## additional helper functions #########
def random_truncated_gaussian(mean, sd, size):
    '''
    simulate from a gaussian truncated to [0,1]

    Parameters
    ----------
        mean: double in [0,1]
        sd: positive double
        size: the number of samples to draw
    Returns
    ----------
        length-size np.array of truncated gaussian draws
    '''
    assert 0 <= mean <= 1, "mean is not in [0,1]"
    samples = np.zeros(size)
    for i in range(size):
        while True:
            draw = np.random.normal(mean, sd, 1)
            if 0 <= draw <= 1:
                samples[i] = draw
                break
    return samples


def t_test(x, eta_0, N = np.inf, WOR = False):
    '''
    run a one-sample, one-sided t-test of the hypothesis that E(x) <= eta_0

    Parameters
    ----------
        x: length-n np.array
            n samples drawn with or without replacement
        eta_0: double
            a hypothesized null mean
        N: int or np.inf
            the size of the population, may be infinite
        WOR: boolean
            was x drawn with or without replacement
    Returns
    ----------
        a P-value for the hypothesis that the mean of the population from which x is drawn is less than eta_0
    '''
    n = x.size[0]
    #currently fpc is not employed so this is conservative for sampling WOR
    fpc = np.sqrt((N - n) / (N-1)) if WOR else 1
    SE = fpc * np.std(x) / np.sqrt(n)
    test_stat = (np.mean(x) - eta_0) / SE
    p_val = 1 - t.cdf(test_stat, n-1)
    return p_val

def stratified_t_test(x, eta_0, N):
    '''
    run a stratified, one-sample, one-sided t-test of the hypothesis that E(x) <= eta_0

    Parameters
    ----------
        x: length-K list of np.arrays
            n_k samples drawn with replacement from each stratum 1 to K
        eta_0: double
            the hypothesized global null mean
        N: length-K list of ints
            the size of each stratum, used to calculate stratum weights
    Returns
    ----------
        a P-value for the hypothesis that the global mean of the population from which x is drawn is less than eta_0
    '''
    n = np.array([x_k.size for x_k in x])
    w = N/np.sum(N)
    g = N * (N - n) / n #used to calculate effective degrees of freedom from Cochran (1977) p. 69
    sample_means = np.array([np.mean(x_k) for x_k in x])
    sample_vars = np.array([np.var(x_k) for x_k in x])
    mean_est = np.sum(w * sample_means)
    #SE_est = np.sqrt((1/np.sum(N)**2) * np.sum(g * sample_vars))
    SE_est = np.sqrt(np.sum((N / np.sum(N))**2 * sample_vars / n))
    #dof = (np.sum(g * sample_vars)**2) / np.sum((g**2 * sample_vars**2) / (n - 1))
    dof = np.min(n - 1) #simpler but conservative (always no larger than above)
    test_stat = (mean_est - eta_0) / SE_est
    p_val = 1 - t.cdf(test_stat, dof)
    return p_val
