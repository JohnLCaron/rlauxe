# Missing Ballots (aka phantoms-to-evil zombies)

TODO: rewrite

From P2Z paper:

    A listing of the groups of ballots and the number of ballots in each group is called a ballot manifest.

    What if the ballot manifest is not accurate?
    It suffices to make worst-case assumptions about the individual randomly selected ballots that the audit cannot find.
    This ensures that the true risk limit remains smaller than the nominal risk limit.

    The dead (not found, phantom) ballots are re-animated as evil zombies: We suppose that they reflect whatever would
    increase the P-value most: a 2-vote overstatement for a ballot-level comparison audit, 
    or a valid vote for every loser in a ballot-polling audit.

From SHANGRLA, section 3.4:

    Let NC denote an upper bound on the number of ballot cards that contain the contest. 
    Suppose that n ≤ NC CVRs contain the contest and that each of those CVRs is associated with a unique,
    identifiable physical ballot card that can be retrieved if that CVR is selected for audit.
    
    If NC > n, create NC − n “phantom ballots” and NC − n “phantom CVRs.” Calculate the assorter mean for all the CVRs,
    including the phantoms by treating the phantom CVRs as if they contain no valid vote in the contest 
    (i.e., the assorter assigns the value 1/2 to phantom CVRs). Find the corresponding assorter margin (v ≡ 2Ā − 1).
    [comment: so use 1/2 for assorter margin calculation].

    To conduct the audit, sample integers between 1 and NC.
    
    1. If the resulting integer is between 1 and n, retrieve and inspect the ballot card associated with the corresponding CVR.
        1. If the associated ballot contains the contest, calculate the overstatement error as in (SHANGRLA eq 2, above).
        2. If the associated ballot does not contain the contest, calculate the overstatement error using the value the 
           assorter assigned to the CVR, but as if the value the assorter assigns to the physical ballot is zero
           (that is, the overstatement error is equal to the value the assorter assigned to the CVR).
       2. If the resulting integer is between n + 1 and NC , we have drawn a phantom CVR and a phantom ballot. Calculate the
          overstatement error as if the value the assorter assigned to the phantom ballot was 0 (turning the phantom into an “evil zombie”),
          and as if the value the assorter assigned to the CVR was 1/2.
    
    Some jurisdictions, notably Colorado, redact CVRs if revealing them might compromise
    vote anonymity. If such CVRs are omitted from the tally and the number of phantom
    CVRs and ballots are increased correspondingly, this approach still leads to a valid RLA.
    But if they are included in the tally, then if they are selected for audit they should be
    treated as if they had the value u TODO (the largest value the assorter can assign) in calculating
    the overstatement error.

From SHANGRLA python code, assertion-RLA.ipynb:

    Any sampled phantom card (i.e., a card for which there is no CVR) is treated as if its CVR is a non-vote (which it is), 
    and as if its MVR was least favorable (an "evil zombie" producing the greatest doubt in every assertion, separately). 
    Any sampled card for which there is a CVR is compared to its corresponding CVR.
    If the card turns out not to contain the contest (despite the fact that the CVR says it does), 
    the MVR is treated in the least favorable way for each assertion (i.e., as a zombie rather than as a non-vote).

So it seems (case 1) we use 1/2 when calculating assorter margins, but during the actual audit, (case 2) we use 0 (polling) and 0? (comparison).

So we need a different routine for "find assorter margin" than "find assorter value". Probably.
Python code (Audit.py, Assorter) doesnt seem to reflect polling case 2 that I can find, but perhaps because the assort is passed in?

    The basic method is assort, but the constructor can be called with (winner, loser)
    instead. In that case,
        assort = (winner - loser + 1)/2

which corresponds to case 1.

------------------------------------------------------------------------------------

    Let N_c = upper bound on ballots for contest C.
    Let Nb = N (physical ballots) = ncvrs (comparison) or nballots in manifest (polling).
    When we have styles, we can calculate Nb_c = physical ballots for contest C.

    Let V_c = reported votes for contest C; V_c <= Nb_c <= N_c.
    Let U_c = undervotes for contest C; U_c = Nb_c - V_c >= 0.
    Let Np_c = nphantoms for contest C; Np_c = N_c - Nb_c.
    Then N_c = V_c + U_c + Np_c.

    Comparison, no styles: we have cvrs, but the cvr doesnt record undervotes.
    We know V_c and N_c. Cant distinguish an undervote from a phantom, so we dont know U_c, or Nb_c or Np_c.
    For estimating, we can use some guess for U_c.
    For auditing, I think we need to assume U_c is 0? So Np_c = N_c - V_c??
    I think we must have a ballot manifest, which means we have Nb, and ...

------------------------------------------------------------------------------------

The margin is calculated with both undervotes and phantoms = 1/2.
But in reality, the phantoms use "worst case" vote for the loser.
If the phantom pct is greater than the margin, the audit will fail.
When hasStyles, we know what that percent is.
So for estimation, we could calculate the margin with usePhantoms=true, since thats what were going to see during the audit.

If we have styles, we can count undervotes, and so we know Np. Since Np has such a strong effect, we will keep it per
contest and use it in the estimation and also the betting strategy.

Should use phantomPct for estimated 1-vote overstatement error rate estimate.

-------------------
From OneAudit, p 9:

    The stratum with linked CVRs comprised 5,294 ballots with 5,218 reported votes in the contest; 
    the “no-CVR” stratum comprised 22,372 ballots with 22,082 reported votes.

    * 5218/5294 = .0143
    * 22082/22372 = .0129