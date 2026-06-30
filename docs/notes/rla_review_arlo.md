# https://github.com/nealmcb/rla-review-arlo/

### inspect_artifacts.py

Inspect the structure and contents of Georgia May 19, 2026 RLA artifacts.
Prints schema, row counts, contests, and key values from the final audit report.


### reproduce_ppeb_sample.py

Reproduce the full PPEB (Proportional-with-Error-Bound) sample draw for Georgia's
May 19, 2026 General Primary RLA using Arlo's sampler logic and numpy 1.26.4
(matching Arlo's poetry.lock pin).

Probably the same as ALPHA:
4.2. Batch Sampling with Probability Proportional to a Bound on the Assorter.


### reproduce_sample.py

Attempt to reproduce the Georgia May 19, 2026 RLA sample selection.

STATUS: PARTIALLY REPRODUCIBLE.
- Ticket numbers for individual batches CAN be reproduced from the
  public seed and public batch keys using consistent_sampler.first_fraction().
- Two ticket numbers verified against the final audit report.
- Full PPEB/MACRO sample draw uses numpy weighted random.choice() with
  weights derived from batch-level candidate totals (public) and
  ballot counts from the manifests (public). The exact numpy version
  matters for reproducibility.
- Missing: exact Arlo version, numpy version used at audit time.

### summarize_manifests.py

Summarize ballot manifests: county counts, batch counts, ballot counts, batch sizes.


## arlo

### macro.py

"""
An implemenation of per-contest batch comparison audits, loosely based on
MACRO. Since MACRO applies to all contests being audited (hence
across-contest), this code acts as if each contest is independently audited
according it its maximum relative overstatement (as if we did MACRO only one
one contest).

MACRO was developed by Philip Stark (see
https://papers.ssrn.com/sol3/papers.cfm?abstract_id=1443314 for the
publication).
"""
