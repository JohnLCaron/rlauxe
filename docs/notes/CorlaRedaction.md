# Corla Redaction

## Boulder

Boulder25 and Boulder26P have a seperate line for each redacted ballot, with "Redacted" in the first column, and no
identifying fields. We accumulate into Redacted Groups by BallotType, and each group becomes a OneAudit pool.

````
"CvrNumber","TabulatorNum","BatchId","RecordId","ImprintedId","CountingGroup","BallotType",
...
"Redacted",,,,,,16,0,1,1,1,0,0,1,0,0,0,1,0,0,1,0,,,,,,,,,0,0,0,0,1,0,0,0,0,,,,,,,,,,,,,,,,,,,,,,
"Redacted",,,,,,10,1,0,1,1,0,1,0,0,0,0,0,1,1,0,0,,0,,,0,,,,,0,0,1,0,0,0,0,0,,,,,,,,,,,,,,,,,,,,,,
"Redacted",,,,,,14,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,0,0,0,0,0,0,0,0,0,0,0,0,0,,0,,0,,,
"Redacted",,,,,,11,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,1,0,1,1,0,0,0,0,1,1,0,1,1,,1,1,,,,
````