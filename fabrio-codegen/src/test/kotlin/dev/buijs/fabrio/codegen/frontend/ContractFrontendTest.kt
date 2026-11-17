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

package dev.buijs.fabrio.codegen.frontend

import dev.buijs.fabrio.codegen.reference.Reference
import dev.buijs.rialto.contract.Severity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

/** The frontend: only a valid contract becomes an IR, and a rejected one says why. */
class ContractFrontendTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `a contract that cannot be read is rejected with its diagnostics`() {
        val missing = directory.resolve("missing.rialto.yaml")

        assertThatThrownBy { ContractFrontend.load(missing) }
            .isInstanceOf(ContractLoadException::class.java)
            .hasMessageStartingWith("Contract $missing is not valid:\n")
            .satisfies({
                val diagnostics = (it as ContractLoadException).diagnostics
                assertThat(diagnostics).isNotEmpty().allMatch { d -> d.severity == Severity.ERROR }

                assertThat(it.message).contains(diagnostics.first().id)
            })
    }

    @Test
    fun `a contract of another format version is rejected`() {
        val contract = directory.resolve("old.rialto.yaml")
        contract.writeText("rialto: 0.9.0\ninfo: {name: old, version: 1.0.0}\n")

        assertThatThrownBy { ContractFrontend.load(contract) }
            .isInstanceOf(ContractLoadException::class.java)
            .hasMessageContaining("'0.9.0' is not supported")
    }

    @Test
    fun `a contract whose source is missing is rejected`() {
        val contract = directory.resolve("source.rialto.yaml")
        contract.writeText(
            "rialto: 1.0.0\ninfo: {name: source, version: 1.0.0}\n" +
                "sources:\n  api: {kind: openapi, path: missing.json, version: 1.0.0, status: current}\n",
        )

        assertThatThrownBy { ContractFrontend.load(contract) }
            .isInstanceOf(ContractLoadException::class.java)
            .hasMessageContaining("missing.json")
    }

    @Test
    fun `a valid contract becomes the IR of its file`() {
        val file = Reference.root.resolve("inputs/contracts/valid/minimal.yaml")
        val ir = ContractFrontend.load(file)

        assertThat(ir.file).isEqualTo(file)
        assertThat(ir.info.name).isEqualTo(ir.contract.info.name)
    }

    @Test
    fun `a contract over several files is read as one`() {
        val case = Reference.cases.single { it.name == "contract-fragments" }

        val ir = ContractFrontend.load(case.contract)

        assertThat(ir.types.keys).containsExactlyElementsOf(ir.contract.types.keys)
        assertThat(ir.types).isNotEmpty()
    }

    @Test
    fun `an exception of the builder carries no diagnostics`() {
        val exception = ContractLoadException("message")

        assertThat(exception.message).isEqualTo("message")
        assertThat(exception.diagnostics).isEmpty()
        assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
    }
}
