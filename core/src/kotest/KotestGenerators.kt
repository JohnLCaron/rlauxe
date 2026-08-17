@file:OptIn(ExperimentalKotest::class)

package org.cryptobiotic.rlauxe

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.constant

/** Generates arbitrary ByteArray of length len. */
fun byteArrays(len: Int): Arb<ByteArray> = Arb.byteArray(Arb.constant(len), Arb.byte())

/**
 * Property-based testing can run slowly. This will speed things up by turning off shrinking and
 * using fewer iterations. Typical usage:
 * ```
 * forAll(propTestFastConfig, Arb.x(), Arb.y()) { x, y -> ... }
 * ```
 */
val propTestFastConfig =
    PropTestConfig(maxFailure = 1, shrinkingMode = ShrinkingMode.Off, iterations = 10)

/**
 * If we know we can afford more effort to run a property test, this will spend extra time
 * trying more inputs and will put more effort into shrinking any counterexamples. Typical usage:
 * ```
 * forAll(propTestSlowConfig, Arb.x(), Arb.y()) { x, y -> ... }
 * ```
 */
val propTestSlowConfig =
    PropTestConfig(iterations = 1000)