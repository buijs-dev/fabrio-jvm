package dev.buijs.fabrio.examples.identify

import dev.buijs.fabrio.http.problem.ApplicationProblemException
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.QueryParam

/** The generated error catalog controls the wire identity of authored application problems. */
@Path("/label")
class LabelResource {
    @GET
    fun validate(@QueryParam("label") label: String?): String {
        if (label == null) throw ApplicationProblemException(LabelErrorsErrorCode.MISSING_LABEL)
        throw ApplicationProblemException(LabelErrorsErrorCode.INVALID_LABEL)
    }
}
