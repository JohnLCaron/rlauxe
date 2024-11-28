package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrIF

data class RaireCvrs(
    val contests: List<RaireCvrContest>,
    val cvrs: List<Cvr>,
    val filename: String,
)

data class RaireCvrContest(
    val contestNumber: Int,
    val candidates: List<Int>,
    val ncvrs: Int,
    val winner: Int = -1,
) {
    fun show() = buildString {
        appendLine(" ncvrs=${ncvrs} RaireCvrContest $contestNumber candidates=$candidates} winner=$winner")
    }
}

// TODO relation to the original CVRs ??
// "RaireCvr is always for one contest" probably an artifact of raire processing
// probably doesnt have to be seperate class, exept for method override hasMarkFor / hasOveVote ?
/** Duplicating the math from SHANGRLA CVR */
class RaireCvr(val cvr: CvrIF) {

    //     def get_vote_for(self, contest_id: str, candidate: str):
    //        return (
    //            False
    //            if (contest_id not in self.votes or candidate not in self.votes[contest_id])
    //            else self.votes[contest_id][candidate]
    //        )
    /** if candidate not ranked, 0, else rank (1 based) */
    fun get_vote_for(contest: Int, candidate: Int): Int {
        val rankedChoices = cvr.votes[contest]
        return if (rankedChoices == null || !rankedChoices.contains(candidate)) 0
               else rankedChoices.indexOf(candidate) + 1
    }

    //        rank_winner = self.get_vote_for(contest_id, winner)
    //        rank_loser = self.get_vote_for(contest_id, loser)
    //
    //        if not bool(rank_winner) and bool(rank_loser):
    //            return 1
    //        elif bool(rank_winner) and bool(rank_loser) and rank_loser < rank_winner:
    //            return 1
    //        else:
    //            return 0
    /**
     * Check whether vote is a vote for the loser with respect to a 'winner only' assertion.
     * Its a vote for the loser if they appear and the winner does not, or they appear before the winner
     *
     * @param winner identifier for winning candidate
     * @param loser identifier for losing candidate
     * @return 1 if the given vote is a vote for 'loser' and 0 otherwise
     */
    fun rcv_lfunc_wo(contest: Int, winner: Int, loser: Int): Int {
        val rank_winner = get_vote_for(contest, winner)
        val rank_loser = get_vote_for(contest, loser)

        return when {
            rank_winner == 0 && rank_loser != 0 -> 1
            rank_winner != 0 && rank_loser != 0 && rank_loser < rank_winner -> 1
            else -> 0
        }
    }

    //         if not cand in remaining:
    //            return 0
    //
    //        if not bool(rank_cand := self.get_vote_for(contest_id, cand)):
    //            return 0
    //        else:
    //            for altc in remaining:
    //                if altc == cand:
    //                    continue
    //                rank_altc = self.get_vote_for(contest_id, altc)
    //                if bool(rank_altc) and rank_altc <= rank_cand:
    //                    return 0
    //            return 1
    /**
     * Check whether 'vote' is a vote for the given candidate in the context
     * where only candidates in 'remaining' remain standing.
     *
     * @param cand identifier for candidate
     * @param remaining list of identifiers of candidates still standing
     * @return 1 if the given vote for the contest counts as a vote for 'cand' and 0 otherwise.
     * Essentially, if you reduce the ballot down to only those candidates in 'remaining',
     * and 'cand' is the first preference, return 1; otherwise return 0.
     */
    fun rcv_votefor_cand(contest: Int, cand: Int, remaining: List<Int>): Int {
        if (cand !in remaining) {
            return 0
        }

        val rank_cand = get_vote_for(contest, cand) ?: return 0
        if (rank_cand == 0) return 0

        for (altc in remaining) {
            if (altc == cand) continue

            val rank_altc = get_vote_for(contest, altc)
            if (rank_altc != 0 && rank_altc <= rank_cand) {
                return 0
            }
        }
        return 1
    }
}

/////////////////////////////////////////////////////////////////////
// DO NOT USE, use readRaireBallots()
// this is (apparently) RAIRE CSV format for ranked choice CVRs from SFDA2019
// input to RAIRE, not sure where these are produced, somewhere in SFDA2019
// eg SFDA2019_PrelimReport12VBMJustDASheets.raire
// TODO Colorado may be very different, see corla
//
// This reads:
// 1
// Contest,339,4,15,16,17,18
// 339,99813_1_1,17
// 339,99813_1_3,16
// 339,99813_1_6,18,17,15,16
// ...

/*
fun readRaireSfdaCvrs(filename: String): RaireCvrs {
    val path: Path = Paths.get(filename)
    val reader: Reader = Files.newBufferedReader(path)
    val parser = CSVParser(reader, CSVFormat.DEFAULT)
    val records = parser.iterator()

    // we expect the first line to be the number of contests
    val ncontests = records.next().get(0).toInt()
    if (ncontests != 1) throw RuntimeException("readRaireSfdaCvrs only allows one contest")

    val contests = mutableListOf<RaireCvrContest>()
    var cvrs = mutableListOf<RaireCvr>()
    var contestId = 0
    var nchoices = 0
    var choices = emptyList<Int>()

    while (records.hasNext()) {
        val line = records.next()
        val first = line.get(0)
        if (first.equals("Contest")) {
            if (cvrs.isNotEmpty()) {
                contests.add(RaireCvrContest(contestId, choices, cvrs.size))
            }
            // start a new contest
            contestId = line.get(1).toInt()
            nchoices = line.get(2).toInt()
            choices = readVariableListOfInt(line,3)
            require(nchoices == choices.size)
            cvrs = mutableListOf()
        } else {
            val cid = line.get(0).toInt()
            val location = line.get(1)
            val rankedChoices = readVariableListOfInt(line, 2)
            require(cid == contestId)
            require(choices.containsAll(rankedChoices))
            cvrs.add(RaireCvr(cid, location, rankedChoices))
        }
    }
    if (cvrs.isNotEmpty()) {
        contests.add(RaireCvrContest(contestId, choices, cvrs.size)) // dont know the winner
    }

    return RaireCvrs(contests, cvrs, filename)
}

private fun readVariableListOfInt(line: CSVRecord, startPos: Int): List<Int> {
    val result = mutableListOf<Int>()
    while (startPos + result.size < line.size()) {
        val s = line.get(startPos + result.size)
        if (s.isEmpty()) break
        val sn = line.get(startPos + result.size)
        result.add(sn.toInt())
    }
    return result
}

 */
