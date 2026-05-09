# Contest Comparison File

````
data class CountyStyles(val countyName: String) {
    val styles = mutableMapOf<Set<String>, Style>()
    var cardCount = 0
}

data class Style(val id: Int, val contests: Set<String>) {
    var cardCount = 0
}
````

are derived from

````
data class Card(val cvrId: Int) {
    val lines = mutableListOf<ComparisonLine>()
}

data class ComparisonLine(
    val countyName: String,
    val contestName: String,
    val imprintedId: String,
    val ballotType: String,
    val cvrChoice: String,
    val mvrChoice: String,
    val cvrId: Int,
)
````

which are the mvr / cvr comparisons

# contestComparison.csv is the audited result, for all contests on the selected ballots ...

county_name,contest_name, imprinted_id,ballot_type,choice_per_voting_computer,audit_board_selection,consensus,record_type,audit_board_comment,timestamp,cvr_id,audit_reason
Broomfield,Broomfield Ballot Question 2G,104-100-11,6,"""Yes/For""","""Yes/For""",YES,uploaded,"",2024-11-19 09:55:14.442508,869211,COUNTY_WIDE_CONTEST
Broomfield,Broomfield Ballot Question 2G,104-19-48,6,"""Yes/For""","""Yes/For""",YES,uploaded,"",2024-11-19 09:17:19.859941,810634,COUNTY_WIDE_CONTEST
Broomfield,Broomfield Ballot Question 2G,104-23-20,3,"""No/Against""","""No/Against""",YES,uploaded,"",2024-11-19 09:30:09.072304,811751,COUNTY_WIDE_CONTEST
Broomfield,Broomfield Ballot Question 2G,104-36-37,6,"""Yes/For""","""Yes/For""",YES,uploaded,"",2024-11-19 09:32:07.561475,815011,COUNTY_WIDE_CONTEST
Broomfield,Broomfield Ballot Question 2G,104-37-41,6,"""Yes/For""","""Yes/For""",YES,uploaded,"",2024-11-19 09:34:04.549326,815168,COUNTY_WIDE_CONTEST
Broomfield,Broomfield Ballot Question 2G,104-61-31,3,"""No/Against""","""No/Against""",YES,uploaded,"",2024-11-19 09:42:38.015308,828814,COUNTY_WIDE_CONTEST
Broomfield,Broomfield Ballot Question 2G,104-74-36,3,"""No/Against""","""No/Against""",YES,uploaded,"",2024-11-19 09:46:41.728707,856760,COUNTY_WIDE_CONTEST

We create BallotStyles from this, (see readContestComparisonCsv()) and use those when generating the simulated CVRs.
We also extract number of nmvrs by contest and by county.

````
Nmvrs by Contest

contest                                               county nmvrs, statewide nmvrs
17th Judicial District Ballot Question 7B                      165,     3
Adams 12 Five Star Schools Ballot Issue 5D                      76,     3
Adams 12 Five Star Schools Ballot Issue 5E                      76,     3
Adams County Ballot Issue 1A                                   112,     0
Adams County Commissioner - District 1                         102,    17
Adams County Commissioner - District 2                         102,    17
Adams County Commissioner - District 5                         102,    17
Adams County Court Judge - Dinnel                              102,    17
Adams County Court Judge - Flaum                               102,    17
Adams County Court Judge - Ivey                                102,    17
Adams County Court Judge - Jean                                102,    17
Adams County Court Judge - Kirby                               102,    17
Adams County Court Judge - Nowak                               102,    17
Adams County School District 14 Ballot Issue 4A                  7,     0
Adams County School District 14 Ballot Issue 4B                  7,     0
Adams-Arapahoe School District 28J Ballot Issue 5A              25,     2
Adams-Arapahoe School District 28J Ballot Issue 5B              25,     2
Affordable Housing 0.5% Sales Tax Increase                      80,     0
Alamosa County Commissioner - District 1                        32,     1
Alamosa County Commissioner - District 3                        32,     1
Alamosa School District RE-11J Ballot Issue 5A                  29,     0
Amendment 79 (CONSTITUTIONAL)                                 3926,   199
Amendment 80 (CONSTITUTIONAL)                                 3868,   199
Amendment 80 (CONSTITUTIONAL) - Rio Blanco                      58,     0
Amendment G (CONSTITUTIONAL)                                  3926,   199
Amendment H (CONSTITUTIONAL)                                  3926,   199
Amendment I (CONSTITUTIONAL)                                  3926,   199
Amendment J (CONSTITUTIONAL)                                  3926,   199
Amendment K (CONSTITUTIONAL)                                  3926,   199
Approval of Multiple Year Financial Obligation with Gilpin C     3,     0
Arapahoe County Ballot Issue 1A                                 52,    17
Arapahoe County Commissioner - District 1                       15,     4
Arapahoe County Commissioner - District 3                       14,     7
Arapahoe County Commissioner - District 5                        6,     0
Arapahoe County Court - Hernandez                               52,    17
Arapahoe County Court - Williford                               52,    17
Archuleta County Commissioner - District 1                      38,     0
Archuleta County Commissioner - District 2                      38,     0
Archuleta County Coroner                                        38,     0
Arvada Fire Protection District Ballot Question 6A               0,     2
Baca County Commissioner - District 1                           88,     0
Baca County Commissioner - District 3                           88,     0
Baca County Coroner                                             88,     0
Baca County Court Judge - Lishchuk                              88,     0
Bent County Commissioner - District 1                           32,     0
Bent County Commissioner-District 3                             32,     0
Bent County Court - Clark                                       32,     0
Boulder County Commissioner - District 1                        48,    15
Boulder County Commissioner - District 2                        48,    15
Boulder County Coroner                                          48,    15
Boulder County Court - Martin                                   48,    15
Broomfield Ballot Question 2A                                   53,     3
Broomfield Ballot Question 2B                                   53,     3
Broomfield Ballot Question 2C                                   53,     3
Broomfield Ballot Question 2D                                   53,     3
Broomfield Ballot Question 2E                                   53,     3
Broomfield Ballot Question 2F                                   53,     3
Broomfield Ballot Question 2G                                   53,     3
Broomfield County Court - DeWick                                53,     3
Brush Rural Fire Protection District Ballot Issue 7A             2,     0
Byers School District 32J Ballot Issue 5C                        0,     2
CANON CITY AREA METROPOLITAN RECREATION AND PARK DISTRICT IS    14,     0
CANON CITY AREA METROPOLITAN RECREATION AND PARK DISTRICT IS    14,     0
CITY OF CANON CITY ISSUE #2A SALES AND USE TAX INCREASE FOR      8,     0
CITY OF TRINIDAD BALLOT ISSUE 2A                                15,     0
CLEAR CREEK COUNTY BALLOT ISSUE 1A                              72,     1
CLEAR CREEK COUNTY LIBRARY DISTRICT BALLOT ISSUE 6A             72,     1
Carbon Valley Parks and Recreation District Ballot Issue 6C     11,     3
Chaffee County Commissioner - District 1                        89,     0
Chaffee County Commissioner - District 2                        89,     0
Chaffee County Court - Bull                                     89,     0
Cherry Creek School District No. 5 Ballot Issue 4A              23,     8
Cherry Creek School District No. 5 Ballot Issue 4B              23,     8
Cheyenne County Commissioner - District 1                       20,     0
Cheyenne County Commissioner - District 3                       20,     0
Cheyenne County Coroner                                         20,     0
Cimarron Hills Fire Protection District Ballot Issue 6A          3,     1
City Council Member                                             28,     0
City of Alamosa Ballot Issue 2A                                 16,     0
City of Alamosa Ballot Issue 2B                                 16,     0
City of Aspen Ballot Issue 2A: Extension of Real Estate Tran    12,     0
City of Aspen Ballot Issue 2B: Extension of Existing 0.45% S    12,     0
City of Aspen Ballot Issue 2C: Imposition of a Use Tax on Mo    12,     0
City of Aurora Ballot Question 3A                               41,     7
City of Boulder Ballot Question 2C                              21,     0
City of Boulder Ballot Question 2D                              21,     0
City of Boulder Ballot Question 2E                              21,     0
City of Central Alderman/City Council                           16,     0
City of Colorado Springs Ballot Issue 2C                        85,    17
City of Colorado Springs Ballot Question 2D                     85,    17
City of Colorado Springs Ballot Question 300                    85,    17
City of Craig Ballot Question 2A                                27,     0
City of Dacono Ballot Question 2G                                3,     0
City of Dacono Ballot Question 2H                                3,     0
City of Dacono Ballot Question 2I                                3,     0
City of Dacono Ballot Question 2J                                3,     0
City of Dacono Council Members                                   3,     0
City of Dacono Mayor                                             3,     0
City of Englewood Ballot Issue 2C                                2,     2
City of Englewood Ballot Question  302                           2,     2
City of Englewood Ballot Question 301                            2,     2
City of Evans Mayor                                              4,     2
City of Evans Ward 2 Council Member                              1,     1
City of Evans Ward 3 Council Member                              3,     1
City of Fort Collins Ballot Issue 2A                            23,     0
City of Fort Collins Ballot Question 2B                         23,     0
City of Fort Collins Ballot Question 2C                         23,     0
City of Fort Collins Ballot Question 2D                         23,     0
City of Fort Lupton Ballot Issue 2B                              2,     1
City of Fort Lupton Ballot Issue 2D                              2,     1
City of Fort Lupton Ballot Question 2C                           2,     1
City of Fort Lupton Ballot Question 2E                           2,     1
City of Fort Morgan Ballot Question 2A                          15,     1
City of Greeley Ballot Issue 2L                                 16,     2
City of Greeley Ballot Issue 2M                                 16,     2
City of Greeley Ballot Question 2N                              16,     2
City of Greeley Ballot Question 2O                              16,     2
City of Holyoke Mayor                                           28,     0
City of Idaho Springs Ballot Question 2A                         6,     0
City of Lafayette Ballot Question 2A                             6,     0
City of Lakewood Ballot Issue 2A                                20,     4
City of Leadville Ballot Issue 2B                               35,     0
City of Leadville Ballot Question 2A                            35,     0
City of Littleton Ballot Issue 3B                                6,     1
City of Longmont Ballot Issue 3A                                19,     0
City of Loveland Ballot Issue 2E                                17,     0
City of Loveland Ballot Issue 2F                                17,     0
City of Loveland Ballot Issue 2G                                17,     0
City of Loveland Ballot Question 2H                             17,     0
City of Loveland Ballot Question 2I                             17,     0
City of Loveland Ballot Question 2J                             17,     0
City of Montrose Ballot Issue 2A                                15,     0
City of Pueblo Ballot Question 2A - Charter Amendment - Muni   118,     0
City of Pueblo Ballot Question 2B - Charter Amendment - Mode   118,     0
City of Pueblo Ballot Question 2C - Charter Amendment - Grea   118,     0
City of Pueblo Ballot Question 2D - Charter Amendment - Muni   118,     0
City of Sterling Referred Ballot Issue 2T                       33,     0
City of Thornton Question 2A                                    32,     0
City of Walsenburg Ballot Question 300 - Walsenburg Trash In    22,     0
City of Westminster Ballot Issue 3C                             27,     2
City of Westminster Ballot Question 3D                          27,     2
City of Wheat Ridge Ballot Question 2B                           2,     0
City of Wheat Ridge Ballot Question 2C                           2,     0
City of Woodland Park Ballot Question 2A                        24,     0
City of Yuma Ballot Issue 2A - Levying a Lodging Tax in the      3,     0
Clear Creek County Commissioner - District 2                    72,     1
Clear Creek County Commissioner - District 3                    72,     1
Clear Creek County Court - Jones                                72,     1
Clear Creek County Sheriff                                      72,     1
Colorado Court of Appeals Judge - Dunn                        3905,   199
Colorado Court of Appeals Judge - Jones                       3905,   199
Colorado Court of Appeals Judge - Kuhn                        3905,   199
Colorado Court of Appeals Judge - Roman                       3905,   199
Colorado Court of Appeals Judge - Schutz                      3905,   199
Colorado Supreme Court Justice - Berkenkotter                 3905,   199
Colorado Supreme Court Justice - Boatright                    3905,   199
Colorado Supreme Court Justice - Marquez                      3905,   199
Conejos County Commissioner District 1                          75,     0
Conejos County Commissioner District 3                          75,     0
Conejos County Court - Kelly                                    75,     0
Cortez Fire Protection District Ballot Issue 6-A                23,     1
Costilla County Commissioner District 1                         47,     0
Costilla County Commissioner District 3                         47,     0
Councilmember                                                    8,     0
County Commissioner - District 2                               117,     0
County Commissioner - District 3                               117,     0
County Court Judge - Cheyenne                                   20,     0
County Court Judge - Denver Blackett                            70,    22
County Court Judge - Denver Cherry                              70,    22
County Court Judge - Denver Faragher                            70,    22
County Court Judge - Denver Goble                               70,    22
County Court Judge - Denver Pallares                            70,    22
County Court Judge - Denver Rodarte                             70,    22
County Court Judge - Denver Rudolph                             70,    22
County Court Judge - Denver Schwartz                            70,    22
County Court Judge - Denver Simonet                             70,    22
County Court Judge - Denver Spahn                               70,    22
County Court Judge - Gunnison                                   49,     0
County Court Judge - Routt                                      77,     0
Crawford Water Conservancy District Ballot Issue 7B              2,     0
Crowley County Commissioner - District 2                        15,     0
Crowley County Commissioner - District 3                        15,     0
Custer County Board of County Commissioners - District 2        34,     0
Custer County Board of County Commissioners - District 3        34,     0
Delta County Commissioner - District 2                          80,     3
Delta County Commissioner - District 3                          80,     3
Delta County Court - Zeerip                                     80,     3
Denver Ballot Issue 2Q                                          99,     0
Denver Ballot Issue 2R                                          99,     0
Denver Downtown Development Authority Ballot Issue 6A            1,     0
Denver Initiated Ordinance 308                                  89,     0
Denver Initiated Ordinance 309                                  89,     0
Denver Public Schools Ballot Issue 4A                           89,     0
Denver Referred Question 2S                                     99,     0
Denver Referred Question 2T                                     99,     0
Denver Referred Question 2U                                     99,     0
Denver Referred Question 2V                                     99,     0
Denver Referred Question 2W                                     89,     0
District Attorney - 10th Judicial District                     189,     4
District Attorney - 11th Judicial District                     193,     1
District Attorney - 12th Judicial District                     326,     1
District Attorney - 13th Judicial District                     409,     2
District Attorney - 14th Judicial District                     181,     0
District Attorney - 15th Judicial District                     174,     0
District Attorney - 16th Judicial District                      83,     1
District Attorney - 17th Judicial District                     155,    20
District Attorney - 18th Judicial District                      73,    19
District Attorney - 19th Judicial District                      86,    15
District Attorney - 1st Judicial District                      204,    25
District Attorney - 20th Judicial District                      48,    15
District Attorney - 21st Judicial District                      60,     4
District Attorney - 22nd Judicial District                     199,     1
District Attorney - 23rd Judicial District                     108,    13
District Attorney - 2nd Judicial District                       70,    22
District Attorney - 3rd Judicial District                       93,     1
District Attorney - 4th Judicial District                      201,    35
District Attorney - 5th Judicial District                      293,     5
District Attorney - 6th Judicial District                      101,     2
District Attorney - 7th Judicial District                      355,     5
District Attorney - 8th Judicial District                      109,    10
District Attorney - 9th Judicial District                      216,     0
District Court Judge - 10th Judicial District - O'Shea         189,     4
District Court Judge - 11th Judicial District - Turner         193,     1
District Court Judge - 12th Judicial District - Cortez         326,     1
District Court Judge - 13th Judicial District - Haenlein       409,     2
District Court Judge - 13th Judicial District - James          409,     2
District Court Judge - 13th Judicial District - McGuire        409,     2
District Court Judge - 15th Judicial District - Davidson       174,     0
District Court Judge - 16th Judicial District - Vigil           83,     1
District Court Judge - 17th Judicial District - Holbrook       155,    20
District Court Judge - 17th Judicial District - Martin         155,    20
District Court Judge - 17th Judicial District - Vasquez        155,    20
District Court Judge - 18th Judicial District - Figa           160,    30
District Court Judge - 18th Judicial District - Leutwyler      160,    30
District Court Judge - 18th Judicial District - Lung           160,    30
District Court Judge - 18th Judicial District - McLean         160,    30
District Court Judge - 18th Judicial District - Toussaint      160,    30
District Court Judge - 18th Judicial District - Whitaker       160,    30
District Court Judge - 18th Judicial District - Whitfield      160,    30
District Court Judge - 19th Judicial District - Crowther        86,    15
District Court Judge - 19th Judicial District - Esser           86,    15
District Court Judge - 19th Judicial District - Taylor          86,    15
District Court Judge - 1st Judicial District - Carrithers      204,    25
District Court Judge - 1st Judicial District - Hunt            204,    25
District Court Judge - 20th Judicial District - Collins         48,    15
District Court Judge - 20th Judicial District - Gunning         48,    15
District Court Judge - 20th Judicial District - Lindsey         48,    15
District Court Judge - 20th Judicial District - Mulvahill       48,    15
District Court Judge - 22nd Judicial District - Plewe          199,     1
District Court Judge - 2nd Judicial District Bailey             70,    22
District Court Judge - 2nd Judicial District Espinosa           70,    22
District Court Judge - 2nd Judicial District Grant              70,    22
District Court Judge - 2nd Judicial District Moses              70,    22
District Court Judge - 2nd Judicial District Myers              70,    22
District Court Judge - 2nd Judicial District Schutte            70,    22
District Court Judge - 2nd Judicial District Scoville           70,    22
District Court Judge - 2nd Judicial District Trujillo           70,    22
District Court Judge - 4th Judicial District - Bentley         201,    35
District Court Judge - 4th Judicial District - Billings-Vela   201,    35
District Court Judge - 4th Judicial District - Brady           201,    35
District Court Judge - 4th Judicial District - Evig            201,    35
District Court Judge - 4th Judicial District - Findorff        201,    35
District Court Judge - 4th Judicial District - May             201,    35
District Court Judge - 4th Judicial District - Moller          201,    35
District Court Judge - 4th Judicial District - Shakes          201,    35
District Court Judge - 5th Judicial District - Olguin-Fresqu   293,     5
District Court Judge - 6th Judicial District - Shropshire      101,     2
District Court Judge - 7th Judicial District - Schultz         355,     5
District Court Judge - 7th Judicial District - Yoder           355,     5
District Court Judge - 8th Judicial District - Cure            109,    10
District Court Judge - 8th Judicial District - Findley         109,    10
District Court Judge - 9th Judicial District - Norrdin         216,     0
Dolores County Commissioner District 2                         143,     0
Dolores County Commissioner District 3                         143,     0
Dolores School District RE-4A Ballot Issue 4-A                  10,     0
Douglas County Commissioner - District 2                        62,    11
Douglas County Commissioner - District 3                        62,    11
Douglas County Court - Waidler                                  62,    11
Douglas County School District RE-1 Ballot Issue 5A             67,    11
Dove Creek Ambulance District Ballot Issue 6A                  117,     0
Durango School District 9-R Ballot Issue 4A                     47,     2
Eagle County Commissioner - District 1                          46,     2
Eagle County Commissioner - District 2                          46,     2
Eagle River Fire Protection District Ballot Issue 6A            17,     0
El Paso County Commissioner - District 2                        33,     6
El Paso County Commissioner - District 3                        27,     6
El Paso County Commissioner - District 4                        22,     6
El Paso County Court - Ankeny                                  128,    32
El Paso County Court - Fennick                                 128,    32
El Paso County Court - Gerhart                                 128,    32
El Paso County Court - Katzman                                 128,    32
El Paso County Court - McKedy                                  128,    32
El Paso County School District No. 20 (Academy) Ballot Issue    27,     8
Elbert County Commissioner - District 1                         21,     2
Elbert County Commissioner - District 3                         21,     2
Elbert County Question 1A                                       21,     2
Elizabeth Mayor                                                  1,     0
Fairmount Fire Protection District Ballot Issue 6B               0,     2
Fairmount Fire Protection District Ballot Question 6C            0,     2
Flagler Rural Fire Protection District Ballot Issue 6C          16,     0
Flying Horse Metropolitan District No. 2 Ballot Question 6C      1,     0
Foothills Park & Recreation District Ballot Issue 6D            13,     7
Fremont County Board of County Commissioners - District 1       20,     1
Fremont County Board of County Commissioners - District 3       20,     1
Fremont County Surveyor                                         20,     1
Frenchman School District No. RE-3 Referred Ballot Issue 4D      3,     0
Funding Poncha Springs Law Enforcement And Parks And Recreat     9,     0
Garfield County Court - Roff                                   117,     0
Gilpin County Commissioner - District 1                        139,     1
Gilpin County Commissioner - District 3                        139,     1
Glenwood Springs Ballot Issue 2A  Tax increase for Streets      23,     0
Grand County Ballot Issue 1A - Lodging Tax Increase             57,     0
Grand County Commissioner - District 1                          57,     0
Grand County Commissioner - District 2                          57,     0
Gunnison County Commissioner - District 1                       49,     0
Gunnison County Commissioner - District 2                       49,     0
Gunnison County Library District Ballot Issue 6A                49,     0
Harrison School District No. 2 Ballot Issue 4A                   9,     0
Hartsel Fire Protection District Ballot Issue 6A - Mill Levy     7,     0
Hinsdale County Commissioner District 1                         81,     0
Hinsdale County Commissioner District 3                         81,     0
Hinsdale County Coroner                                         81,     0
Hinsdale County Sheriff                                         81,     0
Holyoke School District RE-1J Ballot Issue 5K - Bonds           47,     0
Huerfano County Commissioner - District 1                       53,     1
Huerfano County Commissioner - District 2                       53,     1
Hyland Hills Park and Recreation District Ballot Issue No. 6    22,     0
Increase in City Lodging Tax                                     3,     0
Jackson County Commissioner Dist 2                              57,     0
Jackson County Commissioner Dist 3                              57,     0
Jefferson County Ballot Issue 1A                                65,    24
Jefferson County Commissioner - District 1                      65,    24
Jefferson County Commissioner - District 2                      65,    24
Jefferson County Court Burback                                  65,    24
Jefferson County Court Carpenter                                65,    24
Jefferson County Court Goman                                    65,    24
Jefferson County Court Peper                                    65,    24
Jefferson County Court Wheeler                                  65,    24
Ken-Caryl Metropolitan District Ballot Issue 6F                  1,     0
Kiowa County Ballot Issue 1A                                    48,     0
Kiowa County Ballot Issue 1B - Lodging Tax                      48,     0
Kiowa County Commissioner - District 1                          48,     0
Kiowa County Commissioner - District 3                          48,     0
Kiowa County Hospital District Ballot Question 6A               57,     0
Kiowa County Public Library District Ballot Question 6B         48,     0
Kiowa School District C-2 Issue 4A                               4,     0
Kit Carson County Commissioner - District 1                    106,     0
Kit Carson County Commissioner - District 3                    106,     0
La Plata County Ballot Issue 1A                                 45,     1
La Plata County Commissioner District 2                         63,     2
La Plata County Commissioner District 3                         63,     2
La Plata County Treasurer                                       63,     2
Lake County Assessor                                           116,     0
Lake County Commissioner District 1                            116,     0
Lake County Commissioner District 2                            116,     0
Lake County Commissioner District 3                            116,     0
Lake County School District R-1 Ballot Issue 4A                116,     0
Larimer County Ballot Issue 1A                                  69,     0
Larimer County Clerk and Recorder                               52,    10
Larimer County Commissioner - District 2                        52,    10
Larimer County Commissioner - District 3                        52,    10
Larimer County Court Judge - Ecton                              52,    10
Larimer County Court Judge - Lehman                             52,    10
Las Animas County Commissioner District 1                       40,     0
Las Animas County Commissioner District 2                       40,     0
Leadville Lake County Regional Housing Authority Ballot Issu   116,     0
Lincoln County Commissioner - District 2                        25,     0
Lincoln County Commissioner - District 3                        25,     0
Logan County Commissioner - District 1                          64,     0
Logan County Commissioner - District 2                          64,     0
Logan County Court Judge - Brammer                              64,     0
Mesa County Ballot Issue 1A                                     60,     4
Mesa County Ballot Issue 1B                                     60,     4
Mesa County Commissioner - District 1                           60,     4
Mesa County Commissioner - District 3                           60,     4
Mesa County Court Judge - Grattan III                           60,     4
Mesa County Valley School District 51 Ballot Issue 4A           58,     4
Mesa County Valley School District 51 Ballot Issue 4B           58,     4
Mineral County Commissioner - District 2                        18,     0
Mineral County Commissioner - District 3                        18,     0
Moffat County Commissioner District 1                           47,     0
Moffat County Commissioner District 2                           47,     0
Montezuma Cortez RE-1 Ballot Issue 4-B                          34,     1
Montezuma County Ballot Issue 1-A                               56,     1
Montezuma County Commissioner District 2                        56,     1
Montezuma County Commissioner District 3                        56,     1
Montrose County Commissioner - District 1                       32,     1
Montrose County Commissioner - District 3                       32,     1
Montrose County Court - Beckenhauer                             32,     1
Montrose County Court - Harvell                                 32,     1
Montrose County School District RE-1J Ballot Issue 5A           37,     1
Morgan County Commissioner District 1                           38,     1
Morgan County Commissioner District 3                           38,     1
NORTH PARK SCHOOL DISTRICT R-1 BALLOT ISSUE 4A                  57,     0
North Range Metropolitan District No 2 Ballot Question 6I        1,     0
North Range Metropolitan District No 2 Ballot Question 6J        1,     0
North Range Metropolitan District No 2 Ballot Question 6K        1,     0
North Range Metropolitan District No 2 Ballot Question 6L        1,     0
Norwood Fire Protection District Ballot Issue 7A                 3,     0
Norwood School District R-2J Ballot Issue 5B                     3,     0
Otero County Ballot Question 1A                                 36,     1
Otero County Commissioner District 1                            36,     1
Otero County Commissioner District 3                            36,     1
Ouray County Commissioner - District 1                          75,     0
Ouray County Commissioner - District 3                          75,     0
Ouray County Court - Thomasson                                  75,     0
Park County Commissioner District 1                             50,     0
Park County Commissioner District 2                             50,     0
Phillips County Commissioner - District 2                       68,     0
Phillips County Commissioner - District 3                       68,     0
Phillips County Court Judge - Killin                            68,     0
Phillips County Sheriff                                         68,     0
Pitkin County Ballot Issue 1A: Affordable and Workforce Hous    41,     0
Pitkin County Ballot Issue 1B: County Solid Waste Center Bon    41,     0
Pitkin County Ballot Question 1C: BOCC Referred Home Rule Ch    41,     0
Pitkin County Ballot Question 200: Citizen Initiated Home Ru    41,     0
Pitkin County Commissioner - District 3                         41,     0
Pitkin County Commissioner - District 4                         41,     0
Pitkin County Commissioner - District 5                         41,     0
Pitkin County Court - Andrews                                   41,     0
Poudre School District R-1 Ballot Issue 4A                      35,     0
Presidential Electors                                         3905,   199
Proposition 127 (STATUTORY)                                   3979,   162
Proposition 128 (STATUTORY)                                   3979,   162
Proposition 129 (STATUTORY)                                   3997,   151
Proposition 130 (STATUTORY)                                   3891,   151
Proposition 130 (STATUTORY) - Kit Carson                       106,     0
Proposition 131 (STATUTORY)                                   3997,   151
Proposition JJ (STATUTORY)                                    3955,   177
Proposition KK (STATUTORY)                                    3955,   177
Prowers County Ballot Issue 1A                                  18,     0
Prowers County Commissioner - District 1                        18,     0
Prowers County Commissioner - District 3                        18,     0
Pueblo County Commissioner - District 1                        189,     4
Pueblo County Commissioner - District 2                        189,     4
Pueblo County Court - Silva                                    189,     4
Pueblo County Court - Vellar                                   189,     4
Pueblo County Rural 70 School District Ballot Issue 4A          74,     0
Rangely School District RE-4 Ballot Issue 4A                    18,     0
Regent of the University of Colorado - At Large               3905,   199
Regent of the University of Colorado - Congressional Distric  1630,    19
Regent of the University of Colorado - Congressional Distric   127,    32
Regional Transportation District Ballot Issue 7A               463,    48
Regional Transportation District Ballot Question 7B              1,     0
Regional Transportation District Director - District A          26,    10
Regional Transportation District Director - District D          25,     5
Regional Transportation District Director - District E          13,     4
Regional Transportation District Director - District F          16,     3
Regional Transportation District Director - District G          23,     6
Regional Transportation District Director - District H          18,     5
Regional Transportation District Director - District I          74,    13
Regional Transportation District Director - District M          21,    10
Representative to the 119th United States Congress - Distric    69,    22
Representative to the 119th United States Congress - Distric   587,    32
Representative to the 119th United States Congress - Distric  1630,    19
Representative to the 119th United States Congress - Distric   787,    21
Representative to the 119th United States Congress - Distric   127,    32
Representative to the 119th United States Congress - Distric    69,    17
Representative to the 119th United States Congress - Distric   491,    29
Representative to the 119th United States Congress - Distric   145,    27
Retain Revenue For Affordable Housing                           80,     0
Rico Fire Protection District Ballot Question 6B                25,     0
Rio Blanco County Commissioner District 2                       58,     0
Rio Blanco County Commissioner District 3                       58,     0
Rio Grande County Commissioner District 1                       35,     0
Rio Grande County Commissioner District 3                       35,     0
Rio Grande County Court - Stenger                               35,     0
Routt County Commissioner - District 1                          77,     0
Routt County Commissioner - District 2                          77,     0
Saguache County Commissioner District 1                        119,     0
Saguache County Commissioner District 2                        119,     0
Saguache County Court - Schuenemann                            119,     0
San Miguel Authority for Regional Transportation (SMART) Bal    59,     1
San Miguel County Ballot Question 1A                            38,     1
San Miguel County Ballot Question 1B                            38,     1
San Miguel County Clerk and Recorder                            38,     1
San Miguel County Commissioner - District 1                     38,     1
San Miguel County Commissioner - District 3                     38,     1
Sedgwick County Commissioner - District 2                       84,     0
Sedgwick County Commissioner - District 3                       84,     0
Sedgwick County Court - Landry                                  84,     0
Sedgwick County Sheriff                                         84,     0
South Adams County Water and Sanitation District Ballot Issu    20,     0
St. Vrain Valley School District RE-1J Ballot Issue 5C          44,     5
St. Vrain and Left Hand Water Conservancy District Ballot Is    26,     0
State Board of Education Member - Congressional District 2     587,    32
State Board of Education Member - Congressional District 3    1630,    19
State Board of Education Member - Congressional District 4     787,    21
State Board of Education Member - Congressional District 8     145,    27
State Representative - District 1                                7,     2
State Representative - District 10                               9,     2
State Representative - District 11                              14,     3
State Representative - District 12                              13,     5
State Representative - District 13                             420,     2
State Representative - District 14                              21,     6
State Representative - District 15                              22,     2
State Representative - District 16                              13,     4
State Representative - District 17                               7,     0
State Representative - District 18                              14,     3
State Representative - District 19                              24,     8
State Representative - District 2                               11,     5
State Representative - District 20                              18,     7
State Representative - District 21                               9,     5
State Representative - District 22                              20,     3
State Representative - District 23                               9,     2
State Representative - District 24                               5,     3
State Representative - District 25                               8,     4
State Representative - District 26                             220,     2
State Representative - District 27                              10,     4
State Representative - District 28                              12,     5
State Representative - District 29                              15,     5
State Representative - District 3                                6,     2
State Representative - District 30                              10,     3
State Representative - District 31                              20,     2
State Representative - District 32                              17,     1
State Representative - District 33                              54,     3
State Representative - District 34                              17,     4
State Representative - District 35                              15,     4
State Representative - District 36                              14,     1
State Representative - District 37                               6,     4
State Representative - District 38                              12,     3
State Representative - District 39                              18,     2
State Representative - District 4                                7,     2
State Representative - District 40                               8,     0
State Representative - District 41                              10,     2
State Representative - District 42                               2,     0
State Representative - District 43                               6,     2
State Representative - District 44                              13,     1
State Representative - District 45                              24,     5
State Representative - District 46                             105,     2
State Representative - District 47                             353,     3
State Representative - District 48                              23,     4
State Representative - District 49                             221,     5
State Representative - District 5                               12,     4
State Representative - District 50                               8,     3
State Representative - District 51                              13,     1
State Representative - District 52                              10,     3
State Representative - District 53                              14,     4
State Representative - District 54                              96,     7
State Representative - District 55                              28,     0
State Representative - District 56                             184,     8
State Representative - District 57                             166,     0
State Representative - District 58                             450,     2
State Representative - District 59                             141,     3
State Representative - District 6                               10,     1
State Representative - District 60                             156,     4
State Representative - District 61                               5,     4
State Representative - District 62                             366,     2
State Representative - District 63                             310,     3
State Representative - District 64                              27,     3
State Representative - District 65                              25,     2
State Representative - District 7                                3,     1
State Representative - District 8                                6,     3
State Representative - District 9                               13,     4
State Senator - District 10                                     36,     4
State Senator - District 12                                     21,     9
State Senator - District 13                                     24,     6
State Senator - District 14                                     25,     7
State Senator - District 16                                     22,     5
State Senator - District 17                                     28,    11
State Senator - District 18                                     24,     4
State Senator - District 19                                     16,     3
State Senator - District 2                                      32,     6
State Senator - District 21                                     36,     8
State Senator - District 23                                     44,     6
State Senator - District 26                                     10,     5
State Senator - District 28                                     16,     1
State Senator - District 29                                     16,     2
State Senator - District 31                                     22,     9
State Senator - District 33                                      8,     3
State Senator - District 5                                     378,     3
State Senator - District 6                                     742,     5
Stratmoor Hills Fire Protection District Ballot Issue 6B         1,     0
Summit County Clerk and Recorder                                59,     2
Summit County Commissioner - District 1                         59,     2
Summit County Commissioner - District 2                         59,     2
Summit County Commissioner - District 3                         59,     2
Summit School District RE-1 Ballot Issue 4A                     59,     2
Teller County Ballot Question 1A                                73,     3
Teller County Ballot Question 1B                                73,     3
Teller County Commissioner District 1                           73,     3
Teller County Commissioner District 3                           73,     3
Teller County Treasurer                                         73,     3
Thompson School District R2-J Ballot Issue 5A                   29,     0
Thompson School District R2-J Ballot Issue 5B                   29,     0
Town Councilmember District 1                                    3,     1
Town Councilmember District 2                                    3,     0
Town Councilmember District 4                                    3,     1
Town Councilmember District 6                                    8,     1
Town of Avon - Council Member                                    2,     0
Town of Avon Ballot Issue 2C: Use Tax on New Construction to     2,     0
Town of Castle Rock Ballot Issue 2A                             21,     4
Town of Crook Referred Ballot Issue 2V                           1,     0
Town of De Beque Ballot Question 2B                              1,     0
Town of Elizabeth Question 2B                                    1,     0
Town of Erie - Council Member District 1                         3,     1
Town of Erie - Council Member District 2                         0,     1
Town of Erie - Council Member District 3                         5,     2
Town of Erie Ballot Issue 3C                                     7,     2
Town of Erie Mayor                                               8,     4
Town of Fraser Ballot Question 2A                                4,     0
Town of Gilcrest Ballot Question 2P                              1,     0
Town of Gilcrest Mayor                                           1,     0
Town of Gilcrest Trustee                                         1,     0
Town of Granby Board of Trustees                                 6,     0
Town of Granby Mayor                                             6,     0
Town of Gypsum Ballot Issue 2A                                   6,     0
Town of Hayden Councilmember                                     2,     0
Town of Hayden Mayor                                             2,     0
Town of Hot Sulphur Springs Ballot Issue 2B - Use Tax on Con     3,     0
Town of Hot Sulphur Springs Ballot Issue 2C - Town Lodging T     3,     0
Town of Hot Sulphur Springs Trustee                              3,     0
Town of Keenesburg Board of Trustees                             1,     0
Town of Keystone Ballot Issue 2A                                 2,     0
Town of Keystone Ballot Issue 2B                                 2,     0
Town of LaSalle Ballot Question 2K                               1,     0
Town of LaSalle Mayor                                            1,     0
Town of LaSalle Trustee                                          1,     0
Town of Log Lane Village Mayor                                   1,     0
Town of Log Lane Village Trustee                                 1,     0
Town of Mead Ballot Issue 2Q                                     1,     0
Town of Mead Ballot Question 2R                                  1,     0
Town of Mead Trustee                                             1,     0
Town of Merino Referred Ballot Issue 2U                          1,     0
Town of Minturn Ballot Issue 2B: Excise Tax Increase on Shor     1,     0
Town of Monument Ballot Issue 2A                                 3,     1
Town of Monument Ballot Question 2B                              3,     1
Town of Monument Councilmember At-Large - 4 Year Term            3,     1
Town of Monument Councilmember Residential District 1 - 4 Ye     1,     0
Town of Monument Councilmember Residential District 2 - 4 Ye     2,     1
Town of Oak Creek Ballot Question 2A                             2,     0
Town of Olathe Ballot Issue 2B: Public Safety Tax                1,     0
Town of Palisade Ballot Issue 2A                                 1,     0
Town of Palmer Lake - Mayor                                      1,     0
Town of Palmer Lake - Trustee                                    1,     0
Town of Paonia Ballot Issue 2A                                   3,     0
Town of Parker Mayor                                             8,     0
Town of Severance Ballot Issue 2A                                4,     1
Town of Snowmass Village - Mayor                                 6,     0
Town of Snowmass Village - Town Council                          6,     0
Town of Snowmass Village Ballot Question 2D: Approve Workfor     6,     0
Town of Springfield Ballot Issue 2A                             32,     0
Town of Superior - Trustee                                       2,     0
Town of Superior Ballot Issue 3B                                 4,     0
Town of Wellington Ballot Question 2K                            4,     0
Town of Wiggins Mayor                                            3,     0
Town of Wiggins Trustee                                          3,     0
Ute Pass Regional Health Service District Ballot Issue 7A1      58,     3
Walsh Hospital District Ballot Issue 6A                         29,     0
Washington County Commissioner - District 2                     31,     0
Washington County Commissioner - District 3                     31,     0
Weld County Commissioner - At-Large                             86,    15
Weld County Commissioner - District 1                           34,     2
Weld County Commissioner - District 3                           26,     7
Weld County Council - District 1                                34,     2
Weld County School District No. RE-9 Ballot Issue 4C             4,     0
Weld County School District RE-3J Ballot Issue 5F                6,     0
Weld County School District RE-8 Ballot Issue 5G                 5,     2
Weld County School District RE-8 Ballot Issue 5H                 5,     2
West Routt Fire District Ballot Issue 6A                        16,     0
Westminster Public Schools Ballot Issue 4C                       8,     0
Yuma County Commissioner - District 2                           18,     1
Yuma County Commissioner - District 3                           18,     1
Yuma County Court - Jones                                       18,     1
````