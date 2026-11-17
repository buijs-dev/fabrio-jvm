package dev.buijs.fabrio.examples.shipment.mapping

import dev.buijs.fabrio.examples.shipment.domain.Priority
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

// External converters (mapping profile, M3.7): a contract mapping names a Kotlin object with a
// `convert` function by its qualified name; the generated REST mapper calls it for that property.

/** `createdAt` from the API: an instant with any offset, kept as the instant it denotes. */
object ParseShipmentInstant {
    fun convert(value: OffsetDateTime?): Instant? = value?.toInstant()
}

/** `createdAt` to the API: the stored instant, written in UTC. */
object FormatShipmentInstant {
    fun convert(value: Instant?): OffsetDateTime? = value?.atOffset(ZoneOffset.UTC)
}

/**
 * `priority` from the v2 API, where it is an extensible enum (a string on the wire): a member this
 * service does not know is kept as [Priority.Other], unchanged (K2.7, V4.2).
 */
object DecodePriority {
    fun convert(value: String?): Priority? = value?.let(Priority::of)
}

/** `priority` to the v2 API: its wire value, an unknown one as it came in. */
object EncodePriority {
    fun convert(value: Priority?): String? = value?.wire
}
