# OneAudit (overstatement net equivalent audit)
_last changed 9/10/2025_

*** ARCHIVED not active ***

## OneAudit CLCA

OneAudit is a type of CLCA audit, based on the ideas and mathematics of the ONEAudit paper (see appendix).
It deals with 2 cases:

1. CVRS are not available, only subtotals by batch ("Batch level data"), for example by precincts.
2. CVRS are available for some, but not all ballots. The remaining ballots are in one or more batches
   for which subtotals are available. This is a "hybrid" or "heterogenous" audit.

The basic idea is to create an “overstatement-net-equivalent” (ONE) CVR for each batch, and use the average assorter
value in that batch as the value of the (missing) CVR in the CLCA overstatement.

One of the advantages of OneAudit is that one only has to retrieve the physical ballots that are chosen for auditing,
rather than retrieving all the physical ballots in a batch.

Only PLURALITY and IRV can be used with OneAudit.

### Auditing Batch level data with OneAudit

OneAudit "lets audits use batch-level data far more efficiently than traditional batch-level comparison RLAs (BLCAs) do:
create ONE CVRs for each batch, then apply CLCA as if the voting system had provided those CVRs. For
real and simulated data, this saves a large mount of work compared to manually
tabulating the votes on every card in the batches selected for audit, as BLCAs
require. If batches are sufficiently homogeneous, the workload approaches that
of “pure” CLCA using linked CVRs from the voting system." ONEAudit p 13.

The code in OneAuditContest handles BLCAs by setting cvrNc = 0 (and cvrVotes to empty). All CVRS must be part of a pool.

In this case, we find the minimum assort value over all pools and create an
affine transformation of the over-statement assorter values that subtracts the minimum assort value and
rescales so that the null mean is 1/2. See ONEAudit eq (1), p 12.

**TODO**: Test how much the affine transformation helps the sample size.

### Auditing heterogenous voting systems with OneAudit

CVRS are available for some, but not all ballots. When a ballot has been chosen for hand audit:

1. If it has a CVR, we use the standard CLCA over-statement assorter value for the ballot.
2. If it has no CVR, we use the overstatement-net-equivalent (ONE) CVR from the batch that it belongs to.

For results, see [OneAudit version 3](docs/OneAudit3.md).

Older version: [OneAudit version 2](docs/OneAudit2.md).

### Contest counts in the Pooled data

````
"With ONEAudit, you have to start by adding every contest that appears on any card in a tally 
batch to every card in that tally batch and increase the upper bound on the number of cards 
in the contest appropriately."

"The code does use card style information to infer which contests are on one or more cards 
in the batch, but with the pooled data, we don't know which CVR goes with which piece of paper: 
we don't know which card has which contests. We only that this pile of paper goes with this 
group of CVRs. ONEAudit assigns the reported assorter mean to each CVR in the batch. 
The mean is over all cards in the batch, since we don't know which cards have which contests." 

(Phillip Stark, private communication)
````

Even though we dont have CVRs for pooled ballots, we still have to have a ballot manifest (aka "Card Location" manifest) that includes each one. The ballot manifest might identify a pooled ballot only by their position in the batch or it might have an id that has been printed on the ballot. We might have card style information or not. The ballot manifest might have all the information that a CVR has, except for which candidates were voted for. In other words, all the possible variations that a polling audit might have.

When a pooled ballot is selected for sampling, we use the ballot manifest to retrieve the paper ballot and create an MVR. The MVR always has the complete set of contests and votes for that ballot. So when we calculate the assort value for a particular contest and assertion, we always know whether the contest appears on the ballot on not.

For each pool we always have a pool count, which has a list of contests and their votes in the pool, and the number of ballots in the pool. However, we may not know the number of cards for each contest in each pool, that is, we may not know how many undervotes there are by contest. If we do know the undervotes by contest, we then know Nc_g for each contest and can accurately calculate the assorter mean of the pool.

The case that Philip is talking about I think is when we dont know how many cards in each pool contain a given contest (or equivilently the number of undervotes by contest by pool). It is analogous to hasStyles = false in regular audits, although in that case, since we know Nc, the total ballots for a contest, we can correctly calculate the reported margin and the average assort value. The main effect of hasStyles = false is when it comes to sampling: we have to sample with a factor of (Nb / Nc) more ballots, where Nb is the number of physical ballots the contest might be in, and Nc in the number of ballots its actually in. (However, note this only comes into play when doing rounds of sampling; one can ignore this issue for the "one-mvr at a time" audit.)

For OneAudit, we also need to know Nc_g, the number of cards for this contest for each pool g so that we can calculate the average assort values in each pool. Instead, we may only know N_g, the total number of cards in pool g.

The reported assorter mean is over all cards in the batch (when you dont know Nc_g), which makes the mean smaller than it really is.
````
reported assorter mean = (winner - loser) / Npool <=  actual assorter mean =  (winner - loser) / NCpool, since Npool >= NCpool
reported assorter margin =  2 * (reported assorter mean) - 1 <= actual assorter margin = 2 * (actual assorter mean) - 1, since reported assorter mean <= actual assorter mean)
````

**TODO**: understand why Philip's "add every contest that appears on any card in a tally batch to every card in that tally batch and increase the upper bound on the number of cards in the contest appropriately" works.


### OneAudit for Redacted data

We have the use case of "redacted ballots" where we only get pool totals, and perhaps thats an instance where we might have pools but the admin is willing to give us the contest counts in each pool. (Since we then dont need ballot information, this may satisfy the privacy concern that redacted ballots is used for.)

CreateBoulderElectionOneAudit explores creating a OneAudit and making the redacted CVRs into OneAudit pools.

Findings so far:

1. Boulder County apparently does not publish the number of ballots in each pool.
2. Also does not publish the number of ballots for each contest in each pool, ie, the undervotes.
3. While we still can do a simulation with CreateBoulderElectionOneAudit, we probably cant do a real audit with existing published data.
4. At a minimum we need (1).

**TODO**: Assuming we have (1), whats the consequences of not having (2) ??

