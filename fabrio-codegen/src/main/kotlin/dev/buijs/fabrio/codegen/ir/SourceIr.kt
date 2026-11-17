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

import com.fasterxml.jackson.databind.JsonNode
import dev.buijs.rialto.contract.LocatedDocument
import dev.buijs.rialto.contract.Source
import dev.buijs.rialto.contract.SourceKind
import dev.buijs.rialto.contract.SqlColumn
import dev.buijs.rialto.contract.SqlTable
import java.nio.file.Path

/** A source of the contract (K1.9), loaded: an OpenAPI or AsyncAPI document, or an SQL schema. */
sealed class SourceIr {
    /** The name of the source in `sources`. */
    abstract val name: String

    /** The source as declared. */
    abstract val declaration: Source

    /** The file it was read from; for SQL migrations, their directory. */
    abstract val file: Path

    /** The `kind` of the source. */
    val kind: SourceKind get() = declaration.kind

    /** An OpenAPI or AsyncAPI document, addressed by JSON Pointer. */
    class Document(
        override val name: String,
        override val declaration: Source,
        override val file: Path,
        val document: LocatedDocument,
    ) : SourceIr()

    /** An SQL schema, addressed by `table` or `table.column`: the result of every migration (K1.9). */
    class Sql(
        override val name: String,
        override val declaration: Source,
        override val file: Path,
        val tables: Map<String, SqlTable>,
    ) : SourceIr()
}

/** A resolved reference `<source>#<JSON Pointer>` into an OpenAPI or AsyncAPI source (K1.2). */
data class DocumentRef(
    /** The reference as written. */
    val text: String,
    val source: String,
    val pointer: String,
    /** The element the pointer selects. */
    val element: JsonNode,
)

/** A resolved reference `<source>#<table>` or `<source>#<table>.<column>` into an SQL source (K1.2). */
data class TableRef(
    /** The reference as written. */
    val text: String,
    val source: String,
    val table: SqlTable,
    /** The column, for `<table>.<column>`. */
    val column: SqlColumn? = null,
)
