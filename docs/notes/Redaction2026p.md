# Redaction notes for Corla 2026 primary election
last changed 9/13/2026

We have cvrs from 4 counties for the 2026 primary: "Boulder", "La Plata", "Morgan", "Weld"
We run a contest with just those 4 county's votes.

## Compare CVR files (from auditcenter) with manifest (auditcenter)

CVR files are in github/nealmcb/auditcenter/2026/primary/observerfiles (Boulder downloaded separately)
Manifests are in github/nealmcb/auditcenter/2026/primary/files

**Boulder** redacts 2966 cvrs and aggregates redacted cvrs into 21 groups by style. The sum of the groups plus the unredacted cvr
tabulation equals the reported vote totals for the county.

**La Plata** has 9 row redactions and an AGGREGATION line
There are 50 cvrs not found in manifest.

**Morgan** has 25 row redactions and an AGGREGATION line

**Weld** has 20 row redactions

Summary of reading CVRs:

| county   | population | manifestCount | ncvrs | missing | minCardsForVote | nredactedCvrs | cvrNoManifest |
|----------|------------|---------------|-------|---------|-----------------|---------------|---------------|
| Boulder  | 100423     | 100423        | 97457 | 2966    | 2087            | 2966          | 0             |
| La Plata | 16146      | 16146         | 16137 | 9       | 9               | 9             | 50            |
| Morgan   | 5220       | 5220          | 5195  | 25      | 10              | 25            | 0             |
| Weld     | 69640      | 69640         | 69620 | 20      | 11              | 20            | 0             |

where

| field           | example | description                                           |
|-----------------|---------|-------------------------------------------------------|
| county          | Weld    | county name                                           |
| population      | 69640   | county population from round.ballotCardCount          |
| manifestCount   | 69640   | number of entries in the manifest                     |
| ncvrs           | 69620   | count of Cvrs that match entries in the Manifest      |
| missing         | 20      | manifestCount - ncvrs                                 |
| minCardsForVote | 11      | minimum cards needed for missing votes                |
| nredactedCvrs   | 20      | number of redacted cvrs given in CVR file             |
| cvrNoManifest   | 0       | count of Cvrs that dont match entries in the Manifest |


## Compare redaction variants for 4-county 2026 primary

We have cvrs from 4 counties for the 2026 primary: "Boulder", "La Plata", "Morgan", "Weld"

|           | ncontests | nredactions | ncards | npools | phantoms  | onePool | unredacted |
|-----------|-----------|-------------|--------|--------|-----------|---------|------------|
| Corla2026 | 16        | 3020        | 191379 | 4      | 3935 (15) | 424     | 368        | 
| Corla2026 | 16        | 3020        | 191379 | 4      | 442 (14)  | 424     | 368        | 

TODO test that extra cards in phantoms dont affect the diluted margin, ie the population size remains constant:
read the cardManifest and tabulate npops. Also, could add that to CorlaStateElection.

* Redactions are around 1.5% of total votes which accounts for the small effect.
* Boulder county redacted 2966/100423 = 3% of ballots, which skews the numbers in this 4-county contest.
* phantom variant does fine when the top two contests are not audited.
