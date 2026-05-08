email thread with philip 

From me 5/29/2025

I'm thinking about the scenario where the prover is lying/mistaken about which ballots contain the contest we are auditing. Ive been assuming that the MVR is truth as to whether the ballot contains the contest.

In Audit.py, overstatement_assorter(), there is a comment:

     If `use_style == True`, then if the CVR contains the contest but the MVR does not,
    that is considered to be an overstatement, because the ballot is presumed to contain
    the contest.
Is that a typo, should that say "the ballot is presumed to not contain the contest" ?

A possibly related issue is that if you give overstatement_assorter() an MVR and CVR that both do not contain the contest, you get a value of noerror = 1/(2 - margin/upper), which is always greater than 1/2. I would have expected it should return 1/2, so as to not reduce or increase the risk statistic.

More generally Im trying to reason about what the overstatement() values should be, and why.

If the contest does not appear on the MVR, hasStyle = true, then overstatementError is {1, 0, 1/2} depending if the CVR showed a vote for the {winner, loser, other}

If the contest does not appear on the MVR, hasStyle = false, then overstatementError is {1/2, -1/2, 0} depending if the CVR showed a vote for the {winner, loser, other}

If the contest does not appear on the CVR, hasStyle = false, then overstatementError is {-1/2, 1/2, 0} depending if the MVR showed a vote for the {winner, loser, other}

The last two make sense to me, but the first one I dont understand. Perhaps you do mean that hasStyle = true means "the ballot is presumed to contain the contest." ? But then how is it that the MVR should not be considered true?



From Philip 5/30/2025



use_style is an attribute of the sampling. If use_style, the sample to audit any particular contest is drawn only from cards the voting system claims have the contest (because the CVR has the contest), and **the assorted margin is not "diluted"** over any larger set of cards. CVRs that do not contain a contest do not have an assorter value for the contest; they did not contribute to the reported assorter mean.

If not use_style, the sample for auditing a contest is drawn from a pool of cards some of which the voting system might not have claimed contain the contest, and **the margin is "diluted"** over all the cards in the population from which the sample is drawn. CVRs that do not contain the contest will be assigned the value 1/2 by an assorter for that contest; that is their contribution to the reported assorter mean.

has_contest is a property of a CVR or MVR. If not use_style, there is no difference from the perspective of the audit between a CVR (or MVR) that does not contain the contest and a CVR (or MVR) that does not have a valid vote in the contest.

But if use_style, there is a difference. Because the CVR is not trusted (but the upper bound on the number of cards that contain the contest is trusted), if the CVR erroneously reports that a card contains the contest, some card that really did contain the contest was omitted from the pool from which the sample was drawn. That card might have contained a vote for which the assorter value was zero, so to be able to (provisionally) use CVR style data to target the sample without undermining the risk limit, we treat the MVR for the sampled card as if it had the least favorable value (assorter value 0).

If not use_style, when there is no discrepancy between the assorter applied to the MVR and the assorter applied to the CVR (e.g., because the CVR has a non-vote and the MVR does not contain the contest, or vice versa, or neither contains the contest), that is NOT neutral: the margin was already diluted over those cards and CVRs, **so the fact that the reported and true values agree is evidence that the total net overstatement is small, corresponding to an overstatement assorter value greater than 1/2**.

(consistent sampling = has style = reportedMargin, and uniform sampling = no style = dilutedMargin)

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

I think that there are two related misunderstandings in https://github.com/cdos-rla/colorado-rla that have a big impact on the current risk results.

let Npop = population size = the number of ballots that you are sampling over
let Nc = the number of ballots in the population that contain the contest

let nc = the number of samples needed that include the contest
let nd = the number of samples needed to reach the risk limit when drawing from a diluted population, when only some ballots contain the contest

In us.freeandfair.corla.math.Audit, both these methods are using nc:

public static BigDecimal pValueApproximation(
final int auditedBallots, // "auditedBallots" means  the number of needed samples that include the contest
final BigDecimal dilutedMargin,
final BigDecimal gamma,
final int oneUnder,
final int twoUnder,
final int oneOver,
final int twoOver)

// returns  the number of needed samples that include the contest                                      
public static BigDecimal optimistic(
final BigDecimal riskLimit,
final BigDecimal dilutedMargin,
final BigDecimal gamma,
final int twoUnder,
final int oneUnder,
final int oneOver,
final int twoOver)

colorado-rla uses  Audit.optimistic() to calculate nc, and mistakes it for nd.

it then retrieves nc ballots, but only Nc/Npop * nc of those ballots have the contest on it (on average).

In the example I am looking at, (Boulder 2024, 'State Representative District 10'), Nc/Npop is 44675/396121 = .113,
The estimated sample size is 105, based on a dilutedMargin of .07 = 27538/ 396121 (found in roundN/contest.csv).
If you look in roundN/contestComparison.csv, you will find 11 ballots with that contest on it, 11 ~ .113 * 105

colorado-rla then uses nc=105 (not 11) in Audit.pValueApproximation() and sees that the risk limit is satisfied.
So no one notices the problem.

In order to convert nc to nd you must multiply nc by Npop/ Nc
In the example you need to sample 105 / .113 = 929, so then you get 105 samples with the contest on it.

The irony is that consistent sampling would not only fix the problem, but you would only need 10 samples to reach the risk limit, because then the margin is 27538/44675 = .617 instead of 27538/ 396121 = .07.

I realize that its a big deal if the Colorado RLA is giving wrong results. Let me know if you see any flaws in my thinking. Ill keep it under wraps until then.

PS: I was reviewing "Super-Simple Simultaneous Single-Ballot Risk-Limiting Audits" and noticed how section 3 switched notation from N and Nc in the first part, and then (starting with eq 9) to n and N. Section 2 has "Draw at least ρ/µ ballots at random and audit them..." Nowhere does it mention the need to multiply by Npop/ Nc. I can't find it anywhere, not even in the "More style, less work..." paper. I guess we need to run it by Philip before we make conclusions.

///////////////////////////////////////////////////////////////////////////////////////////////////////////////

Hi Philip: 

Im thrashing about trying to understand colorado-rla current sampling strategy and how to duplicate it.

Heres the gist of the problem:

colorado-rla doesnt use consistent sampling, but within each county, they do a uniform sampling to audit a "target contest". 
Im looking at the Colorado 2024 general election, Boulder County, with target contest 'State Representative District 10'. 
This has a reported margin of 27538/44675 = 61.64% and a diluted margin of 27538/396121 = 6.95%.
The reported margin gives an estimated sample size of ~10, and the diluted margin gives an estimated sample size of ~105.

colorado-rla draws 105 samples from the county, but only ~10 have the contest in it. They use the reported margin in their
pValueApproximation code, based on the "Super Simple" paper, and conclude that the risk limit is satisfied.

Because the sample is not restricted to that contest, I think we have a "not use_style" audit and should use the diluted margin in the CLCA assorter. In which case the risk is far from being satisfied. I am using the Betting Risk estimator from the ALPHA paper, but I think the calculations are roughly equivilent, especially in the absence of errors.

So im wondering if one always uses the reported margin and not the diluted margin for the risk calculation, even for "not use_style" audits.
This seems wrong because the Clca assorter assigns the noerror value (1.0 / (2.0 - margin / upper)) to cvrs that dont contain the contest. Then
the risk is satisfied by 10 random ballots that dont even contain the contest. If the clca assorter ignored those by assigning 1/2 to them, and used the normal calculation for cvrs that did contain the contest, that would seem reasonable, and similar to what pValueApproximation does.

OTOH, using the diluted margin seems to penalize you twice, once when calculating the assort values, and once for having to sample 
10 times the number of ballots.

Anyway, Id appreciate your thoughts on what to do. Thanks.

-------

Hi John--

If you use the diluted margin, every card in the sample contributes to the evidence, whether it contains the contest or not: all 105 cards even though only ~10 contain the contest. The cards whose CVRs don't contain the contest were treated as having reported assorter values of 1/2, and the overstatement or understatement is calculated relative to that 1/2. So if the sampled card doesn't contain the contest, the assorter applied to the CVR equals the assorter applied to the card and the overstatement equals zero. Using the diluted margin is exactly equivalent to treating cards (or CVRs) that don't have the contest as having an assorter value of 1/2 for the purpose of auditing the contest, then using those assorter values in the audit.

------------------

yes but when using the diluted margin, you need 105 samples that contain the contest to reach the risk limit. But you have 105 samples total, and only 10 contain the contest, and you are far short of the risk limit.

You have to use the reported margin, then those 10 will reach the risk limit. but that only works if you return 0.5 (not noerror) for the clca assort value for cvrs that dont contain the contest. It seems to me thats what the Super Simple pvalue calculation essentially does.

-----------------

When you use the diluted margin, you need 105 cards; they don’t have to contain the contest. Their assorter value is 1/2 if they don’t.

The audit is checking whether the assorter applied to the CVRs overstated the mean assorter value over all cards—including those that don’t contain the contest—by the diluted margin or more. If not, the true assorter mean is greater than 1/2. Cards that don’t contain the contest are included in the assorter mean and in the overstatement calculation. The calculation also accounts for the fact that the CVR might contain the contest but the card not, and the card might contain the contest but the CVR not.

---------------------

So if you have 105 samples that match, then the risk limit is satisfied, no matter whether any of them contain the contest or not? Ok. Sorry that I keep stumbling over this.

And you use the diluted margin when you have done a uniform sampling, and the reported margin when you have a consistent sampling? And these correspond to "nostyle" and "style" respectively? and those same flags correspond to the flag that the clca assorter wants when deciding what to do when the mvr doesnt contain the contest for a consistent sampling?

-------------------------

So if you have 105 samples that match, then the risk limit is satisfied, no matter whether any of them contain the contest or not?
Yes. You're checking whether the average of a list that includes one number for every card, whether the card contains the contest or not, is greater than 1/2. Cards that don't contain the contest count as 1/2 in the average. Sampling them can help determine whether the overall average is greater than 1/2, because they count in the overall average, just like the other cards.

Ok. Sorry that I keep stumbling over this.

And you use the diluted margin when you have done a uniform sampling,
Yes

and the reported margin when you have a consistent sampling?
When you use style-based sampling. And there's a modification when you use ONEAudit together with style-based sampling, because you're implicitly adding contests to cards that didn't originally contain them when you use batch-level assorter means as assorter values.

And these correspond to "nostyle" and "style" respectively?
Yes. Style-based sampling is implemented using consistent sampling, but what lets you use the reported margin (adjusted for ONEAudit) is that you are auditing each contest using only cards that contain the contest, so only those cards contribute to the assorter mean, and only such cards can tell you whether the true assorter mean is really greater than 1/2.

and those same flags correspond to the flag that the clca assorter wants when deciding what to do when the mvr doesnt contain the contest for a consistent sampling?
Yes.

///////////

This seems wrong because the Clca assorter assigns the same value (1.0 / (2.0 - margin / upper)) to cvrs that dont contain the contest as to
ones that do and have no errors. Then
the risk is satisfied by 10 random ballots that dont even contain the contest.

////////////////

Can you reconcile samples from different strata by calculating the minimum sampling rate across all counties, and throwing out those that exceed that. All of the strata  are uniformly random within each county.












