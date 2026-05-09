## PrecinctLevelResults


https://www.sos.state.co.us/pubs/elections/resultsData.html
https://www.sos.state.co.us/pubs/elections/Results/2024/2024GeneralPrecinctLevelResults.xlsx
convert to cvs and zip to corla/src/test/data/2024election/2024GeneralPrecinctLevelResults.zip

only have results for limited number of contests, no undervotes or card counts

````
County	Precinct	Contest	Choice	Party	Total Votes
ADAMS	4215601243	Presidential Electors	Kamala D. Harris / Tim Walz	DEM	224
ADAMS	4215601244	Presidential Electors	Kamala D. Harris / Tim Walz	DEM	237
ADAMS	4215601245	Presidential Electors	Kamala D. Harris / Tim Walz	DEM	64
...
````

Create a pool for each precinct.
Assume single ballot style TODO dont assume, but how, without card styles ?
This will put all contests on a single card. 
But Boulder, eg, has 2 cards, so boulder24/oa (where we know the card styles) has 2x the cards than corla/county/Boulder.

corla/Boulder totalCardCount=196152 2x= 392304
boulder24 totalCardCount=396697
