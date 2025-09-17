package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.dominion.CastVoteRecord
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportCsv
import org.cryptobiotic.rlauxe.dominion.readDominionCvrExportCsv
import org.cryptobiotic.rlauxe.util.nfn
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.test.Test

class TestBoulderUndervotes {

    @Test
    fun testBoulderBallotType() {
        val filename = "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip"
        val export: DominionCvrExportCsv = readDominionCvrExportCsv(filename, "Boulder")
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
                cardStyles[key + "-A"] = CardStyle(key + "-A", styleId, emptyList(), styleA, 0)
                cardStyles[key + "-B"] = CardStyle(key + "-B", styleId + 1, emptyList(), styleB, 0)
                styleId += 2
            } else {
                value.forEach { contestIds ->
                    cardStyles[key] = CardStyle(key, styleId, emptyList(), contestIds, 0)
                    styleId++
                }
            }
        }
        cardStyles.toSortedMap().forEach { (key, value) ->
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
    // still, we conclude that all ballots in a group have the same CardStyle, which makes it easier to generate accurate simulated CVRs.

    fun extractBallotType(s: String): String {
        val btoke = s.split(" ", "-", ",")[0]
        return btoke.toInt().toString()
    }

    @Test
    fun testSovoContests() {
        val sovo = readBoulderStatementOfVotes(
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            "Boulder2024"
        )

        val filename = "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip"
        val export: DominionCvrExportCsv = readDominionCvrExportCsv(filename, "Boulder")

        val boulderElection = BoulderElection(export, sovo)
        val countCvrVotes = boulderElection.countCvrVotes()
        val countRedactedVotes = boulderElection.countRedactedVotes()

        val oaContests = mutableListOf<OneAuditContest>()
        boulderElection.infoList.forEach { info ->
            val sovoContest = sovo.contests.find { it.contestTitle == info.name }
            if (sovoContest != null) oaContests.add(
                OneAuditContest(info, sovoContest, countCvrVotes[info.id]!!, countRedactedVotes[info.id]!!)
            )
            else println("*** cant find '${info.name}' in BoulderStatementOfVotes")
        }

        println()
        oaContests.forEach {
            println(BoulderContestVotes.header)
            println(it)
        }
    }

    @Test
    fun testSovoProblems() {
        val sovo = readBoulderStatementOfVotes(
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            "Boulder2024"
        )

        val filename = "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip"
        val export: DominionCvrExportCsv = readDominionCvrExportCsv(filename, "Boulder")

        val boulderElection = BoulderElection(export, sovo)
        val countCvrVotes = boulderElection.countCvrVotes()
        val countRedactedVotes = boulderElection.countRedactedVotes()

        val oaContests = mutableListOf<OneAuditContest>()
        boulderElection.infoList.forEach { info ->
            val sovoContest = sovo.contests.find { it.contestTitle == info.name }
            if (sovoContest != null) oaContests.add(
                OneAuditContest(info, sovoContest, countCvrVotes[info.id]!!, countRedactedVotes[info.id]!!)
            )
            else println("*** cant find '${info.name}' in BoulderStatementOfVotes")
        }

        oaContests.forEach {
            println(it.problems())
        }
    }
    // on contest 20, totalVotes and totalBallots is wrong vs the cvrs. only one where voteForN=3.
    // 'Town of Superior - Trustee' (20) candidates=[0, 1, 2, 3, 4, 5, 6] choiceFunction=PLURALITY nwinners=3 voteForN=3
    //  nvotes= 17110, sovoContest.totalVotes = 16417   ***  val nvotes = cvr.nvotes() + red.nvotes()
    //  nballots= 8485, sovoContest.totalBallots = 8254 *** val nballots = (nvotes + sovoContest.totalUnderVotes) / info.voteForN + sovoContest.totalOverVotes

// sovo totalUnderVotes is high or cvr.novotes is low, causing redUnderPct to be much bigger than cvrTabulation.novote
// much worse when voteForN > 1, which is probably a clue as to whats wrong.
// Generate novote CVRS anyway from these calculations. The main thing is to augment the group with the number of
// novotes per contest. Assign novotes across groups based on the number of ballots in that group (it could be theres
// something tricky in that?). Test that we can recreate the sovo contest values from the generated cvrs.
//
// contestTitle, precinctCount, activeVoters, totalBallots, totalVotes, totalUnderVotes, totalOverVotes, diff
//'Town of Erie - Mayor' (17) candidates=[0, 1] choiceFunction=PLURALITY nwinners=1 voteForN=1
// sovoContest=Town of Erie - Mayor, 8, 13681, 10004, 8807, 1193, 4, 0
// allTabulation={1=4454, 0=4353} ncards=9837 novote=1030 underPct= 10%
// cvrTabulation={1=4157, 0=4031} ncards=9218 novote=1030 underPct= 11%
// redTabulation={0=322, 1=297} ncards=619 novote=0 underPct= 0%
//  sovoCards= 10000 = (sovoContest.totalVotes + sovoContest.totalUnderVotes) / info.voteForN
//  phantoms= 0  = sovoContest.totalBallots - sovoCards - sovoContest.totalOverVotes
//  redUnder= 163  = sovoContest.totalUnderVotes / info.voteForN - cvr.novote
//  redNcards= 782 = redUnder + redacted.ncards
//  redUnderPct= 100.0 * redUnder / redNcards  = 20%
//
//contestTitle, precinctCount, activeVoters, totalBallots, totalVotes, totalUnderVotes, totalOverVotes, diff
//'Town of Erie - Council Member District 1' (18) candidates=[0, 1, 2, 3] choiceFunction=PLURALITY nwinners=2 voteForN=2
// sovoContest=Town of Erie - Council Member District 1, 6, 10357, 8512, 11469, 5553, 1, -8511
// allTabulation={2=3992, 0=2654, 1=2634, 3=2189} ncards=8279 novote=1403 underPct= 16%
// cvrTabulation={2=3795, 0=2523, 1=2512, 3=2076} ncards=7998 novote=1403 underPct= 17%
// redTabulation={2=197, 0=131, 1=122, 3=113} ncards=281 novote=0 underPct= 0%
//  sovoCards= 8511 = (sovoContest.totalVotes + sovoContest.totalUnderVotes) / info.voteForN
//  phantoms= 0  = sovoContest.totalBallots - sovoCards - sovoContest.totalOverVotes
//  redUnder= 1373  = sovoContest.totalUnderVotes / info.voteForN - cvr.novote
//  redNcards= 1654 = redUnder + redacted.ncards
//  redUnderPct= 100.0 * redUnder / redNcards  = 83%

    /*
a novote means no candidate was chosen in the contest,
when voteForN = 1, an undervote is a contest where no candidate was chosen. undervote = novote
when voteForN > 1, an undervote is a contest where less than max candidate. if undervote = voteForN - actual, then
  ncards = (totalVotes + undervotes) / VoteForN.

We could modify ContestTabulation to calculate undervotes as well as novotes.
then
  redUndervotes = sovoContest.totalUnderVotes - cvr.undervotes
so
  redNcards = (redVotes + redUndervotes) / VoteForN
 */

    @Test
    fun testRedactedGroups() {
        val sovo = readBoulderStatementOfVotes(
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            "Boulder2024"
        )
        val filename = "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip"
        val export: DominionCvrExportCsv = readDominionCvrExportCsv(filename, "Boulder")
        val election = BoulderElection(export, sovo)

        val contestIds = election.infoList.map { it.id }
        print("          ")
        contestIds.forEach {  print("${nfn(it, 4)}|") }
        println()

        export.redacted.forEach { rgroup ->
            print(rgroup.showVotes(contestIds))
        }
    }

    @Test
    fun testRedactedUndervotes() {
        val sovo = readBoulderStatementOfVotes(
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            "Boulder2024"
        )
        val filename = "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip"
        val export: DominionCvrExportCsv = readDominionCvrExportCsv(filename, "Boulder")
        val election = BoulderElection(export, sovo)

        val contestUnderVoteSums = mutableMapOf<Int, Int>()
        export.redacted.forEach { rgroup ->
            val undervotes = rgroup.undervote() // contestid -> undervote
            undervotes.forEach { key, value ->
                val contestUnderVoteSum = contestUnderVoteSums.getOrPut(key) { 0 }
                contestUnderVoteSums[key] = contestUnderVoteSum + value
            }
        }
        contestUnderVoteSums.toSortedMap().forEach {  println(it) }
        println()
    }

}