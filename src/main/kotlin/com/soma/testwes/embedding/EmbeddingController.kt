package com.soma.testwes.embedding

import com.soma.testwes.photographer.PhotographerPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/galleries/{galleryId}/embeddings")
class EmbeddingController(
    private val embeddingService: EmbeddingService,
) {

    /**
     * 업로드가 끝난 갤러리의 임베딩 계산을 시작한다. 비동기라 즉시 돌아오고, 진행 상황은
     * GET /photos/summary의 embedded 수로 본다.
     *
     * 여러 번 눌러도 안전하다. 기본값은 아직 임베딩이 없는 사진만 처리하므로 중간에 실패한
     * 실행을 이어받는 것과 같다. force=true는 이미 채워진 것까지 다시 계산한다 — 모델이나
     * 전처리를 바꿔 전량 재계산할 때만 쓴다.
     */
    @PostMapping("/run")
    fun run(
        @AuthenticationPrincipal principal: PhotographerPrincipal,
        @PathVariable galleryId: Long,
        @RequestParam(defaultValue = "false") force: Boolean,
    ): EmbeddingRunResult = embeddingService.run(galleryId, principal.photographerId, force)
}
