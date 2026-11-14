/**
 * Copyright (c) 2021 - 2026 Buijs Software
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package dev.buijs.fabrio.codegen.reference

import dev.buijs.fabrio.codegen.pipeline.Pipeline
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import java.io.File

/**
 * The safety net: per reference case, Fabrio's pipeline generates the contract, and every file it
 * produces is byte for byte the file of the reference. A file the reference does not have fails
 * too. Files of the reference the pipeline does not produce yet are reported as coverage; per case
 * the coverage may not drop below `coverage.properties`, which each slice raises.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReferenceOutputTest {
    private val pipeline = Pipeline.fabrio
    private val covered = sortedMapOf<String, Int>()

    @Test
    fun `every case has a ratchet and every ratchet a case`() {
        assertThat(
            Reference.coverage.keys,
        ).containsExactlyInAnyOrderElementsOf(
            Reference.cases.map { it.name },
        )
    }

    @TestFactory
    fun `the pipeline produces the reference output byte for byte`(): List<DynamicTest> =
        Reference.cases.map { case ->
            DynamicTest.dynamicTest(case.name) {
                val comparison = Reference.compare(case, pipeline.run(case.contract).files)
                assertThat(
                    comparison.unexpected,
                ).`as`("${case.name}: files the reference does not have").isEmpty()
                assertThat(
                    comparison.different,
                ).`as`("${case.name}: files that differ from the reference").isEmpty()

                covered[case.name] = comparison.matching.size
                assertThat(comparison.matching.size)
                    .`as`("${case.name}: reference files produced (coverage.properties)")
                    .isGreaterThanOrEqualTo(Reference.coverage.getValue(case.name))
            }
        }

    /** Writes the coverage to the report of the build (`build/reports/reference/coverage.txt`) and prints it. */
    @AfterAll
    fun `report the coverage`() {
        val produced = covered.values.sum()
        val total = Reference.cases.sumOf { it.expected.size }

        val report =
            buildString {
                appendLine(
                    "Reference coverage: $produced of $total reference files produced byte for byte",
                )
                Reference.cases.forEach { case ->
                    val count = covered[case.name]?.toString() ?: "?"
                    appendLine(
                        "  ${case.name}: $count of ${case.expected.size} (legacy: ${case.outcome})",
                    )
                }
            }

        println(report)
        System.getProperty("fabrio.referenceReport")?.let { path ->
            File(path).apply {
                parentFile.mkdirs()
                writeText(report)
            }
        }
    }
}
