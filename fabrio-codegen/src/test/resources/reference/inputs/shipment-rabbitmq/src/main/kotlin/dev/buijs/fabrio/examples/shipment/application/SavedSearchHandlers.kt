package dev.buijs.fabrio.examples.shipment.application

import dev.buijs.fabrio.examples.shipment.domain.CreateSavedSearchCommand
import dev.buijs.fabrio.examples.shipment.domain.SavedSearch
import dev.buijs.fabrio.examples.shipment.domain.SavedSearchCreateAccess
import dev.buijs.fabrio.examples.shipment.domain.SavedSearchDeleteAccess
import dev.buijs.fabrio.examples.shipment.domain.SavedSearchesV2Delegate
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * A user's saved searches (P6.11): the generated accesses filter every read and write on the
 * tenant and the verified actor, and an insert stores both, so another user's saved search (or
 * another tenant's) is absent. The list is bounded by `maxItems` (R2.7).
 */
@ApplicationScoped
internal class SavedSearchHandlers(
    private val create: SavedSearchCreateAccess,
    private val delete: SavedSearchDeleteAccess,
) : SavedSearchesV2Delegate {
    override suspend fun createSavedSearch(
        createSavedSearchCommand: CreateSavedSearchCommand,
    ): SavedSearch = create.create(createSavedSearchCommand)

    override suspend fun deleteSavedSearch(subject: SavedSearch, savedSearchId: UUID) = delete.delete(savedSearchId, null)
}
