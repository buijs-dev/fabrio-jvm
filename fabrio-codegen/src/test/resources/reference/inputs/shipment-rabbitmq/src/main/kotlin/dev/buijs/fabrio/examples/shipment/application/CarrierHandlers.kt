package dev.buijs.fabrio.examples.shipment.application

import dev.buijs.fabrio.examples.shipment.api.v2.model.CarrierPageV2
import dev.buijs.fabrio.examples.shipment.api.v2.model.mapper.toCarrierV2
import dev.buijs.fabrio.examples.shipment.domain.Carrier
import dev.buijs.fabrio.examples.shipment.domain.CarrierDeleteAccess
import dev.buijs.fabrio.examples.shipment.domain.CarrierEntityModel
import dev.buijs.fabrio.examples.shipment.domain.CarrierReadAccess
import dev.buijs.fabrio.examples.shipment.domain.CarrierUpdateAccess
import dev.buijs.fabrio.examples.shipment.domain.CarrierV2Mappings
import dev.buijs.fabrio.examples.shipment.domain.CarriersV2Delegate
import dev.buijs.fabrio.examples.shipment.domain.UpdateCarrier
import dev.buijs.fabrio.search.core.api.SearchRequest
import dev.buijs.fabrio.search.core.api.like
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Rialto authored replacement and list operations: pass the command and the page through. A carrier
 * by name reads through the generated read of its unique key (R1.6). A delete is the generated
 * soft delete: the row stays, marked with who deleted it (P6.1, P6.10). A carrier by id also reads a
 * deleted one (`deleted: include`, P6.3), and a restore clears the marker (L3.7). Suggestions are
 * the live carriers whose name starts with `q`, at most `maxItems` of them by name (R2.7).
 */
@ApplicationScoped
internal class CarrierHandlers(
    private val read: CarrierReadAccess,
    private val update: CarrierUpdateAccess,
    private val delete: CarrierDeleteAccess,
    private val mappings: CarrierV2Mappings,
) : CarriersV2Delegate {
    override suspend fun replaceCarrier(carrierId: UUID, updateCarrier: UpdateCarrier): Carrier =
        update.update(carrierId, updateCarrier, null)

    override suspend fun getCarrierByName(subject: Carrier, name: String): Carrier? = subject

    override suspend fun deleteCarrier(subject: Carrier, carrierId: UUID) = delete.delete(carrierId, null)

    override suspend fun getCarrier(carrierId: UUID): Carrier? =
        read.findByIdIncludingDeleted(carrierId)

    override suspend fun restoreCarrier(subject: Carrier, carrierId: UUID): Carrier? {
        delete.restore(carrierId)
        return read.findById(carrierId)
    }

    override suspend fun suggestCarriers(q: String): List<Carrier> =
        read
            .listCarriers {
                limit = SUGGESTIONS
                filter { CarrierEntityModel.fields.name.like("${likeLiteral(q)}%") }
                sort { CarrierEntityModel.fields.name.asc() }
            }.items

    // Offset pages with the inline count (Q4.4, Q5.1): the generated read gives items, more and total.
    override suspend fun listCarriers(search: SearchRequest): CarrierPageV2 {
        val page = read.listCarriers(search)
        return CarrierPageV2(
            items = page.items.map { it.toCarrierV2(mappings) },
            more = page.more,
            total = Math.toIntExact(checkNotNull(page.total)),
        )
    }
}

/** `maxItems` of `suggestCarriers`. */
private const val SUGGESTIONS = 10

/** [text] matched literally by a `LIKE` pattern escaped with a backslash. */
internal fun likeLiteral(text: String): String =
    text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
