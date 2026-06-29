package org.cryptobiotic.rlauxe.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.auditcenter.makeCorlaElectionClca
import org.cryptobiotic.rlauxe.auditcenter.makeCorlaElectionUniform
import org.cryptobiotic.rlauxe.belgium.createAllBelgiumElections
import org.cryptobiotic.rlauxe.boulder.makeBoulderElectionClca
import org.cryptobiotic.rlauxe.boulder.makeBoulderElectionOA
import org.cryptobiotic.rlauxe.sf.createCvrExportCsvFile
import org.cryptobiotic.rlauxe.sf.makeSFElectionClca
import org.cryptobiotic.rlauxe.sf.makeSFElectionOA

object CreateCaseData {

    @JvmStatic
    fun main(args: Array<String>) {
        val parser = ArgParser("CreateCaseData")
        val case by parser.option(
            ArgType.String, // enum ??
            shortName = "case",
            description = "belgium | boulder2024 | corla2020 | sf2024"
        ).required()
        val output by parser.option(
            ArgType.String,
            shortName = "toptopdir",
            description = "topmost output directory"
        ).required()
        val cvrExport by parser.option(
            ArgType.Boolean,
            shortName = "cvrExport",
            description = "create cvrExport.csv from CVR_Export_20241202143051.zip"
        ).default(false)
        val auditType by parser.option(
            ArgType.String, // enum ??
            shortName = "type",
            description = "oa | clca"
        )
        val auditcenter by parser.option(
            ArgType.String, // enum ??
            shortName = "auditcenter",
            description = "auditcenter local git repo"
        )
        val sampleType by parser.option(
            ArgType.String, // enum ??
            shortName = "sampling",
            description = "style | uniform"
        )

        try {
            parser.parse(args)
            print("CreateCaseData for $case topdir = $output")
            if (case == "sf2024") {
                if (cvrExport) print(" generate cvrExport file")
            }
            if (case in listOf("boulder2024", "sf2024")) {
                if (auditType == "oa") print(" audit type = OneAudit")
                else print(" audit type = CLCA")
            }
            if (case == "corla2020") {
                if (auditcenter == null) {
                    println("You must set auditcenter for corla cases ")
                    return
                }
                print("\n  using auditcenter local git repo at $auditcenter")
                print(" sampling type = ${sampleType ?: "style"}")
            }
            println()

            // what if you're not running from a jar file ??
            val version = CreateCaseData::class.java.getPackage().getImplementationVersion() ?: "unknown"
            println("version=$version")

            when (case) {
                "belgium" -> createAllBelgiumElections(output)
                "boulder2024" -> {
                    when (auditType) {
                        "clca" -> makeBoulderElectionClca(toptopdir = output)
                        else -> makeBoulderElectionOA(toptopdir = output)
                    }
                }
                "corla2020" -> {
                    when (sampleType) {
                        "uniform" -> makeCorlaElectionUniform(toptopdir = output, auditcenter = auditcenter!!)
                        else -> makeCorlaElectionClca(toptopdir = output, auditcenter = auditcenter!!)
                    }
                }
                "sf2024" -> {
                    if (cvrExport) {
                        createCvrExportCsvFile(toptopdir = output)
                    } else {
                        when (auditType) {
                            "clca" -> makeSFElectionClca(toptopdir = output)
                            else -> makeSFElectionOA(toptopdir = output)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            println(t.message)
        }
    }
}

/*
java -classpath cases/build/libs/cases-0.10.0.0-uber.jar org.cryptobiotic.rlauxe.cli.CreateCaseData \
    -case belgium -topdir "/home/stormy/datadrive/rla/cases/belgium/belgium24"
 */
