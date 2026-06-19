# D'Hondt Notes
_last changed 04/16/2026_

<!-- TOC -->
* [D'Hondt Notes](#dhondt-notes)
  * [Show Contest assertions](#show-contest-assertions)
    * [Dhondt assertion doesnt meet risk limit](#dhondt-assertion-doesnt-meet-risk-limit)
    * [Threshold assertion doesnt meet risk limit](#threshold-assertion-doesnt-meet-risk-limit)
    * [Dhondt and Threshold assertion dont meet risk limit](#dhondt-and-threshold-assertion-dont-meet-risk-limit)
    * [Sum of candidate ranges across all contests](#sum-of-candidate-ranges-across-all-contests)
  * [Notes From Bounding paper 04/12/2026](#notes-from-bounding-paper-04122026)
    * [2.1 Context of the Audit](#21-context-of-the-audit)
    * [2.2 Election manifest](#22-election-manifest)
    * [2.3 Auditing D’Hondt Elections](#23-auditing-dhondt-elections)
    * [2.4 Adding a Threshold](#24-adding-a-threshold)
      * [AboveThreshold](#abovethreshold)
      * [BelowThreshold](#belowthreshold)
    * [3.1 Check margins](#31-check-margins)
    * [3.2 Compare expectations using ALPHA martingales.](#32-compare-expectations-using-alpha-martingales)
  * [Notes From Proportional paper 02/05/2026](#notes-from-proportional-paper-02052026)
    * [Section 3. Creating assorters from assertions](#section-3-creating-assorters-from-assertions)
    * [Above Assertion](#above-assertion)
    * [Below Assertion](#below-assertion)
    * [Section 5.1 highest averages](#section-51-highest-averages)
    * [Section 5.2 Simple D’Hondt: Party-only voting](#section-52-simple-dhondt-party-only-voting)
    * [Section 5.3  More complex methods: Multi-candidate voting](#section-53--more-complex-methods-multi-candidate-voting)
<!-- TOC -->


## Show Contest assertions

Experimental output for "risk measuring" audits. In this case, we have limited each audit to 1000 samples.

### Dhondt assertion doesnt meet risk limit

````

DHondtContest 'Anvers' (1) DHONDT voteForN=1 votes={6=368877, 1=249826, 5=127973, 8=125894, 4=125257, 7=90370, 2=70890, 10=10341, 9=8639, 11=7221, 3=4213, 12=1686} undervotes=44400, voteForN=1
   winners=[6, 1, 5, 8, 4, 7, 2] Nc=1235587 Nphantoms=0 Nu=44400 sumVotes=1191187
   nseats=24 winners={6=8, 1=5, 5=3, 8=3, 4=2, 7=2, 2=1} belowMin=[3, 9, 10, 11, 12] threshold=0.05 minVotes=59560
   fw=41964.7 fl=41752.3 fw-fl=212 Npop=1235587 dilutedMargin=0.0516% reportedMargin=0.0516% recountMargin=0.5060% 


candidate          Round:           1 |           2 |           3 |           4 |           5 |           6 |           7 |           8 |           9 |
 1        VLAAMS BELANG :  249826 (2) |  124913 (7) |  83275 (11) |  62456 (17) |  49965 (20) |       41637 |       35689 |       31228 |       27758 |
 2             open vld :  70890 (13) |       35445 |       23630 |       17722 |       14178 |       11815 |       10127 |        8861 |        7876 |
 3          Volt Europa*:        4213 |        2106 |        1404 |        1053 |         842 |         702 |         601 |         526 |         468 |
 4                 PVDA :  125257 (6) |  62628 (16) |       41752 |       31314 |       25051 |       20876 |       17893 |       15657 |       13917 |
 5              Vooruit :  127973 (4) |  63986 (14) |  42657 (23) |       31993 |       25594 |       21328 |       18281 |       15996 |       14219 |
 6                 N-VA :  368877 (1) |  184438 (3) |  122959 (8) |   92219 (9) |  73775 (12) |  61479 (18) |  52696 (19) |  46109 (21) |       40986 |
 7                GROEN :  90370 (10) |  45185 (22) |       30123 |       22592 |       18074 |       15061 |       12910 |       11296 |       10041 |
 8                 CD&V :  125894 (5) |  62947 (15) |  41964 (24) |       31473 |       25178 |       20982 |       17984 |       15736 |       13988 |
 9               Voor U*:        8639 |        4319 |        2879 |        2159 |        1727 |        1439 |        1234 |        1079 |         959 |
10           DierAnimal*:       10341 |        5170 |        3447 |        2585 |        2068 |        1723 |        1477 |        1292 |        1149 |
11        Partij BLANCO*:        7221 |        3610 |        2407 |        1805 |        1444 |        1203 |        1031 |         902 |         802 |
12        BELG.UNIE-BUB*:        1686 |         843 |         562 |         421 |         337 |         281 |         240 |         210 |         187 |

* failed threshold

 seat            winner/round     nvotes,  score,  voteDiff, 
 ( 1)                  N-VA / 1,  368877, 368877, 
 ( 2)         VLAAMS BELANG / 1,  249826, 249826,  119051,
 ( 3)                  N-VA / 2,  368877, 184438,   65388,
 ( 4)               Vooruit / 1,  127973, 127973,   56465,
 ( 5)                  CD&V / 1,  125894, 125894,    2079,
 ( 6)                  PVDA / 1,  125257, 125257,     637,
 ( 7)         VLAAMS BELANG / 2,  249826, 124913,     344,
 ( 8)                  N-VA / 3,  368877, 122959,    1954,
 ( 9)                  N-VA / 4,  368877,  92219,   30740,
 (10)                 GROEN / 1,   90370,  90370,    1849,
 (11)         VLAAMS BELANG / 3,  249826,  83275,    7095,
 (12)                  N-VA / 5,  368877,  73775,    9500,
 (13)              open vld / 1,   70890,  70890,    2885,
 (14)               Vooruit / 2,  127973,  63986,    6904,
 (15)                  CD&V / 2,  125894,  62947,    1039,
 (16)                  PVDA / 2,  125257,  62628,     319,
 (17)         VLAAMS BELANG / 4,  249826,  62456,     172,
 (18)                  N-VA / 6,  368877,  61479,     977,
 (19)                  N-VA / 7,  368877,  52696,    8783,
 (20)         VLAAMS BELANG / 5,  249826,  49965,    2731,
 (21)                  N-VA / 8,  368877,  46109,    3856,
 (22)                 GROEN / 2,   90370,  45185,     924,
 (23)               Vooruit / 3,  127973,  42657,    2528,
 (24)                  CD&V / 3,  125894,  41964,     693,

Contested            loser/round   nvotes,  score, voteDiff,  noerror, nomargin, estSamples, actSamples,   risk, assertion
 (24)                   PVDA/ 3,  125257,  41752,      212, 0.500129,   0.0003,     12074,     1000,    0.7802, winner CD&V/3 loser PVDA/3
 (23)                                                  905, 0.500550,   0.0011,      2831,     1000,    0.3469, winner Vooruit/3 loser PVDA/3
 (24)          VLAAMS BELANG/ 6,  249826,  41637,      327, 0.500265,   0.0005,      5880,     1000,    0.6006, winner CD&V/3 loser VLAAMS BELANG/6
 (23)                                                 1020, 0.500827,   0.0017,      1884,     1000,    0.2037, winner Vooruit/3 loser VLAAMS BELANG/6
 (24)                   N-VA/ 9,  368877,  40986,      978, 0.500892,   0.0018,      1746,     1000,    0.1796, winner CD&V/3 loser N-VA/9
 (23)                                                 1671, 0.501526,   0.0031,      1022,     1000,    0.0531, winner Vooruit/3 loser N-VA/9
````

|                party   | min | reported | max |
|------------------------|-----|----------|-----|
|                  N-VA  |  8  |     8    |  9  |
|         VLAAMS BELANG  |  5  |     5    |  6  |
|               Vooruit  |  2  |     3    |  3  |
|                  CD&V  |  2  |     3    |  3  |
|                  PVDA  |  2  |     2    |  3  |
|                 GROEN  |  2  |     2    |  2  |
|              open vld  |  1  |     1    |  1  |
|           Volt Europa  |  0  |     0    |  0  |
|                Voor U  |  0  |     0    |  0  |
|            DierAnimal  |  0  |     0    |  0  |
|         Partij BLANCO  |  0  |     0    |  0  |
|         BELG.UNIE-BUB  |  0  |     0    |  0  |

### Threshold assertion doesnt meet risk limit

````

DHondtContest 'Hainaut' (5) DHONDT voteForN=1 votes={2=213501, 1=192759, 3=114559, 4=103339, 7=36750, 11=22039, 6=15189, 5=14184, 12=13224, 9=7201, 10=4680, 8=2426} undervotes=79718, voteForN=1
   winners=[2, 1, 3, 4] Nc=819569 Nphantoms=0 Nu=79718 sumVotes=739851
   nseats=17 winners={2=6, 1=5, 3=3, 4=3} belowMin=[5, 6, 7, 8, 9, 10, 11, 12] threshold=0.05 minVotes=36993
   votesForWinner=36750 pct=4.9672 diff=243 votes Npop=819569 dilutedMargin=0.0312% reportedMargin=0.0312% recountMargin=0.0328% 


candidate          Round:           1 |           2 |           3 |           4 |           5 |           6 |           7 |
 1                   MR :  192759 (2) |   96379 (6) |   64253 (8) |  48189 (12) |  38551 (14) |       32126 |       27537 |
 2                   PS :  213501 (1) |  106750 (4) |   71167 (7) |  53375 (10) |  42700 (13) |  35583 (16) |       30500 |
 3          LES ENGAGÉS :  114559 (3) |   57279 (9) |  38186 (15) |       28639 |       22911 |       19093 |       16365 |
 4                  PTB :  103339 (5) |  51669 (11) |  34446 (17) |       25834 |       20667 |       17223 |       14762 |
 5                 N-VA*:       14184 |        7092 |        4728 |        3546 |        2836 |        2364 |        2026 |
 6                 DéFI*:       15189 |        7594 |        5063 |        3797 |        3037 |        2531 |        2169 |
 7                ECOLO*:       36750 |       18375 |       12250 |        9187 |        7350 |        6125 |        5250 |
 8        BELG.UNIE-BUB*:        2426 |        1213 |         808 |         606 |         485 |         404 |         346 |
 9    COLLECTIF CITOYEN*:        7201 |        3600 |        2400 |        1800 |        1440 |        1200 |        1028 |
10       LUTTE OUVRIERE*:        4680 |        2340 |        1560 |        1170 |         936 |         780 |         668 |
11            CHEZ NOUS*:       22039 |       11019 |        7346 |        5509 |        4407 |        3673 |        3148 |
12               BLANCO*:       13224 |        6612 |        4408 |        3306 |        2644 |        2204 |        1889 |

* failed threshold

 seat            winner/round     nvotes,  score,  voteDiff, 
 ( 1)                    PS / 1,  213501, 213501, 
 ( 2)                    MR / 1,  192759, 192759,   20742,
 ( 3)           LES ENGAGÉS / 1,  114559, 114559,   78200,
 ( 4)                    PS / 2,  213501, 106750,    7809,
 ( 5)                   PTB / 1,  103339, 103339,    3411,
 ( 6)                    MR / 2,  192759,  96379,    6960,
 ( 7)                    PS / 3,  213501,  71167,   25212,
 ( 8)                    MR / 3,  192759,  64253,    6914,
 ( 9)           LES ENGAGÉS / 2,  114559,  57279,    6974,
 (10)                    PS / 4,  213501,  53375,    3904,
 (11)                   PTB / 2,  103339,  51669,    1706,
 (12)                    MR / 4,  192759,  48189,    3480,
 (13)                    PS / 5,  213501,  42700,    5489,
 (14)                    MR / 5,  192759,  38551,    4149,
 (15)           LES ENGAGÉS / 3,  114559,  38186,     365,
 (16)                    PS / 6,  213501,  35583,    2603,
 (17)                   PTB / 3,  103339,  34446,    1137,


------------------------------------------------------------------------------
Thresholds             marginInVotes, nomargin, estSamples, actSamples,   risk
BelowThreshold for 'ECOLO':      243, 0.000296,     10517,     1000,    0.7520,

 seat            winner/round     nvotes,  score,  voteDiff, 
 ( 1)                    PS / 1,  213501, 213501, 
 ( 2)                    MR / 1,  192759, 192759,   20742,
 ( 3)           LES ENGAGÉS / 1,  114559, 114559,   78200,
 ( 4)                    PS / 2,  213501, 106750,    7809,
 ( 5)                   PTB / 1,  103339, 103339,    3411,
 ( 6)                    MR / 2,  192759,  96379,    6960,
 ( 7)                    PS / 3,  213501,  71167,   25212,
 ( 8)                    MR / 3,  192759,  64253,    6914,
 ( 9)           LES ENGAGÉS / 2,  114559,  57279,    6974,
 (10)                    PS / 4,  213501,  53375,    3904,
 (11)                   PTB / 2,  103339,  51669,    1706,
 (12)                    MR / 4,  192759,  48189,    3480,
 (13)                    PS / 5,  213501,  42700,    5489,
 (14)                    MR / 5,  192759,  38551,    4149,
 (15)           LES ENGAGÉS / 3,  114559,  38186,     365,
 (16)                 ECOLO / 1,   36750,  36750,    1436,
 (17)                    PS / 6,  213501,  35583,    1167,

Contested          loser/round  nvotes,  score, voteDiff,  noerror, nomargin, estSamples, estRisk, assertion
 (16)                  PTB/ 3,  103339,  34446,     2304, 0.501056,   0.0021,      1475,   0.1312,    winner ECOLO/1 loser PTB/3
 (17)                                               1137, 0.501391,   0.0028,      1121,   0.0689,    winner PS/6 loser PTB/3

````

|                party   | min | reported | max |
|------------------------|-----|----------|-----|
|                    PS  |  5  |     6    |  6  |
|                    MR  |  5  |     5    |  5  |
|           LES ENGAGÉS  |  3  |     3    |  3  |
|                   PTB  |  2  |     3    |  3  |
|                  N-VA  |  0  |     0    |  0  |
|                  DéFI  |  0  |     0    |  0  |
|                 ECOLO  |  0  |     0    |  1  |
|         BELG.UNIE-BUB  |  0  |     0    |  0  |
|     COLLECTIF CITOYEN  |  0  |     0    |  0  |
|        LUTTE OUVRIERE  |  0  |     0    |  0  |
|             CHEZ NOUS  |  0  |     0    |  0  |
|                BLANCO  |  0  |     0    |  0  |

### Dhondt and Threshold assertion dont meet risk limit


````

DHondtContest 'Bruxelles' (2) DHONDT voteForN=1 votes={2=120155, 3=96516, 7=86927, 10=58645, 5=49425, 9=34143, 12=24826, 8=14472, 1=12754, 13=6579, 14=3287, 4=3032, 17=1872, 6=1688, 15=1604, 11=1534, 16=1467} undervotes=31588, voteForN=1
   winners=[2, 3, 7, 10, 5, 9] Nc=550514 Nphantoms=0 Nu=31588 sumVotes=518926
   nseats=16 winners={2=4, 3=4, 7=3, 10=2, 5=2, 9=1} belowMin=[1, 4, 6, 8, 11, 12, 13, 14, 15, 16, 17] threshold=0.05 minVotes=25947
   fw=24129.0 fl=24031.0 fw-fl=98 Npop=550514 dilutedMargin=0.0890% reportedMargin=0.0890% recountMargin=0.4062% 


candidate          Round:           1 |           2 |           3 |           4 |           5 |
 1        VLAAMS BELANG*:       12754 |        6377 |        4251 |        3188 |        2550 |
 2                   MR :  120155 (1) |   60077 (4) |   40051 (9) |  30038 (12) |       24031 |
 3                   PS :   96516 (2) |   48258 (7) |  32172 (11) |  24129 (16) |       19303 |
 4          Volt Europa*:        3032 |        1516 |        1010 |         758 |         606 |
 5          LES ENGAGÉS :   49425 (6) |  24712 (15) |       16475 |       12356 |        9885 |
 6                Agora*:        1688 |         844 |         562 |         422 |         337 |
 7             PTB-PVDA :   86927 (3) |   43463 (8) |  28975 (14) |       21731 |       17385 |
 8                 N-VA*:       14472 |        7236 |        4824 |        3618 |        2894 |
 9                 DéFI :  34143 (10) |       17071 |       11381 |        8535 |        6828 |
10                ECOLO :   58645 (5) |  29322 (13) |       19548 |       14661 |       11729 |
11   Voor U / Pour Vous*:        1534 |         767 |         511 |         383 |         306 |
12    Team Fouad Ahidar*:       24826 |       12413 |        8275 |        6206 |        4965 |
13    COLLECTIF CITOYEN*:        6579 |        3289 |        2193 |        1644 |        1315 |
14       Parti.j BLANCO*:        3287 |        1643 |        1095 |         821 |         657 |
15        BELG.UNIE-BUB*:        1604 |         802 |         534 |         401 |         320 |
16               l'Unie*:        1467 |         733 |         489 |         366 |         293 |
17       LUTTE OUVRIERE*:        1872 |         936 |         624 |         468 |         374 |

* failed threshold

 seat            winner/round     nvotes,  score,  voteDiff, 
 ( 1)                    MR / 1,  120155, 120155, 
 ( 2)                    PS / 1,   96516,  96516,   23639,
 ( 3)              PTB-PVDA / 1,   86927,  86927,    9589,
 ( 4)                    MR / 2,  120155,  60077,   26850,
 ( 5)                 ECOLO / 1,   58645,  58645,    1432,
 ( 6)           LES ENGAGÉS / 1,   49425,  49425,    9220,
 ( 7)                    PS / 2,   96516,  48258,    1167,
 ( 8)              PTB-PVDA / 2,   86927,  43463,    4795,
 ( 9)                    MR / 3,  120155,  40051,    3412,
 (10)                  DéFI / 1,   34143,  34143,    5908,
 (11)                    PS / 3,   96516,  32172,    1971,
 (12)                    MR / 4,  120155,  30038,    2134,
 (13)                 ECOLO / 2,   58645,  29322,     716,
 (14)              PTB-PVDA / 3,   86927,  28975,     347,
 (15)           LES ENGAGÉS / 2,   49425,  24712,    4263,
 (16)                    PS / 4,   96516,  24129,     583,

Contested            loser/round   nvotes,  score, voteDiff,  noerror, nomargin, estSamples, actSamples,   risk, assertion
 (16)                     MR/ 5,  120155,  24031,       98, 0.500198,   0.0004,      7867,     1000,    0.6831, winner PS/4 loser MR/5
 (15)                                                  682, 0.500886,   0.0018,      1759,     1000,    0.1817, winner LES ENGAGÉS/2 loser MR/5

------------------------------------------------------------------------------
Thresholds                        marginInVotes, nomargin, estSamples, actSamples,   risk
BelowThreshold for 'Team Fouad Ahidar':     1120, 0.002039,      1528,     1000,    0.1405,

 seat            winner/round     nvotes,  score,  voteDiff, 
 ( 1)                    MR / 1,  120155, 120155, 
 ( 2)                    PS / 1,   96516,  96516,   23639,
 ( 3)              PTB-PVDA / 1,   86927,  86927,    9589,
 ( 4)                    MR / 2,  120155,  60077,   26850,
 ( 5)                 ECOLO / 1,   58645,  58645,    1432,
 ( 6)           LES ENGAGÉS / 1,   49425,  49425,    9220,
 ( 7)                    PS / 2,   96516,  48258,    1167,
 ( 8)              PTB-PVDA / 2,   86927,  43463,    4795,
 ( 9)                    MR / 3,  120155,  40051,    3412,
 (10)                  DéFI / 1,   34143,  34143,    5908,
 (11)                    PS / 3,   96516,  32172,    1971,
 (12)                    MR / 4,  120155,  30038,    2134,
 (13)                 ECOLO / 2,   58645,  29322,     716,
 (14)              PTB-PVDA / 3,   86927,  28975,     347,
 (15)     Team Fouad Ahidar / 1,   24826,  24826,    4149,
 (16)           LES ENGAGÉS / 2,   49425,  24712,     114,

Contested          loser/round  nvotes,  score, voteDiff,  noerror, nomargin, estSamples, estRisk, assertion
 (15)                   PS/ 4,   96516,  24129,      697, 0.500507,   0.0010,      3072,   0.3771,    winner Team Fouad Ahidar/1 loser PS/4
 (16)                                                584, 0.500708,   0.0014,      2201,   0.2564,    winner LES ENGAGÉS/2 loser PS/4
 (15)                   MR/ 5,  120155,  24031,      795, 0.500602,   0.0012,      2585,   0.3138,    winner Team Fouad Ahidar/1 loser MR/5
 (16)                                                682, 0.500886,   0.0018,      1759,   0.1820,    winner LES ENGAGÉS/2 loser MR/5
````

|                party   | min | reported | max |
|------------------------|-----|----------|-----|
|                    MR  |  4  |     4    |  5  |
|                    PS  |  3  |     4    |  4  |
|              PTB-PVDA  |  3  |     3    |  3  |
|           LES ENGAGÉS  |  1  |     2    |  2  |
|                 ECOLO  |  2  |     2    |  2  |
|                  DéFI  |  1  |     1    |  1  |
|         VLAAMS BELANG  |  0  |     0    |  0  |
|           Volt Europa  |  0  |     0    |  0  |
|                 Agora  |  0  |     0    |  0  |
|                  N-VA  |  0  |     0    |  0  |
|    Voor U / Pour Vous  |  0  |     0    |  0  |
|     Team Fouad Ahidar  |  0  |     0    |  1  |
|     COLLECTIF CITOYEN  |  0  |     0    |  0  |
|        Parti.j BLANCO  |  0  |     0    |  0  |
|         BELG.UNIE-BUB  |  0  |     0    |  0  |
|                l'Unie  |  0  |     0    |  0  |
|        LUTTE OUVRIERE  |  0  |     0    |  0  |


### Sum of candidate ranges across all contests

|                party   | min | reported | max |
|------------------------|-----|----------|-----|
|                  N-VA  | 24  |    24    | 25  |
|         VLAAMS BELANG  | 20  |    20    | 21  |
|                    MR  | 20  |    20    | 21  |
|                    PS  | 14  |    16    | 17  |
|           LES ENGAGÉS  | 12  |    14    | 14  |
|               Vooruit  | 11  |    13    | 13  |
|                  CD&V  | 10  |    11    | 12  |
|              open vld  |  7  |     7    |  7  |
|                  PVDA  |  6  |     6    |  7  |
|                 GROEN  |  6  |     6    |  6  |
|                   PTB  |  5  |     6    |  6  |
|                 ECOLO  |  3  |     3    |  4  |
|              PTB-PVDA  |  3  |     3    |  3  |
|                  DéFI  |  1  |     1    |  1  |
|     Team Fouad Ahidar  |  0  |     0    |  1  |
|           Volt Europa  |  0  |     0    |  0  |
|                Voor U  |  0  |     0    |  0  |
|            DierAnimal  |  0  |     0    |  0  |
|         Partij BLANCO  |  0  |     0    |  0  |
|         BELG.UNIE-BUB  |  0  |     0    |  0  |
|                l'Unie  |  0  |     0    |  0  |
|                 Agora  |  0  |     0    |  0  |
|     COLLECTIF CITOYEN  |  0  |     0    |  0  |
|             CHEZ NOUS  |  0  |     0    |  0  |
|                   RMC  |  0  |     0    |  0  |
|                BLANCO  |  0  |     0    |  0  |
|    Voor U / Pour Vous  |  0  |     0    |  0  |
|        Parti.j BLANCO  |  0  |     0    |  0  |
|        LUTTE OUVRIERE  |  0  |     0    |  0  |
|    GV-GEZOND VERSTAND  |  0  |     0    |  0  |


## Notes From Bounding paper 04/12/2026

"Ideas for bounding sample sizes for Belgian RLAs April 12, 2026"

### 2.1 Context of the Audit

Approximately half of the Belgian voters vote on hand-marked and hand-counted
paper ballots. Voting happens on a Sunday morning, and counting offices op-
erate on Sunday afternoon and sometimes evening. Each counting office has 6
members, who may tally up to 2400 ballots. The ballots are sorted by lists,
then counted, then placed into sealed envelopes. All the ballots in any single
envelope must express votes for the same list.

(So, up to 2400 votes in an envelope that must be kept in order. Hainut example shows typical largest candidate has 30%, so 720)

The other half of the voters vote on ballot marking devices that print paper
ballots, which are scanned, and counted from the scanned electronic records.

("The audit would be based on an evolution of the current BMDs" which is what?)

### 2.2 Election manifest

The manifest typically contains other book keeping information, including
a list of boxes and envelopes and the reported content of each envelope.

In the context of hand-counted ballots, each envelope contains ballots supporting a
single list, and invalid ballots are also gathered in one envelope. In the context
of e-voting, each envelope contains ballots of which an interpretation has been
reported during the scanning process.

### 2.3 Auditing D’Hondt Elections
````
    * S = number of available seats 
    * d(i) divisor for ith index d(i) = i for dhondt
    * P is set of Parties, p ∈ P, ||p|| = number of candidates fielded by p
    * s :: P -> N ∪ {0} allocation os seats to parties
      s(p) is both the number of seats allocated to party p and (if non-zero)
      the index of the candidate in list p who is reported to get seated with the
      lowest quotient. (what is "lowest quotient"?) 
      = LW(p) "last winner"
    * FL(p)   = s(p) + 1    if s(p) < ||p||, otherwise undefined
      the index of the candidate in p who has the highest quotient
      of those who do not get a seat, if it exists
      "first loser"
    * T(p) = total number of valid votes for p
    * T(p,i) = quotient of the i-th ranked candidate of a list p = T(p) / d(i)
    
````

2.3.2

DH(A, B) = D’Hondt assertion that the last winner of list A won over the first loser of list B
        = T(A,LW(A)) > T(B,FL(B))

proto assorter g_AB(b) = b(A) / s(A) - b(B) / FL(B)
    where b(p) is set to 1 if the ballot b is a vote for p, and set to 0 otherwise

assorter h_AB(b) = b(A) * FL(B) / 2 * s(A)  + (1 - b(B)) / 2 
                 = 0 for a vote for B, = 1/2 for a vote for other, = FL(B) / 2 * s(A) vote for A

### 2.4 Adding a Threshold

 T(V) = number of valid votes
theta = T(V) * .05

above threshold AT(p) = T(p) > theta
below threshold BT(p) = T(p) < theta

#### AboveThreshold

ta_(b) = b(A)/2*theta  + b_INV / 2  (what is b_INV ?? probably typo?)

general formula once you have g is

    h(b) = c * g(b) + 1/2
       c = -1/(2a)
       a = lower bound of g

    for AT, g =  (1 - t) if vote for A else -t, so a = -t, c = 1/2t
    then h(b) =  g(b)/2t + 1/2

Formulate using "b(p) = 1 if the ballot b is a vote for p, and set to 0 otherwise"

    g(b) = b(p) - t
    h(b) = (b(p) - t)/2t + 1/2
        = b(p)/2t - t/2t + 1/2
        = b(p)/2t

(paper has ta_(b) = b(A)/2*theta  + b_INV / 2  (what is b_INV ?? probably typo))

other formulation with c = 1 (not used)
    If a >= −1/2, simply setting c = 1 produces an assorter: we have h ⩾ 0, and h̄ > 1/2 iff ḡ > 0. (option 1)

    h(b) = c * g(b) + 1/2   
    h(b) = g(b) + 1/2   
    h(b) = b(p) - t + 1/2

#### BelowThreshold

    g(b) = if (vote == candId) t-1 else t   
    a = lowerbound = t-1 
    c = -1/(2a) = -1/(2*(t-1))

Formulate using using "b(p) = 1 if the ballot b is a vote for p, and set to 0 otherwise"

    g(b) = -b(p) + t  

    h(b) = c * g(b) + 1/2
         = -1/(2*(t-1)) * g(b) + 1/2
         = -g(b)/(2*(t-1)) + 1/2
         = -g(b)/(2*(t-1)) + (t-1)/2(t-1)
         = (-g(b) + (t-1))/(2(t-1))
         = (b(p) - t  + t-1)/(2(t-1))
         = (b(p)-1) / (2(t-1))


### 3.1 Check margins

"estimate (the difficulty) using the reported margins".

Using the smallest margin wont give you the highest difficulty solving, one needs to use noerror, which takes into account the upper limit of the assorter, which != 1 for all the assorters (DH, AT, BT).

    noerror = 1.0 / (2.0 - assorterMargin / assorter.upperBound())

when you get a noerror sample, your payoff is

    payoff_noerror = (1 + λ * (noerror − 1/2))  ;  (taking µ_i approximately 1/2)

where 
* taking µ_i approximately 1/2
* can take λ as constant
* noerror always > 1/2

the larger noerror is, the larger the payoff is.

You need N of these to get over the risk limit

    payoff_noerror^nsamples > 1/risk

    nsamples = ln(1/risk / ln(payoff_noerror))

The larger the payoff is, the smaller N is.

This is "Betting martingale" specific, but I cant imagine how it could be different for another risk function.

This quantity is nice to work with:

    noerrorMargin = 2.0 * noerror - 1.0  
                  = 2 * (noerror - 1/2) 

    where noerror = 1.0 / (2.0 - margin / upper)

then the payoff can be expressed as 

    payoff_noerror = (1 + λ * (noerror − 1/2))  
                   = (1 + λ * noerrorMargin/2)



### 3.2 Compare expectations using ALPHA martingales.

Presume this subsumes BettingMarts.


## Notes From Proportional paper 02/05/2026

Notes From [Assertion-Based Approaches to Auditing Complex Elections, with Application to Party-List Proportional Elections](http://arxiv.org/abs/2107.11903v2)

### Section 3. Creating assorters from assertions

In this section we show how to transform generic linear assertions, i.e. inequalities of the form

    Sum_b∈L { Sum_e∈E {ae * be } } > c

into canonical assertions using assorters as required by SHANGRLA. There are three steps:

1. Construct a set of linear assertions that imply the correctness of the outcome.
   (Constructing such a set is outside the scope of this paper; we suspect there is no
   general method. Moreover, there may be social choice functions for which there is no such set)
2. Determine a ‘proto-assorter’ based on this assertion.
3. Construct an assorter from the proto-assorter via an affine transformation.

We work with social choice functions where each valid ballot can contribute a non-negative (zero or more)
number of ‘votes’ or ‘points’ to various tallies (we refer to these as votes henceforth).

Let the various tallies of interest be T1, T2, ..., Tm for m different entities e∈E.
These represent the total count of the votes across all valid ballots.

Step 1. A linear assertion is a statement of the form

    a1*T1 + a2*T2 + ... + am*Tm > 0  for some constants a1 ,... , am 

For example, a pairwise majority assertion is usually written as pA > pB ,
stating that candidate A got a larger proportion of the valid votes than candidate B.

````
pA > pB
TA/TL > TB/TL
TA − TB > 0
````

a1 = 1, a2 = -1

### Above Assertion

Another example is a super/sub-majority assertion, pA > t, for some threshold t.

````
pA > t
TA/TL > t
TA > t * TL
TA − t * TL > 0
TA − t * Sum(Ti) > 0
(1-t) TA - t * {Ti, i != A} > 0

So the linear coefficients are:

aA = (1-t), ai = -t for i != A.

g(b) = a1 b1 + a2 b2 + · · · + am bm
     = (1-t)*bA + t*sum(bi, i != A)

so if vote is for A, g = (1-t)
   if vote for not B, r = -t
   else 0

lower bound a = -t
upper bound u = (1-t)
c = -1/2a
h = (g(b) - a)/-2a

g lower bound is a = -t
g upper bound is u = (1-t)
c = -1/2a
h = g * c + 1/2 = (g(b) - a)/-2a = (g(b) + t)/2t
h upper bound is h(g upper) = h(1 - t) = (1 - t + t ) /  2t = 1/2t
````

### Below Assertion

Another example is an under threshold assertion, pA < t, for some threshold t.

````
pA < t
TA/TL < t
TA < t * TL
0 < t * TL - TA
0 < t * Sum(Ti) - TA
t * Sum(Ti) - TA > 0
(t-1) TA + t * {Ti, i != A} > 0

So the linear coefficients are:

  aA = (t-1), ai = t for i != A.

so if vote is for A, g = (t-1)
   if vote for not A, r = t
   else 0

lower bound a = (t-1)
upper bound u = t
c = -1/2a = 1/2(1-t)
h = c * g + 1/2 = g/2(1-t) + 1/2 = (g + 1 - t)/2(1-t)
h upper bound is h(g upper) = (t + 1 - t)/2(1-t) = 1/2(1-t)

````

Step 2. For the given linear assertion, we define the following function on ballots, which we call a proto-assorter :

        g(b) = a1*b1 + a2*b2 + · · · + am*bm

where b is a given ballot, and b1 , b2 , . . . , bm are the votes contributed by that ballot to the tallies T1 , T2 , . . . , Tm respectively.

Summing this function across all ballots, Sum_b {g(b)}, gives the left-hand side of the linear assertion.
Thus, the linear assertion is true iff Sum_b {g(b)} > 0. 
The same property holds for the average across ballots, ḡ = Sum_b {g(b)}/L, L = total numver of ballots. The linear assertion is true iff ḡ > 0.

Step 3. To obtain an assorter in canonical form, we apply an affine transformation to g such that it never takes negative values 
and also so that comparing its average value to 1/2 determines the truth of the assertion. One such transformation is

    h(b) = c · g(b) + 1/2      (eq 1)

for some constant c. There are many ways to choose c. Here are two possibilities:

First, we determine a lower bound for the proto-assorter, a value _a_ such that _g(b) >= a_ for all b.
Note that a < 0 in all interesting cases: if not, the assertion would be trivially true (ḡ > 0) or trivially false (ḡ ≡ 0, with aj = 0 for all j).

If a >= −1/2, simply setting c = 1 produces an assorter: we have h ⩾ 0, and h̄ > 1/2 iff ḡ > 0. (option 1)

Otherwise, we can choose c = −1/2a (option 2). From eq 1, this gives:
   
    h(b) = -1/2a * g(b)  + 1/2
    h(b) = (g(b) − a) / −2a     (eq 2)

To see that h(b) is an assorter, first note that h(b) ⩾ 0 since the numerator is always non-negative and the denominator is positive. 
Also, the sum and mean across all ballots are, respectively:

    Sum_b {h(b)} = -1/2a * Sum_b {g(b)} + L/2
    h̄ = -1/2a ḡ + 1/2          (eq 3)

Therefore, h̄ > 1/2 iff ḡ > 0.

### Section 5.1 highest averages

A highest averages method is parameterized by a set of divisors d(1), d(2), . . . d(S) where S is the number of seats.
The divisors for D’Hondt are d(i) = i. Sainte-Laguë has divisors d(i) = 2i − 1.

Define

    fe,s = Te/d(s) for entity e and seat s.

### Section 5.2 Simple D’Hondt: Party-only voting

In the simplest form of highest averages methods, seats are allocated to each
entity (party) based on individual entity tallies. Let We be the number of seats
won and Le the number of the first seat lost by entity e. That is:

    We = max{s : (e, s) ∈ W}; ⊥ if e has no winners. this is e's lowest winner.
    Le = min{s : (e, s) !∈ W}; ⊥ if e won all the seats. this is e's highest loser.

The inequalities that define the winners are, for all parties A with at least
one winner, for all parties B (different from A) with at least one loser, as follows:

    fA,WA > fB,LB    A’s lowest winner beat party B’s highest loser
    TA/d(WA) > TB/d(LB)
    TA/d(WA) - TB/d(LB) > 0

From this, we define the proto-assorter for any ballot b as 

    g_AB(b) = 1/d(WA) if b is a vote for A
            = -1/d(WB) if b is a vote for B
            = 0 otherwisa

    or equivilantly, g_AB(b) = bA/d(WA) - bB/d(WB)

g lower bound is -1/d(WB) = -1/first (lowest winner)
g upper bound is 1/d(WA)  = 1/last   (highest loser)
c = -1.0 / (2 * lower) = first/2
h upper bound is h(g upper) = h(1/last) * c + 1/2 = (1/last) * first/2 + 1/2 = (first/last+1)/2

first and last both range from 1 to nseats, so 
    min upper is (1/nseats + 1)/2 which is between 1/2 and 1
    max upper is (nseats + 1)/2 which is >= 1

### Section 5.3  More complex methods: Multi-candidate voting

Like some Hamiltonian elections, many highest averages elections also allow
voters to select individual candidates. A party’s tally is the total of its candidates’
votes. Then, within each party, the won seats are allocated to the candidates with
the highest individual tallies. The main entities are still parties, allocated seats
according to Equation 4, but the assorter must be generalised to allow one ballot
to contain multiple votes for various candidates.

The proto-assorter for entities (parties) A != B s.t. WA !=⊥, and LB !=⊥, is
very similar to the single-party case, but votes for each party (bA and bB) count
the total, over all that entity’s candidates, and may be larger than one.

    gA,B(b) := bA/d(WA ) − bB/d(LB )

The lower bound is −m/d(LB ), again substituting in to Equation 2 gives

    hA,B(b) = (bA * d(LB)/d(WA) − bB + m ) / 2m




