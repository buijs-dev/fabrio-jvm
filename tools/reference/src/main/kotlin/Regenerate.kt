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

import dev.buijs.fabrio.codegen.generator.FabrioGenerator
import dev.buijs.rialto.contract.RemoteMode
import dev.buijs.rialto.generator.Generation
import java.io.File
import java.util.Properties

/**
 * Regenerates the reference output (arguments: the reference directory and a work directory): per case in `<reference>/cases/<case>.properties`, the files the
 * legacy generator writes into `<reference>/expected/<case>/output`, and its outcome in
 * `<reference>/expected/<case>/outcome.txt`.
 *
 * A case names its `contract` and the generator options (`option.<name>`), with paths relative to
 * the reference directory. `option.sources`, the authored Kotlin roots the legacy generator scans,
 * is a list separated by `,`. The `.rialto/` manifest that Rialto tooling writes next to the files
 * is left out: it is not Fabrio's output.
 */
fun main(args: Array<String>) {
    val reference = File(args[0]).canonicalFile
    val workRoot = File(args[1]).canonicalFile
    val cases = reference.resolve("cases").listFiles { file -> file.extension == "properties" }.orEmpty().sortedBy { it.name }
    require(cases.isNotEmpty()) { "No cases in ${reference.resolve("cases")}" }

    cases.forEach { file ->
        val case = file.nameWithoutExtension
        val properties = Properties().apply { file.reader().use(::load) }
        val contract = reference.resolve(properties.getProperty("contract"))
        val options =
            properties
                .stringPropertyNames()
                .filter { it.startsWith("option.") }
                .associate { key ->
                    val name = key.removePrefix("option.")
                    val value = properties.getProperty(key)
                    name to
                        if (name == FabrioGenerator.OPTION_SOURCES) {
                            value.split(',').filter(String::isNotBlank).joinToString(File.pathSeparator) { reference.resolve(it.trim()).path }
                        } else {
                            value
                        }
                }

        val target = reference.resolve("expected/$case")
        target.deleteRecursively()
        val work = workRoot.resolve(case).apply { deleteRecursively() }
        val result = Generation.run(contract.toPath(), FabrioGenerator(), work.toPath(), options, RemoteMode.OFFLINE)
        val files =
            work
                .walkTopDown()
                .filter { it.isFile }
                .map { it.relativeTo(work).invariantSeparatorsPath }
                .filterNot { it.startsWith(".rialto/") }
                .sorted()
                .toList()
        files.forEach { path -> work.resolve(path).copyTo(target.resolve("output/$path")) }
        work.deleteRecursively()

        val outcome =
            buildString {
                appendLine(if (result.successful) "generated ${files.size} files" else "failed")
                result.diagnostics
                    .filter { it.severity.name == "ERROR" }
                    .map { "${it.code.id} ${it.message.replace(reference.path, "<reference>")}" }
                    .sorted()
                    .forEach(::appendLine)
            }
        target.mkdirs()
        target.resolve("outcome.txt").writeText(outcome)
        println("$case: ${outcome.lineSequence().first()}")
    }
}
