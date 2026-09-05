package org.cryptobiotic.rlauxe.corlaCounty

import org.cryptobiotic.rlauxe.auditcenter.ManifestBatch
import org.cryptobiotic.rlauxe.auditcenter.readCountyManifestCsv
import org.cryptobiotic.rlauxe.cvr.CorlaCvrs
import org.cryptobiotic.rlauxe.cvr.Redaction
import org.cryptobiotic.rlauxe.cvr.readCorlaCvrs

interface CorlaCountyInput {
    val electionName: String
    val countyName: String
    val manifestSource: String
    val cvrsSource: String

    fun readCorlaCvrs(): CorlaCvrs = readCorlaCvrs(cvrsSource, redaction = Redaction())

    fun hasABgroups() = false

    fun readCountyManifest(): List<ManifestBatch> {
        return readCountyManifestCsv(manifestSource)
    }

}