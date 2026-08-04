package com.soma.testwes.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val s3: S3,
    val cluster: Cluster,
    val embedding: Embedding,
) {
    data class S3(
        val bucket: String,
        val presignedUrlTtl: Duration,
        val viewUrlTtl: Duration,
        val maxBatchSize: Int,
    )

    data class Cluster(
        val defaultThreshold: Double,
    )

    data class Embedding(
        /**
         * 임베딩 Lambda 이름. prod는 Parameter Store의 app.embedding.function-name에서 오고,
         * Terraform이 함수를 만들며 채운다. 비어 있으면 실행 요청이 400으로 떨어진다 —
         * 로컬·테스트에는 함수가 없는 것이 정상이라 기동을 막지는 않는다.
         */
        val functionName: String,
    )
}
