package com.soma.testwes.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val s3: S3,
    val cluster: Cluster,
) {
    data class S3(
        val bucket: String,
        val presignedUrlTtl: Duration,
        val maxBatchSize: Int,
    )

    data class Cluster(
        val defaultThreshold: Double,
    )
}
