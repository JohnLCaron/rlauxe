Sampling

Each contest must have a known population P_c of ballots it might be in. |P_c| is used for the diluted margin.

When theres one contest, or all ballots are in an undifferentiated pooll, then P_c = all.

In a CLCA where the Cvrs are complete, P_c = {cvrs with cvr.hasContest(id)}.

If the Cvrs are not complete because the undervotes are not recorded, fire your election vendor.

For Polling, there is no CVR. Annotate the cardManifest to indicate which cards might have that contest, using possibleCOntest field.

For OneAudit, you have some Cvrs and some pool cards. There are no Cvrs for the pool cards. The pool cards have possibleContests(), or
perhaps a refercne to a style.

One can factor out possibleContests() into CardStyles for efficiency. That could be easier to incorporate with a filter, 
so card doesnt have to have a reference to the styles.



=============

In a production audit, the cards are carefully selected to satisfy the P_c. An assertion audit must see only those cards in P_c.
So you have to compare <Mvr, Card> because only the Card has possibleContests(). Cvrs can be used for CLCA or all.
OR you can pass a filter into the Sampling. In general the filter will need the card.

