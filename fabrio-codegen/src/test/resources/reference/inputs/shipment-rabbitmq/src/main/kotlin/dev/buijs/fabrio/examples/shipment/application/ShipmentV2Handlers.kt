package dev.buijs.fabrio.examples.shipment.application

import dev.buijs.fabrio.examples.shipment.api.v2.model.CountV2
import dev.buijs.fabrio.examples.shipment.api.v2.model.ShipmentPageV2
import dev.buijs.fabrio.examples.shipment.api.v2.model.mapper.toShipmentSummaryV2
import dev.buijs.fabrio.examples.shipment.contract.ShipmentErrorCode
import dev.buijs.fabrio.examples.shipment.contract.events.ApproveShipmentEvents
import dev.buijs.fabrio.examples.shipment.contract.events.CancelShipmentEvents
import dev.buijs.fabrio.examples.shipment.contract.events.DecideShipmentEvents
import dev.buijs.fabrio.examples.shipment.domain.CreateShipmentCommand
import dev.buijs.fabrio.examples.shipment.domain.DeliveryProofBlobAccess
import dev.buijs.fabrio.examples.shipment.domain.DeliveryProofFile
import dev.buijs.fabrio.examples.shipment.domain.Shipment
import dev.buijs.fabrio.examples.shipment.domain.ShipmentCreateAccess
import dev.buijs.fabrio.examples.shipment.domain.ShipmentDeleteAccess
import dev.buijs.fabrio.examples.shipment.domain.ShipmentId
import dev.buijs.fabrio.examples.shipment.domain.ShipmentLinkAccess
import dev.buijs.fabrio.examples.shipment.domain.ShipmentReadAccess
import dev.buijs.fabrio.examples.shipment.domain.ShipmentSummary
import dev.buijs.fabrio.examples.shipment.domain.ShipmentTransition
import dev.buijs.fabrio.examples.shipment.domain.ShipmentUpdateAccess
import dev.buijs.fabrio.examples.shipment.domain.ShipmentsV2Delegate
import dev.buijs.fabrio.examples.shipment.domain.TrackingScan
import dev.buijs.fabrio.examples.shipment.domain.UpdateShipment
import dev.buijs.fabrio.examples.shipment.domain.UpdateShipmentLine
import dev.buijs.fabrio.examples.shipment.domain.UploadDeliveryProof
import dev.buijs.fabrio.entity.core.api.PatchField
import dev.buijs.fabrio.http.problem.ApplicationProblemException
import dev.buijs.fabrio.http.problem.NotFoundException
import dev.buijs.fabrio.http.wire.Presence
import dev.buijs.fabrio.persistence.core.api.Versioned
import dev.buijs.fabrio.search.core.api.SearchRequest
import io.quarkus.security.identity.CurrentIdentityAssociation
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID
import org.jboss.logging.Logger

/**
 * Rialto operation handlers for v2: commands and preconditions go to generated accesses.
 * Patches pass through unchanged; listing maps the summary view and preserves generated cursors.
 * A delivery proof goes to the generated blob access, for a shipment the caller can see. A tag is
 * linked or unlinked through the generated link access (L6). A line is removed by updating the
 * shipment without it; the generated resource already found the line in this shipment (R1.5).
 */
@ApplicationScoped
internal class ShipmentV2Handlers(
    private val read: ShipmentReadAccess,
    private val create: ShipmentCreateAccess,
    private val update: ShipmentUpdateAccess,
    private val delete: ShipmentDeleteAccess,
    private val operations: ShipmentOperations,
    private val proofs: DeliveryProofBlobAccess,
    private val links: ShipmentLinkAccess,
    private val identity: CurrentIdentityAssociation,
    private val intake: ShipmentIntake,
) : ShipmentsV2Delegate {
    override suspend fun listShipments(search: SearchRequest): ShipmentPageV2 {
        val page = read.listShipments(search)
        return ShipmentPageV2(
            items = page.items.map { it.toShipmentSummaryV2() },
            nextCursor = Presence.of(page.nextCursor),
            prevCursor = Presence.of(page.previousCursor),
        )
    }

    override suspend fun createShipment(
        createShipmentCommand: CreateShipmentCommand,
    ): Versioned<Shipment> {
        intake.checkRecipient(createShipmentCommand)
        val created = create.create(createShipmentCommand)
        // The verified caller: the principal of the token; a warehouse key only has to be valid (S48).
        val caller = identity.deferredIdentity.awaitSuspending().principal.name
        Logger.getLogger(ShipmentV2Handlers::class.java)
            .infof("Shipment %s created by %s", created.entity.id.value, caller)
        return created
    }

    override suspend fun countShipments(state: String?, search: SearchRequest): CountV2 =
        CountV2().count(operations.count(search, state))

    override suspend fun getShipment(shipmentId: ShipmentId): Versioned<Shipment>? =
        read.findById(shipmentId)

    /**
     * R1.5: both path parameters are conditions. The shipment is read with its tenant (P6.11);
     * a scan of another shipment, or of none, is absent: 404.
     */
    override suspend fun getShipmentScan(
        element: TrackingScan, shipmentId: ShipmentId, scanId: UUID,
    ): TrackingScan = element

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

    override suspend fun decideShipment(
        ifMatch: String,
        shipmentId: ShipmentId,
        updateShipment: UpdateShipment,
        transition: ShipmentTransition,
        events: DecideShipmentEvents,
    ): Versioned<Shipment>? = operations.decide(ifMatch, shipmentId, updateShipment, transition, events)

    override suspend fun patchShipmentContents(
        ifMatch: String,
        shipmentId: ShipmentId,
        updateShipment: UpdateShipment,
    ): Versioned<Shipment> = update.update(shipmentId, updateShipment, ifMatch)

    override suspend fun cancelShipment(
        ifMatch: String,
        shipmentId: ShipmentId,
        events: CancelShipmentEvents,
    ): Versioned<Shipment>? = operations.cancel(ifMatch, shipmentId, events)

    override suspend fun createShipments(command: CreateShipmentCommand): Versioned<Shipment> {
        intake.checkBatchItem(command)
        return create.create(command)
    }

    override suspend fun uploadDeliveryProof(
        shipmentId: ShipmentId,
        uploadDeliveryProof: UploadDeliveryProof,
    ) {
        read.findVersionById(shipmentId) ?: throw ApplicationProblemException(ShipmentErrorCode.SHIPMENT_MISSING)
        proofs.put(uploadDeliveryProof.shipment, uploadDeliveryProof.file)
    }

    override suspend fun addShipmentTag(shipmentId: ShipmentId, tagId: UUID) {
        links.addTags(shipmentId, tagId)
    }

    override suspend fun removeShipmentTag(shipmentId: ShipmentId, tagId: UUID) {
        links.removeTags(shipmentId, tagId)
    }

    /**
     * R1.5: the resource read the shipment in the caller's tenant and found [lineId] among its
     * lines, else 404. The operation has no precondition (the lines tab knows no ETag), so the
     * edit is checked against the version read here. The other lines are kept by their ids; the
     * line no element names any more is deleted (reconcile by identity).
     */
    override suspend fun removeShipmentLine(subject: Shipment, shipmentId: ShipmentId, lineId: UUID) {
        val current = read.findById(shipmentId) ?: throw NotFoundException()
        val lines =
            current.entity.lines
                .filterNot { it.id == lineId }
                .map { UpdateShipmentLine(id = it.id, quantity = it.quantity) }
        update.update(shipmentId, UpdateShipment(lines = PatchField.Value(lines)), current.version)
    }

    override suspend fun getDeliveryProof(shipmentId: ShipmentId): DeliveryProofFile? {
        read.findVersionById(shipmentId) ?: throw ApplicationProblemException(ShipmentErrorCode.SHIPMENT_MISSING)
        return proofs.findById(shipmentId)?.let { DeliveryProofFile(it.file) }
    }
}
