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

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/** The store of one run, as the passes write it and the emitters read it. */
class ContributionsTest {
    private val names = ContributionKey.of<List<String>>("test.names")
    private val count = ContributionKey.of<Int>("test.count")

    private val store = ContributionStore()

    @Test
    fun `a key is named and typed`() {
        assertThat(names.name).isEqualTo("test.names")
        assertThat(names.type).isEqualTo(List::class)
        assertThat(names.toString()).isEqualTo("test.names")
    }

    @Test
    fun `keys are compared by identity, not by name`() {
        val other = ContributionKey.of<List<String>>("test.names")
        store.writer("test.a").put(names, listOf("a"))

        assertThat(store[other]).isNull()
        store.writer("test.b").put(other, listOf("b"))
        assertThat(store[names]).containsExactly("a")
        assertThat(store[other]).containsExactly("b")
    }

    @Test
    fun `an unwritten key has no value and no writer, and requiring it fails`() {
        assertThat(store[names]).isNull()
        assertThat(store.writerOf(names)).isNull()
        assertThat(store.keys).isEmpty()

        assertThatThrownBy { store.require(names) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("No pass contributed 'test.names'")
    }

    @Test
    fun `a pass reads what it and earlier passes wrote, through its writer`() {
        store.writer("test.a").put(count, 1)
        val writer = store.writer("test.b")
        writer.put(names, listOf("b"))

        assertThat(writer[count]).isEqualTo(1)
        assertThat(writer.require(names)).containsExactly("b")
        assertThat(writer.writerOf(count)).isEqualTo("test.a")
        assertThat(writer.keys).containsExactly(count, names)
    }

    @Test
    fun `keys are listed in the order they were first written`() {
        store.writer("test.a").update(count, { 0 }) { it + 1 }

        store.writer("test.b").put(names, emptyList())
        store.writer("test.c").update(count, { 0 }) { it + 1 }

        assertThat(store.keys).containsExactly(count, names)
        assertThat(store[count]).isEqualTo(2)
    }

    @Test
    fun `a pass writes a key once`() {
        val writer = store.writer("test.a")
        writer.put(count, 1)

        assertThatThrownBy { writer.put(count, 2) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Pass 'test.a' writes 'test.count', which pass 'test.a' already wrote")
        assertThat(store[count]).isEqualTo(1)
    }

    @Test
    fun `a key that one pass updated cannot be put by another`() {
        store.writer("test.a").update(count, { 1 }) { it }

        assertThatThrownBy { store.writer("test.b").put(count, 2) }
            .hasMessage("Pass 'test.b' writes 'test.count', which pass 'test.a' already wrote")
    }

    @Test
    fun `update changes a value that another pass put, and keeps its writer`() {
        store.writer("test.a").put(names, listOf("a"))
        store.writer("test.b").update(names, { error("not called: the key has a value") }) {
            it +
                "b"
        }

        assertThat(store[names]).containsExactly("a", "b")
        assertThat(store.writerOf(names)).isEqualTo("test.a")
    }

    @Test
    fun `update writes the initial value through the change`() {
        store.writer("test.a").update(names, { listOf("initial") }) { it + "changed" }

        assertThat(store[names]).containsExactly("initial", "changed")
        assertThat(store.writerOf(names)).isEqualTo("test.a")
    }

    @Test
    fun `a value of another type than its key is rejected`() {
        @Suppress("UNCHECKED_CAST")
        val untyped = count as ContributionKey<Any>

        assertThatThrownBy { store.writer("test.a").put(untyped, "one") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("'test.count' takes a Int, not a String")
        assertThatThrownBy { store.writer("test.a").update(untyped, { 0 }) { "one" } }
            .hasMessage("'test.count' takes a Int, not a String")
        assertThat(store.keys).isEmpty()
        assertThat(store.writerOf(count)).isNull()
    }
}
