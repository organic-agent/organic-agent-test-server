package com.soma.testwes.auth

import com.soma.testwes.photographer.PhotographerPrincipal
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.constraints.NotBlank
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 가입 API는 없다. DB에 직접 만든 계정으로만 로그인한다.
 * 로그아웃은 Spring Security의 로그아웃 필터가 POST /api/auth/logout 으로 처리한다.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authenticationManager: AuthenticationManager,
    private val securityContextRepository: SecurityContextRepository,
) {

    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): MeResponse {
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(request.loginId, request.password),
        )

        // 이 두 줄이 없으면 인증은 성공해도 세션에 남지 않아 다음 요청에서 401이 된다.
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, httpRequest, httpResponse)

        return MeResponse.from(authentication.principal as PhotographerPrincipal)
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: PhotographerPrincipal): MeResponse = MeResponse.from(principal)
}

data class LoginRequest(
    @field:NotBlank val loginId: String,
    @field:NotBlank val password: String,
)

data class MeResponse(val photographerId: Long, val loginId: String, val name: String) {

    companion object {
        fun from(principal: PhotographerPrincipal) = MeResponse(
            photographerId = principal.photographerId,
            loginId = principal.username,
            name = principal.name,
        )
    }
}
