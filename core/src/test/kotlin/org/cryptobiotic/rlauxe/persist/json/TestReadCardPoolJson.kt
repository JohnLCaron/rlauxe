package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.persist.Publisher
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals

class TestReadCardPoolJson {

    @Test
    fun testRoundtrip() {
        val auditDir = "src/test/data/workflow/testCliRoundOneAudit/audit"
        val publisher = Publisher(auditDir)
        val poolFile = publisher.cardPoolsFile()
        val contests = readContestsJsonFile(publisher.contestsFile()).unwrap()
        val infos = contests.map { it.contest.info()}.associateBy { it.id }

        val pools = readCardPoolsJsonFile(poolFile, infos).unwrap()
        println("read ${pools.size} pools (original")

        val scratchFile = createTempFile().toFile()

        writeCardPoolsJsonFile(pools, scratchFile.toString())

        val roundtrip = readCardPoolsJsonFile(scratchFile.toString(), infos).unwrap()
        println("read ${roundtrip.size} pools (roundtrip")
        assertEquals(pools.toSet(), roundtrip.toSet())

        scratchFile.delete()
    }


}