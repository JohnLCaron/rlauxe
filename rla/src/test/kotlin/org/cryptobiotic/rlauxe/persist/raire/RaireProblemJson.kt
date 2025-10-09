@file:OptIn(ExperimentalSerializationApi::class)

package org.cryptobiotic.rlauxe.persist.raire

import au.org.democracydevelopers.raire.irv.Vote
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

//{
// "metadata":
//   {"candidates":
//     ["HUNTER Alan","CLARKE Bruce","COOREY Cate","ANDERSON John","MCILRATH Christopher","LYON Michael","DEY Duncan","PUGH Asren","SWIVEL Mark"],
//    "contest":"2021 NSW Local Government election for Byron Mayoral."
//   },
//  "num_candidates":9,
//  "votes":
//       [
//         {"n":1,"prefs":[4,6,7,5,8,2,3,1,0]},
//         {"n":1,"prefs":[3,6,2]},
//         {"n":1,"prefs":[0,1,6]},
//         {"n":1,"prefs":[8,6]},
//         {"n":1,"prefs":[6,7,8,1,2]},
//         {"n":1,"prefs":[6,2,5]},{"n":5, ...
//         {"n":1,"prefs":[5,0,1,8,2,7,6,4,3]},
//         {"n":1,"prefs":[6,3]},
//         {"n":1,"prefs":[6,5,8,2,3,4,7,0,1]}
//       ],
//   "winner":5,
//   "audit":{"type":"OneOnMargin","total_auditable_ballots":18165}
//}

// class RaireProblem(
//    val metadata: Map<String, Any>,
//    val votes: Array<Vote>,
//    val num_candidates: Int,
//    val winner: Int? = null,
//    val audit: AuditType,
//    val trim_algorithm: TrimAlgorithm?,
//    val difficulty_estimate: Double?,
//    val time_limit_seconds: Double?,
//)

data class RaireProblem(
    val metadata: RaireMetadata, // not sure this should be optional
    val num_candidates: Int,
    val votes: List<Vote>,
    val winner: Int,
    val auditType: String,
    val total_auditable_ballots:Int,
    val trim_algorithm: String? = null,
    val difficulty_estimate: Double? = null,
    val time_limit_seconds: Double? = null,
) {
    override fun toString() = buildString {
        appendLine("RaireProblem(num_candidates=$num_candidates, winner=$winner, auditType='$auditType', total_auditable_ballots=$total_auditable_ballots nvotes=${votes.size}")
        appendLine(" contest='${metadata.contest}'")
        appendLine(" candidates=${metadata.candidates}")
        /* votes.forEach {
            appendLine("  ${it.n} = ${it.prefs.contentToString()}, ")
        }
        appendLine() */
    }
}

@Serializable
data class RaireProblemJson(
    val metadata: RaireMetadataJson, // Map<String, Any> : Any is hard to serialize hahaha
    val num_candidates: Int,
    val votes: List<VoteJson>,
    val winner: Int,
    val audit: AuditJson,
)

fun RaireProblemJson.import(): RaireProblem {

    return RaireProblem(
        this.metadata.import(),
        this.num_candidates,
        this.votes.map { it.import() },
        this.winner,
        this.audit.type,
        this.audit.total_auditable_ballots,
    )
}

fun RaireProblem.publishJson(): RaireProblemJson {

    return RaireProblemJson(
        this.metadata.publishJson() ?: RaireMetadataJson(emptyList(), null),
        this.num_candidates,
        this.votes.map { it.publishJson() },
        this.winner,
        AuditJson(this.auditType, this.total_auditable_ballots),
    )
}


data class RaireMetadata(
    val candidates: List<String> = emptyList(),
    val contest: String? = null,
)

@Serializable
data class RaireMetadataJson(val candidates: List<String>, val contest: String?)

fun RaireMetadataJson.import() = RaireMetadata(candidates, contest)
fun RaireMetadata.publishJson() = RaireMetadataJson(this.candidates, this.contest)

@Serializable
data class VoteJson(val n: Int, val prefs: List<Int>)

fun VoteJson.import() = Vote(n, IntArray(prefs.size) { prefs[it] })
fun Vote.publishJson() = VoteJson(this.n, this.prefs.toList())

@Serializable
data class AuditJson(val type: String, val total_auditable_ballots: Int)

/////////////////////////////////////////////////////////////////////////////////
// TODO error handling
fun readRaireProblemJson(filename: String): RaireProblem {
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        throw RuntimeException("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
        val jsonObject: RaireProblemJson =  jsonReader.decodeFromStream<RaireProblemJson>(inp)
        return jsonObject.import()
    }
}

fun readRaireProblemFromString(json: String): RaireProblem {
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }
    val jsonObject: RaireProblemJson = jsonReader.decodeFromString<RaireProblemJson>(json)
    return jsonObject.import()
}

fun RaireProblem.writeToFile(filename: String) {
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    val jsonObject : RaireProblemJson =  this.publishJson()
    FileOutputStream(filename).use { out ->
        jsonReader.encodeToStream(jsonObject, out)
    }
}
