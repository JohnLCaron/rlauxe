package org.cryptobiotic.rlauxe.corlaCounty

import org.cryptobiotic.rlauxe.util.Locations.cases
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import kotlin.test.Test

class TestCorlaCountyElection {

    @Test
    fun testCorlaCountyPools() {
        val topdir = "$cases/corlaCounty/pitkin20/onepool"

        val auditRecord = AuditRecord.read(topdir) as AuditRecord
        val mvrManager = PersistedMvrManager(auditRecord, false)
        val pools = mvrManager.pools()
        if (pools != null)
            pools.forEach { println(it) }

    }
}