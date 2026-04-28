# Implementing stratified audits
_last changed: 4/20/2026_

# Sweeter

2022	Sweeter than SUITE: Supermartingale Stratified Union-Intersection Tests of Elections
Jacob V. Spertus and Philip B. Stark; 25 Jul 2022
https://arxiv.org/abs/2207.03379

Code in R at https://github.com/spertus/sweeter-than-SUITE

## 3.1 Assorters and assertions

In a stratified audit, the population of ballot cards is partitioned into K disjoint strata. 
Stratum k contains N_k ballot cards, so N = Sum { N_k }.
The weight of stratum k is w_k := N_k /N. The vector is w.
The true mean of the assorter values in stratum k is µ_k. The vector is µ.

For each assorter A there is a set of assorter values {x_i, i=1.. N}.
Each assorter may have its own upper bound u_k in stratum k. (because in principle the assorters could be different??)
The true mean of the assorter values in stratum k is µ_k ; µ := [µ1 , ..., µK ]
The overall assorter mean µ = w dot µ.

Let θ = [θ_1 , ..., θ_K ] with 0 ≤ θ_k ≤ u_k be the _hypothesized stratum mean θ_k in each stratum_ (aka _within-stratum nulls_).
A single intersection null is of the form µ ≤ θ, i.e., ∩ k=1..K { µ_k ≤ θ_k }, (all assertions are true).
The _union-intersection form of the complementary null that the outcome is incorrect_ is:

        H0:   U { w dot 0 < 1/2 }   { ∩ k=1..K { µ_k ≤ θ_k }    }          eq (1)

(the union is over all values of 0 where the weighted sum of the 0_i are < 1/2)

From stratum k we have n_k samples X_k := {X_1,k , ..., X_nk,k }
drawn by simple random sampling, with or without replacement, independently across strata.

## 3.2 Stratified comparison audits

We now show that for stratified audits, the math is simpler if, as before,
we assign a nonnegative number to each card that depends on the votes and
reported votes, but instead of comparing the average of the resulting list to 1/2,
we compare it to a threshold that depends on the hypothesized stratum mean θ_k.

Let uA_k be the upper bound on the original(primitive) assorter for stratum k and
ω_ik := A(c_ik ) − A(b_ik ) ∈ [−uk_A, uk_A]
be the overstatement for the ith card in stratum k, where A(c_ik) is the value of the assorter applied to the CVR and
A(b_ik ) is the value of the assorter for the MVR.

Let Āb_k , Āc_k , and w̄_k = Āc_k − Āb_k be the true assorter mean, reported assorter mean, and average overstatement, all for stratum k.

For a particular θ, the intersection null claims that in stratum k, Āb_k ≤ θ_k .

Adding uA_k − Āc_k to both sides of the inequality yields

    uA_k − ω̄_k ≤ θ_k + uA_k − Āc_k .

Letting u_k := 2*uA_k, take B_ik := uA_k − ω_ik ∈ [0, uk] and B̄_k := Sum { B_ik } / N_k (average B_ik in kth stratum)
Then { B_ik } is a bounded list of nonnegative numbers, and the assertion in stratum k is true if 
    
    B̄_k > β_k := θ_k + uA_k − Āc_k , where all terms on the right are known.

Testing whether B̄ ≤ βk is the canonical problem solved by ALPHA [19]. 
The intersection null can be written

    B̄_k ≤ β_k for all k ∈ {1, . . . , K}.

Define u := [u1 , . . . , uK]. As before, we can reject the complementary null if we
can reject _all_ intersection nulls θ for which 0 ≤ θ ≤ u and w dot θ ≤ 1/2.

## 3.3 Union-intersection tests

A union-intersection test for (1) combines evidence across strata to see whether
any intersection null in the union is plausible given the data, that is, to check
whether the P-value of any intersection null in the union is greater than the risk limit.

Consider a fixed vector θ of within-stratum nulls. Let P (θ) be a valid
P-value for the intersection null µ ≤ θ. We can reject the
union-intersection null (1) if we can reject the intersection null for all feasible θ
in the half-space w dot θ ≤ 1/2.

(so here we are trying to guess "all feasible θ" in the half-space w dot θ ≤ 1/2 )

Equivalently, P(θ) maximized over feasible θ is a P-value for eq (1):

    P∗ := max {P(θ): 0 ≤ θ ≤ u and w dot θ ≤ 1/2}. (max over θ)

This method is fully general in that it can construct a valid P-value for (1) from
stratified samples and any mix of risk-measuring functions that are individually
valid under simple random sampling. However, the tractability of the optimization
problem depends on the within-stratum risk-measuring functions and the form
of P used to pool risk. So does the efficiency of the audit.

3.4 Fisher's Combining Functions (PF)

3.5 Intersection supermartingales

ALPHA derives a simple form for the P-value for an intersection null when
supermartingales are used as test statistics within strata. Let M_k (θ_k) be a
supermartingale constructed from n_k samples drawn from stratum k when the
null µ_k ≤ θ_k is true. Then the product of these supermartingales is also a
supermartingale under the intersection null, so its reciprocal (truncated above at
1) is a valid P-value:

    P_M(θ) := 1 ∧ Prod { M_k (θ_k) }^(-1)  ( I think its the inverse of the product, not the product of the inverse)

Maximizing PM(θ) (equivalently, minimizing the intersection supermartingale) yields PM*, a valid P-value for (1).

3.6 Within-stratum P-values

The class of within-stratum P-values that can be used to construct PF is very
large, but PM is limited to functions that are supermartingales under the null.
Possibilities include SUITE, ALPHa, Empirical Berstein, and Betting TSM (see Stratified paper, 2.3.1 below).

**Empirical Bernstein (EB**), which is a supermartingale presented in Howard
et al. [8] and Waudby-Smith and Ramdas [22]. Although they are generally
not as efficient as ALPHA and other betting supermartingales [22], EB
supermartingales have an exponential analytical form that makes log PM (θ)
or log PF (θ) linear or piecewise linear in θ. Hence, PM* and PF∗ can be
computed quickly for large K by solving a linear program.

## 3.7  Sequential stratum selection

The use of sequential sampling in combination with stratification presents a new
possibility for reducing workload: sample more from strata that are providing
evidence against the intersection null and less from strata that are not helping.
_Perhaps suprisingly, such adaptive sampling yields valid inferences when the
P-value is constructed from supermartingales and the stratum selection function
depends only on past data_.

## 4. Evaluations

Risk was measured by ALPHA or EB combined either as intersection supermartingales (PM*)
or with Fisher’s combining function (PF*), with one of two stratum selectors: proportional allocation or lower-sided testing.

In proportional allocation, the number of samples from each stratum is in proportion to the number of cards in the stratum.

Allocation by lower-sided testing involves testing the null µk ≥ θk sequentially at level 5%.
This allocation rule ignores samples from a given stratum once the
lower-sided hypothesis test rejects, since there is strong evidence that the null is true in that stratum.
This “hard stop” algorithm is unlikely to be optimal, but it
leads to a computationally efficient implementation and illustrates the potential
improvement in workload from adaptive stratum selection.

Tuning parameters were chosen as follows. ALPHA supermartingales were
specified either with τ_ik as described in 2.5.2 (ALPHA-ST, shrink-truncate) or
with a strategy that biases τik towards uk : (ALPHA-UB, “upward bias”) which helps in comparison audits because
the distribution of assorter values consists of a point mass at uA_k = u_k /2 and
typically small masses (with weight equal to the overstatement rates) at 0 and
another small value. This concentration of mass makes it advantageous to bet
more aggressively that the next draw will be above the null mean; that amounts
to biasing τ_ik towards the upper bound u_k. The EB supermartingale
parameters λ_ik were then specified following the “predictable mixture” strategy
[BETTING, Section 3.2], truncated to be below 0.75.)

Intersection supermartingales tend to dominate Fisher pooling unless the
stratum selector is chosen poorly (e.g., the bottom-right panel of Figure 1 and
the last row of Table 2). Stratum selection with the lower-sided testing procedure
is about as efficient as proportional allocation for the ALPHA supermartingales,
but far more efficient than proportional allocation for EB. The biggest impact of
the allocation rule occurred for EB combined by intersection supermartingales
when the reported margin was 0.01 and the true margin was 0.1: proportional
allocation produced an expected workload of 752 cards, while lower-sided testing
produced an expected workload of 271 cards—a 74% reduction. Table 2 shows
that ALPHA-UB with intersection supermartingale combining and lower-sided
testing is the best method overall; ALPHA-UB with intersection combining and
proportional allocation is a close second; EB with intersection combining and
lower-sided testing is also relatively sharp; ALPHA-ST with Fisher combining is
least efficient.

4.2 Comparison to SUITE

For ALPHA, the mean PF∗ (Fischer)  is about half the SUITE P-value; for PM* (supermartingales), the mean is more than an order
of magnitude smaller than the SUITE P-value.


4.3 A highly stratified audit

As mentioned in Section 3.6, many within-stratum risk-measuring functions do
not yield tractable expressions for PF (θ) or PM (θ) as a function of θ, making
it hard to find the maximum P -value over the union unless K is small. Indeed,
previous implementations of SUITE only work for K = 2. 

However, the combined log-P-value for EB is linear in θ for PM* and piecewise linear for PF∗ .
Maximizing the combined log-P -value over the union of intersections is then a linear program
that can be solved efficiently even when K is large.

To demonstrate, we simulated a stratified ballot-polling audit of the 2020
presidential election in California, in which N = 17,500,881 ballots were cast
across K = 58 counties (the strata), using a risk limit of 5%.

Within-stratum P-values were combined using PF∗ (PM did not work well for EB with proportional allocation in simulations).

In 91% of the 300 runs, the audit stopped by the time 70,580 cards had been
drawn statewide. Drawing 70,580 ballots by our modified proportional allocation
rule produces within-county sample sizes ranging from 13 (Alpine County, with
the fewest voters) to 17,067 (Los Angeles County, with the most). A comparison
or hybrid audit using sampling without replacement would presumably require
inspecting substantially fewer ballots.

It took about 3.5 seconds to compute each
P-value in R (4.1.2) using a linear program solver from the lpSolve package.

## 5. Discussion

Our general recommendation for hybrid audits is: (i) use an intersection
supermartingale test with (ii) adaptive stratum selection and (iii) ALPHA-UB (or
another method that can exploit low sample variance to bet more aggressively) as
the risk-measuring function in the comparison stratum and (iv) ALPHA-ST (or a
method that “learns” the population mean) as the risk-measuring function in the
ballot-polling stratum. When the number of strata is large, audits can leverage the
log-linear form of the EB supermartingale to quickly find the maximum P-value,
as illustrated by our simulated audit spread across California’s 58 counties.

# Sequential stratified inference for the mean

2024	Sequential stratified inference for the mean
Jacob V. Spertus, Mayuri Sridhar, Philip B. Stark		September 11, 2024
https://github.com/spertus/UI-NNSMs
https://arxiv.org/abs/2409.06680        version 3 March 13, 2026

We develop conservative tests for the mean of a bounded population using data from
a stratified sample. The sample may be drawn sequentially, with or without replacement.
The tests are “anytime valid,” allowing optional stopping and continuation in each stratum.
We call this combination of properties SFSNP-valid (sequential, finite-sample, nonparametric)
The methods express a hypothesis about the population mean as a union of intersection hypotheses
describing within-stratum means.

* Finite-sample: bounded N
* nonparametric: dont assume some standard underlying distribution like gaussian ?
* sequential: Sequentially valid inference, or "anytime-valid" inference.
Traditional statistics require fixing the sample size (N) before collecting data. 
If one "peeks" at the data and stops early, they are more likely to find a false positive (type-I error). 
Sequentially valid methods eliminate this penalty, ensuring that "anytime validity is free" (no loss of power compared to fixed-sample tests)

1. Introduction

In broad brush, the new method works as follows: the “global” null hypothesis H0 : µ ≤ eta0 is
represented as a union of intersection hypotheses. Each intersection hypothesis specifies the mean in
every stratum and corresponds to a population that satisfies the apriori bounds and has mean not
greater than eta0 . The global null hypothesis is rejected if every intersection hypothesis in the union is
rejected. For a given intersection null, information about each within-stratum mean is summarized
by a test statistic that is a nonnegative supermartingale starting at 1 if the stratum mean is less
than or equal to its hypothesized value — a test supermartingale (TSM). Test supermartingales for
different strata are combined by multiplication and the combination is converted to a P-value for
the intersection null. We explore how the choice of test supermartingale and the interleaving of
samples across strata jointly affect the computational and statistical performance of the test.


The union-of-intersections framing was suggested in
Ottoboni et al. [2018] (SUITE) for sequential inference in RLAs and by Stark in 2019 (see
https://github.com/pbstark/Strat) for inference about stratified binary populations from fixed-
size samples.

2.1 Population and parameters. 

A population is a finite bag of real numbers X := { x_i }, i=1..N
For simplicity, we assume that each element of the population is in [0, 1]. 
The population mean is µ(X), and we want to test the global null hypothesis:

      H0 : µ(X ) ≤ eta0  for global null mean eta0.    ( eq 1)

A lower (1 − α) confidence bound is the largest eta0 for which H0 is not rejected at level α. 
If there are upper bounds for each element of X , an upper one-sided
test can be obtained by subtracting each element from its upper bound and then using a lower
one-sided test, mutatis mutandis.

Let N := [N1 , . . . , NK ] denote the vector of stratum sizes. The symbol X_N := (X_k), k=1..K
denotes a generic stratified population, a tuple of K bags with N_k items in the kth bag, X_k.
N = Sum{ N_k, k=1..K}.
The symbol X*_N := (X*_k), k=1..K denotes the true, unknown population.
The symbol ℵ_N represents all K-tuples of bags of numbers in [0, 1] such that the k th bag, X_k ,
has N_k items; that is, ℵ_N denotes all stratified [0, 1]-valued populations with the requisite
number of items in each stratum.

The vector of stratumwise means for population X_N is µ(X_N ) := [µ(X_1 ), ... , µ(X_K )], where µ(X_k) = Sum{ x_ki }/Nk.
The vector of stratum weights is w :=[w_1 , ... , wk ], where w_k := N_k /N .
The mean of a stratified population X_N is µ(X_N ) := w dot µ(X_N).
Let ℵ0_N := {X_N ∈ ℵ_N : µ(X_N ) ≤ eta0 } denote the set of null populations; 
the global null hypothesis can be written H0 : X*_N  ∈ ℵ0.
Let ℵ1_N := {X_N ∈ ℵ_N : µ(X_N ) >= eta0 } denote the alternative populations;
together ℵ0_N and ℵ1_N partition ℵ_N.

Let µ* := µ(X*_N) be the true global mean, and µ** := [µ*_1, ... , µ*_k ] be the true stratumwise means.
An intersection null hypothesis is the assertion µ** ≤ eta for the intersection null mean eta ∈ [0, 1]^K.
The global null hypothesis can be written as a union of intersection null hypotheses:

      H0 : U {eta ∈ E0 µ** ≤ eta}                      (eq 2)

where E0 := {ζ : w · ζ ≤ eta0 , 0 ≤ ζ ≤ 1} is the set of all intersection nulls for which the global null is true.

3.2(v.1) Sampling design

Recall that a fixed-size stratified sample consists of K independent (unordered) samples, where the sample from stratum k
is drawn by uniform random sampling with or without replacement. When draws are with replacement, the data are IID uniform
draws from Xk . When draws are without replacement, is uniform over all subsets of size nk from Xk.

Sequential stratified samples. Unlike fixed-size stratified samples, sequential stratified samples
have an order within and across strata, which necessitates a more detailed specification.

2.2 Sampling design

Recall that a fixed-size stratified sample produces K independent batches (one for each stratum) of data in no particular order, 
while a sequential stratified sample is ordered both within and across strata. Within stratum k , the data is a sequence
of random variables (X_ki ). When sampling without replacement, i can run from 1 to Nk,
and (X_ki), i=1..Nk is a random permutation of the stratum values {x_ki k=1..N_n}

An _interleaving_ of samples across strata is a stochastic process (Y_t) indexed by discrete
time t; Y^t := (Y_i)i=1..t is the t-prefix of (Y_i)i∈N.
An interleaving is characterized by within-stratum data and a _stratum selection S_t_ : the item in the t-th position in the interleaving comes
from stratum S_t. Let S^t := (S_i)^t i=1..t . The selection S_t must be predictable - it can depend on past data Y^(t−1)
but not on Y_i for i ≥ t, and may also involve auxiliary randomness.

For k ∈ {1..K}, define selection probabilities

      p_kt := Prob (S_t = k | Y^(t−1), S^(t−1) )     (eq 3)

and set p_t := [p_1t , ... , p_Kt ], t ∈ N.
If pt has one component equal to 1 and the rest equal to zero, the stratum selection is deterministic (conditional on the past). 
We refer to p_t as the stratum selector. To summarize, the stratum selection (St) is a stochastic process taking
values in {1..K}, while the stratum selector (pt) is a vector-valued process specifying a
categorical distribution for S_t, given the sampling history so far.

As of time t, the _sampling depth_ in stratum k is the number of items in Y^t that came from stratum k, denoted T_k(t).
Thus X_k^T_k(t) := (X_ki), i=1..T_k(t) are the data from stratum k at time t,
and the t-th item in the interleaving, Y_t = X_St,T_St(t) , is the T_St(t)-th item drawn from stratum S_t.
We can write Y^t = (X_Si,t,T_Si,t(i)), i=1..t

The selections can depend on the intersection null, in which case we augment the notation above. 
For instance, S^t(eta) is a sequence of selections for eta and Y^t(eta) is the corresponding interleaving.
The filtration F_t(eta) := σ(Y^t(eta), S^(t+1)(eta)) is the sigma-field representing everything known
up to time t at null eta.

2.3 Sequential hypothesis tests.

DEFINITION 1 (Stratified sequential P-value)

DEFINITION 2 (Test supermartingale (TSM))


2.3.1 Constructing TSMs from random samples.

Consider a single stratum with true mean µ*_k and null mean eta_k . We construct a process (M_kt)t∈N that is a TSM with respect to the
samples (X_kt )t∈N under the stratum null µ*_k ≤ eta_k . The conditional stratumwise null mean eta_kt
is the mean of the values remaining in X_k at time t if the null is true. (n_kt is "populationMeanIfH0")

In particular, we use the _Betting TSM_ (this is rlauxe betting risk-function):

      M_kt(eta_k) := Prod { (1 + λ_ki (X_ki − eta_ki )), i=1..T_k(t)) }  (eq 2.3.1) 


2.3.2 Intersection test supermartingales (I-TSMs)

Within-stratum TSMs can be combined across strata to form a P-value for an _intersection TSM (I-TSM)_:


      M_t(eta) := Prod { M_kt(eta_k), k=1..K } = Prod { Prod { Z_ki(eta_k), i=1..T_k(t))}, k=1..K  } = Prod { Z̃i }

where

      Z̃i = Z_Si,T_Si(i) (eta_S_i ) is the TSi(i)th term of the within-stratum TSM for stratum S_i

In other words, (Z̃_t) is an interleaving defined by the selections (S_t), in the same order as the
data interleaving (Y_t). Because the selections are predictable, the I-TSM is indeed a TSM for
the hypothesis µ∗ ≤ eta.

If an I-TSM uses betting TSMs in every stratum, it is a betting I-TSM

3. Stratified inference with TSMs.

3.1 Simple stratified inference: combining confidence bounds (LCB)

A lower confidence bound(LCB) for the population mean can be used to test the global null: reject if the LCB is greater
than the null.

3.2. Union-of-intersections test sequence (UI-TS)

DEFINITION 3 (Union-of-Intersections Test Sequence).

Recall the union-of-intersections form of the global null in Equation (2) and the definition
of E0 above. As shown in Section 2.3.2, we can test a particular eta ∈ E0 using an I-TSM.
We can reject H0 if the P-value for every eta ∈ E0 is less than α, i.e., if the smallest I-TSM
evaluated over E0 is at least 1/α.

      Mt := min max Mj(eta) = [max Pt(eta)]^-1 = max [max [Mj(eta)]^-1 ]^-1

is a UI-TS for H0 . When every Mt(eta) is a betting I-TSM, Mt is a betting UI-TS.

3.2.1. The boundary of E0. 

Minimizing over E0 is non-trivial. We restrict attention to within-stratum TSMs that are always monotone decreasing in eta_k .
Consequently, the minimum of Mt(eta) occurs on the boundary of E0, i.e., the set of points in the null that are closest to the alternative.

To formalize, let Ω_k be the set of all possible means µ_k in stratum k.
For example, if stratum k is binary, then Ω_k = {0, 1/N_k , . . . , (N_k − 1)/N_k, N_k }. 

Let Ω = Prod { Ω_k, k=1..K } be the Cartesian product containing all possible stratumwise means µ. 
The boundary of E0 is 
      B := { eta ∈ Ω : w dot eta ≤ eta0 and Ω ∋ ζ > eta =⇒ w dot ζ > eta0 }

Restricting to monotone TSMs, the value of eta that minimizes M_t(eta) is in B. Now define
      C := {eta : w dot eta = eta0 , 0 ≤ eta ≤ 1} ⊂ E0

Because the I-TSMs are componentwise monotone, optimizing over the set C rather than B
gives a conservative result, with little slack. This geometry influences the computational
properties of UI-TSs: in short, relaxing B to C greatly simplifies things. In what follows we always assume this relaxation.


**A function is monotonically decreasing if f(b) <= f(a) for all b > a.**

within-stratum TSMs that are always monotone decreasing in eta_k:

      M_kt(eta_k) < M_kt(eta_k') if eta_k < eta_k', for each k, (TODO what about each t?)

      M_kt(eta_k) := Prod { (1 + λ_ki (X_ki − eta_ki )), i=1..T_k(t)) }  (eq 2.3.1) 

If eta ∈ E0 \ B, then there is some point eta′ ∈ B with eta < eta′ for which we enforce that Mt (eta) uses the same
selections and bets as Mt (eta′). As result, Mt (eta′ ) < Mt (eta) for all t because of the monotonicity of the stratumwise
TSMs in eta_k.

I think that "we enforce that Mt(eta) uses the same selections and bets as Mt(eta′)" means that the monoticity is over eta, not t.

Then the question is eta_t = populationMeanIfH0(eta) ?< eta_t' = populationMeanIfH0(eta')

If N, sum and sample number are the same:

      eta_t <= eta_t'
      (N * eta - sum) / (N - sampleNum) <= (N * eta' - sum) / (N - sampleNum)
      eta  <= eta'

Then eta_t <= eta_t' if eta <= eta'

if λ_i, X_i are the same:

      M_t(eta) <= M_t(eta')
      Prod { (1 + λ_t (X_i − eta_t)) }, where (1 + λ_t (X_i − eta_t)) > 0
      1 + λ_i (X_i − eta_i) <= 1 + λ_i (X_i − eta_i')
      (X_i − eta_i) <= (X_i − eta_i')
      −eta_i <= -eta_i'
      eta_i > eta_i'

so M_t(eta) <= M_t(eta') if eta_i > eta_i', so M_t(eta) is monotonically decreasing in eta.


3.2.2. Global stopping time and global sample size. 

We distinguish between the _stopping time_ of a UI-TS and its _workload_, measured by the number of samples it requires to stop.
The stopping time of the level α test induced by UI-TS M_t at eta is 

      τ(M_t ; eta) := min{t ∈ N : M_t(eta) ≥ 1/α }

This is the number of overall samples needed for M_t(eta) to reject eta; and 

      τ_k(M_t ; eta) := T_k (τ(Mt ; eta), eta)

is the number of samples needed from stratum k. 

The _global stopping time_ is the sample size needed for the “last” I-TSM, considered on its own, to hit or cross 1/α:

      τ(M_t) := inf {t ∈ N : M_t ≥ 1/α} = sup eta∈C { Sum { τk(M_t ; eta), k=1..K } }

On the other hand, the _global sample size nτ_ is the total number of samples drawn across all
strata when H0 is rejected:

      n_τ(Mt) := Sum { sup eta∈C τ_k(Mt ; eta), k=1..K }

The sample size is the more relevant quality for designing a sequential stratified sample or
evaluating the efficiency of a UI-TS, because it quantifies the number of physical samples
required to stop. The stopping time bounds the sample size.

4. Desirable properties: consistency and efficiency.

4.1. Consistency. Loosely speaking, a sequential test is consistent if it eventually rejects
the global null when the global null is false.

DEFINITION 4 (Intersection consistency)
DEFINITION 5 (Global consistency)

We will call a UI-TS _consistent_ if it produces a globally consistent test. A UI-TS must grow
over the entire set of selections {St(eta)}eta∈C to be consistent, and this set is included in the
aforementioned stochastic aspects of the test. In theory, we can use any set of consistent
I-TSMs {Mt(eta)}eta∈C to construct a consistent UI-TS. In practice, C may be uncountably
infinite, so computing the UI-TS P -value Pt may be intractable unless the set {Mt (eta)}eta∈C
has some structure. Perhaps surprisingly, the simplest ways to structure {Mt (eta)}eta∈C —fix
the bets and selections over nulls, time, and strata—can easily yield inconsistent UI-TSs (see
Section A). While consistency is necessary for a useful test, it is not sufficient: a consistent
UI-TS may still require an impractically large sample size.

4.2. Efficiency. 

Practically useful tests should keep n_τ relatively small when the null
is false. There is substantial subtlety lurking here because: (a) the alternative is usually not
a single known distribution (a simple alternative) but a set of distributions (a composite
alternative); (b) nτ is a random variable and it is not given which features of its distribution
(median, expected value, etc) should be minimized; (c) for a UI-TS, n_τ can have a complicated
structure depending on the underlying I-TSMs.

Recall that Lemma 1 relates the sample size and stopping time as τ ≤ n_τ ≤ Kτ for general
UI-TSs. It is difficult to find the UI-TS that minimizes E[nτ ] over all possible bets and
selections, even for a simple alternative XN ∈ ℵ1_N . We start with the more modest goal of
constructing a UI-TS that minimizes the expected stopping time EV[τ] under a simple alternative.
These tests are called _stopping-time optimal (STO)_.

STO UI-TSs can be constructed as the minimum of a collection of Kelly-optimal I-TSMs.
We also consider constraining the UI-TS to use the same stratum selections (St ) for all eta ∈ E0 , in which case nτ = τ by Lemma 1.
We call such UI-TSs STO for (St).

DEFINITION 6 (Efficiency at XN)
DEFINITION 7 (Kelly-optimal betting I-TSM for bets and selections)
DEFINITION 8 (Kelly-optimal betting I-TSM conditional on S^t )

4.2.1. Approximate Kelly optimality under the intersection-composite alternative.

We can use a predictable approximation of the Kelly-optimal
strategy using past data, “approximate growth rate adapted to the particular alternative”
(AGRAPA) [Waudby-Smith and Ramdas, 2024].

4.2.2. Other betting strategies.

We propose two additional betting strategies that, when
paired with fixed stratum selections St := St (η), are more tractable computationally than
AGRAPA and other approximately Kelly-optimal bets.

The first strategy sets λ_t(eta) := λt . Such bets may vary over time as a predictable function 
of past data, but must be identical for all I-TSMs {M_t(eta), eta∈C} . 
The predictable plug-in strategy λP_kt := 1 ∧ sqrt(2*log(2/α)/(σ̂_k(t−1)^2 * t * logt),
recommended by Waudby-Smith and Ramdas[2024], is an instance of a fixed bet when truncated to [0, 1] rather than [0, 1/eta_k].

The second strategy is λInverse = λI_kt(eta) := c_kt / eta_k , where c_kt is a predictable tuning parameter. 
An inverse bet is valid as long as 0 ≤ c_kt ≤ 1 and eta_k > 0.

To set c_kt, note that for any given null eta_k, it is sensible to bet more when
the true mean µ_k is larger and the standard deviation σ_k is smaller. This suggests setting
c_kt := l_k ∨ (µ̂_k(t−1) − σ̂_k(t−1)) ∧ u_k , where µ_̂k(t−1) and σ̂_k(t−1) are predictable estimates of
the true mean and standard deviation.

The limits l_k, u_k ∈ [0, 1) are user-chosen truncation parameters ensuring the bets are valid. As
defaults, we recommend setting l_k = 0.1 and u_k = 0.9 so that some amount is always wagered
but the I-TSMs cannot go broke.

λI_kt(eta) produces a set of I-TSMs that are convex over eta ∈ C, making the UI-TS more computationally tractable.

5. Computation

In principle, the results of Section 4.2.1 allow one to construct an
approximately optimal I-TSM for every eta ∈ C ; Lemma 3 then constructs a STO UI-TS under
the alternative µ⋆ > eta0 . In practice, this can be infeasible to implement. The tractability of the
minimization depends on the constituent I-TSMs and how they vary over eta ∈ C . We classify
I-TSMs accordingly. The term _eta-aware_ refers to betting and selection strategies that depend on eta;
_eta-oblivious_ refers to strategies that do not. An I-TSM or UI-TS is called _eta-oblivious_ only
if all its selectors and bets are _eta-oblivious_. We now describe a few strategies for computation
under different population sizes and numbers of strata.

* Small K, small N, discrete support
* Small K
* Moderate K
* Arbitrary K

6. Application to risk-limiting audits.

6.1. Point masses: error-free card-level comparison audits. 

Using the stratified CCA parametrization in SWEETER, we constructed populations with stratum
sizes N = [200, 200] and identical values x_ik := µ_k = 1/2 for i ∈ {1, . . . , 200} and k ∈ {1, 2},
representing ballots with error-free reference values. The within stratum nulls were defined
as eta_k := (1 − θ_k − Āc_k)/2 where Āc_k was the stratumwise reported assorter mean - the share
of votes for the winner - and θ ∈ {θ : w dot θ = 1/2, 0 ≤ θ ≤ 1}. Letting Āc := [Āc_1 , Āc_2], the
global reported assorter mean Ā_c := w dot Ā ranged between 0.51 and 0.75, corresponding to
electoral margins between 2% and 50%. The gap between stratumwise assorter means was
either Āc_2 − Āc_1 = 0 or Āc_2 − Āc_1 = 0.5.

For each population, we found the (deterministic) sample sizes for LCBs and UI-TSs.
The LCBs were constructed with no correction of the stratum-wise LCBs for multiplicity
(e.g., Šidák’s correction) in order to lower bound the sample sizes required by any valid LCB
strategy. The UI-TSs were constructed using the banding strategy discussed in Section 5,
partitioning C into G ∈ {1, 3, 10, 100, 500} equal-length segments and evaluating the I-TSMs
at the endpoints. Bets were AGRAPA, predictable mixture, or inverse. The selection rule was
eta-oblivious, nonadaptive round-robin, an eta-aware and adaptive “predictable Kelly” strategy
using a UCB-style algorithm on every eta ∈ C, or an eta-oblivious “greedy” strategy using the
UCB-style algorithm iteratively on the minimizing eta at time (t − 1) to produce a single
interleaving for all I-TSMs.

Figure 3 gives sample sizes for G = 100. UI-TSs are generally more efficient than LCBs. 
The difference is substantial (the y-axis is on the log scale)
except when neither the bets nor selections are eta-aware (e.g., predictable plug-in bets with
round robin selection). Despite having approximately optimal stopping times, the predictable
Kelly selection rule led to unnecessarily large sample sizes because it used different selections
for each intersection null. 


UI-TS with either Agrapa or inverse; Greedy selection or round robin.

v1.7.1

We ran each combination of inference method, betting strategy, and selection strategy on each
population and recorded the (deterministic) stopping time.

inference method = LCB or *UI-TS
betting strategy = AGRAPA, predictable mixture, or inverse
selection strategy (select next stratum)
    * eta-oblivious, nonadaptive round-robin
    * eta-aware and adaptive “predictable Kelly” strategy using a UCB-style algorithm on every eta ∈ C
    * eta-oblivious “greedy” strategy using the UCB-style algorithm iteratively on the minimizing eta at time (t − 1) to produce a single interleaving for all I-TSMs.

inference: *UI-TSs are generally more efficient than LCBs
betting: agrapa or *inverse
selection: Greedy selection and *round robin performed similarly, except when the stratum gap was large and the bets were fixed



7. Conclusions

A UI-TS is not necessarily a TSM, but is an E -process for
the global null. UI-TSs can require substantially smaller samples than the simple approach of
combining lower confidence bounds (LCBs), but their computational cost is generally higher.

We presented stopping time optimal (STO) UI-TSs, and constructed them from Kelly-
optimal I-TSMs. Because STO targets stopping time (the sample size for the hardest-to-reject
intersection null) rather than the overall sample size required to reject every intersection null,
the STO UI-TS is not necessarily the most efficient UI-TS: there is generally a gap up to a
factor of K . We navigated this issue by evaluating selection strategies that are fixed over the
union: round-robin or a “greedy” UCB-style selection that predictably maximizes the expected
growth of the previously smallest I-TSM. These were generally sharper than the unconditional
STO UI-TS.

7.1. When stratification helps. 

Stratified sampling and inference with a UI-TS can be
sharper than unstratified sampling and inference with a TSM.

////////////////////////////////////////////////////////////
https://github.com/spertus/UI-TS notes

It appears this repo is used for both SliceDice (SF_oneaudit_example.ipynb) and Stratified.

Stratified
these reference original paper, not v3:

- `utils.py` contains all functions implementing the proposed methods
- `test.py` contains unit test for all functions
- `significance_simulations.py` runs the t-test simulation that generated Figure 1  (Estimated significance level...)
- `pointmass_simulations.py` runs the pointmass simulations presented in Section 7.1 (pointmass)
- `bernoulli_simulations.py` runs the Bernoulli simulations presented in Section 7.2 (bernoulli)
- `gaussian_simulations.py` runs the Gaussian simulations presented in Section 7.3 (gaussian)
- `pgd_time.py` times the convex UI-TS against the LCB method, as described in Section C.4 (Inverse betting computation times)
- `r_plots.R` contains the R code used to generate all plots in the paper


| name                         | original | v3    |
|------------------------------|----------|-------|
| Estimated significance level | Fig 1    | Fig 1 |
| Sample sizes pointmass       | Fig 4    | Fig 3 |
| Bernoulli distribution       | Fig 5    | Fig 4 |
| Gaussian distributions       | Fig 6    |       |
| Stopping times..             | Fig 7    | Fig 5 |

org
Figure 4: Sample sizes (y-axis, log scale) for various sequential stratified tests (line colors and types)
of the global null H0 : µ⋆ ≤ 1/2 for 2-stratum point-mass alternatives with varying global means
(x-axis) and between-stratum spread (columns). For example, when the global mean is 0.6 and the
spread is 0.5, the within-stratum means are µ⋆ = [0.35, 0.85]. UI-TSs were computed using the
banding strategy, with G = 100. Note that the round robin (blue) and greedy Kelly (red) lines
overlap in the left column panels. All methods assumed sampling was with replacement, but sample
sizes were capped at 400: a sample size of 400 is technically 400 or greater. LCB = lower confidence
bound; UI-TS = union-intersection test sequence.

v3
FIG 3. Sample sizes (y-axis, log scale) for various sequential stratified tests (line colors and types) of 2-stratum,
error-free, card-level comparison audit populations with varying global reported assorter means (x-axis) and
spread between the within-stratum reported assorter means (columns). For example, a global reported assorter
mean of 0.6 and stratum gap of 0.5 means Āc = [0.35, 0.85]. UI-TSs were computed using the banding strategy,
with G = 100. All methods assumed sampling was with replacement, but sample sizes were capped at 400: a
sample size shown as 400 is really 400 or greater. LCB = lower confidence bound; UI-TS = union-intersection test
sequence.