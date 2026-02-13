@file:OptIn(ExperimentalSerializationApi::class)

package org.cryptobiotic.rlauxe.rairejson

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

//{
//  "metadata": {
//    "candidates": [
//      "WORTHINGTON Alison",
//      "GRACE David",
//      "SMITH Gary",
//      "HATCHER Mat",
//      "HARRISON N (Tubby)",
//      "POLLOCK Rob",
//      "STARMER Karyn"
//    ],
//    "contest": "2021 NSW Local Government election for Eurobodalla Mayoral."
//  },
//  "solution": {
//    "Ok": {
//      "assertions": [
//        {
//          "assertion": {
//            "type": "NEB",
//            "winner": 3,
//            "loser": 2
//          },
//          "margin": 2797,
//          "difficulty": 9.126206649982123
//        },
// ...
//       {
//          "assertion": {
//            "type": "NEN",
//            "winner": 3,
//            "loser": 0,
//            "continuing": [
//              0,
//              1,
//              3,
//              4,
//              5,
//              6
//            ]
//          },
//          "margin": 2651,
//          "difficulty": 9.628819313466616
//        }
//     ],
//      "difficulty": 23.079566003616637,
//      "margin": 1106,
//      "winner": 3,
//      "num_candidates": 7,
//      "time_to_determine_winners": {
//        "work": 7,
//        "seconds": 0.000127923
//      },
//      "time_to_find_assertions": {
//        "work": 290,
//        "seconds": 0.011886019
//      },
//      "time_to_trim_assertions": {
//        "work": 810,
//        "seconds": 0.0001667450000000014
//      }
//    }
//  }
//}

data class RaireSolution(
    val metadata: RaireMetadata?, // should not be optional
    val assertions: List<SolutionAssertion>,
    val difficulty: Double,
    val margin: Int,
    val winner: Int,
    val num_candidates: Int,
    val time_to_determine_winners: RaireTimeJson,
    val time_to_find_assertions: RaireTimeJson,
    val time_to_trim_assertions: RaireTimeJson,
)  {

    override fun toString() = buildString {
        appendLine("RaireSolution(difficulty=$difficulty, margin=$margin, winner=$winner, num_candidates=$num_candidates, num_assertions=${assertions.size}" )
        appendLine(" contest='${metadata!!.contest}'")
        appendLine(" candidates=${metadata.candidates}")
        assertions.forEach {
            appendLine("  ${it} ")
        }
        appendLine()
    }
}

@Serializable
data class RaireSolutionContainerJson(
    val metadata: RaireMetadataJson?,
    val solution: RaireSolutionOkOrErrorJson,
)

fun RaireSolutionContainerJson.import(): RaireSolution {
    val solution = solution.Ok!!.import()
    val metadata = metadata!!.import()
    return solution.copy(metadata = metadata)
}

@Serializable
data class RaireSolutionOkOrErrorJson(
    val Ok: RaireSolutionOkJson?,
    val Err: String?,
)

@Serializable
data class RaireSolutionOkJson(
    val assertions: List<AssertionContainerJson>,
    val difficulty: Double,
    val margin: Int,
    val winner: Int,
    val num_candidates: Int,
    val time_to_determine_winners: RaireTimeJson,
    val time_to_find_assertions: RaireTimeJson,
    val time_to_trim_assertions: RaireTimeJson,
)

fun RaireSolutionOkJson.import(): RaireSolution {
    return RaireSolution(
        null,
        assertions.map { it.import()} ,
        this.difficulty,
        this.margin,
        this.winner,
        this.num_candidates,
        this.time_to_determine_winners,
        this.time_to_find_assertions,
        this.time_to_trim_assertions,
    )
}

data class SolutionAssertion(
    val type: String,
    val winner: Int,
    val loser: Int,
    val continuing: List<Int>?,
    val margin: Int,
    val difficulty: Double,
)

@Serializable
data class AssertionContainerJson(
    val assertion: SolutionAssertionJson, // Map<String, Any> : Any is hard to serialize hahaha
    val margin: Int,
    val difficulty: Double,
)

fun AssertionContainerJson.import(): SolutionAssertion {
    return SolutionAssertion(
        this.assertion.type,
        this.assertion.winner,
        this.assertion.loser,
        this.assertion.continuing,
        this.margin,
        this.difficulty
    )
}

@Serializable
data class SolutionAssertionJson(
    val type: String,
    val winner: Int,
    val loser: Int,
    val continuing: List<Int>?,
)

@Serializable
data class RaireTimeJson(
    val work: Int,
    val seconds: Double,
)

/////////////////////////////////////////////////////////////////////////////////

fun readRaireSolutionJson(filename: String): RaireSolution {
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        throw RuntimeException("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
        val jsonObject =  jsonReader.decodeFromStream<RaireSolutionContainerJson>(inp)
        return jsonObject.import()
    }
}
