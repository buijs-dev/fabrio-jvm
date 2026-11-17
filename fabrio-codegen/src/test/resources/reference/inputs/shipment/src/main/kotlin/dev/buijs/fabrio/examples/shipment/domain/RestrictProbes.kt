package dev.buijs.fabrio.examples.shipment.domain

import dev.buijs.rialto.annotations.*
import java.util.UUID

// App-only types, not in rialto-spec's Shipment contract: a to-many child and a link that restrict
// deleting their owner (persistence P4.9, P4.12), which RestrictProfileTest deletes through the
// generated accesses. ContractEqualityTest names them as documented exceptions.

/** A zone a rate sheet links to; the link keeps the sheet. */
@Entity
@Storage(table = "db#rate_zone")
data class RateZone(
    @Identity @Column("id") val id: UUID,
    @Column("name") val name: String,
)

/** An entry of a rate sheet; while one exists, the sheet cannot be deleted. */
@Entity
@Storage(table = "db#rate_sheet_entry")
data class RateSheetEntry(
    @Identity @Column("id") val id: UUID,
    @Column("label") val label: String,
)

/** A sheet of rates: its entries and its zone links have `onDelete: restrict`. */
@Entity
@Storage(table = "db#rate_sheet")
data class RateSheet(
    @Identity @Column("id") val id: UUID,
    @Column("name") val name: String,
    @Owned @Children(parentKey = "sheet_id", reconcile = Reconcile.REPLACE_ALL, onDelete = OnDelete.RESTRICT, orderBy = ["label asc"]) val entries: List<RateSheetEntry>,
    @Reference @Link(table = "db#rate_sheet_zone", ownerKey = "sheet_id", targetKey = "zone_id", onDelete = OnDelete.RESTRICT, orderBy = ["name asc"]) val zones: List<RateZone>,
)

@Command(source = RateSheet::class, kind = CommandKind.CREATE)
data class CreateRateSheet(
    @Role(value = RoleKind.APPLICATION, provider = "newSavedSearchId") val id: UUID,
    val name: String,
    val entries: List<CreateRateSheetEntry>,
    @Items(unique = true) val zones: List<UUID>,
)

// The children are written by a create and an update of the sheet, as owned children are.
@Command(source = RateSheet::class, kind = CommandKind.UPDATE, strategy = UpdateStrategy.REPLACE)
data class ReplaceRateSheet(
    val name: String,
    val entries: List<CreateRateSheetEntry>,
    @Items(unique = true) val zones: List<UUID>,
)

@Command(source = RateSheetEntry::class, kind = CommandKind.CREATE)
data class CreateRateSheetEntry(
    @Role(value = RoleKind.APPLICATION, provider = "newSavedSearchId") val id: UUID,
    val label: String,
)
