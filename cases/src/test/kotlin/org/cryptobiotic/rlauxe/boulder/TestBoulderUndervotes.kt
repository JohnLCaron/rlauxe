package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.dominion.CastVoteRecord
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportCsv
import org.cryptobiotic.rlauxe.dominion.readDominionCvrExportCsv
import org.cryptobiotic.rlauxe.oneaudit.CardPoolWithBallotStyle
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.mergeReduce
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.test.Test
import kotlin.text.appendLine

class TestBoulderUndervotes {
    // val cvrFilename = "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip"
    // val sovoFilename = "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip"

    val datadir = "$testdataDir/cases/boulder2025"
    val cvrFilename = "$datadir/Redacted-CVR-PUBLIC.utf8.csv"
    val sovoFilename = "$datadir/2025C-Boulder-County-Official-Statement-of-Votes.utf8.csv"

    val sovo = readBoulderStatementOfVotes(sovoFilename, "Boulder2024")

    @Test
    fun testBoulderBallotType() {
        val export: DominionCvrExportCsv = readDominionCvrExportCsv(cvrFilename, "Boulder")
        // println(export.summary())

        var count = 0
        val ballotTypes = mutableMapOf<String, MutableList<List<Int>>>()
        export.cvrs.forEach { cvr: CastVoteRecord ->
            val contestIds = cvr.contestVotes.map { it.contestId }
            val prevContestIds = ballotTypes.getOrPut(cvr.ballotType) { mutableListOf() }
            if (!prevContestIds.contains(contestIds)) {
                prevContestIds.add(contestIds)
            }
            count++
        }
        println("processed $count CastVoteRecords")

        var styleId = 1
        val cardStyles = mutableMapOf<String, CardStyle>()
        ballotTypes.toSortedMap().forEach { (key, value) ->
            if (value.size == 2) {
                val (styleA, styleB) = if (value[0].size > value[1].size) {
                    Pair(value[0], value[1])
                } else {
                    Pair(value[1], value[0])
                }
                cardStyles[key + "-A"] = CardStyle(key + "-A", styleA, styleId)
                cardStyles[key + "-B"] = CardStyle(key + "-B", styleB, styleId+1)
                styleId += 2
            } else {
                value.forEach { contestIds ->
                    cardStyles[key] = CardStyle(key, contestIds, styleId)
                    styleId++
                }
            }
        }
        cardStyles.toSortedMap().forEach { (_, value) ->
            println(value)
        }

        println()
        export.redacted.forEach { rgroup ->
            println(rgroup)
            // test if theres a cardStyle that matches
            val isA = rgroup.ballotType.contains("-A")
            val gcardStyle = extractBallotType(rgroup.ballotType) + "-" + if (isA) "A" else "B"
            val cardStyle = cardStyles[gcardStyle]
            if (cardStyle != null) {
                val gids = rgroup.contestVotes.map { it.key }.sorted()
                if (cardStyle.contestIds != gids)
                    println("*** rgroup '${rgroup.ballotType}'\n $gids !=\n ${cardStyle.contestIds} (${gcardStyle})")
            } else {
                println("***dont have cardStyle ${gcardStyle}")
            }
            println()
        }
    }
    // with this exception, redacted groups match existing CardStyle:
    // RedactedGroup '06, 33, & 36-A', contestIds=[0, 1, 2, 3, 5, 10, 11, 12, 13, 14, 15, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42], totalVotes=8012
    //*** rgroup '06, 33, & 36-A'
    // [0, 1, 2, 3, 5, 10, 11, 12, 13, 14, 15, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42] !=
    // [0, 1, 2, 3, 5, 10, 11, 13, 14, 15, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42] (6-A)
    //
    // still, we will assume that all ballots in a group have the same CardStyle, which makes it easier to generate accurate simulated CVRs.
    // this wrongly includes contest 12,

    fun extractBallotType(s: String): String {
        val btoke = s.split(" ", "-", ",")[0]
        return btoke.toInt().toString()
    }

    @Test
    fun showSovoContestDetail2() {
        val export: DominionCvrExportCsv = readDominionCvrExportCsv(cvrFilename, "Boulder")

        val election2 = CreateBoulderElection(export, sovo, isClca=false)
        println()
        election2.oaContests.forEach { (_, oa) ->
            println(BoulderContestVotes.header)
            println(oa.details())
        }
    }

    @Test
    fun showPoolVotes() {
        val export: DominionCvrExportCsv = readDominionCvrExportCsv(cvrFilename, "Boulder")

        println("votes, undervotes")

        val election2 = CreateBoulderElection(export, sovo, isClca=false)
        val contestIds = election2.infoList.map { it.id }
        showPoolVotes(contestIds, election2.cardPools)
    }

    fun showPoolVotes(contestIds: List<Int>, cardPools: List<CardPoolWithBallotStyle>, width:Int = 4) {
        println("votes, undervotes")
        print("${trunc("poolName", 9)}:")
        contestIds.forEach {  print("${nfn(it, width)}|") }
        println()

        cardPools.forEach {
            println(it.showVotes(contestIds, width))
        }
    }

    @Test
    fun showRedactedUndervotes2() {
        val export: DominionCvrExportCsv = readDominionCvrExportCsv(cvrFilename, "Boulder")
        // val election1 = BoulderElectionOAsim(export, sovo)
        val election2 = CreateBoulderElection(export, sovo, isClca=false)

        val contestIds = election2.infoList.map { it.id }

        // show the sums and compare to sovo
        // val list1 = election1.cardPools.map { it.undervotes() }
        val list2 = election2.cardPools.map { it.undervotes() }

        //val sum1 = mutableMapOf<Int, Int>()
        //sum1.mergeReduce(list1)
        val sum2 = mutableMapOf<Int, Int>()
        sum2.mergeReduce(list2)

        //val contests = election.oaContests.keys.toSortedSet()

        println("redUndervotes, sumPoolUndervotes2, diff")
        val showit = buildString {
            val width=5
            contestIds.forEach {  append("${nfn(it, width)}|") }
            appendLine()

            contestIds.forEach { id ->
                val oaContest = election2.oaContests[id]!!
                if (oaContest == null)
                    ("     |")
                else {
                    append("${nfn((oaContest.redUndervotes), width)}|")
                }
            }
            appendLine()

            contestIds.forEach { id ->
                val undervote2 = sum2[id]
                if (undervote2 == null)
                    append("     |")
                else {
                    append("${nfn(undervote2, width)}|")
                }
            }
            appendLine()

            contestIds.forEach { id ->
                val undervote2 = sum2[id]!!
                val oaContest = election2.oaContests[id]!!
                if (oaContest == null)
                    ("     |")
                else {
                    append("${nfn((oaContest.redUndervotes - undervote2), width)}|")
                }
            }
            appendLine()

        }
        println(showit)
    }
// redUndervotes, poolUndervotes2Sum, diff
//    0|    1|    2|    3|    4|    5|    6|    7|    8|    9|   10|   11|   12|   13|   14|   15|   16|   17|   18|   19|   20|   21|   22|   23|   24|   25|   26|   27|   28|   29|   30|   31|   32|   33|   34|   35|   36|   37|   38|   39|   40|   41|   42|   43|   44|   45|   46|   47|   48|   49|   50|   51|   52|   53|   54|   55|   56|   57|   58|   59|   60|   61|   62|   63|   64|
//  100|  753| 2294| 1030|  465|  547|   66|  145|  408|  189|  273| 2372| 1288| 2461| 1198| 2599|   86|  167|  465|  216| 1320| 2240| 2318| 2280| 2424| 2511| 2511| 2512| 2517| 2693| 2701| 2687| 2713| 2660| 1115| 1437| 1220|  815| 1646|  760| 1042|  943|  913|  573|  803|  879|  821|  868|  153|  174|  184|  146|  151|   83|   18|   80|  437|    2|    3|    5|    4|   13|   13|  959|  327|
//    0|  651| 2193|  928|  423|  518|   62|  128|  369|  168|  252| 2270| 1617| 2361| 1096| 2497|   83|  160|  455|  212|  603| 2138| 2216| 2178| 2322| 2409| 2409| 2410| 2415| 2591| 2599| 2585| 2611| 2558| 1013| 1335| 1118|  713| 1544|  658|  940|  841|  811|    4|  234|  310|  252|  299|   96|  117|  127|   89|   51|   24|   11|   38|  212|    2|    3|    1|    0|    4|    4|  390|  137|
//  100|  102|  101|  102|   42|   29|    4|   17|   39|   21|   21|  102| -329|  100|  102|  102|    3|    7|   10|    4|  717|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  569|  569|  569|  569|  569|   57|   57|   57|   57|  100|   59|    7|   42|  225|    0|    0|    4|    4|    9|    9|  569|  190|

    @Test
    fun showRedactedNcards() {
        val export: DominionCvrExportCsv = readDominionCvrExportCsv(cvrFilename, "Boulder")
        val election2 = CreateBoulderElection(export, sovo, isClca=false)

        val contestIds = election2.infoList.map { it.id }

        val list2 = election2.cardPools.map { pool ->
            pool.minCardsNeeded.keys.associate{ it to pool.ncards() }
        }
        val sum2 = mutableMapOf<Int, Int>()
        sum2.mergeReduce(list2)

        println("sovoRedcards, sumPoolCards, diff")
        val showit = buildString {
            val width=5
            contestIds.forEach {  append("${nfn(it, width)}|") }
            appendLine()

            contestIds.forEach { id ->
                val oaContest = election2.oaContests[id]!!
                if (oaContest == null)
                    ("     |")
                else {
                    append("${nfn((oaContest.redNcards), width)}|")
                }
            }
            appendLine()

            contestIds.forEach { id ->
                val poolCards = sum2[id]
                if (poolCards == null)
                    append("     |")
                else {
                    append("${nfn(poolCards, width)}|")
                }
            }
            appendLine()

            contestIds.forEach { id ->
                val poolCards = sum2[id]!!
                val oaContest = election2.oaContests[id]!!
                if (oaContest == null)
                    ("     |")
                else {
                    append("${nfn((oaContest.redNcards - poolCards), width)}|")
                }
            }
            appendLine()
        }
        println(showit)
    }
// combined A and B pools
// sovoRedcards, sumPoolCards, diff
//    0|    1|    2|    3|    4|    5|    6|    7|    8|    9|   10|   11|   12|   13|   14|   15|   16|   17|   18|   19|   20|   21|   22|   23|   24|   25|   26|   27|   28|   29|   30|   31|   32|   33|   34|   35|   36|   37|   38|   39|   40|   41|   42|   43|   44|   45|   46|   47|   48|   49|   50|   51|   52|   53|   54|   55|   56|   57|   58|   59|   60|   61|   62|   63|   64|
// 6483| 6485| 6484| 6485| 3092| 2117|  369|  848| 2245| 1518| 1505| 6485| 2610| 6483| 6485| 6485|  196|  786|  514|  272|  620| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 5814| 5814| 5814| 5814| 5814|  470|  470|  470|  456| 1002|  720|  142|  312| 2678|   11|   11|   21|   19|   31|   31| 5814| 2055|
// 6383| 6383| 6383| 6383| 3050| 2088|  365|  831| 2206| 1497| 1484| 6383| 2939| 6383| 6383| 6383|  193|  779|  509|  270|  381| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6373| 6373| 6373| 6373| 6373|  543|  543|  543|  500| 1117|  779|  149|  381| 2862|   12|   12|   22|   15|   22|   22| 6373| 2221|
//  100|  102|  101|  102|   42|   29|    4|   17|   39|   21|   21|  102| -329|  100|  102|  102|    3|    7|    5|    2|  239|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102| -559| -559| -559| -559| -559|  -73|  -73|  -73|  -44| -115|  -59|   -7|  -69| -184|   -1|   -1|   -1|    4|    9|    9| -559| -166|

// seperate A and B pools
// sovoRedcards, sumPoolCards, diff
//    0|    1|    2|    3|    4|    5|    6|    7|    8|    9|   10|   11|   12|   13|   14|   15|   16|   17|   18|   19|   20|   21|   22|   23|   24|   25|   26|   27|   28|   29|   30|   31|   32|   33|   34|   35|   36|   37|   38|   39|   40|   41|   42|   43|   44|   45|   46|   47|   48|   49|   50|   51|   52|   53|   54|   55|   56|   57|   58|   59|   60|   61|   62|   63|   64|
// 6483| 6485| 6484| 6485| 3092| 2117|  369|  848| 2245| 1518| 1505| 6485| 2610| 6483| 6485| 6485|  196|  786|  514|  272|  620| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 6485| 5814| 5814| 5814| 5814| 5814|  470|  470|  470|  456| 1002|  720|  142|  312| 2678|   11|   11|   21|   19|   31|   31| 5814| 2055|
// 6383| 6383| 6383| 6383| 3050| 2088|  365|  831| 2206| 1497| 1484| 6383| 2566| 6383| 6383| 6383|  193|  779|  509|  270|  381| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 6383| 5245| 5245| 5245| 5245| 5245|  413|  413|  413|  399|  902|  661|  135|  270| 2453|   11|   11|   17|   15|   22|   22| 5245| 1865|
//  100|  102|  101|  102|   42|   29|    4|   17|   39|   21|   21|  102|   44|  100|  102|  102|    3|    7|    5|    2|  239|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  102|  569|  569|  569|  569|  569|   57|   57|   57|   57|  100|   59|    7|   42|  225|    0|    0|    4|    4|    9|    9|  569|  190|

// so distribute diff to pools proportionate to sumPoolCards
// note that diff => 0 except 12, same contest that was added to redacted group '06, 33, & 36-A'. It looks like you should remove 12 from that group, there are no votes for 12 (done)

    @Test
    fun showNcards() {
        val export: DominionCvrExportCsv = readDominionCvrExportCsv(cvrFilename, "Boulder")
        val election2 = CreateBoulderElection(export, sovo, isClca=false)

        val contestIds = election2.infoList.map { it.id }

        val list2 = election2.cardPools.map { pool ->
            pool.minCardsNeeded.keys.associate{ it to pool.ncards() }
        }
        val sum2 = mutableMapOf<Int, Int>()
        sum2.mergeReduce(list2)

        //val contests = election.oaContests.keys.toSortedSet()

        println("sovo.totalBallots, oaContest.calcCards, diff, oaContest.sumAllCards, diff")
        val showit = buildString {
            val width=7
            contestIds.forEach {  append("${nfn(it, width)}|") }
            appendLine()

            contestIds.forEach { id ->
                val oaContest = election2.oaContests[id]!!
                if (oaContest == null)
                    ("     |")
                else {
                    append("${nfn((oaContest.sovoContest.totalBallots), width)}|")
                }
            }
            appendLine()

            contestIds.forEach { id ->
                val oaContest = election2.oaContests[id]!!
                if (oaContest == null)
                    ("     |")
                else {
                    append("${nfn(oaContest.Nc() , width)}|")
                }
            }
            appendLine()

            contestIds.forEach { id ->
                val oaContest = election2.oaContests[id]!!
                if (oaContest == null)
                    ("     |")
                else {
                    append("${nfn(oaContest.Nc() - oaContest.totalCards, width)}|")
                }
            }
            appendLine()

            contestIds.forEach { id ->
                val oaContest = election2.oaContests[id]!!
                if (oaContest == null)
                    ("     |")
                else {
                    append("${nfn(oaContest.sumAllCards(), width)}|")
                }
            }
            appendLine()

            contestIds.forEach { id ->
                val oaContest = election2.oaContests[id]!!
                if (oaContest == null)
                    ("     |")
                else {
                    append("${nfn(oaContest.Nc() - oaContest.sumAllCards(), width)}|")
                }
            }
            appendLine()
        }
        println(showit)
    }
    // note that diff => 0 except 12, same contest that was added to redacted group '06, 33, & 36-A'

    // sovoTotalBallots, sumCards, diff
    //      0|      1|      2|      3|      4|      5|      6|      7|      8|      9|     10|     11|     12|     13|     14|     15|     16|     17|     18|     19|     20|     21|     22|     23|     24|     25|     26|     27|     28|     29|     30|     31|     32|     33|     34|     35|     36|     37|     38|     39|     40|     41|     42|     43|     44|     45|     46|     47|     48|     49|     50|     51|     52|     53|     54|     55|     56|     57|     58|     59|     60|     61|     62|     63|     64|
    // 198886| 198886| 198886| 198886|  87432|  94987|  44770|  49038|  56298|  19083|  29697| 198886|  71452| 198886| 198886| 198886|   4267|  10004|   8512|   1492|   8254| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 197371| 197371| 197371| 197371| 197371|  57140|  57140|  57140|  18708|  55398|   9938|   1566|   8165|  72445|    156|    156|    176|    171|    327|    327| 197371|  67626|
    // 198884| 198886| 198885| 198886|  87432|  94987|  44770|  49038|  56298|  19083|  29697| 198886|  71452| 198884| 198886| 198886|   4267|  10004|   8512|   1492|   8485| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 197371| 197371| 197371| 197371| 197371|  57140|  57140|  57140|  18708|  55398|   9938|   1566|   8165|  72445|    156|    156|    176|    171|    327|    327| 197371|  67626|
    //      2|      0|      1|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      2|      0|      0|      0|      0|      0|      0|   -231|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|

    // contest 20 has bug in sovoTotalBallots, must use sumCards as Nc, see oaContest.Nc(). Also note that contest 20 only appears on ballotStyle 7

    // ok just approx
    // use oaContest.sumAllCards for Nc()
    // sovo.totalBallots, oaContest.calcCards, diff, oaContest.sumAllCards, diff
    //      0|      1|      2|      3|      4|      5|      6|      7|      8|      9|     10|     11|     12|     13|     14|     15|     16|     17|     18|     19|     20|     21|     22|     23|     24|     25|     26|     27|     28|     29|     30|     31|     32|     33|     34|     35|     36|     37|     38|     39|     40|     41|     42|     43|     44|     45|     46|     47|     48|     49|     50|     51|     52|     53|     54|     55|     56|     57|     58|     59|     60|     61|     62|     63|     64|
    // 198886| 198886| 198886| 198886|  87432|  94987|  44770|  49038|  56298|  19083|  29697| 198886|  71452| 198886| 198886| 198886|   4267|  10004|   8512|   1492|   8254| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 197371| 197371| 197371| 197371| 197371|  57140|  57140|  57140|  18708|  55398|   9938|   1566|   8165|  72445|    156|    156|    176|    171|    327|    327| 197371|  67626|
    // 198884| 198886| 198885| 198886|  87432|  94987|  44770|  49038|  56298|  19083|  29697| 198886|  71452| 198884| 198886| 198886|   4267|  10004|   8512|   1492|   8485| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 198886| 197371| 197371| 197371| 197371| 197371|  57140|  57140|  57140|  18708|  55398|   9938|   1566|   8165|  72445|    156|    156|    176|    171|    327|    327| 197371|  67626|
    //      2|      0|      1|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      2|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|      0|
    // 198884| 198884| 198884| 198884|  87437|  94990|  44771|  49033|  56294|  19085|  29701| 198884|  71448| 198884| 198884| 198884|   4267|  10009|   8515|   1494|   8252| 198884| 198884| 198884| 198884| 198884| 198884| 198884| 198884| 198884| 198884| 198884| 198884| 198884| 198884| 198884| 198884| 198884| 198884| 198884| 198884| 198884| 198884| 197371| 197371| 197371| 197371| 197371|  57129|  57129|  57129|  18694|  55395|   9951|   1573|   8152|  72485|    157|    157|    174|    169|    320|    320| 197371|  67636|
    //      2|      2|      2|      2|     -5|     -3|     -1|      5|      4|     -2|     -4|      2|      4|      2|      2|      2|      0|     -5|     -3|     -2|    233|      2|      2|      2|      2|      2|      2|      2|      2|      2|      2|      2|      2|      2|      2|      2|      2|      2|      2|      2|      2|      2|      2|      0|      0|      0|      0|      0|     11|     11|     11|     14|      3|    -13|     -7|     13|    -40|     -1|     -1|      2|      2|      7|      7|      0|    -10|
}