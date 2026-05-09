
## How does Corla do their county-level sampling?

Im guessing that it does uniform sampling across all ballots in the county. 
So the population is all ballots in the county, and the sampling is simply a random draw from that population.
The number of samples is based on the margin of the "target" contest. 


Doesnt do "style based sampling" so presumably the margin is fully diluted, ie they didnt try to only sample the pilot contest,
which i think means the sampling is uniform.

We can assume that there are CVRs for all ballots. So we have the county-level subtotals for each contest,
as well as the reported number of ballots for each contest in the county,
which give us the "reported margin" within the county.

If there is an accurate, independent knowledge of the maximum number of ballots for each contest in the county, we can use
that for the trusted bound for each contest (aka Nc). Otherwise we have to use the ballot counts from the CVRs.

Without control over the sampling, we can only do "risk measuring" audits.
So, each county runs an independent audit for all contests, and we measure the risk for each contest in that county.
If the contest is contained within the county, then theres nothing else to be done.

If Corla is using the entire population size for the reported margin, then using the CVRs to determine the contests' actual
ballot count will be a big improvement.

Another problem might be:
Corla creates a pool for each precinct, assumes a single ballot style, this will put all contests on a since card.
But Boulder, eg, has 2 cards, so boulder24/oa (where we know the card styles) has 2x the cards than corla/county/Boulder.

corla/Boulder totalCardCount=196152 2x= 392304
boulder24 totalCardCount=396697
