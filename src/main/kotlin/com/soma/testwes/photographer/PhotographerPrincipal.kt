package com.soma.testwes.photographer

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * 세션에 담기는 인증 주체. 컨트롤러가 매번 photographer를 다시 조회하지 않도록
 * id를 들고 다닌다.
 */
class PhotographerPrincipal(
    val photographerId: Long,
    private val loginId: String,
    private val passwordHash: String,
    val name: String,
) : UserDetails {

    override fun getAuthorities(): Collection<GrantedAuthority> = listOf(SimpleGrantedAuthority(ROLE))

    override fun getPassword(): String = passwordHash

    override fun getUsername(): String = loginId

    companion object {
        const val ROLE = "ROLE_PHOTOGRAPHER"

        fun from(photographer: Photographer) = PhotographerPrincipal(
            photographerId = photographer.requiredId,
            loginId = photographer.loginId,
            passwordHash = photographer.password,
            name = photographer.name,
        )
    }
}
