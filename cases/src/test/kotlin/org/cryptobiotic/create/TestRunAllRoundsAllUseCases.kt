package org.cryptobiotic.create

import org.cryptobiotic.rlauxe.audit.runAllRoundsAndVerify
import org.cryptobiotic.rlauxe.cases
import kotlin.test.Test

class TestRunAllRoundsAllUseCases {
    @Test
    fun runBoulder24oa() {
        val topdir = "$cases/boulder/boulder2024/oa"
        runAllRoundsAndVerify(topdir)
    }

    @Test
    fun runBoulder24clca() { // simulate CVRs
        val topdir = "$cases/boulder/boulder2024/clca"
        runAllRoundsAndVerify("$topdir")
    }

    @Test
    fun runColoradoClca() {
        val topdir = "$cases/corla/clca"
        runAllRoundsAndVerify("$topdir")
    }

    @Test
    fun runColoradoPolling() {
        val topdir = "$cases/corla/polling"
        runAllRoundsAndVerify("$topdir")
    }

    @Test
    fun runSFElectionOASP() {
        val topdir = "$cases/sf2024/oasp"
        runAllRoundsAndVerify("$topdir")
    }

    @Test
    fun runSFElectionOA() {
        val topdir = "$cases/sf2024/oa"
        runAllRoundsAndVerify("$topdir")
    }

    @Test
    fun runSFElectionClca() {
        val topdir = "$cases/sf2024/clca"
        runAllRoundsAndVerify("$topdir")
    }
}