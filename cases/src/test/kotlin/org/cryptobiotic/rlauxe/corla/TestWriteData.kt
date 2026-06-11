package org.cryptobiotic.rlauxe.corla

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.auditcenter.Colorado2024AuditCenterInput
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CountyAudit
import org.cryptobiotic.rlauxe.persist.CountyContestData
import org.cryptobiotic.rlauxe.persist.CountyData
import org.cryptobiotic.rlauxe.persist.readCountyContestData
import org.cryptobiotic.rlauxe.persist.readCountyData
import org.cryptobiotic.rlauxe.testdataDir
import kotlin.test.Test

class TestWriteData {

    @Test
    fun testWriteCountyContestData() {
        val topdir = "$testdataDir/cases/corla/consistent"
        val countyAudit = AuditRecord.readWithResult("$topdir/audit").unwrap()
        val contestMap = countyAudit.contests.associate { it.contest.info().name to it }
        writeCountyContestData(topdir, contestMap, Colorado2024AuditCenterInput().countyContestTabs)

        val roundtrip: List<CountyContestData> = readCountyContestData("$topdir/${CountyAudit.countyContestDataFile}")
        roundtrip.forEach { println(it) }
    }

    @Test
    fun testWriteCountyData() {
        val topdir = "$testdataDir/cases/corla/consistent"
        writeCountyData(topdir, Colorado2024AuditCenterInput().strataMap.values.toList())

        val roundtrip: List<CountyData> = readCountyData("$topdir/${CountyAudit.countyDataFile}")
        roundtrip.forEach { println(it) }
    }
}