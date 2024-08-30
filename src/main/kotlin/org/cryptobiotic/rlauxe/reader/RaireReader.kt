package org.cryptobiotic.rlauxe.reader

import org.cryptobiotic.rlauxe.raire.RaireContest
import java.io.File

import org.cryptobiotic.rlauxe.*


// Data file in .raire format.
// first is the list of contests
// second is a ballot (cvr) which is a map : contestId : candidateId : vote
fun readRaireBallots(fileName: String): Pair<List<RaireContest>, Map<String, MutableMap<String, MutableMap<String, Int>>>> {

//  A map between ballot id and the relevant CVR.
    val lines = File(fileName).bufferedReader().readLines()

//  Total number of contests described in data file
    var lineIndex = 0
    val ncontests = lines[lineIndex++].toInt()

//  Map between contest id and number of ballots involving that contest
    val num_ballots = mutableMapOf<String, Int>()

//  Map between contest id and the candidates & winner & order of that contest.
    val contest_info = mutableMapOf<String, ContestInfo>()

    repeat(ncontests) { contestIdx ->
        // toks = [line.strip() for line in lines[1 + i].strip().split(',')]
        // Contest,1,5,1,2,3,4,5,winner,4
        val toks: List<String> = lines[lineIndex++].split(",")

    //  Get contest id and number of candidates in that contest
        val cid = toks[1] // contest id
        val ncands = toks[2].toInt() // ncandidates

    //  Get list of candidate identifiers
        val cands = mutableListOf<String>()
        repeat(ncands) { cands.add(toks[3 + it]) }

        val windx = toks.indexOf("winner")
        val winner = if (windx >= 0) toks[windx + 1] else "N/A"

        val inf_index = toks.indexOf("informal")
        val informal = if (inf_index < 0) 0 else toks[inf_index + 1].toInt()

        val order = mutableListOf<Int>()
        if (toks.contains("order")) {
            if (inf_index < 0) for (tokidx in windx + 2 until toks.size) order.add(toks[tokidx].toInt())
            else for (tokidx in windx + 2 until inf_index) order.add(toks[tokidx].toInt())
        }

        contest_info[cid] = ContestInfo(cands, winner, order)
        num_ballots[cid] = informal
    }

    // TODO I dont see how this code works for more than one contest

    //         for l in range(ncontests+1,len(lines)):
    //            toks = [line.strip() for line in lines[l].strip().split(',')]
    //
    //            cid = toks[0]
    //            bid = toks[1]
    //            prefs = toks[2:]
    //
    //            ballot = {}
    //            for c in contest_info[cid][0]:
    //                if c in prefs:
    //                    idx = prefs.index(c)
    //                    ballot[c] = idx
    //
    //            num_ballots[cid] += 1
    //
    //            if not bid in cvrs:
    //                cvrs[bid] = {cid: ballot}
    //            else:
    //                cvrs[bid][cid] = ballot

    val ballots = mutableMapOf<String, MutableMap<String, MutableMap<String, Int>>>() //  ballotId -> contestId -> candidate -> Preference rank
    while (lineIndex < lines.size) {
        // 1,1,4,1,2,3
        // 1,2,4,1,2,3
        // ...
        val toks: List<String> = lines[lineIndex++].split(",")
        // toks = [line.strip() for line in lines[l].strip().split(',')]

        val cid = toks[0] // contest id
        val bid = toks[1] // ballot id
        val prefs = toks.subList(2, toks.size) // remaining "order"

        val votes = mutableMapOf<String, Int>() // candidate to preference rank
        for (candidate in contest_info[cid]!!.candidates) {
            if (candidate in prefs) {
                votes[candidate] = prefs.indexOf(candidate) + 1  // LOOK: 1 based
            }
        }
        val ncid = num_ballots[cid]!!
        num_ballots[cid] = ncid + 1

        val conBallotMap = ballots.getOrPut(bid) { mutableMapOf() }
        conBallotMap[cid] = votes
    }

    val raireContests = mutableListOf<RaireContest>()

    for ((cid, trip) in contest_info) {
            val (cands, winner, order) = trip
            val con = RaireContest(cid, cands, winner, num_ballots[cid]!!, outcome = IntArray(order.size) { order[it] })
            raireContests.add(con)
        }

    // TODO translate cvrs into immutables
    return Pair(raireContests, ballots)
}

data class ContestInfo(val candidates: List<String>, val winner: String, val order: List<Int>)

fun showRaireBallots(
    raireBallots: Pair<List<RaireContest>, Map<String, MutableMap<String, MutableMap<String, Int>>>>,
    limit: Int = Integer.MAX_VALUE
) {
    val (raireContests, ballots) = raireBallots
    println("RaireContests ${raireContests}")
    println("Cvrs Records")

    // Map<String, MutableMap<String, MutableMap<String, Int>>> ballotId: contestId : candidateId : count
    var ballotIter = ballots.entries.iterator()
    for (idx in 0..limit) {
        if (!ballotIter.hasNext()) break
        val (ballotId, cvr) = ballotIter.next()

        println(buildString {
            append(" Ballot '$ballotId'= ")
            for ((contId, mm2) in cvr) {
                append("Contest '$contId': ")
                for ((candId, pref) in mm2) {
                    append("'$candId':$pref, ")
                }
            }
        })
    }
    if (ballotIter.hasNext()) println(" ...")
}
