#### Comparison error rates

The assumptions that one makes about the comparison error rates greatly affect the sample size estimation. These rates should
be empirically determined, and public tables for different voting machines should be published
and kept updated.

ComparisonSamplerSimulation creates modified mvrs from a set of cvrs, with possibly non-zero valeus for errors p1, p2, p3, and p4.
This works for both Plurality and IRV comparison audits.
If p1 == p3, and p2 == p4, the margin stays the same. Call this fuzzed simulation.
Current defaults rather arbitrarily chosen are:

        val p1: Double = 1.0e-2, // apriori rate of 1-vote overstatements; voted for other, cvr has winner
        val p2: Double = 1.0e-4, // apriori rate of 2-vote overstatements; voted for loser, cvr has winner
        val p3: Double = 1.0e-2, // apriori rate of 1-vote understatements; voted for winner, cvr has other
        val p4: Double = 1.0e-4, // apriori rate of 2-vote understatements; voted for winner, cvr has loser

FOr IRV, the corresponding descriptions of the errror rates are:

    NEB two vote overstatement: cvr has winner as first pref (1), mvr has loser preceeding winner (0)
    NEB one vote overstatement: cvr has winner as first pref (1), mvr has winner preceding loser, but not first (1/2)
    NEB two vote understatement: cvr has loser preceeding winner(0), mvr has winner as first pref (1)
    NEB one vote understatement: cvr has winner preceding loser, but not first (1/2), mvr has winner as first pref (1)
    
    NEN two vote overstatement: cvr has winner as first pref among remaining (1), mvr has loser as first pref among remaining (0)
    NEN one vote overstatement: cvr has winner as first pref among remaining (1), mvr has neither winner nor loser as first pref among remaining (1/2)
    NEN two vote understatement: cvr has loser as first pref among remaining (0), mvr has winner as first pref among remaining (1)
    NEN one vote understatement: cvr has neither winner nor loser as first pref among remaining (1/2), mvr has winner as first pref among remaining  (1)

See _Estimated Sample sizes with fuzz_ (below) for a different error simulation.
We expect the spread to increase, but also shift to larger samples sizes, since the cost of overstatement is higher than understatements.

If the errors are from random processes, its possible that margins remain approx the same, but also possible that some rates
are more likely to be affected than others. Itw worth noting that these rates combine machine errors with human errors of
fetching and interpreting ballots.

In any case, currrently all assumptions on the a-priori error rates are arbitrary. These need to be measured for existing
machines and practices. While these do not affect the reliabilty of the audit, they have a strong impact on the estimated sample sizes.

#### Estimating Sample sizes with fuzz

Estimated sample size vs margin at different "fuzz" percentages. The MVRs are "fuzzed" by taking _fuzzPct_ of the ballots
and randomly changing the candidate voted for. When fuzzPct = 0.0, the cvrs and mvrs agree.
When fuzzPct = 0.01, 1% of the contest's votes were randomly changed, and so on. Note that this method of generating
errors doesnt change the reported mean, on average.

The first plot shows that Comparison sample sizes are somewhat affected by fuzz. The second plot shows that Plotting sample sizes
have greater spread, but on average are not much affected.

* [Comparison Sample sizes with fuzz](docs/plots/ComparisonFuzzConcurrent.html)
* [Polling Sample sizes with fuzz](docs/plots/PollingFuzzConcurrent.html)

#### Comparison fuzz effect on under/overstatement error rates

With a mixture of contests with different candidate sizes, and empty votes allowed, here is a representative table of
how the fuzzing generates p1, p2, p3 and p4 error rates:

| ncand | p1     | p2     | p3     | p4     |
|-------|--------|--------|--------|--------|
| 2     | 0.2541 | 0.2460 | 0.2471 | 0.2570 |
| 3     | 0.3283 | 0.1502 | 0.3271 | 0.1437 |
| 4     | 0.3516 | 0.0925 | 0.3402 | 0.0897 |
| 5     | 0.3387 | 0.0744 | 0.3311 | 0.0723 |
| 6     | 0.2987 | 0.0421 | 0.2905 | 0.0426 |
| 7     | 0.2874 | 0.0374 | 0.2811 | 0.0338 |
| 8     | 0.2899 | 0.0339 | 0.2846 | 0.0327 |
| 9     | 0.2509 | 0.0212 | 0.2466 | 0.0218 |
| 10    | 0.2834 | 0.0265 | 0.2680 | 0.0238 |

(See GenerateComparisonErrorTable.generateErrorTable())

For example, a two-candidate contest has significantly higher two-vote error rates (p2), since its more likely to flip a
vote between winner and loser, than switch a vote to/from other.

For now, we will use this table to generate the error rates when estimating the sample sizes.