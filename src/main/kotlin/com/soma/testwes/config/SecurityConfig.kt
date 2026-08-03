package com.soma.testwes.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    @param:Value("\${cors.allowed-origins}") private val allowedOrigins: String,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(
        userDetailsService: UserDetailsService,
        passwordEncoder: PasswordEncoder,
    ): AuthenticationManager {
        val provider = DaoAuthenticationProvider(userDetailsService)
        provider.setPasswordEncoder(passwordEncoder)
        return ProviderManager(provider)
    }

    /** 로그인 성공 후 SecurityContext를 세션에 넣는 곳. AuthController가 직접 호출한다. */
    @Bean
    fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()

    @Bean
    fun filterChain(http: HttpSecurity, securityContextRepository: SecurityContextRepository): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            // 프론트가 별도 오리진이라 CSRF 토큰 왕복을 붙이기 전까지는 끈다.
            // 세션 쿠키의 SameSite=Lax 기본값이 1차 방어선이다.
            .csrf { it.disable() }
            .securityContext { it.securityContextRepository(securityContextRepository) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
            .authorizeHttpRequests {
                it.requestMatchers("/api/auth/login").permitAll()
                    // CD가 배포 성공 판정에 쓴다. 인증을 걸면 배포가 실패한다.
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .anyRequest().authenticated()
            }
            // 로그인 폼으로 리다이렉트하지 않고 401을 준다. 호출자는 브라우저가 아니라 SPA다.
            .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout {
                it.logoutUrl("/api/auth/logout")
                    .logoutSuccessHandler(HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
            }

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = this@SecurityConfig.allowedOrigins.split(",").map { it.trim() }.filter { it.isNotBlank() }
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            // 세션 쿠키를 실어 보내야 하므로 필수. 이 값이 true면 origin에 *를 못 쓴다.
            allowCredentials = true
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }
}
