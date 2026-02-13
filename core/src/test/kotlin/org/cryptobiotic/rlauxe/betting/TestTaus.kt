package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import kotlin.test.Test
import kotlin.test.*

class TestTaus {

    @Test
    fun testPlurality() {
        val taus = Taus(1.0)
        println(taus)
        println("names = ${taus.names()}")
        println("values = ${taus.values()}")
        assertEquals("[p2o, p1o, noerror, p1u, p2u]", taus.names().toString())

        taus.names().forEach { name ->
            val v = taus.valueOf(name)
            val trip = taus.nameOf(v)
            assertEquals(name, trip)
        }

        taus.values().forEach { v ->
            val name = taus.nameOf(v)
            val trip = taus.valueOf(name)
            assertEquals(v, trip)
        }

        taus.values().forEach { v ->
            val isClcaError = taus.isClcaError(v)
            val name = taus.nameOf(v)
            assertEquals(name != "noerror", isClcaError)
        }

        taus.values().forEach { v ->
            val isPhantom = taus.isPhantom(v)
            val name = taus.nameOf(v)
            assertEquals(name == "p1o", isPhantom)
        }
    }

    @Test
    fun testPluralityOverride() {
        val taus = Taus(1.0, use7override = true)
        println(taus)
        println("names = ${taus.names()}")
        println("values = ${taus.values()}")
        assertEquals("[win-los, win-oth, oth-los, noerror, oth-win, los-oth, los-win]", taus.names().toString())

        taus.names().forEach { name ->
            val v = taus.valueOf(name)
            val trip = taus.nameOf(v)
            if (name != trip) println("$trip confused with $name")
        }

        taus.values().forEach { v ->
            val name = taus.nameOf(v)
            val trip = taus.valueOf(name)
            if (v != trip) println("$trip confused with $v")
        }

        taus.values().forEach { v ->
            val isClcaError = taus.isClcaError(v)
            val name = taus.nameOf(v)
            assertEquals(name != "noerror", isClcaError)
        }

        taus.values().forEach { v ->
            val isPhantom = taus.isPhantom(v)
            val name = taus.nameOf(v)
            assertEquals(name == "oth-los", isPhantom)
        }
    }

   //  @Test
    fun testUpperNotOne() {
        val tausNotOne = Taus(1.1)
        println(tausNotOne)
        println("names = ${tausNotOne.names()}")
        println("values = ${tausNotOne.values()}")

        assertEquals("[win-los, win-oth, oth-los, noerror, los-oth, oth-win, los-win]", tausNotOne.names().toString())

        tausNotOne.names().forEach { name ->
            val v = tausNotOne.valueOf(name)
            val trip = tausNotOne.nameOf(v)
            assertEquals(name, trip)
        }

        tausNotOne.values().forEach { v ->
            val name = tausNotOne.nameOf(v)
            val trip = tausNotOne.valueOf(name)
            assertEquals(v, trip)
        }

        tausNotOne.values().forEach { v ->
            val isClcaError = tausNotOne.isClcaError(v)
            val name = tausNotOne.nameOf(v)
            assertEquals(name != "noerror", isClcaError)
        }

        tausNotOne.values().forEach { v ->
            val isPhantom = tausNotOne.isPhantom(v)
            val name = tausNotOne.nameOf(v)
            assertEquals(name == "oth-los", isPhantom)
        }
    }

    @Test
    fun testTausRates() {
        val config = AuditConfig(AuditType.CLCA)
        val apriori = config.clcaConfig.apriori.makeErrorCounts(42, .542, 1.0)
        assertTrue(apriori.errorRates().isEmpty())
    }

}