# Corla Redaction
last changed 9/01/2026

# General County Election

1. Each county publish their cvrs.
1. Each county publish their manifest batch counts.
2. Each county publish their contest vote totals.
3. Each county publish their card totals by contest. Or not.

Scenarios:

1. **Unredacted cvrs**: The county has unredacted cvrs and uses them to do the audit.

2. **Redacted cvrs are not in the published cvrs**
- The cvrs cant be used to calculate the contest totals, these must be provided. 
- Difference between contest card count and ncvrs = nphantoms for the contest.
- If we have card totals by contest, can do per county, else for election.

3. **Redacted cvrs are aggregated in the published cvrs, no contest counts**
- The cvrs + redaction can be used to calculate the contest totals, compare with provided subtotals.

4. **Redacted cvrs are aggregated or listed with no ids, and redacted contest counts are included**
- The cvrs + redacted counts = card totals by contest.

In the above 4 cases, there is no way to identify which card to audit, so both the mvr and cvr are phantoms, and the
CLCA assort value is

`bassort value = noerror / 2`

where noerror = `1.0 / (2.0 - margin / upper)` goes to 1/2 as the margin goes to 0
      upper = upper bound of assorter, eg 1 for plurality assorter

5. **Redacted cvrs' ids are given, or can be inferred from manifest lists**

- Since we can identify the mvr if selected, we can put the redacted ballots in one or more OneAudit pools. 
  Then the cvr value = the pool average of the assorter, and the value of the mvr may be \[0,1/2,1] so 
  
`bassort value = (1-(cvr-mvr)/upper)*noerror = \[1-avg, 1.5-avg, 2-avg] * noerror when upper=1`


## Boulder

Boulder25 and Boulder26P have a seperate line for each redacted ballot, with "Redacted" in the first column, and no
identifying fields.

````
"CvrNumber","TabulatorNum","BatchId","RecordId","ImprintedId","CountingGroup","BallotType",
...
"Redacted",,,,,,16,0,1,1,1,0,0,1,0,0,0,1,0,0,1,0,,,,,,,,,0,0,0,0,1,0,0,0,0,,,,,,,,,,,,,,,,,,,,,,
"Redacted",,,,,,10,1,0,1,1,0,1,0,0,0,0,0,1,1,0,0,,0,,,0,,,,,0,0,1,0,0,0,0,0,,,,,,,,,,,,,,,,,,,,,,
"Redacted",,,,,,14,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,0,0,0,0,0,0,0,0,0,0,0,0,0,,0,,0,,,
"Redacted",,,,,,11,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,1,0,1,1,0,0,0,0,1,1,0,1,1,,1,1,,,,
````