package org.cryptobiotic.rlauxe.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.auditcenter.makeCorla2020ClcaWithCvrs
import org.cryptobiotic.rlauxe.auditcenter.makeCorla2020Clca
import org.cryptobiotic.rlauxe.auditcenter.makeCorla2020Uniform
import org.cryptobiotic.rlauxe.auditcenter.makeCorla2020UniformWithCvrs
import org.cryptobiotic.rlauxe.auditcenter.makeCorla2022Primary
import org.cryptobiotic.rlauxe.auditcenter.makeCorla2024
import org.cryptobiotic.rlauxe.belgium.makeBelgium2024Data
import org.cryptobiotic.rlauxe.boulder.makeBoulderElectionClca
import org.cryptobiotic.rlauxe.boulder.makeBoulderElectionOA
import org.cryptobiotic.rlauxe.ga.makeGa2026
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
            description = "belgium | boulder2024 | corla2020 | corla2020withCvrs | corla2022p | corla2024 | ga26p | sf2024"
        ).required()
        val toptopdir by parser.option(
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
            description = "oa | clca | poll"
        )
        val auditcenter by parser.option(
            ArgType.String, // enum ??
            shortName = "auditcenter",
            description = "auditcenter local git repo"
        )
        val input by parser.option(
            ArgType.String, // enum ??
            shortName = "input",
            description = "input directory"
        )
        val output by parser.option(  // only used by sf
            ArgType.String, // enum ??
            shortName = "output",
            description = "output directory"
        )
        val sampleType by parser.option(
            ArgType.String, // enum ??
            shortName = "sampling",
            description = "style | uniform"
        )

        try {
            parser.parse(args)
            print("CreateCaseData for $case toptopdir = $toptopdir")

            if (case == "sf2024") {
                if (cvrExport) print(" generate cvrExport file")
            }
            if (case in listOf("boulder2024", "sf2024")) {
                if (auditType == "oa") print(" audit type = OneAudit")
                else print(" audit type = CLCA")
            }
            if (case.startsWith("corla")) {
                if (auditcenter == null) {
                    println("\nYou must set auditcenter for corla cases ")
                    return
                }
                print("\n  using auditcenter local git repo at $auditcenter")
                print(" sampling type = ${sampleType ?: "style"}")
            }
            if (case == "corla2020withCvrs") {
                if (input == null) {
                    println("\nYou must set input to 'votedatabase/cvr/Colorado'")
                    return
                }
                print("\n  using votedatabase/ at $input")
                print(" sampling type = ${sampleType ?: "style"}")
            }
            if (case == "ga26p") {
                if (input == null) {
                    println("\nYou must set input directory to github/nealmcb/rla-review-arlo/2026-05-19-primary/extracted")
                    return
                }
            }
            println()

            // what if you're not running from a jar file ??
            val version = CreateCaseData::class.java.getPackage().getImplementationVersion() ?: "unknown"
            println("version=$version")

            when (case) {
                "belgium" -> makeBelgium2024Data(toptopdir)

                "boulder2024" -> {
                    when (auditType) {
                        "clca" -> makeBoulderElectionClca(toptopdir = toptopdir)
                        else -> makeBoulderElectionOA(toptopdir = toptopdir)
                    }
                }

                "corla2020" -> {
                    when (sampleType) {
                        "uniform" -> makeCorla2020Uniform(toptopdir = toptopdir, auditcenter = auditcenter!!)
                        else -> makeCorla2020Clca(toptopdir = toptopdir, auditcenter = auditcenter!!)
                    }
                }

                "corla2022p" -> {
                    makeCorla2022Primary(toptopdir = toptopdir, auditcenter = auditcenter!!)
                }

                "corla2024" -> {
                    makeCorla2024(toptopdir = toptopdir, auditcenter = auditcenter!!)
                }

                "corla2020withCvrs" -> {
                    when (sampleType) {
                        "uniform" -> makeCorla2020UniformWithCvrs(toptopdir = toptopdir, auditcenter = auditcenter!!,
                            votedatabase = input!!)
                        else -> makeCorla2020ClcaWithCvrs(toptopdir = toptopdir, auditcenter = auditcenter!!,
                            votedatabase = input!!)
                    }
                }

                "ga26p" -> {
                    makeGa2026(toptopdir, input!!, auditType)
                }

                "sf2024" -> {
                    if (cvrExport) {
                        createCvrExportCsvFile(useCvrDir = toptopdir)
                    } else {
                        if (output == null) {
                            when (auditType) {
                                "clca" -> makeSFElectionClca(topdir = "$toptopdir/clca", useCvrDir = toptopdir)
                                else -> makeSFElectionOA(topdir = "$toptopdir/oa", useCvrDir = toptopdir)
                            }
                        } else {
                            when (auditType) {
                                "clca" -> makeSFElectionClca(topdir = output!!, useCvrDir = toptopdir)
                                else -> makeSFElectionOA(topdir = output!!, useCvrDir = toptopdir)
                            }
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
    -case belgium -topdir "/home/stormy/datadrive/rla/cases/belgium/belgium2024"
 */
