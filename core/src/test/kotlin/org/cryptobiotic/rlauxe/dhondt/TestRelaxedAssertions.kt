package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.dhondt.CandSeatRanges.Companion.showSeatRanges
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.testdataDir
import kotlin.test.Test

class TestRelaxedAssertions {
    val auditdir = "$testdataDir/cases/belgium/2024limited/"
    val auditRecord = AuditRecord.read(auditdir)!!
    val contests = auditRecord.contests
    val lastRound = auditRecord.rounds.last()
    val config = auditRecord.config
    val sampleLimit = config.creation.riskMeasuringSampleLimit

    @Test
    fun testAssorters() {
        print( showSeatRanges(auditdir))
    }

/*
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

nseats=150 ncands=30
*/

    @Test
    fun testShowRelaxedAssertions() {
        contests.forEach{ contestUA ->
            val contestRound = lastRound.contestRounds.find { it.contestUA.id == contestUA.id }
            if (contestRound != null) {
                val result = (contestUA.contest as DHondtContest).showRelaxedAssertions(contestRound)
                println(result)
                println("=======================================================================================================")
            }
        }
    }

    @Test
    fun testContestedSeats() {
        var totalCount = 0
        contests.forEach{ contestUA ->
            val contestRound = lastRound.contestRounds.find { it.contestUA.id == contestUA.id }
            if (contestRound != null) {
                val count = (contestUA.contest as DHondtContest).countContestedSeats(contestRound)
                totalCount += count
                val result = (contestUA.contest as DHondtContest).showContestedSeats(contestRound)
                print(result)
            }
        }
        println("\ntotalCount = $totalCount")
    }

    @Test
    fun problem() {
        val contestRound = lastRound.contestRounds.find { it.contestUA.id == 5 } // Hainut
        if (contestRound != null) {
            val count = (contestRound.contestUA.contest as DHondtContest).countContestedSeats(contestRound)
            println("count = $count")
            val result = (contestRound.contestUA.contest as DHondtContest).showContestedSeats(contestRound)
            println(result)
            println("=======================================================================================================")
        }
    }

}

/*
Anvers (1) Nc=1235587 Nphantoms=0 votes={6=368877, 1=249826, 5=127973, 8=125894, 4=125257, 7=90370, 2=70890, 10=10341, 9=8639, 11=7221, 3=4213, 12=1686} undervotes=44400, voteForN=1
reported winners
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

Contested           loser/round   nvotes,  score, voteDiff,  noerror, nomargin, estSamples, actSamples, estRisk, assertion
 (24)                   PVDA/ 3,  125257,  41752,      212, 0.500129,   0.0003,     12074,     1000,    0.7802, winner CD&V/3 loser PVDA/3
 (23)                                                  905, 0.500550,   0.0011,      2831,     1000,    0.3469, winner Vooruit/3 loser PVDA/3
 (24)          VLAAMS BELANG/ 6,  249826,  41637,      327, 0.500265,   0.0005,      5880,     1000,    0.6006, winner CD&V/3 loser VLAAMS BELANG/6
 (23)                                                 1020, 0.500827,   0.0017,      1884,     1000,    0.2037, winner Vooruit/3 loser VLAAMS BELANG/6
 (24)                   N-VA/ 9,  368877,  40986,      978, 0.500892,   0.0018,      1746,     1000,    0.1796, winner CD&V/3 loser N-VA/9
 (23)                                                 1671, 0.501526,   0.0031,      1022,     1000,    0.0531, winner Vooruit/3 loser N-VA/9


|                party   | min | reported | max |
|------------------------|-----|----------|-----|
|                  N-VA  |  8  |     8    |  9  |
|         VLAAMS BELANG  |  5  |     5    |  6  |
|                  PVDA  |  2  |     2    |  3  |
|               Vooruit  |  2  |     3    |  3  |
|                  CD&V  |  2  |     3    |  3  |
|                 GROEN  |  2  |     2    |  2  |
|              open vld  |  1  |     1    |  1  |
|           Volt Europa  |  0  |     0    |  0  |
|                Voor U  |  0  |     0    |  0  |
|            DierAnimal  |  0  |     0    |  0  |
|         Partij BLANCO  |  0  |     0    |  0  |
|         BELG.UNIE-BUB  |  0  |     0    |  0  |

=======================================================================================================
BrabantFlamant (10) Nc=751697 Nphantoms=0 votes={5=182883, 1=119345, 4=98092, 7=93465, 2=83744, 3=57600, 6=57395, 8=9961, 10=6859, 9=4086, 11=3255} undervotes=35012, voteForN=1
reported winners
 seat            winner/round     nvotes,  score,  voteDiff,
 ( 1)                  N-VA / 1,  182883, 182883,
 ( 2)         VLAAMS BELANG / 1,  119345, 119345,   63538,
 ( 3)               Vooruit / 1,   98092,  98092,   21253,
 ( 4)                  CD&V / 1,   93465,  93465,    4627,
 ( 5)                  N-VA / 2,  182883,  91441,    2024,
 ( 6)              open vld / 1,   83744,  83744,    7697,
 ( 7)                  N-VA / 3,  182883,  60961,   22783,
 ( 8)         VLAAMS BELANG / 2,  119345,  59672,    1289,
 ( 9)                  PVDA / 1,   57600,  57600,    2072,
 (10)                 GROEN / 1,   57395,  57395,     205,
 (11)               Vooruit / 2,   98092,  49046,    8349,
 (12)                  CD&V / 2,   93465,  46732,    2314,
 (13)                  N-VA / 4,  182883,  45720,    1012,
 (14)              open vld / 2,   83744,  41872,    3848,
 (15)         VLAAMS BELANG / 3,  119345,  39781,    2091,



|                party   | min | reported | max |
|------------------------|-----|----------|-----|
|                  N-VA  |  4  |     4    |  4  |
|         VLAAMS BELANG  |  3  |     3    |  3  |
|              open vld  |  2  |     2    |  2  |
|               Vooruit  |  2  |     2    |  2  |
|                  CD&V  |  2  |     2    |  2  |
|                  PVDA  |  1  |     1    |  1  |
|                 GROEN  |  1  |     1    |  1  |
|                Voor U  |  0  |     0    |  0  |
|         BELG.UNIE-BUB  |  0  |     0    |  0  |
|         Partij BLANCO  |  0  |     0    |  0  |
|                l'Unie  |  0  |     0    |  0  |

=======================================================================================================
BrabantWallon (11) Nc=270051 Nphantoms=0 votes={1=90486, 3=58077, 2=31741, 8=23587, 5=20221, 7=8750, 6=5753, 13=4830, 9=4781, 11=4659, 14=1003, 10=918, 4=802, 12=664} undervotes=13779, voteForN=1
reported winners
 seat            winner/round     nvotes,  score,  voteDiff,
 ( 1)                    MR / 1,   90486,  90486,
 ( 2)           LES ENGAGÉS / 1,   58077,  58077,   32409,
 ( 3)                    MR / 2,   90486,  45243,   12834,
 ( 4)                    PS / 1,   31741,  31741,   13502,
 ( 5)                    MR / 3,   90486,  30162,    1579,



|                party   | min | reported | max |
|------------------------|-----|----------|-----|
|                    MR  |  3  |     3    |  3  |
|                    PS  |  1  |     1    |  1  |
|           LES ENGAGÉS  |  1  |     1    |  1  |
|                 Agora  |  0  |     0    |  0  |
|                   PTB  |  0  |     0    |  0  |
|                  N-VA  |  0  |     0    |  0  |
|                  DéFI  |  0  |     0    |  0  |
|                 ECOLO  |  0  |     0    |  0  |
|     COLLECTIF CITOYEN  |  0  |     0    |  0  |
|                l'Unie  |  0  |     0    |  0  |
|             CHEZ NOUS  |  0  |     0    |  0  |
|                   RMC  |  0  |     0    |  0  |
|                BLANCO  |  0  |     0    |  0  |
|         BELG.UNIE-BUB  |  0  |     0    |  0  |

=======================================================================================================
Bruxelles (2) Nc=550514 Nphantoms=0 votes={2=120155, 3=96516, 7=86927, 10=58645, 5=49425, 9=34143, 12=24826, 8=14472, 1=12754, 13=6579, 14=3287, 4=3032, 17=1872, 6=1688, 15=1604, 11=1534, 16=1467} undervotes=31588, voteForN=1
reported winners
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

Contested           loser/round   nvotes,  score, voteDiff,  noerror, nomargin, estSamples, actSamples, estRisk, assertion
 (16)                     MR/ 5,  120155,  24031,       98, 0.500198,   0.0004,      7867,     1000,    0.6831, winner PS/4 loser MR/5
 (15)                                                  682, 0.500886,   0.0018,      1759,     1000,    0.1817, winner LES ENGAGÉS/2 loser MR/5

------------------------------------------------------------------------------
Thresholds             marginInVotes, nomargin, estSamples, actSamples,   risk
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

Contested         loser/round     nvotes,  score, voteDiff,  noerror, nomargin, estSamples, estRisk, assertion
 (15)                     PS/ 4,   96516,  24129,      697, 0.500507,   0.0010,      3072,   0.3771,    winner Team Fouad Ahidar/1 loser PS/4
 (16)                                                  584, 0.500708,   0.0014,      2201,   0.2564,    winner LES ENGAGÉS/2 loser PS/4
 (15)                     MR/ 5,  120155,  24031,      795, 0.500602,   0.0012,      2585,   0.3138,    winner Team Fouad Ahidar/1 loser MR/5
 (16)                                                  682, 0.500886,   0.0018,      1759,   0.1820,    winner LES ENGAGÉS/2 loser MR/5


|                party   | min | reported | max |
|------------------------|-----|----------|-----|
|                    MR  |  4  |     4    |  5  |
|                    PS  |  3  |     4    |  4  |
|              PTB-PVDA  |  3  |     3    |  3  |
|           LES ENGAGÉS  |  1  |     2    |  2  |
|                 ECOLO  |  2  |     2    |  2  |
|                  DéFI  |  1  |     1    |  1  |
|     Team Fouad Ahidar  |  0  |     0    |  1  |
|         VLAAMS BELANG  |  0  |     0    |  0  |
|           Volt Europa  |  0  |     0    |  0  |
|                 Agora  |  0  |     0    |  0  |
|                  N-VA  |  0  |     0    |  0  |
|    Voor U / Pour Vous  |  0  |     0    |  0  |
|     COLLECTIF CITOYEN  |  0  |     0    |  0  |
|        Parti.j BLANCO  |  0  |     0    |  0  |
|         BELG.UNIE-BUB  |  0  |     0    |  0  |
|                l'Unie  |  0  |     0    |  0  |
|        LUTTE OUVRIERE  |  0  |     0    |  0  |

=======================================================================================================
FlandreEast (4) Nc=1083369 Nphantoms=0 votes={1=234888, 5=231470, 4=127758, 7=125871, 2=119200, 6=103722, 3=75942, 11=8057, 8=8007, 10=2352, 9=1390} undervotes=44712, voteForN=1
reported winners
 seat            winner/round     nvotes,  score,  voteDiff,
 ( 1)         VLAAMS BELANG / 1,  234888, 234888,
 ( 2)                  N-VA / 1,  231470, 231470,    3418,
 ( 3)               Vooruit / 1,  127758, 127758,  103712,
 ( 4)                  CD&V / 1,  125871, 125871,    1887,
 ( 5)              open vld / 1,  119200, 119200,    6671,
 ( 6)         VLAAMS BELANG / 2,  234888, 117444,    1756,
 ( 7)                  N-VA / 2,  231470, 115735,    1709,
 ( 8)                 GROEN / 1,  103722, 103722,   12013,
 ( 9)         VLAAMS BELANG / 3,  234888,  78296,   25426,
 (10)                  N-VA / 3,  231470,  77156,    1140,
 (11)                  PVDA / 1,   75942,  75942,    1214,
 (12)               Vooruit / 2,  127758,  63879,   12063,
 (13)                  CD&V / 2,  125871,  62935,     944,
 (14)              open vld / 2,  119200,  59600,    3335,
 (15)         VLAAMS BELANG / 4,  234888,  58722,     878,
 (16)                  N-VA / 4,  231470,  57867,     855,
 (17)                 GROEN / 2,  103722,  51861,    6006,
 (18)         VLAAMS BELANG / 5,  234888,  46977,    4884,
 (19)                  N-VA / 5,  231470,  46294,     683,
 (20)               Vooruit / 3,  127758,  42586,    3708,

Contested           loser/round   nvotes,  score, voteDiff,  noerror, nomargin, estSamples, actSamples, estRisk, assertion
 (20)                   CD&V/ 3,  125871,  41957,      629, 0.500436,   0.0009,      3573,     1000,    0.4322, winner Vooruit/3 loser CD&V/3


|                party   | min | reported | max |
|------------------------|-----|----------|-----|
|         VLAAMS BELANG  |  5  |     5    |  5  |
|                  N-VA  |  5  |     5    |  5  |
|               Vooruit  |  2  |     3    |  3  |
|                  CD&V  |  2  |     2    |  3  |
|              open vld  |  2  |     2    |  2  |
|                 GROEN  |  2  |     2    |  2  |
|                  PVDA  |  1  |     1    |  1  |
|                Voor U  |  0  |     0    |  0  |
|         BELG.UNIE-BUB  |  0  |     0    |  0  |
|    GV-GEZOND VERSTAND  |  0  |     0    |  0  |
|         Partij BLANCO  |  0  |     0    |  0  |

=======================================================================================================
FlandreWest (3) Nc=866870 Nphantoms=0 votes={1=202800, 5=192037, 4=137422, 7=121447, 2=65840, 6=45502, 3=44129, 8=9700, 9=8178} undervotes=39815, voteForN=1
reported winners
 seat            winner/round     nvotes,  score,  voteDiff,
 ( 1)         VLAAMS BELANG / 1,  202800, 202800,
 ( 2)                  N-VA / 1,  192037, 192037,   10763,
 ( 3)               Vooruit / 1,  137422, 137422,   54615,
 ( 4)                  CD&V / 1,  121447, 121447,   15975,
 ( 5)         VLAAMS BELANG / 2,  202800, 101400,   20047,
 ( 6)                  N-VA / 2,  192037,  96018,    5382,
 ( 7)               Vooruit / 2,  137422,  68711,   27307,
 ( 8)         VLAAMS BELANG / 3,  202800,  67600,    1111,
 ( 9)              open vld / 1,   65840,  65840,    1760,
 (10)                  N-VA / 3,  192037,  64012,    1828,
 (11)                  CD&V / 2,  121447,  60723,    3289,
 (12)         VLAAMS BELANG / 4,  202800,  50700,   10023,
 (13)                  N-VA / 4,  192037,  48009,    2691,
 (14)               Vooruit / 3,  137422,  45807,    2202,
 (15)                 GROEN / 1,   45502,  45502,     305,
 (16)                  PVDA / 1,   44129,  44129,    1373,



|                party   | min | reported | max |
|------------------------|-----|----------|-----|
|         VLAAMS BELANG  |  4  |     4    |  4  |
|                  N-VA  |  4  |     4    |  4  |
|               Vooruit  |  3  |     3    |  3  |
|                  CD&V  |  2  |     2    |  2  |
|              open vld  |  1  |     1    |  1  |
|                  PVDA  |  1  |     1    |  1  |
|                 GROEN  |  1  |     1    |  1  |
|                Voor U  |  0  |     0    |  0  |
|         Partij BLANCO  |  0  |     0    |  0  |

=======================================================================================================
Hainaut (5) Nc=819569 Nphantoms=0 votes={2=213501, 1=192759, 3=114559, 4=103339, 7=36750, 11=22039, 6=15189, 5=14184, 12=13224, 9=7201, 10=4680, 8=2426} undervotes=79718, voteForN=1
reported winners
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

Contested         loser/round     nvotes,  score, voteDiff,  noerror, nomargin, estSamples, estRisk, assertion
 (16)                    PTB/ 3,  103339,  34446,     2304, 0.501056,   0.0021,      1475,   0.1312,    winner ECOLO/1 loser PTB/3
 (17)                                                 1137, 0.501391,   0.0028,      1121,   0.0689,    winner PS/6 loser PTB/3


|                party   | min | reported | max |
|------------------------|-----|----------|-----|
|                    PS  |  5  |     6    |  6  |
|                    MR  |  5  |     5    |  5  |
|           LES ENGAGÉS  |  3  |     3    |  3  |
|                   PTB  |  2  |     3    |  3  |
|                 ECOLO  |  0  |     0    |  1  |
|                  N-VA  |  0  |     0    |  0  |
|                  DéFI  |  0  |     0    |  0  |
|         BELG.UNIE-BUB  |  0  |     0    |  0  |
|     COLLECTIF CITOYEN  |  0  |     0    |  0  |
|        LUTTE OUVRIERE  |  0  |     0    |  0  |
|             CHEZ NOUS  |  0  |     0    |  0  |
|                BLANCO  |  0  |     0    |  0  |

=======================================================================================================
Limbourg (7) Nc=606094 Nphantoms=0 votes={1=141988, 5=136606, 7=90715, 4=75191, 3=52303, 2=40985, 6=27619, 8=5505, 9=4660, 10=1202} undervotes=29320, voteForN=1
reported winners
 seat            winner/round     nvotes,  score,  voteDiff,
 ( 1)         VLAAMS BELANG / 1,  141988, 141988,
 ( 2)                  N-VA / 1,  136606, 136606,    5382,
 ( 3)                  CD&V / 1,   90715,  90715,   45891,
 ( 4)               Vooruit / 1,   75191,  75191,   15524,
 ( 5)         VLAAMS BELANG / 2,  141988,  70994,    4197,
 ( 6)                  N-VA / 2,  136606,  68303,    2691,
 ( 7)                  PVDA / 1,   52303,  52303,   16000,
 ( 8)         VLAAMS BELANG / 3,  141988,  47329,    4974,
 ( 9)                  N-VA / 3,  136606,  45535,    1794,
 (10)                  CD&V / 2,   90715,  45357,     178,
 (11)              open vld / 1,   40985,  40985,    4372,
 (12)               Vooruit / 2,   75191,  37595,    3390,



|                party   | min | reported | max |
|------------------------|-----|----------|-----|
|         VLAAMS BELANG  |  3  |     3    |  3  |
|                  N-VA  |  3  |     3    |  3  |
|               Vooruit  |  2  |     2    |  2  |
|                  CD&V  |  2  |     2    |  2  |
|              open vld  |  1  |     1    |  1  |
|                  PVDA  |  1  |     1    |  1  |
|                 GROEN  |  0  |     0    |  0  |
|                Voor U  |  0  |     0    |  0  |
|         Partij BLANCO  |  0  |     0    |  0  |
|         BELG.UNIE-BUB  |  0  |     0    |  0  |

=======================================================================================================
Liège (6) Nc=685695 Nphantoms=0 votes={1=179296, 2=137443, 3=103711, 4=91188, 7=49936, 11=21877, 6=13816, 5=10840, 10=10656, 8=8289, 12=3361, 9=1502} undervotes=53780, voteForN=1
reported winners
 seat            winner/round     nvotes,  score,  voteDiff,
 ( 1)                    MR / 1,  179296, 179296,
 ( 2)                    PS / 1,  137443, 137443,   41853,
 ( 3)           LES ENGAGÉS / 1,  103711, 103711,   33732,
 ( 4)                   PTB / 1,   91188,  91188,   12523,
 ( 5)                    MR / 2,  179296,  89648,    1540,
 ( 6)                    PS / 2,  137443,  68721,   20927,
 ( 7)                    MR / 3,  179296,  59765,    8956,
 ( 8)           LES ENGAGÉS / 2,  103711,  51855,    7910,
 ( 9)                 ECOLO / 1,   49936,  49936,    1919,
 (10)                    PS / 3,  137443,  45814,    4122,
 (11)                   PTB / 2,   91188,  45594,     220,
 (12)                    MR / 4,  179296,  44824,     770,
 (13)                    MR / 5,  179296,  35859,    8965,
 (14)           LES ENGAGÉS / 3,  103711,  34570,    1289,

Contested           loser/round   nvotes,  score, voteDiff,  noerror, nomargin, estSamples, actSamples, estRisk, assertion
 (14)                     PS/ 4,  137443,  34360,      210, 0.500262,   0.0005,      5939,     1000,    0.6036, winner LES ENGAGÉS/3 loser PS/4


|                party   | min | reported | max |
|------------------------|-----|----------|-----|
|                    MR  |  5  |     5    |  5  |
|                    PS  |  3  |     3    |  4  |
|           LES ENGAGÉS  |  2  |     3    |  3  |
|                   PTB  |  2  |     2    |  2  |
|                 ECOLO  |  1  |     1    |  1  |
|                  N-VA  |  0  |     0    |  0  |
|                  DéFI  |  0  |     0    |  0  |
|     COLLECTIF CITOYEN  |  0  |     0    |  0  |
|         BELG.UNIE-BUB  |  0  |     0    |  0  |
|                BLANCO  |  0  |     0    |  0  |
|             CHEZ NOUS  |  0  |     0    |  0  |
|                   RMC  |  0  |     0    |  0  |

=======================================================================================================
Luxembourg (8) Nc=193691 Nphantoms=0 votes={3=56289, 1=54196, 2=29488, 6=13506, 9=5888, 4=4413, 8=4300, 5=3975, 7=3354} undervotes=18282, voteForN=1
reported winners
 seat            winner/round     nvotes,  score,  voteDiff,
 ( 1)           LES ENGAGÉS / 1,   56289,  56289,
 ( 2)                    MR / 1,   54196,  54196,    2093,
 ( 3)                    PS / 1,   29488,  29488,   24708,
 ( 4)           LES ENGAGÉS / 2,   56289,  28144,    1344,



|                party   | min | reported | max |
|------------------------|-----|----------|-----|
|           LES ENGAGÉS  |  2  |     2    |  2  |
|                    MR  |  1  |     1    |  1  |
|                    PS  |  1  |     1    |  1  |
|                  N-VA  |  0  |     0    |  0  |
|                  DéFI  |  0  |     0    |  0  |
|                 ECOLO  |  0  |     0    |  0  |
|                BLANCO  |  0  |     0    |  0  |
|     COLLECTIF CITOYEN  |  0  |     0    |  0  |
|             CHEZ NOUS  |  0  |     0    |  0  |

=======================================================================================================
Namur (9) Nc=338346 Nphantoms=0 votes={3=90694, 1=80042, 2=52913, 5=31463, 8=22014, 12=9595, 7=8151, 6=5526, 9=5357, 11=4556, 4=983, 10=881} undervotes=26171, voteForN=1
reported winners
 seat            winner/round     nvotes,  score,  voteDiff,
 ( 1)           LES ENGAGÉS / 1,   90694,  90694,
 ( 2)                    MR / 1,   80042,  80042,   10652,
 ( 3)                    PS / 1,   52913,  52913,   27129,
 ( 4)           LES ENGAGÉS / 2,   90694,  45347,    7566,
 ( 5)                    MR / 2,   80042,  40021,    5326,
 ( 6)                   PTB / 1,   31463,  31463,    8558,
 ( 7)           LES ENGAGÉS / 3,   90694,  30231,    1232,



|                party   | min | reported | max |
|------------------------|-----|----------|-----|
|           LES ENGAGÉS  |  3  |     3    |  3  |
|                    MR  |  2  |     2    |  2  |
|                    PS  |  1  |     1    |  1  |
|                   PTB  |  1  |     1    |  1  |
|                 Agora  |  0  |     0    |  0  |
|                  N-VA  |  0  |     0    |  0  |
|                  DéFI  |  0  |     0    |  0  |
|                 ECOLO  |  0  |     0    |  0  |
|                BLANCO  |  0  |     0    |  0  |
|         BELG.UNIE-BUB  |  0  |     0    |  0  |
|     COLLECTIF CITOYEN  |  0  |     0    |  0  |
|             CHEZ NOUS  |  0  |     0    |  0  |

=======================================================================================================
 */


/*
with this table, I can calculate minSeats for any coalition = subset of parties.

FlandersEast    winner Vooruit-3    loser CD&V-3
Liege           winner LESENGAGÉS-3 loser PS-4
Bruxelle        winner PS-4         loser MR-5
Hainut          winner ECOLO-1      loser PTB-3, add assertion: winner PS-6 loser PTB-3
Anvers          winner CD&V-3       loser PVDA-3
                winner Vooruit-3    loser PVDA-3
                winner CD&V-3       loser VLAAMS BELANG-6
*/




