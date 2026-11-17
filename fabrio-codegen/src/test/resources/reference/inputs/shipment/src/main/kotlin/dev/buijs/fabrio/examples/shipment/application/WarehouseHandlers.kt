package dev.buijs.fabrio.examples.shipment.application

import dev.buijs.fabrio.entity.core.api.EntityField
import dev.buijs.fabrio.entity.core.api.EntityFieldType
import dev.buijs.fabrio.entity.core.api.SortDirection
import dev.buijs.fabrio.entity.core.api.SortField
import dev.buijs.fabrio.examples.shipment.domain.Warehouse
import dev.buijs.fabrio.examples.shipment.domain.WarehouseId
import dev.buijs.fabrio.examples.shipment.domain.WarehouseReadAccess
import dev.buijs.fabrio.examples.shipment.domain.WarehousesV2Delegate
import dev.buijs.fabrio.search.core.api.ComparisonOperator
import dev.buijs.fabrio.search.core.api.like
import dev.buijs.fabrio.search.core.api.search
import dev.buijs.fabrio.search.core.spi.StandardSearchConfiguration
import jakarta.enterprise.context.ApplicationScoped

/**
 * A warehouse by id through the generated read (U5.11: the warehouse link). Suggestions are the
 * warehouses whose name starts with `q`, at most `maxItems` of them by name (R2.7; U2.11: the
 * warehouse choice). No list operation declares the name filterable, so the search that finds
 * them is this handler's own.
 */
@ApplicationScoped
internal class WarehouseHandlers(
    private val read: WarehouseReadAccess,
) : WarehousesV2Delegate {
    override suspend fun getWarehouse(warehouseId: WarehouseId): Warehouse? = read.findById(warehouseId)

    override suspend fun suggestWarehouses(q: String): List<Warehouse> =
        read
            .findAll(
                search(SUGGESTIONS) {
                    limit = MAX_SUGGESTIONS
                    filter { NAME.like("${likeLiteral(q)}%") }
                    sort { NAME.asc() }
                },
            ).items
}

/** `maxItems` of `suggestWarehouses`. */
private const val MAX_SUGGESTIONS = 10

private val ID = EntityField(name = "id", type = EntityFieldType.UUID, sortable = true)

private val NAME =
    EntityField(
        name = "name",
        type = EntityFieldType.STRING,
        sortable = true,
        filterable = true,
        operators = setOf(ComparisonOperator.LIKE),
    )

/** A name prefix, by name, then by id. */
private val SUGGESTIONS =
    StandardSearchConfiguration(
        defaultLimit = MAX_SUGGESTIONS,
        maxLimit = MAX_SUGGESTIONS,
        defaultSort = listOf(SortField(NAME, SortDirection.ASC)),
        fields = listOf(ID, NAME),
        tiebreakerField = ID,
    )
