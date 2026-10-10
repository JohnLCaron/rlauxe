# Colorado 2026 Primary notes

From 2026/primary/finalReports/ContestsListRound1.csv

United States Senator - DEM,state_wide_contest,risk_limit_achieved,1,1444047,904334,"""John Hickenlooper""",50376,0.03000000,210,0,0,0,0,0,0,1.03905000,0,209,209

|         field |                               value |                                              description |
| ------------- | ----------------------------------- | -------------------------------------------------------- |
|     NCounties |                                  62 |           number of counties, or county name if only one |
|          name |         United States Senator - DEM |                                             contest name |
|          npop |                             1444047 |             county population from round.ballotCardCount |
|            nc |                              904334 |     contest population from round.contestBallotBardBount |
| marginInVotes |                               50376 |                                    from round.min_margin |
|       choices | [John Hickenlooper, Julie Gonzales] |                               list of choices/candidates |
|         NCand |                                   2 |                           number of choices / candidates |
|   auditReason |                  state_wide_contest |                                               from round |
|    countyMvrs |                                3895 | count of county mvrs over all counties with this contest |
|      nsamples |                                 209 |                   from round.optimistic_samples_to_audit |
|     riskLimit |                                0.03 |                                    from round.risk_limit |

Colorado also publishes CVRtoAuditBoardInterpretationComparison.csv (built from rla_export/sql/contest_comparison.sql), which lists imprinted_id alongside cai.cvr_id for every ballot any audit board reviewed. 

imprinted_id is copied verbatim from the county’s Dominion CVR export (DominionCVRExportParser.java:632-633) in tabulator-batch-record format — confirmed directly: Adams’s manifest row 101,1,100 (100 ballots) lines up with the file’s first row, imprinted_id=101-1-100, cvr_id=509816 — record 100 of a 100-ballot batch. 

_That’s exactly the address our reconstruction computes, in a public file, next to the real cvr_id._ ??
"real cvr_id = db sequence id"

## creating the statewide manifest

Concat each county's manifest (in "county order"), where county order is alphabetic, except Broomfield is at the end. See 2026/primary/finalReports/ContestIDsByCounty.csv.

Get the first contest_sample_size PRNGs. Take modulo (population size), interpret that as an index into the manifest. That gives you the imprintedId, which can be checked against the CvrMvrComparison file. Note that you dont need cvr_id. 

For a single county contest, same except use just the county manifest.

For a multiple county contest, same except use just the contest county's manifests. (do they do that?)

Cant use "index into the manifest" for style-based sampling, have to assign PRNs to all ballots, sort, and take first contest_sample_size.


