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

import dev.buijs.fabrio.codegen.frontend.ContractFrontend
import dev.buijs.fabrio.codegen.frontend.ContractLoadException
import dev.buijs.fabrio.codegen.reference.Reference
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class ContractIrTest {
    private val shipment: ContractIr by lazy {
        ContractFrontend.load(Reference.cases.single { it.name == "example-shipment" }.contract)
    }

    @TestFactory
    fun `every reference contract builds an IR with every section`(): List<DynamicTest> =
        Reference.cases.map { case ->
            DynamicTest.dynamicTest(case.name) {
                val ir = ContractFrontend.load(case.contract)
                val contract = ir.contract
                assertThat(ir.sources.keys).containsExactlyElementsOf(contract.sources.keys)
                assertThat(ir.types.keys).containsExactlyElementsOf(contract.types.keys)
                assertThat(ir.shapes.keys).containsExactlyElementsOf(contract.shapes.keys)
                assertThat(ir.operations.keys).containsExactlyElementsOf(contract.operations.keys)
                assertThat(ir.mappings).hasSameSizeAs(contract.mappings)
                assertThat(ir.storage.keys).containsExactlyElementsOf(contract.storage.keys)
                assertThat(ir.events.keys).containsExactlyElementsOf(contract.events.keys)
                assertThat(ir.streams.keys).containsExactlyElementsOf(contract.streams.keys)
                assertThat(
                    ir.subscriptions.keys,
                ).containsExactlyElementsOf(contract.subscriptions.keys)
                assertThat(
                    ir.presentation.views.keys,
                ).containsExactlyElementsOf(contract.views.keys)
                assertThat(ir.auth?.declaration).isEqualTo(contract.auth)
            }
        }

    @Test
    fun `an operation is bound to the OpenAPI operation of each API source`() {
        val operation = shipment.operation("getShipment")

        assertThat(operation.rest.map { Triple(it.source, it.method, it.path) })
            .containsExactly(
                Triple("apiV1", "get", "/shipments/{shipmentId}"),
                Triple("api", "get", "/shipments/{shipmentId}"),
            )
        assertThat(
            operation.rest.map {
                it.reference.element
                    .path("operationId")
                    .asText()
            },
        ).allMatch { it.isNotEmpty() }

        assertThat(
            operation.output,
        ).isEqualTo(DeclarationRef.Type(TypeRef.Named("Shipment", TypeKind.ENTITY)))
        assertThat(operation.outputList).isFalse()
    }

    @Test
    fun `types resolve their properties and variants`() {
        val recipient = shipment.type("Recipient") as TypeIr.Union
        assertThat(recipient.variants)
            .containsExactly(
                java.util.Map.entry("person", TypeRef.Named("PersonRecipient", TypeKind.VALUE)),
                java.util.Map.entry("company", TypeRef.Named("CompanyRecipient", TypeKind.VALUE)),
            )

        val entity = shipment.type("Shipment") as TypeIr.Entity
        assertThat(entity.identity.map { it.name }).containsExactly("id")
        assertThat(
            entity.properties.values
                .flatMap { it.type.named() }
                .map { it.name },
        ).contains("Recipient")
        assertThat(
            shipment.typesOf<TypeIr.Entity>().map { it.name },
        ).contains("Shipment", "Carrier")
    }

    @Test
    fun `storage, events, mappings and auth resolve into their sources`() {
        val rateSheet = shipment.storage.getValue("RateSheet")
        assertThat(rateSheet.table.table.name).isEqualTo("rate_sheet")
        assertThat(rateSheet.tables.keys).containsExactly("db#rate_sheet_zone")
        assertThat(
            rateSheet.tables
                .getValue("db#rate_sheet_zone")
                .table.columns,
        ).containsKeys("sheet_id", "zone_id")

        val approved = shipment.events.getValue("ShipmentApproved")
        assertThat(approved.channel.source).isEqualTo("events")
        assertThat(approved.channel.pointer).isEqualTo("/channels/shipmentEvents")
        assertThat(approved.data.name).isEqualTo("ShipmentApproved")

        val address = shipment.mappings.first()
        assertThat(
            (address.source as MappingSide.Element)
                .reference.element
                .path("type")
                .asText(),
        ).isEqualTo("object")
        assertThat(
            address.target,
        ).isEqualTo(
            MappingSide.Contract(DeclarationRef.Type(TypeRef.Named("Address", TypeKind.VALUE))),
        )

        assertThat(
            shipment.auth!!
                .apiKeySchemes
                .getValue("warehouseKey")
                .element
                .path("type")
                .asText(),
        ).isEqualTo("apiKey")
        assertThat(shipment.source("db")).isInstanceOf(SourceIr.Sql::class.java)
    }

    @Test
    fun `an invalid contract is not read into an IR`(
        @TempDir
        directory: Path,
    ) {
        val contract = directory.resolve("broken.rialto.yaml")
        contract.writeText(
            "rialto: 1.0.0\ninfo: {name: broken, version: 1.0.0}\ntypes:\n  A: {kind: value, properties: {b: {type: Missing}}}\n",
        )

        assertThatThrownBy { ContractFrontend.load(contract) }
            .isInstanceOf(ContractLoadException::class.java)
            .satisfies({ assertThat((it as ContractLoadException).diagnostics).isNotEmpty() })
    }
}
