package org.cryptobiotic.rlauxe.sf

import org.cryptobiotic.rlauxe.corla.ElectionDetailXml
import kotlin.test.Test

class TestColoradoElectionFromAudit {

    @Test
    fun testReadColoradoElectionDetail() {
        val xmlFile = "src/test/data/SF2024/summary.xml"
        val electionResultXml: ElectionSummaryXml = readSf2024electionSummaryXML(xmlFile)
        println(electionResultXml)
    }


}