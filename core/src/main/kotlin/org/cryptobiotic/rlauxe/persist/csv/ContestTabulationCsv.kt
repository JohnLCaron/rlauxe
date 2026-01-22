package org.cryptobiotic.rlauxe.persist.csv

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.raire.VoteConsolidator
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.ContestTabulation
import java.io.*
import kotlin.text.isEmpty
import kotlin.text.split

private val logger = KotlinLogging.logger("ContestTabulationCsv")

// class ContestTabulation(val contestId: Int, val voteForN: Int, val isIrv: Boolean, val candidateIdToIndex: Map<Int, Int>): RegVotesIF {
//    override val votes = mutableMapOf<Int, Int>() // cand -> votes
//    val irvVotes = VoteConsolidator() // candidate indexes
//    val notfound = mutableMapOf<Int, Int>() // candidate -> nvotes; track candidates on the cvr but not in the contestInfo, for debugging
//
//    var ncards = 0 // TODO should be "how many cards are in the population"?
//    var novote = 0  // how many cards had no vote for this contest?
//    var undervotes = 0  // how many undervotes = voteForN - nvotes
//    var overvotes = 0  // how many overvotes = (voteForN < cands.size)
//    var nphantoms = 0  // how many overvotes = (voteForN < cands.size)

val ContestTabulationHeader = "contestId, voteForN, cands, ncards, novote, undervotes, overvotes, nphantoms, isIrv, cand:count ... \n"

fun writeContestTabulationCsv(tab: ContestTabulation) = buildString {
    val sortedCandIds = tab.candidateIdToIndex.toList().sortedBy { it.second }.map { it.first }

    append("${tab.contestId}, ${tab.voteForN}, ${sortedCandIds.joinToString(" ")}, ${tab.ncards}, ${tab.novote}, ")
    append("${tab.undervotes}, ${tab.overvotes}, ${tab.nphantoms}, ${if (tab.isIrv) "yes" else ""}, ")

    if (tab.isIrv) {
        tab.irvVotes.votes().forEach { (votes, count) -> append("${votes.joinToString(" ")}:$count, ") }
    } else {
        tab.votes.forEach { (cand, vote) -> append("$cand:$vote, ") }
    }
    appendLine()
}

fun writeContestTabulationCsvFile(tabs: List<ContestTabulation>, outputFilename: String) {
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
    writer.write(ContestTabulationHeader)
    tabs.forEach {
        writer.write(writeContestTabulationCsv(it))
    }
    writer.close()
}

////////////////////////////////////////////////////////////////////////////

fun readContestTabulationCsv(ttokens: List<String>): ContestTabulation {
    var idx = 0
    val contestId = ttokens[idx++].toInt()
    val voteForN = ttokens[idx++].toInt()
    val candsToken = ttokens[idx++]
    val ncards = ttokens[idx++].toInt()
    val novote = ttokens[idx++].toInt()
    val undervotes = ttokens[idx++].toInt()
    val overvotes = ttokens[idx++].toInt()
    val nphantoms = ttokens[idx++].toInt()
    val isIrv = ttokens[idx++] == "yes"

    val candIdToIndex = if (!isIrv) emptyMap() else {
        val cands: List<Int> = candsToken.split(" ").map { it.trim().toInt() }
        cands.mapIndexed { idx, id -> Pair(id, idx) }.toMap()
    }

    val tab = ContestTabulation(contestId, voteForN, isIrv, candIdToIndex)
    tab.ncards = ncards
    tab.novote = novote
    tab.undervotes = undervotes
    tab.overvotes = overvotes
    tab.nphantoms = nphantoms

    if (!isIrv) {
        val votes = mutableMapOf<Int, Int>()
        while (idx < ttokens.size) {
            val tokens = ttokens[idx]
            if (tokens.isEmpty()) break
            val vtokens = tokens.split(":")
            val cand = vtokens[0].toInt()
            val vote = vtokens[1].toInt()
            votes[cand] = vote
            idx++
        }
        tab.votes.putAll(votes)

    } else {
        val vc = tab.irvVotes
        while (idx < ttokens.size) {
            val tokens = ttokens[idx]
            if (tokens.isEmpty()) break
            val vtokens = tokens.split(":")
            val candsToken = vtokens[0] // .remove("[]")
            val candArray =
                if (candsToken.isEmpty()) intArrayOf() else candsToken.split(" ").map { it.trim().toInt() }
                    .toIntArray()
            val count = vtokens[1].toInt()
            vc.addVotes(candArray, count)
            idx++
        }
        tab.irvVotes
    }

    return tab
}

fun readContestTabulationCsv(line: String): ContestTabulation {
    val tokens = line.split(",")
    val ttokens = tokens.map { it.trim() }
    return(readContestTabulationCsv(ttokens))
}

fun readContestTabulationCsvFile(filename: String): List<ContestTabulation> {
    val reader: BufferedReader = File(filename).bufferedReader()
    reader.readLine() // get rid of header line

    val cards = mutableListOf<ContestTabulation>()
    while (true) {
        val line = reader.readLine() ?: break
        cards.add(readContestTabulationCsv(line))
    }
    reader.close()
    return cards
}



