package com.soma.testwes.photo

import com.soma.testwes.photographer.PhotographerPrincipal
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/galleries/{galleryId}/photos")
class PhotoController(
    private val photoService: PhotoService,
) {

    /**
     * 업로드 1단계. 파일 목록을 받아 사진 행을 만들고 S3 PUT용 presigned URL을 돌려준다.
     * 프론트는 받은 URL로 S3에 직접 올린다.
     */
    @PostMapping("/upload-urls")
    fun issueUploadUrls(
        @AuthenticationPrincipal principal: PhotographerPrincipal,
        @PathVariable galleryId: Long,
        @Valid @RequestBody request: IssueUploadUrlsRequest,
    ): IssueUploadUrlsResponse {
        val issued = photoService.issueUploadUrls(
            galleryId = galleryId,
            photographerId = principal.photographerId,
            files = request.files.map { NewPhoto(it.filename, it.contentType) },
        )
        return IssueUploadUrlsResponse(issued)
    }

    /** 업로드 2단계. S3 PUT을 마친 사진들을 통보받는다. */
    @PostMapping("/complete")
    fun complete(
        @AuthenticationPrincipal principal: PhotographerPrincipal,
        @PathVariable galleryId: Long,
        @Valid @RequestBody request: CompleteUploadRequest,
    ): CountResponse =
        CountResponse(photoService.markUploaded(galleryId, principal.photographerId, request.photoIds))

    /**
     * 업로드 3단계. 임베딩 파이프라인이 계산한 벡터를 적재한다.
     * 여기까지 채워진 사진만 클러스터링 대상이 된다.
     */
    @PutMapping("/embeddings")
    fun putEmbeddings(
        @AuthenticationPrincipal principal: PhotographerPrincipal,
        @PathVariable galleryId: Long,
        @Valid @RequestBody request: PutEmbeddingsRequest,
    ): CountResponse {
        val embeddings = request.embeddings.map { PhotoEmbedding(it.photoId, it.vector) }
        return CountResponse(photoService.applyEmbeddings(galleryId, principal.photographerId, embeddings))
    }

    /**
     * 조회 화면용 목록. 사진마다 서명된 GET URL이 붙어 온다.
     * status를 생략하면 PENDING까지 전부 나온다.
     */
    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: PhotographerPrincipal,
        @PathVariable galleryId: Long,
        @RequestParam(required = false) status: PhotoStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "200") size: Int,
    ): PhotoPage = photoService.list(galleryId, principal.photographerId, status, page, size)

    @GetMapping("/summary")
    fun summary(
        @AuthenticationPrincipal principal: PhotographerPrincipal,
        @PathVariable galleryId: Long,
    ): PhotoSummary = photoService.summarize(galleryId, principal.photographerId)
}

data class IssueUploadUrlsRequest(
    @field:NotEmpty val files: List<FileRequest>,
) {
    data class FileRequest(
        @field:NotBlank val filename: String,
        @field:NotBlank val contentType: String,
    )
}

data class IssueUploadUrlsResponse(val uploads: List<IssuedUpload>)

data class CompleteUploadRequest(
    @field:NotEmpty val photoIds: List<Long>,
)

data class PutEmbeddingsRequest(
    @field:NotEmpty val embeddings: List<EmbeddingRequest>,
) {
    data class EmbeddingRequest(val photoId: Long, val vector: FloatArray) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EmbeddingRequest) return false
            return photoId == other.photoId && vector.contentEquals(other.vector)
        }

        override fun hashCode(): Int = 31 * photoId.hashCode() + vector.contentHashCode()
    }
}

data class CountResponse(val count: Int)
