package com.soma.testwes.photo

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface PhotoRepository : JpaRepository<Photo, Long> {

    fun findAllByGalleryIdAndIdIn(galleryId: Long, ids: Collection<Long>): List<Photo>

    fun findAllByGalleryId(galleryId: Long, pageable: Pageable): Page<Photo>

    fun findAllByGalleryIdAndStatus(galleryId: Long, status: PhotoStatus, pageable: Pageable): Page<Photo>

    fun countByGalleryId(galleryId: Long): Long

    fun countByGalleryIdAndStatus(galleryId: Long, status: PhotoStatus): Long

    /**
     * 임베딩 실행 대상 수. status가 아니라 embedding 컬럼을 보는 이유는 클러스터링 쿼리와
     * 같은 기준을 쓰기 위해서다 — 상태 값이 어긋나도 이 수는 진실을 말한다.
     */
    fun countByGalleryIdAndEmbeddingIsNull(galleryId: Long): Long
}
