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

import kotlin.reflect.KClass

/**
 * The key of one contribution and the type of its value. Keys are compared by identity: declare
 * each once, as a constant next to the pass that writes it.
 *
 * ```kotlin
 * val ENTITY_CLASSES = ContributionKey.of<Map<String, EntityClass>>("domain.entity-classes")
 * ```
 */
class ContributionKey<T : Any>(
    /** A name for messages, unique by convention: `<profile>.<what>`. */
    val name: String,
    val type: KClass<T>,
) {
    override fun toString(): String = name

    companion object {
        inline fun <reified T : Any> of(name: String): ContributionKey<T> =
            ContributionKey(name, T::class)
    }
}

/** What the passes contributed, as emitters read it. */
interface Contributions {
    /** The value of [key], or null when no pass wrote it. */
    operator fun <T : Any> get(key: ContributionKey<T>): T?

    /** The value of [key]; fails when no pass wrote it. */
    fun <T : Any> require(key: ContributionKey<T>): T =
        get(key) ?: error("No pass contributed '$key'")

    /** Every key with a value, in the order they were first written. */
    val keys: List<ContributionKey<*>>

    /** The pass that wrote [key], or null. */
    fun writerOf(key: ContributionKey<*>): String?
}

/** What one pass writes to; it reads what the passes before it contributed. */
interface ContributionWriter : Contributions {
    /** Writes [key]; a key has one writer, which may write it once. */
    fun <T : Any> put(
        key: ContributionKey<T>,
        value: T,
    )

    /**
     * Changes [key], which this pass or an earlier one wrote, or writes it from [initial]: for a
     * value that several passes add to, such as the members of one generated class.
     */
    fun <T : Any> update(
        key: ContributionKey<T>,
        initial: () -> T,
        change: (T) -> T,
    )
}

/** The store of one pipeline run. Values are kept in write order, so the store is deterministic. */
internal class ContributionStore : Contributions {
    private val values = linkedMapOf<ContributionKey<*>, Any>()
    private val writers = mutableMapOf<ContributionKey<*>, String>()

    override fun <T : Any> get(key: ContributionKey<T>): T? =
        values[key]?.let(key.type.javaObjectType::cast)

    override val keys: List<ContributionKey<*>> get() = values.keys.toList()

    override fun writerOf(key: ContributionKey<*>): String? = writers[key]

    /** The writer of the pass [pass]. */
    fun writer(pass: String): ContributionWriter =
        object : ContributionWriter, Contributions by this {
            override fun <T : Any> put(
                key: ContributionKey<T>,
                value: T,
            ) {
                writers[key]?.let {
                    error(
                        "Pass '$pass' writes '$key', which pass '$it' already wrote",
                    )
                }

                set(key, value, pass)
            }

            override fun <T : Any> update(
                key: ContributionKey<T>,
                initial: () -> T,
                change: (T) -> T,
            ) {
                set(key, change(get(key) ?: initial()), writers[key] ?: pass)
            }
        }

    private fun <T : Any> set(
        key: ContributionKey<T>,
        value: T,
        writer: String,
    ) {
        require(key.type.isInstance(value)) {
            "'$key' takes a ${key.type.simpleName}, not a ${value::class.simpleName}"
        }

        values[key] = value
        writers[key] = writer
    }
}
