package org.cryptobiotic.cli

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFile
import org.cryptobiotic.rlauxe.util.makeDeciles
import org.cryptobiotic.rlauxe.workflow.readCardManifest
import kotlin.test.Test

class ShowPoolSizes {


    @Test
    fun showCardPools() {
        showCardPoolSizes("sf2024/oa", "$testdataDir/cases/sf2024/oa/audit")
        showCardPoolSizes("sf2024/oans", "$testdataDir/cases/sf2024/oans/audit")
        showCardPoolSizes("boulder24/oa", "$testdataDir/cases/boulder24/oa/audit")
        showCardPoolSizes("corla/oa", "$testdataDir/cases/corla/oneaudit/audit")
    }

    fun showCardPoolSizes(what: String, where: String) {
        val publisher = Publisher(where)

        val contestsResults = readContestsJsonFile(publisher.contestsFile())
        val allContests = if (contestsResults is Ok) contestsResults.unwrap().sortedBy { it.id } else null
        val infos = allContests?.map{ it.contest.info() }?.associateBy { it.id }

        val cardManifest = readCardManifest(publisher)
        val ncards = cardManifest.populations.map { it.ncards() }
        val deciles = makeDeciles(ncards)
        println(" $what ncards deciles = $deciles npools= ${cardManifest.populations.size}")
    }

}