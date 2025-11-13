package org.cryptobiotic.rlauxe.cli

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.csv.AuditableCardCsvReader
import org.cryptobiotic.rlauxe.persist.existsOrZip
import org.cryptobiotic.rlauxe.util.ErrorMessages
import java.nio.file.Files.notExists
import java.nio.file.Path

private val logger = KotlinLogging.logger("EnterMvrsCli")

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

fun enterMvrs(inputDir: String, mvrFile: String): Result<Boolean, ErrorMessages> {
    val errs = ErrorMessages("enterMvrs")

    if (notExists(Path.of(inputDir))) {
        return errs.add("EnterMvrsCli Audit Directory $inputDir does not exist")
    }
    if (!existsOrZip(mvrFile)) {
        return errs.add("EnterMvrsCli Mvrs file $mvrFile does not exist")
    }

    val result = AuditRecord.readFromResult(inputDir)
    if (result is Err) return result

    val auditRecord = result.unwrap()
    val mvrs = AuditableCardCsvReader(mvrFile)

    if (!auditRecord.enterMvrs(mvrs, errs)) {
        return Err(errs)
    }
    return Ok(true)
}