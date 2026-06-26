package org.cryptobiotic.create

import org.cryptobiotic.rlauxe.audit.runAllRoundsAndVerify
import org.cryptobiotic.rlauxe.testdataDir
import org.junit.jupiter.api.Test

class TestRunAllRoundsAllUseCases {
    @Test
    fun runBoulder24oa() {
        val topdir = "$testdataDir/cases/boulder24/oa"
        runAllRoundsAndVerify(topdir)
    }

    @Test
    fun runBoulder24clca() { // simulate CVRs
        val topdir = "$testdataDir/cases/boulder24/clca"
        runAllRoundsAndVerify("$topdir")
    }

    @Test
    fun runColoradoClca() {
        val topdir = "$testdataDir/cases/corla/clca"
        runAllRoundsAndVerify("$topdir")
    }

    @Test
    fun runColoradoPolling() {
        val topdir = "$testdataDir/cases/corla/polling"
        runAllRoundsAndVerify("$topdir")
    }

    @Test
    fun runSFElectionOASP() {
        val topdir = "$testdataDir/cases/sf2024/oasp"
        runAllRoundsAndVerify("$topdir")
    }

    @Test
    fun runSFElectionOA() {
        val topdir = "$testdataDir/cases/sf2024/oa"
        runAllRoundsAndVerify("$topdir")
    }

    @Test
    fun runSFElectionClca() {
        val topdir = "$testdataDir/cases/sf2024/clca"
        runAllRoundsAndVerify("$topdir")
    }
}