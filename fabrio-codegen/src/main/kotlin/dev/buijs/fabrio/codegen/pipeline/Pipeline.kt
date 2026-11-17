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

package dev.buijs.fabrio.codegen.pipeline

import dev.buijs.fabrio.codegen.frontend.ContractFrontend
import dev.buijs.fabrio.codegen.ir.ContractIr
import java.nio.file.Path

/**
 * One step of the meaning of a profile: it reads the [ContractIr], and what earlier passes
 * contributed, and contributes what the emitters need. A pass never changes the IR and writes
 * no files.
 */
interface Pass {
    /** A unique name, for messages: `<profile>.<what>`. */
    val name: String

    /** The Rialto capabilities this pass implements (C3), such as `domain.entity`. */
    val capabilities: Set<String>

    fun contribute(
        ir: ContractIr,
        contributions: ContributionWriter,
    )
}

/** Turns the IR and the contributions into files. An emitter decides nothing a pass could decide. */
interface Emitter {
    /** A unique name, for messages. */
    val name: String

    fun emit(
        ir: ContractIr,
        contributions: Contributions,
    ): List<GeneratedFile>
}

/** One generated file: a path relative to the output root, with `/` as separator, and its text, written as UTF-8. */
data class GeneratedFile(
    val path: String,
    val content: String,
) {
    init {
        require(path.isNotEmpty() && !path.startsWith('/') && '\\' !in path) {
            "Not a relative path with '/': '$path'"
        }

        require(
            path.split('/').none { it.isEmpty() || it == "." || it == ".." },
        ) { "Not a normalized path: '$path'" }
    }

    /** The content as written. */
    val bytes: ByteArray get() = content.toByteArray(Charsets.UTF_8)

    override fun toString(): String = path
}

/** The result of one run: the files, sorted by path, and what the passes contributed. */
class PipelineResult(
    val files: List<GeneratedFile>,
    val contributions: Contributions,
)

/**
 * The frontend, the passes and the emitters: contract → [ContractIr] → passes → emitters → files.
 * Passes run in the order given, then the emitters; a run with the same contract always gives the
 * same files in the same order.
 */
class Pipeline(
    val passes: List<Pass>,
    val emitters: List<Emitter>,
) {
    init {
        duplicates(passes.map { it.name })?.let { error("Two passes are named '$it'") }

        duplicates(emitters.map { it.name })?.let { error("Two emitters are named '$it'") }
    }

    /** The capabilities of every pass, sorted. */
    val capabilities: Set<String> get() = passes.flatMap { it.capabilities }.toSortedSet()

    /** Reads, validates and generates the contract at [contract]. */
    fun run(contract: Path): PipelineResult = run(ContractFrontend.load(contract))

    fun run(ir: ContractIr): PipelineResult {
        val store = ContributionStore()
        passes.forEach { pass -> pass.contribute(ir, store.writer(pass.name)) }

        val files = mutableMapOf<String, Pair<String, GeneratedFile>>()
        emitters.forEach { emitter ->
            emitter.emit(ir, store).forEach { file ->
                files[file.path]?.let { (other, _) ->
                    error("Emitters '$other' and '${emitter.name}' both write ${file.path}")
                }

                files[file.path] = emitter.name to file
            }
        }

        return PipelineResult(files.values.map { it.second }.sortedBy { it.path }, store)
    }

    companion object {
        /**
         * Fabrio's passes and emitters. Each slice adds the passes of its profile and the emitters
         * of its files (see docs/architecture.md); slice 0 has none yet.
         */
        val fabrio: Pipeline get() = Pipeline(passes = emptyList(), emitters = emptyList())

        private fun duplicates(names: List<String>): String? =
            names
                .groupBy { it }
                .entries
                .firstOrNull {
                    it.value.size >
                        1
                }?.key
    }
}
