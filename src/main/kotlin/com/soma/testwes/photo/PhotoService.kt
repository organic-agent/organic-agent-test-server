package com.soma.testwes.photo

import com.soma.testwes.config.AppProperties
import com.soma.testwes.gallery.GalleryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.util.UUID

@Service
@Transactional(readOnly = true)
class PhotoService(
    private val photoRepository: PhotoRepository,
    private val galleryService: GalleryService,
    private val s3Presigner: S3Presigner,
    private val properties: AppProperties,
) {

    /**
     * 수천 장을 한 요청으로 받는다. 이미지 바이트는 이 서버를 통과하지 않고
     * 프론트가 받은 URL로 S3에 직접 PUT 한다.
     */
    @Transactional
    fun issueUploadUrls(galleryId: Long, photographerId: Long, files: List<NewPhoto>): List<IssuedUpload> {
        require(files.isNotEmpty()) { "업로드할 파일이 없습니다" }
        require(files.size <= properties.s3.maxBatchSize) {
            "한 번에 발급할 수 있는 URL은 ${properties.s3.maxBatchSize}개까지입니다 (요청: ${files.size}개)"
        }

        val gallery = galleryService.getOwned(galleryId, photographerId)

        val photos = files.map { file ->
            Photo(
                gallery = gallery,
                s3Key = buildKey(galleryId, file.filename),
                originalFilename = file.filename,
                contentType = file.contentType,
            )
        }

        return photoRepository.saveAll(photos).map { photo ->
            IssuedUpload(
                photoId = photo.requiredId,
                s3Key = photo.s3Key,
                uploadUrl = presignPut(photo.s3Key, photo.contentType),
            )
        }
    }

    /**
     * S3 업로드는 프론트가 직접 하므로 이 서버는 완료 사실을 알 수 없다.
     * 프론트가 끝난 것들을 모아 통보해준다.
     */
    @Transactional
    fun markUploaded(galleryId: Long, photographerId: Long, photoIds: List<Long>): Int {
        galleryService.getOwned(galleryId, photographerId)
        val photos = loadOwnedPhotos(galleryId, photoIds)
        photos.forEach { it.markUploaded() }
        return photos.size
    }

    /** 임베딩 계산은 별도 파이프라인이 하고, 결과만 여기로 들어온다. */
    @Transactional
    fun applyEmbeddings(galleryId: Long, photographerId: Long, embeddings: List<PhotoEmbedding>): Int {
        galleryService.getOwned(galleryId, photographerId)

        val byId = loadOwnedPhotos(galleryId, embeddings.map { it.photoId }).associateBy { it.requiredId }
        embeddings.forEach { byId.getValue(it.photoId).applyEmbedding(it.vector) }
        return embeddings.size
    }

    fun summarize(galleryId: Long, photographerId: Long): PhotoSummary {
        galleryService.getOwned(galleryId, photographerId)
        return PhotoSummary(
            total = photoRepository.countByGalleryId(galleryId),
            uploaded = photoRepository.countByGalleryIdAndStatus(galleryId, PhotoStatus.UPLOADED),
            embedded = photoRepository.countByGalleryIdAndStatus(galleryId, PhotoStatus.EMBEDDED),
        )
    }

    /** 다른 갤러리의 사진 id를 섞어 보내는 요청을 여기서 걸러낸다. */
    private fun loadOwnedPhotos(galleryId: Long, photoIds: List<Long>): List<Photo> {
        require(photoIds.isNotEmpty()) { "대상 사진이 없습니다" }

        val photos = photoRepository.findAllByGalleryIdAndIdIn(galleryId, photoIds.toSet())
        val found = photos.map { it.requiredId }.toSet()
        val missing = photoIds.toSet() - found
        require(missing.isEmpty()) { "이 갤러리에 없는 사진입니다: $missing" }

        return photos
    }

    private fun buildKey(galleryId: Long, filename: String): String {
        // 원본 파일명은 컬럼에 따로 남긴다. 키에 그대로 쓰면 중복·인코딩 문제가 생긴다.
        val extension = filename.substringAfterLast('.', "").lowercase()
        val suffix = if (extension.isBlank()) "" else ".$extension"
        return "galleries/$galleryId/${UUID.randomUUID()}$suffix"
    }

    private fun presignPut(key: String, contentType: String): String {
        val putRequest = PutObjectRequest.builder()
            .bucket(properties.s3.bucket)
            .key(key)
            // 프론트도 같은 Content-Type으로 PUT 해야 서명이 맞는다.
            .contentType(contentType)
            .build()

        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(properties.s3.presignedUrlTtl)
            .putObjectRequest(putRequest)
            .build()

        return s3Presigner.presignPutObject(presignRequest).url().toExternalForm()
    }
}

data class NewPhoto(val filename: String, val contentType: String)

data class IssuedUpload(val photoId: Long, val s3Key: String, val uploadUrl: String)

data class PhotoEmbedding(val photoId: Long, val vector: FloatArray) {

    // FloatArray는 equals/hashCode가 참조 비교라 data class 기본 구현이 어긋난다.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PhotoEmbedding) return false
        return photoId == other.photoId && vector.contentEquals(other.vector)
    }

    override fun hashCode(): Int = 31 * photoId.hashCode() + vector.contentHashCode()
}

data class PhotoSummary(val total: Long, val uploaded: Long, val embedded: Long)
