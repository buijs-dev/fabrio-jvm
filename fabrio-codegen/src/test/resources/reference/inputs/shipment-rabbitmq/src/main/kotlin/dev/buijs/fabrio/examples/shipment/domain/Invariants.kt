package dev.buijs.fabrio.examples.shipment.domain

import dev.buijs.fabrio.examples.shipment.contract.ShipmentErrorCode
import dev.buijs.fabrio.http.problem.ApplicationProblemException
import dev.buijs.fabrio.http.problem.FieldError

/**
 * The authored invariants the contract names (`invariant: Address.nonBlank`, D3.2). A value that
 * breaks one fails its construction with a validation problem, code `blank`, at the property of
 * the request it came from (E1.4).
 */
internal object Invariants {
    /** `Address.nonBlank`: neither street nor city is only whitespace, which `minLength` allows. */
    fun addressNonBlank(
        street: String,
        city: String,
    ) {
        val errors =
            listOfNotNull(
                blank("street", street),
                blank("city", city),
            )
        if (errors.isNotEmpty()) {
            throw ApplicationProblemException(ShipmentErrorCode.BLANK, errors = errors)
        }
    }

    /** An Address is the `destination` of every request that carries one. */
    private fun blank(
        property: String,
        value: String,
    ): FieldError? =
        if (value.isBlank()) {
            FieldError.body(listOf("destination", property), ShipmentErrorCode.BLANK)
        } else {
            null
        }
}
