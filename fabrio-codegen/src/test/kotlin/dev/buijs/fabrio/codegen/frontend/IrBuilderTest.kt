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

import dev.buijs.fabrio.codegen.ir.ContractIr
import dev.buijs.fabrio.codegen.ir.DeclarationRef
import dev.buijs.fabrio.codegen.ir.MappingSide
import dev.buijs.fabrio.codegen.ir.SourceIr
import dev.buijs.fabrio.codegen.ir.TypeIr
import dev.buijs.fabrio.codegen.ir.TypeKind
import dev.buijs.fabrio.codegen.ir.TypeRef
import dev.buijs.rialto.contract.Contract
import dev.buijs.rialto.contract.ContractFiles
import dev.buijs.rialto.contract.ContractMapper
import dev.buijs.rialto.contract.Diagnostics
import dev.buijs.rialto.contract.RemoteMode
import dev.buijs.rialto.contract.SourceKind
import dev.buijs.rialto.contract.Sources
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * The IR builder on small contracts written for the test. They are read into the model without
 * validation, so a reference that validation would reject reaches the builder, which must reject
 * it too, with the location in the contract.
 */
class IrBuilderTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `type expressions resolve to their structure and the kind of each named type`() {
        val ir =
            build(
                """
                types:
                  Tag: {kind: value, wraps: string}
                  Size: {kind: enum, members: [small, large]}
                  Item:
                    kind: entity
                    identity: [id]
                    properties:
                      id: {type: uuid}
                      price: {type: 'decimal(8,2)'}
                      tags: {type: 'list<Tag?>'}
                      sizes: {type: set<Size>}
                      stock: {type: 'map<string, Size?>'}
                      byTag: {type: 'map<Tag, int32>'}
                      note: {type: string, nullable: true}
                  Summary: {kind: projection, source: Item, properties: {id: id, priceOf: price}}
                  Choice: {kind: union, discriminator: kind, variants: {tag: Tag}}
                """,
            )

        val item = ir.type("Item") as TypeIr.Entity
        assertThat(item.properties.mapValues { it.value.type })
            .containsExactly(
                entry("id", TypeRef.Scalar("uuid")),
                entry("price", TypeRef.Scalar("decimal", 8, 2)),
                entry("tags", TypeRef.ListOf(TypeRef.Named("Tag", TypeKind.VALUE), true)),
                entry("sizes", TypeRef.SetOf(TypeRef.Named("Size", TypeKind.ENUM), false)),
                entry(
                    "stock",
                    TypeRef.MapOf(
                        TypeRef.Scalar("string"),
                        TypeRef.Named("Size", TypeKind.ENUM),
                        true,
                    ),
                ),
                entry(
                    "byTag",
                    TypeRef.MapOf(
                        TypeRef.Named("Tag", TypeKind.VALUE),
                        TypeRef.Scalar("int32"),
                        false,
                    ),
                ),
                entry("note", TypeRef.Scalar("string")),
            )
        assertThat(item.properties.getValue("note").nullable).isTrue()
        assertThat(item.properties.getValue("id").nullable).isFalse()
        assertThat(
            item.properties.values
                .map { it.owner }
                .distinct(),
        ).containsExactly("Item")
        assertThat(
            item.properties
                .getValue("byTag")
                .type
                .named()
                .map { it.name },
        ).containsExactly("Tag")
        assertThat(
            item.properties
                .getValue("price")
                .type
                .named(),
        ).isEmpty()

        val tag = ir.type("Tag") as TypeIr.Value
        assertThat(tag.wraps).isEqualTo(TypeRef.Scalar("string"))
        assertThat(tag.properties).isEmpty()

        val size = ir.type(TypeRef.Named("Size", TypeKind.ENUM)) as TypeIr.Enum
        assertThat(size.members).containsExactly("small", "large")
        assertThat(size.properties).isEmpty()

        val summary = ir.type("Summary") as TypeIr.Projection
        assertThat(summary.source).isEqualTo(TypeRef.Named("Item", TypeKind.ENTITY))
        assertThat(summary.paths).containsExactly(entry("id", "id"), entry("priceOf", "price"))

        val choice = ir.type("Choice") as TypeIr.Union
        assertThat(
            choice.variants,
        ).containsExactly(entry("tag", TypeRef.Named("Tag", TypeKind.VALUE)))

        assertThat(ir.types.values.map { it.kind })
            .containsExactly(
                TypeKind.VALUE,
                TypeKind.ENUM,
                TypeKind.ENTITY,
                TypeKind.PROJECTION,
                TypeKind.UNION,
            )
        assertThat(ir.typesOf<TypeIr.Value>().map { it.name }).containsExactly("Tag")
    }

    @Test
    fun `a shape property names the property of its source type, or none`() {
        val ir =
            build(
                """
                types:
                  Item: {kind: entity, identity: [id], properties: {id: {type: uuid}, name: {type: string}}}
                shapes:
                  Rename:
                    source: Item
                    properties:
                      name: {role: caller}
                      reason: {role: caller}
                      nested: {role: caller, shape: Rename}
                """,
            )

        val rename = ir.shape("Rename")
        assertThat(rename.source).isEqualTo(TypeRef.Named("Item", TypeKind.ENTITY))
        assertThat(rename.properties.keys).containsExactly("name", "reason", "nested")
        assertThat(
            rename.properties
                .getValue("name")
                .source
                ?.name,
        ).isEqualTo("name")
        assertThat(rename.properties.getValue("reason").source).isNull()
        assertThat(rename.properties.getValue("reason").shape).isNull()
        assertThat(rename.properties.getValue("nested").shape).isEqualTo("Rename")
    }

    @Test
    fun `operations resolve their REST binding, input, output and subject`() {
        val ir =
            build(
                """
                types:
                  Item: {kind: entity, identity: [id], properties: {id: {type: uuid}, name: {type: string}}}
                shapes:
                  CreateItem: {source: Item, properties: {name: {role: caller}}}
                operations:
                  getItem:
                    rest: api#/paths/~1items~1{id}/get
                    output: Item
                  listItems:
                    rest: {api: api#/paths/~1items/get, legacy: legacy#/paths/~1items/get}
                    output: list<Item>
                  createItem:
                    rest: api#/paths/~1items/post
                    input: CreateItem
                    output: Item
                  odd:
                    rest: api#/paths/~1a~0b/get
                  close:
                    transition: Item.close
                  delete:
                    subject: Item
                  local: {}
                """,
            )

        val get = ir.operation("getItem")
        assertThat(get.rest.map { Triple(it.source, it.path, it.method) })
            .containsExactly(Triple("api", "/items/{id}", "get"))
        assertThat(
            get.rest
                .single()
                .reference.element
                .path("operationId")
                .asText(),
        ).isEqualTo("getItem")
        assertThat(
            get.output,
        ).isEqualTo(DeclarationRef.Type(TypeRef.Named("Item", TypeKind.ENTITY)))
        assertThat(get.outputList).isFalse()
        assertThat(get.input).isNull()

        val list = ir.operation("listItems")
        assertThat(list.rest.map { it.source }).containsExactly("api", "legacy")
        assertThat(list.output?.name).isEqualTo("Item")
        assertThat(list.outputList).isTrue()

        val create = ir.operation("createItem")
        assertThat(create.input).isEqualTo(DeclarationRef.ShapeOf("CreateItem"))
        assertThat(create.input?.name).isEqualTo("CreateItem")

        // JSON Pointer escapes: ~1 is '/', ~0 is '~'.
        assertThat(
            ir
                .operation("odd")
                .rest
                .single()
                .path,
        ).isEqualTo("/a~b")

        assertThat(ir.operation("close").subject).isEqualTo(TypeRef.Named("Item", TypeKind.ENTITY))
        assertThat(ir.operation("delete").subject).isEqualTo(TypeRef.Named("Item", TypeKind.ENTITY))

        val local = ir.operation("local")
        assertThat(local.rest).isEmpty()
        assertThat(listOf(local.input, local.output, local.subject)).containsOnlyNulls()
    }

    @Test
    fun `storage resolves its table, its joins and its link tables, each once`() {
        val ir =
            build(
                """
                types:
                  Item: {kind: entity, identity: [id], properties: {id: {type: uuid}, name: {type: string}}}
                  Tag: {kind: entity, identity: [id], properties: {id: {type: uuid}}}
                storage:
                  Item:
                    table: db#item
                    columns:
                      id: id
                      tag: {join: tag_id, table: db#tag, key: id}
                      owner:
                        join:
                        - {column: tag_id, table: db#tag}
                        - {column: id, table: db#item}
                        key: id
                    links:
                      tags: {table: db#item_tag, ownerKey: item_id, targetKey: tag_id}
                      readOnly: {ownerKey: item_id}
                  Tag: {table: db#tag.label, columns: {id: id}}
                """,
            )

        val item = ir.storage.getValue("Item")
        assertThat(item.type).isEqualTo(TypeRef.Named("Item", TypeKind.ENTITY))
        assertThat(item.table.text).isEqualTo("db#item")
        assertThat(item.table.source).isEqualTo("db")
        assertThat(item.table.table.name).isEqualTo("item")
        assertThat(item.table.column).isNull()
        assertThat(item.tables.keys).containsExactly("db#tag", "db#item_tag")
        assertThat(
            item.tables
                .getValue("db#item_tag")
                .table.columns,
        ).containsKeys("item_id", "tag_id")

        val tag = ir.storage.getValue("Tag").table
        assertThat(tag.table.name).isEqualTo("tag")
        assertThat(tag.column?.name).isEqualTo("label")
    }

    @Test
    fun `events, streams, subscriptions, mappings, views and auth resolve`() {
        val ir =
            build(
                """
                types:
                  Item: {kind: entity, identity: [id], properties: {id: {type: uuid}, name: {type: string}}}
                  ItemAdded: {kind: value, properties: {id: {type: uuid}}}
                shapes:
                  ItemView: {source: Item, properties: {name: {role: caller}}}
                auth:
                  providers: {main: {issuer: https://auth.example.invalid}}
                  scopes: {read: {scheme: api#/components/securitySchemes/oauth}}
                  apiKeys: {partner: {scheme: api#/components/securitySchemes/key, lookup: lookupKey}}
                events:
                  ItemAdded:
                    {type: added, source: /items, subject: id, data: ItemAdded, channel: events#/channels/items, mode: structured}
                  ItemShown:
                    {type: shown, source: /items, subject: id, data: ItemView, channel: events#/channels/items, mode: binary}
                streams:
                  updates: {events: [ItemAdded, ItemShown], channel: events#/channels/items, transport: sse, replay: PT1H}
                subscriptions:
                  notify:
                    {event: ItemAdded, handler: authored, delivery: {attempts: 3, deadLetter: events#/channels/items}}
                mappings:
                  - {source: api#/components/schemas/Item, target: Item, direction: both}
                  - {source: ItemView, target: api#/components/schemas/Item, direction: toTarget}
                operations:
                  getItem: {rest: 'api#/paths/~1items~1{id}/get', output: Item}
                  listItems: {rest: api#/paths/~1items/get, output: list<Item>}
                  createItem: {rest: api#/paths/~1items/post}
                  suggest: {}
                views:
                  ItemDetail:
                    route: /items/{id}
                    load: getItem
                    submit: createItem
                    fields: {name: {options: {operation: suggest, label: name}}}
                    actions: {reload: {operation: getItem}}
                    list:
                      operation: listItems
                      columns: [name]
                      actions: {again: {operation: listItems}, add: {operation: createItem}}
                  Empty: {route: /empty}
                navigation:
                  home: ItemDetail
                """,
            )

        val added = ir.events.getValue("ItemAdded")
        assertThat(
            added.data,
        ).isEqualTo(DeclarationRef.Type(TypeRef.Named("ItemAdded", TypeKind.VALUE)))
        assertThat(added.channel.source).isEqualTo("events")
        assertThat(added.channel.pointer).isEqualTo("/channels/items")
        assertThat(
            ir.events.getValue("ItemShown").data,
        ).isEqualTo(DeclarationRef.ShapeOf("ItemView"))

        val updates = ir.streams.getValue("updates")
        assertThat(updates.events).containsExactly("ItemAdded", "ItemShown")
        assertThat(updates.channel.text).isEqualTo("events#/channels/items")
        assertThat(ir.subscriptions.getValue("notify").event).isEqualTo("ItemAdded")

        val (inbound, outbound) = ir.mappings
        assertThat(inbound.source.text).isEqualTo("api#/components/schemas/Item")
        assertThat(inbound.target.text).isEqualTo("Item")
        assertThat(inbound.direction).isEqualTo("both")
        assertThat(
            outbound.source,
        ).isEqualTo(MappingSide.Contract(DeclarationRef.ShapeOf("ItemView")))
        assertThat((outbound.target as MappingSide.Element).reference.pointer)
            .isEqualTo("/components/schemas/Item")

        assertThat(
            ir.presentation.views
                .getValue("ItemDetail")
                .operations,
        ).containsExactly("getItem", "createItem", "listItems", "suggest")
        assertThat(
            ir.presentation.views
                .getValue("Empty")
                .operations,
        ).isEmpty()
        assertThat(ir.presentation.navigation?.home).isEqualTo("ItemDetail")

        val auth = requireNotNull(ir.auth)
        assertThat(
            auth.scopeSchemes
                .getValue("read")
                .element
                .path("type")
                .asText(),
        ).isEqualTo("oauth2")
        assertThat(
            auth.apiKeySchemes
                .getValue("partner")
                .element
                .path("type")
                .asText(),
        ).isEqualTo("apiKey")
    }

    @Test
    fun `the IR gives the sources, the info, the errors and the extensions of the contract`() {
        val ir =
            build(
                """
                x-fabrio-note: kept
                errors:
                  format: problem-details
                  validationStatus: 400
                  identify: member
                  wire:
                    fieldErrors: /errors
                    field: {location: /pointer, code: /code, detail: /detail}
                    locationFormat: pointer
                """,
            )

        assertThat(ir.info.name).isEqualTo("test")
        assertThat(ir.file).isEqualTo(directory.resolve("test.rialto.yaml"))
        assertThat(ir.errors?.format).isEqualTo("problem-details")
        assertThat(ir.extensions).containsEntry("x-fabrio-note", "kept")
        assertThat(ir.auth).isNull()
        assertThat(ir.presentation.navigation).isNull()

        val api = ir.source("api") as SourceIr.Document
        assertThat(api.kind).isEqualTo(SourceKind.OPENAPI)
        assertThat(api.name).isEqualTo("api")
        assertThat(api.file.fileName.toString()).isEqualTo("api.json")
        assertThat(api.document.at("/paths")).isNotNull()

        val db = ir.source("db") as SourceIr.Sql
        assertThat(db.kind).isEqualTo(SourceKind.SQL)
        assertThat(db.declaration.path).isEqualTo("db.sql")
        assertThat(db.tables.keys).containsExactly("item", "tag", "item_tag")
        assertThat(ir.source("events").kind).isEqualTo(SourceKind.ASYNCAPI)
    }

    @Test
    fun `a lookup of a name the contract does not have fails with the name`() {
        val ir = build("")

        assertThatThrownBy { ir.type("Missing") }.hasMessage("No type 'Missing' in test")
        assertThatThrownBy { ir.shape("Missing") }.hasMessage("No shape 'Missing' in test")
        assertThatThrownBy { ir.operation("missing") }.hasMessage("No operation 'missing' in test")
        assertThatThrownBy { ir.source("missing") }.hasMessage("No source 'missing' in test")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unresolved")
    fun `a reference that does not resolve is a ContractLoadException at its location`(
        @Suppress("UNUSED_PARAMETER")
        case: String,
        yaml: String,
        message: String,
    ) {
        assertThatThrownBy { build(yaml) }
            .isInstanceOf(ContractLoadException::class.java)
            .hasMessage("${directory.resolve("test.rialto.yaml")}, $message")
            .satisfies({ assertThat((it as ContractLoadException).diagnostics).isEmpty() })
    }

    @Test
    fun `a source that could not be loaded is a ContractLoadException`() {
        assertThatThrownBy {
            build("", sources = "  migrations: {kind: sql, path: 'migrations/*.sql'}\n")
        }.isInstanceOf(ContractLoadException::class.java)
            .hasMessage(
                "${directory.resolve(
                    "test.rialto.yaml",
                )}, sources.migrations: the source was not loaded",
            )
    }

    private fun build(
        yaml: String,
        sources: String = "",
    ): ContractIr {
        directory.resolve("api.json").writeText(OPENAPI)
        directory.resolve("legacy.json").writeText(OPENAPI)
        directory.resolve("events.json").writeText(ASYNCAPI)
        directory.resolve("db.sql").writeText(SQL)

        val file = directory.resolve("test.rialto.yaml")
        file.writeText(HEADER + sources + "\n" + yaml.trimIndent() + "\n")

        val diagnostics = Diagnostics()
        val document =
            requireNotNull(ContractFiles.read(file, diagnostics)) { diagnostics.all.toString() }

        val contract = ContractMapper.json.treeToValue(document.root, Contract::class.java)
        val sources = Sources.load(contract, document, diagnostics, RemoteMode.OFFLINE)
        return ContractFrontend.build(contract, file, sources)
    }

    private fun <K, V> entry(
        key: K,
        value: V,
    ): Map.Entry<K, V> = java.util.AbstractMap.SimpleEntry(key, value)

    companion object {
        private val HEADER =
            """
            rialto: 1.0.0
            info: {name: test, version: 1.0.0}
            sources:
              api: {kind: openapi, path: api.json}
              legacy: {kind: openapi, path: legacy.json}
              events: {kind: asyncapi, path: events.json}
              db: {kind: sql, path: db.sql}
            """.trimIndent() + "\n"

        private const val ITEM =
            "types:\n  Item: {kind: entity, identity: [id], properties: {id: {type: uuid}, name: {type: string}}}"

        private val OPENAPI =
            """
            {
              "openapi": "3.1.0",
              "info": {"title": "test", "version": "1.0.0"},
              "paths": {
                "/items": {"get": {"operationId": "listItems"}, "post": {"operationId": "createItem"}},
                "/items/{id}": {"get": {"operationId": "getItem"}},
                "/a~b": {"get": {"operationId": "odd"}}
              },
              "components": {
                "schemas": {"Item": {"type": "object"}},
                "securitySchemes": {"key": {"type": "apiKey"}, "oauth": {"type": "oauth2"}}
              }
            }
            """.trimIndent()

        private val ASYNCAPI =
            """{"asyncapi": "3.0.0", "info": {"title": "test", "version": "1.0.0"}, "channels": {"items": {"address": "items"}}}"""

        private val SQL =
            """
            CREATE TABLE item (id uuid PRIMARY KEY, name text, tag_id uuid);
            CREATE TABLE tag (id uuid PRIMARY KEY, label text);
            CREATE TABLE item_tag (item_id uuid, tag_id uuid);
            """.trimIndent()

        private fun case(
            name: String,
            yaml: String,
            message: String,
        ): Arguments = Arguments.of(name, yaml, message)

        @JvmStatic
        fun unresolved(): List<Arguments> =
            listOf(
                case(
                    "a property type that is not a type expression",
                    "types:\n  A: {kind: value, properties: {b: {type: 'list<'}}}",
                    "types.A.properties.b.type: 'list<' is not a type expression",
                ),
                case(
                    "a property of a type that does not exist",
                    "types:\n  A: {kind: value, properties: {b: {type: list<Missing>}}}",
                    "types.A.properties.b.type: no type 'Missing'",
                ),
                case(
                    "a value object wrapping a type that does not exist",
                    "types:\n  A: {kind: value, wraps: Missing}",
                    "types.A.wraps: no type 'Missing'",
                ),
                case(
                    "a union variant that does not exist",
                    "types:\n  A: {kind: union, discriminator: kind, variants: {b: Missing}}",
                    "types.A.variants.b: no type 'Missing'",
                ),
                case(
                    "a projection of a type that does not exist",
                    "types:\n  A: {kind: projection, source: Missing, properties: [id]}",
                    "types.A.source: no type 'Missing'",
                ),
                case(
                    "a shape of a type that does not exist",
                    "shapes:\n  A: {source: Missing}",
                    "shapes.A.source: no type 'Missing'",
                ),
                case(
                    "an operation input that is no type or shape",
                    "operations:\n  op: {input: Missing}",
                    "operations.op.input: no type or shape 'Missing'",
                ),
                case(
                    "an operation output that is no type or shape",
                    "operations:\n  op: {output: list<Missing>}",
                    "operations.op.output: no type or shape 'Missing'",
                ),
                case(
                    "an operation subject that does not exist",
                    "operations:\n  op: {subject: Missing}",
                    "operations.op.subject: no type 'Missing'",
                ),
                case(
                    "the entity of a transition that does not exist",
                    "operations:\n  op: {transition: Missing.close}",
                    "operations.op.subject: no type 'Missing'",
                ),
                case(
                    "a REST reference without a source",
                    "operations:\n  op: {rest: /paths/~1items/get}",
                    "operations.op.rest: '/paths/~1items/get' is not a reference <source>#<location>",
                ),
                case(
                    "a REST reference into an SQL source",
                    "operations:\n  op: {rest: db#item}",
                    "operations.op.rest: 'db' is not a loaded OpenAPI or AsyncAPI source",
                ),
                case(
                    "a REST reference into a source that is not declared",
                    "operations:\n  op: {rest: other#/paths/~1items/get}",
                    "operations.op.rest: 'other' is not a loaded OpenAPI or AsyncAPI source",
                ),
                case(
                    "a REST reference that does not resolve",
                    "operations:\n  op: {rest: api#/paths/~1missing/get}",
                    "operations.op.rest: 'api#/paths/~1missing/get' does not resolve",
                ),
                case(
                    "a REST reference that is not an operation",
                    "operations:\n  op: {rest: api#/components/schemas/Item}",
                    "operations.op.rest: 'api#/components/schemas/Item' is not an OpenAPI " +
                        "operation /paths/<path>/<method>",
                ),
                case(
                    "a REST reference to a path item",
                    "operations:\n  op: {rest: api#/paths/~1items}",
                    "operations.op.rest: 'api#/paths/~1items' is not an OpenAPI operation /paths/<path>/<method>",
                ),
                case(
                    "a mapping side that does not resolve",
                    "mappings:\n  - {source: api#/components/schemas/Missing, target: Missing, direction: both}",
                    "mappings[0].source: 'api#/components/schemas/Missing' does not resolve",
                ),
                case(
                    "a mapping target that is no type or shape",
                    "mappings:\n  - {source: api#/components/schemas/Item, target: Missing, direction: both}",
                    "mappings[0].target: no type or shape 'Missing'",
                ),
                case(
                    "the storage of a type that does not exist",
                    "storage:\n  Missing: {table: db#item}",
                    "storage.Missing: no type 'Missing'",
                ),
                case(
                    "a storage table in a document source",
                    "$ITEM\nstorage:\n  Item: {table: api#item}",
                    "storage.Item.table: 'api' is not a loaded SQL source",
                ),
                case(
                    "a storage table that does not exist",
                    "$ITEM\nstorage:\n  Item: {table: db#missing}",
                    "storage.Item.table: 'db#missing' does not resolve",
                ),
                case(
                    "a storage column that does not exist",
                    "$ITEM\nstorage:\n  Item: {table: db#item.missing}",
                    "storage.Item.table: 'db#item.missing' does not resolve",
                ),
                case(
                    "a joined table that does not exist",
                    "$ITEM\nstorage:\n  Item: {table: db#item, columns: {tag: {join: tag_id, table: db#missing, key: id}}}",
                    "storage.Item.columns.tag.table: 'db#missing' does not resolve",
                ),
                case(
                    "a table of a join path that does not exist",
                    "$ITEM\nstorage:\n  Item: {table: db#item, columns: {tag: {join: [{column: a, table: db#missing}], key: id}}}",
                    "storage.Item.columns.tag.join: 'db#missing' does not resolve",
                ),
                case(
                    "a link table that does not exist",
                    "$ITEM\nstorage:\n  Item: {table: db#item, links: {tags: {table: db#missing, ownerKey: item_id}}}",
                    "storage.Item.links.tags.table: 'db#missing' does not resolve",
                ),
                case(
                    "an event payload that is no type or shape",
                    "events:\n  E: {type: e, source: /e, subject: id, data: Missing, channel: events#/channels/items, mode: binary}",
                    "events.E.data: no type or shape 'Missing'",
                ),
                case(
                    "an event channel that does not resolve",
                    "$ITEM\nevents:\n  E: {type: e, source: /e, subject: id, data: Item, channel: events#/channels/missing, mode: binary}",
                    "events.E.channel: 'events#/channels/missing' does not resolve",
                ),
                case(
                    "a stream of an event that does not exist",
                    "streams:\n  s: {events: [Missing], channel: events#/channels/items, transport: sse, replay: PT1H}",
                    "streams.s.events: 'Missing' does not resolve",
                ),
                case(
                    "a stream channel that does not resolve",
                    "streams:\n  s: {events: [], channel: events#/channels/missing, transport: sse, replay: PT1H}",
                    "streams.s.channel: 'events#/channels/missing' does not resolve",
                ),
                case(
                    "a subscription of an event that does not exist",
                    "subscriptions:\n  s: {event: Missing, handler: h, delivery: {attempts: 1, deadLetter: events#/channels/items}}",
                    "subscriptions.s.event: 'Missing' does not resolve",
                ),
                case(
                    "a view of an operation that does not exist",
                    "views:\n  V: {route: /v, load: missing}",
                    "views.V: 'missing' does not resolve",
                ),
                case(
                    "the security scheme of a scope that does not resolve",
                    "auth:\n  providers: {main: {issuer: https://a.invalid}}\n  scopes: {read: {scheme: api#/components/securitySchemes/missing}}",
                    "auth.scopes.read.scheme: 'api#/components/securitySchemes/missing' does not resolve",
                ),
                case(
                    "the security scheme of an API key that does not resolve",
                    "auth:\n  providers: {main: {issuer: https://a.invalid}}\n  apiKeys: {k: {scheme: api#/missing, lookup: l}}",
                    "auth.apiKeys.k.scheme: 'api#/missing' does not resolve",
                ),
            )
    }
}
