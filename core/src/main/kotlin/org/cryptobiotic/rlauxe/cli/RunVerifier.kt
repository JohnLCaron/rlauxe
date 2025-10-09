package org.cryptobiotic.rlauxe.cli


import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.verifier.VerifyAuditRecord

/** Run election record verification CLI. */
object RunVerifier {

    @JvmStatic
    fun main(args: Array<String>) {
        val parser = ArgParser("RunVerifier")
        val inputDir by parser.option(
            ArgType.String,
            shortName = "in",
            description = "Directory containing input election record"
        ).required()
        val nthreads by parser.option(
            ArgType.Int,
            shortName = "nthreads",
            description = "Number of parallel threads to use"
        ).default(11)
        val showTime by parser.option(
            ArgType.Boolean,
            shortName = "time",
            description = "Show timing"
        ).default(false)

        parser.parse(args)
        println("RunVerifier on $inputDir")
        runVerifier(inputDir, nthreads, showTime)
    }

    fun runVerifier(inputDir: String, nthreads: Int, showTime: Boolean = false): String {
        val verifier = VerifyAuditRecord(inputDir)
        return verifier.verify()
    }
}
