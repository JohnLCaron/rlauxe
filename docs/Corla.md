# CORLA
10/20/2024

## Comparison to betting martingale

Uses Kaplan-Markov MACRO P-value approximation, eq 10 in SuperSimple paper.

In this simulation, I used the "ballot at a time" approach to discover when the risk < riskLimit,
rather than calculating the expected sample size, "batch mode" and seeing what the risk was.

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

Notes on the non-IRV part of CORLA server.

* uses sparkjava web framework (now abandoned), with Jetty providing the Servlet container.
* hibernate/jpa ORM with postgres database
* everything revolves around the database as global, mutable shared state. No real separation of business logic
  from the persistence layer, unless you count the ASMs.
* The auditing math is contained in a few dozen line of code in the Audit class.
* Almost no unit testing, there may be some integration testing I havent found yet.
* Uses BigDecimal instead of Double for some reason.
* Log4J 2.17.2 (not vulnerable to RCE attack, but stable release is 2.24.0)
* Gson 2.8.1 (should be upgraded to latest stable).
* Maven build
* Eclipse project source layout

The value of the current code is the web based interface tailored to the desired workflow, no doubt
familiar to the Colorado Dept of State.

In principle, it might be easy to switch to using a different algorithm / library, but i havent yet untangled 
the workflow logic that feeds it. In particular:

* batching of ballots for auditing (must already be done in CORLA)
* lots of work that Stark et al are doing on stratification will likely require lots of code that isnt part 
  of the algorithm per se. Can we provide that? Is CORLA planning to? 
