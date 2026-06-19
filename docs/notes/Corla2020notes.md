Corla2020 Notes
6/18/26

I can read in most of the cvr data from https://votedatabase.com for the Colorado 202 General elections. 
Much of the work is matching the contest and candidate names from the cvr export file to what's in auditcenter.

## votedatabase

* missing San Juan county
* there are two counties (Monroe and Roosevelt) from some other state
* Baca has a copy of Huerfano's cvrs instead of Baca's cvrs.
* Garfield has an older or ad-hoc export format 
* Las Animas only has 106 cards out of ~8000

## redactions

* Boulder, Doloros, Pitkin, possibly Jefferson have redactions and what looks like the totals of the redacted data.
* Another 6-8 counties have redactions but dont seem to have the totals of the redacted data.
* Most counties have no obvious redactions
* I am ignoring redactions for now

## auditcenter

* Gunnison, San Juan are missing from tabulate_county.csv
* probably can use cvr data to substitute for Gunnison (TODO)

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