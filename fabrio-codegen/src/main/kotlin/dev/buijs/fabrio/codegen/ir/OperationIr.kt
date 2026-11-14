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

import dev.buijs.rialto.contract.Operation

/**
 * The OpenAPI operation an operation is bound to (R1.1): one per API source that declares it. The
 * reference `<source>#/paths/<path>/<method>` is resolved to the operation object.
 */
data class RestBinding(
    val reference: DocumentRef,
    /** The path template, such as `/shipments/{shipmentId}`. */
    val path: String,
    /** The HTTP method in lowercase, such as `get`. */
    val method: String,
) {
    /** The API source of the binding. */
    val source: String get() = reference.source
}

/** An operation of `operations`: the declaration, and what it refers to resolved. */
data class OperationIr(
    val name: String,
    val declaration: Operation,
    /** The OpenAPI operations, in the order written; empty for a local operation. */
    val rest: List<RestBinding>,
    val input: DeclarationRef?,
    /** The type or shape of the response; for `output: list<T>`, that of each element. */
    val output: DeclarationRef?,
    /** Whether the response is a bare, unpaged array of [output] (R2.7). */
    val outputList: Boolean,
    /** The entity the operation addresses (R1.7), from `subject` or its transition. */
    val subject: TypeRef.Named?,
)
