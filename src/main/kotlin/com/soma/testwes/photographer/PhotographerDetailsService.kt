package com.soma.testwes.photographer

import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PhotographerDetailsService(
    private val photographerRepository: PhotographerRepository,
) : UserDetailsService {

    @Transactional(readOnly = true)
    override fun loadUserByUsername(username: String): UserDetails {
        val photographer = photographerRepository.findByLoginId(username)
            ?: throw UsernameNotFoundException("존재하지 않는 계정입니다")
        return PhotographerPrincipal.from(photographer)
    }
}
