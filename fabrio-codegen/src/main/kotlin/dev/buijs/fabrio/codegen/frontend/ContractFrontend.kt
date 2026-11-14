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

import dev.buijs.fabrio.codegen.ir.ContractIr
import dev.buijs.rialto.contract.Contract
import dev.buijs.rialto.contract.ContractFiles
import dev.buijs.rialto.contract.ContractValidator
import dev.buijs.rialto.contract.Diagnostic
import dev.buijs.rialto.contract.Diagnostics
import dev.buijs.rialto.contract.RemoteMode
import dev.buijs.rialto.contract.Severity
import dev.buijs.rialto.contract.Sources
import java.nio.file.Path

/** A contract that cannot be read into the IR: it is invalid, or a reference does not resolve. */
class ContractLoadException(
    message: String,
    /** The errors of validation; empty when the IR itself could not be built. */
    val diagnostics: List<Diagnostic> = emptyList(),
) : IllegalArgumentException(message)

/**
 * The frontend of the pipeline: a Rialto contract to a [ContractIr]. It validates the contract with
 * rialto-core, loads its sources and resolves every reference; it reads no generated or authored
 * source text.
 */
object ContractFrontend {
    /** Validates the contract at [file] (with its fragments and sources) and builds its IR. */
    fun load(
        file: Path,
        remote: RemoteMode = RemoteMode.OFFLINE,
    ): ContractIr {
        val validation = ContractValidator.validate(file, remote)
        val errors = validation.diagnostics.filter { it.severity == Severity.ERROR }

        val contract = validation.contract
        if (contract == null || errors.isNotEmpty()) {
            throw ContractLoadException(
                "Contract $file is not valid:\n" +
                    errors.joinToString("\n") { "${it.id} ${it.message} (${it.location})" },
                errors,
            )
        }

        val diagnostics = Diagnostics()
        val document =
            ContractFiles.read(file, diagnostics)
                ?: throw ContractLoadException("Contract $file cannot be read", diagnostics.all)
        return build(contract, file, Sources.load(contract, document, diagnostics, remote))
    }

    /** The IR of a valid [contract] read from [file], with its loaded [sources]. */
    fun build(
        contract: Contract,
        file: Path,
        sources: Sources,
    ): ContractIr = IrBuilder(contract, file, sources).build()
}
