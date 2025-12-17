package org.cryptobiotic.rlauxe.sf

import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.oneaudit.CardPoolFromCvrs
import org.cryptobiotic.rlauxe.persist.json.readCardPoolsJsonFile
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import kotlin.collections.component1
import kotlin.collections.component2

private val logger = KotlinLogging.logger("checkSfElectionTotals")

fun checkSfElectionTotals(
    auditDir: String,
    castVoteRecordZip: String,
    contestManifestFilename: String,
    summaryFile: String
) {

    val workflow = PersistedWorkflow(auditDir)
    val config = workflow.auditConfig()
    val contestsUA = workflow.contestsUA()
    val infos = contestsUA.associate { it.id to it.contest.info() }

    val publisher = workflow.publisher
    val cardPools = readCardPoolsJsonFile(publisher.cardPoolsFile(), infos).unwrap()
    val poolTabs = mutableMapOf<Int, ContestTabulation>()
    cardPools.forEach { pool ->
        (pool as CardPoolFromCvrs).addTo(poolTabs)
    }

    // need the non-pooled votes to be added

    // check against staxContests
    val contestManifest = readContestManifestFromZip(castVoteRecordZip, contestManifestFilename)
    println("IRV contests = ${contestManifest.irvContests}")

    val staxContests = StaxReader().read(summaryFile)
    println("staxContests")
    poolTabs.toSortedMap().forEach { (id, poolTab) ->
        val contestName = contestManifest.contests[id]!!.Description
        val staxContest: StaxReader.StaxContest = staxContests.find { it.id == contestName }!!
        if (staxContest.ncards() != poolTab.ncards) {
            logger.warn { "staxContest $contestName ($id) has ncards = ${staxContest.ncards()} not equal to cvr summary = ${poolTab.ncards} " }
            // assertEquals(staxContest.blanks(), contest.blanks)
        }
        println("  $contestName ($id) has stax ncards = ${staxContest.ncards()}, cvr ncards = ${poolTab.ncards}")
    }

}