package com.soma.testwes.photo

import com.soma.testwes.config.AppProperties
import com.soma.testwes.gallery.GalleryService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Instant
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

    /**
     * 조회 화면이 쓰는 목록. 버킷은 전면 비공개(퍼블릭 액세스 차단)라 s3Key만으로는 아무것도
     * 못 띄우므로, 사진마다 presigned GET URL을 함께 내려 <img src>에 그대로 꽂게 한다.
     *
     * 클러스터링 결과(GET /clusters)는 s3Key만 돌려주니, 프론트는 여기서 받은 s3Key -> viewUrl
     * 로 묶음을 그리면 된다.
     */
    fun list(galleryId: Long, photographerId: Long, status: PhotoStatus?, page: Int, size: Int): PhotoPage {
        galleryService.getOwned(galleryId, photographerId)

        require(page >= 0) { "page는 0 이상이어야 합니다 (요청: $page)" }
        require(size in 1..properties.s3.maxBatchSize) {
            "size는 1 ~ ${properties.s3.maxBatchSize} 사이여야 합니다 (요청: $size)"
        }

        // id 오름차순 = 업로드 URL을 발급한 순서. 페이지를 넘겨도 순서가 흔들리지 않는다.
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"))
        val found = if (status == null) {
            photoRepository.findAllByGalleryId(galleryId, pageable)
        } else {
            photoRepository.findAllByGalleryIdAndStatus(galleryId, status, pageable)
        }

        return PhotoPage(
            photos = found.content.map(::toView),
            page = found.number,
            size = found.size,
            totalCount = found.totalElements,
            hasNext = found.hasNext(),
            viewUrlTtlSeconds = properties.s3.viewUrlTtl.seconds,
        )
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

    private fun toView(photo: Photo) = PhotoView(
        photoId = photo.requiredId,
        s3Key = photo.s3Key,
        originalFilename = photo.originalFilename,
        contentType = photo.contentType,
        status = photo.status,
        createdAt = photo.createdAt,
        // PENDING은 URL만 발급되고 실제 객체는 아직 없을 수 있다. URL을 주면 프론트의
        // <img>가 깨진 이미지를 그리므로, 올라온 것이 확실한 사진에만 채운다.
        viewUrl = if (photo.status == PhotoStatus.PENDING) null else presignGet(photo.s3Key),
    )

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

    /**
     * 버킷이 비공개라 브라우저가 객체를 직접 못 받는다. 서명된 GET URL이 CloudFront 없이
     * 이미지를 띄우는 유일한 방법이다.
     *
     * 서명에는 EC2 인스턴스 롤의 임시 자격증명이 쓰인다. 그 세션이 만료되면 TTL이 남아 있어도
     * URL이 함께 죽으므로, view-url-ttl을 몇 시간 단위로 늘리려면 그 점을 먼저 따져야 한다.
     */
    private fun presignGet(key: String): String {
        val getRequest = GetObjectRequest.builder()
            .bucket(properties.s3.bucket)
            .key(key)
            .build()

        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(properties.s3.viewUrlTtl)
            .getObjectRequest(getRequest)
            .build()

        return s3Presigner.presignGetObject(presignRequest).url().toExternalForm()
    }
}

data class NewPhoto(val filename: String, val contentType: String)

data class IssuedUpload(val photoId: Long, val s3Key: String, val uploadUrl: String)

data class PhotoView(
    val photoId: Long,
    val s3Key: String,
    val originalFilename: String,
    val contentType: String,
    val status: PhotoStatus,
    val createdAt: Instant,
    /** 서명된 S3 GET URL. 아직 안 올라온(PENDING) 사진은 null. */
    val viewUrl: String?,
)

data class PhotoPage(
    val photos: List<PhotoView>,
    val page: Int,
    val size: Int,
    val totalCount: Long,
    val hasNext: Boolean,
    /** viewUrl이 살아 있는 시간. 프론트는 이 시간이 지나기 전에 목록을 다시 불러야 한다. */
    val viewUrlTtlSeconds: Long,
)

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
