package dev.buijs.fabrio.examples.shipment.application

import dev.buijs.fabrio.examples.shipment.api.v1.model.CountV1
import dev.buijs.fabrio.examples.shipment.api.v1.model.ShipmentPageV1
import dev.buijs.fabrio.examples.shipment.api.v1.model.mapper.toShipmentSummaryV1
import dev.buijs.fabrio.examples.shipment.contract.events.ApproveShipmentEvents
import dev.buijs.fabrio.examples.shipment.domain.CreateShipmentCommand
import dev.buijs.fabrio.examples.shipment.domain.Shipment
import dev.buijs.fabrio.examples.shipment.domain.ShipmentCreateAccess
import dev.buijs.fabrio.examples.shipment.domain.ShipmentDeleteAccess
import dev.buijs.fabrio.examples.shipment.domain.ShipmentId
import dev.buijs.fabrio.examples.shipment.domain.ShipmentReadAccess
import dev.buijs.fabrio.examples.shipment.domain.ShipmentSummary
import dev.buijs.fabrio.examples.shipment.domain.ShipmentUpdateAccess
import dev.buijs.fabrio.examples.shipment.domain.ShipmentsV1Delegate
import dev.buijs.fabrio.examples.shipment.domain.UpdateShipment
import dev.buijs.fabrio.http.wire.Presence
import dev.buijs.fabrio.persistence.core.api.Versioned
import dev.buijs.fabrio.search.core.api.SearchRequest
import jakarta.enterprise.context.ApplicationScoped

/**
 * Rialto operation handlers for v1: commands and preconditions go to generated accesses.
 * Patches pass through unchanged; listing maps the summary view and preserves generated cursors.
 */
@ApplicationScoped
internal class ShipmentV1Handlers(
    private val read: ShipmentReadAccess,
    private val create: ShipmentCreateAccess,
    private val update: ShipmentUpdateAccess,
    private val delete: ShipmentDeleteAccess,
    private val operations: ShipmentOperations,
) : ShipmentsV1Delegate {
    override suspend fun listShipments(search: SearchRequest): ShipmentPageV1 {
        val page = read.listShipments(search)
        return ShipmentPageV1(
            items = page.items.map { it.toShipmentSummaryV1() },
            nextCursor = Presence.of(page.nextCursor),
            prevCursor = Presence.of(page.previousCursor),
        )
    }

    override suspend fun createShipment(
        createShipmentCommand: CreateShipmentCommand,
    ): Versioned<Shipment> =
        create.create(createShipmentCommand)

    override suspend fun countShipments(state: String?, search: SearchRequest): CountV1 =
        CountV1().count(operations.count(search, state))

    override suspend fun getShipment(shipmentId: ShipmentId): Versioned<Shipment>? =
        read.findById(shipmentId)

    override suspend fun deleteShipment(subject: Shipment, ifMatch: String, shipmentId: ShipmentId) =
        delete.delete(shipmentId, ifMatch)

    override suspend fun patchShipmentNote(
        ifMatch: String,
        shipmentId: ShipmentId,
        updateShipment: UpdateShipment,
    ): Versioned<Shipment> = update.update(shipmentId, updateShipment, ifMatch)

    override suspend fun approveShipment(
        ifMatch: String,
        shipmentId: ShipmentId,
        events: ApproveShipmentEvents,
    ): Versioned<Shipment>? = operations.approve(ifMatch, shipmentId, events)
}
