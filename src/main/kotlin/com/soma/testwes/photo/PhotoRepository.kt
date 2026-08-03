package com.soma.testwes.photo

import org.springframework.data.jpa.repository.JpaRepository

interface PhotoRepository : JpaRepository<Photo, Long> {

    fun findAllByGalleryIdAndIdIn(galleryId: Long, ids: Collection<Long>): List<Photo>

    fun countByGalleryId(galleryId: Long): Long

    fun countByGalleryIdAndStatus(galleryId: Long, status: PhotoStatus): Long
}
