Im investigating using round-robin stratum selector and η-oblivious betting (or perhaps η-aware inverse betting) for arbitrary K strata. 

The first version of the "Sequential stratified inference" mentioned using "using solvers.cp from version 1.3.2 of the cvxpy module" for inverse betting. Im wondering if the code driving that is available? It would be helpful to understand how the constraints are formulated.

Im also looking at https://github.com/cobyqa/cobyqa which "is a derivative-free optimization solver designed to supersede COBYLA."

Any thoughts appreciated.

    E0 := {ζ : w · ζ ≤ η0 , 0 ≤ ζ ≤ 1}

The boundary of E0 is

    B := {η ∈ Ω : w · η ≤ η0 and Ω ∋ ζ > η =⇒ w · ζ > η0 }.

Futhermore, define
    
    C := {η : w · η = η0 , 0 ≤ η ≤ 1} ⊂ E0 .

Because of the componentwise monotonicity, optimizing over the set C rather than B gives a
conservative result. In what follows, we will generally define Mt = min _η∈C { Mt (η) }

which means w · ζ = η0, and we are minimizing over all η∈C.

# Stratified audits

For contests that span counties, the question is how to combine the county results.

Each county is a stratum, and each contest in the stratum has its own sample size, namely the count of sampled ballots
that have that contest on it.

The contests are independent of each other (except that some appear together on the same sampled ballots). So we can see this as
independent risk measuring audits, one for each contest that spans contests.

Im wondering if we have a "fixed-size stratified audit" because all the counties have already done their sampling, and those sample sizes are fixed?

The Alpha paper describes SUITE:

"When the sample is stratified, what is needed is an inference about the number of votes
in the stratum for each candidate. To solve that problem, [SUITE] used a test
in the polling stratum based on the multinomial distribution, maximizing the P-value over a
nuisance parameter, the number of ballot cards in the stratum with no valid vote for either
candidate. SUITE represents the hypothesis that the outcome is wrong as a union of inter-
sections of hypotheses. The union is over all ways of partitioning outcome-changing errors
across strata. The intersection is across strata for each partition in the union. For each par-
tition, for each stratum, SUITE computes a P-value for the hypothesis that the error in that
stratum exceeds its allocation, then combines those P-values across strata (using a combin-
ing function such as Fisher’s combining function) to test the intersection hypothesis that the
error in every stratum exceeds its allocation in the partition. If the maximum P-value of that
intersection hypothesis over all allocations of outcome-changing error is less than or equal
to the risk limit, the audit stops."

The Alpha paper describes SHANGRLA:

"[SHANGRLA] extends the union-intersection approach to use
SHANGRLA assorters, avoiding the need to maximize P-values over nuisance parameters
in individual strata and permitting sampling with or without replacement"

SHANGRLA says:

"The central idea of the approach taken in SUITE [16] can be used with SHANGRLA to
accommodate stratified sampling and to combine ballot-polling and ballot-level compari-
son audits: Look at all allocations of error across strata that would result in an incorrect
outcome. Reject the hypothesis that the outcome is incorrect if the maximum P -value
across all such allocations is less than the risk limit.

SHANGRLA will generally yield a sharper (i.e., more efficient) test than SUITE, because
it deals more efficiently with ballot cards that do not contain the contest in question,
because it avoids combining overstatements across candidate pairs and across contests,
and because it accommodates sampling without replacement more efficiently.

With SHANGRLA, whatever the sampling scheme used to select ballots or groups of
ballots, the underlying statistical question is the same: is the average value of each assorter
applied to all the ballot cards greater than 1/2?"

and then describes the math needed for a stratified audit.

ALPHA has:

"5.1. ALPHA obviates the need to use a combining function across strata. Because ALPHA
works with polling and comparison strategies, it can be the basis of the test in every stratum,
whereas SUITE used completely different “risk measuring functions” for strata where
the audit involves ballot polling and strata where the audit involves comparisons. We shall see
that this obviates the need to use a combining function to combine P-values across strata: the
test supermartingales can just be multiplied, and the combined P-value is the reciprocal of
their product. This is because (predictably) multiplying terms in the product representation of
different sequences—each of which, under the nulls in the intersection, is a nonnegative su-
permartingale starting at one—yields a nonnegative supermartingale starting at one. Thus the
product of the stratum-wise test statistics in any order (including interleaving terms across
strata) is also a test statistic with the property that the chance it is greater than or equal to
1/α is at most α under the intersection null. Because Fisher’s combining function adds two
degrees of freedom to the chi-square distribution for each stratum, avoiding the need for
a combining function can substantially increase power as the number of strata grows. 

Table 1 illustrates this increase: it shows the combined P-value for the intersection hypothesis
when the P-value in each stratum is 0.5. The number of strata ranges from 2—which might
arise in an audit in a single jurisdiction when stratifying on mode of voting (in-person ver-
sus absentee)—to 150—which might arise in auditing a cross-jurisdictional contest in a state
with many counties. For instance, Georgia has 159 counties, Kentucky has 120, Texas has
254, and Virginia has 133.
"

and then describes the math needed for a stratified audit, which looks different, or at least
more detailed, than SHANGRLA.

ALPHA uses a stratum selector to decide which stratum to sample next when combining; presumably we could continue
to use that algorithm as long as we arent peeking ahead.

ALPHA has:

"In general, the power of the test of the intersection null will depend on the stratum selector
S(·), which can be adaptive. For instance, if data from stratum s suggest that θs ≤ µs , fu-
ture values of S(i) might omit stratum s or sample from s less frequently, instead sampling
preferentially from strata where there is some evidence that the intersection null is false,
to maximize the expected rate at which the test supermartingale grows, minimizing the P -
value. Indeed, for a fixed µ, choosing S(i) can be viewed as a (possibly finite-population)
multi-armed bandit problem: which stratum should the next sample come from to maxi-
mize the expected rate of growth of the test statistic?

An additional complication is that we want fast growth for all vectors µ of stratumwise means for which the population mean
µ̃ ≤ 1/2. Importantly, different stratum selectors can be used for different
values of µ; this flexibility is explored by Spertus and Stark (2022) [SWEETER].
"

SWEETER has:

"[ALPHA] provided a new approach to union-intersection tests using
nonnegative supermartingales (NNSMs): _intersection supermartingales_, which
open the possibility of reducing sample sizes by adaptive stratum selection (using
the first t sampled cards to select the stratum from which to draw the (t+1)th
card). [ALPHA] does not provide an algorithm for stratum selection or evaluate
the performance of the approach; this paper does both."

SWEETER has lots more detail and refinements on Stratified comparison audits, and the use of stratum selection
(example round-robin and adaptive):

"The use of sequential sampling in combination with stratification presents a new
possibility for reducing workload: sample more from strata that are providing
evidence against the intersection null and less from strata that are not helping.
Perhaps suprisingly, such adaptive sampling yields valid inferences when the
P-value is constructed from supermartingales and the stratum selection function
depends only on past data."

STRATIFIED continues to elaborate on stratum selection, sequential inference, and searching for maximum P-values.  

"[Sweeter] investigated sample sizes for a range of combining functions, TSMs, and selection strategies for stratified comparison audits.
The present contribution can be viewed as a set of methods for rigorous inference in a nonpara-
metric problem with a multi-dimensional nuisance paramete"

All of these emphasis minimizing sample sizes; are there simplifications if all the sampling is already done?


//////////////////////////////////////////////////////////////////////////////////////

Im guessing we have a fixed-size stratified audit whose overall risk can be measured with
a union of intersection hypotheses. That is, we dont need sequential stratified sampling to interleave
the audits, because we dont control what samples are made (?)

The first version of the stratified paper has:

"In broad brush, the new method works as follows: the “global” null hypothesis H0 : µ ≤ η0 is
represented as a union of intersection hypotheses. Each intersection hypothesis specifies the mean in
every stratum and corresponds to a population that satisfies the a priori bounds and has mean not
greater than η0 . The global null hypothesis is rejected if every intersection hypothesis in the union is
rejected. For a given intersection null, information about each within-stratum mean is summarized
by a test statistic that is a nonnegative supermartingale starting at 1 if the stratum mean is less
than or equal to its hypothesized value — a test supermartingale (TSM). Test supermartingales for
different strata are combined by multiplication and the combination is converted to a P-value for
the intersection null."

OTOH, the Alpha paper clearly favors sequential selection

////////////////////////////
eta0 = 0.5
u = 2

wk = 0.370, 0.630, mu_k = 0.400, 0.700,
eta_1_grid = 0.000, 0.135, 0.270, 0.405, 0.540, 0.675, 0.810, 0.945, 1.080, 1.215, 1.350,  // mu_k = .4
eta_2_grid = 0.794, 0.715, 0.635, 0.556, 0.476, 0.397, 0.318, 0.238, 0.159, 0.079, 0.000,  // mu_k = .7
this is a grid of assorter means

what is this?

    // transformed overstatement assorters
    beta1 = (1 + eta1 - mu[0]) / 2  // transformed null means in stratum 1
    beta2 = (1 + eta2 - mu[1]) / 2  // transformed null means in stratum 2

beta_1_grid = 0.300, 0.368, 0.435, 0.503, 0.570, 0.638, 0.705, 0.773, 0.840, 0.908, 0.975,
beta_2_grid = 0.547, 0.507, 0.468, 0.428, 0.388, 0.349, 0.309, 0.269, 0.229, 0.190, 0.150,

then the grid endpoints go from beta1 to beta2: WRONG confused with Kpoint

    val beta_grid: Pair(beta1, beta2)

    band i:
        val startpoint = beta_grid[i]
        val endpoint = beta_grid[i+1]
        val centroid = doubleArrayOf( (startpoint.first + endpoint.first)/2, (startpoint.second + endpoint.second)/2 )

fun mean2margin(mean: Double) = 2.0 * mean - 1.0
noerror = 1 / (2 - assorterMargin)
noerror = 1 / (2 - (2 * mean - 1))
noerror = 1 / (3 - 2 * mean)
(3 - 2 * mean) = 1/noerror
mean = (3 - 1/noerror)/2

beta = (eta + 1 - mu[0]) / 2
overstatement = 

bassort = (1-o/u)*noerror = tau * noerror
where
    o = overstatement error
    u = assorter upper bound
    v = reported assorter margin
    tau = (1-o/u)
    noerror = 1/(2-v/u)

taus=(1-o/u)= [0, u12, 1-u12, 1, 2-u12, 1+u12, 2] where u12= 1/2u
taus=(1-o/u)= [0, .5, 1, 1.5, 2] when u = 1
upper bound of bassort is 2*noerror


        val eta_1_grid = numpy_linspace(start = max(0.0, eta_0 - wk[1]), end = min(u, eta_0/wk[0]), npts = n_bands + 1) // 1D List
start = max(0.0, eta_0 - wk[1])
start = max(0.0, 0.5 - 0.63) = 0

end = min(u, eta_0/wk[0])
end = min(2, .5/.370) = 1.35

        val eta_2_grid = eta_1_grid.map { (eta_0 - wk[0] * it) / wk[1]} // 1D List
(eta_0 - wk[0] * it) / wk[1]


bands
startpoint=(0.30000, 0.54706, ), mu=0.45555555555555555
centroid=(0.33375, 0.52721, ), mu=0.4555555555555556
endpoint=(0.36750, 0.50735, ), mu=0.4555555555555556

//////////////////////////////////////////////////////

The within stratum nulls were defined
as etak := (1 − θk − Āck )/2 where Āck was the stratumwise reported assorter mean—the share of votes for the winner
and θ ∈ {θ : w · θ = 1/2, 0 ≤ θ ≤ 1} are the null means with constraint w · θ = 1/2

    val wk = Nk.map { it / N.toDouble() }
    val reportedMean = numpy_dotDD(wk, Ack)
    

w · θ = 1/2 = eta0
1/2 = wk1 * theta1 + wk2*theta2
(1/2 - wk1*theta1) = wk2 * theta2
(1/2 - wk1*theta1)/wk2 = theta2

theta1 is a grid around Ak1; then theta2 must = (eta0 - wk1 * theta1) / wk2, so

    val eta_2_grid = eta_1_grid.map { (eta_0 - wk[0] * it) / wk[1]} // 1D List

so thats the eta grid. The within stratum nulls are 

nullk := (1 − thetak − Āck )/2

but we have the wrong sign for etak:

betak = (etak + 1 - Ack) / 2 

        // eta_1_grid = np.linspace(max(0, eta_0 - w[1]), min(u, eta_0/w[0]), n_bands + 1)
        val eta_1_grid = numpy_linspace(start = max(0.0, eta_0 - wk[1]), end = min(u, eta_0/wk[0]), npts = n_bands + 1) // 1D List

        // val eta_2_grid = (eta_0 - w[0] * eta_1_grid) / w[1]
        val eta_2_grid = eta_1_grid.map { (eta_0 - wk[0] * it) / wk[1]} // 1D List

    // transformed overstatement assorters
    betak = (etak + 1 - Ac[k]) / 2 //  transformed null means in stratum 1


