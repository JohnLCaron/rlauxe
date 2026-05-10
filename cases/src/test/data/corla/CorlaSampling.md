# Corla Sampling algo

From /home/stormy/Downloads/sun_apr_26_2026_auditcenter_inconsistency_in_colorado_rla.md
Claude via Neal

## Step-by-Step: The Ballot Selection Algorithm

### Step 1 — Construct the Domain (Total Ballots Cast)

```java
// BallotSelection.java, randomSelection()
final int domainSize = ballotsCast(contestResult.countyIDs()).intValue();
```

`ballotsCast()` sums the total ballots across **all counties participating in the contest**, queried from the `ballot_manifest_info` table. This is the PRNG upper bound: every ballot in scope gets a unique integer in `[1, domainSize]`.

## Single-County Audits

For a **county-wide contest** (`AuditReason.COUNTY_WIDE_CONTEST`):

- `contestResult.countyIDs()` returns **one county**.
- `domainSize` = total ballots in that single county's manifest.
- The "ultimate sequence" projection is trivially just the county's own manifest sequence.
- Random numbers map directly to that county's batches.
- The `Selection` object has exactly **one `Segment`** (one county).

## State-Wide Audits

For a **statewide contest** (`AuditReason.STATE_WIDE_CONTEST`), e.g., a Governor's race appearing on ballots in all 64 Colorado counties:

- `contestResult.countyIDs()` returns **all participating counties** (potentially all 64).
- `domainSize` = sum of ballots across all of those counties' manifests.
- The `projectUltimateSequence()` function stitches all counties' batch manifests into a single global integer namespace, sorted by `countyID` then `sequenceEnd`.
- A single PRNG sequence produces numbers across the full combined domain.
- `selectTributes()` then routes each random number to the correct county+batch via `isHolding()`.
- The `Selection` object has **one `Segment` per county**, each populated with only the ballots that fell within that county's slice of the global range.

Each county's audit board then only sees and handles **their own segment**.


## Sub-State, Multi-County Districts ("Larger than a County but Less than the Whole State")

The `AuditReason` enum includes:

```java
GEOGRAPHICAL_SCOPE("Geographical Scope")
```

This enum value exists precisely to represent contests with a geographic scope that is **not a single county and not the entire state** — for example, a congressional district, judicial district, or school district spanning multiple counties.

**The code fully supports this case.** The `ContestResult` model stores a `Set<County>` (not a boolean flag for state/county), and `randomSelection()` simply uses `contestResult.countyIDs()` — regardless of how many counties that is. If a contest spans 5 counties, the domain is the total ballots across those 5 counties, the ultimate sequence is projected across their combined manifests, and each county gets its own segment.

```java
// ContestResult.java
public Set<County> getCounties() {
    return Collections.unmodifiableSet(this.counties);
}
public Set<Long> countyIDs() { ... }  // used as the scope for domainSize and selectTributes
```

**However**, there is a practical caveat: the system's UI and workflow was designed primarily around the state-vs-county dichotomy in Colorado. The `GEOGRAPHICAL_SCOPE` audit reason is present but there is no dedicated UI flow for district-level contest setup separate from the general contest targeting mechanism. Operationally, as long as the `ContestResult` is populated with the correct participating counties (which happens during contest ingestion from CVRs), the sampling algorithm handles any subset of counties transparently.


| Audit Scope           | countyIDs size | Domain Size                      | Segments     | Notes                                            |
|-----------------------|----------------|----------------------------------|--------------|--------------------------------------------------|
| Single-county         | 1              | County manifest total            | 1            | Trivial projection                               |
| Multi-county district | 2–N            | Sum of district county manifests | N            | Fully supported; use `GEOGRAPHICAL_SCOPE` reason |
| Statewide             | All            | Sum of all county manifests      | All counties | Primary design target                            |


/////////////////////////////////////////////////////

So it would seem that for each target, there is a uniform sampling across all the counties that contain it. We see the sample size
in the roundX/contest.csv file :

````
data class CorlaContestRoundCsv(
    val contestName: String,
    val nwinners: Int,                  // usually VoteForN
    val ballotCardCount: Int,           // Npop
    val contestBallotCardCount: Int,    // Nc
    val winners: String,
    val minMargin: Int,                 // use minMargin/Npop to check dilutedMargin consistent with optimisticSamplesToAudit
    val riskLimit: Double,
    val gamma: Double,
    val optimisticSamplesToAudit: Int, 
    val estimatedSamplesToAudit: Int,  // always same as optimisticSamplesToAudit?
)
````

All targeted contests are either single-county or statewide except for one:

                                                                           ---from tabulateCounty---     --------from contestRound-----
contestName, countyName                                                 winner,  loser, voteMargin,   voteMargin,    npop, margin, nsamples, calcSamples
Arapahoe, District Attorney 18th Judicial District                      175078,  128368,  46710,           46710,  351540, 0.133,         55,    52

canonical has

Arapahoe,District Attorney - 18th Judicial District,"Amy Padden,Carol Chambers"
Elbert,District Attorney - 18th Judicial District,"Amy Padden,Carol Chambers"

tabulateCounty has

Elbert,District Attorney - 18th Judicial District,Amy Padden,0
Elbert,District Attorney - 18th Judicial District,Carol Chambers,0

so we will assume it araphoe only.

District Attorney - 18th Judicial District,county_wide_contest,in_progress,1,351540,351540,"""Amy Padden""",46710,0.03000000,0,0,0,0,0,0,0,1.03905000,0,55,55
District Attorney - 18th Judicial District          351540,  351540,       55, county_wide_contest

351540 is npop for Arapahoe, but we use this to get Npop

## Computing the opportunistic risk for non-targeted Multi-county contests

For each such contest, form the set of counties and their Npop:


Contest 'Representative to the 119th United States Congress - District 1'
Nc=372303 Nphantoms=0 Nu=26568 sumVotes=345735
0/1 votes=264606/74598 diff=190008 (w-l)/w =0.7181 Npop=1802938 dilutedMargin=10.5388% recountMargin=71.8079%

county,     Npop,     nmvrs  
Arapahoe,   351540,    52       6760    0.000147921     smallest
Denver,    1104271,   258       4280    0.000233638     163
Jefferson,  367779,    66       5572    0.000179456     54
Statewide              22
           1823590

the sum of the county Npop is the contest's nPop, 1823590 close to Npop=1802938. Note Nc = 372303, 5x smaller.

reducing to lowest sampling gives 52+163+54= 269
diluted margin is 190008/1802938 = .105


///////////////////////////////
````
Merged Contest Info

contest                                               Npop,      Nc, voteMargin, countyMvrs, stateMvrs, Ncounties, auditReason
17th Judicial District Ballot Question 7B           516401,  279529,   37549,       165,        3,            2,   opportunistic_benefits
Adams 12 Five Star Schools Ballot Issue 5D          516401,  117043,   12622,        76,        3,            2,   opportunistic_benefits
Adams 12 Five Star Schools Ballot Issue 5E          516401,  117043,   10481,        76,        3,            2,   opportunistic_benefits
Adams County Ballot Issue 1A                        468858,  231986,   85596,       112,        0,            1,   opportunistic_benefits
Adams County Commissioner - District 1              468858,  236872,   13085,       102,       17,            1,   opportunistic_benefits
Adams County Commissioner - District 2              468858,  236872,   44326,       102,       17,            1,   opportunistic_benefits
Adams County Commissioner - District 5              468858,  236872,   17761,       102,       17,            1,   county_wide_contest
Adams County Court Judge - Dinnel                   468858,  236872,   60800,       102,       17,            1,   opportunistic_benefits
Adams County Court Judge - Flaum                    468858,  236872,   47948,       102,       17,            1,   opportunistic_benefits
Adams County Court Judge - Ivey                     468858,  236872,   49346,       102,       17,            1,   opportunistic_benefits
Adams County Court Judge - Jean                     468858,  236872,   54476,       102,       17,            1,   opportunistic_benefits
Adams County Court Judge - Kirby                    468858,  236872,   60202,       102,       17,            1,   opportunistic_benefits
Adams County Court Judge - Nowak                    468858,  236872,   55743,       102,       17,            1,   opportunistic_benefits
Adams County School District 14 Ballot Issue 4A     468858,   10619,     241,         7,        0,            1,   opportunistic_benefits
Adams County School District 14 Ballot Issue 4B     468858,   10619,     109,         7,        0,            1,   opportunistic_benefits
Adams-Arapahoe School District 28J Ballot Issue 5A  799746,   84894,   22669,        25,        2,            2,   opportunistic_benefits
Adams-Arapahoe School District 28J Ballot Issue 5B  799746,   84894,   39107,        25,        2,            2,   opportunistic_benefits
Affordable Housing 0.5% Sales Tax Increase           14627,   13566,    2860,        80,        0,            1,   opportunistic_benefits
Alamosa County Commissioner - District 1             15216,    7671,    1757,        32,        1,            1,   county_wide_contest
Alamosa County Commissioner - District 3             15216,    7671,       0,        32,        1,            1,   opportunistic_benefits
Alamosa School District RE-11J Ballot Issue 5A       19412,    6832,     331,        29,        0,            2,   opportunistic_benefits
Amendment 79 (CONSTITUTIONAL)                      4746866, 3239682,  741720,      3926,      199,           63,   opportunistic_benefits
Amendment 80 (CONSTITUTIONAL)                      4743138, 3235954,   42047,      3868,      199,           62,   opportunistic_benefits
Amendment 80 (CONSTITUTIONAL) - Rio Blanco            3728,    3728,     474,        58,        0,            1,   county_wide_contest
Amendment G (CONSTITUTIONAL)                       4746866, 3239682, 1398674,      3926,      199,           63,   opportunistic_benefits
Amendment H (CONSTITUTIONAL)                       4746866, 3239682, 1356513,      3926,      199,           63,   opportunistic_benefits
Amendment I (CONSTITUTIONAL)                       4746866, 3239682, 1103998,      3926,      199,           63,   opportunistic_benefits
Amendment J (CONSTITUTIONAL)                       4746866, 3239682,  882306,      3926,      199,           63,   opportunistic_benefits
Amendment K (CONSTITUTIONAL)                       4746866, 3239682,  297433,      3926,      199,           63,   opportunistic_benefits
Approval of Multiple Year Financial Obligation wit    4183,      66,      32,         3,        0,            1,   opportunistic_benefits
Arapahoe County Ballot Issue 1A                     330888,  330888,  127667,        52,       17,            1,   opportunistic_benefits
Arapahoe County Commissioner - District 1           330888,   80484,   11461,        15,        4,            1,   opportunistic_benefits
Arapahoe County Commissioner - District 3           330888,   71410,    1756,        14,        7,            1,   opportunistic_benefits
Arapahoe County Commissioner - District 5           330888,   43867,   14268,         6,        0,            1,   opportunistic_benefits
Arapahoe County Court - Hernandez                   330888,  330888,   95117,        52,       17,            1,   opportunistic_benefits
Arapahoe County Court - Williford                   330888,  330888,   97788,        52,       17,            1,   opportunistic_benefits
Archuleta County Commissioner - District 1            9489,    9489,    1878,        38,        0,            1,   county_wide_contest
Archuleta County Commissioner - District 2            9489,    9489,       0,        38,        0,            1,   opportunistic_benefits
Archuleta County Coroner                              9489,    9489,       0,        38,        0,            1,   opportunistic_benefits
Arvada Fire Protection District Ballot Question 6A  367779,    7078,    3557,         0,        2,            1,   opportunistic_benefits
Ault Fire Protection District Ballot Issue 6A       182397,    3209,     586,         0,        0,            1,   opportunistic_benefits
Ault Fire Protection District Ballot Issue 6B       182397,    3209,     528,         0,        0,            1,   opportunistic_benefits
BNC Metropolitan District No 2 Ballot Issue 6N      468858,     421,     129,         0,        0,            1,   opportunistic_benefits
BNC Metropolitan District No 2 Ballot Question 6M   468858,     421,      91,         0,        0,            1,   opportunistic_benefits
BRUSH RURAL FIRE PROTECTION DISTRICT BALLOT ISSUE    16507,    1068,     104,         2,        0,            2,   opportunistic_benefits
Baca County Commissioner - District 1                 2165,    2034,       0,        88,        0,            1,   opportunistic_benefits
Baca County Commissioner - District 3                 2165,    2034,       0,        88,        0,            1,   opportunistic_benefits
Baca County Coroner                                   2165,    2034,       0,        88,        0,            1,   opportunistic_benefits
Baca County Court Judge - Lishchuk                    2165,    2034,      30,        88,        0,            1,   opportunistic_benefits
Bannock Ballot Issue 6A                             248173,      23,       5,         0,        0,            1,   opportunistic_benefits
Bent County Commissioner-District 1                   2221,    2221,     537,        32,        0,            1,   county_wide_contest
Bent County Commissioner-District 3                   2221,    2221,       0,        32,        0,            1,   opportunistic_benefits
Bent County Court - Clark                             2221,    2221,     748,        32,        0,            1,   opportunistic_benefits
Boulder County Commissioner - District 1            396121,  198600,  142746,        48,       15,            1,   opportunistic_benefits
Boulder County Commissioner - District 2            396121,  198600,   91515,        48,       15,            1,   opportunistic_benefits
Boulder County Coroner                              396121,  198600,       0,        48,       15,            1,   opportunistic_benefits
Boulder County Court - Martin                       396121,  198600,   82792,        48,       15,            1,   opportunistic_benefits
Broomfield Ballot Question 2A                        47543,   47543,    8239,        53,        3,            1,   opportunistic_benefits
Broomfield Ballot Question 2B                        47543,   47543,   17739,        53,        3,            1,   opportunistic_benefits
Broomfield Ballot Question 2C                        47543,   47543,     464,        53,        3,            1,   opportunistic_benefits
Broomfield Ballot Question 2D                        47543,   47543,   10691,        53,        3,            1,   opportunistic_benefits
Broomfield Ballot Question 2E                        47543,   47543,    9993,        53,        3,            1,   opportunistic_benefits
Broomfield Ballot Question 2F                        47543,   47543,   17256,        53,        3,            1,   opportunistic_benefits
Broomfield Ballot Question 2G                        47543,   47543,    6780,        53,        3,            1,   county_wide_contest
Broomfield County Court - DeWick                     47543,   47543,   19279,        53,        3,            1,   opportunistic_benefits
Byers School District No. 32J Ballot Issue 5C       799746,    1585,     246,         0,        2,            2,   opportunistic_benefits
CANON CITY AREA METROPOLITAN RECREATION AND PARK D   26051,   15275,     542,        14,        0,            1,   opportunistic_benefits
CANON CITY AREA METROPOLITAN RECREATION AND PARK D   26051,   15275,      89,        14,        0,            1,   opportunistic_benefits
CITY OF CANON CITY ISSUE #2A SALES AND USE TAX INC   26051,    9130,     611,         8,        0,            1,   opportunistic_benefits
CITY OF TRINIDAD BALLOT ISSUE 2A                      7886,    3952,     645,        15,        0,            1,   opportunistic_benefits
CLEAR CREEK COUNTY BALLOT ISSUE 1A                    6180,    6180,    3430,        72,        1,            1,   opportunistic_benefits
CLEAR CREEK COUNTY LIBRARY DISTRICT BALLOT ISSUE 6    6180,    6180,    1906,        72,        1,            1,   opportunistic_benefits
Calhan School District No. RJ1 Ballot Question 5B   387297,    2016,     894,         0,        0,            1,   opportunistic_benefits
Calhan School District RJ1 Question 5B               20652,     118,      61,         0,        0,            1,   opportunistic_benefits
Carbon Valley Parks and Recreation District Ballot  182397,   22714,    4026,        11,        3,            1,   opportunistic_benefits
Chaffee County Commissioner - District 1             14627,   14627,    1868,        89,        0,            1,   opportunistic_benefits
Chaffee County Commissioner - District 2             14627,   14627,    1220,        89,        0,            1,   county_wide_contest
Chaffee County Court - Bull                          14627,   14627,    5433,        89,        0,            1,   opportunistic_benefits
Cherry Creek School District No. 5 Ballot Issue 4A  330888,  172327,   21424,        23,        8,            1,   opportunistic_benefits
Cherry Creek School District No. 5 Ballot Issue 4B  330888,  172327,   17400,        23,        8,            1,   opportunistic_benefits
Cheyenne County Commissioner - District 1             1064,    1064,       0,        20,        0,            1,   opportunistic_benefits
Cheyenne County Commissioner - District 3             1064,    1064,       0,        20,        0,            1,   opportunistic_benefits
Cheyenne County Coroner                               1064,    1064,       0,        20,        0,            1,   opportunistic_benefits
Cimarron Hills Fire Protection District Ballot Iss  387297,    7193,     531,         3,        1,            1,   opportunistic_benefits
City Council Member                                   2384,     997,     116,        28,        0,            1,   opportunistic_benefits
City of Alamosa Ballot Issue 2A                      15216,    3791,     376,        16,        0,            1,   opportunistic_benefits
City of Alamosa Ballot Issue 2B                      15216,    3791,     424,        16,        0,            1,   opportunistic_benefits
City of Aspen Ballot Issue 2A: Extension of Real E   11348,    4556,    1281,        12,        0,            1,   opportunistic_benefits
City of Aspen Ballot Issue 2B: Extension of Existi   11348,    4556,    1814,        12,        0,            1,   opportunistic_benefits
City of Aspen Ballot Issue 2C: Imposition of a Use   11348,    4556,    2866,        12,        0,            1,   opportunistic_benefits
City of Aurora Question 3A                         1047919,  165017,   20891,        41,        7,            3,   opportunistic_benefits
City of Boulder Ballot Question 2C                  396121,   57027,    8900,        21,        0,            1,   opportunistic_benefits
City of Boulder Ballot Question 2D                  396121,   57027,    6385,        21,        0,            1,   opportunistic_benefits
City of Boulder Ballot Question 2E                  396121,   57027,      64,        21,        0,            1,   opportunistic_benefits
City of Central Alderman/City Council                 4183,     410,      72,        16,        0,            1,   opportunistic_benefits
City of Cherry Hills Village Ballot Question 2A     330888,    4437,     318,         0,        0,            1,   opportunistic_benefits
City of Cherry Hills Village Ballot Question 300    330888,    4437,    2344,         0,        0,            1,   opportunistic_benefits
City of Cherry Hills Village Balot Question 2B      330888,    4437,    2613,         0,        0,            1,   opportunistic_benefits
City of Cherry Hills Village Councilmember Distric  330888,    4437,       0,         0,        0,            1,   opportunistic_benefits
City of Cherry Hills Village Councilmember Distric  330888,    4437,       0,         0,        0,            1,   opportunistic_benefits
City of Cherry Hills Village Councilmember Distric  330888,    4437,       0,         0,        0,            1,   opportunistic_benefits
City of Cherry Hills Village Mayor                  330888,    4437,       0,         0,        0,            1,   opportunistic_benefits
City of Colorado Springs Ballot Issue 2C            387297,  254918,  111448,        85,       17,            1,   opportunistic_benefits
City of Colorado Springs Ballot Question 2D         387297,  254918,    2735,        85,       17,            1,   opportunistic_benefits
City of Colorado Springs Ballot Question 300        387297,  254918,   22371,        85,       17,            1,   county_wide_contest
City of Craig Ballot Question 2A                      6699,    4071,    1073,        27,        0,            1,   county_wide_contest
City of Dacono Ballot Question 2G                   182397,    3109,     622,         3,        0,            1,   opportunistic_benefits
City of Dacono Ballot Question 2H                   182397,    3109,     504,         3,        0,            1,   opportunistic_benefits
City of Dacono Ballot Question 2I                   182397,    3109,     146,         3,        0,            1,   opportunistic_benefits
City of Dacono Ballot Question 2J                   182397,    3109,    1584,         3,        0,            1,   opportunistic_benefits
City of Dacono Council Members                      182397,    3109,       0,         3,        0,            1,   opportunistic_benefits
City of Dacono Mayor                                182397,    3109,    1200,         3,        0,            1,   opportunistic_benefits
City of Englewood Ballot Issue 2C                   330888,   18734,     272,         2,        2,            1,   opportunistic_benefits
City of Englewood Ballot Question  302              330888,   18734,    5812,         2,        2,            1,   opportunistic_benefits
City of Englewood Ballot Question 301               330888,   18734,   10579,         2,        2,            1,   opportunistic_benefits
City of Evans Mayor                                 182397,    8486,    1578,         4,        2,            1,   opportunistic_benefits
City of Evans Ward 1 Council Member                 182397,    2065,       0,         0,        0,            1,   opportunistic_benefits
City of Evans Ward 2 Council Member                 182397,    2895,       0,         1,        1,            1,   opportunistic_benefits
City of Evans Ward 3 Council Member                 182397,    3526,       0,         3,        1,            1,   opportunistic_benefits
City of Fort Collins Ballot Issue 2A                458224,   97348,   50158,        23,        0,            1,   opportunistic_benefits
City of Fort Collins Ballot Question 2B             458224,   97348,   26909,        23,        0,            1,   opportunistic_benefits
City of Fort Collins Ballot Question 2C             458224,   97348,   23713,        23,        0,            1,   opportunistic_benefits
City of Fort Collins Ballot Question 2D             458224,   97348,   23893,        23,        0,            1,   opportunistic_benefits
City of Fort Lupton Ballot Issue 2B                 182397,    4354,     839,         2,        1,            1,   opportunistic_benefits
City of Fort Lupton Ballot Issue 2D                 182397,    4354,     654,         2,        1,            1,   opportunistic_benefits
City of Fort Lupton Ballot Question 2C              182397,    4354,    2069,         2,        1,            1,   opportunistic_benefits
City of Fort Lupton Ballot Question 2E              182397,    4354,     405,         2,        1,            1,   opportunistic_benefits
City of Fort Morgan Ballot Question 2A               13669,    4127,    2692,        15,        1,            1,   county_wide_contest
City of Greeley Ballot Issue 2L                     182397,   45853,   11074,        16,        2,            1,   opportunistic_benefits
City of Greeley Ballot Issue 2M                     182397,   45853,   14181,        16,        2,            1,   opportunistic_benefits
City of Greeley Ballot Question 2N                  182397,   45853,    8869,        16,        2,            1,   opportunistic_benefits
City of Greeley Ballot Question 2O                  182397,   45853,    6312,        16,        2,            1,   opportunistic_benefits
City of Holyoke Mayor                                 2384,     997,     261,        28,        0,            1,   county_wide_contest
City of Idaho Springs Ballot Question 2A              6180,     930,     247,         6,        0,            1,   opportunistic_benefits
City of Lafayette Ballot Question 2A                396121,   18689,    3743,         6,        0,            1,   opportunistic_benefits
City of Lakewood Ballot Issue 2A                    367779,   89378,   19678,        20,        4,            1,   opportunistic_benefits
City of Leadville Ballot Issue 2B                     3973,    1581,      83,        35,        0,            1,   opportunistic_benefits
City of Leadville Ballot Question 2A                  3973,    1581,     237,        35,        0,            1,   opportunistic_benefits
City of Littleton Ballot Issue 3B                   946840,   28308,     381,         6,        1,            3,   opportunistic_benefits
City of Longmont Ballot Issue 3A                    578518,   55848,   26166,        19,        0,            2,   opportunistic_benefits
City of Louisville City Council Ward 1              396121,    4259,      19,         0,        0,            1,   opportunistic_benefits
City of Loveland Ballot Issue 2E                    458224,   50141,   18020,        17,        0,            1,   opportunistic_benefits
City of Loveland Ballot Issue 2F                    458224,   50141,   14758,        17,        0,            1,   opportunistic_benefits
City of Loveland Ballot Issue 2G                    458224,   50141,   11730,        17,        0,            1,   opportunistic_benefits
City of Loveland Ballot Question 2H                 458224,   50141,   10253,        17,        0,            1,   opportunistic_benefits
City of Loveland Ballot Question 2I                 458224,   50141,    9006,        17,        0,            1,   opportunistic_benefits
City of Loveland Ballot Question 2J                 458224,   50141,    9123,        17,        0,            1,   opportunistic_benefits
City of Montrose Ballot Issue 2A                     26005,   12410,     242,        15,        0,            1,   opportunistic_benefits
City of Pueblo Ballot Question 2A - Charter Amendm  170790,   50574,    1199,       118,        0,            1,   opportunistic_benefits
City of Pueblo Ballot Question 2B - Charter Amendm  170790,   50574,     182,       118,        0,            1,   opportunistic_benefits
City of Pueblo Ballot Question 2C - Charter Amendm  170790,   50574,    2015,       118,        0,            1,   opportunistic_benefits
City of Pueblo Ballot Question 2D - Charter Amendm  170790,   50574,    5836,       118,        0,            1,   opportunistic_benefits
City of Sterling Referred Ballot Issue 2T            10274,    5038,     146,        33,        0,            1,   opportunistic_benefits
City of Thornton Question 2A                        468858,   68870,   31386,        32,        0,            1,   opportunistic_benefits
City of Walsenburg Ballot Question 300 - Walsenbur    4511,    1610,     518,        22,        0,            1,   opportunistic_benefits
City of Westminster Ballot Issue 3C                 836637,   63867,    3581,        27,        2,            2,   opportunistic_benefits
City of Westminster Ballot Question 3D              836637,   63867,    4909,        27,        2,            2,   opportunistic_benefits
City of Wheat Ridge Ballot Question 2B              367779,   20148,    9870,         2,        0,            1,   opportunistic_benefits
City of Wheat Ridge Ballot Question 2C              367779,   20148,    6198,         2,        0,            1,   opportunistic_benefits
City of Woodland Park Ballot Question 2A             17112,    5285,     778,        24,        0,            1,   opportunistic_benefits
City of Yuma Ballot Issue 2A - Levying a Lodging T    4719,    1261,      51,         3,        0,            1,   opportunistic_benefits
Clear Creek County Commissioner - District 2          6180,    6180,     663,        72,        1,            1,   opportunistic_benefits
Clear Creek County Commissioner - District 3          6180,    6180,     641,        72,        1,            1,   county_wide_contest
Clear Creek County Court - Jones                      6180,    6180,    2146,        72,        1,            1,   opportunistic_benefits
Clear Creek County Sheriff                            6180,    6180,       0,        72,        1,            1,   opportunistic_benefits
Colorado Court of Appeals Judge - Dunn             4746866, 3239722,  907565,      3905,      199,           63,   opportunistic_benefits
Colorado Court of Appeals Judge - Jones            4746866, 3239722,  503905,      3905,      199,           63,   opportunistic_benefits
Colorado Court of Appeals Judge - Kuhn             4746866, 3239722,  826629,      3905,      199,           63,   opportunistic_benefits
Colorado Court of Appeals Judge - Roman            4746866, 3239722,  836501,      3905,      199,           63,   opportunistic_benefits
Colorado Court of Appeals Judge - Schutz           4746866, 3239722,  712233,      3905,      199,           63,   opportunistic_benefits
Colorado Supreme Court Justice - Berkenkotter      4746866, 3239722,  847284,      3905,      199,           63,   opportunistic_benefits
Colorado Supreme Court Justice - Boatright         4746866, 3239722,  640976,      3905,      199,           63,   opportunistic_benefits
Colorado Supreme Court Justice - Marquez           4746866, 3239722,  708027,      3905,      199,           63,   opportunistic_benefits
Conejos County Commissioner District 1                4196,    4196,     663,        75,        0,            1,   opportunistic_benefits
Conejos County Commissioner District 3                4196,    4196,     418,        75,        0,            1,   county_wide_contest
Conejos County Court - Kelly                          4196,    4196,    1821,        75,        0,            1,   opportunistic_benefits
Cortez Fire Protection District Ballot Issue 6-A     15436,    7815,     654,        23,        1,            1,   opportunistic_benefits
Costilla County Commissioner District 1               2149,    2149,     479,        47,        0,            1,   opportunistic_benefits
Costilla County Commissioner District 3               2149,    2149,     340,        47,        0,            1,   county_wide_contest
Councilmember                                       248173,   36454,     961,         8,        0,            1,   opportunistic_benefits
County Commissioner - District 2                     30750,   30750,    1943,       117,        0,            1,   county_wide_contest
County Commissioner - District 3                     30750,   30750,     772,       117,        0,            1,   opportunistic_benefits
County Court Judge - Cheyenne                         1064,    1064,     430,        20,        0,            1,   county_wide_contest
County Court Judge - Denver Blackett               1104271,  369425,  132277,        70,       22,            1,   opportunistic_benefits
County Court Judge - Denver Cherry                 1104271,  369425,  138046,        70,       22,            1,   opportunistic_benefits
County Court Judge - Denver Faragher               1104271,  369425,  136028,        70,       22,            1,   opportunistic_benefits
County Court Judge - Denver Goble                  1104271,  369425,  134099,        70,       22,            1,   opportunistic_benefits
County Court Judge - Denver Pallares               1104271,  369425,  131216,        70,       22,            1,   opportunistic_benefits
County Court Judge - Denver Rodarte                1104271,  369425,  134672,        70,       22,            1,   opportunistic_benefits
County Court Judge - Denver Rudolph                1104271,  369425,  121922,        70,       22,            1,   opportunistic_benefits
County Court Judge - Denver Schwartz               1104271,  369425,  128740,        70,       22,            1,   opportunistic_benefits
County Court Judge - Denver Simonet                1104271,  369425,  136684,        70,       22,            1,   opportunistic_benefits
County Court Judge - Denver Spahn                  1104271,  369425,  137323,        70,       22,            1,   opportunistic_benefits
County Court Judge - Gunnison                        11126,   11126,    4617,        49,        0,            1,   opportunistic_benefits
County Court Judge - Routt                           33261,   16628,    6419,        77,        0,            1,   opportunistic_benefits
Crawford Water Conservancy District Ballot Issue 7   45791,     912,     486,         2,        0,            2,   opportunistic_benefits
Crowley County Commissioner - District 2              1729,    1729,       0,        15,        0,            1,   opportunistic_benefits
Crowley County Commissioner - District 3              1729,    1729,     967,        15,        0,            1,   county_wide_contest
Crystal Lakes Fire Protection District Ballot Issu  458224,    1197,     814,         0,        0,            1,   opportunistic_benefits
Custer County Board of County Commissioners - Dist    3932,    3932,     851,        34,        0,            1,   county_wide_contest
Custer County Board of County Commissioners - Dist    3932,    3932,    1236,        34,        0,            1,   opportunistic_benefits
Delta County Commissioner - District 2               19786,   19746,       0,        80,        3,            1,   opportunistic_benefits
Delta County Commissioner - District 3               19786,   19746,    5479,        80,        3,            1,   opportunistic_benefits
Delta County Court - Zeerip                          19786,   19746,    1820,        80,        3,            1,   county_wide_contest
Denver Ballot Issue 2Q                             1104271,  368208,   38403,        99,        0,            1,   county_wide_contest
Denver Ballot Issue 2R                             1104271,  368208,    3706,        99,        0,            1,   opportunistic_benefits
Denver Downtown Development Authority Ballot Issue 1104271,    1699,     823,         1,        0,            1,   opportunistic_benefits
Denver Initiated Ordinance 308                     1104271,  366622,   50541,        89,        0,            1,   opportunistic_benefits
Denver Initiated Ordinance 309                     1104271,  366622,   91313,        89,        0,            1,   opportunistic_benefits
Denver Public Schools Ballot Issue 4A              1104271,  366622,  169746,        89,        0,            1,   opportunistic_benefits
Denver Referred Question 2S                        1104271,  368208,   97088,        99,        0,            1,   opportunistic_benefits
Denver Referred Question 2T                        1104271,  368208,    4579,        99,        0,            1,   opportunistic_benefits
Denver Referred Question 2U                        1104271,  368208,   92059,        99,        0,            1,   opportunistic_benefits
Denver Referred Question 2V                        1104271,  368208,   94286,        99,        0,            1,   opportunistic_benefits
Denver Referred Question 2W                        1104271,  366622,   68667,        89,        0,            1,   opportunistic_benefits
District Attorney - 10th Judicial District          170790,   86126,   10184,       189,        4,            1,   opportunistic_benefits
District Attorney - 11th Judicial District           58002,   56618,       0,       193,        1,            4,   opportunistic_benefits
District Attorney - 12th Judicial District           32059,   24514,       0,       326,        1,            6,   opportunistic_benefits
District Attorney - 13th Judicial District           39062,   38927,       0,       409,        2,            7,   opportunistic_benefits
District Attorney - 14th Judicial District           50196,   33387,       0,       181,        0,            3,   opportunistic_benefits
District Attorney - 15th Judicial District            9498,    9177,       0,       174,        0,            4,   opportunistic_benefits
District Attorney - 16th Judicial District           12989,   12989,       0,        83,        1,            3,   opportunistic_benefits
District Attorney - 17th Judicial District          516401,  284415,       0,       155,       20,            2,   opportunistic_benefits
District Attorney - 18th Judicial District          351540,  351540,   46710,        73,       19,            2,   county_wide_contest
District Attorney - 19th Judicial District          182397,  182060,       0,        86,       15,            1,   opportunistic_benefits
District Attorney - 1st Judicial District           371962,  371544,       0,       204,       25,            2,   opportunistic_benefits
District Attorney - 20th Judicial District          396121,  198600,       0,        48,       15,            1,   opportunistic_benefits
District Attorney - 21st Judicial District           92921,   92921,       0,        60,        4,            1,   opportunistic_benefits
District Attorney - 22nd Judicial District           16889,   16757,    5305,       199,        1,            2,   opportunistic_benefits
District Attorney - 23rd Judicial District          271420,  271299,   59237,       108,       13,            3,   opportunistic_benefits
District Attorney - 2nd Judicial District          1104271,  369425,       0,        70,       22,            1,   opportunistic_benefits
District Attorney - 3rd Judicial District            12397,   12397,       0,        93,        1,            2,   opportunistic_benefits
District Attorney - 4th Judicial District           404409,  403633,   83949,       201,       35,            2,   opportunistic_benefits
District Attorney - 5th Judicial District            56960,   56189,       0,       293,        5,            4,   opportunistic_benefits
District Attorney - 6th Judicial District            45639,   45639,       0,       101,        2,            2,   opportunistic_benefits
District Attorney - 7th Judicial District            66700,   66506,       0,       355,        5,            6,   opportunistic_benefits
District Attorney - 8th Judicial District           459069,  229885,    2305,       109,       10,            2,   opportunistic_benefits
District Attorney - 9th Judicial District            45826,   45826,       0,       216,        0,            3,   opportunistic_benefits
District Court Judge - 10th Judicial District - O'  170790,   86126,   17651,       189,        4,            1,   opportunistic_benefits
District Court Judge - 11th Judicial District - Tu   58002,   56618,    6042,       193,        1,            4,   opportunistic_benefits
District Court Judge - 12th Judicial District - Co   32059,   24514,    8014,       326,        1,            6,   opportunistic_benefits
District Court Judge - 13th Judicial District - Ha   39062,   38927,    4106,       409,        2,            7,   opportunistic_benefits
District Court Judge - 13th Judicial District - Ja   39062,   38927,    4003,       409,        2,            7,   opportunistic_benefits
District Court Judge - 13th Judicial District - Mc   39062,   38927,    6207,       409,        2,            7,   opportunistic_benefits
District Court Judge - 15th Judicial District - Da    9498,    9177,    2388,       174,        0,            4,   opportunistic_benefits
District Court Judge - 16th Judicial District - Vi   12989,   12989,    4453,        83,        1,            3,   opportunistic_benefits
District Court Judge - 17th Judicial District - Ho  516401,  284415,   67695,       155,       20,            2,   opportunistic_benefits
District Court Judge - 17th Judicial District - Ma  516401,  284415,   57569,       155,       20,            2,   opportunistic_benefits
District Court Judge - 17th Judicial District - Va  516401,  284415,   70851,       155,       20,            2,   opportunistic_benefits
District Court Judge - 18th Judicial District - Fi  602308,  602187,  127462,       160,       30,            4,   opportunistic_benefits
District Court Judge - 18th Judicial District - Le  602308,  602187,  123984,       160,       30,            4,   opportunistic_benefits
District Court Judge - 18th Judicial District - Lu  602308,  602187,  148783,       160,       30,            4,   opportunistic_benefits
District Court Judge - 18th Judicial District - Mc  602308,  602187,  168696,       160,       30,            4,   opportunistic_benefits
District Court Judge - 18th Judicial District - To  602308,  602187,  143718,       160,       30,            4,   opportunistic_benefits
District Court Judge - 18th Judicial District - Wh  602308,  602187,  155154,       160,       30,            4,   opportunistic_benefits
District Court Judge - 18th Judicial District - Wh  602308,  602187,   61762,       160,       30,            4,   opportunistic_benefits
District Court Judge - 19th Judicial District - Cr  182397,  182060,   15611,        86,       15,            1,   county_wide_contest
District Court Judge - 19th Judicial District - Es  182397,  182060,   34439,        86,       15,            1,   opportunistic_benefits
District Court Judge - 19th Judicial District - Ta  182397,  182060,   37387,        86,       15,            1,   opportunistic_benefits
District Court Judge - 1st Judicial District - Car  371962,  371544,   92064,       204,       25,            2,   opportunistic_benefits
District Court Judge - 1st Judicial District - Hun  371962,  371544,  102453,       204,       25,            2,   opportunistic_benefits
District Court Judge - 20th Judicial District - Co  396121,  198600,   79250,        48,       15,            1,   opportunistic_benefits
District Court Judge - 20th Judicial District - Gu  396121,  198600,   80680,        48,       15,            1,   opportunistic_benefits
District Court Judge - 20th Judicial District - Li  396121,  198600,   77589,        48,       15,            1,   opportunistic_benefits
District Court Judge - 20th Judicial District - Mu  396121,  198600,   71385,        48,       15,            1,   opportunistic_benefits
District Court Judge - 22nd Judicial District - Pl   16889,   16757,    5839,       199,        1,            2,   opportunistic_benefits
District Court Judge - 2nd Judicial District Baile 1104271,  369425,  139893,        70,       22,            1,   opportunistic_benefits
District Court Judge - 2nd Judicial District Espin 1104271,  369425,  138785,        70,       22,            1,   opportunistic_benefits
District Court Judge - 2nd Judicial District Grant 1104271,  369425,  127035,        70,       22,            1,   opportunistic_benefits
District Court Judge - 2nd Judicial District Moses 1104271,  369425,  142873,        70,       22,            1,   opportunistic_benefits
District Court Judge - 2nd Judicial District Myers 1104271,  369425,  137956,        70,       22,            1,   opportunistic_benefits
District Court Judge - 2nd Judicial District Schut 1104271,  369425,  147075,        70,       22,            1,   opportunistic_benefits
District Court Judge - 2nd Judicial District Scovi 1104271,  369425,  138208,        70,       22,            1,   opportunistic_benefits
District Court Judge - 2nd Judicial District Truji 1104271,  369425,  141320,        70,       22,            1,   opportunistic_benefits
District Court Judge - 4th Judicial District - Ben  404409,  403633,   69412,       201,       35,            2,   opportunistic_benefits
District Court Judge - 4th Judicial District - Bil  404409,  403633,   67101,       201,       35,            2,   opportunistic_benefits
District Court Judge - 4th Judicial District - Bra  404409,  403633,   84870,       201,       35,            2,   opportunistic_benefits
District Court Judge - 4th Judicial District - Evi  404409,  403633,   63851,       201,       35,            2,   opportunistic_benefits
District Court Judge - 4th Judicial District - Fin  404409,  403633,   57392,       201,       35,            2,   opportunistic_benefits
District Court Judge - 4th Judicial District - May  404409,  403633,   75493,       201,       35,            2,   opportunistic_benefits
District Court Judge - 4th Judicial District - Mol  404409,  403633,   70605,       201,       35,            2,   opportunistic_benefits
District Court Judge - 4th Judicial District - Sha  404409,  403633,   95958,       201,       35,            2,   opportunistic_benefits
District Court Judge - 5th Judicial District - Olg   56960,   56189,   18001,       293,        5,            4,   opportunistic_benefits
District Court Judge - 6th Judicial District - Shr   45639,   45639,   13682,       101,        2,            2,   opportunistic_benefits
District Court Judge - 7th Judicial District - Sch   66700,   66506,   13567,       355,        5,            6,   opportunistic_benefits
District Court Judge - 7th Judicial District - Yod   66700,   66506,   14583,       355,        5,            6,   opportunistic_benefits
District Court Judge - 8th Judicial District - Cur  459069,  229885,   61502,       109,       10,            2,   opportunistic_benefits
District Court Judge - 8th Judicial District - Fin  459069,  229885,   61879,       109,       10,            2,   opportunistic_benefits
District Court Judge - 9th Judicial District - Nor   45826,   45826,   16119,       216,        0,            3,   opportunistic_benefits
Dolores County Commissioner District 2                1453,    1431,     299,       143,        0,            1,   opportunistic_benefits
Dolores County Commissioner District 3                1453,    1431,     516,       143,        0,            1,   opportunistic_benefits
Dolores School District RE-4A Ballot Issue 4-A       15436,    2732,     942,        10,        0,            1,   opportunistic_benefits
Douglas County Commissioner - District 2            248173,  248052,   29760,        62,       11,            1,   county_wide_contest
Douglas County Commissioner - District 3            248173,  248052,   35059,        62,       11,            1,   opportunistic_benefits
Douglas County Court - Waidler                      248173,  248052,   66889,        62,       11,            1,   opportunistic_benefits
Douglas County School District RE-1 Ballot Issue 5  268825,  250197,   44093,        67,       11,            2,   opportunistic_benefits
Dove Creek Ambulance District Ballot Issue 6A         1453,    1189,     195,       117,        0,            1,   county_wide_contest
Durango School District 9-R Ballot Issue 4A          36150,   26997,    5622,        47,        2,            1,   opportunistic_benefits
Eagle County Commissioner - District 1               28988,   28217,    4635,        46,        2,            1,   county_wide_contest
Eagle County Commissioner - District 2               28988,   28217,       0,        46,        2,            1,   opportunistic_benefits
Eagle River Fire Protection District Ballot Issue    28988,   11090,    3052,        17,        0,            1,   opportunistic_benefits
El Paso County Commissioner - District 2            387297,   86597,   24150,        33,        6,            1,   opportunistic_benefits
El Paso County Commissioner - District 3            387297,   91244,    7262,        27,        6,            1,   opportunistic_benefits
El Paso County Commissioner - District 4            387297,   57957,    9878,        22,        6,            1,   opportunistic_benefits
El Paso County Court - Ankeny                       387297,  387050,   88436,       128,       32,            1,   opportunistic_benefits
El Paso County Court - Fennick                      387297,  387050,   68816,       128,       32,            1,   opportunistic_benefits
El Paso County Court - Gerhart                      387297,  387050,   95264,       128,       32,            1,   opportunistic_benefits
El Paso County Court - Katzman                      387297,  387050,   51888,       128,       32,            1,   opportunistic_benefits
El Paso County Court - McKedy                       387297,  387050,   84435,       128,       32,            1,   opportunistic_benefits
El Paso County School District No. 20 (Academy) Ba  387297,   77299,   11171,        27,        8,            1,   opportunistic_benefits
Elbert County Commissioner - District 1              20652,   20652,       0,        21,        2,            1,   opportunistic_benefits
Elbert County Commissioner - District 3              20652,   20652,       0,        21,        2,            1,   opportunistic_benefits
Elbert County Question 1A                            20652,   20652,    9438,        21,        2,            1,   county_wide_contest
Eldorado Springs Public Improvement District Ballo  396121,     176,     135,         0,        0,            1,   opportunistic_benefits
Elizabeth Mayor                                      20652,    1590,      47,         1,        0,            1,   opportunistic_benefits
Elizabeth Trustee Ward 1                             20652,     509,      47,         0,        0,            1,   opportunistic_benefits
Elizabeth Trustee Ward 3                             20652,     570,      10,         0,        0,            1,   opportunistic_benefits
Fairmount Fire Protection District Ballot Issue 6B  367779,    7078,    2658,         0,        2,            1,   opportunistic_benefits
Fairmount Fire Protection District Ballot Question  367779,    7078,    3632,         0,        2,            1,   opportunistic_benefits
Flagler Rural Fire Protection District Ballot Issu    3807,     601,      79,        16,        0,            1,   opportunistic_benefits
Flying Horse Metropolitan District No. 2 Ballot Qu  387297,    3145,    1633,         1,        0,            1,   opportunistic_benefits
Flying Horse Metropolitan District No. 3 Ballot Qu  387297,     426,     221,         0,        0,            1,   opportunistic_benefits
Foothills Park & Recreation District Ballot Issue   367779,   63117,   30452,        13,        7,            1,   opportunistic_benefits
Fremont County Board of County Commissioners - Dis   26051,   25831,       0,        20,        1,            1,   opportunistic_benefits
Fremont County Board of County Commissioners - Dis   26051,   25831,    9809,        20,        1,            1,   county_wide_contest
Fremont County Surveyor                              26051,   25831,       0,        20,        1,            1,   opportunistic_benefits
Frenchman School District No. RE-3 Referred Ballot   10274,     624,      59,         3,        0,            1,   opportunistic_benefits
Funding Poncha Springs Law Enforcement And Parks A   14627,    1021,      40,         9,        0,            1,   opportunistic_benefits
Garfield County Court - Roff                         30750,   30750,     404,       117,        0,            1,   opportunistic_benefits
Gilpin County Commissioner - District 1               4183,    4183,     217,       139,        1,            1,   county_wide_contest
Gilpin County Commissioner - District 3               4183,    4183,     107,       139,        1,            1,   opportunistic_benefits
Glenwood Springs Ballot Issue 2A  Tax increase for   30750,    4845,      91,        23,        0,            1,   opportunistic_benefits
Granby Ranch Metropolitan District Ballot Issue 6A   10236,     282,     201,         0,        0,            1,   opportunistic_benefits
Grand County Ballot Issue 1A - Lodging Tax Increas   10236,   10060,    1804,        57,        0,            1,   opportunistic_benefits
Grand County Commissioner - District 1               10236,   10060,    1455,        57,        0,            1,   opportunistic_benefits
Grand County Commissioner - District 2               10236,   10060,    1346,        57,        0,            1,   county_wide_contest
Gunnison County Commissioner - District 1            11126,   11126,    2722,        49,        0,            1,   opportunistic_benefits
Gunnison County Commissioner - District 2            11126,   11126,    2592,        49,        0,            1,   opportunistic_benefits
Gunnison County Library District Ballot Issue 6A     11126,   11126,    1683,        49,        0,            1,   county_wide_contest
Harrison School District No. 2 Ballot Issue 4A      387297,   25420,    3542,         9,        0,            1,   opportunistic_benefits
Hartsel Fire Protection District Ballot Issue 6A -   13392,    2070,     255,         7,        0,            1,   opportunistic_benefits
Hinsdale County Commissioner District 1                618,     618,     104,        81,        0,            1,   county_wide_contest
Hinsdale County Commissioner District 3                618,     618,       0,        81,        0,            1,   opportunistic_benefits
Hinsdale County Coroner                                618,     618,       0,        81,        0,            1,   opportunistic_benefits
Hinsdale County Sheriff                                618,     618,     160,        81,        0,            1,   opportunistic_benefits
Holyoke School District RE-1J Ballot Issue 5K - Bo    8474,    1566,      70,        47,        0,            3,   opportunistic_benefits
Homestead Public Improvement District of Boulder C  396121,     171,      33,         0,        0,            1,   opportunistic_benefits
Huerfano County Commissioner - District 1             4511,    4511,     628,        53,        1,            1,   county_wide_contest
Huerfano County Commissioner - District 2             4511,    4511,      40,        53,        1,            1,   opportunistic_benefits
Hyland Hills Park and Recreation District Ballot I  468858,   51732,    8023,        22,        0,            1,   opportunistic_benefits
Increase in City Lodging Tax                          4183,      66,      24,         3,        0,            1,   opportunistic_benefits
Jackson County Commissioner Dist 2                     845,     845,       0,        57,        0,            1,   opportunistic_benefits
Jackson County Commissioner Dist 3                     845,     845,       0,        57,        0,            1,   opportunistic_benefits
Jefferson County Ballot Issue 1A                    367779,  367361,   53261,        65,       24,            1,   opportunistic_benefits
Jefferson County Commissioner - District 1          367779,  367361,   43566,        65,       24,            1,   opportunistic_benefits
Jefferson County Commissioner - District 2          367779,  367361,   41410,        65,       24,            1,   county_wide_contest
Jefferson County Court- Burback                     367779,  367361,  108107,        65,       24,            1,   opportunistic_benefits
Jefferson County Court-Carpenter                    367779,  367361,  108347,        65,       24,            1,   opportunistic_benefits
Jefferson County Court-Goman                        367779,  367361,  102877,        65,       24,            1,   opportunistic_benefits
Jefferson County Court-Peper                        367779,  367361,  106431,        65,       24,            1,   opportunistic_benefits
Jefferson County Court-Wheeler                      367779,  367361,  110228,        65,       24,            1,   opportunistic_benefits
Ken-Caryl Metropolitan District Ballot Issue 6F     367779,    7553,    2211,         1,        0,            1,   opportunistic_benefits
Kiowa County Ballot Issue 1A                          1060,     870,     100,        48,        0,            1,   opportunistic_benefits
Kiowa County Ballot Issue 1B - Lodging Tax            1060,     870,      98,        48,        0,            1,   opportunistic_benefits
Kiowa County Commissioner - District 1                1060,     870,       0,        48,        0,            1,   opportunistic_benefits
Kiowa County Commissioner - District 3                1060,     870,       0,        48,        0,            1,   opportunistic_benefits
Kiowa County Hospital District Ballot Question 6A     1060,    1060,     136,        57,        0,            1,   county_wide_contest
Kiowa County Public Library District Ballot Questi    1060,     870,     420,        48,        0,            1,   opportunistic_benefits
Kiowa Mayor                                          20652,     406,       0,         0,        0,            1,   opportunistic_benefits
Kiowa School District C-2 Issue 4A                   20652,    2166,      86,         4,        0,            1,   opportunistic_benefits
Kit Carson County Commissioner - District 1           3807,    3739,       0,       106,        0,            1,   opportunistic_benefits
Kit Carson County Commissioner - District 3           3807,    3739,       0,       106,        0,            1,   opportunistic_benefits
La Plata County Ballot Issue 1A                      36150,   24578,    8804,        45,        1,            1,   opportunistic_benefits
La Plata County Commissioner District 2              36150,   36150,       0,        63,        2,            1,   opportunistic_benefits
La Plata County Commissioner District 3              36150,   36150,    4183,        63,        2,            1,   county_wide_contest
La Plata County Treasurer                            36150,   36150,   21846,        63,        2,            1,   opportunistic_benefits
Lafayette Downtown Development Authority Ballot Is  396121,     327,     165,         0,        0,            1,   opportunistic_benefits
Lafayette Downtown Development Authority Ballot Qu  396121,     327,     181,         0,        0,            1,   opportunistic_benefits
Lake County Assessor                                  3973,    3973,       0,       116,        0,            1,   opportunistic_benefits
Lake County Commissioner District 1                   3973,    3973,     816,       116,        0,            1,   opportunistic_benefits
Lake County Commissioner District 2                   3973,    3973,     293,       116,        0,            1,   opportunistic_benefits
Lake County Commissioner District 3                   3973,    3973,     280,       116,        0,            1,   opportunistic_benefits
Lake County School District R-1 Ballot Issue 4A       3973,    3973,     251,       116,        0,            1,   county_wide_contest
Larimer County Ballot Issue 1A                      458224,  228213,    7686,        69,        0,            1,   opportunistic_benefits
Larimer County Clerk and Recorder                   458224,  229040,   32770,        52,       10,            1,   county_wide_contest
Larimer County Commissioner - District 2            458224,  229040,   23897,        52,       10,            1,   opportunistic_benefits
Larimer County Commissioner - District 3            458224,  229040,   20387,        52,       10,            1,   opportunistic_benefits
Larimer County Court Judge - Ecton                  458224,  229040,   47914,        52,       10,            1,   opportunistic_benefits
Larimer County Court Judge - Lehman                 458224,  229040,   80655,        52,       10,            1,   opportunistic_benefits
Larimer County Fox Ridge Estates Public Improvemen  458224,      27,      18,         0,        0,            1,   opportunistic_benefits
Larimer County Grayhawk Knolls Public Improvement   458224,      54,      19,         0,        0,            1,   opportunistic_benefits
Larimer County Poudre Overlook Public Improvement   458224,     184,      95,         0,        0,            1,   opportunistic_benefits
Larimer County Tanager Public Improvement District  458224,      33,      23,         0,        0,            1,   opportunistic_benefits
Larimer County Vine Drive Public Improvement Distr  458224,      70,      30,         0,        0,            1,   opportunistic_benefits
Las Animas County Commissioner District 1             7886,    7886,       0,        40,        0,            1,   opportunistic_benefits
Las Animas County Commissioner District 2             7886,    7886,    1486,        40,        0,            1,   county_wide_contest
Leadville Lake County Regional Housing Authority B    3973,    3973,     695,       116,        0,            1,   opportunistic_benefits
Lincoln County Commissioner - District 2              2595,    2595,    1010,        25,        0,            1,   opportunistic_benefits
Lincoln County Commissioner - District 3              2595,    2595,     798,        25,        0,            1,   county_wide_contest
Logan County Commissioner - District 1               10274,   10274,       0,        64,        0,            1,   opportunistic_benefits
Logan County Commissioner - District 2               10274,   10274,       0,        64,        0,            1,   opportunistic_benefits
Logan County Court Judge - Brammer                   10274,   10274,    1200,        64,        0,            1,   county_wide_contest
Mesa County Ballot Issue 1A                          92921,   92921,   11483,        60,        4,            1,   county_wide_contest
Mesa County Ballot Issue 1B                          92921,   92921,   11483,        60,        4,            1,   opportunistic_benefits
Mesa County Commissioner - District 1                92921,   92921,   28421,        60,        4,            1,   opportunistic_benefits
Mesa County Commissioner - District 3                92921,   92921,       0,        60,        4,            1,   opportunistic_benefits
Mesa County Court Judge - Grattan III                92921,   92921,   27997,        60,        4,            1,   opportunistic_benefits
Mesa County Valley School District 51 Ballot Issue   92921,   90852,   17946,        58,        4,            1,   opportunistic_benefits
Mesa County Valley School District 51 Ballot Issue   92921,   90852,   14650,        58,        4,            1,   opportunistic_benefits
Mineral County Commissioner - District 2               766,     766,       0,        18,        0,            1,   opportunistic_benefits
Mineral County Commissioner - District 3               766,     766,     334,        18,        0,            1,   county_wide_contest
Moffat County Commissioner District 1                 6699,    6699,    3349,        47,        0,            1,   opportunistic_benefits
Moffat County Commissioner District 2                 6699,    6699,       0,        47,        0,            1,   opportunistic_benefits
Montezuma Cortez RE-1 Ballot Issue 4-B               15436,    9917,     671,        34,        1,            1,   opportunistic_benefits
Montezuma County Ballot Issue 1-A                    15436,   15326,    2053,        56,        1,            1,   county_wide_contest
Montezuma County Commissioner District 2             15436,   15326,       0,        56,        1,            1,   opportunistic_benefits
Montezuma County Commissioner District 3             15436,   15326,       0,        56,        1,            1,   opportunistic_benefits
Montrose County Commissioner - District 1            26005,   25991,       0,        32,        1,            1,   opportunistic_benefits
Montrose County Commissioner - District 3            26005,   25991,    6122,        32,        1,            1,   county_wide_contest
Montrose County Court - Beckenhauer                  26005,   25991,    6351,        32,        1,            1,   opportunistic_benefits
Montrose County Court - Harvell                      26005,   25991,    5083,        32,        1,            1,   opportunistic_benefits
Montrose School District RE-1J Ballot Issue 5A       41288,   24741,   11111,        37,        1,            3,   opportunistic_benefits
Morgan County Commissioner District 1                13669,   13602,    6219,        38,        1,            1,   opportunistic_benefits
Morgan County Commissioner District 3                13669,   13602,    7382,        38,        1,            1,   opportunistic_benefits
Mountain Shadows Metropolitan District Ballot Issu  367779,    1135,     238,         0,        0,            1,   opportunistic_benefits
Mt Crested Butte Town Council                        11126,     721,      34,         0,        0,            1,   opportunistic_benefits
NORTH PARK SCHOOL DISTRICT R-1 BALLOT ISSUE 4A         845,     845,     106,        57,        0,            1,   county_wide_contest
North Range Metropolitan District No 2 Ballot Ques  468858,    1596,     667,         1,        0,            1,   opportunistic_benefits
North Range Metropolitan District No 2 Ballot Ques  468858,    1596,     672,         1,        0,            1,   opportunistic_benefits
North Range Metropolitan District No 2 Ballot Ques  468858,    1596,     734,         1,        0,            1,   opportunistic_benefits
North Range Metropolitan District No 2 Ballot Ques  468858,    1596,     666,         1,        0,            1,   opportunistic_benefits
Norwood Fire Protection District Ballot Issue 7A     31013,    1225,     586,         3,        0,            2,   opportunistic_benefits
Norwood School District R-2J Issue 5B                31013,    1174,      89,         3,        0,            2,   opportunistic_benefits
Otero County Ballot Question 1A                       9039,    9039,    3659,        36,        1,            1,   county_wide_contest
Otero County Commissioner District 1                  9039,    9039,       0,        36,        1,            1,   opportunistic_benefits
Otero County Commissioner District 3                  9039,    9039,       0,        36,        1,            1,   opportunistic_benefits
Ouray County Commissioner - District 1                4157,    4157,     411,        75,        0,            1,   county_wide_contest
Ouray County Commissioner - District 3                4157,    4157,       0,        75,        0,            1,   opportunistic_benefits
Ouray County Court - Thomasson                        4157,    4157,    1713,        75,        0,            1,   opportunistic_benefits
Parachute Ballot Issue 2B Increase sales and use t   30750,     468,      87,         0,        0,            1,   opportunistic_benefits
Park County Commissioner District 1                  13392,   12228,    1816,        50,        0,            1,   county_wide_contest
Park County Commissioner District 2                  13392,   12228,    1878,        50,        0,            1,   opportunistic_benefits
Phillips County Commissioner - District 2             2384,    2384,       0,        68,        0,            1,   opportunistic_benefits
Phillips County Commissioner - District 3             2384,    2384,       0,        68,        0,            1,   opportunistic_benefits
Phillips County Court Judge - Killin                  2384,    2384,    1522,        68,        0,            1,   opportunistic_benefits
Phillips County Sheriff                               2384,    2384,       0,        68,        0,            1,   opportunistic_benefits
Pitkin County Ballot Issue 1A: Affordable and Work   11348,   11348,    2091,        41,        0,            1,   county_wide_contest
Pitkin County Ballot Issue 1B: County Solid Waste    11348,   11348,    6731,        41,        0,            1,   opportunistic_benefits
Pitkin County Ballot Question 1C: BOCC Referred Ho   11348,   11348,    3509,        41,        0,            1,   opportunistic_benefits
Pitkin County Ballot Question 200: Citizen Initiat   11348,   11348,    2074,        41,        0,            1,   opportunistic_benefits
Pitkin County Commissioner - District 3              11348,   11348,       0,        41,        0,            1,   opportunistic_benefits
Pitkin County Commissioner - District 4              11348,   11348,       0,        41,        0,            1,   opportunistic_benefits
Pitkin County Commissioner - District 5              11348,   11348,    3926,        41,        0,            1,   opportunistic_benefits
Pitkin County Court - Andrews                        11348,   11348,    5701,        41,        0,            1,   opportunistic_benefits
Polo Reserve Metropolitan District Ballot Issue 6A  330888,     108,      21,         0,        0,            1,   opportunistic_benefits
Poudre School District R-1 Ballot Issue 4A          458224,  133149,   17499,        35,        0,            1,   opportunistic_benefits
Presidential Electors                              4746866, 3239722,  350348,      3905,      199,           63,   state_wide_contest
Proposition 127 (STATUTORY)                        4746866, 3236953,  289847,      3979,      162,           63,   opportunistic_benefits
Proposition 128 (STATUTORY)                        4746866, 3236953,  728725,      3979,      162,           63,   opportunistic_benefits
Proposition 129 (STATUTORY)                        4746866, 3236000,  164544,      3997,      151,           63,   opportunistic_benefits
Proposition 130 (STATUTORY)                        4743059, 3232261,  167935,      3891,      151,           62,   opportunistic_benefits
Proposition 130 (STATUTORY) - Kit Carson              3807,    3739,     256,       106,        0,            1,   county_wide_contest
Proposition 131 (STATUTORY)                        4746866, 3236000,  210403,      3997,      151,           63,   opportunistic_benefits
Proposition JJ (STATUTORY)                         4746866, 3238465, 1618132,      3955,      177,           63,   opportunistic_benefits
Proposition KK (STATUTORY)                         4746866, 3238465,  268650,      3955,      177,           63,   opportunistic_benefits
Prowers County Ballot Issue 1A                        5209,    5209,    2364,        18,        0,            1,   county_wide_contest
Prowers County Commissioner - District 1              5209,    5209,       0,        18,        0,            1,   opportunistic_benefits
Prowers County Commissioner - District 3              5209,    5209,       0,        18,        0,            1,   opportunistic_benefits
Pueblo County Commissioner - District 1             170790,   86126,    1560,       189,        4,            1,   opportunistic_benefits
Pueblo County Commissioner - District 2             170790,   86126,    3294,       189,        4,            1,   county_wide_contest
Pueblo County Court - Silva                         170790,   86126,   15503,       189,        4,            1,   opportunistic_benefits
Pueblo County Court - Vellar                        170790,   86126,   12033,       189,        4,            1,   opportunistic_benefits
Pueblo County Rural 70 School District Ballot Issu  170790,   34405,    1789,        74,        0,            1,   opportunistic_benefits
Rangely School District RE-4 Ballot Issue 4A          3728,    1372,     150,        18,        0,            1,   opportunistic_benefits
Regent of the University of Colorado - At Large    4746866, 3239722,  115121,      3905,      199,           63,   state_wide_contest
Regent of the University of Colorado - Congression  527947,  411636,   53314,      1630,       19,           26,   opportunistic_benefits
Regent of the University of Colorado - Congression  387297,  381486,   61239,       127,       32,            1,   opportunistic_benefits
Regional Transportation District Ballot Issue 7A   3146030, 1671212,  648779,       463,       48,            8,   opportunistic_benefits
Regional Transportation District Ballot Question 7  182397,    2179,      67,         1,        0,            1,   opportunistic_benefits
Regional Transportation District Director - Distri 1435159,  121919,   16512,        26,       10,            2,   opportunistic_benefits
Regional Transportation District Director - Distri 1802938,  102354,    5920,        25,        5,            3,   opportunistic_benefits
Regional Transportation District Director - Distri 1435159,   85759,   31253,        13,        4,            2,   opportunistic_benefits
Regional Transportation District Director - Distri  330888,   92273,    3870,        16,        3,            1,   opportunistic_benefits
Regional Transportation District Director - Distri  579061,  124958,       0,        23,        6,            2,   opportunistic_benefits
Regional Transportation District Director - Distri  579061,  130193,       0,        18,        5,            2,   opportunistic_benefits
Regional Transportation District Director - Distri 1094919,  134819,       0,        74,       13,            4,   opportunistic_benefits
Regional Transportation District Director - Distri  367779,  118203,   70306,        21,       10,            1,   opportunistic_benefits
Representative to the 119th United States Congress 1802938,  372303,  190008,        69,       22,            3,   opportunistic_benefits
Representative to the 119th United States Congress 1506033,  437926,  164187,       587,       32,           11,   opportunistic_benefits
Representative to the 119th United States Congress  527947,  411636,   19983,      1630,       19,           26,   opportunistic_benefits
Representative to the 119th United States Congress 2151594,  468420,   51961,       787,       21,           21,   opportunistic_benefits
Representative to the 119th United States Congress  387297,  381486,   49966,       127,       32,            1,   opportunistic_benefits
Representative to the 119th United States Congress 2519969,  366168,   70501,        69,       17,            5,   opportunistic_benefits
Representative to the 119th United States Congress 1532961,  452678,   60409,       491,       29,           11,   opportunistic_benefits
Representative to the 119th United States Congress 1109479,  349105,    2448,       145,       27,            3,   opportunistic_benefits
Retain Revenue For Affordable Housing                14627,   13566,    2294,        80,        0,            1,   opportunistic_benefits
Rico Fire Protection District Ballot Question 6B      1453,     211,     168,        25,        0,            1,   opportunistic_benefits
Rio Blanco County Commissioner District 2             3728,    3728,       0,        58,        0,            1,   opportunistic_benefits
Rio Blanco County Commissioner District 3             3728,    3728,       0,        58,        0,            1,   opportunistic_benefits
Rio Grande County Commissioner District 1             6286,    6286,    3230,        35,        0,            1,   opportunistic_benefits
Rio Grande County Commissioner District 3             6286,    6286,       0,        35,        0,            1,   opportunistic_benefits
Rio Grande County Court - Stenger                     6286,    6286,    1382,        35,        0,            1,   county_wide_contest
Routt County Commissioner - District 1               33261,   16628,    1400,        77,        0,            1,   county_wide_contest
Routt County Commissioner - District 2               33261,   16628,    3655,        77,        0,            1,   opportunistic_benefits
Saguache County Commissioner District 1               3446,    3446,     208,       119,        0,            1,   county_wide_contest
Saguache County Commissioner District 2               3446,    3446,     164,       119,        0,            1,   opportunistic_benefits
Saguache County Court - Schuenemann                   3446,    3446,    1111,       119,        0,            1,   opportunistic_benefits
San Miguel Authority for Regional Transportation (    6461,    4028,     186,        59,        1,            2,   opportunistic_benefits
San Miguel County Ballot Question 1A                  5008,    4868,     996,        38,        1,            1,   county_wide_contest
San Miguel County Ballot Question 1B                  5008,    4868,    1353,        38,        1,            1,   opportunistic_benefits
San Miguel County Clerk and Recorder                  5008,    4868,       0,        38,        1,            1,   opportunistic_benefits
San Miguel County Commissioner - District 1           5008,    4868,       0,        38,        1,            1,   opportunistic_benefits
San Miguel County Commissioner - District 3           5008,    4868,    2546,        38,        1,            1,   opportunistic_benefits
Second Creek Farm Metropolitan District No 2 Ballo  468858,     259,     100,         0,        0,            1,   opportunistic_benefits
Second Creek Farm Metropolitan District No 2 Ballo  468858,     259,      35,         0,        0,            1,   opportunistic_benefits
Second Creek Farm Metropolitan District No 2 Ballo  468858,     259,       3,         0,        0,            1,   opportunistic_benefits
Second Creek Farm Metropolitan District No 2 Ballo  468858,     259,       5,         0,        0,            1,   opportunistic_benefits
Second Creek Farm Metropolitan District No 2 Ballo  468858,     259,      32,         0,        0,            1,   opportunistic_benefits
Second Creek Farm Metropolitan District No 2 Ballo  468858,     259,      98,         0,        0,            1,   opportunistic_benefits
Sedgwick County Commissioner - District 2             1371,    1371,       0,        84,        0,            1,   opportunistic_benefits
Sedgwick County Commissioner - District 3             1371,    1371,     116,        84,        0,            1,   county_wide_contest
Sedgwick County Court - Landry                        1371,    1371,     423,        84,        0,            1,   opportunistic_benefits
Sedgwick County Sheriff                               1371,    1371,       0,        84,        0,            1,   opportunistic_benefits
South Adams County Water and Sanitation District B  468858,   30135,   13758,        20,        0,            1,   opportunistic_benefits
Spring Canyon Ballot Issue 6B                       248173,      48,      10,         0,        0,            1,   opportunistic_benefits
St. Vrain Valley School District RE-1J Ballot Issu 1084285,  119603,   52696,        44,        5,            4,   opportunistic_benefits
St. Vrain and Left Hand Water Conservancy District 1036742,   76515,   48151,        26,        0,            3,   opportunistic_benefits
State Board of Education Member - Congressional Di 1506033,  437926,  297038,       587,       32,           11,   opportunistic_benefits
State Board of Education Member - Congressional Di  527947,  411636,   53350,      1630,       19,           26,   opportunistic_benefits
State Board of Education Member - Congressional Di 2151594,  468420,   91667,       787,       21,           21,   opportunistic_benefits
State Board of Education Member - Congressional Di 1109479,  349105,   16038,       145,       27,            3,   opportunistic_benefits
State Representative - District 1                  1104271,   38101,   10075,         7,        2,            2,   opportunistic_benefits
State Representative - District 10                  396121,   44675,   27538,         9,        2,            1,   county_wide_contest
State Representative - District 11                  396121,   48977,   17630,        14,        3,            1,   opportunistic_benefits
State Representative - District 12                  396121,   56216,   27530,        13,        5,            1,   opportunistic_benefits
State Representative - District 13                   60892,   58437,    5678,       420,        2,            6,   opportunistic_benefits
State Representative - District 14                  387297,   61620,   12129,        21,        6,            1,   opportunistic_benefits
State Representative - District 15                  387297,   49299,    7305,        22,        2,            1,   opportunistic_benefits
State Representative - District 16                  387297,   44231,       7,        13,        4,            1,   opportunistic_benefits
State Representative - District 17                  387297,   28366,    3038,         7,        0,            1,   opportunistic_benefits
State Representative - District 18                  404409,   49693,    3269,        14,        3,            2,   opportunistic_benefits
State Representative - District 19                  578518,   60740,     123,        24,        8,            2,   opportunistic_benefits
State Representative - District 2                  1104271,   57563,   25328,        11,        5,            1,   opportunistic_benefits
State Representative - District 20                  387297,   59566,   24367,        18,        7,            1,   opportunistic_benefits
State Representative - District 21                  387297,   32517,    4987,         9,        5,            1,   opportunistic_benefits
State Representative - District 22                  387297,   49296,    8225,        20,        3,            1,   opportunistic_benefits
State Representative - District 23                  367779,   54289,   13780,         9,        2,            1,   opportunistic_benefits
State Representative - District 24                  836637,   57090,    7088,         5,        3,            2,   opportunistic_benefits
State Representative - District 25                  367779,   63471,    2536,         8,        4,            1,   opportunistic_benefits
State Representative - District 26                   72676,   50442,    6382,       220,        2,            4,   opportunistic_benefits
State Representative - District 27                  367779,   56651,    7216,        10,        4,            1,   opportunistic_benefits
State Representative - District 28                  367779,   54668,    2892,        12,        5,            1,   opportunistic_benefits
State Representative - District 29                  836637,   53547,   10751,        15,        5,            2,   opportunistic_benefits
State Representative - District 3                  1435159,   44209,   11429,         6,        2,            2,   opportunistic_benefits
State Representative - District 30                  367779,   46082,   10486,        10,        3,            1,   opportunistic_benefits
State Representative - District 31                  468858,   34252,    3612,        20,        2,            1,   opportunistic_benefits
State Representative - District 32                  468858,   37264,       0,        17,        1,            1,   opportunistic_benefits
State Representative - District 33                  698798,   58445,    9257,        54,        3,            3,   opportunistic_benefits
State Representative - District 34                  468858,   45458,    2463,        17,        4,            1,   opportunistic_benefits
State Representative - District 35                  836637,   33547,    8836,        15,        4,            2,   opportunistic_benefits
State Representative - District 36                  799746,   35255,   12918,        14,        1,            2,   opportunistic_benefits
State Representative - District 37                  330888,   52458,   30594,         6,        4,            1,   opportunistic_benefits
State Representative - District 38                  698667,   57881,    4953,        12,        3,            2,   opportunistic_benefits
State Representative - District 39                  248173,   66522,   11059,        18,        2,            1,   opportunistic_benefits
State Representative - District 4                  1104271,   45500,   23692,         7,        2,            1,   opportunistic_benefits
State Representative - District 40                  330888,   45847,    7048,         8,        0,            1,   opportunistic_benefits
State Representative - District 41                  330888,   39001,    9383,        10,        2,            1,   opportunistic_benefits
State Representative - District 42                  330888,   25227,       0,         2,        0,            1,   opportunistic_benefits
State Representative - District 43                  248173,   58106,    1373,         6,        2,            1,   opportunistic_benefits
State Representative - District 44                  248173,   57356,    9124,        13,        1,            1,   opportunistic_benefits
State Representative - District 45                  248173,   62332,   14169,        24,        5,            1,   opportunistic_benefits
State Representative - District 46                  170790,   47649,    2077,       105,        2,            1,   opportunistic_benefits
State Representative - District 47                  204610,   46943,   15319,       353,        3,            9,   opportunistic_benefits
State Representative - District 48                  651255,   46405,       0,        23,        4,            2,   opportunistic_benefits
State Representative - District 49                  864708,   62160,   15851,       221,        5,            4,   opportunistic_benefits
State Representative - District 5                  1104271,   44842,   23853,        12,        4,            1,   opportunistic_benefits
State Representative - District 50                  182397,   27706,     563,         8,        3,            1,   opportunistic_benefits
State Representative - District 51                  458224,   57607,    2832,        13,        1,            1,   opportunistic_benefits
State Representative - District 52                  458224,   56687,   13078,        10,        3,            1,   opportunistic_benefits
State Representative - District 53                  458224,   47581,   20393,        14,        4,            1,   opportunistic_benefits
State Representative - District 54                  112707,   54628,       0,        96,        7,            2,   opportunistic_benefits
State Representative - District 55                   92921,   51840,       0,        28,        0,            1,   opportunistic_benefits
State Representative - District 56                 1215161,   59320,   28536,       184,        8,            7,   opportunistic_benefits
State Representative - District 57                   71086,   46928,    5108,       166,        0,            3,   opportunistic_benefits
State Representative - District 58                   83589,   58355,    5070,       450,        2,            8,   opportunistic_benefits
State Representative - District 59                   61075,   57000,    1149,       141,        3,            3,   opportunistic_benefits
State Representative - District 6                  1104271,   49273,   31272,        10,        1,            1,   opportunistic_benefits
State Representative - District 60                  232512,   55578,   20692,       156,        4,            5,   opportunistic_benefits
State Representative - District 61                  579061,   54485,       0,         5,        4,            2,   opportunistic_benefits
State Representative - District 62                  207360,   41416,    2941,       366,        2,            8,   opportunistic_benefits
State Representative - District 63                  217652,   45838,       0,       310,        3,            7,   opportunistic_benefits
State Representative - District 64                  640621,   58672,   14653,        27,        3,            2,   opportunistic_benefits
State Representative - District 65                  640621,   66814,   15782,        25,        2,            2,   opportunistic_benefits
State Representative - District 7                  1104271,   33229,   14872,         3,        1,            1,   opportunistic_benefits
State Representative - District 8                  1104271,   51055,   33982,         6,        3,            1,   opportunistic_benefits
State Representative - District 9                  1104271,   32425,   14468,        13,        4,            2,   opportunistic_benefits
State Senator - District 10                         387297,   94146,   15533,        36,        4,            1,   opportunistic_benefits
State Senator - District 12                         404409,   80096,    1096,        21,        9,            2,   opportunistic_benefits
State Senator - District 13                         651255,   64934,    7985,        24,        6,            2,   opportunistic_benefits
State Senator - District 14                         458224,   92493,   34653,        25,        7,            1,   opportunistic_benefits
State Senator - District 16                         698667,  109589,    4433,        22,        5,            2,   opportunistic_benefits
State Senator - District 17                         578518,  101924,   31765,        28,       11,            2,   opportunistic_benefits
State Senator - District 18                         396121,   94815,   65330,        24,        4,            1,   opportunistic_benefits
State Senator - District 19                         836637,  103352,   14302,        16,        3,            2,   opportunistic_benefits
State Senator - District 2                          248173,  112175,   24920,        32,        6,            1,   opportunistic_benefits
State Senator - District 21                         799746,   73010,    1457,        36,        8,            2,   opportunistic_benefits
State Senator - District 23                         640621,  112617,   73876,        44,        6,            2,   opportunistic_benefits
State Senator - District 26                        1802938,   84061,   20537,        10,        5,            3,   opportunistic_benefits
State Senator - District 28                         799746,   59713,   14918,        16,        1,            2,   opportunistic_benefits
State Senator - District 29                         330888,   68489,       0,        16,        2,            1,   opportunistic_benefits
State Senator - District 31                        1104271,  100551,   61254,        22,        9,            1,   opportunistic_benefits
State Senator - District 33                        1104271,   74886,   43677,         8,        3,            1,   opportunistic_benefits
State Senator - District 5                          128621,   94004,    3952,       378,        3,            7,   opportunistic_benefits
State Senator - District 6                          129757,   98713,   11155,       742,        5,           13,   opportunistic_benefits
Stratmoor Hills Fire Protection District Ballot Is  387297,    2599,     561,         1,        0,            1,   opportunistic_benefits
Summit County Clerk and Recorder                     17819,   17819,       0,        59,        2,            1,   opportunistic_benefits
Summit County Commissioner - District 1              17819,   17819,    4820,        59,        2,            1,   opportunistic_benefits
Summit County Commissioner - District 2              17819,   17819,    3220,        59,        2,            1,   opportunistic_benefits
Summit County Commissioner - District 3              17819,   17819,       0,        59,        2,            1,   opportunistic_benefits
Summit School District RE-1 Ballot Issue 4A          17819,   17701,    2276,        59,        2,            1,   county_wide_contest
Teller County Ballot Question 1A                     17112,   16583,    1721,        73,        3,            1,   county_wide_contest
Teller County Ballot Question 1B                     17112,   16583,    2509,        73,        3,            1,   opportunistic_benefits
Teller County Commissioner District 1                17112,   16583,       0,        73,        3,            1,   opportunistic_benefits
Teller County Commissioner District 3                17112,   16583,       0,        73,        3,            1,   opportunistic_benefits
Teller County Treasurer                              17112,   16583,       0,        73,        3,            1,   opportunistic_benefits
Thompson School District R2-J Ballot Issue 5A      1036742,   87866,   12328,        29,        0,            3,   opportunistic_benefits
Thompson School District R2-J Ballot Issue 5B      1036742,   87866,   13499,        29,        0,            3,   opportunistic_benefits
Town Councilmember District 1                       248173,    7937,    2765,         3,        1,            1,   opportunistic_benefits
Town Councilmember District 2                       248173,    8840,    2381,         3,        0,            1,   opportunistic_benefits
Town Councilmember District 4                       248173,    6797,     641,         3,        1,            1,   opportunistic_benefits
Town Councilmember District 6                       248173,    9792,    3363,         8,        1,            1,   opportunistic_benefits
Town of Avon - Council Member                        28988,    2380,     340,         2,        0,            1,   opportunistic_benefits
Town of Avon Ballot Issue 2C: Use Tax on New Const   28988,    2380,     138,         2,        0,            1,   opportunistic_benefits
Town of Castle Rock Ballot Issue 2A                 248173,   49516,    5089,        21,        4,            1,   opportunistic_benefits
Town of Crook Referred Ballot Issue 2V               10274,      68,       5,         1,        0,            1,   opportunistic_benefits
Town of De Beque Ballot Question 2B                  92921,     226,      60,         1,        0,            1,   opportunistic_benefits
Town of Elizabeth Question 2B                        20652,    1590,      88,         1,        0,            1,   opportunistic_benefits
Town of Erie - Council Member District 1            396121,    8504,      19,         3,        1,            1,   opportunistic_benefits
Town of Erie - Council Member District 2            578518,    8394,     209,         0,        1,            2,   opportunistic_benefits
Town of Erie - Council Member District 3            182397,    6146,    1014,         5,        2,            1,   opportunistic_benefits
Town of Erie Ballot Issue 3C                        578518,   22978,    1552,         7,        2,            2,   opportunistic_benefits
Town of Erie Mayor                                  578518,   23044,     577,         8,        4,            2,   opportunistic_benefits
Town of Fowler Ballot Issue 2A                        9039,     648,     350,         0,        0,            1,   opportunistic_benefits
Town of Fraser Ballot Question 2A                    10236,     668,     198,         4,        0,            1,   opportunistic_benefits
Town of Gilcrest Ballot Question 2P                 182397,     407,     182,         1,        0,            1,   opportunistic_benefits
Town of Gilcrest Mayor                              182397,     407,       0,         1,        0,            1,   opportunistic_benefits
Town of Gilcrest Trustee                            182397,     407,       0,         1,        0,            1,   opportunistic_benefits
Town of Granby Board of Trustees                     10236,    1231,       4,         6,        0,            1,   opportunistic_benefits
Town of Granby Mayor                                 10236,    1231,     348,         6,        0,            1,   opportunistic_benefits
Town of Gypsum Ballot Issue 2A                       28988,    3782,     366,         6,        0,            1,   opportunistic_benefits
Town of Hayden Councilmember                         33261,    1120,      98,         2,        0,            1,   opportunistic_benefits
Town of Hayden Mayor                                 33261,    1120,       0,         2,        0,            1,   opportunistic_benefits
Town of Hot Sulphur Springs Ballot Issue 2B - Use    10236,     422,     254,         3,        0,            1,   opportunistic_benefits
Town of Hot Sulphur Springs Ballot Issue 2C - Town   10236,     422,      11,         3,        0,            1,   opportunistic_benefits
Town of Hot Sulphur Springs Trustee                  10236,     422,      52,         3,        0,            1,   opportunistic_benefits
Town of Hudson Ballot Issue 2F                      182397,     736,     222,         0,        0,            1,   opportunistic_benefits
Town of Hudson Town Council                         182397,     736,       4,         0,        0,            1,   opportunistic_benefits
Town of Keenesburg Board of Trustees                182397,    1117,      39,         1,        0,            1,   opportunistic_benefits
Town of Kersey Mayor                                182397,     747,       0,         0,        0,            1,   opportunistic_benefits
Town of Kersey Trustee                              182397,     747,      21,         0,        0,            1,   opportunistic_benefits
Town of Keystone Ballot Issue 2A                     17819,     770,     284,         2,        0,            1,   opportunistic_benefits
Town of Keystone Ballot Issue 2B                     17819,     770,     419,         2,        0,            1,   opportunistic_benefits
Town of Kiowa Issue 2A                               20652,     406,     123,         0,        0,            1,   opportunistic_benefits
Town of LaSalle Ballot Question 2K                  182397,    1107,     579,         1,        0,            1,   opportunistic_benefits
Town of LaSalle Mayor                               182397,    1107,     258,         1,        0,            1,   opportunistic_benefits
Town of LaSalle Trustee                             182397,    1107,      65,         1,        0,            1,   opportunistic_benefits
Town of Log Lane Village Mayor                       13669,     352,      47,         1,        0,            1,   opportunistic_benefits
Town of Log Lane Village Trustee                     13669,     352,       0,         1,        0,            1,   opportunistic_benefits
Town of Lyons Ballot Question 2B                    396121,    1564,     883,         0,        0,            1,   opportunistic_benefits
Town of Mead Ballot Issue 2Q                        182397,    4081,     189,         1,        0,            1,   opportunistic_benefits
Town of Mead Ballot Question 2R                     182397,    4081,     731,         1,        0,            1,   opportunistic_benefits
Town of Mead Trustee                                182397,    4081,      41,         1,        0,            1,   opportunistic_benefits
Town of Merino Referred Ballot Issue 2U              10274,     165,      42,         1,        0,            1,   opportunistic_benefits
Town of Minturn Ballot Issue 2B: Excise Tax Increa   28988,     608,     260,         1,        0,            1,   opportunistic_benefits
Town of Monument Ballot Issue 2A                    387297,    7803,     372,         3,        1,            1,   opportunistic_benefits
Town of Monument Ballot Question 2B                 387297,    7803,    3057,         3,        1,            1,   opportunistic_benefits
Town of Monument Councilmember At-Large - 4 Year T  387297,    7803,     302,         3,        1,            1,   opportunistic_benefits
Town of Monument Councilmember Residential Distric  387297,    3256,       0,         1,        0,            1,   opportunistic_benefits
Town of Monument Councilmember Residential Distric  387297,    4547,       0,         2,        1,            1,   opportunistic_benefits
Town of Morrison Ballot Question 2F                 367779,     211,      99,         0,        0,            1,   opportunistic_benefits
Town of Morrison Trustee                            367779,     211,      19,         0,        0,            1,   opportunistic_benefits
Town of Mountain View Ballot Question 2D            367779,     346,      45,         0,        0,            1,   opportunistic_benefits
Town of Mountain View Ballot Question 2E            367779,     346,     109,         0,        0,            1,   opportunistic_benefits
Town of Mt Crested Butte Ballot Issue 2A             11126,     721,     259,         0,        0,            1,   opportunistic_benefits
Town of Oak Creek Ballot Question 2A                 33261,     502,     221,         2,        0,            1,   opportunistic_benefits
Town of Olathe Ballot Issue 2B: Public Safety Tax    26005,     489,     119,         1,        0,            1,   opportunistic_benefits
Town of Palisade Ballot Issue 2A                     92921,    1550,     387,         1,        0,            1,   opportunistic_benefits
Town of Palmer Lake - Mayor                         387297,    1800,      42,         1,        0,            1,   opportunistic_benefits
Town of Palmer Lake - Trustee                       387297,    1800,     133,         1,        0,            1,   opportunistic_benefits
Town of Paonia Ballot Issue 2A                       19786,     965,     181,         3,        0,            1,   opportunistic_benefits
Town of Parker Mayor                                248173,   36454,     463,         8,        0,            1,   opportunistic_benefits
Town of Rockvale - Board of Trustees                 26051,     389,       0,         0,        0,            1,   opportunistic_benefits
Town of Rockvale - Mayor                             26051,     389,     142,         0,        0,            1,   opportunistic_benefits
Town of Severance Ballot Issue 2A                   182397,    6639,    2482,         4,        1,            1,   opportunistic_benefits
Town of Snowmass Village - Mayor                     11348,    1821,     107,         6,        0,            1,   opportunistic_benefits
Town of Snowmass Village - Town Council              11348,    1821,     116,         6,        0,            1,   opportunistic_benefits
Town of Snowmass Village Ballot Question 2D: Appro   11348,    1821,     372,         6,        0,            1,   opportunistic_benefits
Town of Springfield Ballot Issue 2A                   2165,     719,     175,        32,        0,            1,   county_wide_contest
Town of Superior - Trustee                          396121,    8227,      15,         2,        0,            1,   opportunistic_benefits
Town of Superior Ballot Issue 3B                    396121,    8138,     571,         4,        0,            1,   opportunistic_benefits
Town of Wellington Ballot Question 2K               458224,    6695,    1162,         4,        0,            1,   opportunistic_benefits
Town of Wiggins Mayor                                13669,     967,       0,         3,        0,            1,   opportunistic_benefits
Town of Wiggins Trustee                              13669,     967,       1,         3,        0,            1,   opportunistic_benefits
Ute Pass Regional Health Service District Ballot I  278677,   14565,    2423,        58,        3,            3,   opportunistic_benefits
Walsh Hospital District Ballot Issue 6A               2165,     823,     128,        29,        0,            1,   opportunistic_benefits
Washington County Commissioner - District 2           2838,    2838,     700,        31,        0,            1,   county_wide_contest
Washington County Commissioner - District 3           2838,    2838,       0,        31,        0,            1,   opportunistic_benefits
Weld County Commissioner - At-Large                 182397,  182060,       0,        86,       15,            1,   opportunistic_benefits
Weld County Commissioner - District 1               182397,   60299,       0,        34,        2,            1,   opportunistic_benefits
Weld County Commissioner - District 3               182397,   53215,       0,        26,        7,            1,   opportunistic_benefits
Weld County Council - District 1                    182397,   60299,    9927,        34,        2,            1,   opportunistic_benefits
Weld County School District No. RE-7 Ballot Issue   182397,    2729,     731,         0,        0,            1,   opportunistic_benefits
Weld County School District No. RE-7 Ballot Issue   182397,    2729,     720,         0,        0,            1,   opportunistic_benefits
Weld County School District No. RE-9 Ballot Issue   182397,    4939,      59,         4,        0,            1,   opportunistic_benefits
Weld County School District RE-10J Ballot Issue 5D  196066,     549,       3,         0,        0,            2,   opportunistic_benefits
Weld County School District RE-3J Ballot Issue 5F   651255,    8450,     748,         6,        0,            2,   opportunistic_benefits
Weld County School District RE-8 Ballot Issue 5G O  229940,    7823,     490,         5,        2,            2,   opportunistic_benefits
Weld County School District RE-8 Ballot Issue 5H B  229940,    7823,     607,         5,        2,            2,   opportunistic_benefits
West Routt Fire District Ballot Issue 6A             33261,    1576,     102,        16,        0,            1,   opportunistic_benefits
Westminster Public Schools Ballot Issue 4C          468858,   30262,   11562,         8,        0,            1,   opportunistic_benefits
Yuma County Commissioner - District 2                 4719,    4719,       0,        18,        1,            1,   opportunistic_benefits
Yuma County Commissioner - District 3                 4719,    4719,       0,        18,        1,            1,   opportunistic_benefits
Yuma County Court - Jones                             4719,    4719,    2131,        18,        1,            1,   county_wide_contest


Strata Info

county      nmvrs,  npop
Adams         214,  468858
Alamosa        65,   15216
Arapahoe       52,  351540
Archuleta      38,    9489
Baca           91,    2165
Bent           32,    2221
Boulder       120,  396121
Broomfield     53,   47543
Chaffee        89,   14627
Cheyenne       20,    1064
Clear Creek     72,    6180
Conejos        75,    4196
Costilla       47,    2149
Crowley        15,    1729
Custer         34,    3932
Delta          80,   19786
Denver        258, 1104271
Dolores       146,    1453
Douglas        62,  248173
Eagle          47,   28988
El Paso       128,  387297
Elbert         21,   20652
Fremont        21,   26051
Garfield      117,   30750
Gilpin        139,    4183
Grand          57,   10236
Gunnison       49,   11126
Hinsdale       81,     618
Huerfano       53,    4511
Jackson        57,     845
Jefferson      66,  367779
Kiowa          57,    1060
Kit Carson    109,    3807
La Plata       63,   36150
Lake          116,    3973
Larimer       121,  458224
Las Animas     40,    7886
Lincoln        25,    2595
Logan          64,   10274
Mesa           60,   92921
Mineral        18,     766
Moffat         47,    6699
Montezuma      56,   15436
Montrose       32,   26005
Morgan         38,   13669
Otero          36,    9039
Ouray          75,    4157
Park           56,   13392
Phillips       68,    2384
Pitkin         41,   11348
Prowers        18,    5209
Pueblo        381,  170790
Rio Blanco     58,    3728
Rio Grande     35,    6286
Routt         175,   33261
Saguache      119,    3446
San Miguel     38,    5008
Sedgwick       84,    1371
Statewide     199, 4767518
Summit         59,   17819
Teller         74,   17112
Washington     31,    2838
Weld           87,  182397
Yuma           18,    4719


statewideContests                                      Npop, Nc,   needSamples, auditReason
Presidential Electors                              4746866, 3239722,       99, state_wide_contest
Regent of the University of Colorado - At Large    4746866, 3239722,      301, state_wide_contest

Process finished with exit code 0

````