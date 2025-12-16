package org.cryptobiotic.rlauxe.audit


import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.persist.json.writePopulationsJsonFile
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.createZipFile
import org.cryptobiotic.rlauxe.verify.VerifyResults
import org.cryptobiotic.rlauxe.verify.checkContestsCorrectlyFormed
import kotlin.io.path.Path

interface CreateElectionPIF {
    fun contestsUA(): List<ContestUnderAudit>
    fun populations(): List<PopulationIF>?

    // if you immediately write to disk, you only need one pass through the iterator
    fun cardManifest() : CloseableIterator<AuditableCard>
}

class CreateElectionP(
    val contestsUA: List<ContestUnderAudit>,
    val populations: List<PopulationIF>?,
    val cardManifest: List<AuditableCard>
):  CreateElectionPIF {

    override fun contestsUA() = contestsUA
    override fun populations() = populations
    override fun cardManifest() = Closer( cardManifest.iterator() )
}

private val logger = KotlinLogging.logger("CreateAudit")

class CreateAuditP(val name: String, val config: AuditConfig, election: CreateElectionPIF, val auditDir: String, clear: Boolean = true) {

    val stopwatch = Stopwatch()

    init {
        if (clear) clearDirectory(Path(auditDir))

        val publisher = Publisher(auditDir)
        writeAuditConfigJsonFile(config, publisher.auditConfigFile())
        logger.info{"CreateAudit writeAuditConfigJsonFile to ${publisher.auditConfigFile()}\n  $config"}

        if (election.populations() != null) {
            writePopulationsJsonFile(election.populations()!!, publisher.populationsFile())
            logger.info { "CreateAudit write ${election.populations()!!.size} populations, to ${publisher.populationsFile()}" }
        }

        val cards = election.cardManifest()
        val countCvrs = writeAuditableCardCsvFile(cards, publisher.cardManifestFile())
        createZipFile(publisher.cardManifestFile(), delete = true)
        logger.info { "CreateAudit write ${countCvrs} cards to ${publisher.cardManifestFile()}" }

        // this may change the auditStatus to misformed
        val contestsUA = election.contestsUA()

        val results = VerifyResults()
        checkContestsCorrectlyFormed(config, contestsUA, results)
        if (results.hasErrors) {
            logger.warn{ results.toString() }
        } else {
            logger.info{ results.toString() }
        }

        // sf only writes these:
        // contestsUA.filter { it.preAuditStatus == TestH0Status.InProgress }

        // write contests
        writeContestsJsonFile(contestsUA, publisher.contestsFile())
        logger.info{"CreateAudit write ${contestsUA.size} contests to ${publisher.contestsFile()}"}

        // cant write the sorted cards until after seed is generated, after committment to cardManifest
    }
}
