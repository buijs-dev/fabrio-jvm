package dev.buijs.fabrio.examples.shipment.application

import dev.buijs.fabrio.entity.core.spi.TypeGenerator
import dev.buijs.fabrio.examples.shipment.domain.ShipmentId
import jakarta.inject.Singleton
import java.util.UUID

/** Supplies the application-generated shipment identity (domain value provider). */
@Singleton
class NewShipmentId : TypeGenerator<ShipmentId>("newShipmentId") {
    override fun generate(): ShipmentId = ShipmentId(UUID.randomUUID())
}

/** Supplies the application-generated identity of an owned shipment line. */
@Singleton
class NewLineId : TypeGenerator<UUID>("newLineId") {
    override fun generate(): UUID = UUID.randomUUID()
}

/** Supplies the application-generated identity of an owned shipment package. */
@Singleton
class NewPackageId : TypeGenerator<UUID>("newPackageId") {
    override fun generate(): UUID = UUID.randomUUID()
}

/** Supplies the application-generated identity of an owned customs declaration. */
@Singleton
class NewDeclarationId : TypeGenerator<UUID>("newDeclarationId") {
    override fun generate(): UUID = UUID.randomUUID()
}

/** Supplies the application-generated identity of a saved search. */
@Singleton
class NewSavedSearchId : TypeGenerator<UUID>("newSavedSearchId") {
    override fun generate(): UUID = UUID.randomUUID()
}
