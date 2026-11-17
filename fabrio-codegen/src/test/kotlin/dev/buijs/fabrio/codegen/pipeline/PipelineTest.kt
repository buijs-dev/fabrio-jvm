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
import dev.buijs.fabrio.codegen.frontend.ContractLoadException
import dev.buijs.fabrio.codegen.ir.ContractIr
import dev.buijs.fabrio.codegen.ir.TypeIr
import dev.buijs.fabrio.codegen.reference.Reference
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/** The pipeline end to end, on rialto-spec's backend test contract, with a pass and an emitter of the test. */
class PipelineTest {
    private val contract = Reference.root.resolve("inputs/contracts/valid/backend.yaml")

    private val ir: ContractIr by lazy { ContractFrontend.load(contract) }

    /** Per entity, the names of its properties. */
    private val entityProperties =
        ContributionKey.of<Map<String, List<String>>>(
            "test.entity-properties",
        )

    private val entityPass =
        object : Pass {
            override val name = "test.entities"
            override val capabilities = setOf("domain.entity")

            override fun contribute(
                ir: ContractIr,
                contributions: ContributionWriter,
            ) {
                contributions.put(
                    entityProperties,
                    ir.typesOf<TypeIr.Entity>().associate {
                        it.name to
                            it.properties.keys.toList()
                    },
                )
            }
        }

    /** One file per entity, written in reverse order: the pipeline sorts them. */
    private val entityEmitter =
        object : Emitter {
            override val name = "test.entity-files"

            override fun emit(
                ir: ContractIr,
                contributions: Contributions,
            ): List<GeneratedFile> =
                contributions
                    .require(entityProperties)
                    .map { (entity, properties) ->
                        GeneratedFile(
                            "domain/$entity.txt",
                            properties.joinToString("\n", postfix = "\n"),
                        )
                    }.reversed()
        }

    @Test
    fun `a contract runs through the frontend, the passes and the emitters`() {
        val result = Pipeline(listOf(entityPass), listOf(entityEmitter)).run(contract)

        assertThat(result.files.map { it.path }).containsExactly("domain/Order.txt")
        assertThat(result.files.single().content).isEqualTo("id\nstatus\ntotal\nnote\n")
        assertThat(result.contributions.writerOf(entityProperties)).isEqualTo("test.entities")
    }

    @Test
    fun `the same contract gives the same files`() {
        val pipeline = Pipeline(listOf(entityPass), listOf(entityEmitter))

        assertThat(pipeline.run(contract).files).isEqualTo(pipeline.run(contract).files)
    }

    @Test
    fun `the capabilities are those of the passes`() {
        assertThat(
            Pipeline(listOf(entityPass), emptyList()).capabilities,
        ).containsExactly("domain.entity")
        assertThat(Pipeline.fabrio.capabilities).isEmpty()
    }

    @Test
    fun `a contribution has one writer`() {
        val second =
            object : Pass by entityPass {
                override val name = "test.second"
            }

        assertThatThrownBy { Pipeline(listOf(entityPass, second), emptyList()).run(contract) }
            .hasMessageContaining(
                "Pass 'test.second' writes 'test.entity-properties', which pass 'test.entities' already wrote",
            )
    }

    @Test
    fun `passes add to a contribution with update`() {
        val counter = ContributionKey.of<Int>("test.counter")
        val counting =
            (1..3).map { index ->
                object : Pass {
                    override val name = "test.count-$index"
                    override val capabilities = emptySet<String>()

                    override fun contribute(
                        ir: ContractIr,
                        contributions: ContributionWriter,
                    ) {
                        contributions.update(counter, { 0 }) { it + index }
                    }
                }
            }

        val result = Pipeline(counting, emptyList()).run(contract)
        assertThat(result.contributions[counter]).isEqualTo(6)
        assertThat(result.contributions.writerOf(counter)).isEqualTo("test.count-1")
    }

    @Test
    fun `two emitters cannot write the same file`() {
        val twin =
            object : Emitter by entityEmitter {
                override val name = "test.twin"
            }

        assertThatThrownBy {
            Pipeline(
                listOf(entityPass),
                listOf(entityEmitter, twin),
            ).run(contract)
        }.hasMessageContaining(
            "Emitters 'test.entity-files' and 'test.twin' both write domain/Order.txt",
        )
    }

    @Test
    fun `a generated file has a normalized relative path`() {
        listOf("/abs.kt", "a/../b.kt", "a//b.kt", "a\\b.kt", "").forEach { path ->
            assertThatThrownBy {
                GeneratedFile(
                    path,
                    "",
                )
            }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `a generated file keeps a valid relative path and writes its text as UTF-8`() {
        val file = GeneratedFile("a/.b/c-d.kt", "é€\n")

        assertThat(file.path).isEqualTo("a/.b/c-d.kt")
        assertThat(file.toString()).isEqualTo("a/.b/c-d.kt")
        assertThat(file.bytes).isEqualTo("é€\n".toByteArray(Charsets.UTF_8))
        listOf("a/./b.kt", "a/", "..", ".").forEach { path ->
            assertThatThrownBy { GeneratedFile(path, "") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("Not a normalized path: '$path'")
        }

        assertThatThrownBy {
            GeneratedFile("/a.kt", "")
        }.hasMessage("Not a relative path with '/': '/a.kt'")
    }

    @Test
    fun `passes run in the order given, each reading what the passes before it wrote`() {
        val order = mutableListOf<String>()
        val first = ContributionKey.of<String>("test.first")
        val second = ContributionKey.of<String>("test.second")

        fun pass(
            name: String,
            body: (ContributionWriter) -> Unit,
        ) = object : Pass {
            override val name = name
            override val capabilities = emptySet<String>()

            override fun contribute(
                ir: ContractIr,
                contributions: ContributionWriter,
            ) {
                order += name
                body(contributions)
            }
        }

        val writing = pass("test.write") { it.put(first, "1") }

        val reading = pass("test.read") { it.put(second, it.require(first) + "2") }

        val result = Pipeline(listOf(writing, reading), emptyList()).run(ir)
        assertThat(order).containsExactly("test.write", "test.read")
        assertThat(result.contributions[second]).isEqualTo("12")
        assertThat(result.contributions.keys).containsExactly(first, second)
        assertThat(result.files).isEmpty()

        order.clear()
        assertThatThrownBy { Pipeline(listOf(reading, writing), emptyList()).run(ir) }
            .hasMessage("No pass contributed 'test.first'")
        assertThat(order).containsExactly("test.read")
    }

    @Test
    fun `emitters run after every pass and their files come out sorted by path`() {
        val events = mutableListOf<String>()
        val pass =
            object : Pass {
                override val name = "test.pass"
                override val capabilities = setOf("b.cap", "a.cap")

                override fun contribute(
                    ir: ContractIr,
                    contributions: ContributionWriter,
                ) {
                    events += name
                }
            }

        fun emitter(
            name: String,
            vararg paths: String,
        ) = object : Emitter {
            override val name = name

            override fun emit(
                ir: ContractIr,
                contributions: Contributions,
            ): List<GeneratedFile> {
                events += name
                return paths.map { GeneratedFile(it, name) }
            }
        }

        val pipeline =
            Pipeline(
                listOf(pass),
                listOf(emitter("test.z", "z/b.kt", "a/z.kt"), emitter("test.a", "m.kt", "a/a.kt")),
            )
        val result = pipeline.run(ir)

        assertThat(events).containsExactly("test.pass", "test.z", "test.a")
        assertThat(
            result.files.map { it.path },
        ).containsExactly("a/a.kt", "a/z.kt", "m.kt", "z/b.kt")
        assertThat(result.files.associate { it.path to it.content })
            .containsEntry("a/z.kt", "test.z")
            .containsEntry("m.kt", "test.a")
        assertThat(pipeline.capabilities).containsExactly("a.cap", "b.cap")
        assertThat(pipeline.passes).containsExactly(pass)
        assertThat(pipeline.emitters).hasSize(2)
    }

    @Test
    fun `one emitter cannot write a path twice`() {
        val emitter =
            object : Emitter {
                override val name = "test.twice"

                override fun emit(
                    ir: ContractIr,
                    contributions: Contributions,
                ) = listOf(GeneratedFile("a.kt", "1"), GeneratedFile("a.kt", "2"))
            }

        assertThatThrownBy { Pipeline(emptyList(), listOf(emitter)).run(ir) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Emitters 'test.twice' and 'test.twice' both write a.kt")
    }

    @Test
    fun `passes and emitters have unique names`() {
        assertThatThrownBy { Pipeline(listOf(entityPass, entityPass), emptyList()) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Two passes are named 'test.entities'")
        assertThatThrownBy { Pipeline(emptyList(), listOf(entityEmitter, entityEmitter)) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Two emitters are named 'test.entity-files'")
    }

    @Test
    fun `Fabrio's pipeline has no passes or emitters yet, and generates nothing`() {
        val fabrio = Pipeline.fabrio

        assertThat(fabrio.passes).isEmpty()
        assertThat(fabrio.emitters).isEmpty()
        val result = fabrio.run(contract)
        assertThat(result.files).isEmpty()
        assertThat(result.contributions.keys).isEmpty()
    }

    @Test
    fun `an invalid contract stops the run before any pass`() {
        var ran = false
        val pass =
            object : Pass by entityPass {
                override fun contribute(
                    ir: ContractIr,
                    contributions: ContributionWriter,
                ) {
                    ran = true
                }
            }

        assertThatThrownBy {
            Pipeline(listOf(pass), emptyList()).run(contract.resolveSibling("missing.yaml"))
        }.isInstanceOf(ContractLoadException::class.java)
        assertThat(ran).isFalse()
    }
}
