package com.soma.testwes.gallery

import org.springframework.data.jpa.repository.JpaRepository

interface GalleryRepository : JpaRepository<Gallery, Long> {

    fun findAllByPhotographerIdOrderByIdDesc(photographerId: Long): List<Gallery>
}
