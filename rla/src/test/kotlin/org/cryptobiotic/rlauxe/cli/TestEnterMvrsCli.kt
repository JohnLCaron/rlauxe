package org.cryptobiotic.rlauxe.cli

import kotlin.test.Test

// TODO this messes up the real audit info
class TestEnterMvrsCli {

    @Test
    fun testEnterMvrsClca() {
        val auditDir = "/home/stormy/temp/persist/testPersistentWorkflowClca"
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
        val auditDir = "/home/stormy/temp/persist/testPersistentWorkflowClca"
        EnterMvrsCli.main(
            arrayOf(
                "-in", auditDir,
                "-mvrs", "$auditDir/noexist.csv"
            )
        )
    }
}