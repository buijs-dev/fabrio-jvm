package dev.buijs.fabrio.examples.shipment.domain
import dev.buijs.rialto.annotations.*
import dev.buijs.rialto.annotations.Enum
import dev.buijs.fabrio.blob.BinaryContent
import java.time.Instant
import java.time.LocalDate
import java.math.BigDecimal
import java.util.UUID

@Value
@JvmInline
value class ShipmentId(val value: UUID)

@Value
@JvmInline
value class WarehouseId(val value: UUID)

enum class Status { draft, approved, rejected }

@Value(invariant = "Address.nonBlank")
data class Address(
    @Length(min = 1, max = 200) val street: String,
    @Length(min = 1, max = 120) val city: String,
) {
    // D3.2: the authored invariant `Address.nonBlank`; an Address that breaks it is never built.
    init {
        Invariants.addressNonBlank(street, city)
    }
}

@Entity
@Storage(table = "db#shipment_line")
data class ShipmentLine(
    @Identity @Column("public_id") val id: UUID,
    @Range(min = "1") @Column("quantity") val quantity: Int,
)

/** A shipment from a warehouse to a destination.
 *
 * @property note Free note for the driver.
 * @property lineCount The number of lines, counted in every read. */
@Entity(aggregateRoot = true)
@Transitions("state")
@Transition(name = "approve", from = ["draft"], to = "approved", sets = ["approvedAt=now", "approvedBy=actor"], emits = ["ShipmentApproved"])
@Transition(name = "reject", from = ["draft"], to = "rejected")
@Storage(table = "db#shipment", storageKey = "storage_id", version = "version", delete = "physical", retryAttempts = 3, retryIdempotent = true)
@Scope("tenant_id", ScopeSource.TENANT)
data class Shipment(
    @Identity @Column("public_id") val id: ShipmentId,
    /** Free note for the driver. */
    @Length(max = 280) @Column("delivery_note") val note: String?,
    @Column("expected_delivery") val expectedDelivery: LocalDate?,
    @Embedded("street=destination_street", "city=destination_city") val destination: Address,
    @Default("\"draft\"", Phase.BUSINESS) @Column("state") val state: Status,
    @Items(max = 100) @Owned @Children(parentKey = "shipment_id", reconcile = Reconcile.IDENTITY, onDelete = OnDelete.CASCADE, nullAs = "empty", order = "position") val lines: List<ShipmentLine>,
    @Computed(count = "lines") val lineCount: Int,
    @Reference @Join(column = "warehouse_id", table = "db#warehouse", key = "public_id") val warehouse: WarehouseId?,
    @Immutable @Column("recipient") val recipient: Recipient,
    @Default("\"standard\"", Phase.DECODE) @Column("priority") val priority: Priority,
    @Column("created_at") @Generated(GeneratedBy.STORAGE) val createdAt: Instant,
    @Column("approved_by") val approvedBy: String?,
    @Column("approved_at") val approvedAt: Instant?,
    @Length(max = 280) @Column("rejection_reason") val rejectionReason: String?,
    @Column("label") val label: String,
    @Reference @Link(table = "db#shipment_tag", ownerKey = "shipment_id", targetKey = "tag_id", onDelete = OnDelete.CASCADE, orderBy = ["name asc"]) val tags: List<Tag>,
    @Reference @Link(ownerKey = "shipment_id", orderBy = ["scannedAt asc"]) val scans: List<TrackingScan>,
    @Owned @Children(parentKey = "shipment_id", reconcile = Reconcile.REPLACE_ALL, onDelete = OnDelete.CASCADE, order = "position") val packages: List<ShipmentPackage>,
    @Owned @Children(ownerKey = "customs_declaration_id", onDelete = OnDelete.RESTRICT) val customsDeclaration: CustomsDeclaration?,
    @Reference @Join(column = "handling_unit_id", table = "db#handling_unit", key = "id") val handlingUnit: HandlingUnit?,
    @Column("service_codes") val serviceCodes: Set<String>,
)

/** Emitted when a draft shipment is approved. */
@Event(type = "com.example.shipment.approved.v1", source = "/shipments", subject = "shipmentId", channel = "events#/channels/shipmentEvents", mode = ContentMode.STRUCTURED)
data class ShipmentApproved(
    val shipmentId: ShipmentId,
    val approvedAt: Instant,
)

@Projection(source = Shipment::class)
@Storage(table = "db#shipment")
@Scope("tenant_id", ScopeSource.TENANT)
data class ShipmentSummary(
    @Column("public_id") val id: ShipmentId,
    @Default("\"draft\"", Phase.BUSINESS) @Column("state") val state: Status,
    /** Free note for the driver. */
    @Length(max = 280) @Column("delivery_note") val note: String?,
    @Column("expected_delivery") val expectedDelivery: LocalDate?,
    @Column("created_at") val createdAt: Instant,
)

// An extensible enum (K2.7): a priority this service does not know stays Other, with its wire value
// unchanged, in every read, write and store (V4.2).
@Enum(extensible = true)
sealed interface Priority {
    val wire: String

    data object Standard : Priority {
        override val wire = "standard"
    }

    data object Express : Priority {
        override val wire = "express"
    }

    data class Other(
        override val wire: String,
    ) : Priority

    companion object {
        /** The priority of [wire]: a member, or [Other] for a value outside them. */
        fun of(wire: String): Priority =
            when (wire) {
                Standard.wire -> Standard
                Express.wire -> Express
                else -> Other(wire)
            }
    }
}

@Union("kind")
sealed interface Recipient

@Value
@Variant("person")
data class PersonRecipient(
    @Length(min = 1) val name: String,
) : Recipient

@Value
@Variant("company")
data class CompanyRecipient(
    @Length(min = 1) val companyName: String,
    val contact: String,
) : Recipient

@Entity
@Storage(table = "db#warehouse", storageKey = "storage_id")
data class Warehouse(
    @Identity @Column("public_id") val id: WarehouseId,
    @Length(min = 1, max = 120) @Column("name") val name: String,
)

@Entity
@Storage(table = "db#tag")
data class Tag(
    @Identity @Column("id") val id: UUID,
    @Column("name") val name: String,
)

@Entity
@Storage(table = "db#tracking_scan")
data class TrackingScan(
    @Identity @Column("id") val id: UUID,
    @Column("scanned_at") val scannedAt: Instant,
)

@Entity
@Storage(table = "db#shipment_package")
data class ShipmentPackage(
    @Identity @Column("id") val id: UUID,
    @Range(min = "0.001") @Decimal(precision = 8, scale = 3) @Column("weight_kg") val weightKg: BigDecimal,
)

@Entity
@Storage(table = "db#customs_declaration")
data class CustomsDeclaration(
    @Identity @Column("id") val id: UUID,
    @Length(min = 1) @Column("contents") val contents: String,
)

/** @property deletedAt When the carrier was deleted; only a read that includes deleted carriers shows one. */
@Entity
@Unique("name")
@Storage(table = "db#carrier", delete = "soft:deleted_at", restore = true, deleteBy = "deleted_by")
data class Carrier(
    @Identity @Column("id") val id: UUID,
    @Length(min = 1, max = 120) @Column("name") val name: String,
    @Format("email") @Column("contact_email") val contactEmail: String?,
    @Column("deleted_at") val deletedAt: Instant?,
)

// A carrier's rate for a zone: carrier and zone identify it together (D2.2, P2.2).
@Entity
@Storage(table = "db#carrier_rate")
data class CarrierRate(
    @Identity @Column("carrier_id") val carrierId: UUID,
    @Identity @Column("zone") val zone: String,
    @Range(min = "0") @Decimal(precision = 10, scale = 2) @Column("amount") val amount: BigDecimal,
)

@Union("kind")
@Storage(table = "db#handling_unit", unionStrategy = "single-table", discriminator = "kind")
sealed interface HandlingUnit

@Entity
@Variant("pallet")
data class Pallet(
    @Identity @Column("id") val id: UUID,
    @Range(min = "1") @Column("capacity_kg") val capacityKg: Int,
) : HandlingUnit

@Entity
@Variant("container")
data class Container(
    @Identity @Column("id") val id: UUID,
    @Column("seal") val seal: String,
) : HandlingUnit

@Projection(source = Shipment::class)
@Storage(table = "db#shipment")
@Scope("tenant_id", ScopeSource.TENANT)
data class ShipmentCity(
    @Column("public_id") val id: ShipmentId,
    @Length(min = 1, max = 120) @Column("destination_city") @From("destination.city") val city: String,
)

/** Emitted when a shipment is cancelled; cancellation is recorded separately from its approval status. */
@Event(type = "com.example.shipment.cancelled.v1", source = "/shipments", subject = "shipmentId", channel = "events#/channels/shipmentCancellations", mode = ContentMode.BINARY)
data class ShipmentCancelled(
    val shipmentId: ShipmentId,
    val cancelledAt: Instant,
    val reason: String,
)

@Value
data class WarehouseService(
    val priority: Priority,
)

@Command(source = Shipment::class, kind = CommandKind.CREATE)
data class CreateShipment(
    @Role(value = RoleKind.APPLICATION, provider = "newShipmentId") val id: ShipmentId,
    /** Free note for the driver. */
    @Length(max = 200) val note: String?,
    val expectedDelivery: LocalDate?,
    val destination: Address,
    val warehouse: WarehouseId?,
    val lines: List<CreateShipmentLine>,
    val recipient: Recipient,
    val priority: Priority,
    @Role(value = RoleKind.STORAGE) val createdAt: Instant,
    @Role(value = RoleKind.DERIVED) val approvedBy: String?,
    @Role(value = RoleKind.DERIVED) val label: String,
    @Items(unique = true) val tags: List<UUID>,
    val packages: List<CreateShipmentPackage>,
    val customsDeclaration: CreateCustomsDeclaration?,
    val handlingUnit: UUID?,
    val serviceCodes: Set<String>,
)

@Command(source = ShipmentLine::class, kind = CommandKind.CREATE)
data class CreateShipmentLine(
    @Role(value = RoleKind.APPLICATION, provider = "newLineId") val id: UUID,
    val quantity: Int,
)

@Command(source = Shipment::class, kind = CommandKind.UPDATE, strategy = UpdateStrategy.MERGE_PATCH, partial = true)
data class PatchShipmentNote(
    /** Free note for the driver. */
    val note: String?,
)

@Command(source = ShipmentPackage::class, kind = CommandKind.CREATE)
data class CreateShipmentPackage(
    @Role(value = RoleKind.APPLICATION, provider = "newPackageId") val id: UUID,
    val weightKg: BigDecimal,
)

@Command(source = CustomsDeclaration::class, kind = CommandKind.CREATE)
data class CreateCustomsDeclaration(
    @Role(value = RoleKind.APPLICATION, provider = "newDeclarationId") val id: UUID,
    val contents: String,
)

@Command(source = Shipment::class, kind = CommandKind.UPDATE, strategy = UpdateStrategy.MERGE_PATCH)
data class DecideShipment(
    val rejectionReason: String?,
)

@Command(source = Carrier::class, kind = CommandKind.UPDATE, strategy = UpdateStrategy.REPLACE)
data class ReplaceCarrier(
    val name: String,
    val contactEmail: String?,
)

@Shape(source = Shipment::class, partial = true)
data class CountShipments(
    val state: Status,
)

@Command(source = Shipment::class, kind = CommandKind.UPDATE, strategy = UpdateStrategy.MERGE_PATCH, partial = true)
data class PatchShipmentContents(
    val warehouse: WarehouseId?,
    val lines: List<ReconcileShipmentLine>,
    val packages: List<CreateShipmentPackage>,
    @Items(unique = true) val tags: List<UUID>,
)

@Command(source = ShipmentLine::class, kind = CommandKind.UPDATE, strategy = UpdateStrategy.REPLACE)
data class ReconcileShipmentLine(
    val id: UUID? = null,
    val quantity: Int,
)


/** Proof that a shipment was delivered, kept outside the row in a blob store.
 *
 * @property file A photo or a signed PDF of the delivery. */
@Entity
@Storage(table = "db#delivery_proof")
@Scope("tenant_id", ScopeSource.TENANT)
data class DeliveryProof(
    @Identity @Column("shipment_id") val shipment: ShipmentId,
    @Binary(maxSize = 10485760, mediaTypes = ["image/jpeg", "application/pdf"]) @Blob("proofs", "file_key", mediaType = "file_media_type", size = "file_size", sha256 = "file_sha256", check = true) val file: BinaryContent,
)

@Shape(source = DeliveryProof::class)
data class UploadDeliveryProof(
    val shipment: ShipmentId,
    val file: BinaryContent,
)

@Shape(source = DeliveryProof::class)
data class DeliveryProofFile(
    @Role(RoleKind.DERIVED) val file: BinaryContent,
)

/**
 * A shipment list filter that a user saved; only that user sees it, in their tenant.
 *
 * @property filter An RSQL filter for listShipments.
 * @property owner The user who saved it, from the verified token; shown, never sent.
 */
@Entity
@Storage(table = "db#saved_search")
@Scope("tenant_id", ScopeSource.TENANT)
@Scope("owner_id", ScopeSource.ACTOR)
data class SavedSearch(
    @Identity @Column("id") val id: UUID,
    @Length(min = 1, max = 80) @Column("name") val name: String,
    @Length(min = 1, max = 1000) @Column("filter") val filter: String,
    @Column("owner_id") val owner: String,
)

@Command(source = SavedSearch::class, kind = CommandKind.CREATE)
data class CreateSavedSearch(
    @Role(value = RoleKind.APPLICATION, provider = "newSavedSearchId") val id: UUID,
    val name: String,
    val filter: String,
)
