package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.corlaInput.Colorado2026Primary
import org.cryptobiotic.rlauxe.corlaInput.auditcenter
import kotlin.test.Test
import kotlin.test.assertEquals

// turns out this isnt the canonical state manifest because its been sorted lexically on batchId.
// Also Broomfield is alphabetic.  too bad, not very useful.
class TestStateManifest {

    @Test
    fun testAgainstManifestCompareFile() {
        val stateInput = Colorado2026Primary()
        val stateManifest = stateInput.statewideManifest
        val stateBatches = stateManifest.batchInterator()
        val mcs: List<ManifestCompare> = ManifestCompare.readFromFile("$auditcenter/2026/primary/finalReports/CountyBallotManifestToCVRcomparison.csv")
        val mcsIter = mcs.iterator()

        while (stateBatches.hasNext()) {
            val stateBatch = stateBatches.next()
            if (!mcsIter.hasNext()) throw RuntimeException("ran out")
            val mc = mcsIter.next()
            assertEquals(mc.countyName, stateBatch.countyName)
            assertEquals(mc.scannerId, stateBatch.tabulatorNum)
            assertEquals(mc.batchId, stateBatch.batchId)
            assertEquals(mc.countPerManifest, stateBatch.nballotCards)
            println(mc)
        }
    }
}