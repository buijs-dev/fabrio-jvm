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

import dev.buijs.fabrio.codegen.ir.ContractIr
import dev.buijs.fabrio.codegen.ir.TypeIr
import dev.buijs.fabrio.codegen.reference.Reference
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/** The pipeline end to end, on rialto-spec's backend test contract, with a pass and an emitter of the test. */
class PipelineTest {
    private val contract = Reference.root.resolve("inputs/contracts/valid/backend.yaml")

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
}
