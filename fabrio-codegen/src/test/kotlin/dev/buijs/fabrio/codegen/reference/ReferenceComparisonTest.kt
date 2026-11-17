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

import dev.buijs.fabrio.codegen.ir.ContractIr
import dev.buijs.fabrio.codegen.pipeline.Contributions
import dev.buijs.fabrio.codegen.pipeline.Emitter
import dev.buijs.fabrio.codegen.pipeline.GeneratedFile
import dev.buijs.fabrio.codegen.pipeline.Pipeline
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.readText

/** The safety net itself: it tells a matching file from a different and an unexpected one. */
class ReferenceComparisonTest {
    private val case = Reference.cases.single { it.name == "example-shipment-identify-type" }

    @Test
    fun `a file is matching, different or unexpected`() {
        val (first, second) = case.expected.keys.toList()
        val emitter =
            object : Emitter {
                override val name = "test.copy"

                override fun emit(
                    ir: ContractIr,
                    contributions: Contributions,
                ) = listOf(
                    GeneratedFile(first, case.expected.getValue(first).readText()),
                    GeneratedFile(second, case.expected.getValue(second).readText() + " "),
                    GeneratedFile("extra/Extra.kt", ""),
                )
            }

        val comparison =
            Reference.compare(
                case,
                Pipeline(emptyList(), listOf(emitter)).run(case.contract).files,
            )

        assertThat(comparison.matching).containsExactly(first)
        assertThat(comparison.different).containsExactly(second)
        assertThat(comparison.unexpected).containsExactly("extra/Extra.kt")
    }
}
