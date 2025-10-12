package org.cryptobiotic.rlauxe.cli


import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.csv.readAuditableCardCsvFile
import java.nio.file.Files.notExists
import java.nio.file.Path

/** Enter real Mvrs from a file. */
object EnterMvrsCli {

    @JvmStatic
    fun main(args: Array<String>) {
        val parser = ArgParser("EnterMvrsCli")
        val inputDir by parser.option(
            ArgType.String,
            shortName = "in",
            description = "Directory containing input election record"
        ).required()
        val mvrFile by parser.option(
            ArgType.String,
            shortName = "mvrs",
            description = "File containing new Mvrs for latest round"
        ).required()

        parser.parse(args)
        println("EnterMvrs from audit in $inputDir with mvrFile=$mvrFile")
        enterMvrs(inputDir, mvrFile)
    }
}

fun enterMvrs(inputDir: String, mvrFile: String): Boolean {
    if (notExists(Path.of(inputDir))) {
        println("EnterMvrsCli Audit Directory $inputDir does not exist")
        return false
    }
    if (notExists(Path.of(mvrFile))) {
        println("EnterMvrsCli Mvrs file $mvrFile does not exist")
        return false
    }

    val auditRecord: AuditRecord = AuditRecord.readFrom(inputDir)
    val mvrs = readAuditableCardCsvFile(mvrFile)
    return auditRecord.enterMvrs(mvrs)
}