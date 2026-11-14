package dev.buijs.fabrio.examples.shipment.application

import dev.buijs.fabrio.events.core.ReceivedEvent
import dev.buijs.fabrio.examples.shipment.contract.events.NotifyWarehouseHandler
import dev.buijs.fabrio.examples.shipment.contract.events.RecordCancellationHandler
import dev.buijs.fabrio.examples.shipment.domain.ShipmentApproved
import dev.buijs.fabrio.examples.shipment.domain.ShipmentCancelled
import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger

/** Rialto events subscriptions A3: the generated consumer deduplicates warehouse notifications. */
@ApplicationScoped
class NotifyWarehouse : NotifyWarehouseHandler {
    override suspend fun handle(event: ReceivedEvent<ShipmentApproved>) {
        Logger.getLogger(NotifyWarehouse::class.java)
            .infof("Warehouse notified: shipment %s, event %s", event.data.shipmentId, event.id)
    }
}

/**
 * Rialto events A3 idempotent consumer: repeated cancellation deliveries only repeat a log entry.
 * Cancellation is recorded by the event itself; this example has no external side effects.
 */
@ApplicationScoped
class RecordCancellation : RecordCancellationHandler {
    override suspend fun handle(event: ReceivedEvent<ShipmentCancelled>) {
        Logger.getLogger(RecordCancellation::class.java)
            .infof(
                "Cancellation recorded: shipment %s, reason %s, event %s",
                event.data.shipmentId, event.data.reason, event.id,
            )
    }
}
