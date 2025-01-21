package org.cryptobiotic.rlauxe.sampling

data class EstimationResult(
    val task: SimulateSampleSizeTask,
    val repeatedResult: RunTestRepeatedResult,
    val failed: Boolean
)