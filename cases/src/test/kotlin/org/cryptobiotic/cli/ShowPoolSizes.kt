package org.cryptobiotic.cli

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFile
import org.cryptobiotic.rlauxe.estimateOld.makeDeciles
import org.cryptobiotic.rlauxe.workflow.readCardPools
import kotlin.test.Test

class ShowPoolSizes {

    @Test
    fun showCardPools() {
        showCardPoolSizes("sf2024/oa", "$cases/sf/sf2024/oa")
        showCardPoolSizes("sf2024/oans", "$cases/sf/sf2024/oans")
        showCardPoolSizes("boulder24/oa", "$cases/boulder/boulder24/oa")
        // showCardPoolSizes("corla/oa", "$cases/corla/corla2024/oneaudit")
    }

    fun showCardPoolSizes(what: String, topdir: String) {
        val publisher = Publisher(topdir)

        val contestsResults = readContestsJsonFile(publisher.contestsFile())
        val allContests = if (contestsResults .isOk) contestsResults.unwrap().sortedBy { it.id } else return
        val infos = allContests.map{ it.contest.info() }.associateBy { it.id }

        val pools = readCardPools(publisher, infos)!!
        val ncards = pools.map { it.ncards() }
        val deciles = makeDeciles(ncards)
        println(" $what ncards deciles = $deciles npools= ${pools.size}")
    }

}