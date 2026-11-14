package dev.buijs.fabrio.examples.shipment.application

import dev.buijs.fabrio.persistence.core.api.Versioned
import dev.buijs.fabrio.examples.shipment.contract.ShipmentErrorCode
import dev.buijs.fabrio.examples.shipment.contract.events.ApproveShipmentEvents
import dev.buijs.fabrio.examples.shipment.contract.events.CancelShipmentEvents
import dev.buijs.fabrio.examples.shipment.contract.events.DecideShipmentEvents
import dev.buijs.fabrio.examples.shipment.domain.Shipment
import dev.buijs.fabrio.examples.shipment.domain.ShipmentApproved
import dev.buijs.fabrio.examples.shipment.domain.ShipmentCancelled
import dev.buijs.fabrio.examples.shipment.domain.ShipmentId
import dev.buijs.fabrio.examples.shipment.domain.ShipmentReadAccess
import dev.buijs.fabrio.examples.shipment.domain.ShipmentTransition
import dev.buijs.fabrio.examples.shipment.domain.ShipmentUpdateAccess
import dev.buijs.fabrio.examples.shipment.domain.Status
import dev.buijs.fabrio.examples.shipment.domain.UpdateShipment
import dev.buijs.fabrio.http.problem.ApplicationProblemException
import dev.buijs.fabrio.search.core.api.SearchCriteria
import dev.buijs.fabrio.search.core.api.SearchException
import dev.buijs.fabrio.search.core.api.SearchRequest
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant

/**
 * Rialto authored operations and emitted events: approve a nonempty draft, or reject a draft,
 * through its generated transition, which alone sets the state. Cancellation records an event without changing approval
 * status. The generated resource wraps each write and its event in one transaction. Pass the
 * precondition to the update access.
 */
@ApplicationScoped
internal class ShipmentOperations(
    private val read: ShipmentReadAccess,
    private val update: ShipmentUpdateAccess,
) {
    suspend fun approve(
        version: String,
        id: ShipmentId,
        events: ApproveShipmentEvents,
    ): Versioned<Shipment>? =
        decide(version, id, UpdateShipment(), ShipmentTransition.APPROVE) {
            events.shipmentApproved(it)
        }

    /**
     * Rialto lifecycle L5.6: the decision in the body selects approve or reject. Only an approval
     * needs a line; the rule `approve` alone emits `ShipmentApproved` (L5.10).
     */
    suspend fun decide(
        version: String,
        id: ShipmentId,
        command: UpdateShipment,
        transition: ShipmentTransition,
        events: DecideShipmentEvents,
    ): Versioned<Shipment>? =
        decide(version, id, command, transition) { events.shipmentApproved(it) }

    private suspend fun decide(
        version: String,
        id: ShipmentId,
        command: UpdateShipment,
        transition: ShipmentTransition,
        approved: suspend (ShipmentApproved) -> Unit,
    ): Versioned<Shipment>? {
        val shipment = read.findById(id)?.entity ?: return null
        if (transition == ShipmentTransition.APPROVE && shipment.lines.isEmpty()) {
            throw ApplicationProblemException(ShipmentErrorCode.NO_LINES)
        }
        // The generated transition checks the version and `draft`, sets the new state and, for an
        // approval, `approvedAt` and `approvedBy` in the same statement (L5.2, L5.7).
        val result = update.transition(id, command, version, transition)
        if (transition == ShipmentTransition.APPROVE) {
            approved(ShipmentApproved(id, checkNotNull(result.entity.approvedAt)))
        }
        return result
    }

    suspend fun cancel(
        version: String,
        id: ShipmentId,
        events: CancelShipmentEvents,
    ): Versioned<Shipment>? {
        if (read.findById(id) == null) return null
        val result = update.update(id, UpdateShipment(), version)
        events.shipmentCancelled(ShipmentCancelled(id, Instant.now(), "Cancelled by request"))
        return result
    }

    /**
     * Rialto list count operation: counts for the filter of `listShipments`, as that list applies it
     * (query Q5.2), with the older `state` parameter on top.
     */
    suspend fun count(
        search: SearchRequest,
        state: String?,
    ): Long {
        // `state` goes into the filter as a known value only, never as text of the caller's.
        val status =
            state?.let { value ->
                Status.entries.firstOrNull { it.name == value }
                    ?: throw SearchException("Unknown state '$value'", parameter = "state")
            }
        val filter = search.criteria?.filter?.takeIf { it.isNotBlank() }
        val parts = listOfNotNull(filter?.let { "($it)" }, status?.let { "state==${it.name}" })
        return read.listShipmentsCount(SearchRequest(criteria = SearchCriteria(filter = parts.joinToString(";").ifEmpty { null })))
    }
}
