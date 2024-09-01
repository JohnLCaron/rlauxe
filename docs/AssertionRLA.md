# assertion-RLA.ipynb


## Overview of the assertion audit tool

The tool requires as input:

+ audit-specific and contest-specific parameters, such as
    - whether to sample with or without replacement
    - the name of the risk function to use, and any parameters it requires
    - a risk limit for each contest to be audited
    - the social choice function for each contest, including the number of winners
    - candidate identifiers for each contest
    - reported winner(s) for each contest
    - an upper bound on the number of ballot cards that contain each contest
    - an upper bound on the total number of cards across all contests
    - whether to use card style information to target sampling
+ a ballot manifest (see below)
+ a random seed
+ a file of cast vote records (for ballot-comparison audits)
+ reported vote tallies for for each contest (for ballot-polling audits of plurality, supermajority, and approval social choice functions)
+ json files of assertions for IRV contests (one file per IRV contest)
+ human reading of voter intent from the paper cards selected for audit

The tool helps select cards for audit, and reports when the audit has found sufficiently strong evidence to stop.

The tool exports a log of all the audit inputs except the CVR file, but including the auditors' manually determined voter intent from the audited cards.


### phantoms-to-evil-zombies

The `use_style` parameter controls whether the sample is drawn from all cards (`use_style == False`) or card style information is used
to target the cards that purport to contain each contest (`use_style == True`).
In the current implementation, card style information is inferred from cast-vote records, with additional 'phantom' CVRs 
if there could be more cards that contain a contest than is accounted for in the CVRs.
Errors in the card style information are treated conservatively using the  "phantoms-to-evil-zombies" (~2EZ) approach 
([Banuelos & Stark, 2012](https://arxiv.org/abs/1207.3413)) so that the risk limit remains valid, even if the CVRs misrepresent
which cards contain which contests.

The two ways of sampling are treated differently.
If the sample is to be drawn only from cards that--according to the CVR--contain a particular contest, and a sampled card turns out not to
contain that contest, that is considered a discrepancy, dealt with using the ~2EZ approach.
It is assumed that every CVR corresponds to a card in the manifest, but there might
be cards cast in the contest for which there is no corresponding CVR. In that case,
phantom CVRs are created to ensure that the audit is still truly risk-limiting.

Given an independent (i.e., not relying on the voting system) upper bound on the number of cards that contain the contest, 
if the number of CVRs that contain the contest does not exceed that bound, we can sample from paper purported to contain 
the contest and use the ~2EZ approach to deal with missing CVRs. This can greatly increase the efficiency of the audit if
some contests appear on only a small percentage of the cast cards ([Glazer, Spertus, and Stark, 2021](https://dl.acm.org/doi/10.1145/3457907)).
If there are more CVRs than the upper bound on the number of cards, extra CVRs can be deleted provided
that deletion does not change any contest outcome. See [Stark, 2022](https://arxiv.org/abs/2207.01362).
(However, if there more CVRs than cards, that is evidence of a process failure.)

Any sampled phantom card (i.e., a card for which there is no CVR) is treated as if its CVR is a non-vote (which it is), 
and as if its MVR was least favorable (an "evil zombie" producing the greatest doubt in every assertion, separately). 
Any sampled card for which there is a CVR is compared to its corresponding CVR.
If the card turns out not to contain the contest (despite the fact that the CVR says it does), 
the MVR is treated in the least favorable way for each assertion (i.e., as a zombie rather than as a non-vote).


### Internal workflow

+ Read overall audit information (including the seed) and contest information
+ Read assertions for IRV contests and construct assertions for all other contests
+ Read ballot manifest
+ Read cvrs; Every CVR should have a corresponding manifest entry.
+ Prepare ~2EZ:
    - `N_phantoms = max_cards - cards_in_manifest`
    - If `N_phantoms < 0`, complain
    - Else create `N_phantoms` phantom cards
    - For each contest `c`:
        + `N_c` is the input upper bound on the number of cards that contain `c`
        + if `N_c is None`, `N_c = max_cards - non_c_cvrs`, where `non_c_cvrs` is #CVRs that don't contain `c`
        + `C_c` is the number of CVRs that contain the contest
        + if `C_c > N_c`, complain
        + else if `N_c - C_c > N_phantoms`, complain
        + else:
            - Consider contest `c` to be on the first `N_c - C_c` phantom CVRs
            - Consider contest `c` to be on the first `N_c - C_c` phantom ballots
+ Create Assertions for every Contest. This involves also creating an Assorter for every Assertion, and a `NonnegMean` test
  for every Assertion.
+ Calculate assorter margins for all assorters:
    - If `not use_style`, apply the Assorter to all cards and CVRs, including phantoms
    - Else apply the assorter only to cards/cvrs reported to contain the contest, including phantoms that contain the contest
+ Set `assertion.test.u` to the appropriate value for each assertion: `assorter.upper_bound` for polling audits or
  `2/(2-assorter.margin/assorter.upper_bound)` for ballot-level comparison audits
+ Estimate starting sample size for the specified sampling design (w/ or w/o replacement, stratified, etc.), for chosen risk function, use of card-style information, etc.:
    - User-specified criterion, controlled by parameters. Examples:
        + expected sample size for completion, on the assumption that there are no errors
        + 90th percentile of sample size for completion, on the assumption that errors are not more frequent than specified
    - If `not use_style`, base estimate on sampling from the entire manifest, i.e., smallest assorter margin
    - Else use consistent sampling:
        + Augment each CVR (including phantoms) with a probability of selection, `p`, initially 0
        + For each contest `c`:
            - Find sample size `n_c` that meets the criterion
            - For each non-phantom CVR that contains the contest, set `p = max(p, n_c/N_c)`
        + Estimated sample size is the sum of `p` over all non-phantom CVRs
+ Draw the random sample:
    - Use the specified design, including using consistent sampling for style information
    - Express sample cards in terms of the manifest
    - Export
+ Read manual interpretations of the cards (MVRs)
+ Calculate attained risk for each assorter
    - Use ~2EZ to deal with phantom CVRs or cards; the treatment depends on whether `use_style == True`
+ Report
+ Estimate incremental sample size if any assorter nulls have not been rejected
+ Draw incremental sample; etc


# Audit parameters.

The overall audit involves information that is the same across contests:

* `seed`: the numeric seed for the pseudo-random number generator used to draw sample (for SHA256 PRNG)
* `sim_seed`: seed for simulations to estimate sample sizes (for Mersenne Twister PRNG)
* `quantile`: quantile of the sample size to use for setting initial sample size
* `cvr_file`: filename for CVRs (input)
* `manifest_file`: filename for ballot manifest (input)
* `use_style`: Boolean. If True, use card style information (inferred from CVRs) to target samples. If False, sample from all cards, regardless of the contest.
* `sample_file`: filename for sampled card identifiers (output)
* `mvr_file`: filename for manually ascertained votes from sampled cards (input)
* `log_file`: filename for audit log (output)
* `error_rate_1`: expected rate of 1-vote overstatements. Recommended value $\ge$ 0.001 if there are hand-marked ballots. 
      Larger values increase the initial sample size, but make it more likely that the audit will conclude after a single round even if the audit finds errors
* `error_rate_2`: expected rate of 2-vote overstatements. 2-vote overstatements should be extremely rare.
      Recommended value: 0. Larger values increase the initial sample size, but make it more likely that the audit will conclude after a single round even if the audit finds errors
* `reps`: number of replications to use to estimate sample sizes. If `reps is None`, uses a deterministic method
* `quantile`: quantile of sample size to estimate. Not used if `reps is None`
* `strata`: a dict describing the strata. Keys are stratum identifiers; values are dicts containing:
    + `max_cards`: an upper bound on the number of pieces of paper cast in the contest. This should be derived independently of the voting system. A ballot consists of one or more cards.
    + `replacement`: whether to sample from this stratum with replacement.
    + `use_style`: True if the sample in that stratum uses card-style information.
    + `audit_type` one of Contest.POLLING, Contest.CARD_COMPARISON, Contest.BATCH_COMPARISON but only POLLING and CARD_COMPARISON are currently implemented.
    + `test`: the name of the function to be used to measure risk. Options are `kaplan_markov`,`kaplan_wald`,`kaplan_kolmogorov`,`wald_sprt`,`kaplan_mart`, `alpha_mart`, `betting_mart`.
        Not all risk functions work with every social choice function or every sampling method.
    + `estim`: the estimator to be used by the `alpha_mart` risk function. Options:
        - `fixed_alternative_mean` (default)
        - `shrink_trunc`
        - `optimal_comparison`
    + `bet`: the method to select the bet for the `betting_mart` risk function. Options:
        - `fixed_bet` (default)
        - `agrapa`
    + `test_kwargs`: keyword arguments for the risk function

----

* `contests`: a dict of contest-specific data
    + the keys are unique contest identifiers for contests under audit
    + the values are Contest objects with attributes:
        - `risk_limit`: the risk limit for the audit of this contest
        - `cards`: an upper bound on the number of cast cards that contain the contest
        - `choice_function`: `PLURALITY`, `SUPERMAJORITY`, or `IRV`
        - `n_winners`: number of winners for majority contests. (Multi-winner IRV, aka STV, is not supported)
        - `share_to_win`: for super-majority contests, the fraction of valid votes required to win, e.g., 2/3.
             (share_to_win*n_winners must be less than 100%)
        - `candidates`: list of names or identifiers of candidates
        - `reported_winners` : list of identifier(s) of candidate(s) reported to have won. Length should equal `n_winners`.
        - `assertion_file`: filename for a set of json descriptors of Assertions (see technical documentation) that 
              collectively imply the reported outcome of the contest is correct. Required for IRV; ignored for other social choice functions
        - `audit_type`: the audit strategy. Currently `POLLING (ballot-polling)`, `CARD_COMPARISON` (ballot-level comparison audits), and `ONEAUDIT`
               are implemented. HYBRID and STRATIFIED are planned.
        - `test`: the risk function for the audit. Default is `NonnegMean.alpha_mart`, the alpha supermartingale test
        - `estim`: estimator for the alternative hypothesis for the test. Default is `NonnegMean.shrink_trunc`
        - `use_style`: True to use style information from CVRs to target the sample. False for polling audits or for sampling from all ballots for every contest.
        - other keys and values are added by the software, including `cvrs`, the number of CVRs that contain the contest, 
           and `p`, the sampling fraction expected to be required to confirm the contest
      

# How many more cards should be audited?

Estimate how many more cards will need to be audited to confirm any remaining contests. The enlarged sample size is based on:

* cards already sampled
* the assumption that we will continue to see errors at the same rate observed in the sample