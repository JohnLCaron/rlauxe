Sampling
11/23/2025

Each contest must have a known population P_c of cards it might be in. |P_c| is used for the diluted margin.

1. When theres one contest, or all ballots are in an undifferentiated pooll, then P_c = all.
2. In a CLCA where the Cvrs are complete, P_c = { cvrs with cvr.hasContest(id) }. Test cvr, not mvr.
3. If the Cvrs are not complete because the undervotes are not recorded, fire your election vendor. Meanwhile, annotate cards.
4. For Polling, there is no CVR. Annotate the cardManifest to indicate which cards might have that contest, using possibleContest field.
5. For OneAudit, you have some Cvrs and some pool cards. There are no Cvrs for the pool cards, use the annotation to see if its in P_c.
   Test cvr, not mvr.


annotation: A card has possibleContests(), or perhaps a reference to a style, in order to implement hasContest() = "is in P_c".
One can factor out possibleContests() into CardStyles for efficiency. That could be easier to incorporate with a filter, 
so card doesnt have to have a reference to the styles.

=============

In a production audit, the cards are selected to satisfy the P_c. An assertion audit must see only those cards in P_c.
So you have to compare <Mvr, Card> because only the Card has possibleContests(). Cvrs can be used for CLCA or all.
OR you can pass a filter into the Sampling. In general the filter will need the card.

So whats with hasStyle ?? Perhaps hasStyle is just used when constructing the card manifest ??
The other place is maybe in bassort(hasStyle)?

generalise haStyle: "all", "cvrsAreComplete", etc ??

==================
if you have card style for a cvr then you know the undervotes. so you dont need to record it
so possibleContests == poolContests OR possible contests in sample population
OA pools vs sample populations

=============================================

Attacks

1. Clca, SwitchWinnerMinAttack

* Create two-candidate Contest with given margin. No undervotes, no phantoms. candB is reported winner. Generate cards to match.
* Let diff = margin * Nc. In the mvrs, switch (diff + 3)/2 votes for candB to candA. Now candA is winner by 1 vote.


2. Clca, HideInUndervotesAttack

* Create two-candidate Contest with 
  undervotes = Nc*undervotePct. 
  nvotes = Nc - undervotes
  diff = undervotes + 1; win = (nvotes + diff)/2, lose = nvotes - win
  candB is reported winner. Generate cards to match.
* Let In the mvrs, switch all undervotes to candA. Now candA is winner by 1 vote.


3. Clca, HideInOtherGroupAttack

* create 2 card styles, style1 = contest1, contest2, style2 = contest2

* Create two-candidate Contest1 with given margin. No undervotes, no phantoms. candB is reported winner. 
  Let diff = margin * Nc. 
  In the cardManifest, switch (diff + 1) cards to style2
  In the mvrs, switch (diff + 1) cvrs from candB to candA

* the cardManifest tabulation fails for Clca, since the cvrs must be present.


4. OneAudit, HideInOtherPoolAttack; hasStyles = false

* create 2 card styles, style1 = contest1, contest2, style2 = contest2

* Create two-candidate Contest1 with given margin. No undervotes, no phantoms. candB is reported winner.
  Let diff = margin * Nc.
  In the cardManifest, switch (diff + 1) cards to style2
  In the mvrs, switch (diff + 1) cvrs from candB to candA

* the cardManifest tabulation fails for Clca, since the cvrs must be present.









