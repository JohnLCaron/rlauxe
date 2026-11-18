package org.cryptobiotic.rlauxe.corlacvr

import org.cryptobiotic.rlauxe.corlaInput.readCorlaCvrs
import kotlin.test.Test

class TestReadCountyCvrs {

    @Test
    fun testReadMorganRedactedCvrs() {
        val filename = "/home/joncifer/rla/cases/corla/2026/cvrs/CVR_Export_20261113104631-Morgan-redacted.csv"
        val corlaCvrs = readCountyCvrs(filename)
        println(filename)
        println(corlaCvrs.show())
        println(corlaCvrs.redaction().show(corlaCvrs.schema))

        /* val output = "/home/joncifer/Downloads/Morgan.anon.csv"
        val anon = Anonomicer(corlaCvrs) // , styleOnly=styleOnly)
        anon.writeRedactedFile(output)
        anon.rpt.writeTo("/home/joncifer/Downloads/Morgan-Report.txt")
        println("write and verify Redaction to $output") */
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
        val filename = "/home/joncifer/rla/cases/corla/2026/cvrs/Morgan-unredact-redacted.csv"
        val corlaCvrs = readCountyCvrs(filename)
        println(filename)
        println(corlaCvrs.show())
        println(corlaCvrs.redaction().show(corlaCvrs.schema))
    }
    // verify_redaction.py /home/joncifer/rla/cases/corla/2026/cvrs/Morgan-unredact.csv /home/joncifer/rla/cases/corla/2026/cvrs/Morgan-unredact-redacted.csv
}