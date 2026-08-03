package com.soma.testwes.gallery

import com.soma.testwes.photographer.Photographer
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "gallery")
class Gallery(

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "photographer_id", nullable = false)
    val photographer: Photographer,

    @Column(nullable = false)
    val name: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    val requiredId: Long
        get() = id ?: error("아직 저장되지 않은 Gallery 다")
}
