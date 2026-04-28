package org.cryptobiotic.rlauxe.strata

// I think allocation refers to how many samples come from each strata.

//
// class Allocations:
//    '''
//    fixed, predictable, and/or eta-adaptive stratum allocation rules, given bets
//    Parameters
//    ----------
//        x: length-K list of length-n_k np.arrays with elements in [0,1]
//            the data sampled from each stratum
//        n: length-K list or np.array of ints
//            the total sample size in each stratum, i.e. the length of each x
//        N: length-K list or np.array of ints
//            the (population) size of each stratum
//        eta: length-K np.array in [0,1]^K
//            the vector of null means across strata
//        lam: a length-K np.arry each of len(x[k])
//
//    Returns
//    ----------
//        allocation: a length sum(n_k) sequence of interleaved stratum selections in each round
//    '''
//    #is there a way to bifurcate allocations and betting into eta-oblivious/aware using class structure

// eta-oblivious, nonadaptive round robin strategy
// return a length N sequence of interleaved stratum selections // TODO change to sample at a time?
fun round_robinY(Nk: List<Int>): List<Int> {
    val K = Nk.size
    val leftInStata = IntArray(K) { Nk[it] }

    val selectedStrata = mutableListOf<Int>()
    while (leftInStata.sum() > 0) {
        repeat(K) { strataNum ->
            val leftInStatum = leftInStata[strataNum]
            if (leftInStatum > 0) {
                selectedStrata.add(strataNum)
                leftInStata[strataNum] = leftInStatum - 1
            }
        }
    }
    return selectedStrata
}

fun round_robin(running_Tk: IntArray, Nk: List<Int>): Int {
    val exhausted =  running_Tk.mapIndexed { k, v -> (v == Nk[k]) }
    val next = numpy_argmin_product(running_Tk, exhausted, )
    return next
}

//    def round_robin(x, running_T_k, n, N, eta, lam, **kwargs):
//        #eta-nonadaptive round robin strategy
//        exhausted = np.ones(len(n))
//        exhausted[running_T_k == n] = np.inf
//        next = np.argmin(exhausted * running_T_k) // JMJ!!
//        return next

//
//    def proportional_round_robin(x, running_T_k, n, N, eta, lam, **kwargs):
//        #eta-nonadaptive round robin strategy, proportional to total sample size
//        exhausted = np.ones(len(n))
//        exhausted[running_T_k == n] = np.inf
//        next = np.argmin(exhausted * running_T_k / n)
//        return next
//
//    def more_to_larger_means(x, running_T_k, n, N, eta, lam, **kwargs):
//        #eta-nonadaptive
//        #samples more from strata with larger values of x on average
//        #does round robin until every stratum has been sampled once
//        if any(running_T_k == 0):
//            next = Allocations.round_robin(x, running_T_k, n, N, eta, lam)
//        else:
//            K = len(x)
//            eps = kwargs.get("eps", 0.01)
//            sd_min = kwargs.get("sd_min", 0.05)
//            #UCB-like algorithm targeting the largest stratum mean
//            past_x = [x[k][0:running_T_k[k]] for k in range(K)]
//            means = np.array([np.mean(px) for px in past_x])
//            std_errors = np.array([np.maximum(np.std(px), sd_min) for px in past_x]) / np.sqrt(running_T_k)
//            ucbs = means + 2 * std_errors
//            scores = np.where(running_T_k == n, -np.inf, ucbs)
//            next = np.argmax(scores)
//        return next
//
//    def neyman(x, running_T_k, n, N, eta, lam, **kwargs):
//        #eta-adaptive
//        #uses a predictable Neyman allocation to set allocation probabilities
//        #see Neyman (1934)
//        if any(running_T_k <= 2):
//            #use round robin until we have at least 2 samples from each stratum
//            next = Allocations.round_robin(x, running_T_k, n, N, eta, lam)
//        else:
//            K = len(x)
//            eps = kwargs.get("eps", 0.01) #lower bound on sd
//            sds = np.array([np.std(x[k][0:running_T_k[k]]) for k in range(K)]) + eps
//            sds = np.where(running_T_k == n, 0, sds)
//            neyman_weights = N * sds
//            probs = neyman_weights / np.sum(neyman_weights)
//            next = np.random.choice(np.arange(K), size = 1, p = probs)
//        return next
//
//    def proportional_to_mart(x, running_T_k, n, N, eta, lam, **kwargs):
//        #eta-adaptive strategy, based on size of martingale for given intersection null
//        #this function involves alot of overhead, may want to restructure
//        if any(running_T_k <= 1):
//            next = Allocations.round_robin(x, running_T_k, n, N, eta, lam)
//        K = len(x)
//        marts = np.array([mart(x[k], eta[k], None, lam[k], N[k], log = False)[running_T_k[k]] for k in range(K)])
//        scores = np.minimum(np.maximum(marts, 1), 1e3)
//        scores = np.where(running_T_k == n, 0, scores) #if the stratum is exhausted, its score is 0
//        probs = scores / np.sum(scores)
//        next = np.random.choice(np.arange(K), size = 1, p = probs)
//        return next
//
//    def predictable_kelly(x, running_T_k, n, N, eta, lam, terms, **kwargs):
//        '''
//        for this allocation function and greedy kelly, need to pass in an array of the past log-growths (terms)
//        terms is a list of the log-growths in each stratum
//        '''
//        #this estimates the expected log-growth of each martingale
//        #and then draws with probability proportional to this growth
//        #currently, can't use randomized betting rules (would need to pass in terms directly)
//        if any(running_T_k <= 2):
//            next = Allocations.round_robin(x, running_T_k, n, N, eta, lam)
//        else:
//            K = len(x)
//            eps = kwargs.get("eps", 0.01)
//            sd_min = kwargs.get("sd_min", 0.05)
//            #return past terms for each stratum on log scale
//            #compute martingale as if sampling were with replacement (N = np.inf)
//            past_terms = [terms[k][0:running_T_k[k]] for k in range(K)]
//
//            #use a UCB-like approach to select next stratum
//            est_log_growth = np.array([np.mean(pt) for pt in past_terms])
//            se_log_growth = np.array([np.maximum(np.std(pt), sd_min) for pt in past_terms]) / np.sqrt(running_T_k)
//            ucbs_log_growth = est_log_growth + 2 * se_log_growth
//            scores = np.where(running_T_k == n, -np.inf, ucbs_log_growth)
//            next = np.argmax(scores)
//        return next
//    #essentially just a renaming of predictable_kelly, but is handled differently
//    def greedy_kelly(x, running_T_k, n, N, eta, lam, terms, **kwargs):
//        '''
//        terms is a list of the log-growths in each stratum
//        '''
//        if any(running_T_k <= 2):
//            next = Allocations.round_robin(x, running_T_k, n, N, eta, lam)
//        else:
//            K = len(x)
//            eps = kwargs.get("eps", 0.01)
//            sd_min = kwargs.get("sd_min", 0.05)
//            #return past terms for each stratum on log scale
//            #compute martingale as if sampling were with replacement (N = np.inf)
//            past_terms = [terms[k][0:running_T_k[k]] for k in range(K)]
//
//            #use a UCB-like approach to select next stratum
//            est_log_growth = np.array([np.mean(t) for t in past_terms])
//            se_log_growth = np.array([np.maximum(np.std(pt), sd_min) for pt in past_terms]) / np.sqrt(running_T_k)
//            ucbs_log_growth = est_log_growth + 2 * se_log_growth
//            scores = np.where(running_T_k == n, -np.inf, ucbs_log_growth)
//            next = np.argmax(scores)
//        return next
