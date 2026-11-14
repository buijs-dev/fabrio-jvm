package dev.buijs.fabrio.examples.shipment.application

import dev.buijs.fabrio.examples.shipment.contract.WarehouseKeys
import dev.buijs.fabrio.security.ApiKey
import dev.buijs.fabrio.security.HashedApiKeys
import dev.buijs.fabrio.security.ServicePrincipal
import io.smallrye.config.ConfigMapping
import io.smallrye.config.WithDefaults
import jakarta.enterprise.context.ApplicationScoped

/** The warehouse keys, per service: `shipment.warehouse.keys.<service>=<key>[,<key>]`. */
@ConfigMapping(prefix = "shipment.warehouse")
internal interface WarehouseKeyConfig {
    /** Per service, its valid keys; none by default. */
    @WithDefaults
    fun keys(): Map<String, List<String>>
}

/**
 * Rialto auth S6.2: the lookup `WarehouseKeys` of the `X-Warehouse-Key` header. A known key is the
 * service it is configured for, with `shipments:write`; with a token of that scope it may create a
 * shipment (S48). The caller stays the one the token names; the key only has to be valid (S6.3).
 *
 * Which keys are valid is this seam's, not the contract's: here they come from configuration (a
 * secret in a deployment), only their SHA-256 digests are kept, and they are compared in constant
 * time. Rotation is two keys of one service valid at once; revocation is removing one. A deployment
 * that stores keys elsewhere replaces this bean with one backed by its key store.
 */
@ApplicationScoped
internal class ConfiguredWarehouseKeys(
    config: WarehouseKeyConfig,
) : WarehouseKeys {
    private val keys =
        HashedApiKeys(
            config.keys().flatMap { (service, keys) ->
                keys.map { it to ServicePrincipal(service, setOf("shipments:write")) }
            }.toMap(),
        )

    override suspend fun resolve(key: ApiKey): ServicePrincipal? = keys.resolve(key)
}
