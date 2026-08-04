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
}
