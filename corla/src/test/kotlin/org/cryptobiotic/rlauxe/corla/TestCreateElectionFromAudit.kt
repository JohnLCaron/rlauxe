package org.cryptobiotic.rlauxe.corla

import kotlin.test.Test

class TestCreateElectionFromAudit {

    @Test
    fun testReadColoradoElectionDetail() {
        val detailXmlFile = "src/test/data/2024election/detail.xml"
        val electionResultXml: ElectionDetailXml = readColoradoElectionDetail(detailXmlFile)
    }

    // use tabulate.csv for contests and votes, and round1/contests.csv (Nc)
    @Test
    fun testCreateElectionFromAudit() {
        val auditDir = "/home/stormy/temp/corla/election/"
        val tabulateFile = "src/test/data/2024audit/tabulate.csv"
        val contestRoundFile = "src/test/data/2024audit/round1/contest.csv"
        val detailXmlFile = "src/test/data/2024election/detail.xml"
        val precinctFile = "src/test/data/2024election/2024GeneralPrecinctLevelResults.zip"

        createElectionFromAudit(auditDir, tabulateFile, contestRoundFile, precinctFile)
    }

}