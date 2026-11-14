package dev.buijs.fabrio.examples.shipment.application

import dev.buijs.fabrio.examples.shipment.contract.ShipmentErrorCode
import dev.buijs.fabrio.examples.shipment.domain.CompanyRecipient
import dev.buijs.fabrio.examples.shipment.domain.CreateShipmentCommand
import dev.buijs.fabrio.examples.shipment.domain.PersonRecipient
import dev.buijs.fabrio.http.problem.ApplicationProblemException
import dev.buijs.fabrio.http.problem.FieldError
import dev.buijs.fabrio.security.quarkus.CurrentTenant
import io.smallrye.config.ConfigMapping
import jakarta.enterprise.context.ApplicationScoped
import java.util.Optional
import java.util.UUID

/**
 * What the intake of a shipment depends on outside the contract, as configuration: recipients the
 * carrier refuses, warehouses whose integration is down, tenants that used up their quota. None by
 * default; the tests configure them (S41).
 */
@ConfigMapping(prefix = "shipment.intake")
internal interface ShipmentIntakeConfig {
    fun refusedRecipients(): Optional<List<String>>

    fun unavailableWarehouses(): Optional<List<UUID>>

    fun quotaReachedTenants(): Optional<List<String>>
}

/**
 * The authored checks of a create before it is written, each with its declared application code
 * (Rialto errors E1.1, S41): `invalidRecipient` (422) for a refused recipient, and for a batch item
 * also `warehouseUnavailable` (503) and `shipmentQuota` (429).
 */
@ApplicationScoped
internal class ShipmentIntake(
    private val config: ShipmentIntakeConfig,
    private val currentTenant: CurrentTenant,
) {
    /** The checks of `createShipment`, which declares `invalidRecipient`. */
    fun checkRecipient(command: CreateShipmentCommand) {
        val name =
            when (val recipient = command.recipient) {
                is PersonRecipient -> recipient.name
                is CompanyRecipient -> recipient.companyName
            }
        if (name in config.refusedRecipients().orElse(emptyList())) {
            throw ApplicationProblemException(
                ShipmentErrorCode.INVALID_RECIPIENT,
                "The carrier does not deliver to this recipient",
                listOf(FieldError.body(listOf("recipient"), ShipmentErrorCode.INVALID_RECIPIENT)),
            )
        }
    }

    /** The checks of a `createShipments` item, which declares all three codes. */
    fun checkBatchItem(command: CreateShipmentCommand) {
        if (currentTenant.require() in config.quotaReachedTenants().orElse(emptyList())) {
            throw ApplicationProblemException(ShipmentErrorCode.SHIPMENT_QUOTA)
        }
        val warehouse = command.warehouse?.value
        if (warehouse != null && warehouse in config.unavailableWarehouses().orElse(emptyList())) {
            throw ApplicationProblemException(ShipmentErrorCode.WAREHOUSE_UNAVAILABLE)
        }
        checkRecipient(command)
    }
}
