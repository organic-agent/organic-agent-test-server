package com.soma.testwes.gallery

import com.soma.testwes.cluster.ClusterResult
import com.soma.testwes.cluster.PhotoClusterService
import com.soma.testwes.photographer.PhotographerPrincipal
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/galleries")
class GalleryController(
    private val galleryService: GalleryService,
    private val photoClusterService: PhotoClusterService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal principal: PhotographerPrincipal,
        @Valid @RequestBody request: CreateGalleryRequest,
    ): GalleryResponse = GalleryResponse.from(galleryService.create(principal.photographerId, request.name))

    @GetMapping
    fun list(@AuthenticationPrincipal principal: PhotographerPrincipal): List<GalleryResponse> =
        galleryService.findAllOwnedBy(principal.photographerId).map(GalleryResponse::from)

    @GetMapping("/{galleryId}")
    fun get(
        @AuthenticationPrincipal principal: PhotographerPrincipal,
        @PathVariable galleryId: Long,
    ): GalleryResponse = GalleryResponse.from(galleryService.getOwned(galleryId, principal.photographerId))

    /**
     * 프론트가 준 유사도로 갤러리 안의 사진을 묶어 S3 키 목록만 돌려준다.
     * threshold를 생략하면 app.cluster.default-threshold 를 쓴다.
     */
    @GetMapping("/{galleryId}/clusters")
    fun clusters(
        @AuthenticationPrincipal principal: PhotographerPrincipal,
        @PathVariable galleryId: Long,
        @RequestParam(required = false) threshold: Double?,
    ): ClusterResult = photoClusterService.cluster(galleryId, principal.photographerId, threshold)
}

data class CreateGalleryRequest(
    @field:NotBlank val name: String,
)

data class GalleryResponse(val id: Long, val name: String, val createdAt: Instant) {

    companion object {
        fun from(gallery: Gallery) = GalleryResponse(
            id = gallery.requiredId,
            name = gallery.name,
            createdAt = gallery.createdAt,
        )
    }
}
