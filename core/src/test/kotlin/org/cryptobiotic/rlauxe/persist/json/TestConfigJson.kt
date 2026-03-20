package org.cryptobiotic.rlauxe.persist.json

import org.cryptobiotic.rlauxe.audit.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import kotlin.test.assertNotNull

class TestConfigJson {

    @Test
    fun testRoundtrip() {
        testRoundtrips(Config.from(AuditType.CLCA).replaceSeed(12356667890L))
        testRoundtrips(Config.from(AuditType.POLLING))
        testRoundtrips(Config.from(AuditType.ONEAUDIT))

        testRoundtrips(
            Config.from(AuditType.CLCA, riskLimit=.03, nsimEst=42, simFuzzPct=.111, contestSampleCutoff=10000)
        )

        testRoundtrips(
            Config.from(
                AuditType.POLLING, riskLimit=.03, nsimEst=42, simFuzzPct=.111, contestSampleCutoff=10000
            )
        )
        testRoundtrips(
            Config.from(
                AuditType.ONEAUDIT, riskLimit=.03, nsimEst=42, simFuzzPct=.111,
                contestSampleCutoff=10000,
            )
        )
    }

    fun testRoundtrips(target: Config) {
        testRoundtrip(target)
        testRoundtripIO(target)
    }

    fun testRoundtrip(target: Config) {
        val roundtripElection = testElectionRoundtrip(target)
        val roundtripCreation = testCreationRoundtrip(target)
        val roundtripRound = testRoundRoundtrip(target)
        val roundtrip = Config(roundtripElection, roundtripCreation, roundtripRound) // hmm no version
        assertNotNull(roundtrip)
        assertEquals(roundtrip, target)
        assertTrue(roundtrip.equals(target))
        println("testRoundtrip for ${target.election.electionName} ${target.auditType}")
    }

    fun testElectionRoundtrip(target: Config): ElectionInfo {
        val json = target.election.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(roundtrip, target.election)
        assertTrue(roundtrip.equals(target.election))
        return roundtrip
    }

    fun testCreationRoundtrip(target: Config): AuditCreationConfig {
        val json = target.creation.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(roundtrip, target.creation)
        assertTrue(roundtrip.equals(target.creation))
        return roundtrip
    }

    fun testRoundRoundtrip(target: Config): AuditRoundConfig {
        val json = target.round.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(roundtrip, target.round)
        assertTrue(roundtrip.equals(target.round))
        return roundtrip
    }

    fun testRoundtripIO(target: Config) {
        val scratchFile = kotlin.io.path.createTempFile().toFile()
        writeElectionInfoJsonFile(target.election, "$scratchFile.election.json")
        writeAuditCreationConfigJsonFile(target.creation, "$scratchFile.creation.json")
        writeAuditRoundConfigJsonFile(target.round, "$scratchFile.round.json")

        val electionrt = readElectionInfoUnwrapped("$scratchFile.election.json")
        assertNotNull(electionrt)
        val creationrt = readAuditCreationConfigUnwrapped("$scratchFile.creation.json")
        assertNotNull(creationrt)
        val roundrt = readAuditRoundConfigUnwrapped("$scratchFile.round.json")
        assertNotNull(roundrt)

        val roundtrip = Config(electionrt, creationrt, roundrt) // hmm no version

        assertEquals(roundtrip, target)
        assertTrue(roundtrip.equals(target))

        scratchFile.delete()
        println("testRoundtripIO for ${target.election.electionName} ${target.auditType}")
    }
}