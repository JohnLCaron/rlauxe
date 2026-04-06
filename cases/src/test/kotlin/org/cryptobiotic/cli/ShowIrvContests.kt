package org.cryptobiotic.cli

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.CardWithBatchName
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.dominion.CvrExportToCardAdapter
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvFile
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvIterator
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFile
import org.cryptobiotic.rlauxe.irv.IrvCount
import org.cryptobiotic.rlauxe.irv.IrvContest
import org.cryptobiotic.rlauxe.irv.VoteConsolidator
import org.cryptobiotic.rlauxe.irv.showIrvCountResult
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test

class ShowIrvContests {

    @Test
    fun showRaireContests() {
        val stopwatch = Stopwatch()

        val topDir = "$testdataDir/cases/sf2024"
        val publisher = Publisher("$topDir/clca/audit")
        val contestsResults = readContestsJsonFile(publisher.contestsFile())
        val contestsUA = if (contestsResults .isOk) contestsResults.unwrap()
        else throw RuntimeException("Cannot read contests from ${publisher.contestsFile()} err = $contestsResults")

        val irvCounters = mutableListOf<IrvCounter>()
        contestsUA.filter { it.isIrv }.forEach { contestUA ->
            irvCounters.add(IrvCounter(contestUA.contest as IrvContest))
        }

        val cvrCsv = "$topDir/$cvrExportCsvFile"
        val cardIter = CvrExportToCardAdapter(cvrExportCsvIterator(cvrCsv), null, false)
        var count = 0
        while (cardIter.hasNext()) {
            irvCounters.forEach { it.addCvr(cardIter.next())}
            count++
        }
        println("processed $count cvrs $stopwatch")

        irvCounters.forEach { counter ->
            println("${counter.rcontest}")
            val votes = counter.vc.makeVotes(counter.rcontest.info.candidateIds.size)
            val irvCount = IrvCount(votes.votes, counter.rcontest.info.candidateIds)
            showIrvCount(counter.rcontest, irvCount)
        }
        println("showIrvCounts took $stopwatch")
    }
}

data class IrvCounter(val rcontest: IrvContest) {
    val vc = VoteConsolidator()
    val contestId = rcontest.id

    fun addCvr( cvr: CardWithBatchName) {
        val votes = cvr.votes!![contestId]
        if (votes != null) {
            vc.addVote(votes)
        }
    }
}

fun showIrvCount(rcontest: IrvContest, irvCount: IrvCount) {
    val roundResult = irvCount.runRounds()
    println(showIrvCountResult(roundResult, rcontest.info))
    println("================================================================================================\n")
}