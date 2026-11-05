package org.cryptobiotic.rlauxe.corlaCounty

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.util.Locations.cases
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import kotlin.test.Test

class TestCorlaCountyElection {

    @Test
    fun testCorlaCountyPools() {
        val topdir = "$cases/corla/2020/county/Pitkin"

        val auditRecord = AuditRecord.readPartialResult(topdir).unwrap()
        val mvrManager = PersistedMvrManager(auditRecord, false)
        val pools = mvrManager.pools()
        if (pools != null)
            pools.forEach { println(it) }

    }
}