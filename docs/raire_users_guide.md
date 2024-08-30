# RAIRE Users Guide Test

This page describes step-by-step instructions for running an audit of an IRV election.


The theory is described in *[RAIRE: Risk-Limiting Audits for IRV Elections](https://arxiv.org/abs/1903.08804)* by Michelle Blom, Peter J. Stuckey, Vanessa Teague.  A simple example is *[here](code/RAIREExample.ipynb)*

These are the steps for running the audit:


1. Download the electronic Cast Vote Records (CVRs) from the *[San Francisco Department of Elections](https://sfelections.sfgov.org/results)*


2. Translate them into RAIRE's format using the CVR converter at ****


3. Download the *[RAIRE tool](https://github.com/michelleblom/audit-irv-cp)* and compile it.


4. Run RAIRE on the reformatted CVRs from Step 2.  RAIRE will output a list of Assertions.


5. Upload RAIRE's assertions to the auditing notebook here, along with the electronic CVRs (from Step 1) and election metadata as requested.


6. Enter an appropriately-generated seed as requested.


7.  Download the list of ballot IDs for audit and upload them at *[the Paper Ballot checking tool](https://rla.vptech.io)*


8. Multiple teams may now retrieve and record the paper ballots they are instructed to find.  (**Perhaps insert instructions for the two-person protocol here.)  This produces a set of Manual Vote Records (MVRs).


9. Upload the MVRs here as instructed.  Execute the Risk-Limiting Audit calculation as instructed.


10.  If all the assertions are accepted the audit is now complete, and confirms the announced election outcome.  If not, decide whether to perform a full manual recount or follow the escalation instructions (returning to Step 7). 

(From RAIREExample.ipynb)

# RAIRE example assertion parser and visualizer

This notebook parses and visualizes RAIRE assertions.
Right now it's hardcoded to read RAIRE_sample_audit1.json, but you can change that.
Start by executing the rectangle above to understand the election and the apparent winner.
The audit needs to exclude all the other possible winners, though we don't care about other elimination orders in which the apparent winner still wins.
Execute the next code snippet to see the trees of possible alternative elimination orders.
Each tree will be pruned according to RAIRE's assertions, with each pruned branch tagged with the assertion that allowed us to exclude it.
You (the auditor) need to check that all the leaves end in an assertion, which shows that they have been excluded.

Now the audit begins! We now apply a Risk Limiting Audit to test each of the assertions above.
For each assertion, we consider the opposite hypothesis, that candidate C *can* be eliminated at that point. We then try to audit until that hypothesis can be rejected.  If all the hypotheses are rejected, the election result is declared correct.  At any time, if the audit has failed to reject all the hypotheses, a full manual recount can be conducted.
