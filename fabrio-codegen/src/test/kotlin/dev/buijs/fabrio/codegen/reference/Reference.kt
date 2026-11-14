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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.reader

/**
 * The reference output of the safety net, under `src/test/resources/reference`: what the legacy
 * generator wrote for each case (see docs/architecture.md).
 *
 * - `cases/<case>.properties`: the `contract` of the case, and the options the legacy generator had;
 * - `inputs/`: the contracts and every document they refer to;
 * - `expected/<case>/output/`: the files the legacy generator wrote, and `outcome.txt`: whether it succeeded;
 * - `coverage.properties`: per case, how many reference files the pipeline must produce at least.
 */
object Reference {
    /** The reference directory, as the test runtime sees it. */
    val root: Path =
        Paths
            .get(
                requireNotNull(
                    Reference::class.java.getResource("/reference/cases"),
                ) { "No reference on the classpath" }.toURI(),
            ).parent

    /** One case: a contract and the files the legacy generator wrote for it. */
    class Case(
        val name: String,
        val contract: Path,
        /** The reference files by their path relative to the output root. */
        val expected: Map<String, Path>,
        /** The first line of `outcome.txt`: `generated <n> files` or `failed`. */
        val outcome: String,
    )

    val cases: List<Case> by lazy {
        root
            .resolve("cases")
            .listDirectoryEntries("*.properties")
            .sortedBy { it.name }
            .map { file ->
                val name = file.name.removeSuffix(".properties")
                val properties = Properties().apply { file.reader().use(::load) }

                val expected = root.resolve("expected/$name")
                val output = expected.resolve("output")
                Case(
                    name = name,
                    contract =
                        root.resolve(
                            requireNotNull(properties.getProperty("contract")) {
                                "$file has no contract"
                            },
                        ),
                    expected =
                        if (output.isDirectory()) {
                            Files.walk(output).use { paths ->
                                paths
                                    .filter { it.isRegularFile() }
                                    .toList()
                                    .associateBy {
                                        output
                                            .relativize(
                                                it,
                                            ).invariantSeparatorsPathString
                                    }.toSortedMap()
                            }
                        } else {
                            emptyMap()
                        },
                    outcome =
                        expected
                            .resolve("outcome.txt")
                            .readText()
                            .lineSequence()
                            .first(),
                )
            }
    }

    /** How the files of a run compare with the reference of a case. */
    class Comparison(
        /** Files that are byte for byte those of the reference. */
        val matching: List<String>,
        /** Files with a path the reference has, and other content. */
        val different: List<String>,
        /** Files the reference does not have. */
        val unexpected: List<String>,
    )

    /** Compares [files] with the reference of [case]. */
    fun compare(
        case: Case,
        files: List<GeneratedFile>,
    ): Comparison {
        val (known, unexpected) = files.partition { it.path in case.expected }

        val (matching, different) =
            known.partition { it.bytes.contentEquals(case.expected.getValue(it.path).readBytes()) }

        return Comparison(
            matching.map { it.path },
            different.map { it.path },
            unexpected.map { it.path },
        )
    }

    /** Per case, the number of reference files the pipeline must produce at least (the ratchet). */
    val coverage: Map<String, Int> by lazy {
        Properties()
            .apply { root.resolve("coverage.properties").reader().use(::load) }
            .let { properties ->
                properties.stringPropertyNames().associateWith {
                    properties
                        .getProperty(
                            it,
                        ).trim()
                        .toInt()
                }
            }
    }
}
