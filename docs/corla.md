# CORLA
10/20/2024

## Comparison to betting martingale

Performance is similar when there are few overstatements, and progressively worse as the rate
of two-vote overstatement goes up. Havent yet tested the effects of one-vote overstatements. 

Compare to same plots in [Betting](Betting.md).

Plot 1 shows the average number of samples needed to reject the null, aka "success":

[Number of samples needed](plots/corla/CorlaPlot.plotSuccessVsTheta.10000.html)

Plot 2 shows the percentage of successes when the cutoff is 20% of N. Note these are false positives when
theta <= 0.5:

[Percentage of successes when the cutoff is 20%](plots/corla/CorlaPlot.plotSuccess20VsTheta.10000.html)

Plot 3 zooms in on the false positives when the cutoff is 20% of N:

[False positives when the cutoff is 20%](plots/corla/CorlaPlot.plotFailuresVsTheta.10000.html)

Plot 4 zooms in on the successes (same as Plot 2) close to theta = 1/2:

[Percentage of successes, theta close to 1/2](plots/corla/CorlaPlot.plotSuccess20VsThetaNarrow.10000.html)

## Notes

* uses sparkjava web framework, which is now abandoned
* hibernate/jpa ORM with postgres database
* everything revolves around the database as global, mutable shared state. No real separation of business logic
  from the persistence layer, unless you count the ASMs.
* The auditing math is contained in a few dozen line of code in Audit class, implementing Kaplan-Markov MACRO P-value
  approximation, eq 10 in SuperSimple paper.
* Almost no unit testing, there may be some integration testing I havent found yet.