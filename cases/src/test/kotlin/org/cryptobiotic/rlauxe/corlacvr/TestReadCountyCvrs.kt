package org.cryptobiotic.rlauxe.corlacvr

import org.cryptobiotic.rlauxe.corlaInput.readCorlaCvrs
import kotlin.test.Test

class TestReadCountyCvrs {

    @Test
    fun testReadMorganRedactedCvrs() {
        val filename1 = "CVR_Export_20261113104631- Redacted- No balance.csv"
        val filename2 = "CVR_Export_20261113104631-Redacted.csv"
        val filename3 = "CVR_Export_20261113104631-Redacted-v0.4-rebuilt.csv"
        val corlaCvrs = readCountyCvrs("/home/joncifer/Downloads/$filename3")
        println(filename3)
        println(corlaCvrs.show())
        println(corlaCvrs.redaction().show(corlaCvrs.schema))

        val output = "/home/joncifer/Downloads/Morgan.anon.csv"
        val anon = Anonomicer(corlaCvrs) // , styleOnly=styleOnly)
        anon.writeRedactedFile(output)
        anon.rpt.writeTo("/home/joncifer/Downloads/Morgan-Report.txt")
        println("write and verify Redaction to $output")
    }

    @Test
    fun testReadLaPlataRedactedCvrs() {
        val filename1 = "Modified_CVR_Export_20261113122247.csv"
        val output = "/home/joncifer/Downloads/LaPlata.anon.csv"
        val corlaCvrs = readCountyCvrs("/home/joncifer/Downloads/$filename1")
        println(filename1)
        println(corlaCvrs.show())
        println(corlaCvrs.redaction().show(corlaCvrs.schema))

        val anon = Anonomicer(corlaCvrs) // , styleOnly=styleOnly)
        anon.writeRedactedFile(output)
        anon.rpt.writeTo("/home/joncifer/Downloads/LaPlata-Report.txt")
        println("write and verify Redaction to $output")
    }

    @Test
    fun testPythonOutput() {
        val filename = "/home/joncifer/rla/temp/python/Morgan-Redacted.csv"
        val corlaCvrs = readCountyCvrs(filename)
        println(filename)
        println(corlaCvrs.show())
        println(corlaCvrs.redaction().show(corlaCvrs.schema))
    }
}