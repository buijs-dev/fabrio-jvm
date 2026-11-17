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

package dev.buijs.fabrio.codegen.frontend

import dev.buijs.fabrio.codegen.ir.AuthIr
import dev.buijs.fabrio.codegen.ir.ContractIr
import dev.buijs.fabrio.codegen.ir.DeclarationRef
import dev.buijs.fabrio.codegen.ir.DocumentRef
import dev.buijs.fabrio.codegen.ir.EventIr
import dev.buijs.fabrio.codegen.ir.MappingIr
import dev.buijs.fabrio.codegen.ir.MappingSide
import dev.buijs.fabrio.codegen.ir.OperationIr
import dev.buijs.fabrio.codegen.ir.PresentationIr
import dev.buijs.fabrio.codegen.ir.PropertyIr
import dev.buijs.fabrio.codegen.ir.RestBinding
import dev.buijs.fabrio.codegen.ir.ShapeIr
import dev.buijs.fabrio.codegen.ir.ShapePropertyIr
import dev.buijs.fabrio.codegen.ir.SourceIr
import dev.buijs.fabrio.codegen.ir.StorageIr
import dev.buijs.fabrio.codegen.ir.StreamIr
import dev.buijs.fabrio.codegen.ir.SubscriptionIr
import dev.buijs.fabrio.codegen.ir.TableRef
import dev.buijs.fabrio.codegen.ir.TypeIr
import dev.buijs.fabrio.codegen.ir.TypeKind
import dev.buijs.fabrio.codegen.ir.TypeRef
import dev.buijs.fabrio.codegen.ir.ViewIr
import dev.buijs.rialto.contract.ColumnRef
import dev.buijs.rialto.contract.Contract
import dev.buijs.rialto.contract.LoadedSource
import dev.buijs.rialto.contract.Operation
import dev.buijs.rialto.contract.Property
import dev.buijs.rialto.contract.Sources
import dev.buijs.rialto.contract.Storage
import dev.buijs.rialto.contract.TypeDecl
import dev.buijs.rialto.contract.TypeExpression
import dev.buijs.rialto.contract.View
import java.nio.file.Path

/** Builds the [ContractIr] of one valid contract; every reference that does not resolve is a [ContractLoadException]. */
internal class IrBuilder(
    private val contract: Contract,
    private val file: Path,
    private val loaded: Sources,
) {
    private val kinds: Map<String, TypeKind> =
        contract.types.mapValues { (_, type) -> kindOf(type) }

    fun build(): ContractIr {
        val types = contract.types.mapValues { (name, type) -> type(name, type) }

        return ContractIr(
            contract = contract,
            file = file,
            sources = contract.sources.mapValues { (name, _) -> source(name) },
            types = types,
            shapes =
                contract.shapes.mapValues { (name, shape) ->
                    val source = named(shape.source, "shapes.$name.source")
                    val sourceProperties = types.getValue(source.name).properties
                    ShapeIr(
                        name = name,
                        declaration = shape,
                        source = source,
                        properties =
                            shape.properties.mapValues { (property, declaration) ->
                                ShapePropertyIr(property, declaration, sourceProperties[property])
                            },
                    )
                },
            operations =
                contract.operations.mapValues { (name, operation) -> operation(name, operation) },
            mappings =
                contract.mappings.mapIndexed { index, mapping ->
                    MappingIr(
                        declaration = mapping,
                        source = side(mapping.source, "mappings[$index].source"),
                        target = side(mapping.target, "mappings[$index].target"),
                    )
                },
            storage = contract.storage.mapValues { (name, storage) -> storage(name, storage) },
            events =
                contract.events.mapValues { (name, event) ->
                    EventIr(
                        name = name,
                        declaration = event,
                        data = declaration(event.data, "events.$name.data"),
                        channel = document(event.channel, "events.$name.channel"),
                    )
                },
            streams =
                contract.streams.mapValues { (name, stream) ->
                    stream.events.forEach { event ->
                        expect(event in contract.events, "streams.$name.events", event)
                    }

                    StreamIr(
                        name,
                        stream,
                        document(stream.channel, "streams.$name.channel"),
                        stream.events,
                    )
                },
            subscriptions =
                contract.subscriptions.mapValues { (name, subscription) ->
                    expect(
                        subscription.event in contract.events,
                        "subscriptions.$name.event",
                        subscription.event,
                    )
                    SubscriptionIr(name, subscription, subscription.event)
                },
            presentation =
                PresentationIr(
                    views =
                        contract.views.mapValues { (name, view) ->
                            ViewIr(name, view, operationsOf(name, view))
                        },
                    navigation = contract.navigation,
                ),
            auth =
                contract.auth?.let { auth ->
                    AuthIr(
                        declaration = auth,
                        scopeSchemes =
                            auth.scopes.mapValues { (name, scope) ->
                                document(scope.scheme, "auth.scopes.$name.scheme")
                            },
                        apiKeySchemes =
                            auth.apiKeys.mapValues { (name, key) ->
                                document(key.scheme, "auth.apiKeys.$name.scheme")
                            },
                    )
                },
        )
    }

    // Sources and references -------------------------------------------------------------------

    private fun source(name: String): SourceIr {
        val declaration = contract.sources.getValue(name)
        return when (
            val source =
                loaded.loaded[name] ?: fail("sources.$name", "the source was not loaded")
        ) {
            is LoadedSource.Document -> {
                SourceIr.Document(
                    name,
                    declaration,
                    source.file,
                    source.document,
                )
            }
            is LoadedSource.Sql -> {
                SourceIr.Sql(name, declaration, source.file, source.schema)
            }
        }
    }

    private fun document(
        reference: String,
        at: String,
    ): DocumentRef {
        val (source, location) = split(reference, at)
        val document =
            loaded.loaded[source] as? LoadedSource.Document
                ?: fail(at, "'$source' is not a loaded OpenAPI or AsyncAPI source")
        val element = document.document.at(location) ?: fail(at, "'$reference' does not resolve")
        return DocumentRef(reference, source, location, element)
    }

    private fun table(
        reference: String,
        at: String,
    ): TableRef {
        val (source, location) = split(reference, at)
        val sql =
            loaded.loaded[source] as? LoadedSource.Sql
                ?: fail(at, "'$source' is not a loaded SQL source")
        val table =
            sql.schema[location.substringBefore('.').lowercase()]
                ?: fail(at, "'$reference' does not resolve")
        val column =
            if ('.' in location) {
                table.columns[location.substringAfter('.').lowercase()]
                    ?: fail(at, "'$reference' does not resolve")
            } else {
                null
            }

        return TableRef(reference, source, table, column)
    }

    private fun split(
        reference: String,
        at: String,
    ): Pair<String, String> {
        if ('#' !in reference) fail(at, "'$reference' is not a reference <source>#<location>")
        return reference.substringBefore('#') to reference.substringAfter('#')
    }

    // Types ------------------------------------------------------------------------------------

    private fun type(
        name: String,
        type: TypeDecl,
    ): TypeIr =
        when (type) {
            is TypeDecl.Entity -> {
                TypeIr.Entity(name, type, properties(name, type.properties))
            }

            is TypeDecl.Value -> {
                TypeIr.Value(
                    name,
                    type,
                    properties(name, type.properties),
                    type.wraps?.let { typeRef(it, "types.$name.wraps") },
                )
            }

            is TypeDecl.Enum -> {
                TypeIr.Enum(name, type)
            }

            is TypeDecl.Union -> {
                TypeIr.Union(
                    name,
                    type,
                    type.variants.mapValues { (value, variant) ->
                        named(variant, "types.$name.variants.$value")
                    },
                )
            }

            is TypeDecl.Projection -> {
                TypeIr.Projection(name, type, named(type.source, "types.$name.source"))
            }
        }

    private fun properties(
        owner: String,
        properties: Map<String, Property>,
    ): Map<String, PropertyIr> =
        properties.mapValues { (name, property) ->
            PropertyIr(
                name,
                owner,
                property,
                typeRef(property.type, "types.$owner.properties.$name.type"),
            )
        }

    private fun typeRef(
        text: String,
        at: String,
    ): TypeRef =
        typeRef(TypeExpression.parse(text) ?: fail(at, "'$text' is not a type expression"), at)

    private fun typeRef(
        expression: TypeExpression,
        at: String,
    ): TypeRef =
        when (expression) {
            is TypeExpression.Scalar -> {
                TypeRef.Scalar(
                    expression.name,
                    expression.precision,
                    expression.scale,
                )
            }
            is TypeExpression.Named -> {
                named(expression.name, at)
            }
            is TypeExpression.ListOf -> {
                TypeRef.ListOf(
                    typeRef(expression.element, at),
                    expression.nullableElements,
                )
            }
            is TypeExpression.SetOf -> {
                TypeRef.SetOf(
                    typeRef(expression.element, at),
                    expression.nullableElements,
                )
            }
            is TypeExpression.MapOf -> {
                TypeRef.MapOf(
                    typeRef(expression.key, at),
                    typeRef(expression.value, at),
                    expression.nullableValues,
                )
            }
        }

    private fun named(
        name: String,
        at: String,
    ): TypeRef.Named = TypeRef.Named(name, kinds[name] ?: fail(at, "no type '$name'"))

    /** A type or shape name. */
    private fun declaration(
        name: String,
        at: String,
    ): DeclarationRef =
        when (name) {
            in kinds -> DeclarationRef.Type(named(name, at))
            in contract.shapes -> DeclarationRef.ShapeOf(name)
            else -> fail(at, "no type or shape '$name'")
        }

    // Operations -------------------------------------------------------------------------------

    private fun operation(
        name: String,
        operation: Operation,
    ): OperationIr =
        OperationIr(
            name = name,
            declaration = operation,
            rest =
                operation.rest?.all.orEmpty().map { reference ->
                    restBinding(reference, "operations.$name.rest")
                },
            input = operation.input?.let { declaration(it, "operations.$name.input") },
            output = operation.outputType?.let { declaration(it, "operations.$name.output") },
            outputList = operation.outputList,
            subject =
                (operation.subject?.type ?: operation.transitionRef?.entity)?.let {
                    named(it, "operations.$name.subject")
                },
        )

    /** `<source>#/paths/<path>/<method>`: the operation object, its path template and its method. */
    private fun restBinding(
        reference: String,
        at: String,
    ): RestBinding {
        val document = document(reference, at)
        val segments = document.pointer.split('/')
        if (segments.size != 4 || segments[0].isNotEmpty() || segments[1] != "paths") {
            fail(at, "'$reference' is not an OpenAPI operation /paths/<path>/<method>")
        }

        val path = segments[2].replace("~1", "/").replace("~0", "~")
        return RestBinding(document, path, segments[3])
    }

    // Mappings and storage ---------------------------------------------------------------------

    private fun side(
        text: String,
        at: String,
    ): MappingSide =
        if ('#' in text) {
            MappingSide.Element(document(text, at))
        } else {
            MappingSide.Contract(declaration(text, at))
        }

    private fun storage(
        name: String,
        storage: Storage,
    ): StorageIr {
        val at = "storage.$name"
        val references =
            storage.columns.flatMap { (property, column) ->
                when (column) {
                    is ColumnRef.Plain -> {
                        emptyList()
                    }
                    is ColumnRef.Join -> {
                        listOf(column.table to "$at.columns.$property.table")
                    }
                    is ColumnRef.JoinPath -> {
                        column.steps.map {
                            it.table to
                                "$at.columns.$property.join"
                        }
                    }
                }
            } +
                storage.links.mapNotNull { (property, link) ->
                    link.table?.let {
                        it to
                            "$at.links.$property.table"
                    }
                }

        return StorageIr(
            type = named(name, at),
            declaration = storage,
            table = table(storage.table, "$at.table"),
            tables =
                references
                    .filter { (reference, _) -> reference != storage.table }
                    .distinctBy { (reference, _) -> reference }
                    .associate { (reference, location) -> reference to table(reference, location) },
        )
    }

    // Views ------------------------------------------------------------------------------------

    private fun operationsOf(
        name: String,
        view: View,
    ): List<String> {
        val operations =
            listOfNotNull(view.load, view.submit, view.list?.operation) +
                view.actions.values.map { it.operation } +
                view.list
                    ?.actions
                    ?.values
                    ?.map { it.operation }
                    .orEmpty() +
                view.fields.values.mapNotNull { it.options?.operation }

        operations.forEach { expect(it in contract.operations, "views.$name", it) }

        return operations.distinct()
    }

    // ------------------------------------------------------------------------------------------

    private fun expect(
        condition: Boolean,
        at: String,
        name: String,
    ) {
        if (!condition) fail(at, "'$name' does not resolve")
    }

    private fun fail(
        at: String,
        message: String,
    ): Nothing = throw ContractLoadException("$file, $at: $message")

    private companion object {
        fun kindOf(type: TypeDecl): TypeKind =
            when (type) {
                is TypeDecl.Entity -> TypeKind.ENTITY
                is TypeDecl.Value -> TypeKind.VALUE
                is TypeDecl.Enum -> TypeKind.ENUM
                is TypeDecl.Union -> TypeKind.UNION
                is TypeDecl.Projection -> TypeKind.PROJECTION
            }
    }
}
