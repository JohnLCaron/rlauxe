
2. Stratified audits

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
nonnegative supermartingales (NNSMs): intersection supermartingales, which
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


