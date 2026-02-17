# Attacks
02/17/2026

_Attacks_ are scenarios where the actual winner is not the reported winner. They may be intentional, due to malicious
actors, or unintentional, due to mistakes in the process or bugs in the software.

## Attack with phantoms

Here we investigate what happens when the percentage of phantoms is high enough to flip the election, but the reported margin
does not reflect that. In other words an attack (or error) when the phantoms are not correctly reported.

We create CLCA simulations at different margins and percentage of phantoms, and fuzz the MVRs at 1%.
We measure the "true margin" of the MVRs, including phantoms, by applying the CVR assorter, and use that for the x axis.

The error estimation strategies in this plot are:
* noerror : The apriori error rates are 0.
* fuzzPct: The apriori error rates are calculated from the true fuzzPct (so, the best possible guess).
* phantomPct: use _phantomPct_ as the apriori error rates.

These are just the initial guesses for the error rates. In all cases, they are adjusted as samples are made and errors are found.

Here are plots of sample size as a function of true margin, for phantomPct of 0, 2, and 5 percent:

<a href="https://johnlcaron.github.io/rlauxe/docs/plots/attack/marginWithPhantoms0/marginWithPhantoms0LogLinear.html" rel="marginWithPhantoms0LogLinear">![marginWithPhantoms0LogLinear](docs/plots/attack/marginWithPhantoms0/marginWithPhantoms0LogLinear.png)</a>
<a href="https://johnlcaron.github.io/rlauxe/docs/plots/attack/marginWithPhantoms2/marginWithPhantoms2LogLinear.html" rel="marginWithPhantoms2LogLinear">![marginWithPhantoms2LogLinear](docs/plots/attack/marginWithPhantoms2/marginWithPhantoms2LogLinear.png)</a>
<a href="https://johnlcaron.github.io/rlauxe/docs/plots/attack/marginWithPhantoms5/marginWithPhantoms5LogLinear.html" rel="marginWithPhantoms5LogLinear">![marginWithPhantoms5LogLinear](docs/plots/attack/marginWithPhantoms5/marginWithPhantoms5LogLinear.png)</a>

* The true margin is approximately the reported margin minus the phantom percentage.
* Once the true margin falls below 0, the audit goes to a full count, as it should.
* The fuzzPct strategy does a bit better when the phantom rate is not too high.

## Attack with wrong reported winner

Here we investigate an attack when the reported winner is different than the actual winner.

We create simulations at the given reported margins, with no fuzzing or phantoms.
Then in the MVRs we flip just enough votes to make the true margin < 50%. We want to be sure that
the percent of false positives stays below the risk limit (here its 5%):

<a href="https://johnlcaron.github.io/rlauxe/docs/plots/attack/attacksByStrategy/clcaAttacksByStrategyFalsePositives.html" rel="clcaAttacksByStrategyFalsePositives">![clcaAttacksByStrategyFalsePositives](docs/plots/attack/attacksByStrategy/clcaAttacksByStrategyFalsePositives.png)</a>

* The false positives stay below the risk limit of 5%.

## Clca, SwitchWinnerMinAttack

* Create two-candidate Contest with given margin. No undervotes, no phantoms. candB is reported winner. Generate cards to match.
* Let diff = margin * Nc. In the mvrs, switch (diff + 3)/2 votes for candB to candA. Now candA is winner by 1 vote.


## Clca, HideInUndervotesAttack

* Create two-candidate Contest with
  undervotes = Nc*undervotePct.
  nvotes = Nc - undervotes
  diff = undervotes + 1; win = (nvotes + diff)/2, lose = nvotes - win
  candB is reported winner. Generate cards to match.
* Let In the mvrs, switch all undervotes to candA. Now candA is winner by 1 vote.


## Clca, HideInOtherGroupAttack

* create 2 card styles, style1 = contest1, contest2, style2 = contest2

* Create two-candidate Contest1 with given margin. No undervotes, no phantoms. candB is reported winner.
  Let diff = margin * Nc.
  In the cardManifest, switch (diff + 1) cards to style2
  In the mvrs, switch (diff + 1) cvrs from candB to candA

* the cardManifest tabulation fails for Clca, since the cvrs must be present.

## OneAudit, HideInOtherPoolAttack; hasStyles = false

* create 2 card styles, style1 = contest1, contest2, style2 = contest2

* Create two-candidate Contest1 with given margin. No undervotes, no phantoms. candB is reported winner.
  Let diff = margin * Nc.
  In the cardManifest, switch (diff + 1) cards to style2
  In the mvrs, switch (diff + 1) cvrs from candB to candA

* the cardManifest tabulation fails for Clca, since the cvrs must be present.
