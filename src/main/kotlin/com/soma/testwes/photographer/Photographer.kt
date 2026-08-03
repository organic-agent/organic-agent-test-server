package com.soma.testwes.photographer

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 가입 API는 두지 않는다. 계정은 운영자가 DB에 직접 INSERT 해서 만들고,
 * 이 서버는 그 계정으로 로그인만 시킨다.
 */
@Entity
@Table(name = "photographer")
class Photographer(

    @Column(name = "login_id", nullable = false, unique = true)
    val loginId: String,

    /** BCrypt 해시. 평문을 넣으면 로그인이 조용히 실패한다. */
    @Column(nullable = false)
    val password: String,

    @Column(nullable = false)
    val name: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    val requiredId: Long
        get() = id ?: error("아직 저장되지 않은 Photographer 다")
}
