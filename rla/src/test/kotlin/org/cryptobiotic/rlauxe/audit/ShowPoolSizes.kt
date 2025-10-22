package org.cryptobiotic.rlauxe.audit

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.readCardPoolsJsonFile
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFile
import org.cryptobiotic.rlauxe.util.makeDeciles
import kotlin.test.Test

class ShowPoolSizes {


    @Test
    fun showCardPools() {
        showCardPoolSizes("sf2024/oa", "/home/stormy/rla/cases/sf2024/oa/audit")
        showCardPoolSizes("sf2024/oans", "/home/stormy/rla/cases/sf2024/oans/audit")
        showCardPoolSizes("boulder24/oa", "/home/stormy/rla/cases/boulder24/oa/audit")
        showCardPoolSizes("corla/oa", "/home/stormy/rla/cases/corla/oneaudit/audit")
    }

    fun showCardPoolSizes(what: String, where: String) {
        val publisher = Publisher(where)

        val contestsResults = readContestsJsonFile(publisher.contestsFile())
        val allContests = if (contestsResults is Ok) contestsResults.unwrap().sortedBy { it.id } else null
        val infos = allContests?.map{ it.contest.info() }?.associateBy { it.id }

        val cardPools = readCardPoolsJsonFile(publisher.cardPoolsFile(), infos!!).unwrap()
        val ncards = cardPools.map { it.ncards() }
        val deciles = makeDeciles(ncards)
        println(" $what ncards deciles = $deciles npools= ${cardPools.size}")
    }

}