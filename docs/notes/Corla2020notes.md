Corla2020 Notes
6/19/26

I can read in most of the cvr data from https://votedatabase.com for the Colorado 202 General elections. 
Much of the work is matching the contest and candidate names from the cvr export file to what's in auditcenter.

## votedatabase

* missing San Juan county
* there are two counties (Monroe and Roosevelt) from some other state
* Baca has a copy of Huerfano's cvrs instead of Baca's cvrs.
* Garfield has an older or ad-hoc export format (fixed)
* Las Animas only has 106 cards out of ~8000
* Las Platas was missing the last 5 contest headers (fixed)
* El Paso generally reports .05% - .15% (40 - 600) more votes than auditcenter records

## Presidential Electors

````
Contest 'Presidential Electors' (372) PLURALITY voteForN=1 votes={0=1796603, 1=1360401, 2=5045, 3=2721, 4=8942, 5=355, 6=52216, 7=2507, 8=1997, 9=568, 10=636, 11=376, 12=493, 13=351, 14=195, 15=761, 16=1033, 17=612, 18=567, 19=175, 20=8058, 21=0, 22=3, 23=21} 

  undervotes=34544, 
  Nc=3279180 
  Nphantoms=0 
  Nu=34544  = Nc - sumVotes
  sumVotes=3244636
  Npop=3279180 hasStyle=true
````
cvrs have 3265572 cards, for a diff of 3279180-3265572 = 13606
cvrs have 3231170 votes, for a diff of 3196555-3231170 = -34645, ie 34645 more votes than were tallied in 

auditcenter: 
  nvotes=3234451 
  ncards=3265572, 
  undervotes=0, 
  novote=0, 
  overvotes=0)

cvrs:
  nvotes=3231170 
  ncards=3265572, 
  undervotes=34402, 
  novote=34402, 
  overvotes=0)

co [0=1796603, 1=1360401, 4=8942, 20=8058, 2=5045, 6=4870, 3=2721, 7=2507, 8=1997, 16=1033, 15=761, 17=612, 9=568, 18=567, 12=493, 11=376, 5=355, 13=351, 14=195, 10=60, 23=21, 19=16, 22=3, 21=0]
ac [0=1792789, 1=1354251, 6=52104, 4=8922, 20=8033, 2=5024, 3=2716, 7=2499, 8=1990, 16=1032, 15=755, 10=635, 17=609, 18=567, 9=562, 12=491, 11=375, 5=353, 13=350, 14=195, 19=175, 23=21, 22=3, 21=0]
cvr[0=1790747, 1=1353070, 6=52067, 4=8904, 20=8035, 2=5020, 3=2716, 7=2499, 8=1993, 16=1031, 15=754, 10=634, 17=609, 18=567, 9=562, 12=491, 11=375, 5=353, 13=349, 14=195, 19=175, 23=21, 22=3]

co {0=1796603, 1=1360401, 2=5045, 3=2721, 4=8942, 5=355, 6=52216, 7=2507, 8=1997, 9=568, 10=636, 11=376, 12=493, 13=351, 14=195, 15=761, 16=1033, 17=612, 18=567, 19=175, 20=8058, 21=0, 22=3, 23=21}
ac [0=1792789, 1=1354251, 2=5024, 3=2716, 4=8922, 5=353, 6=52104, 7=2499, 8=1990, 9=562, 10=635, 11=375, 12=491, 13=350, 14=195, 15=755, 16=1032, 17=609, 18=567, 19=175, 20=8033, 21=0, 22=3, 23=21],
cvr[0=1790747, 1=1353070, 2=5020, 3=2716, 4=8904, 5=353, 6=52067, 7=2499, 8=1993, 9=562, 10=634, 11=375, 12=491, 13=349, 14=195, 15=754, 16=1031, 17=609, 18=567, 19=175, 20=8035, 22=3, 23=21], 

ac and cvr agree, but the contest tab was messed up, now fixed

````
'Presidential Electors' (372) PLURALITY voteForN=1 votes={0=1796603, 1=1360401, 6=52216, 4=8942, 20=8058, 2=5045, 3=2721, 7=2507, 8=1997, 16=1033, 15=761, 10=636, 17=612, 9=568, 18=567, 12=493, 11=376, 5=355, 13=351, 14=195, 19=175, 23=21, 22=3, 21=0} undervotes=34544, winners=[0] Nc=3279180 Nphantoms=0 Nu=34544 sumVotes=3244636
   0 'Joseph R. Biden / Kamala D. Harris': votes=1796603  (winner)
   1 'Donald J. Trump / Michael R. Pence': votes=1360401 
   2 'Don Blankenship / William Mohr': votes=5045 
   3 'Bill Hammons / Eric Bodenstab': votes=2721 
   4 'Howie Hawkins / Angela Nicole Walker': votes=8942 
   5 'Blake Huber / Frank Atwood': votes=355 
   6 'Jo Jorgensen / Jeremy "Spike" Cohen': votes=52216 
   7 'Brian Carroll / Amar Patel': votes=2507 
   8 'Mark Charles / Adrian Wallace': votes=1997 
   9 'Phil Collins / Billy Joe Parker': votes=568 
   10 'Roque "Rocky" De La Fuente / Darcy G. Richardson': votes=636 
   11 'Dario Hunter / Dawn Neptune Adams': votes=376 
   12 'Princess Khadijah Maryam Jacob-Fambro / Khadijah Maryam Jacob Sr.': votes=493 
   13 'Alyson Kennedy / Malcolm Jarrett': votes=351 
   14 'Joseph Kishore / Norissa Santa Cruz': votes=195 
   15 'Kyle Kenley Kopitke / Nathan Re Vo Sorenson': votes=761 
   16 'Gloria La Riva / Sunil Freeman': votes=1033 
   17 'Joe McHugh / Elizabeth Storm': votes=612 
   18 'Brock Pierce / Karla Ballard': votes=567 
   19 'Jordan "Cancer" Scott / Jennifer Tepool': votes=175 
   20 'Kanye West / Michelle Tidball': votes=8058 
   21 'Kasey Wells / Rachel Wells': votes=0 
   22 'Todd Cella / Timothy Bryan Cella': votes=3 
   23 'Tom Hoefling / Andy Prior': votes=21 
    Total=3244636
````

Senator:
````
auditcenter = ContestTabulation(id=634 isIrv=false, voteForN=1, votes=[0=1719784, 1=1419004, 2=9730, 3=8917, 4=55861, 5=55, 6=78, 7=4], nvotes=3213433 ncards=3265570, undervotes=0, novote=0, overvotes=0)
cvrs = ContestTabulation(id=634 isIrv=false, voteForN=1, votes=[0=1717803, 1=1417777, 2=9722, 3=8908, 4=55826, 5=55, 6=78, 7=4], nvotes=3210173 ncards=3265570, undervotes=55397, novote=55397, overvotes=0)
````

would we set nphantoms = Nc - cvrs.ncards = 3279180 - 3265570 = 13610 = .004150428 ??

## redactions

* Boulder, Doloros, Pitkin, possibly Jefferson have redactions and what looks like the totals of the redacted data.
* Another 6-8 counties have redactions but dont seem to have the totals of the redacted data.
* Most counties have no obvious redactions
* I am ignoring redactions for now

## auditcenter

* Gunnison, San Juan are missing from tabulate_county.csv
* probably can use cvr data to substitute for Gunnison (TODO)
* Grand,Town of Granby Board of Trustees,"Chris Michalowski, Natascha O'Flaherty, Kristie DeLay, Mary (Cathy) Tindle, Rebecca Quesada"
  but Natascha O'Flaherty is not listed in tabulate_county.csv. On purpose or accidental ? According to CVRs she has 496 votes

## does votedatabase and auditcenter agree ?
The data very closely matches the total contest counts in auditcenter. Which is a pleasant surprise.


Overall we have all counties except Baca, Garfield, Gunnison, and San Juan. Should be able to recover Garfield and Gunnison.

## Summary of data (with 4 missing counties)

* missing counties are "Baca", "Gunnison", "Las Animas", "San Juan"
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

/////

contact Philip about uniform algo DONE
read Garfield DONE
reorder contests?
remove Baca and Las Animas DONE
remove small contests DONE
add Gunnison
compare votedatabase and auditcenter total votes
compare uniform and style sampling