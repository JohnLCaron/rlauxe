# Corla2020 Using votedatabase for Cvrs
6/27/26

We obtained the cvr data from https://votedatabase.com for the Colorado 2020 General elections, and used it to run
a real audit. This is for testing purposes only: the data is not official, some of the data is missing, in particular
the redacted data is largely unaccounted for.

## votedatabase

* missing San Juan county
* there are two counties (Monroe and Roosevelt) from some other state
* Baca has a copy of Huerfano's cvrs instead of Baca's cvrs.
* Garfield has an older or ad-hoc export format (workaround)
* Las Animas only has 106 cards out of ~8000
* Las Platas was missing the last 5 contest headers (fixed)
* El Paso generally reports .05% - .15% (40 - 600) more votes than auditcenter records

## redactions

* Most counties have no obvious redactions
* Boulder appears to be the only county that includes the redacted ballot counts in the export file. Adding them in made 
  Boulder County go from 66393 to 251 missing votes (2646 to 25 missing cards).
* Except for Boulder I am ignoring redactions for now

## auditcenter

* Gunnison, San Juan are missing from tabulate_county.csv
* probably can use cvr data to substitute for Gunnison (TODO)
* Grand,Town of Granby Board of Trustees, "Chris Michalowski, Natascha O'Flaherty, Kristie DeLay, Mary (Cathy) Tindle, Rebecca Quesada"
  but Natascha O'Flaherty is not listed in tabulate_county.csv. On purpose or accidental ? According to CVRs she has 496 votes
* countyTabs.csv has inconsistent candidate naming

## do votedatabase and auditcenter agree ?

The cvr data very closely match the contest vote counts in auditcenter.
All but 15 contests have cvrs within 1% of the auditcenter, and most are below .1%. 
It seems likely most differences are due to redacted ballots.
See [Vote Differences](Corla2020cvrDiff.md) for details.

## Summary of data (with 4 missing counties)

* missing counties are "Baca", "Gunnison", "Las Animas", "San Juan", which are left out of the audit
* total cards = 4103490
* total contests = 651
* 3 contests are below recountMargin of .005
* 30 contests have only 5 cards
* auditable contests = 526 (after removing NoLoser, MinMargin, and MinSize contests)

## Uniform vs Style Based Sampling

The Colorado 2020 General Election audit used a risk limit of 4%.

Here are several scenarios that differ in which contests are chosen for the style-based audit. The uniform sample is what was actually done and does not vary.

### only targeted contests

rlauxe nmvrs = 4153
corla nmvrs = 8245
contests under maxRisk (rlauxe) = 230 / 526 = 43%
contests under maxRisk (corla) = 364 / 526 = 69%

|               | rlauxe  |   corla  |
|---------------|---------|----------|
| under maxRisk |   230   |    364   |
| under      5% |   234   |    366   |
| under     10% |   259   |    381   |
| under     20% |   297   |    393   |
| under     30% |   321   |    397   |

### targeted contests and contests with estMvrs <= maxMvrs

maxMvrs = 120
rlauxe nmvrs = 8223
corla nmvrs = 8245
contests under maxRisk (rlauxe) = 482 / 526 = 91%
contests under maxRisk (corla) = 364 / 526 = 69%

|               | rlauxe  |   corla  |
|---------------|---------|----------|
| under maxRisk |   482   |    364   |
| under      5% |   482   |    366   |
| under     10% |   483   |    381   |
| under     20% |   487   |    393   |
| under     30% |   490   |    397   |

### targeted plus important contests

* targeted
* multicounty contests
* contestName.startsWith("Representative to the")
* contestName.startsWith("State")

rlauxe nmvrs = 7680
corla nmvrs = 8245
contests under maxRisk (rlauxe) = 379 / 526 = 72%
contests under maxRisk (corla) = 364 / 526 = 69%

|               | rlauxe  |   corla  |
|---------------|---------|----------|
| under maxRisk |   379   |    364   |
| under      5% |   384   |    366   |
| under     10% |   391   |    381   |
| under     20% |   402   |    393   |
| under     30% |   413   |    397   |

### all contests

rlauxe nmvrs = 19404
corla nmvrs = 8245
contests under maxRisk (rlauxe) = 526 / 526 = 100%
contests under maxRisk (corla) = 364 / 526 = 69%

|               | rlauxe  |   corla  |
|---------------|---------|----------|
| under maxRisk |   526   |    364   |
| under      5% |   526   |    366   |
| under     10% |   526   |    381   |
| under     20% |   526   |    393   |
| under     30% |   526   |    397   |

## Experiments with relaxed risk limits

### all contests with relaxed risks

* if estMvrs > 250, use 20% risk limit
* if estMvrs > 150, use 10% risk limit
* if estMvrs in [50, 150), use 5% risk kimit
* if estMvrs < 50, use audit risk limit

rlauxe nmvrs = 14056
corla nmvrs = 8245
contests under maxRisk (rlauxe) = 526 / 526 = 100%
contests under maxRisk (corla) = 367 / 526 = 69%

|               | rlauxe  |   corla  |
|---------------|---------|----------|
| under maxRisk |   526   |    367   |
| under      5% |   488   |    366   |
| under     10% |   511   |    381   |
| under     20% |   526   |    393   |
| under     30% |   526   |    397   |

### all contests > 500 with relaxed risks

* if estMvrs > 500, do not include
* if estMvrs > 250, use 20% risk limit
* if estMvrs > 150, use 10% risk limit
* if estMvrs in [50, 150), use 5% risk kimit
* if estMvrs < 50, use audit risk limit

rlauxe nmvrs = 11273
corla nmvrs = 8245
contests under maxRisk (rlauxe) = 519 / 526 = 98%
contests under maxRisk (corla) = 367 / 526 = 69%

|               | rlauxe  |   corla  |
|---------------|---------|----------|
| under maxRisk |   519   |    367   |
| under      5% |   485   |    366   |
| under     10% |   501   |    381   |
| under     20% |   519   |    393   |
| under     30% |   520   |    397   |

## TODO DONE

* contact Philip about uniform algorithm DONE
* read Garfield DONE
* remove small contests from being audited DONE
* remove Baca and Las Animas from tabulation DONE
  * could use Baca and Las Animas tabulate_county.csv entries to synthesize cvrs
  * could use Gunnison cvrs to synthesize its entry in tabulate_county.csv
* add Boulder redacted ballots back in (by simulation). DONE
* compare votedatabase and auditcenter votes DONE
* compare uniform and style sampling DONE
* look for problems and bugs in uniform vs style comparison DONE
* in separate election, generate synthetic cvrs for all counties in 2020 and compare to real ones DONE
* improve algorithm for generating synthetic cvrs DONE
* generate 2024 general election with synthetic cvrs DONE

## TODO

* show plot of incremental cost of adding the low margin contests: what do the lowest n contests cost ?

## Appendix A Votes differences between auditcenter and votedatabase

[See](Corla2020cvrDiff.md)

**Use the rlauxe viewer to see these interactively.**

## Appendix B track down bug in countyTabulation 

### Senator
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

### Presidential Electors
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

