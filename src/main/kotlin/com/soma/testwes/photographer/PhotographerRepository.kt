package com.soma.testwes.photographer

import org.springframework.data.jpa.repository.JpaRepository

interface PhotographerRepository : JpaRepository<Photographer, Long> {

    fun findByLoginId(loginId: String): Photographer?
}
