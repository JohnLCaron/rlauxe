package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.core.Cvr
import java.io.File

data class ContestInfo(val candidates: List<String>, val winner: String, val order: List<Int>)

// Data file in .raire format.
// first is the list of contests
// second is a ballot (cvr) which is a map : contestId : candidateId : vote
fun readRaireBallots(fileName: String): RaireCvrs {

//  A map between ballot id and the relevant CVR.
    val lines = File(fileName).bufferedReader().readLines()

//  Total number of contests described in data file
    var lineIndex = 0
    val ncontests = lines[lineIndex++].toInt()

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
        val winner = if (windx >= 0) toks[windx + 1]
                    else "-1"

        val inf_index = toks.indexOf("informal")
        //val informal = if (inf_index < 0) 0 else toks[inf_index + 1].toInt()
        //num_ballots[cid] = informal // wtf??

        val order = mutableListOf<Int>()
        if (toks.contains("order")) {
            if (inf_index < 0) for (tokidx in windx + 2 until toks.size) order.add(toks[tokidx].toInt())
            else for (tokidx in windx + 2 until inf_index) order.add(toks[tokidx].toInt())
        }

        contest_info[cid] = ContestInfo(cands, winner, order)
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


    //  Map between contest id and number of ballots involving that contest
    val ncvrsMap = mutableMapOf<Int, Int>()

    val ballots = mutableMapOf<String, MutableMap<Int, IntArray>>() //  cvrId -> contestId -> candidate ranks
    while (lineIndex < lines.size) {
        // 1,1,4,1,2,3
        // 1,2,4,1,2,3
        // ...
        val toks: List<String> = lines[lineIndex++].split(",")
        val contestNumber = toks[0].toInt()
        val cvrId = toks[1]
        val ranks = toks.subList(2, toks.size) // remaining "order"

        val votes = mutableListOf<Int>() // candidate to preference rank
        for (token in ranks) {
            if (!token.isEmpty()) votes.add(token.toInt())
        }

        val conBallotMap = ballots.getOrPut(cvrId) { mutableMapOf() }
        conBallotMap[contestNumber] = votes.toIntArray()

        val ncvrs = ncvrsMap.getOrPut(contestNumber) { 0 }
        ncvrsMap[contestNumber] = ncvrs + 1
    }
    val cvrs = ballots.map { (key, votes) -> Cvr(key, votes) }

    val raireContests = mutableListOf<RaireCvrContest>()
    for ((cid, trip) in contest_info) {
        val (cands, winner, order) = trip // TODO what is order ?
        val contestNumber = cid.toInt()
        /*     val contestNumber: Int, val candidates: List<Int>, val ncvrs: Int, val winner: Int = -1, */
        val con = RaireCvrContest(
            contestNumber,
            cands.map { it.toInt() },
            ncvrsMap[contestNumber]!!,
            winner.toInt(),
            )
        raireContests.add(con)
    }

    return RaireCvrs(raireContests, cvrs, fileName)
}

/*
fun showRaireBallots(
    raireBallots: RaireCvrs,
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

 */
