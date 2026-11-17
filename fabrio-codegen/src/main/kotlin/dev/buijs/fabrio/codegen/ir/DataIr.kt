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

package dev.buijs.fabrio.codegen.ir

import dev.buijs.rialto.contract.Mapping
import dev.buijs.rialto.contract.Storage

/** One side of a mapping (M1): a type or shape of the contract, or an element of a source. */
sealed class MappingSide {
    /** The name or reference as written. */
    abstract val text: String

    data class Contract(
        val declaration: DeclarationRef,
    ) : MappingSide() {
        override val text: String get() = declaration.name
    }

    data class Element(
        val reference: DocumentRef,
    ) : MappingSide() {
        override val text: String get() = reference.text
    }
}

/** A mapping of `mappings` (M1), with both sides resolved. */
data class MappingIr(
    val declaration: Mapping,
    val source: MappingSide,
    val target: MappingSide,
) {
    val direction: String get() = declaration.direction
}

/**
 * The storage of a type (P1): the declaration, with its table and every other table it names
 * (joins, links) resolved in the SQL source.
 */
data class StorageIr(
    /** The type it stores. */
    val type: TypeRef.Named,
    val declaration: Storage,
    val table: TableRef,
    /** Every table reference of the declaration other than [table], by the reference as written. */
    val tables: Map<String, TableRef>,
)
