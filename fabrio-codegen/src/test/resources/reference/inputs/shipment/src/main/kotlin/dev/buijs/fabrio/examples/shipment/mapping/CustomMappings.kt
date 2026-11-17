package dev.buijs.fabrio.examples.shipment.mapping

import dev.buijs.fabrio.examples.shipment.api.v1.model.CreateShipmentV1
import dev.buijs.fabrio.examples.shipment.domain.CreateShipmentV1CustomMapping
import dev.buijs.fabrio.examples.shipment.domain.Priority
import dev.buijs.fabrio.examples.shipment.domain.Shipment
import dev.buijs.fabrio.examples.shipment.domain.ShipmentV1CustomMapping
import dev.buijs.fabrio.examples.shipment.domain.ShipmentV2CustomMapping
import jakarta.enterprise.context.ApplicationScoped

/** Rialto mapping M3 custom properties: keep the label; the public id is the legacy reference. */
@ApplicationScoped
class ShipmentCustomMappings : ShipmentV1CustomMapping, ShipmentV2CustomMapping {
    override fun labelToDto(source: Shipment): String = source.label

    override fun legacyReferenceToDto(source: Shipment): String = source.id.value.toString()
}

/** Rialto mapping M3 custom priority: v1 has no priority input and creates standard shipments. */
@ApplicationScoped
class CreateShipmentCustomMappings : CreateShipmentV1CustomMapping {
    override fun priorityToTarget(source: CreateShipmentV1): Priority = Priority.Standard
}
