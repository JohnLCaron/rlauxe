package org.cryptobiotic.rlauxe.shangrla

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.estimate.RunTestRepeatedResult
import org.cryptobiotic.rlauxe.estimate.runTestRepeated
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

// SHANGRLA examples/assertion-RLA.ipynb
// this is an IRV contest, from SF DA race in 2019

//# Assertion RLA
//## Overview of the assertion audit tool
//
//The tool requires as input:
//
//+ audit-specific and contest-specific parameters, such as
//    - whether to sample with or without replacement
//    - the name of the risk function to use, and any parameters it requires
//    - a risk limit for each contest to be audited
//    - the social choice function for each contest, including the number of winners
//    - candidate identifiers for each contest
//    - reported winner(s) for each contest
//    - an upper bound on the number of ballot cards that contain each contest
//    - an upper bound on the total number of cards across all contests
//    - whether to use card style information to target sampling
//+ a ballot manifest (see below)
//+ a random seed
//+ a file of cast vote records (for ballot-comparison audits)
//+ reported vote tallies for for each contest (for ballot-polling audits of plurality, supermajority, and approval social choice functions)
//+ json files of assertions for IRV contests (one file per IRV contest)
//+ human reading of voter intent from the paper cards selected for audit
//
//`use_style` controls whether the sample is drawn from all cards (`use_style == False`) or card style information is used
//to target the cards that purport to contain each contest (`use_style == True`).
//In the current implementation, card style information is inferred from cast-vote records, with additional 'phantom' CVRs if there could be more cards that contain a contest than is accounted for in the CVRs.
//Errors in the card style information are treated conservatively using the  "phantoms-to-evil-zombies" (~2EZ) approach ([Banuelos & Stark, 2012](https://arxiv.org/abs/1207.3413)) so that the risk limit remains valid, even if the CVRs misrepresent
//which cards contain which contests.
//
//The two ways of sampling are treated differently.
//If the sample is to be drawn only from cards that--according to the CVR--contain a particular contest, and a sampled card turns out not to
//contain that contest, that is considered a discrepancy, dealt with using the ~2EZ approach.
//It is assumed that every CVR corresponds to a card in the manifest, but there might
//be cards cast in the contest for which there is no corresponding CVR. In that case,
//phantom CVRs are created to ensure that the audit is still truly risk-limiting.
//
//Given an independent (i.e., not relying on the voting system) upper bound on the number of cards that contain the contest, if the number of CVRs that contain the contest does not exceed that bound, we can sample from paper purported to contain the contest and use the ~2EZ approach to deal with missing CVRs. This can greatly increase the efficiency of the audit if
//some contests appear on only a small percentage of the cast cards ([Glazer, Spertus, and Stark, 2021](https://dl.acm.org/doi/10.1145/3457907)).
//If there are more CVRs than the upper bound on the number of cards, extra CVRs can be deleted provided
//that deletion does not change any contest outcome. See [Stark, 2022](https://arxiv.org/abs/2207.01362).
//(However, if there more CVRs than cards, that is evidence of a process failure.)
//
//Any sampled phantom card (i.e., a card for which there is no CVR) is treated as if its CVR is a non-vote (which it is), and as if its MVR was least favorable (an "evil zombie" producing the greatest doubt in every assertion, separately). Any sampled card for which there is a CVR is compared to its corresponding CVR.
//If the card turns out not to contain the contest (despite the fact that the CVR says it does), the MVR is treated in the least favorable way for each assertion (i.e., as a zombie rather than as a non-vote).
//
//The tool helps select cards for audit, and reports when the audit has found sufficiently strong evidence to stop.
//
//The tool exports a log of all the audit inputs except the CVR file, but including the auditors' manually determined voter intent from the audited cards.
//
//The pre-10/2021 version used a single sample to audit all contests.

//### Internal workflow
//
//+ Read overall audit information (including the seed) and contest information
//+ Read assertions for IRV contests and construct assertions for all other contests
//+ Read ballot manifest
//+ Read cvrs. Every CVR should have a corresponding manifest entry.
//+ Prepare ~2EZ:
//    - `N_phantoms = max_cards - cards_in_manifest`
//    - If `N_phantoms < 0`, complain
//    - Else create `N_phantoms` phantom cards
//    - For each contest `c`:
//        + `N_c` is the input upper bound on the number of cards that contain `c`
//        + if `N_c is None`, `N_c = max_cards - non_c_cvrs`, where `non_c_cvrs` is #CVRs that don't contain `c`
//        + `C_c` is the number of CVRs that contain the contest
//        + if `C_c > N_c`, complain
//        + else if `N_c - C_c > N_phantoms`, complain
//        + else:
//            - Consider contest `c` to be on the first `N_c - C_c` phantom CVRs
//            - Consider contest `c` to be on the first `N_c - C_c` phantom ballots
//+ Create Assertions for every Contest. This involves also creating an Assorter for every Assertion, and a `NonnegMean` test
//    for every Assertion.
//+ Calculate assorter margins for all assorters:
//    - If `not use_style`, apply the Assorter to all cards and CVRs, including phantoms
//    - Else apply the assorter only to cards/cvrs reported to contain the contest, including phantoms that contain the contest
//+ Set `assertion.test.u` to the appropriate value for each assertion: `assorter.upper_bound` for polling audits or
//      `2/(2-assorter.margin/assorter.upper_bound)` for ballot-level comparison audits
//+ Estimate starting sample size for the specified sampling design (w/ or w/o replacement, stratified, etc.), for chosen risk function, use of card-style information, etc.:
//    - User-specified criterion, controlled by parameters. Examples:
//        + expected sample size for completion, on the assumption that there are no errors
//        + 90th percentile of sample size for completion, on the assumption that errors are not more frequent than specified
//    - If `not use_style`, base estimate on sampling from the entire manifest, i.e., smallest assorter margin
//    - Else use consistent sampling:
//        + Augment each CVR (including phantoms) with a probability of selection, `p`, initially 0
//        + For each contest `c`:
//            - Find sample size `n_c` that meets the criterion
//            - For each non-phantom CVR that contains the contest, set `p = max(p, n_c/N_c)`
//        + Estimated sample size is the sum of `p` over all non-phantom CVRs
//+ Draw the random sample:
//    - Use the specified design, including using consistent sampling for style information
//    - Express sample cards in terms of the manifest
//    - Export
//+ Read manual interpretations of the cards (MVRs)
//+ Calculate attained risk for each assorter
//    - Use ~2EZ to deal with phantom CVRs or cards; the treatment depends on whether `use_style == True`
//+ Report
//+ Estimate incremental sample size if any assorter nulls have not been rejected
//+ Draw incremental sample; etc
//
////////////////////////////////////////////////////////////////////////////////////////////////
//# Audit parameters.
//
//The overall audit involves information that is the same across contests, encapsulated in
//a dict called `audit`:
//
//* `seed`: the numeric seed for the pseudo-random number generator used to draw sample (for SHA256 PRNG)
//* `sim_seed`: seed for simulations to estimate sample sizes (for Mersenne Twister PRNG)
//* `quantile`: quantile of the sample size to use for setting initial sample size
//* `cvr_file`: filename for CVRs (input)
//* `manifest_file`: filename for ballot manifest (input)
//* `use_style`: Boolean. If True, use card style information (inferred from CVRs) to target samples. If False, sample from all cards, regardless of the contest.
//* `sample_file`: filename for sampled card identifiers (output)
//* `mvr_file`: filename for manually ascertained votes from sampled cards (input)
//* `log_file`: filename for audit log (output)
//* `error_rate_1`: expected rate of 1-vote overstatements. Recommended value $\ge$ 0.001 if there are hand-marked ballots.
//     Larger values increase the initial sample size, but make it more likely that the audit will conclude after a single round even if the audit finds errors
//* `error_rate_2`: expected rate of 2-vote overstatements. 2-vote overstatements should be extremely rare.
//     Recommended value: 0. Larger values increase the initial sample size, but make it more likely that the audit will conclude after a single round even if the audit finds errors
//* `reps`: number of replications to use to estimate sample sizes. If `reps is None`, uses a deterministic method
//* `quantile`: quantile of sample size to estimate. Not used if `reps is None`
//* `strata`: a dict describing the strata. Keys are stratum identifiers; values are dicts containing:
//    + `max_cards`: an upper bound on the number of pieces of paper cast in the contest. This should be derived independently of the voting system.
//         A ballot consists of one or more cards.
//    + `replacement`: whether to sample from this stratum with replacement.
//    + `use_style`: True if the sample in that stratum uses card-style information.
//    + `audit_type` one of Contest.POLLING, Contest.CARD_COMPARISON, Contest.BATCH_COMPARISON but only POLLING and CARD_COMPARISON are currently implemented.
//    + `test`: the name of the function to be used to measure risk.
//       Options are `kaplan_markov`,`kaplan_wald`,`kaplan_kolmogorov`,`wald_sprt`,`kaplan_mart`, `alpha_mart`, `betting_mart`.
//Not all risk functions work with every social choice function or every sampling method.
//    + `estim`: the estimator to be used by the `alpha_mart` risk function. Options:
//        - `fixed_alternative_mean` (default)
//        - `shrink_trunc`
//        - `optimal_comparison`
//    + `bet`: the method to select the bet for the `betting_mart` risk function. Options:
//        - `fixed_bet` (default)
//        - `agrapa`
//    + `test_kwargs`: keyword arguments for the risk function
//
data class ShangrlaAudit(
    val seed: Long,
    val simSeed: Long?,
    val quantile: Double, // quantile of the sample size to use for setting initial sample size
    val cvrFile: String, // filename for CVRs (input)
    val manifestFile: String, // filename for ballot manifest (input)
    val useStyle: Boolean, // use card style information (inferred from CVRs) to target samples. otherwise, sample from all cards, regardless of the contest.
    val sampleFile: String, // filename for sampled card identifiers (output)
    val mvrFile: String, // filename for manually ascertained votes from sampled cards (input)
    val logFile: String,
    val errorRate1: Double,
    val errorRate2: Double,
    val reps: Int, // number of replications to use to estimate sample sizes. If `reps is None`, uses a deterministic method
)

//----
//
//* `contests`: a dict of contest-specific data
//    + the keys are unique contest identifiers for contests under audit
//    + the values are Contest objects with attributes:
//        - `risk_limit`: the risk limit for the audit of this contest
//        - `cards`: an upper bound on the number of cast cards that contain the contest
//        - `choice_function`: `Audit.SOCIAL_CHOICE_FUNCTION.PLURALITY`,
//          `Audit.SOCIAL_CHOICE_FUNCTION.SUPERMAJORITY`, or `Audit.SOCIAL_CHOICE_FUNCTION.IRV`
//        - `n_winners`: number of winners for majority contests. (Multi-winner IRV, aka STV, is not supported)
//        - `share_to_win`: for super-majority contests, the fraction of valid votes required to win, e.g., 2/3.
//             (share_to_win*n_winners must be less than 100%)
//        - `candidates`: list of names or identifiers of candidates
//        - `reported_winners` : list of identifier(s) of candidate(s) reported to have won.
//             Length should equal `n_winners`.
//        - `assertion_file`: filename for a set of json descriptors of Assertions (see technical documentation) that collectively imply the reported outcome of the contest is correct. Required for IRV; ignored for other social choice functions
//        - `audit_type`: the audit strategy. Currently `Audit.AUDIT_TYPE.POLLING (ballot-polling)`,
//           `Audit.AUDIT_TYPE.CARD_COMPARISON` (ballot-level comparison audits), and `Audit.AUDIT_TYPE.ONEAUDIT`
//            are implemented. HYBRID and STRATIFIED are planned.
//        - `test`: the risk function for the audit. Default is `NonnegMean.alpha_mart`, the alpha supermartingale test
//        - `estim`: estimator for the alternative hypothesis for the test. Default is `NonnegMean.shrink_trunc`
//        - `use_style`: True to use style information from CVRs to target the sample. False for polling audits or for sampling from all ballots for every contest.
//        - other keys and values are added by the software, including `cvrs`, the number of CVRs that contain the contest, and `p`, the sampling fraction expected to be required to confirm the contest

data class ShangrlaContest(
    val name: String,
    val risk_limit: Double = 0.05,
    val cards: Int, // upper bound on the number of cast cards that contain the contest
    val choice_function: SocialChoiceFunction,
    val n_winners: Int,
    val candidate: List<Int>,
    val winner: List<Int>,
    val assertion_file: String, // for IRV
    val audit_type: AuditType,
    //   'test':             NonnegMean.alpha_mart,
    //   'estim':            NonnegMean.optimal_comparison
)

// SHANGRLA examples/assertion-RLA.ipynb

class AssertionRLA {

    @Test
    fun workflow() {

//audit = Audit.from_dict({
//         'seed':           12345678901234567890,
//         'sim_seed':       314159265,
//         'cvr_file':       './data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire',
//         'manifest_file':  './data/SFDA2019/N19 ballot manifest with WH location for RLA Upload VBM 11-14.xlsx',
//         'sample_file':    './data/sample.csv',
//         'mvr_file':       './data/mvr.json',
//         'log_file':       './data/log.json',
//         'quantile':       0.8,
//         'error_rate_1':   0.001,
//         'error_rate_2':   0.0,
//         'reps':           100,
//         'strata':         {'stratum_1': {'max_cards':   293555,
//                                          'use_style':   True,
//                                          'replacement': False,
//                                          'audit_type':  Audit.AUDIT_TYPE.CARD_COMPARISON,
//                                          'test':        NonnegMean.alpha_mart,
//                                          'estimator':   NonnegMean.optimal_comparison,
//                                          'test_kwargs': {}
//                                         }
//                           }
//        })

        val audit = ShangrlaAudit(
            1234567890123456789L,
            314159265,
            0.8,
            "src/test/data/raire/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire",
            manifestFile = "src/test/data/raire/SFDA2019/N19-manifest.csv",
            // "./data/SFDA2019/N19 ballot manifest with WH location for RLA Upload VBM 11-14.xlsx",
            true,
            "./data/sample.csv",
            "./data/mvr.json",
            "./data/log.json",
            0.01,
            0.00,
            100
        )

        //# contests to audit. Edit with details of your contest (eg., Contest 339 is the DA race)
        //contest_dict = {'339':{
        //                   'name': 'DA',
        //                   'risk_limit':       0.05,
        //                   'cards':            146662,
        //                   'choice_function':  Contest.SOCIAL_CHOICE_FUNCTION.IRV,
        //                   'n_winners':        1,
        //                   'candidates':       ['15','16','17','18','45'],
        //                   'winner':           ['15'],
        //                   'assertion_file':   './data/SFDA2019/SF2019Nov8Assertions.json',
        //                   'audit_type':       Audit.AUDIT_TYPE.CARD_COMPARISON,
        //                   'test':             NonnegMean.alpha_mart,
        //                   'estim':            NonnegMean.optimal_comparison
        //                  }
        //               }
        //
        //contests = Contest.from_dict_of_dicts(contest_dict)
        val scontest = ShangrlaContest(
            name = "DA",
            risk_limit = 0.05,
            cards = 146662,
            choice_function = SocialChoiceFunction.IRV,
            n_winners = 1,
            candidate = listOf(15, 16, 17, 18, 45),
            winner = listOf(15),
            assertion_file = "./data/raire/SFDA2019/SF2019Nov8Assertions.json",
            audit_type = AuditType.CLCA,
        )

        // Raire Assertions
        //# read the assertions for the IRV contest
        //for c in contests:
        //    if contests[c].choice_function == Contest.SOCIAL_CHOICE_FUNCTION.IRV:
        //        with open(contests[c].assertion_file, 'r') as f:
        //            contests[c].assertion_json = json.load(f)['audits'][0]['assertions']
        //
        // original file fails on
        // Unexpected JSON token at offset 5212: Expected start of the array '[', but had '"' instead at path: $.audits[0].assertions[12].already_eliminated
        // JSON input: .....        "already_eliminated": "",
        // had to change                     "already_eliminated": "",  to                     "already_eliminated": [],
        // also no good reaspon to use string instead of int

        val ncs = mapOf("334" to 1000, "339" to 12000) // TODO
        val nps = mapOf("334" to 0, "339" to 0) // TODO

        val rr =
            readRaireResultsJson("src/test/data/raire/SFDA2019/SF2019Nov8Assertions.json")
        val raireResults = rr.import(ncs, nps)
        val show = raireResults.show()
        println(show)

        //## Read the ballot manifest
        //# special for Primary/Dominion manifest format
        //manifest = pd.read_excel(audit.manifest_file)
        // (293555, 293555) = audit.max_cards, np.sum(manifest['Total Ballots'])
       // val manifest = readDominionBallotManifest(audit.manifestFile, 339)
        //println(" manifest nbatches=${manifest.batches.size} manifest.nballots =${manifest.nballots}")
        //assertEquals(293555, manifest.nballots)

//## Read the CVR data and create CVR objects
//# for ballot-level comparison audits
//cvr_list, cvrs_read, unique_ids = CVR.from_raire_file(audit.cvr_file)
//cvr_list = Dominion.raire_to_dominion(cvr_list)
//print(f'Read {cvrs_read} cvrs; {unique_ids} unique CVR identifiers after merging')
// Read 146663 cvrs; 146662 unique CVR identifiers after merging

        // This single contest cvr file is the only real cvr data in SHANGRLA
        // //         'cvr_file':       './data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire',
        val raireCvrs = readRaireBallotsCsv(audit.cvrFile)
        val rcContest = raireCvrs.contests.first()
        val N = raireCvrs.cvrs.size // ??
        println(" raireCvrs contests=${raireCvrs.contests.size} ncards =${N}")
        assertEquals(1, raireCvrs.contests.size)
        assertEquals(146662, N)

// # double-check whether the manifest accounts for every card
//audit.max_cards, np.sum(manifest['Total Ballots'])
//        (293555, 293555)
// # Check that there is a card in the manifest for every card (possibly) cast. If not, add phantoms.
//manifest, manifest_cards, phantom_cards = Dominion.prep_manifest(manifest, audit.max_cards, len(cvr_list))
//manifest
// 5481 rows by 6 cols

// TODO we have 146662 cvrs and 293555 manifest ballots. Why is that almost 2x (293324) ?

//## Create CVRs for phantom cards
//#%%
//# For Comparison Audits Only
//#----------------------------
//
//# If the sample draws a phantom card, these CVRs will be used in the comparison.
//# phantom MVRs should be treated as zeros by the Assorter for every contest
//
//# setting use_style = False to generate phantoms
//
//cvr_list, phantom_vrs = CVR.make_phantoms(audit=audit, contests=contests, cvr_list=cvr_list, prefix='phantom-1-')
//print(f"Created {phantom_vrs} phantom records")
// "Created 0 phantom records" TODO why?

//# find the mean of the assorters for the CVRs and check whether the assertions are met
//min_margin = Assertion.set_all_margins_from_cvrs(audit=audit, contests=contests, cvr_list=cvr_list)
//
//print(f'minimum assorter margin {min_margin}')
//Contest.print_margins(contests)
        // minimum assorter margin 0.019902906001554532
        //margins in contest 339:
        //	assertion 18 v 17 elim 15 16 45: 0.045792366120740224
        //	assertion 17 v 16 elim 15 18 45: 0.019902906001554532
        //	assertion 15 v 18 elim 16 17 45: 0.028923647570604505
        //	assertion 18 v 16 elim 15 17 45: 0.0830003681935334
        //	assertion 17 v 16 elim 15 45: 0.058079120699294995
        //	assertion 15 v 17 elim 16 45: 0.08064120222007065
        //	assertion 15 v 17 elim 16 18 45: 0.10951712099930444
        //	assertion 18 v 16 elim 15 45: 0.14875018750596603
        //	assertion 15 v 16 elim 17 45: 0.13548158350492967
        //	assertion 15 v 16 elim 17 18 45: 0.1365247985163165
        //	assertion 15 v 16 elim 18 45: 0.16666893946625572
        //	assertion 15 v 16 elim 45: 0.15626406294745743
        //	assertion 15 v 45: 0.2956457705472446
        println()

        val expected = listOf(
            0.045792366120740224,
            0.019902906001554532,
            0.028923647570604505,
            0.0830003681935334,
            0.058079120699294995,
            0.08064120222007065,
            0.10951712099930444,
            0.14875018750596603,
            0.13548158350492967,
            0.1365247985163165,
            0.16666893946625572,
            0.15626406294745743,
            0.2956457705472446,
        )
        // these are the averages of the polling plurality assorters; use this to set the margins
        var count = 0
        val rrContest: RaireContestUnderAudit = raireResults.contests.first()
        val assorts: List<RaireAssorter> = rrContest.makeAssorters()
        val rcvrs = raireCvrs.cvrs
        val margins = assorts.map { assort ->
            val mean = rcvrs.map { assort.assort(it) }.average()
            println(" ${assort.desc()} mean=$mean margin = ${mean2margin(mean)}")
            assertEquals(expected[count++], mean2margin(mean))
            mean2margin(mean)
        }
        val minMargin = margins.min()
        println("min = $minMargin")

// seems to write audit as a json file to audit.log_file
//audit.write_audit_parameters(contests=contests)

        //  Double check here - debugging
        // replicate_p_values(N, raireResults.contests, rcContest.cvrs)

//## Set up for sampling
//# find initial sample size
// sample_size = audit.find_sample_size(contests, cvrs=cvr_list)
// print(f'{sample_size=}\n{[(i, c.sample_size) for i, c in contests.items()]}')
// sample_size=372
//[('339', 372)]

        val results = calc_sample_sizes(10, raireResults.contests, raireCvrs.cvrs)
        println("calc_sample_sizes = $results")
        val sampleSize = results.sampleCount[0] // TODO

        /*
                val ccontest2: Contest = rrContest.toContest()
                val cvras: List<CvrUnderAudit> = makeCvras(rcvrs, Random)
                // contests: List<Contest>, cvrs : Iterable<Cvr>, riskLimit: Double=0.05
                val caudit2: AuditComparison = makeComparisonAudit(listOf(ccontest), rcvrs)
                // val N: Int, val alpha: Double, val error_rate_1: Double, val error_rate_2: Double, val reps: Int, val quantile: Double
                val findSampleSize = FindSampleSize(N, scontest.risk_limit, audit.errorRate1, audit.errorRate2, audit.reps, audit.quantile)
                // audit: AuditComparison, contests: List<ContestUnderAudit>, cvrs: List<CvrUnderAudit>,
                val cua = ContestUnderAudit(ccontest, N)
                val ss = findSampleSize.find_sample_size(caudit, listOf(cua), cvras)
                println("sampleSize = $ss")
         */


        ///// consistent sampling
//## Draw the first sample
//#%%
//# draw the initial sample using consistent sampling
//prng = SHA256(audit.seed)
//CVR.assign_sample_nums(cvr_list, prng)
        // TODO evaluate secureRandom for production, also needs to be deterministic, ie seeded
        val cvras = rcvrs.mapIndexed { idx, it -> CvrUnderAudit(it, idx, Random.nextLong())}

        println("calc_sample_sizes = $results")

//#%%
//sampled_cvr_indices = CVR.consistent_sampling(cvr_list=cvr_list, contests=contests)
        val contestUAs = raireResults.contests.map {
            // TODO it.ncvrs = N
            it
        }
        val contestRounds = contestUAs.map{ contest -> ContestRound(contest, 1) }
        val auditRound = AuditRound(1, contestRounds, sampleNumbers = emptyList())
        consistentSampling(auditRound, StartTestBallotCardsClca(rcvrs, rcvrs, 999666L))
        println("sampled = ${auditRound.sampleNumbers.size}")

//n_sampled_phantoms = np.sum(sampled_cvr_indices > manifest_cards)
//print(f'The sample includes {n_sampled_phantoms} phantom cards.')
        // The sample includes 0 phantom cards.
//#%%
//len(cvr_list), manifest_cards, audit.max_cards
        // (146662, 293555, 293555)
//# for comparison audit
//cards_to_retrieve, sample_order, cvr_sample, mvr_phantoms_sample = \
//    Dominion.sample_from_cvrs(cvr_list, manifest, sampled_cvr_indices)
//
//# for polling audit (not used)
//# cards_to_retrieve, sample_order, mvr_phantoms_sample = Dominion.sample_from_manifest(manifest, sample)
//#%%
//# write the sample "cards_to_retrieve" to the file "audit.sample_file"
//Dominion.write_cards_sampled(audit.sample_file, cards_to_retrieve, print_phantoms=False)



//#%% md
//## Read the audited sample data
//#%%
//# for real data
//with open(audit.mvr_file) as f:
//    mvr_json = json.load(f)
//
//mvr_sample = CVR.from_dict(mvr_json['ballots'])
//
//# for simulated data, no errors
//mvr_sample = cvr_sample.copy()
//#%% md
//## Find measured risks for all assertions
//#%%
//CVR.prep_comparison_sample(mvr_sample, cvr_sample, sample_order)  # for comparison audit
//# CVR.prep_polling_sample(mvr_sample, sample_order)  # for polling audit
//
//###### TEST
//# permute part of the sample to introduce errors deliberately
//mvr_sample = cvr_sample.copy()
//n_errs = 5
//errs = mvr_sample[0:n_errs].copy()
//np.random.seed(12345678)
//np.random.shuffle(errs)
//mvr_sample[0:n_errs] = errs
//#%%
//p_max = Assertion.set_p_values(contests=contests, mvr_sample=mvr_sample, cvr_sample=cvr_sample)
//print(f'maximum assertion p-value {p_max}')
//done = audit.summarize_status(contests)


// p-values for assertions in contest 339
//	18 v 17 elim 15 16 45: 0.47847909464463045
//	17 v 16 elim 15 18 45: 0.48779308744628547
//	15 v 18 elim 16 17 45: 0.44631352397209006
//	18 v 16 elim 15 17 45: 2.877844184074244e-07
//	17 v 16 elim 15 45: 0.0035354717267599813
//	15 v 17 elim 16 45: 4.0458461637126434e-07
//	15 v 17 elim 16 18 45: 2.3358046805505937e-07
//	18 v 16 elim 15 45: 5.790407277111407e-13
//	15 v 16 elim 17 45: 9.312863307985585e-12
//	15 v 16 elim 17 18 45: 1.8495628594508403e-09
//	15 v 16 elim 18 45: 1.1604860427653792e-14
//	15 v 16 elim 45: 9.55405920633293e-14
//	15 v 45: 1.373326785425354e-26
//
//contest 339 audit INCOMPLETE at risk limit 0.05. Attained risk 0.48779308744628547

//# Log the status of the audit
//audit.write_audit_parameters(contests)
//#%% md
//# How many more cards should be audited?
//
//Estimate how many more cards will need to be audited to confirm any remaining contests. The enlarged sample size is based on:
//
//* cards already sampled
//* the assumption that we will continue to see errors at the same rate observed in the sample
//#%%
//# Estimate sample size required to confirm the outcome, if errors continue
//# at the same rate as already observed.
//
//new_size = audit.find_sample_size(contests, cvrs=cvr_list, mvr_sample=mvr_sample, cvr_sample=cvr_sample)
//print(f'{new_size=}\n{[(i, c.sample_size) for i, c in contests.items()]}')
        // new_size=7098
        //[('339', 6726)]
//
//#%%
//# save the first sample
//sampled_cvr_indices_old, cards_to_retrieve_old, sample_order_old, cvr_sample_old, mvr_phantoms_sample_old = \
//    sampled_cvr_indices, cards_to_retrieve,     sample_order,     cvr_sample,     mvr_phantoms_sample
//#%%
//# draw the sample
//sampled_cvr_indices = CVR.consistent_sampling(cvr_list=cvr_list, contests=contests)
//n_sampled_phantoms = np.sum(sampled_cvr_indices > manifest_cards)
//print(f'The sample includes {n_sampled_phantoms} phantom cards.')
//
//# for comparison audit
//cards_to_retrieve, sample_order, cvr_sample, mvr_phantoms_sample = \
//    Dominion.sample_from_cvrs(cvr_list, manifest, sampled_cvr_indices)
//
//# for polling audit
//# cards_to_retrieve, sample_order, mvr_phantoms_sample = Dominion.sample_from_manifest(manifest, sample)
//
//# write the sample
//# could write only the incremental sample using list(set(cards_to_retrieve) - set(cards_to_retrieve_old))
//Dominion.write_cards_sampled(audit.sample_file, cards_to_retrieve, print_phantoms=False)
//#%%
//# for real data
//with open(audit.mvr_file) as f:
//    mvr_json = json.load(f)
//
//mvr_sample = CVR.from_dict(mvr_json['ballots'])
//
//# for simulated data, no errors
//mvr_sample = cvr_sample.copy()
//#%% md
//## Find measured risks for all assertions
//#%%
//CVR.prep_comparison_sample(mvr_sample, cvr_sample, sample_order)  # for comparison audit
//# CVR.prep_polling_sample(mvr_sample, sample_order)  # for polling audit
//
//###### TEST
//# permute part of the sample to introduce errors deliberately
//mvr_sample = cvr_sample.copy()
//n_errs = 5
//errs = mvr_sample[0:n_errs].copy()
//np.random.seed(12345678)
//np.random.shuffle(errs)
//mvr_sample[0:n_errs] = errs
//#%%
//p_max = Assertion.set_p_values(contests=contests, mvr_sample=mvr_sample, cvr_sample=cvr_sample)
//print(f'maximum assertion p-value {p_max}')
//done = audit.summarize_status(contests)
        // p-values for assertions in contest 339
        //	18 v 17 elim 15 16 45: 1.4449144036994632e-65
        //	17 v 16 elim 15 18 45: 1.2395432394747009e-28
        //	15 v 18 elim 16 17 45: 5.2774518365009846e-42
        //	18 v 16 elim 15 17 45: 6.205447790363212e-127
        //	17 v 16 elim 15 45: 2.8080650629218943e-86
        //	15 v 17 elim 16 45: 2.6490812247796027e-123
        //	15 v 17 elim 16 18 45: 1.7998086930305547e-166
        //	18 v 16 elim 15 45: 1.7022899132638306e-231
        //	15 v 16 elim 17 45: 4.74504262834718e-210
        //	15 v 16 elim 17 18 45: 2.445447527327654e-209
        //	15 v 16 elim 18 45: 9.191770721839916e-261
        //	15 v 16 elim 45: 8.342200191989171e-244
        //	15 v 45: 0.0
        //
        //contest 339 AUDIT COMPLETE at risk limit 0.05. Attained risk 1.2395432394747009e-28
        // TODO now its overkill
    }
}

fun replicate_p_values(
    N: Int,
    contests: List<RaireContestUnderAudit>,
    cvrs: List<Cvr>,
) {
    // TODO SHANGRLA doing complicated stuff. I think trying to audit simultaneous contests (dont understand the rules for that)
    //   Has strata but not using them. What are they?
    //   How does consistent sampling play with multiple contests?
    //   The ballot pools may be differenent for each contest (style?).
    //   I think you randomly pick ballots until your contest.sample_size is satisfied
    //   Also see  proportional-with-error-bound (PPEB) sampling (aslam pdf)
    //
    // TODO SHANGRLA has an option to do a simulation, then pick the max over contest and assertion.
    //   Surprising that they dont just use the min margin, at least within a contest.
    //   Calls test.sample_size(), test-specific simulation.
    //   We could do our own simulation, dont need to follow SHANGLRA's convolutions.
    val sample_size = 372 // just use this from SHANGRLA for now, see if we can replicate the p-values

    //val auditComparison = makeRaireComparisonAudit(contests, cvrs)
   // val comparisonAssertions = auditComparison.assertions.values.first()
    //val minAssorter = comparisonAssertions[1].assorter // the one with the smallest margin


    val contest = contests.first()
    val minAssorter = contest.minClcaAssertion()!!.cassorter // the one with the smallest margin

    val sampler: Sampler = makeClcaNoErrorSampler(contest.id, cvrs, minAssorter)

    val optimal = OptimalComparisonNoP1(
        N = N,
        withoutReplacement = true,
        upperBound = minAssorter.upperBound(),
        p2 = 0.01
    )

    val betta = BettingMart(bettingFn = optimal, Nc = N, noerror = minAssorter.noerror(), upperBound = minAssorter.upperBound(), withoutReplacement = false)
    val debugSeq = betta.setDebuggingSequences()
    val result = betta.testH0(sample_size, true) { sampler.sample() }
    println(result)
    println("pvalues=  ${debugSeq.pvalues()}")}

fun calc_sample_sizes(
    ntrials: Int,
    contests: List<RaireContestUnderAudit>,
    cvrs: List<Cvr>,
): RunTestRepeatedResult {

    val N = cvrs.size
    //val auditComparison = makeRaireComparisonAudit(contests, cvrs)
    //val comparisonAssertions = auditComparison.assertions.values.first()
    // val minAssorter = comparisonAssertions[1].assorter // the one with the smallest margin
    //val minAssertion = comparisonAssertions.minBy { it.margin }
    //val minAssorter = minAssertion.assorter

    val contest = contests.first().makeClcaAssertions(cvrs)
    val minAssorter = contest.minClcaAssertion()!!.cassorter // the one with the smallest margin

    val sampler: Sampler = makeClcaNoErrorSampler(contest.id, cvrs, minAssorter)

    val optimal = AdaptiveBetting(
        Nc = N,
        withoutReplacement = true,
        a = minAssorter.noerror(),
        d = 100,
        ClcaErrorRates(.001, .01, 0.0, 0.0),
    )
    val betta = BettingMart(bettingFn = optimal, Nc = N, noerror = minAssorter.noerror(), upperBound = minAssorter.upperBound(), withoutReplacement = false)

    return runTestRepeated(
        drawSample = sampler,
        // maxSamples = N,
        ntrials = ntrials,
        testFn = betta,
        testParameters = mapOf("p2o" to optimal.p2o),
        margin = minAssorter.assorter().reportedMargin(),
        Nc = N,
    )
}

////////////////////////////////////////////////////////////////////////////////////////////////
fun maxVal(inputList: ArrayList<ArrayList<String>>): Int {
    var maxValue = inputList[0][1].toInt()

    for (i in 1 until inputList.size) {
        if (inputList[i][1].toInt() > maxValue) {
            maxValue = inputList[i][1].toInt()
        }
    }

    return maxValue
}

fun sumVal(inputList: ArrayList<ArrayList<String>>): Int {
    var sum = 0

    for (i in 1 until inputList.size) {
        sum += inputList[i][1].toInt()
    }

    return sum
}

fun prep_manifest(
    manifest: ArrayList<ArrayList<String>>,
    maxCardsEdit: Int,
    cvrCount: Int,
): ArrayList<ArrayList<String>> {
    val manifestCards = sumVal(manifest)
    var phantomCards = maxCardsEdit - manifestCards
    if (phantomCards < 0) {
        throw Exception("There are more cards in manifest than maxCards")
    }
    if (phantomCards > 0) {
        if (phantomCards + manifestCards != cvrCount) {
            throw Exception("There are missing cvrs")
        }
    }
    val phantomCvrs = cvrCount - manifestCards
    if (phantomCvrs > 0) {
        for (i in 1..phantomCvrs) {
            manifest.add(arrayListOf("phantom", (maxVal(manifest) + 1).toString()))
            phantomCards -= 1
        }
    }
    return manifest
}

fun sample_from_manifest(
    manifest: ArrayList<ArrayList<String>>,
    sample: ArrayList<Int>,
): ArrayList<ArrayList<String>> {
    val cardsToRetrieve = ArrayList<ArrayList<String>>()

    for (i in 1 until sample.size) {
        cardsToRetrieve.add(manifest[sample[i] - 1])
    }

    return cardsToRetrieve
}