package org.cryptobiotic.rlauxe.cli

import org.cryptobiotic.rlauxe.persist.auditdir
import org.cryptobiotic.rlauxe.testdataDir
import kotlin.test.Test
import kotlin.test.assertFailsWith

// TODO: test that the sampleMvrs are correct
class TestEnterMvrsCli {

    @Test
    fun testEnterMvrsClca() {
        val topdir = "src/test/data/testRunCli/clca"
        EnterMvrsCli.main(
            arrayOf(
                "-in", topdir,
                "-mvrs", "$topdir/$auditdir/private/sortedMvrs.csv"
            )
        )
    }

    @Test
    fun testEnterMvrsError1() {
        val topdir = "/my/bad/testPersistentWorkflowClca"

        assertFailsWith<RuntimeException> {
            EnterMvrsCli.main(
                arrayOf(
                    "-in", topdir,
                    "-mvrs", "$topdir/sortedCards.csv"
                )
            )
        }
    }

    @Test
    fun testEnterMvrsError2() {
        val topdir = "$testdataDir/persist/testPersistentWorkflowClca"
        assertFailsWith<RuntimeException> {
            EnterMvrsCli.main(
                arrayOf(
                    "-in", topdir,
                    "-mvrs", "$topdir/noexist.csv"
                )
            )
        }
    }
}