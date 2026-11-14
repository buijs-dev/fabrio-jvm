package dev.buijs.fabrio.examples.shipment.application

import dev.buijs.fabrio.blob.BinaryContent
import dev.buijs.fabrio.blob.BlobStore
import dev.buijs.fabrio.blob.FileSystemBlobStore
import dev.buijs.fabrio.blob.s3.S3BlobStore
import dev.buijs.fabrio.examples.shipment.domain.DeliveryProofFileCheck
import dev.buijs.fabrio.http.problem.UnsupportedMediaTypeException
import dev.buijs.fabrio.persistence.postgres.api.PostgresBlobStore
import dev.buijs.fabrio.persistence.reactive.spi.QueryExecutor
import io.quarkus.runtime.Startup
import io.smallrye.config.ConfigMapping
import io.smallrye.config.WithDefault
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Named
import jakarta.inject.Singleton
import java.net.URI
import java.nio.file.Path
import java.util.Optional
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The content check of a delivery proof (rest R7.3): the declared media type decides, and the
 * content must start as that type does. A PDF declared as `application/pdf` is a PDF whatever its
 * file name; a PNG declared as `image/jpeg` is rejected.
 */
@ApplicationScoped
internal class DeliveryProofContent : DeliveryProofFileCheck {
    override suspend fun check(content: BinaryContent) {
        val head = withContext(Dispatchers.IO) { content.open().use { it.readNBytes(4) } }
        val signature = SIGNATURES[content.mediaType]
        if (signature == null || !head.startsWith(signature)) {
            throw UnsupportedMediaTypeException("The content is not ${content.mediaType}")
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private companion object {
        val SIGNATURES =
            mapOf(
                "image/jpeg" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),
                "application/pdf" to "%PDF".toByteArray(Charsets.US_ASCII),
            )
    }
}

/**
 * The blob store `proofs` (persistence P7): `shipment.proofs.kind` chooses a directory
 * (`filesystem`, the default), the database (`postgres`, the table `fabrio_blob`) or S3 (`s3`, such
 * as the MinIO of dev/compose.yaml).
 */
@ConfigMapping(prefix = "shipment.proofs")
internal interface ProofStoreConfig {
    @WithDefault("filesystem")
    fun kind(): String

    @WithDefault("build/proofs")
    fun directory(): String

    fun s3(): S3

    interface S3 {
        fun endpoint(): Optional<URI>

        @WithDefault("us-east-1")
        fun region(): String

        @WithDefault("proofs")
        fun bucket(): String

        @WithDefault("shipment")
        fun accessKey(): String

        @WithDefault("shipment-secret")
        fun secretKey(): String
    }
}

@ApplicationScoped
internal class ProofStores {
    // At startup: the S3 store creates its bucket, which is blocking work.
    @Produces
    @Startup
    @Singleton
    @Named("proofs")
    fun proofs(
        config: ProofStoreConfig,
        queryExecutor: QueryExecutor,
    ): BlobStore =
        when (config.kind()) {
            "filesystem" -> FileSystemBlobStore(Path.of(config.directory()))
            "postgres" -> PostgresBlobStore(queryExecutor, "proofs")
            "s3" -> {
                val s3 = config.s3()
                val client = S3BlobStore.client(s3.region(), s3.accessKey(), s3.secretKey(), s3.endpoint().orElse(null))
                S3BlobStore(client, s3.bucket()).also { it.createBucketIfMissing() }
            }
            else -> error("Unknown proof store '${config.kind()}': filesystem, postgres or s3")
        }
}
