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

import dev.buijs.rialto.contract.Contract
import dev.buijs.rialto.contract.ErrorModel
import dev.buijs.rialto.contract.Info
import java.nio.file.Path

/**
 * The contract as Fabrio's passes read it: every section of contract format 1.0.0, as read-only
 * structures with their references resolved, the ones into sources included. It is built from
 * the rialto-core model and the loaded sources by the frontend ([dev.buijs.fabrio.codegen.frontend.ContractFrontend]);
 * no source text is read and nothing Fabrio-specific is added. Passes enrich it through
 * contributions, never by changing it.
 *
 * Every map keeps the order of the contract, so whatever iterates it is deterministic.
 */
class ContractIr(
    /** The model as read, for what the IR does not lift. */
    val contract: Contract,
    /** The root file of the contract. */
    val file: Path,
    val sources: Map<String, SourceIr>,
    val types: Map<String, TypeIr>,
    val shapes: Map<String, ShapeIr>,
    val operations: Map<String, OperationIr>,
    val mappings: List<MappingIr>,
    /** Per stored type, its storage. */
    val storage: Map<String, StorageIr>,
    val events: Map<String, EventIr>,
    val streams: Map<String, StreamIr>,
    val subscriptions: Map<String, SubscriptionIr>,
    val presentation: PresentationIr,
    val auth: AuthIr?,
) {
    val info: Info get() = contract.info

    /** The errors section (E1); it holds no references. */
    val errors: ErrorModel? get() = contract.errors

    /** Top-level extension keys (`x-<generator>-<name>`, K1.8). */
    val extensions: Map<String, Any?> get() = contract.extensions

    /** The type [name]. */
    fun type(name: String): TypeIr =
        requireNotNull(types[name]) { "No type '$name' in ${info.name}" }

    /** The type [reference] names. */
    fun type(reference: TypeRef.Named): TypeIr = type(reference.name)

    /** The shape [name]. */
    fun shape(name: String): ShapeIr =
        requireNotNull(shapes[name]) { "No shape '$name' in ${info.name}" }

    /** The operation [name]. */
    fun operation(name: String): OperationIr =
        requireNotNull(operations[name]) { "No operation '$name' in ${info.name}" }

    /** Every type of kind [T], in contract order. */
    inline fun <reified T : TypeIr> typesOf(): List<T> = types.values.filterIsInstance<T>()

    /** The source [name]. */
    fun source(name: String): SourceIr =
        requireNotNull(sources[name]) { "No source '$name' in ${info.name}" }
}
