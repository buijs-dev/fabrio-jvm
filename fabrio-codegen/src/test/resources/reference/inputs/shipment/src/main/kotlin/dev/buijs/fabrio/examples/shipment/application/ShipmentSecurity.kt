package dev.buijs.fabrio.examples.shipment.application

import dev.buijs.fabrio.events.core.CloudEvent
import dev.buijs.fabrio.examples.shipment.contract.events.ShipmentSocketAccess
import dev.buijs.fabrio.examples.shipment.contract.events.ShipmentUpdatesAccess
import dev.buijs.fabrio.examples.shipment.domain.CanApproveShipmentPolicy
import dev.buijs.fabrio.examples.shipment.domain.Priority
import dev.buijs.fabrio.examples.shipment.domain.ShipmentId
import dev.buijs.fabrio.examples.shipment.domain.ShipmentReadAccess
import dev.buijs.fabrio.security.ServicePrincipal
import jakarta.enterprise.context.ApplicationScoped
import java.security.Principal
import java.util.UUID
import org.eclipse.microprofile.jwt.JsonWebToken

/**
 * Rialto auth S3.4: express approval requires the token's supervisor realm role.
 * Fabrio maps both realm roles and OAuth scopes to identity roles, so `hasRole` would also
 * accept a scope named `supervisor`. Check the verified token's `realm_access.roles` itself.
 */
@ApplicationScoped
internal class ShipmentApprovalPolicy(
    private val shipments: ShipmentReadAccess,
    private val token: JsonWebToken,
) : CanApproveShipmentPolicy {
    override suspend fun allows(principal: Principal, resource: ShipmentId): Boolean {
        val shipment = shipments.findById(resource)?.entity ?: return false
        return principal !is ServicePrincipal &&
            (shipment.priority == Priority.Standard || hasSupervisorRealmRole())
    }

    private fun hasSupervisorRealmRole(): Boolean {
        val realm = token.getClaim<Any?>("realm_access")
        val roles = when (realm) {
            is jakarta.json.JsonObject -> realm.getJsonArray("roles")
            is Map<*, *> -> realm["roles"]
            else -> null
        }
        return when (roles) {
            is jakarta.json.JsonArray -> roles.getValuesAs(jakarta.json.JsonString::class.java)
                .any { it.string == "supervisor" }
            is Collection<*> -> "supervisor" in roles
            else -> false
        }
    }

}

/** Rialto events S4.4: tenant isolation is enforced by Fabrio's stream hub. */
@ApplicationScoped
internal class ShipmentStreamAccess : ShipmentUpdatesAccess, ShipmentSocketAccess {
    override suspend fun canRead(principal: Principal, event: CloudEvent): Boolean = true
}
