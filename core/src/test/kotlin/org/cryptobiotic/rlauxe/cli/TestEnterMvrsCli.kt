package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

class TestEnterMvrsCli {

    @Test
    fun testEnterMvrsClca() {
        val auditDir = "/home/stormy/rla/persist/testPersistentWorkflowClca"
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
        EnterMvrsCli.main(
            arrayOf(
                "-in", auditDir,
                "-mvrs", "$auditDir/sortedCards.csv"
            )
        )
    }

    @Test
    fun testEnterMvrsError2() {
        val auditDir = "/home/stormy/rla/persist/testPersistentWorkflowClca"
        EnterMvrsCli.main(
            arrayOf(
                "-in", auditDir,
                "-mvrs", "$auditDir/noexist.csv"
            )
        )
    }
}