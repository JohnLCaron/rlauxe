# Corla2020 Notes
6/20/26

I can read in most of the cvr data from https://votedatabase.com for the Colorado 202 General elections. 
Much of the work is matching the contest and candidate names from the cvr export file to what's in auditcenter.

## votedatabase

* missing San Juan county
* there are two counties (Monroe and Roosevelt) from some other state
* Baca has a copy of Huerfano's cvrs instead of Baca's cvrs.
* Garfield has an older or ad-hoc export format (workaround)
* Las Animas only has 106 cards out of ~8000
* Las Platas was missing the last 5 contest headers (fixed)
* El Paso generally reports .05% - .15% (40 - 600) more votes than auditcenter records

## redactions

* Boulder, Dolores, Pitkin, possibly Jefferson have redactions and what looks like the totals of the redacted data.
* Another 6-8 counties have redactions but dont seem to have the totals of the redacted data.
* Most counties have no obvious redactions
* I am ignoring redactions for now

## auditcenter

* Gunnison, San Juan are missing from tabulate_county.csv
* probably can use cvr data to substitute for Gunnison (TODO)
* Grand,Town of Granby Board of Trustees,"Chris Michalowski, Natascha O'Flaherty, Kristie DeLay, Mary (Cathy) Tindle, Rebecca Quesada"
  but Natascha O'Flaherty is not listed in tabulate_county.csv. On purpose or accidental ? According to CVRs she has 496 votes

## does votedatabase and auditcenter agree ?

The data very closely matches the total contest counts in auditcenter. Which is a pleasant surprise. But why do they differ?
The difference between cvr count and auditcenter Nc = number of phantoms needed. This is mostly due to missing ballots.
All but 17 contests have phantoms less than 1% ot t.

* missing counties are "Baca", "Gunnison", "Las Animas", "San Juan" and are left out of the audit
* San Juan has a population < 1000.
* could use Gunnison cvrs to synthesize its entry in tabulate_county.csv
* could use Baca and Las Animas tabulate_county.csv entries to synthesize cvrs
* could add Boulder redacted ballots back in (by simulation). (TODO)
* then see what else differs

## Summary of data (with 4 missing counties)

* total cards = 4100856
* total contests = 651
* 3 contests are below recountMargin of .005
* 30 contests have only 5 cards
* auditable contests = 526 (after removing NoLoser, MinMargin, and MinSize contests)

## Uniform Sampling 

* 8306 mvrs
* all but 62 contests have estimated risk <=  3%
* all but 48 contests have estimated risk <=  5%
* all but 41 contests have estimated risk <= 10%

## Style Based Sampling

* chose only targets to audit:
  * 4010 mvrs
  * about 210/560 of the contests have estimated risk < .03
* targets and other contests with estMvrs <= 100:
  * 8524 mvrs 
  * all but 62 contests have estimated risk <= .03
* targets and other contests with estMvrs < 500: in [150, 500) (10%), in [50, 150) (5%), < 50 (3%):
  * 15016 mvrs
  * all but 96 have estimated risk <=  3% 
  * all but 47 have estimated risk <=  5% (97)
  * all but 20 have estimated risk <= 10% (33)

## TODO

contact Philip about uniform algo DONE
read Garfield DONE
reorder contests?
remove Baca and Las Animas DONE
remove small contests DONE
add Gunnison
compare votedatabase and auditcenter votes DONE
compare uniform and style sampling

## Appendix A Votes diffrences between auditcenter and votedatabase

## Appendix B

## Senator
                                          V
contest = {0=1719784, 1=1419004, 2=9730, 3=1376, 4=55861, 5=55, 6=78, 7=4} sumVotes=3205892     Nc=3279180          Nu=73288
      ac =[0=1719784, 1=1419004, 2=9730, 3=8917, 4=55861, 5=55, 6=78, 7=4],  nvotes=3213433 ncards=3268188, undervotes=54755
   cvrs = [0=1719445, 1=1418651, 2=9724, 3=8913, 4=55873, 5=55, 6=78, 7=4],  nvotes=3212743 ncards=3268188, undervotes=55445

0 'John W. Hickenlooper': votes=1719784  (winner)
1 'Cory Gardner': votes=1419004
2 'Daniel Doyle': votes=9730
3 'Stephan "Seku" Evans': votes=1376      <-------
4 'Raymon Anthony Doane': votes=55861
5 'Danny Skelly': votes=55
6 'Bruce Lohmiller': votes=78
7 'Michael Sanchez': votes=4

## Presidential Electors
                                                          V                                V                                                                      V
co={0=1792789, 1=1354251, 2=5024, 3=2716, 4=8922, 5=353,  6=4870, 7=2499, 8=1990, 9=562,  10=60, 11=375, 12=491, 13=350, 14=195, 15=755, 16=1032, 17=609, 18=567,  19=16, 20=8033, 21=0, 22=3, 23=21}
ac=[0=1792789, 1=1354251, 2=5024, 3=2716, 4=8922, 5=353, 6=52104, 7=2499, 8=1990, 9=562, 10=635, 11=375, 12=491, 13=350, 14=195, 15=755, 16=1032, 17=609, 18=567, 19=175, 20=8033, 21=0, 22=3, 23=21],
cv=[0=1792457, 1=1353905, 2=5021, 3=2717, 4=8923, 5=353, 6=52112, 7=2500, 8=1993, 9=562, 10=635, 11=375, 12=491, 13=350, 14=195, 15=755, 16=1032, 17=609, 18=567, 19=175, 20=8037, 22=3,       23=21], 

all the discrepency is where we have a candidate mismatch, must be in CountyContestBuilder

0 'Joseph R. Biden / Kamala D. Harris': votes=1792789  (winner)
1 'Donald J. Trump / Michael R. Pence': votes=1354251
2 'Don Blankenship / William Mohr': votes=5024
3 'Bill Hammons / Eric Bodenstab': votes=2716
4 'Howie Hawkins / Angela Nicole Walker': votes=8922
5 'Blake Huber / Frank Atwood': votes=353
6 'Jo Jorgensen / Jeremy "Spike" Cohen': votes=4870                 <-------
7 'Brian Carroll / Amar Patel': votes=2499
8 'Mark Charles / Adrian Wallace': votes=1990
9 'Phil Collins / Billy Joe Parker': votes=562
10 'Roque "Rocky" De La Fuente / Darcy G. Richardson': votes=60       <-------
11 'Dario Hunter / Dawn Neptune Adams': votes=375
12 'Princess Khadijah Maryam Jacob-Fambro / Khadijah Maryam Jacob Sr.': votes=491
13 'Alyson Kennedy / Malcolm Jarrett': votes=350
14 'Joseph Kishore / Norissa Santa Cruz': votes=195
15 'Kyle Kenley Kopitke / Nathan Re Vo Sorenson': votes=755
16 'Gloria La Riva / Sunil Freeman': votes=1032
17 'Joe McHugh / Elizabeth Storm': votes=609
18 'Brock Pierce / Karla Ballard': votes=567
19 'Jordan "Cancer" Scott / Jennifer Tepool': votes=16                <-------
20 'Kanye West / Michelle Tidball': votes=8033
21 'Kasey Wells / Rachel Wells': votes=0
22 'Todd Cella / Timothy Bryan Cella': votes=3
23 'Tom Hoefling / Andy Prior': votes=21

ac=nvotes=3234451 ncards=3268190, undervotes=33739, 
cv=nvotes=3233788 ncards=3268190, undervotes=34402, 

county=Denver csvfile = /home/stormy/datadrive/votedatabase/cvr/Colorado/Denver/cvr.csv
no matches for original candidate 'Jo Jorgensen / Jeremy ''Spike'' Cohen' for contest 'Presidential Electors' county 'Denver'
no matches for original candidate 'Roque ''Rocky'' De La Fuente / Darcy G. Richardson' for contest 'Presidential Electors' county 'Denver'
no matches for original candidate 'Jordan ''Cancer'' Scott / Jennifer Tepool' for contest 'Presidential Electors' county 'Denver'
no matches for original candidate 'Stephan ''Seku'' Evans' for contest 'United States Senator' county 'Denver'

county=Garfield csvfile = /home/stormy/datadrive/votedatabase/cvr/Colorado/Garfield/cvr.csv
no matches for original candidate 'Jo Jorgensen / Jeremy ""Spike"" Cohen' for contest 'Presidential Electors' county 'Garfield'
no matches for original candidate 'Roque ""Rocky"" De La Fuente / Darcy G. Richardson' for contest 'Presidential Electors' county 'Garfield'
no matches for original candidate 'Princess Khadijah Maryam Jacob-Fambro/Khadijah Maryam Jacob' for contest 'Presidential Electors' county 'Garfield'
no matches for original candidate 'Jordan ""Cancer"" Scott / Jennifer Tepool' for contest 'Presidential Electors' county 'Garfield'
no matches for original candidate 'Stephan ""Seku"" Evans' for contest 'United States Senator' county 'Garfield'

county=La Plata csvfile = /home/stormy/datadrive/votedatabase/cvr/Colorado/La Plata/cvr.csv
no matches for original candidate 'Jo Jorgensen / Jeremy Spike Cohen' for contest 'Presidential Electors' county 'La Plata'
no matches for original candidate 'Roque Rocky De La Fuente / Darcy G. Richardson' for contest 'Presidential Electors' county 'La Plata'
no matches for original candidate 'Jordan Cancer Scott / Jennifer Tepool' for contest 'Presidential Electors' county 'La Plata'
no matches for original candidate 'Stephan Seku Evans' for contest 'United States Senator' county 'La Plata'

3 'Stephan "Seku" Evans': votes=1376      <-------


input.matchCanonicalCandidate(county, contest, cand) works, so where are we not using that ??  DominionConverter.convertToCard ??

but cvr reading is ok. problem must be when we actually  read the CountyTab with janked candidate names, not getting added to the contest sum 
so back to CountyContestBuilder
