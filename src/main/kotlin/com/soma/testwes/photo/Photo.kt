package com.soma.testwes.photo

import com.soma.testwes.gallery.Gallery
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Array
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "photo")
class Photo(

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gallery_id", nullable = false)
    val gallery: Gallery,

    /** presigned URL을 발급하며 정한 업로드 목적지. 바이트는 이 서버를 거치지 않는다. */
    @Column(name = "s3_key", nullable = false, unique = true)
    val s3Key: String,

    @Column(name = "original_filename", nullable = false)
    val originalFilename: String,

    @Column(name = "content_type", nullable = false)
    val contentType: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    // allOpen 플러그인이 엔티티를 open으로 만들어 private setter를 못 쓴다.
    // 상태 전이는 아래 메서드로만 한다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PhotoStatus = PhotoStatus.PENDING

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = EMBEDDING_DIMENSION)
    @Column(name = "embedding")
    var embedding: FloatArray? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    val requiredId: Long
        get() = id ?: error("아직 저장되지 않은 Photo 다")

    /** 프론트가 S3 업로드를 마쳤다고 알려올 때. */
    fun markUploaded() {
        status = PhotoStatus.UPLOADED
    }

    fun applyEmbedding(vector: FloatArray) {
        require(vector.size == EMBEDDING_DIMENSION) {
            "임베딩 차원이 맞지 않습니다: ${vector.size} (기대값 $EMBEDDING_DIMENSION)"
        }
        embedding = vector
        status = PhotoStatus.EMBEDDED
    }

    companion object {
        /**
         * vector(512) 컬럼과 반드시 같아야 한다. 모델을 바꿔 차원이 달라지면
         * 마이그레이션으로 컬럼 타입을 함께 바꿔야 한다.
         */
        const val EMBEDDING_DIMENSION = 512
    }
}
