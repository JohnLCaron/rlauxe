package org.cryptobiotic.rlauxe.belgium


import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.writeSortedCardsExternalSort
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.cli.runRound
import org.cryptobiotic.rlauxe.dhondt.DhondtCandidate
import org.cryptobiotic.rlauxe.dhondt.makeProtoContest
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import kotlin.test.Test
import kotlin.test.fail

class TestCreateBelgiumClcaFromJson {
    val elections = mapOf(
        "Anvers" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription d'Anvers.json",
        "Bruxelles" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Bruxelles-Capitale.json",
        "FlandreWest" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Flandre occidentale.json",
        "FlandreEast" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Flandre orientale.json",
        "Hainaut" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Hainaut.json",
        "Liège" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Liège.json",
        "Limbourg" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Limbourg.json",
        "Luxembourg" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Luxembourg.json",
        "Namur" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Namur.json",
        "BrabantFlamant" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription du Brabant flamand.json",
        "BrabantWallon" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription du Brabant wallon.json",
    )
    val toptopdir = "/home/stormy/rla/cases/belgium/2024"

    @Test
    fun createBelgiumElection() {
        createBelgiumElection("Anvers")
    }

    @Test
    fun createAllBelgiumElection() {
        val allmvrs = mutableMapOf<String, Pair<Int, Int>>()
        elections.keys.forEach {
            allmvrs[it] =  createBelgiumElection(it)
        }
        allmvrs.forEach {
            val pct = (100.0 * it.value.second) / it.value.first.toDouble()
            println("${sfn(it.key, 15)}: Nc= ${trunc(it.value.first.toString(), 10)} " +
                    " nmvrs= ${trunc(it.value.second.toString(), 6)} pct= ${dfn(pct, 2)} %")
        }
    }

    fun createBelgiumElection(electionName: String): Pair<Int, Int> {
        println("======================================================")
        println("electionName $electionName")
        val filename = elections[electionName]!!
        val result: Result<BelgiumElectionJson, ErrorMessages> = readBelgiumElectionJson(filename)
        val belgiumElection = if (result is Ok) result.unwrap()
            else throw RuntimeException("Cannot read belgiumElection from ${filename} err = $result")

        val dhondtParties = belgiumElection.ElectionLists.mapIndexed { idx, it ->  DhondtCandidate(it.PartyLabel, idx+1, it.NrOfVotes) }
        val nwinners = belgiumElection.ElectionLists.sumOf { it.NrOfSeats }
        val dcontest = makeProtoContest(electionName, 1, dhondtParties, nwinners, belgiumElection.NrOfBlankVotes,.05)

        val totalVotes = belgiumElection.NrOfValidVotes + belgiumElection.NrOfBlankVotes
        val contestd = dcontest.createContest(Nc = totalVotes, Ncast = totalVotes)

        val topdir = "$toptopdir/$electionName"
        createBelgiumClca(topdir, contestd)

        val publisher = Publisher("$topdir/audit")
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsExternalSort(topdir, publisher, config.seed)

        val auditdir = "$topdir/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = true)
        println()
        print(results)
        if (results.hasErrors) fail()

        println("============================================================")
        var done = false
        var finalRound: AuditRound? = null
        while (!done) {
            val lastRound = runRound(inputDir = auditdir, useTest = true, quiet = true)
            if (lastRound != null) finalRound = lastRound
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
        }

        return if (finalRound != null) {
            println("$electionName: ${finalRound.show()}")
            Pair(totalVotes, finalRound.nmvrs)
        } else Pair(0, 0)
    }

    @Test
    fun showBelgiumElection() {
        val electionName = "Liège"
        val result = showBelgiumElection(electionName)
        val pct = (100.0 * result.second) / result.first.toDouble()
        println("${sfn(electionName, 15)}: Nc= ${trunc(result.first.toString(), 10)} " +
                " nmvrs= ${trunc(result.second.toString(), 6)} pct= ${dfn(pct, 2)} %")
    }

    @Test
    fun showAllBelgiumElection() {
        val allmvrs = mutableMapOf<String, Pair<Int, Int>>()
        elections.keys.forEach {
            allmvrs[it] = showBelgiumElection(it)
        }
        allmvrs.forEach {
            val pct = (100.0 * it.value.second) / it.value.first.toDouble()
            println("${sfn(it.key, 15)}: Nc= ${trunc(it.value.first.toString(), 10)} " +
                    " nmvrs= ${trunc(it.value.second.toString(), 6)} pct= ${dfn(pct, 2)} %")
        }
    }

    fun showBelgiumElection(electionName: String): Pair<Int, Int> {
        println("======================================================")
        println("showBelgiumElection $electionName")
        val topdir = "$toptopdir/$electionName"
        val auditdir = "$topdir/audit"

        val auditRecord = PersistedWorkflow(auditdir, useTest=true).auditRecord
        val contestUA = auditRecord.contests.first()
        println(contestUA.show())
        println("minAssertion: ${contestUA.minAssertion()!!.assorter}")
        println("  ${contestUA.contest.showAssertionDiff(contestUA.minAssertion())}")
        println(contestUA.contest.showCandidates())

        val finalRound = auditRecord.rounds.last()
        val Nc = finalRound.contestRounds.first().Nc
        return Pair(Nc, finalRound.nmvrs)
    }
}

/*
======================================================
showBelgiumElection Anvers
minAssertion: (8/4): reportedMean=0.5003 reportedMargin=0.0005
  winner=41964.7 loser=41752.3 diff=212.3 recountMargin=0.0051
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

======================================================
showBelgiumElection Bruxelles
minAssertion: (3/2): reportedMean=0.5004 reportedMargin=0.0009
  winner=24129.0 loser=24031.0 diff=98.0 recountMargin=0.0041
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

======================================================
showBelgiumElection FlandreWest
minAssertion: (3/7): reportedMean=0.5063 reportedMargin=0.0126
  winner=44129.0 loser=40482.3 diff=3646.7 recountMargin=0.0826
candidate          Round:           1 |           2 |           3 |           4 |           5 |
 1        VLAAMS BELANG :  202800 (1) |  101400 (5) |   67600 (8) |  50700 (12) |       40560 |
 2             open vld :   65840 (9) |       32920 |       21946 |       16460 |       13168 |
 3                 PVDA :  44129 (16) |       22064 |       14709 |       11032 |        8825 |
 4              Vooruit :  137422 (3) |   68711 (7) |  45807 (14) |       34355 |       27484 |
 5                 N-VA :  192037 (2) |   96018 (6) |  64012 (10) |  48009 (13) |       38407 |
 6                GROEN :  45502 (15) |       22751 |       15167 |       11375 |        9100 |
 7                 CD&V :  121447 (4) |  60723 (11) |       40482 |       30361 |       24289 |
 8               Voor U*:        9700 |        4850 |        3233 |        2425 |        1940 |
 9        Partij BLANCO*:        8178 |        4089 |        2726 |        2044 |        1635 |

======================================================
showBelgiumElection FlandreEast
minAssertion: (4/7): reportedMean=0.5009 reportedMargin=0.0017
  winner=42586.0 loser=41957.0 diff=629.0 recountMargin=0.0148
candidate          Round:           1 |           2 |           3 |           4 |           5 |           6 |
 1        VLAAMS BELANG :  234888 (1) |  117444 (6) |   78296 (9) |  58722 (15) |  46977 (18) |       39148 |
 2             open vld :  119200 (5) |  59600 (14) |       39733 |       29800 |       23840 |       19866 |
 3                 PVDA :  75942 (11) |       37971 |       25314 |       18985 |       15188 |       12657 |
 4              Vooruit :  127758 (3) |  63879 (12) |  42586 (20) |       31939 |       25551 |       21293 |
 5                 N-VA :  231470 (2) |  115735 (7) |  77156 (10) |  57867 (16) |  46294 (19) |       38578 |
 6                GROEN :  103722 (8) |  51861 (17) |       34574 |       25930 |       20744 |       17287 |
 7                 CD&V :  125871 (4) |  62935 (13) |       41957 |       31467 |       25174 |       20978 |
 8               Voor U*:        8007 |        4003 |        2669 |        2001 |        1601 |        1334 |
 9        BELG.UNIE-BUB*:        1390 |         695 |         463 |         347 |         278 |         231 |
10   GV-GEZOND VERSTAND*:        2352 |        1176 |         784 |         588 |         470 |         392 |
11        Partij BLANCO*:        8057 |        4028 |        2685 |        2014 |        1611 |        1342 |

======================================================
showBelgiumElection Hainaut
minAssertion: (4/1): reportedMean=0.5085 reportedMargin=0.0170
  winner=34446.3 loser=32126.5 diff=2319.8 recountMargin=0.0673
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

======================================================
showBelgiumElection Liège
minAssertion: (3/2): reportedMean=0.5006 reportedMargin=0.0012
  winner=34570.3 loser=34360.8 diff=209.6 recountMargin=0.0061
candidate          Round:           1 |           2 |           3 |           4 |           5 |           6 |
 1                   MR :  179296 (1) |   89648 (5) |   59765 (7) |  44824 (12) |  35859 (13) |       29882 |
 2                   PS :  137443 (2) |   68721 (6) |  45814 (10) |       34360 |       27488 |       22907 |
 3          LES ENGAGÉS :  103711 (3) |   51855 (8) |  34570 (14) |       25927 |       20742 |       17285 |
 4                  PTB :   91188 (4) |  45594 (11) |       30396 |       22797 |       18237 |       15198 |
 5                 N-VA*:       10840 |        5420 |        3613 |        2710 |        2168 |        1806 |
 6                 DéFI*:       13816 |        6908 |        4605 |        3454 |        2763 |        2302 |
 7                ECOLO :   49936 (9) |       24968 |       16645 |       12484 |        9987 |        8322 |
 8    COLLECTIF CITOYEN*:        8289 |        4144 |        2763 |        2072 |        1657 |        1381 |
 9        BELG.UNIE-BUB*:        1502 |         751 |         500 |         375 |         300 |         250 |
10               BLANCO*:       10656 |        5328 |        3552 |        2664 |        2131 |        1776 |
11            CHEZ NOUS*:       21877 |       10938 |        7292 |        5469 |        4375 |        3646 |
12                  RMC*:        3361 |        1680 |        1120 |         840 |         672 |         560 |

======================================================
showBelgiumElection Limbourg
minAssertion: (4/1): reportedMean=0.5069 reportedMargin=0.0138
  winner=37595.5 loser=35497.0 diff=2098.5 recountMargin=0.0558
candidate          Round:           1 |           2 |           3 |           4 |
 1        VLAAMS BELANG :  141988 (1) |   70994 (5) |   47329 (8) |       35497 |
 2             open vld :  40985 (11) |       20492 |       13661 |       10246 |
 3                 PVDA :   52303 (7) |       26151 |       17434 |       13075 |
 4              Vooruit :   75191 (4) |  37595 (12) |       25063 |       18797 |
 5                 N-VA :  136606 (2) |   68303 (6) |   45535 (9) |       34151 |
 6                GROEN*:       27619 |       13809 |        9206 |        6904 |
 7                 CD&V :   90715 (3) |  45357 (10) |       30238 |       22678 |
 8               Voor U*:        5505 |        2752 |        1835 |        1376 |
 9        Partij BLANCO*:        4660 |        2330 |        1553 |        1165 |
10        BELG.UNIE-BUB*:        1202 |         601 |         400 |         300 |

======================================================
showBelgiumElection Luxembourg
minAssertion: (3/1): reportedMean=0.5054 reportedMargin=0.0108
  winner=28144.5 loser=27098.0 diff=1046.5 recountMargin=0.0372
candidate          Round:           1 |           2 |           3 |
 1                   MR :   54196 (2) |       27098 |       18065 |
 2                   PS :   29488 (3) |       14744 |        9829 |
 3          LES ENGAGÉS :   56289 (1) |   28144 (4) |       18763 |
 4                 N-VA*:        4413 |        2206 |        1471 |
 5                 DéFI*:        3975 |        1987 |        1325 |
 6                ECOLO :       13506 |        6753 |        4502 |
 7               BLANCO*:        3354 |        1677 |        1118 |
 8    COLLECTIF CITOYEN*:        4300 |        2150 |        1433 |
 9            CHEZ NOUS*:        5888 |        2944 |        1962 |

======================================================
showBelgiumElection Namur
minAssertion: (3/2): reportedMean=0.5112 reportedMargin=0.0223
  winner=30231.3 loser=26456.5 diff=3774.8 recountMargin=0.1249
candidate          Round:           1 |           2 |           3 |           4 |
 1                   MR :   80042 (2) |   40021 (5) |       26680 |       20010 |
 2                   PS :   52913 (3) |       26456 |       17637 |       13228 |
 3          LES ENGAGÉS :   90694 (1) |   45347 (4) |   30231 (7) |       22673 |
 4                Agora*:         983 |         491 |         327 |         245 |
 5                  PTB :   31463 (6) |       15731 |       10487 |        7865 |
 6                 N-VA*:        5526 |        2763 |        1842 |        1381 |
 7                 DéFI*:        8151 |        4075 |        2717 |        2037 |
 8                ECOLO :       22014 |       11007 |        7338 |        5503 |
 9               BLANCO*:        5357 |        2678 |        1785 |        1339 |
10        BELG.UNIE-BUB*:         881 |         440 |         293 |         220 |
11    COLLECTIF CITOYEN*:        4556 |        2278 |        1518 |        1139 |
12            CHEZ NOUS*:        9595 |        4797 |        3198 |        2398 |

======================================================
showBelgiumElection BrabantFlamant
minAssertion: (1/5): reportedMean=0.5107 reportedMargin=0.0213
  winner=39781.7 loser=36576.6 diff=3205.1 recountMargin=0.0806
candidate          Round:           1 |           2 |           3 |           4 |           5 |
 1        VLAAMS BELANG :  119345 (2) |   59672 (8) |  39781 (15) |       29836 |       23869 |
 2             open vld :   83744 (6) |  41872 (14) |       27914 |       20936 |       16748 |
 3                 PVDA :   57600 (9) |       28800 |       19200 |       14400 |       11520 |
 4              Vooruit :   98092 (3) |  49046 (11) |       32697 |       24523 |       19618 |
 5                 N-VA :  182883 (1) |   91441 (5) |   60961 (7) |  45720 (13) |       36576 |
 6                GROEN :  57395 (10) |       28697 |       19131 |       14348 |       11479 |
 7                 CD&V :   93465 (4) |  46732 (12) |       31155 |       23366 |       18693 |
 8               Voor U*:        9961 |        4980 |        3320 |        2490 |        1992 |
 9        BELG.UNIE-BUB*:        4086 |        2043 |        1362 |        1021 |         817 |
10        Partij BLANCO*:        6859 |        3429 |        2286 |        1714 |        1371 |
11               l'Unie*:        3255 |        1627 |        1085 |         813 |         651 |

======================================================
showBelgiumElection BrabantWallon
minAssertion: (1/3): reportedMean=0.5042 reportedMargin=0.0083
  winner=30162.0 loser=29038.5 diff=1123.5 recountMargin=0.0372
candidate          Round:           1 |           2 |           3 |           4 |
 1                   MR :   90486 (1) |   45243 (3) |   30162 (5) |       22621 |
 2                   PS :   31741 (4) |       15870 |       10580 |        7935 |
 3          LES ENGAGÉS :   58077 (2) |       29038 |       19359 |       14519 |
 4                Agora*:         802 |         401 |         267 |         200 |
 5                  PTB :       20221 |       10110 |        6740 |        5055 |
 6                 N-VA*:        5753 |        2876 |        1917 |        1438 |
 7                 DéFI*:        8750 |        4375 |        2916 |        2187 |
 8                ECOLO :       23587 |       11793 |        7862 |        5896 |
 9    COLLECTIF CITOYEN*:        4781 |        2390 |        1593 |        1195 |
10               l'Unie*:         918 |         459 |         306 |         229 |
11            CHEZ NOUS*:        4659 |        2329 |        1553 |        1164 |
12                  RMC*:         664 |         332 |         221 |         166 |
13               BLANCO*:        4830 |        2415 |        1610 |        1207 |
14        BELG.UNIE-BUB*:        1003 |         501 |         334 |         250 |

         Anvers: Nc=    1235587  nmvrs=  12030 pct= 0.97 %
      Bruxelles: Nc=     550514  nmvrs=   7713 pct= 1.40 %
    FlandreWest: Nc=     866870  nmvrs=    951 pct= 0.11 %
    FlandreEast: Nc=    1083369  nmvrs=   3473 pct= 0.32 %
        Hainaut: Nc=     819569  nmvrs=    529 pct= 0.06 %
          Liège: Nc=     685695  nmvrs=   5803 pct= 0.85 %
       Limbourg: Nc=     606094  nmvrs=    649 pct= 0.11 %
     Luxembourg: Nc=     193691  nmvrs=    554 pct= 0.29 %
          Namur: Nc=     338346  nmvrs=    303 pct= 0.09 %
 BrabantFlamant: Nc=     751697  nmvrs=    374 pct= 0.05 %
  BrabantWallon: Nc=     270051  nmvrs=    600 pct= 0.22 %

 */