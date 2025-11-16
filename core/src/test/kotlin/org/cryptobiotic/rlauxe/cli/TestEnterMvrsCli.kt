package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test
import kotlin.test.assertFailsWith

// TODO: test that the sampleMvrs are correct
class TestEnterMvrsCli {

    @Test
    fun testEnterMvrsClca() {
        val auditDir = "src/test/data/workflow/testCliRoundClca/audit"
        EnterMvrsCli.main(
            arrayOf(
                "-in", auditDir,
                "-mvrs", "$auditDir/sortedCards.csv"
            )
        )
    }

    @Test
    fun testEnterMvrsError1() {
        val auditDir = "/my/bad/testPersistentWorkflowClca"

        assertFailsWith<IllegalArgumentException> {
            EnterMvrsCli.main(
                arrayOf(
                    "-in", auditDir,
                    "-mvrs", "$auditDir/sortedCards.csv"
                )
            )
        }
    }

    @Test
    fun testEnterMvrsError2() {
        val auditDir = "/home/stormy/rla/persist/testPersistentWorkflowClca"
        assertFailsWith<IllegalArgumentException> {
            EnterMvrsCli.main(
                arrayOf(
                    "-in", auditDir,
                    "-mvrs", "$auditDir/noexist.csv"
                )
            )
        }
    }
}