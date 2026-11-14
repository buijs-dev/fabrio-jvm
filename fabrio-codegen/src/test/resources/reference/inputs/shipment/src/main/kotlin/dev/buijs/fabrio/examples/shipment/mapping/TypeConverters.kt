package dev.buijs.fabrio.examples.shipment.mapping

import dev.buijs.fabrio.entity.core.spi.TypeConverter
import dev.buijs.fabrio.examples.shipment.api.v1.model.StatusV1
import dev.buijs.fabrio.examples.shipment.api.v2.model.StatusV2
import dev.buijs.fabrio.examples.shipment.domain.Status
import jakarta.enterprise.context.ApplicationScoped
import java.time.LocalDate

/** Rialto persistence scalar mapping: PostgreSQL DATE values retain their calendar date. */
@ApplicationScoped
class ShipmentDateConverter : TypeConverter<Any, LocalDate> {
    override fun convert(raw: Any?): LocalDate? = when (raw) {
        null -> null
        is LocalDate -> raw
        else -> throw IllegalArgumentException(
            "Expected a PostgreSQL DATE, got ${raw.javaClass.name}",
        )
    }
}

/** Rialto mapping M3.1: v1 status members correspond by name, ignoring case. */
@ApplicationScoped
class ShipmentStatusV1Converter : TypeConverter<Status, StatusV1> {
    override fun convert(raw: Status?): StatusV1? =
        raw?.let { StatusV1.valueOf(it.name.uppercase()) }
}

/** Rialto mapping M3.1: v2 keeps the same status member names as v1. */
@ApplicationScoped
class ShipmentStatusV2Converter : TypeConverter<Status, StatusV2> {
    override fun convert(raw: Status?): StatusV2? =
        raw?.let { StatusV2.valueOf(it.name.uppercase()) }
}
