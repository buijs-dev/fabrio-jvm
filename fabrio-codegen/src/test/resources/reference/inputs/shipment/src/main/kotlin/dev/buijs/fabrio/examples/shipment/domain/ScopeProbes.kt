package dev.buijs.fabrio.examples.shipment.domain

import dev.buijs.rialto.annotations.*
import java.util.UUID

// App-only types, not in rialto-spec's Shipment contract: references into a scope (persistence
// P6.11), which ScopeProfileTest reads and writes through the generated accesses.
// ContractEqualityTest names them as documented exceptions.

/** A topic of shipment remarks, in its tenant. */
@Entity
@Storage(table = "db#remark_topic", storageKey = "storage_id")
@Scope("tenant_id", ScopeSource.TENANT)
data class RemarkTopic(
    @Identity @Column("public_id") val id: UUID,
    @Column("title") val title: String,
)

/** A remark on a topic: hidden when its topic is outside the caller's tenant. */
@Entity
@Storage(table = "db#shipment_remark")
@Scope("tenant_id", ScopeSource.TENANT)
data class ShipmentRemark(
    @Identity @Column("id") val id: UUID,
    @Column("text") val text: String,
    @Reference @Join(column = "topic_id", table = "db#remark_topic", key = "public_id") val topic: RemarkTopic,
)

/** A user's pinned filter, in their tenant: a target only its user may refer to. */
@Entity
@Storage(table = "db#search_pin")
@Scope("tenant_id", ScopeSource.TENANT)
@Scope("owner_id", ScopeSource.ACTOR)
data class SearchPin(
    @Identity @Column("id") val id: UUID,
    @Column("label") val label: String,
)

/** An alert of a user on their pin: its pin must be theirs. */
@Entity
@Storage(table = "db#search_alert")
@Scope("tenant_id", ScopeSource.TENANT)
@Scope("owner_id", ScopeSource.ACTOR)
data class SearchAlert(
    @Identity @Column("id") val id: UUID,
    @Column("name") val name: String,
    @Reference @Join(column = "pin_id", table = "db#search_pin", key = "id") val pin: SearchPin,
)

@Command(source = SearchAlert::class, kind = CommandKind.CREATE)
data class CreateSearchAlert(
    @Role(value = RoleKind.APPLICATION, provider = "newSavedSearchId") val id: UUID,
    val name: String,
    val pin: UUID,
)
