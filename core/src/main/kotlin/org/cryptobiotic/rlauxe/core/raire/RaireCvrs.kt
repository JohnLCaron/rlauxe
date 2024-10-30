package org.cryptobiotic.rlaux.core.raire

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

// this is probably RAIRE CSV format for ranked choice CVRs
// input to RAIRE I assume, not sure where these are produced
// We can use them also as the input to the RLA
data class RaireCvrs(
    val contests: List<RaireCvrContest>,
    val filename: String,
)

data class RaireCvrContest(
    val contestNumber: Int,
    val choices: List<Int>,
    val cvrs: List<RaireCvr>,
)

// RaireCvr is always for one contest.
data class RaireCvr(
    val contestNumber: Int,
    val location: String,    // guess this might be a location
    val rankedChoices: List<Int>,
) {
    constructor(ranks: List<Int>): this(0, "", ranks) // for quick testing

    fun rank(candidate: Int): Int {
        return rankedChoices.indexOf(candidate) + 1 // seems to be 1 based, see TestRcvAssorter
    }

// def get_vote_for(self, contest_id: str, candidate: str):
//    return (
//        False
//        if (contest_id not in self.votes or candidate not in self.votes[contest_id])
//        else self.votes[contest_id][candidate]
//    )


    fun get_vote_for(candidate: Int): Int {
        return if (!rankedChoices.contains(candidate)) 0 else rank(candidate)
    }


//
//     def rcv_lfunc_wo(self, contest_id: str, winner: str, loser: str) -> int:
//        """
//        Check whether vote is a vote for the loser with respect to a 'winner only'
//        assertion between the given 'winner' and 'loser'.
//
//        Parameters:
//        -----------
//        contest_id: string
//            identifier for the contest
//        winner: string
//            identifier for winning candidate
//        loser: string
//            identifier for losing candidate
//        cvr: CVR object
//
//        Returns:
//        --------
//        1 if the given vote is a vote for 'loser' and 0 otherwise
//        """
//        rank_winner = self.get_vote_for(contest_id, winner)
//        rank_loser = self.get_vote_for(contest_id, loser)
//
//        if not bool(rank_winner) and bool(rank_loser):
//            return 1
//        elif bool(rank_winner) and bool(rank_loser) and rank_loser < rank_winner:
//            return 1
//        else:
//            return 0
//


    /**
     * Check whether vote is a vote for the loser with respect to a 'winner only'
     * assertion between the given 'winner' and 'loser'.
     *
     * @param winner identifier for winning candidate
     * @param loser identifier for losing candidate
     * @return 1 if the given vote is a vote for 'loser' and 0 otherwise
     */
    fun rcv_lfunc_wo(winner: Int, loser: Int): Int {
        val rank_winner = get_vote_for(winner)
        val rank_loser = get_vote_for(loser)

        return when {
            rank_winner == 0 && rank_loser != 0 -> 1
            rank_winner != 0 && rank_loser != 0 && rank_loser < rank_winner -> 1
            else -> 0
        }
    }

//    def rcv_votefor_cand(self, contest_id: str, cand: str, remaining: list) -> int:
//        """
//        Check whether 'vote' is a vote for the given candidate in the context
//        where only candidates in 'remaining' remain standing.
//
//        Parameters:
//        -----------
//        contest_id: string
//            identifier of the contest used in the CVRs
//        cand: string
//            identifier for candidate
//        remaining: list
//            list of identifiers of candidates still standing
//
//        vote: dict of dicts
//
//        Returns:
//        --------
//        1 if the given vote for the contest counts as a vote for 'cand' and 0 otherwise. Essentially,
//        if you reduce the ballot down to only those candidates in 'remaining',
//        and 'cand' is the first preference, return 1; otherwise return 0.
//        """
//        if not cand in remaining:
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
     * @return 1 if the given vote for the contest counts as a vote for 'cand' and 0 otherwise. Essentially,
     * if you reduce the ballot down to only those candidates in 'remaining',
     * and 'cand' is the first preference, return 1; otherwise return 0.
     */
    fun rcv_votefor_cand(cand: Int, remaining: List<Int>): Int {
        if (cand !in remaining) {
            return 0
        }

        val rank_cand = get_vote_for(cand) ?: return 0
        if (rank_cand == 0) return 0

        for (altc in remaining) {
            if (altc == cand) {
                continue
            }
            val rank_altc = get_vote_for(altc)
            if (rank_altc != 0 && rank_altc <= rank_cand) {
                return 0
            }
        }
        return 1
    }
}

/////////////////////////////////////////////////////////////////////
// From SFDA2019_PrelimReport12VBMJustDASheets.raire
// TODO Colorado may be very diffrent

// 1
// Contest,339,4,15,16,17,18
// 339,99813_1_1,17
// 339,99813_1_3,16
// 339,99813_1_6,18,17,15,16
// ...

fun readRaireCvrs(filename: String): RaireCvrs {
    val path: Path = Paths.get(filename)
    val reader: Reader = Files.newBufferedReader(path)
    val parser = CSVParser(reader, CSVFormat.DEFAULT)
    val records = parser.iterator()

    // we expect the first line to be the number of contests
    val ncontests = records.next().get(0).toInt()
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
                contests.add(RaireCvrContest(contestId, choices, cvrs))
            }
            // start a new contest
            contestId = line.get(1).toInt()
            nchoices = line.get(2).toInt()
            choices = readVariableListOfInt(line,3)
            require(nchoices == choices.size)
            cvrs = mutableListOf()
        } else {
            val cid = line.get(0).toInt()
            val wtf = line.get(1)
            val rankedChoices = readVariableListOfInt(line, 2)
            require(cid == contestId)
            require(choices.containsAll(rankedChoices))
            cvrs.add(RaireCvr(cid, wtf, rankedChoices))
        }
    }
    if (cvrs.isNotEmpty()) {
        contests.add(RaireCvrContest(contestId, choices, cvrs))
    }

    return RaireCvrs(contests, filename)
}

private fun readVariableListOfInt(line: CSVRecord, startPos: Int): List<Int> {
    val result = mutableListOf<Int>()
    while (startPos + result.size < line.size()) {
        val s = line.get(startPos + result.size)
        if (s.isEmpty()) break
        result.add(line.get(startPos + result.size).toInt())
    }
    return result
}
