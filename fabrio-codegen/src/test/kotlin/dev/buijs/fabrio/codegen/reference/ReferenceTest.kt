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

import dev.buijs.fabrio.codegen.pipeline.GeneratedFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes

/**
 * The reference itself: complete, consistent with its outcomes, and made of files that a
 * [GeneratedFile] can reproduce byte for byte. Without this, the safety net could pass on a
 * reference that went missing or that no pipeline can ever match.
 */
class ReferenceTest {
    @Test
    fun `the reference has its 35 cases and 822 files`() {
        assertThat(Reference.cases).hasSize(35)
        assertThat(Reference.cases.map { it.name }).doesNotHaveDuplicates()
        assertThat(Reference.cases.sumOf { it.expected.size }).isEqualTo(822)

        assertThat(
            Reference.cases.filter { it.name.startsWith("example-") }.associate {
                it.name to
                    it.expected.size
            },
        ).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "example-shipment" to 380,
                "example-shipment-rabbitmq" to 380,
                "example-shipment-identify-type" to 2,
            ),
        )
    }

    @Test
    fun `every case has its contract and an outcome that matches its files`() {
        Reference.cases.forEach { case ->
            assertThat(case.contract).`as`(case.name).isRegularFile()

            if (case.outcome == "failed") {
                assertThat(case.expected).`as`(case.name).isEmpty()
            } else {
                assertThat(
                    case.outcome,
                ).`as`(case.name).isEqualTo("generated ${case.expected.size} files")
            }
        }
    }

    @Test
    fun `every reference file is UTF-8 text at a path a generated file can have`() {
        val files = Reference.cases.flatMap { it.expected.entries }

        assertThat(files).isNotEmpty()

        files.forEach { (path, file) ->
            assertThat(file.isRegularFile()).isTrue()
            val bytes = file.readBytes()
            val text =
                Charsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()

            assertThat(GeneratedFile(path, text).bytes).`as`(path).isEqualTo(bytes)
        }
    }

    @Test
    fun `no files compare as nothing matching, different or unexpected`() {
        val comparison =
            Reference.compare(
                Reference.cases.first { it.expected.isNotEmpty() },
                emptyList(),
            )

        assertThat(comparison.matching).isEmpty()
        assertThat(comparison.different).isEmpty()
        assertThat(comparison.unexpected).isEmpty()
    }

    @Test
    fun `the ratchet of a case lies between zero and its number of files`() {
        assertThat(Reference.coverage.values).allMatch { it >= 0 }

        Reference.cases.forEach { case ->
            assertThat(
                Reference.coverage.getValue(case.name),
            ).`as`(case.name).isLessThanOrEqualTo(case.expected.size)
        }
    }
}
